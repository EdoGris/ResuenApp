"""Exportación a LRC "mejorado": marca por línea y por palabra (<mm:ss.xx>)."""

from __future__ import annotations

from .models import AlignmentResult


def _stamp(ms: int) -> str:
    ms = max(0, ms)
    return f"{ms // 60_000:02d}:{ms % 60_000 // 1000:02d}.{ms % 1000 // 10:02d}"


def to_lrc(result: AlignmentResult, title: str = "", artist: str = "") -> str:
    out: list[str] = []
    if title:
        out.append(f"[ti:{title}]")
    if artist:
        out.append(f"[ar:{artist}]")
    out.append(f"[la:{result.language}]")
    out.append("[by:ResuenApp aligner]")
    for line in result.lines:
        if line.words:
            body = " ".join(f"<{_stamp(w.startMs)}>{w.text}" for w in line.words)
        else:
            body = line.text
        out.append(f"[{_stamp(line.startMs)}]{body}")
    return "\n".join(out) + "\n"
