"""Trabajos de alineación: estado en SQLite y un único trabajador en segundo plano.

Un solo trabajador porque los modelos ocupan casi toda la memoria de la GPU; las canciones
se procesan de a una, en orden de llegada.
"""

from __future__ import annotations

import logging
import queue
import sqlite3
import threading
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Callable

from .models import AlignmentResult, Job, Status

log = logging.getLogger("aligner.jobs")


@dataclass
class JobInput:
    id: str
    audio_path: Path
    lyrics: str
    language: str | None
    title: str
    artist: str
    duration_ms: int | None


Aligner = Callable[[JobInput], AlignmentResult]


class JobStore:
    def __init__(self, db_path: Path):
        db_path.parent.mkdir(parents=True, exist_ok=True)
        self._db = sqlite3.connect(db_path, check_same_thread=False)
        self._lock = threading.Lock()
        with self._lock:
            self._db.execute(
                """CREATE TABLE IF NOT EXISTS jobs (
                    id TEXT PRIMARY KEY,
                    status TEXT NOT NULL,
                    error TEXT,
                    result TEXT,
                    lyrics TEXT NOT NULL,
                    language TEXT,
                    title TEXT NOT NULL DEFAULT '',
                    artist TEXT NOT NULL DEFAULT '',
                    duration_ms INTEGER,
                    created_at REAL NOT NULL,
                    updated_at REAL NOT NULL
                )"""
            )
            self._db.commit()

    def get(self, job_id: str) -> Job | None:
        row = self._row(job_id)
        if row is None:
            return None
        result = AlignmentResult.model_validate_json(row["result"]) if row["result"] else None
        return Job(id=row["id"], status=Status(row["status"]), error=row["error"], result=result)

    def meta(self, job_id: str) -> dict | None:
        return self._row(job_id)

    def _row(self, job_id: str) -> dict | None:
        with self._lock:
            cur = self._db.execute("SELECT * FROM jobs WHERE id = ?", (job_id,))
            row = cur.fetchone()
            if row is None:
                return None
            return dict(zip([c[0] for c in cur.description], row))

    def put_queued(self, job: JobInput) -> None:
        now = time.time()
        with self._lock:
            self._db.execute(
                """INSERT INTO jobs (id, status, error, result, lyrics, language, title, artist,
                                     duration_ms, created_at, updated_at)
                   VALUES (?, 'queued', NULL, NULL, ?, ?, ?, ?, ?, ?, ?)
                   ON CONFLICT(id) DO UPDATE SET status='queued', error=NULL, result=NULL,
                       language=excluded.language, updated_at=excluded.updated_at""",
                (job.id, job.lyrics, job.language, job.title, job.artist, job.duration_ms, now, now),
            )
            self._db.commit()

    def set_status(self, job_id: str, status: Status, error: str | None = None,
                   result: AlignmentResult | None = None) -> None:
        with self._lock:
            self._db.execute(
                "UPDATE jobs SET status = ?, error = ?, result = ?, updated_at = ? WHERE id = ?",
                (status.value, error, result.model_dump_json() if result else None, time.time(), job_id),
            )
            self._db.commit()

    def delete(self, job_id: str) -> bool:
        with self._lock:
            cur = self._db.execute("DELETE FROM jobs WHERE id = ?", (job_id,))
            self._db.commit()
            return cur.rowcount > 0

    def pending(self) -> list[dict]:
        with self._lock:
            cur = self._db.execute(
                "SELECT * FROM jobs WHERE status IN ('queued', 'processing') ORDER BY created_at"
            )
            names = [c[0] for c in cur.description]
            return [dict(zip(names, r)) for r in cur.fetchall()]

    def count_queued(self) -> int:
        with self._lock:
            return self._db.execute(
                "SELECT COUNT(*) FROM jobs WHERE status IN ('queued', 'processing')"
            ).fetchone()[0]


class Worker:
    """Procesa los trabajos de a uno. El audio subido se borra al terminar."""

    def __init__(self, store: JobStore, uploads_dir: Path, aligner: Aligner):
        self.store = store
        self.uploads_dir = uploads_dir
        self.aligner = aligner
        self._queue: queue.Queue[JobInput | None] = queue.Queue()
        self._thread: threading.Thread | None = None

    def audio_path(self, job_id: str) -> Path:
        return self.uploads_dir / f"{job_id}.audio"

    def start(self) -> None:
        # Retomar lo que quedó pendiente al reiniciar el servidor.
        for row in self.store.pending():
            path = self.audio_path(row["id"])
            if path.exists():
                self.store.set_status(row["id"], Status.queued)
                self._queue.put(self._input(row, path))
            else:
                self.store.set_status(row["id"], Status.failed, error="El audio se perdió al reiniciar; vuelve a enviarlo.")
        self._thread = threading.Thread(target=self._run, name="aligner-worker", daemon=True)
        self._thread.start()

    def stop(self) -> None:
        self._queue.put(None)
        if self._thread:
            self._thread.join(timeout=5)

    def submit(self, job: JobInput) -> None:
        self.store.put_queued(job)
        self._queue.put(job)

    def drain(self) -> None:
        """Procesa en este hilo lo que haya en la cola (para tests)."""
        while not self._queue.empty():
            job = self._queue.get_nowait()
            if job is not None:
                self._process(job)

    def _run(self) -> None:
        while True:
            job = self._queue.get()
            if job is None:
                return
            self._process(job)

    def _process(self, job: JobInput) -> None:
        current = self.store.get(job.id)
        if current is None or current.status != Status.queued:
            return  # borrado o ya procesado mientras esperaba
        self.store.set_status(job.id, Status.processing)
        try:
            result = self.aligner(job)
            self.store.set_status(job.id, Status.done, result=result)
        except Exception as e:  # noqa: BLE001 - cualquier fallo queda registrado en el trabajo
            log.exception("Falló la alineación de %s", job.id)
            self.store.set_status(job.id, Status.failed, error=f"{type(e).__name__}: {e}"[:500])
        finally:
            job.audio_path.unlink(missing_ok=True)

    @staticmethod
    def _input(row: dict, path: Path) -> JobInput:
        return JobInput(
            id=row["id"], audio_path=path, lyrics=row["lyrics"], language=row["language"],
            title=row["title"], artist=row["artist"], duration_ms=row["duration_ms"],
        )

