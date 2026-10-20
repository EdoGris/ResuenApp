"""Alineación forzada con WhisperX.

1. ffmpeg normaliza el audio subido (FLAC, ALAC, MP3…) a WAV.
2. Demucs separa la voz (opcional): la música de fondo confunde a ambos modelos.
3. Whisper transcribe la voz: da el idioma y dónde suena aproximadamente cada línea.
4. wav2vec2 (whisperx.align) alinea el texto exacto de cada línea dentro de su ventana y
   devuelve inicio y fin de cada palabra.

Las bibliotecas pesadas (torch, whisperx) se importan recién al procesar, para que la API y
los tests funcionen sin ellas.
"""

from __future__ import annotations

import logging
import subprocess
import tempfile
import threading
from pathlib import Path

from .config import Settings
from .jobs import JobInput
from .models import AlignmentResult, Line, Word
from .text import TimedWord, Window, display_words, line_windows, singable_lines

log = logging.getLogger("aligner.pipeline")

SAMPLE_RATE = 16_000
# Margen alrededor de la ventana estimada de cada línea, en segundos.
WINDOW_PAD = 1.0
# Bajo este puntaje medio, una línea que no apareció en la transcripción se marca estimada.
MIN_SCORE = 0.5


class WhisperXAligner:
    def __init__(self, settings: Settings):
        self.settings = settings
        self._lock = threading.Lock()
        self._device: str | None = None
        self._asr = None
        self._align_models: dict[str, tuple] = {}

    # ---------- Modelos (se cargan una vez) ----------

    @property
    def device(self) -> str:
        if self._device is None:
            if self.settings.device != "auto":
                self._device = self.settings.device
            else:
                try:
                    import torch

                    self._device = "cuda" if torch.cuda.is_available() else "cpu"
                except ImportError:
                    self._device = "cpu"
        return self._device

    @property
    def whisper_model(self) -> str:
        return self.settings.whisper_model or ("large-v3" if self.device == "cuda" else "small")

    def _load_asr(self):
        if self._asr is None:
            import whisperx

            compute = "float16" if self.device == "cuda" else "int8"
            self._asr = whisperx.load_model(self.whisper_model, self.device, compute_type=compute)
        return self._asr

    def _load_align(self, language: str):
        if language not in self._align_models:
            import whisperx

            self._align_models[language] = whisperx.load_align_model(language_code=language, device=self.device)
        return self._align_models[language]

    # ---------- Proceso ----------

    def __call__(self, job: JobInput) -> AlignmentResult:
        with self._lock, tempfile.TemporaryDirectory(prefix="aligner-") as tmp:
            return self._align(job, Path(tmp))

    def _align(self, job: JobInput, tmp: Path) -> AlignmentResult:
        import whisperx

        lines = singable_lines(job.lyrics)
        if not lines:
            raise ValueError("La letra no tiene líneas para alinear.")

        wav = tmp / "input.wav"
        _run(["ffmpeg", "-nostdin", "-v", "error", "-y", "-i", str(job.audio_path), "-ac", "2", "-ar", "44100", str(wav)])
        voice = self._separate_vocals(wav, tmp) if self.settings.separate_vocals else wav
        audio = whisperx.load_audio(str(voice))
        duration = len(audio) / SAMPLE_RATE

        # 3) Transcripción: idioma y ubicación aproximada de cada línea.
        transcript = self._load_asr().transcribe(audio, batch_size=16, language=job.language or None)
        language = job.language or transcript.get("language") or "en"
        align_model, metadata = self._load_align(language)
        heard = whisperx.align(
            transcript["segments"], align_model, metadata, audio, self.device, return_char_alignments=False
        )
        timed = [
            TimedWord(w["word"], w["start"], w["end"])
            for w in heard.get("word_segments", [])
            if "start" in w and "end" in w
        ]
        windows = line_windows(lines, timed, duration)

        # 4) Alineación forzada del texto exacto, línea por línea.
        segments = []
        for i, (line, win) in enumerate(zip(lines, windows)):
            floor = windows[i - 1].start if i > 0 else 0.0
            ceiling = windows[i + 1].end if i + 1 < len(windows) else duration
            segments.append({
                "text": line,
                "start": max(floor, win.start - WINDOW_PAD, 0.0),
                "end": min(ceiling, win.end + WINDOW_PAD, duration),
            })
        aligned = whisperx.align(segments, align_model, metadata, audio, self.device, return_char_alignments=False)
        return build_result(lines, windows, aligned["segments"], language, duration,
                            model=f"whisperx/{self.whisper_model} + wav2vec2-{language}"
                            + (" + demucs" if self.settings.separate_vocals else ""))

    def _separate_vocals(self, wav: Path, tmp: Path) -> Path:
        _run(["python", "-m", "demucs.separate", "--two-stems=vocals", "-n", "htdemucs",
              "-d", self.device, "-o", str(tmp / "sep"), str(wav)])
        return tmp / "sep" / "htdemucs" / wav.stem / "vocals.wav"


