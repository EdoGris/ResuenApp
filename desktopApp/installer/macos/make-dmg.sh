#!/bin/bash
# Arma la .dmg final a partir de ResuenApp.app: ícono de ResuenApp en el volumen, fondo con la
# marca y una ventana de Finder sin barras, del tamaño exacto del fondo (sin scroll).
# Uso: make-dmg.sh <ResuenApp.app> <salida.dmg>
set -euo pipefail

app="$1"
out="$2"
here="$(cd "$(dirname "$0")" && pwd)"
icons="$here/../../icons"
work="$(mktemp -d)"

# Fondo para pantallas normales y Retina en un solo .tiff (660 x 400 puntos).
tiffutil -cathidpicheck "$here/dmg-background.png" "$here/dmg-background@2x.png" -out "$work/background.tiff"

mkdir "$work/src"
ditto "$app" "$work/src/ResuenApp.app"
rm -f "$out"

# create-dmg acomoda la ventana con AppleScript (Finder). En los servidores de CI a veces
# Finder tarda en responder, así que se reintenta.
for attempt in 1 2 3; do
  if create-dmg \
      --volname "ResuenApp" \
      --volicon "$icons/resuenapp.icns" \
      --background "$work/background.tiff" \
      --window-pos 200 140 \
      --window-size 660 400 \
      --icon-size 128 \
      --text-size 13 \
      --icon "ResuenApp.app" 170 205 \
      --hide-extension "ResuenApp.app" \
      --app-drop-link 490 205 \
      --format UDZO \
      "$out" "$work/src"; then
    rm -rf "$work"
    exit 0
  fi
  echo "create-dmg falló (intento $attempt), reintentando…"
  rm -f "$out"
  sleep 5
done
exit 1
