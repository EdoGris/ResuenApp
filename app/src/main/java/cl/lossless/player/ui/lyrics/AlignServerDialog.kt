package cl.lossless.player.ui.lyrics

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import cl.lossless.player.R
import kotlinx.coroutines.launch

/** Dirección y token del servidor de alineación de letras (carpeta aligner/ del repositorio). */
@Composable
fun AlignServerDialog(
    url: String,
    token: String,
    onTest: suspend (url: String, token: String) -> String,
    onSave: (url: String, token: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var newUrl by remember { mutableStateOf(url) }
    var newToken by remember { mutableStateOf(token) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_align_dialog_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.settings_align_dialog_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = newUrl,
                    onValueChange = { newUrl = it; testResult = null },
                    label = { Text(stringResource(R.string.settings_align_dialog_address)) },
                    placeholder = { Text("http://192.168.1.20:8000") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = newToken,
                    onValueChange = { newToken = it; testResult = null },
                    label = { Text(stringResource(R.string.settings_align_dialog_token)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    enabled = newUrl.isNotBlank() && !testing,
                    onClick = {
                        testing = true
                        scope.launch {
                            testResult = onTest(newUrl.trim(), newToken.trim())
                            testing = false
                        }
                    },
                ) { Text(stringResource(if (testing) R.string.settings_align_dialog_testing else R.string.settings_align_dialog_test)) }
                testResult?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(newUrl, newToken); onDismiss() }) { Text(stringResource(R.string.settings_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) } },
    )
}
