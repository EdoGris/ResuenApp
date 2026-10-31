# Fuentes de letras: evaluación y decisión

Evaluación de las APIs listadas en [apis_letras_canciones.md](apis_letras_canciones.md)
(y de otras alternativas) como fuentes en línea para ResuenApp. Octubre 2026.

## Cómo busca letras la app

`LyricsRepository` va de lo más fiel a lo menos:

1. Archivo `.lrc` junto al audio.
2. Sincronización hecha por el usuario ("Sincronizar tocando").
3. Letra incrustada en las etiquetas.
4. Caché de descargas anteriores ("sin letra" se recuerda 7 días).
5. **LRCLIB**: búsqueda exacta (título, artista, duración ±2 s) y luego aproximada.
6. **Lyrics.ovh** (nuevo): solo texto plano, si LRCLIB no tiene nada o no responde.

Una letra sin tiempos (de 3, 6 o de LRCLIB) se sincroniza después con la estimación en
el teléfono o con el servidor de alineación (WhisperX), así que una fuente de solo texto
igual termina mostrándose con scroll sincronizado.

## Evaluación

| Fuente | Clave | Tipo | Riesgo legal / ToS | Fiabilidad | Decisión |
| :-- | :-- | :-- | :-- | :-- | :-- |
| LRCLIB | No | Sincronizada (LRC, a veces por palabra) y plana | Bajo: pensada para reproductores, contenido comunitario | Buena; a veces saturada (la app reintenta con Retry-After) | Ya integrada, fuente principal |
| Lyrics.ovh | No | Solo plana | Medio: API no oficial que reúne letras de otros sitios | Irregular: a veces lenta o caída | **Integrada como respaldo**, sin reintentos y con tiempos cortos |
| Genius (API oficial) | Sí (token) | Solo metadatos (título, URL, portada) | La API **no entrega la letra**; extraerla raspando genius.com va contra sus condiciones de uso | El HTML cambia y rompe los scrapers | **No integrar**. Además el token quedaría dentro del APK |
| Musixmatch | Sí | 30 % de la letra en el plan gratis | Licenciada, pero el plan gratis no sirve para mostrar letras completas | Buena | No integrar |
| NetEase Cloud Music (API no oficial) | No | Sincronizada (LRC), mucho catálogo asiático y occidental | Alto: API interna no documentada, sin permiso explícito | Cambia sin aviso, puede bloquear por región | Solo si algún día se quiere una opción "experimental" desactivada por defecto |
| ChartLyrics | — | — | — | Cerrada | Descartada |

## Por qué no Genius

Genius tiene la mayor cobertura, pero su API pública solo devuelve metadatos por temas de
derechos de autor; las librerías como `LyricsGenius` obtienen la letra descargando la
página web y extrayendo el texto. Eso:

- Contradice las condiciones de uso de Genius (prohíben el acceso automatizado al sitio).
- Obliga a meter un token en la app (no se deben guardar claves en el repo ni en el APK).
- Se rompe cada vez que Genius cambia su HTML.

Si en el futuro se quiere, la alternativa limpia es usar la API solo para **buscar** la
canción y ofrecer un botón "Ver en Genius" que abra la página en el navegador, con un
token que el usuario pegue en Ajustes.

## Detalles de la integración de Lyrics.ovh

- `LyricsOvhClient.kt`: `GET https://api.lyrics.ovh/v1/{artista}/{título}` con los mismos
  título y artista limpios que se usan para LRCLIB (`TrackMetadataCleaner`).
- Necesita artista: sin artista no se consulta.
- Limpia el encabezado "Paroles de la chanson … par …" que trae a veces, unifica saltos de
  línea y deja como mucho una línea en blanco seguida.
- Sin reintentos: si falla, la app sigue sin letra y **no** guarda "sin letra" en la caché,
  para volver a intentar la próxima vez.
- Si LRCLIB falló y Lyrics.ovh tampoco encontró nada, se muestra el error de LRCLIB (como
  antes).
- La letra aparece con la fuente "Lyrics.ovh".
- No usa APIs de Android salvo `org.json` (igual que `LrcLibClient`), así que se puede mover
  a un módulo compartido de Kotlin Multiplatform cambiando solo el transporte HTTP y el JSON.

Las pistas que quedaron marcadas "sin letra" antes de esta versión se vuelven a buscar al
vencer la caché (7 días), con "Buscar de nuevo", o borrando la caché de letras en Ajustes.
