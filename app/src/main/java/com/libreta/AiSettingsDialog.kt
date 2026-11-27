package com.libreta

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) Button(onClick = onClick) { Text(label, maxLines = 1) }
    else OutlinedButton(onClick = onClick) { Text(label, maxLines = 1) }
}

@Composable
fun AiSettingsDialog(initial: AiSettings, onSave: (AiSettings) -> Unit, onDismiss: () -> Unit) {
    var provider by remember { mutableStateOf(initial.provider) }
    var baseUrl by remember { mutableStateOf(initial.baseUrl) }
    var apiKey by remember { mutableStateOf(initial.apiKey) }
    var model by remember { mutableStateOf(initial.model) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Agente de IA") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Plantillas", fontSize = 12.sp, color = Color.Gray)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AiPresets.forEach { p ->
                        OutlinedButton(onClick = { provider = p.provider; baseUrl = p.baseUrl; model = p.model }) {
                            Text(p.name, maxLines = 1)
                        }
                    }
                }
                Text("Protocolo de la API", fontSize = 12.sp, color = Color.Gray)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Choice("Anthropic", provider == AiProvider.ANTHROPIC) { provider = AiProvider.ANTHROPIC }
                    Choice("Compatible OpenAI", provider == AiProvider.OPENAI) { provider = AiProvider.OPENAI }
                }
                OutlinedTextField(
                    value = baseUrl, onValueChange = { baseUrl = it }, singleLine = true,
                    label = { Text("URL base") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = model, onValueChange = { model = it }, singleLine = true,
                    label = { Text("Modelo (nombre exacto del proveedor)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = apiKey, onValueChange = { apiKey = it }, singleLine = true,
                    label = { Text("Clave de API") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "La clave se guarda solo en este dispositivo. Para modelos locales (Ollama, LM Studio) puede quedar vacía.",
                    fontSize = 11.sp, color = Color.Gray
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(AiSettings(provider, baseUrl.trim().trimEnd('/'), apiKey.trim(), model.trim()))
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
