"""Texto de la letra: normalización, identificador del trabajo y emparejamiento con lo transcrito."""

from __future__ import annotations

import difflib
import hashlib
import re
import unicodedata
from dataclasses import dataclass

# Rótulos que no se cantan: "[Coro]", "(x2)", "Verse 1:". "(Oh, yeah)" sí se canta.
# Mismas reglas que LyricsAligner.splitLines en la app.
_LABEL = re.compile(
    r"^\s*(\[[^\]]*]|\(\s*(x\s*\d+|\d+\s*x|bis|repeat|repetir)\s*\)|"
    r"(verse|verso|chorus|coro|estribillo|bridge|puente|intro|outro|pre-?chorus)\s*\d*\s*:?)\s*$",
    re.IGNORECASE,
)
_WORD = re.compile(r"[^\W_]+(?:['’][^\W_]+)*")


def normalize_lyrics(text: str) -> str:
    """Texto canónico para el identificador: líneas recortadas, sin vacías, unidas por "\\n".

    La app hace lo mismo (AlignmentClient.normalizeLyrics) para calcular el id sin subir nada.
    """
    text = text.replace("\r\n", "\n").replace("\r", "\n").lstrip("﻿")
    return "\n".join(line.strip() for line in text.split("\n") if line.strip())


def job_id(audio_sha256: str, lyrics: str) -> str:
    """sha256(<sha256 del audio en hex> + "\\n" + letra normalizada), en hex."""
    payload = audio_sha256.lower() + "\n" + normalize_lyrics(lyrics)
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()


def singable_lines(text: str) -> list[str]:
    """Líneas que se cantan (sin rótulos como "[Coro]" ni líneas vacías)."""
    return [line for line in normalize_lyrics(text).split("\n") if line and not _LABEL.match(line)]


def tokens(text: str) -> list[str]:
    """Palabras en minúsculas y sin tildes, para comparar letra y transcripción."""
    plain = unicodedata.normalize("NFKD", text.lower())
    plain = "".join(c for c in plain if not unicodedata.combining(c))
    return [w.replace("’", "'") for w in _WORD.findall(plain)]


def display_words(line: str) -> list[str]:
    """Palabras tal como se muestran (con puntuación pegada), separadas por espacios."""
    return line.split()


@dataclass(frozen=True)
class TimedWord:
    text: str
    start: float  # segundos
    end: float


@dataclass(frozen=True)
class Window:
    """Intervalo aproximado de una línea; [matched] = se encontró en la transcripción."""

    start: float
    end: float
    matched: bool


def line_windows(lines: list[str], transcript: list[TimedWord], duration: float) -> list[Window]:
    """Ubica cada línea de la letra sobre la transcripción de Whisper.

    Se alinean las dos secuencias de palabras (difflib) y cada línea toma el intervalo de sus
    palabras coincidentes. Las líneas sin coincidencias reparten, en proporción a su largo, el
    hueco entre las vecinas que sí se encontraron.
    """
    if not lines:
        return []
    lyric_tokens: list[tuple[str, int]] = []
    for i, line in enumerate(lines):
        lyric_tokens += [(t, i) for t in tokens(line)]
    heard: list[tuple[str, TimedWord]] = []
    for w in transcript:
        heard += [(t, w) for t in tokens(w.text)]

    found: dict[int, list[TimedWord]] = {}
    matcher = difflib.SequenceMatcher(
        a=[t for t, _ in lyric_tokens], b=[t for t, _ in heard], autojunk=False
    )
    for block in matcher.get_matching_blocks():
        for k in range(block.size):
            line = lyric_tokens[block.a + k][1]
            found.setdefault(line, []).append(heard[block.b + k][1])

    windows: list[Window | None] = [None] * len(lines)
    for i, words in found.items():
        # Una sola palabra suelta coincidente en una línea larga suele ser casualidad.
        if len(words) == 1 and len(tokens(lines[i])) > 3:
            continue
        windows[i] = Window(min(w.start for w in words), max(w.end for w in words), matched=True)

    # Descartar ventanas que rompen el orden (una coincidencia con otra repetición del coro).
    last_end = 0.0
    for i, w in enumerate(windows):
        if w is None:
            continue
        if w.start < last_end - 1.0:
            windows[i] = None
        else:
            last_end = max(last_end, w.end)

    return _fill_gaps(lines, windows, duration)


def _fill_gaps(lines: list[str], windows: list[Window | None], duration: float) -> list[Window]:
    out: list[Window] = []
    i = 0
    n = len(lines)
    while i < n:
        if windows[i] is not None:
            out.append(windows[i])  # type: ignore[arg-type]
            i += 1
            continue
        j = i
        while j < n and windows[j] is None:
            j += 1
        gap_start = out[-1].end if out else 0.0
        gap_end = windows[j].start if j < n else duration  # type: ignore[union-attr]
        if gap_end <= gap_start:
            gap_end = gap_start + 0.5 * (j - i)
        weights = [max(1, len(tokens(lines[k]))) for k in range(i, j)]
        total = sum(weights)
        t = gap_start
        for w in weights:
            span = (gap_end - gap_start) * w / total
            out.append(Window(t, t + span, matched=False))
            t += span
        i = j
    return out
