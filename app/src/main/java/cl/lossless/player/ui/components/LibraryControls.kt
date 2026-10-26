package cl.lossless.player.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import cl.lossless.player.R
import cl.lossless.player.library.FolderSort

/** Campo de búsqueda de una sola línea con botón para borrar. */
@Composable
fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Rounded.Search, null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.library_search_clear))
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
    )
}

private val FolderSort.labelRes: Int
    get() = when (this) {
        FolderSort.NAME_ASC -> R.string.library_sort_name_asc
        FolderSort.NAME_DESC -> R.string.library_sort_name_desc
        FolderSort.NEWEST -> R.string.library_sort_newest
        FolderSort.OLDEST -> R.string.library_sort_oldest
        FolderSort.MOST_TRACKS -> R.string.library_sort_most_tracks
    }

/** Botón con menú para elegir el orden de las carpetas. */
@Composable
fun FolderSortButton(selected: FolderSort, onSelect: (FolderSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = stringResource(R.string.library_sort))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            FolderSort.entries.forEach { sort ->
                DropdownMenuItem(
                    text = { Text(stringResource(sort.labelRes)) },
                    trailingIcon = { if (sort == selected) Icon(Icons.Rounded.Check, null) },
                    onClick = { open = false; onSelect(sort) },
                )
            }
        }
    }
}
