# Servidor de alineación de letras (WhisperX)

Servicio opcional para ResuenApp: recibe el audio de una canción y su letra sin marcas de
tiempo, y devuelve cuándo se canta cada línea y cada palabra (JSON en milisegundos o LRC
"mejorado"). La app lo usa cuando la letra que encontró no trae tiempos; mientras tanto
muestra la estimación hecha en el teléfono o la letra estática.

## Cómo funciona

1. `ffmpeg` convierte el audio subido (FLAC, ALAC, MP3…) a WAV.
2. **Demucs** separa la voz de la música (se puede desactivar).
3. **Whisper** transcribe la voz: detecta el idioma y ubica aproximadamente cada línea,
   emparejando lo transcrito con la letra (`app/text.py`).
4. **wav2vec2** (`whisperx.align`) alinea el texto exacto de cada línea dentro de su
   ventana y da inicio y fin de cada palabra (`app/pipeline.py`).
5. Las líneas que no se pudieron ubicar con confianza se interpolan y se marcan
   `"estimated": true`.

Procesa una canción a la vez. Con GPU NVIDIA tarda segundos por canción; en CPU, del orden
de minutos (usa `small` en vez de `large-v3`). La primera vez descarga los modelos (varios GB)
al volumen `data/`.

## Levantarlo con Docker

```bash
cd aligner
cp .env.example .env        # y cambia ALIGNER_TOKEN por algo largo y aleatorio
docker compose up -d --build
curl -H "Authorization: Bearer <token>" http://localhost:8000/v1/health
```

Sin GPU, borra el bloque `deploy:` de `docker-compose.yml`.

En la app: **Ajustes → Letras → Servidor de sincronización**, con la dirección del PC en tu
red (p. ej. `http://192.168.1.20:8000`) y el mismo token. "Probar conexión" muestra el
modelo y si usa GPU o CPU.

> La app habla HTTP sin cifrar con el servidor, así que el token y el audio viajan en claro
> por tu red local. Fuera de casa, usa una VPN como Tailscale o pon el servidor detrás de
> HTTPS (Caddy, nginx); no lo expongas directo a internet.

## Sin Docker (para desarrollo)

```bash
cd aligner
python3.11 -m venv .venv && . .venv/bin/activate
pip install ".[ml,test]"          # sin ml basta para correr los tests
ALIGNER_TOKEN=prueba uvicorn app.main:app_factory --factory --reload
pytest -q
```

Requiere `ffmpeg` instalado.

## Configuración (variables de entorno)

| Variable | Por defecto | Uso |
|---|---|---|
| `ALIGNER_TOKEN` | vacío | Token que exige la API (`Authorization: Bearer …`). Vacío = sin autenticación |
| `ALIGNER_DEVICE` | `auto` | `cuda`, `cpu` o `auto` |
| `ALIGNER_WHISPER_MODEL` | vacío | Vacío = `large-v3` con GPU, `small` en CPU |
| `ALIGNER_SEPARATE_VOCALS` | `1` | Separar la voz con Demucs antes de alinear |
| `ALIGNER_MAX_UPLOAD_MB` | `400` | Tamaño máximo del audio |
| `ALIGNER_DATA_DIR` | `data` | Trabajos (SQLite), audio pendiente y modelos |

## API

Todas las rutas exigen `Authorization: Bearer <token>` si hay token configurado.

El id de un trabajo es `sha256(<sha256 del audio en hex> + "\n" + letra normalizada)`, donde
la letra normalizada son sus líneas recortadas, sin las vacías, unidas por `\n`. La app lo
calcula igual (`AlignmentClient.jobId`), así que pregunta primero sin subir nada: si la
canción ya se procesó, no se vuelve a enviar.

| Método | Ruta | Uso |
|---|---|---|
| `GET` | `/v1/alignments/{id}` | Estado y resultado. 404 si no existe |
| `POST` | `/v1/alignments` | multipart: `audio` (archivo), `lyrics`, y opcionales `language`, `title`, `artist`, `durationMs`. 202 si se encoló; 200 si ya existía |
| `GET` | `/v1/alignments/{id}.lrc` | El resultado como LRC mejorado (`[00:12.34]<00:12.34>Hola <00:12.90>mundo`) |
| `DELETE` | `/v1/alignments/{id}` | Borrar el trabajo (permite reprocesar) |
| `GET` | `/v1/health` | Versión, dispositivo, modelo y trabajos en cola |

Respuesta de un trabajo terminado:

```json
{
  "id": "1119a76a…",
  "status": "done",
  "result": {
    "version": 1,
    "language": "es",
    "durationMs": 214000,
    "model": "whisperx/large-v3 + wav2vec2-es + demucs",
    "confidence": 0.87,
    "lines": [
      {
        "startMs": 12340, "endMs": 15800, "text": "Primera línea de la letra",
        "estimated": false,
        "words": [ { "startMs": 12340, "endMs": 12900, "text": "Primera", "score": 0.93 } ]
      }
    ]
  }
}
```

`status` pasa por `queued` → `processing` → `done` o `failed` (con `error`). Un trabajo que
falló se puede reenviar con el mismo `POST`. El audio subido se borra al terminar; solo se
guarda el resultado.

## Licencias

WhisperX (BSD-2-Clause), Whisper (MIT), Demucs (MIT), FastAPI (MIT). Los modelos wav2vec2
que descarga WhisperX para cada idioma tienen sus propias licencias (en su mayoría
Apache-2.0 o MIT), indicadas en Hugging Face.
