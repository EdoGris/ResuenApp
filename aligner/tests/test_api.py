import hashlib

import pytest
from fastapi.testclient import TestClient

from app.config import Settings
from app.main import create_app
from app.models import AlignmentResult, Line
from app.text import job_id

LYRICS = "Primera línea\nSegunda línea\n"
AUDIO = b"RIFF fake audio bytes"


def fake_aligner(job):
    if "falla" in job.lyrics:
        raise RuntimeError("modelo caído")
    assert job.audio_path.read_bytes() == AUDIO
    return AlignmentResult(language=job.language or "es", durationMs=1000, model="fake", confidence=1.0,
                           lines=[Line(startMs=0, endMs=500, text="Primera línea")])


@pytest.fixture
def setup(tmp_path):
    settings = Settings(data_dir=tmp_path, token="secreto", device="cpu", whisper_model="tiny",
                        separate_vocals=False, max_upload_mb=1)
    app = create_app(settings, aligner=fake_aligner, start_worker=False)
    client = TestClient(app, headers={"Authorization": "Bearer secreto"})
    return app, client, tmp_path


def post(client, lyrics=LYRICS, audio=AUDIO):
    return client.post("/v1/alignments", files={"audio": ("song.flac", audio)},
                       data={"lyrics": lyrics, "title": "T", "artist": "A", "durationMs": "1000"})


def test_requires_token(setup):
    app, _, _ = setup
    assert TestClient(app).get("/v1/health").status_code == 401
    assert TestClient(app, headers={"Authorization": "Bearer otro"}).get("/v1/health").status_code == 401


def test_full_flow(setup):
    app, client, tmp = setup
    expected = job_id(hashlib.sha256(AUDIO).hexdigest(), LYRICS)
    assert client.get(f"/v1/alignments/{expected}").status_code == 404

    r = post(client)
    assert r.status_code == 202
    assert r.json() == {"id": expected, "status": "queued"}

    app.state.worker.drain()
    job = client.get(f"/v1/alignments/{expected}").json()
    assert job["status"] == "done"
    assert job["result"]["lines"][0]["text"] == "Primera línea"
    assert not list((tmp / "uploads").iterdir()), "el audio se borra al terminar"

    lrc = client.get(f"/v1/alignments/{expected}.lrc")
    assert lrc.status_code == 200 and "[00:00.00]Primera línea" in lrc.text and "[ti:T]" in lrc.text

    # Reenviar lo mismo no reprocesa.
    again = post(client)
    assert again.status_code == 200 and again.json()["status"] == "done"

    assert client.delete(f"/v1/alignments/{expected}").status_code == 204
    assert client.get(f"/v1/alignments/{expected}").status_code == 404


def test_failure_is_reported_and_can_retry(setup):
    app, client, _ = setup
    jid = post(client, lyrics="esto falla").json()["id"]
    app.state.worker.drain()
    job = client.get(f"/v1/alignments/{jid}").json()
    assert job["status"] == "failed" and "modelo caído" in job["error"]
    assert post(client, lyrics="esto falla").status_code == 202  # un fallo se puede reintentar


def test_rejects_empty_and_too_large(setup):
    _, client, _ = setup
    assert post(client, lyrics="[Coro]\n\n").status_code == 422
    assert post(client, audio=b"").status_code == 422
    assert post(client, audio=b"x" * (1024 * 1024 + 1)).status_code == 413


def test_lrc_missing_until_done(setup):
    _, client, _ = setup
    jid = post(client).json()["id"]
    assert client.get(f"/v1/alignments/{jid}.lrc").status_code == 404
