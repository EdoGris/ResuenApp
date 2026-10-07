"""API HTTP del servicio de alineación."""

from __future__ import annotations

import hashlib
import hmac
import logging
import shutil
import tempfile
from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import Depends, FastAPI, File, Form, Header, HTTPException, Response, UploadFile, status
from fastapi.responses import PlainTextResponse

from . import __version__
from .config import Settings
from .jobs import Aligner, JobInput, JobStore, Worker
from .lrc import to_lrc
from .models import Health, Job, Status
from .text import job_id, singable_lines

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")

CHUNK = 1 << 20


def create_app(settings: Settings | None = None, aligner: Aligner | None = None,
               start_worker: bool = True) -> FastAPI:
    settings = settings or Settings()
    settings.uploads_dir.mkdir(parents=True, exist_ok=True)
    store = JobStore(settings.db_path)
    if aligner is None:
        from .pipeline import WhisperXAligner

        aligner = WhisperXAligner(settings)
    worker = Worker(store, settings.uploads_dir, aligner)

    @asynccontextmanager
    async def lifespan(_: FastAPI):
        if start_worker:
            worker.start()
        yield
        if start_worker:
            worker.stop()

    app = FastAPI(title="ResuenApp aligner", version=__version__, lifespan=lifespan)
    app.state.worker = worker
    app.state.store = store

    def auth(authorization: str | None = Header(default=None)) -> None:
        if not settings.token:
            return
        expected = f"Bearer {settings.token}"
        if authorization is None or not hmac.compare_digest(authorization.encode(), expected.encode()):
            raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Token inválido", headers={"WWW-Authenticate": "Bearer"})

    protected = [Depends(auth)]

    @app.get("/v1/health", response_model=Health, dependencies=protected)
    def health() -> Health:
        device = getattr(aligner, "device", "n/a")
        model = getattr(aligner, "whisper_model", "n/a")
        return Health(version=__version__, device=device, whisperModel=model,
                      separateVocals=settings.separate_vocals, queued=store.count_queued())

    # Va antes de /{job_id} para que "abc.lrc" no se tome como un id.
    @app.get("/v1/alignments/{job_id}.lrc", response_class=PlainTextResponse, dependencies=protected)
    def get_lrc(job_id: str) -> PlainTextResponse:
        job = store.get(job_id)
        if job is None or job.status != Status.done or job.result is None:
            raise HTTPException(status.HTTP_404_NOT_FOUND, "Alineación no disponible")
        meta = store.meta(job_id) or {}
        return PlainTextResponse(to_lrc(job.result, meta.get("title", ""), meta.get("artist", "")),
                                 media_type="text/plain; charset=utf-8")

    @app.get("/v1/alignments/{job_id}", response_model=Job, response_model_exclude_none=True,
             dependencies=protected)
    def get_job(job_id: str) -> Job:
        job = store.get(job_id)
        if job is None:
            raise HTTPException(status.HTTP_404_NOT_FOUND, "No existe")
        return job

    @app.post("/v1/alignments", response_model=Job, response_model_exclude_none=True,
              status_code=status.HTTP_202_ACCEPTED, dependencies=protected)
    def create_job(
        response: Response,
        audio: UploadFile = File(...),
        lyrics: str = Form(...),
        language: str | None = Form(default=None),
        title: str = Form(default=""),
        artist: str = Form(default=""),
        durationMs: int | None = Form(default=None),
    ) -> Job:
        if not singable_lines(lyrics):
            raise HTTPException(422, "La letra está vacía")
        tmp, digest = _save_upload(audio, settings.uploads_dir, settings.max_upload_mb)
        try:
            jid = job_id(digest, lyrics)
            existing = store.get(jid)
            if existing is not None and existing.status != Status.failed:
                response.status_code = status.HTTP_200_OK
                return existing
            target = worker.audio_path(jid)
            shutil.move(str(tmp), target)
        finally:
            tmp.unlink(missing_ok=True)
        lang = (language or "").strip().lower()[:8] or None
        worker.submit(JobInput(id=jid, audio_path=target, lyrics=lyrics, language=lang,
                               title=title[:300], artist=artist[:300], duration_ms=durationMs))
        return store.get(jid)  # type: ignore[return-value]

    @app.delete("/v1/alignments/{job_id}", status_code=status.HTTP_204_NO_CONTENT, dependencies=protected)
    def delete_job(job_id: str) -> Response:
        if not store.delete(job_id):
            raise HTTPException(status.HTTP_404_NOT_FOUND, "No existe")
        worker.audio_path(job_id).unlink(missing_ok=True)
        return Response(status_code=status.HTTP_204_NO_CONTENT)

    return app


def _save_upload(audio: UploadFile, folder: Path, max_mb: int) -> tuple[Path, str]:
    """Guarda el archivo subido calculando su SHA-256; corta si supera el límite."""
    limit = max_mb * CHUNK
    sha = hashlib.sha256()
    size = 0
    fd, name = tempfile.mkstemp(dir=folder, suffix=".part")
    path = Path(name)
    try:
        with open(fd, "wb") as out:
            while chunk := audio.file.read(CHUNK):
                size += len(chunk)
                if size > limit:
                    raise HTTPException(413, f"El audio supera {max_mb} MB")
                sha.update(chunk)
                out.write(chunk)
    except BaseException:
        path.unlink(missing_ok=True)
        raise
    if size == 0:
        path.unlink(missing_ok=True)
        raise HTTPException(422, "El audio está vacío")
    return path, sha.hexdigest()



def app_factory() -> FastAPI:
    """Punto de entrada para uvicorn: `uvicorn app.main:app_factory --factory`."""
    return create_app()
