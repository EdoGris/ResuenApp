# Lossless Player (Android 14+)

Reproductor local de MP3, FLAC y ALAC construido con las librerías oficiales de Android:
Media3 / ExoPlayer, MediaCodec, MediaStore y AudioManager.

## Cómo abrirlo
1. Abre la carpeta en Android Studio (versión reciente). Si falta el Gradle wrapper,
   Android Studio lo genera al sincronizar.
2. Sincroniza Gradle y revisa que las versiones de Media3, de `media3-ffmpeg-decoder`
   (Jellyfin) y del Compose BOM sigan vigentes. Las dos primeras deben coincidir.
3. Instala la app en un teléfono con Android 14 o superior.

## Estructura
- `library/MusicRepository.kt`: lee la biblioteca local con MediaStore (MP3, FLAC, M4A).
- `playback/LosslessRenderersFactory.kt`: decodificadores nativos con FFmpeg como
  respaldo, y salida PCM float de 32 bits para no truncar audio de 24 bits.
- `playback/AudioProbe.kt`: lee códec, frecuencia y canales reales del archivo.
- `playback/BitPerfectController.kt`: modo bit-perfect para DAC USB
  (`AudioMixerAttributes`, API 34).
- `playback/PlaybackService.kt`: `MediaSessionService` para reproducir en segundo plano,
  con controles en la notificación y pantalla de bloqueo.
- `MainActivity.kt`: interfaz en Compose con la lista de pistas, los controles y el
  estado de la salida.
- `lyrics/`: letras (.lrc junto al audio, etiquetas, LRCLIB), estimación de tiempos en el
  teléfono y cliente del servidor de alineación (`AlignmentClient.kt`).
- `aligner/`: servidor opcional (Python, FastAPI + WhisperX) que calcula los tiempos exactos
  de cada palabra de una letra sin marcas. Ver [aligner/README.md](aligner/README.md).

## Qué calidad llega según la salida
- **DAC USB**: bit-perfect si el DAC ofrece un modo con la misma frecuencia y canales
  que el archivo. La app lo indica como "BIT-PERFECT". En este modo el volumen se
  controla en el DAC y los demás sonidos del sistema se silencian.
- **Audífonos con cable**: decodificación sin pérdida, pero el mezclador de Android
  normalmente remuestrea (por lo general a 48 kHz).
- **Bluetooth**: el códec Bluetooth (LDAC, aptX, AAC o SBC) recomprime con pérdida.
  Ninguna app puede evitarlo.

## Cómo verificar
Con música sonando por el DAC, ejecuta:

    adb shell dumpsys media.audio_flinger

Busca el hilo de salida USB y revisa que su frecuencia y formato coincidan con el
archivo, y que tenga la marca de bit-perfect.

## Cola de sincronización
Las búsquedas de carátulas y la sincronización de letras pasan por una cola persistente
(`sync/`, con WorkManager): sigue aunque se cierre la app y solo trabaja con la red elegida
en Ajustes → Sincronización (solo Wi-Fi, solo datos o ambas). "Solo Wi-Fi" usa las redes
sin límite de datos; un punto de acceso Wi-Fi marcado como medido cuenta como datos.

## Limitaciones conocidas y próximos pasos
- **Cambio de frecuencia entre pistas.** Cuando la siguiente pista tiene otra frecuencia
  (por ejemplo, de 44,1 a 96 kHz), la reconfiguración se hace al cambiar de pista y
  puede competir con la creación del AudioTrack. Hay que probarlo con un DAC real. Si
  falla, conviene manejar la cola en el servicio y preparar cada pista después de
  configurar el mezclador.
- **Formato float vs. entero.** Si el DAC solo ofrece mezcladores enteros (24 o 32 bits)
  y Android no acepta la salida float en bit-perfect, hay que hacer que el AudioSink
  entregue PCM entero en el formato exacto del mezclador.
- **Archivos .m4a.** Pueden contener AAC (con pérdida). La app muestra el códec real
  al reproducir.
- **Gapless.** ExoPlayer lo maneja en MP3 con encabezado LAME y en FLAC; en ALAC
  depende de los metadatos del archivo.
