# Avisos de terceros

## Lyricslay

Partes de la función de letras sincronizadas están basadas en
[Lyricslay](https://github.com/Arzatar/Lyricslay):

- `app/src/main/java/cl/lossless/player/lyrics/LrcParser.kt` — portado de `src/lrclib.js`
  (lectura de LRC y descarte de la segunda copia duplicada).
- `app/src/main/java/cl/lossless/player/lyrics/TrackMetadataCleaner.kt` — portado de
  `src/trackMetadata.js` (limpieza de título y artista).
- La estrategia de búsqueda en LRCLIB (exacta con duración y luego aproximada,
  prefiriendo letras sincronizadas).

```
MIT License

Copyright (c) 2026 Arzatar

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## LRCLIB

Las letras en línea provienen de [LRCLIB](https://lrclib.net), una base de datos
comunitaria y gratuita de letras.