def build_result(lines: list[str], windows: list[Window], aligned_segments: list[dict],
                 language: str, duration: float, model: str) -> AlignmentResult:
    """Arma el resultado a partir de los segmentos alineados (uno por línea, en orden).

    Las palabras que wav2vec2 no pudo ubicar (números, símbolos) se interpolan entre sus
    vecinas; una línea sin ninguna palabra ubicada toma la ventana estimada.
    """
    out: list[Line] = []
    scores: list[float] = []
    prev_start = 0
    for i, line in enumerate(lines):
        win = windows[i]
        seg = aligned_segments[i] if i < len(aligned_segments) else {}
        raw = seg.get("words") or []
        texts = display_words(line)
        if len(raw) != len(texts):
            # WhisperX separa por espacios; si no coincide, usar sus palabras tal cual.
            texts = [w.get("word", "") for w in raw] or texts
        starts = [w.get("start") for w in raw] or [None] * len(texts)
        ends = [w.get("end") for w in raw] or [None] * len(texts)
        line_scores = [w["score"] for w in raw if w.get("score") is not None]
        located = [s for s in starts if s is not None]

        if located:
            starts, ends = _interpolate(starts, ends, win)
            mean = sum(line_scores) / len(line_scores) if line_scores else 0.0
            estimated = not win.matched and mean < MIN_SCORE
            scores += line_scores
        else:
            n = max(1, len(texts))
            step = (win.end - win.start) / n
            starts = [win.start + k * step for k in range(n)]
            ends = [s + step for s in starts]
            estimated = True

        words = [
            Word(startMs=_ms(s), endMs=_ms(e), text=t,
                 score=(raw[k].get("score") if k < len(raw) else None))
            for k, (t, s, e) in enumerate(zip(texts, starts, ends))
        ]
        start = max(_ms(starts[0]), prev_start)
        end = max(_ms(ends[-1]), start)
        out.append(Line(startMs=start, endMs=end, text=line, estimated=estimated, words=words))
        prev_start = start

    confidence = round(sum(scores) / len(scores), 3) if scores else 0.0
    return AlignmentResult(language=language, durationMs=_ms(duration), model=model,
                           confidence=confidence, lines=out)


def _interpolate(starts: list, ends: list, win: Window) -> tuple[list[float], list[float]]:
    n = len(starts)
    s = list(starts)
    e = list(ends)
    k = 0
    while k < n:
        if s[k] is not None:
            if e[k] is None:
                e[k] = s[k]
            k += 1
            continue
        j = k
        while j < n and s[j] is None:
            j += 1
        left = e[k - 1] if k > 0 else win.start
        right = s[j] if j < n else max(win.end, left)
        step = (right - left) / (j - k + 1) if right > left else 0.0
        for m in range(k, j):
            s[m] = left + step * (m - k + 0.5)
            e[m] = s[m] + step * 0.9
        k = j
    return s, e


def _ms(seconds: float) -> int:
    return max(0, int(round(seconds * 1000)))


def _run(cmd: list[str]) -> None:
    proc = subprocess.run(cmd, capture_output=True, text=True)
    if proc.returncode != 0:
        raise RuntimeError(f"{cmd[0]} {cmd[2] if len(cmd) > 2 else ''} falló: {proc.stderr.strip()[-300:]}")
