package com.sitson.vocab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sitson.vocab.provider.ProviderConfig
import com.sitson.vocab.provider.ProviderConfigValidator
import com.sitson.vocab.provider.SecureProviderStore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val providerStore = SecureProviderStore(applicationContext)
        setContent { VocabApp(providerStore.load(), providerStore::save) }
    }
}

private enum class Screen { TODAY, PROVIDER_SETTINGS }

@Composable
fun VocabApp(initialConfig: ProviderConfig, onSaveConfig: (ProviderConfig) -> Unit) {
    var screen by remember { mutableStateOf(Screen.TODAY) }
    var providerConfig by remember { mutableStateOf(initialConfig) }
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            when (screen) {
                Screen.TODAY -> TodayScreen(onSettings = { screen = Screen.PROVIDER_SETTINGS })
                Screen.PROVIDER_SETTINGS -> ProviderSettingsScreen(
                    initialConfig = providerConfig,
                    onSave = {
                        onSaveConfig(it)
                        providerConfig = it
                        screen = Screen.TODAY
                    },
                    onBack = { screen = Screen.TODAY },
                )
            }
        }
    }
}

@Composable
private fun TodayScreen(onSettings: () -> Unit) {
    Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Today", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            TextButton(onClick = onSettings) { Text("AI settings") }
        }
        Text("Build understanding and active use separately.")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ProgressCard("Understand", "0 / 6", Modifier.weight(1f))
            ProgressCard("Use", "0 / 4", Modifier.weight(1f))
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Next review", style = MaterialTheme.typography.titleLarge)
                Text("The medicine produced a subtle improvement in his condition.")
                Text("Choose the meaning that best fits this context.")
            }
        }
        Button(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("Start learning") }
        Text("Works offline with cached learning material.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ProviderSettingsScreen(
    initialConfig: ProviderConfig,
    onSave: (ProviderConfig) -> Unit,
    onBack: () -> Unit,
) {
    var baseUrl by remember { mutableStateOf(initialConfig.baseUrl) }
    var model by remember { mutableStateOf(initialConfig.model) }
    var apiKey by remember { mutableStateOf(initialConfig.apiKey) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("AI provider", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text("Use any HTTPS provider that supports the OpenAI Chat Completions API.")
        OutlinedTextField(
            value = baseUrl, onValueChange = { baseUrl = it }, modifier = Modifier.fillMaxWidth(),
            label = { Text("Base URL") }, supportingText = { Text("Example: https://api.openai.com/v1") },
            singleLine = true,
        )
        OutlinedTextField(
            value = model, onValueChange = { model = it }, modifier = Modifier.fillMaxWidth(),
            label = { Text("Model") }, singleLine = true,
        )
        OutlinedTextField(
            value = apiKey, onValueChange = { apiKey = it }, modifier = Modifier.fillMaxWidth(),
            label = { Text("API key") }, visualTransformation = PasswordVisualTransformation(), singleLine = true,
        )
        Text(
            "The key is encrypted using Android Keystore and is never included in backup exports.",
            style = MaterialTheme.typography.bodySmall,
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = {
                val candidate = ProviderConfig(baseUrl, model, apiKey)
                error = ProviderConfigValidator.validate(candidate)
                if (error == null) onSave(candidate)
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save provider") }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
    }
}

@Composable
private fun ProgressCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(value, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AppPreview() = VocabApp(ProviderConfig(), {})
