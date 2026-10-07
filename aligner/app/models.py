"""Esquemas de la API (tiempos en milisegundos)."""

from __future__ import annotations

from enum import Enum

from pydantic import BaseModel, Field


class Status(str, Enum):
    queued = "queued"
    processing = "processing"
    done = "done"
    failed = "failed"


class Word(BaseModel):
    startMs: int
    endMs: int
    text: str
    score: float | None = None


class Line(BaseModel):
    startMs: int
    endMs: int
    text: str
    # Tiempos interpolados: la línea no se pudo alinear con confianza.
    estimated: bool = False
    words: list[Word] = Field(default_factory=list)


class AlignmentResult(BaseModel):
    version: int = 1
    language: str
    durationMs: int
    model: str
    confidence: float
    lines: list[Line]


class Job(BaseModel):
    id: str
    status: Status
    error: str | None = None
    result: AlignmentResult | None = None


class Health(BaseModel):
    version: str
    device: str
    whisperModel: str
    separateVocals: bool
    queued: int
