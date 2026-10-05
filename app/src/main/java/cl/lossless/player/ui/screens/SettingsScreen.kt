package cl.lossless.player.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import cl.lossless.player.ui.components.FormatBadge
import cl.lossless.player.ui.components.ResuenLogo
import androidx.compose.ui.platform.LocalContext
import cl.lossless.player.ui.theme.AccentColor
import cl.lossless.player.ui.theme.isDark
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.runtime.remember
import cl.lossless.player.library.FolderExclusions
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cl.lossless.player.ui.LibraryUiState
import cl.lossless.player.ui.components.ScreenHeader
import cl.lossless.player.ui.theme.ThemeMode

@Composable
fun SettingsScreen(
    state: LibraryUiState,
    audioSource: String,
    audioOutput: String,
    onPickFolder: () -> Unit,
    onShowWholeLibrary: () -> Unit,
    onRescan: () -> Unit,
    onFetchCovers: () -> Unit,
    onCancelFetch: () -> Unit,
    onSaveCoversInFolder: (Boolean) -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
    onAccent: (AccentColor) -> Unit,
    onTintedText: (Boolean) -> Unit,
    equalizerSummary: String,
    onOpenEqualizer: () -> Unit,
    onOpenExclusions: () -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader(title = "Ajustes")
        BrandHeader()

        SettingsSection("Biblioteca", Icons.Rounded.Folder) {
            SettingRow(
                title = "Ubicación",
                subtitle = state.location?.displayPath ?: "Toda la música del dispositivo",
                monospace = true,
            )
            Row(Modifier.padding(horizontal = 16.dp)) {
                FilledTonalButton(onClick = onPickFolder) { Text("Elegir carpeta") }
                if (state.location != null) {
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = onShowWholeLibrary) { Text("Usar toda la música") }
                }
            }
            val suggestions = remember(state.allTracks, state.excludedFolders) {
                FolderExclusions.suggestions(state.allTracks, state.excludedFolders)
            }
            SettingRow(
                title = "Carpetas a omitir",
                subtitle = buildString {
                    append(if (state.excludedFolders.isEmpty()) "Ninguna" else "${state.excludedFolders.size} omitidas")
                    if (suggestions.isNotEmpty()) append(" · ${suggestions.size} de otras apps detectadas")
                },
                trailing = { Icon(Icons.Rounded.FolderOff, null) },
                onClick = onOpenExclusions,
            )
            SettingRow(
                title = "Volver a escanear",
                subtitle = "${state.tracks.size} pistas · ${state.albums.size} álbumes",
                trailing = { Icon(Icons.Rounded.Refresh, null) },
                onClick = onRescan,
            )
        }

        SettingsSection("Carátulas", Icons.Rounded.Image) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                if (state.coverTaskRunning) {
                    OutlinedButton(onClick = onCancelFetch) { Text("Detener búsqueda") }
                } else {
                    OutlinedButton(onClick = onFetchCovers, enabled = state.albums.isNotEmpty()) {
                        Text("Buscar carátulas faltantes")
                    }
                }
                state.coverTaskStatus?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (state.coverTaskRunning) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
            }
            SettingRow(
                title = "Guardar cover.jpg en la carpeta",
                subtitle = if (state.location == null) "Requiere elegir una carpeta" else "Además de la caché de la app",
                trailing = {
                    Switch(
                        checked = state.saveCoversInFolder,
                        onCheckedChange = onSaveCoversInFolder,
                        enabled = state.location != null,
                    )
                },
            )
        }

        SettingsSection("Apariencia", Icons.Rounded.Palette) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                ThemeMode.entries.forEachIndexed { i, mode ->
                    SegmentedButton(
                        selected = state.themeMode == mode,
                        onClick = { onThemeMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                    ) { Text(mode.label) }
                }
            }
            Text(
                "Color de acento · " + if (state.dynamicColor) "Material You" else state.accent.label,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
            )
            AccentPicker(
                selected = state.accent,
                dark = state.themeMode.isDark(),
                enabled = true,
                showSelection = !state.dynamicColor,
                onSelect = onAccent,
            )
            if (state.dynamicColor) {
                Text(
                    "Material You está activo: la app usa los colores de tu fondo de pantalla. " +
                        "Toca un color para usar tu propia paleta.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
                )
            }
            SettingRow(
                title = "Colorear textos",
                subtitle = if (state.dynamicColor) "Los títulos y subtítulos toman un matiz de tu fondo de pantalla"
                else "Los títulos y subtítulos toman un matiz del color de acento",
                trailing = {
                    Switch(checked = state.tintedText, onCheckedChange = onTintedText)
                },
            )
            AccentPreview(Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            SettingRow(
                title = "Colores dinámicos",
                subtitle = "Usa la paleta del fondo de pantalla (Material You); reemplaza el color de acento",
                trailing = { Switch(checked = state.dynamicColor, onCheckedChange = onDynamicColor) },
            )
        }

        SettingsSection("Audio", Icons.Rounded.GraphicEq) {
            SettingRow(
                title = "Ecualizador",
                subtitle = "$equalizerSummary · 10 bandas, preamp, graves y virtualizador",
                trailing = { Icon(Icons.Rounded.Equalizer, null) },
                onClick = onOpenEqualizer,
            )
            SettingRow(title = "Origen", subtitle = audioSource, monospace = true)
            SettingRow(
                title = "Salida",
                subtitle = audioOutput,
                monospace = true,
                trailing = if (audioOutput.startsWith("BIT-PERFECT")) {
                    { Icon(Icons.Rounded.Usb, null, tint = MaterialTheme.colorScheme.primary) }
                } else null,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Cabecera de marca: isotipo, nombre y versión. */
@Composable
private fun BrandHeader() {
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ResuenLogo(Modifier.size(76.dp))
        Column(Modifier.padding(start = 16.dp)) {
            Text("ResuenApp", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "Lossless Player" + (version?.let { " · v$it" } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsSection(title: String, icon: ImageVector, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(vertical = 12.dp)) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            content()
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String? = null,
    monospace: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let {
            { Text(it, fontFamily = if (monospace) FontFamily.Monospace else null) }
        },
        trailingContent = trailing,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
    )
}

/** Muestras circulares de los colores de acento. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccentPicker(
    selected: AccentColor,
    dark: Boolean,
    enabled: Boolean,
    showSelection: Boolean,
    onSelect: (AccentColor) -> Unit,
) {
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AccentColor.entries.forEach { accent ->
            val isSelected = showSelection && accent == selected
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(accent.swatch(dark).copy(alpha = if (enabled) 1f else 0.35f))
                    .border(
                        width = if (isSelected) 3.dp else 0.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                        shape = CircleShape,
                    )
                    .clickable(enabled = enabled) { onSelect(accent) }
                    .semantics { contentDescription = accent.label },
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        tint = if (dark) Color.Black else Color.White,
                    )
                }
            }
        }
    }
}

/** Vista previa en vivo: pestaña, carpeta y pista seleccionada con el tema actual. */
@Composable
private fun AccentPreview(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.background)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.clip(CircleShape).background(colors.primaryContainer).padding(horizontal = 14.dp, vertical = 6.dp)
            ) { Text("Pistas", color = colors.secondary, style = MaterialTheme.typography.labelLarge) }
            Text("Álbumes", color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.weight(1f))
            Icon(Icons.Rounded.Folder, null, tint = colors.primary)
        }
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = colors.primaryContainer,
            border = BorderStroke(1.dp, colors.primary),
        ) {
            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(colors.surfaceContainerLowest),
                    contentAlignment = Alignment.Center,
                ) { Text("01", color = colors.primary, fontWeight = FontWeight.Bold) }
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text("Pista seleccionada", color = colors.onPrimaryContainer, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Artista • Álbum",
                        color = colors.onPrimaryContainer.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                FormatBadge("FLAC 24-bit")
            }
        }
        Text("Texto de la app", color = colors.onSurface, style = MaterialTheme.typography.bodyMedium)
    }
}
