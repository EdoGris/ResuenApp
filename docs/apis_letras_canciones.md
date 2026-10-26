# APIs Gratuitas de Letras de Canciones

Guía de referencia rápida con las mejores opciones de APIs públicas y gratuitas para integrar letras de canciones en aplicaciones y reproductores de música.

---

## 🚀 Principales Opciones Recomendadas

### 1. LRCLIB (Recomendada para Letras Sincronizadas)
Diseñada especialmente para desarrolladores de reproductores de música de código abierto.

*   **Endpoint Base:** `https://lrclib.net/api/`
*   **API Key / Registro:** ❌ No requiere.
*   **Formatos disponibles:** Texto plano, letras sincronizadas línea por línea (`LRC`) y palabra por palabra.
*   **Ideal para:** Aplicaciones con funciones de karaoke o scroll automatizado según el tiempo de reproducción.

### 2. Lyrics.ovh (La más simple)
Una API comunitaria muy directa e ideal para consultas rápidas de texto.

*   **Endpoint Base:** `https://api.lyrics.ovh/v1/`
*   **API Key / Registro:** ❌ No requiere.
*   **Formatos disponibles:** Solo texto plano.
*   **Ideal para:** Proyectos rápidos, bots de chat o aplicaciones sencillas que solo requieran mostrar la letra completa de una vez.

### 3. Genius API + Wrapper/Scraper (Mayor Cobertura)
La base de datos de música más grande del mundo. Su API oficial solo entrega metadatos por derechos de autor, por lo que requiere un "scraper" para extraer la letra.

*   **Plataforma Oficial:** https://genius.com/api-clients
*   **API Key / Registro:** ✔️ Requiere cuenta gratuita (Client Access Token).
*   **Formatos disponibles:** Texto plano con anotaciones de la comunidad.
*   **Librerías Populares:** 
    *   **Python:** `LyricsGenius` (vía pip)
    *   **JavaScript:** `genius-lyrics-api` (vía npm)

---

## 📊 Tabla Comparativa

| API | Requiere API Key | Tipo de Letra | Tipo de Licencia / Uso |
| :--- | :--- | :--- | :--- |
| **LRCLIB** | ❌ No | Plana y Sincronizada (LRC) | Gratuita (Uso no comercial) |
| **Lyrics.ovh** | ❌ No | Solo Texto Plano | Gratuita y Comunitaria |
| **Genius (+ Wrapper)** | ✔️ Sí | Solo Texto Plano | Gratuita para desarrolladores |

> ⚠️ **Nota sobre Musixmatch:** Su plan gratuito está fuertemente limitado y solo devuelve el 30% del texto a modo de vista previa. No se recomienda para consumo final gratuito.
