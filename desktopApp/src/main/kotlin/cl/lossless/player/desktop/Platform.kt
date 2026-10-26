package cl.lossless.player.desktop

/** Sistema en el que corre la app: cambia rutas, atajos y algunos textos. */
val IsMac: Boolean = System.getProperty("os.name").orEmpty().lowercase().contains("mac")
