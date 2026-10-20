package cl.lossless.player.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cl.lossless.player.R
import cl.lossless.player.library.FolderExclusions
import cl.lossless.player.library.FolderSuggestion
import cl.lossless.player.library.FolderTreeNode
import cl.lossless.player.library.Track
import cl.lossless.player.library.folderSegments
import cl.lossless.player.library.folderTree
import cl.lossless.player.library.volumeLabel
import cl.lossless.player.ui.components.ScreenHeader

/** Una fila visible del árbol (aplanado según las carpetas expandidas). */
private data class TreeRow(val node: FolderTreeNode, val depth: Int)

/**
 * Elegir qué carpetas forman parte de la biblioteca. Marcada = incluida.
 * Omitir una carpeta omite también sus subcarpetas.
 */
@Composable
fun ExcludedFoldersScreen(
    allTracks: List<Track>,
    excluded: Set<String>,
    onBack: () -> Unit,
    onSetExcluded: (key: String, excluded: Boolean) -> Unit,
    onExcludeAll: (keys: List<String>) -> Unit,
    onClear: () -> Unit,
) {
    val tree = remember(allTracks) { allTracks.folderTree() }
    val suggestions = remember(allTracks, excluded) { FolderExclusions.suggestions(allTracks, excluded) }
    // Los volúmenes empiezan expandidos
    var expanded by rememberSaveable(tree) { mutableStateOf(tree.map { it.key }.toSet()) }
    val rows = remember(tree, expanded) { flatten(tree, expanded) }
    val hiddenTracks = remember(allTracks, excluded) {
        allTracks.count { FolderExclusions.isExcluded(it.folderSegments, excluded) }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            ScreenHeader(
                title = stringResource(R.string.settings_excluded_folders),
                onBack = onBack,
                actions = {
                    if (excluded.isNotEmpty()) TextButton(onClick = onClear) { Text(stringResource(R.string.settings_excluded_include_all)) }
                },
            )
            Text(
                if (excluded.isEmpty()) stringResource(R.string.settings_excluded_hint)
                else pluralStringResource(R.plurals.settings_excluded_folders_count, excluded.size, excluded.size) +
                    " · " + pluralStringResource(R.plurals.settings_hidden_tracks_count, hiddenTracks, hiddenTracks),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )

            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            ) {
                if (suggestions.isNotEmpty()) {
                    item(key = "suggestions") {
                        SuggestionsCard(suggestions, onExclude = { onSetExcluded(it, true) }, onExcludeAll = onExcludeAll)
                        Spacer(Modifier.height(16.dp))
                    }
                }
                item(key = "tree-title") {
                    Text(
                        stringResource(R.string.settings_excluded_all_folders),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                    )
                }
                items(rows, key = { it.node.key }) { row ->
                    val ownExcluded = row.node.key in excluded
                    // Omitida por una carpeta superior: no se puede marcar por separado.
                    val inheritedExcluded = !ownExcluded &&
                        FolderExclusions.isExcluded(row.node.segments, excluded)
                    FolderTreeRow(
                        row = row,
                        isExpanded = row.node.key in expanded,
                        included = !ownExcluded && !inheritedExcluded,
                        inheritedExcluded = inheritedExcluded,
                        onToggleExpand = {
                            expanded = if (row.node.key in expanded) expanded - row.node.key else expanded + row.node.key
                        },
                        onIncludedChange = { include -> onSetExcluded(row.node.key, !include) },
                    )
                }
            }
        }
    }
}

private fun flatten(nodes: List<FolderTreeNode>, expanded: Set<String>, depth: Int = 0): List<TreeRow> =
    nodes.flatMap { node ->
        listOf(TreeRow(node, depth)) +
            if (node.key in expanded) flatten(node.children, expanded, depth + 1) else emptyList()
    }

@Composable
private fun SuggestionsCard(
    suggestions: List<FolderSuggestion>,
    onExclude: (String) -> Unit,
    onExcludeAll: (List<String>) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Sms, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.settings_excluded_other_apps_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Text(
                stringResource(R.string.settings_excluded_other_apps_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
            suggestions.forEach { s ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            (s.appNameRes?.let { stringResource(it) } ?: s.appName) + " · " +
                                pluralStringResource(R.plurals.settings_tracks_count, s.trackCount, s.trackCount),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            s.displayPath,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.StartEllipsis,
                        )
                    }
                    TextButton(onClick = { onExclude(s.key) }) { Text(stringResource(R.string.settings_excluded_exclude)) }
                }
            }
            if (suggestions.size > 1) {
                FilledTonalButton(
                    onClick = { onExcludeAll(suggestions.map { it.key }) },
                    modifier = Modifier.align(Alignment.End).padding(top = 4.dp),
                ) {
                    Icon(Icons.Rounded.FolderOff, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.settings_excluded_exclude_all))
                }
            }
        }
    }
}

@Composable
private fun FolderTreeRow(
    row: TreeRow,
    isExpanded: Boolean,
    included: Boolean,
    inheritedExcluded: Boolean,
    onToggleExpand: () -> Unit,
    onIncludedChange: (Boolean) -> Unit,
) {
    val node = row.node
    val hasChildren = node.children.isNotEmpty()
    val name = if (row.depth == 0) volumeLabel(node.name) else node.name
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = hasChildren, role = Role.Button, onClick = onToggleExpand)
            .padding(start = (row.depth * 16).dp, top = 2.dp, bottom = 2.dp)
            .alpha(if (included) 1f else 0.55f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            if (hasChildren) {
                Icon(
                    if (isExpanded) Icons.Rounded.KeyboardArrowDown else Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = stringResource(if (isExpanded) R.string.settings_collapse else R.string.settings_expand),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Icon(
            if (included) Icons.Rounded.Folder else Icons.Rounded.FolderOff,
            null,
            tint = if (included) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(22.dp),
        )
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                when {
                    inheritedExcluded -> stringResource(R.string.settings_excluded_by_parent)
                    !included -> stringResource(
                        R.string.settings_excluded_folder_tracks,
                        pluralStringResource(R.plurals.settings_tracks_count, node.trackCount, node.trackCount),
                    )
                    else -> pluralStringResource(R.plurals.settings_tracks_count, node.trackCount, node.trackCount)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Checkbox(
            checked = included,
            onCheckedChange = onIncludedChange,
            enabled = !inheritedExcluded,
        )
    }
}
