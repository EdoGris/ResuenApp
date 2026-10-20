"""Configuración por variables de entorno (ver README)."""

from __future__ import annotations

import os
from dataclasses import dataclass, field
from pathlib import Path


def _flag(name: str, default: bool) -> bool:
    value = os.environ.get(name)
    return default if value is None else value.strip().lower() in {"1", "true", "yes", "si", "sí"}


@dataclass
class Settings:
    data_dir: Path = field(default_factory=lambda: Path(os.environ.get("ALIGNER_DATA_DIR", "data")))
    # Vacío = sin autenticación (solo para pruebas en tu propia red).
    token: str = field(default_factory=lambda: os.environ.get("ALIGNER_TOKEN", ""))
    # "auto" usa la GPU si hay una con CUDA.
    device: str = field(default_factory=lambda: os.environ.get("ALIGNER_DEVICE", "auto"))
    # Vacío = large-v3 con GPU, small en CPU.
    whisper_model: str = field(default_factory=lambda: os.environ.get("ALIGNER_WHISPER_MODEL", ""))
    separate_vocals: bool = field(default_factory=lambda: _flag("ALIGNER_SEPARATE_VOCALS", True))
    max_upload_mb: int = field(default_factory=lambda: int(os.environ.get("ALIGNER_MAX_UPLOAD_MB", "400")))

    @property
    def uploads_dir(self) -> Path:
        return self.data_dir / "uploads"

    @property
    def db_path(self) -> Path:
        return self.data_dir / "jobs.db"
