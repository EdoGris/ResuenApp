# ResuenApp para macOS

La versión de escritorio corre también en Mac. El workflow **Compilar macOS** genera dos
imágenes de disco, una por tipo de procesador:

| Mac | Artefacto de GitHub Actions |
| --- | --- |
| Apple Silicon (M1, M2, M3, M4…) | `ResuenApp-macOS-apple-silicon` |
| Intel | `ResuenApp-macOS-intel` |

Para saber cuál tienes: menú Apple → *Acerca de este Mac*. Si dice "Chip Apple M…" es Apple
Silicon; si dice "Procesador Intel…", es Intel. Requiere macOS 11 (Big Sur) o posterior.

## Instalar

1. Descarga el artefacto desde la pestaña *Actions* del repositorio (ejecución de
   **Compilar macOS**) y descomprime el .zip: adentro está `ResuenApp-x.y.z-macOS-….dmg`.
2. Abre la .dmg y arrastra **ResuenApp** a la carpeta **Aplicaciones**.

## Abrirla la primera vez (app sin firma de Apple)

La app no está firmada ni notarizada por Apple (eso requiere una cuenta de Apple Developer,
99 USD al año), así que macOS la bloquea la primera vez:

1. Abre ResuenApp desde Aplicaciones. Aparece un aviso de que no se puede verificar;
   pulsa **Aceptar** (o **Listo**).
2. Ve a **Ajustes del Sistema → Privacidad y seguridad**, baja hasta *Seguridad* y pulsa
   **Abrir igualmente** junto al mensaje sobre ResuenApp. Confirma con tu contraseña o Touch ID.
3. Desde entonces se abre normalmente.

En macOS 14 o anterior también funciona: clic derecho (o Control + clic) sobre la app →
**Abrir** → **Abrir**.

Si macOS dice que la app "está dañada y no se puede abrir", quítale la marca de descarga
desde la Terminal y vuelve a abrirla:

    xattr -dr com.apple.quarantine /Applications/ResuenApp.app

## Qué cambia respecto de Windows

- Los ajustes y la biblioteca se guardan en `~/Library/Application Support/ResuenApp`.
- La búsqueda se abre con **⌘F** y las carpetas se eligen con el selector de Finder.
- La salida es Core Audio en modo compartido: la decodificación es sin pérdida, pero macOS
  remuestrea si la frecuencia del archivo no coincide con la configurada para el dispositivo
  (aplicación *Configuración de Audio MIDI*). El modo exclusivo (hog mode) para bit-perfect
  queda para una versión posterior.

## Firmarla más adelante

Con una cuenta de Apple Developer se puede firmar y notarizar en el mismo workflow
(`macOS { signing { … }; notarization { … } }` del plugin de Compose, con el certificado y
la contraseña de aplicación guardados como secretos del repositorio). Así desaparece el aviso
de Gatekeeper.
