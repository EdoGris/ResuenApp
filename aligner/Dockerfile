# Servicio de alineación de letras para ResuenApp.
# Las ruedas de PyTorch de PyPI para Linux ya traen CUDA: la misma imagen usa la GPU NVIDIA
# si Docker la expone (ver docker-compose.yml) y la CPU si no.
FROM python:3.11-slim

RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /srv
COPY pyproject.toml ./
COPY app ./app
RUN pip install --no-cache-dir ".[ml]"

# Modelos descargados (Whisper, wav2vec2, Demucs) y trabajos: en el volumen, sobreviven a reinicios.
ENV ALIGNER_DATA_DIR=/data \
    HF_HOME=/data/models/hf \
    TORCH_HOME=/data/models/torch \
    XDG_CACHE_HOME=/data/models/cache
VOLUME /data
EXPOSE 8000

CMD ["uvicorn", "app.main:app_factory", "--factory", "--host", "0.0.0.0", "--port", "8000"]
