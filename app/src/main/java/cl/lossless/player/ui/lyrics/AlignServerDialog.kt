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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
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
        title = { Text("Servidor de sincronización") },
        text = {
            Column {
                Text(
                    "Calcula los tiempos exactos de cada palabra con WhisperX. Instálalo en tu PC o " +
                        "servidor (ver aligner/README.md en el repositorio). Deja la dirección vacía para no usarlo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = newUrl,
                    onValueChange = { newUrl = it; testResult = null },
                    label = { Text("Dirección") },
                    placeholder = { Text("http://192.168.1.20:8000") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = newToken,
                    onValueChange = { newToken = it; testResult = null },
                    label = { Text("Token") },
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
                ) { Text(if (testing) "Probando…" else "Probar conexión") }
                testResult?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(newUrl, newToken); onDismiss() }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
