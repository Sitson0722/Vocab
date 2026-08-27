package com.sitson.vocab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sitson.vocab.data.StudyItem
import com.sitson.vocab.data.WordImporter
import com.sitson.vocab.provider.ProviderConfig
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { VocabApp() }
    }
}

private enum class AppTab(val label: String) { TODAY("Today"), WORDS("Words"), STATS("Statistics"), SETTINGS("Settings") }

@Composable
fun VocabApp(vm: AppViewModel = viewModel()) {
    var tab by remember { mutableStateOf(AppTab.TODAY) }
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            if (vm.currentItem != null) StudyScreen(vm) else Scaffold(
                bottomBar = {
                    NavigationBar {
                        AppTab.entries.forEach { item ->
                            NavigationBarItem(
                                selected = tab == item, onClick = { tab = item },
                                icon = { Text(item.label.take(1), fontWeight = FontWeight.Bold) },
                                label = { Text(item.label) },
                            )
                        }
                    }
                },
            ) { padding ->
                Column(Modifier.padding(padding).fillMaxSize()) {
                    vm.message?.let { Notice(it, vm::clearMessage) }
                    when (tab) {
                        AppTab.TODAY -> TodayScreen(vm, onAddWords = { tab = AppTab.WORDS })
                        AppTab.WORDS -> WordsScreen(vm)
                        AppTab.STATS -> StatisticsScreen(vm)
                        AppTab.SETTINGS -> ProviderSettingsScreen(vm)
                    }
                }
            }
        }
    }
}

@Composable
private fun Notice(message: String, dismiss: () -> Unit) {
    Card(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()) {
        Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(message, Modifier.weight(1f)); TextButton(onClick = dismiss) { Text("Dismiss") }
        }
    }
}

@Composable
private fun TodayScreen(vm: AppViewModel, onAddWords: () -> Unit) {
    val stats = vm.statistics
    var reviewQuantity by remember { mutableStateOf("20") }
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Today", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text("Train the weakest knowledge at the moment it becomes useful to retrieve.")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard("Due", stats.due.toString(), Modifier.weight(1f))
            MetricCard("Senses", stats.words.toString(), Modifier.weight(1f))
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Review", style = MaterialTheme.typography.titleLarge)
                Text("Review at any time. The weakest predicted memories come first; ${stats.due} are currently below the target retention schedule.")
                OutlinedTextField(
                    value = reviewQuantity,
                    onValueChange = { reviewQuantity = it.filter(Char::isDigit).take(3) },
                    modifier = Modifier.fillMaxWidth(), label = { Text("How many to review") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true,
                )
                Button(
                    onClick = { vm.startSession(true, reviewQuantity.toIntOrNull() ?: 20) },
                    enabled = stats.attempts > 0 && (reviewQuantity.toIntOrNull() ?: 0) > 0,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Start review now") }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Learn new words", style = MaterialTheme.typography.titleLarge)
                Text("Introduces unpractised senses in context, then asks you to produce the word.")
                Button(onClick = { vm.startSession(false) }, enabled = stats.words > 0, modifier = Modifier.fillMaxWidth()) { Text("Start new learning") }
            }
        }
        if (stats.words == 0) OutlinedButton(onClick = onAddWords, modifier = Modifier.fillMaxWidth()) { Text("Add your first words") }
    }
}

@Composable
private fun WordsScreen(vm: AppViewModel) {
    var showImport by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column { Text("Words", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold); Text("${vm.words.size} distinct senses") }
            Button(onClick = { showImport = true }) { Text("Add") }
        }
        Spacer(Modifier.height(12.dp))
        if (vm.words.isEmpty()) Text("No vocabulary yet. Tap Add to paste a list or extract words with AI.")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(vm.words, key = { it.id }) { word ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(word.term, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(word.phonetic, color = MaterialTheme.colorScheme.secondary)
                        Text(word.definition)
                        Text(word.phrase, color = MaterialTheme.colorScheme.primary)
                        Text(word.example, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
    if (showImport) ImportDialog(vm, onDismiss = { showImport = false })
}

@Composable
private fun ImportDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!vm.busy) onDismiss() },
        title = { Text("Import vocabulary") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Offline fixed format — one sense per line:")
                Text(WordImporter.FORMAT_HELP, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth(),
                    minLines = 7, label = { Text("Vocabulary list or any source text") },
                    placeholder = { Text("subtle | /ˈsʌtəl/ | 细微而不易察觉的 | subtle difference | There is a subtle difference between them.") },
                )
                Text("Fixed-format import stays offline. AI import sends this text to your configured provider and extracts separate senses, collocations, and examples.", style = MaterialTheme.typography.bodySmall)
                if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.importFixed(text); onDismiss() }, enabled = !vm.busy) { Text("Import fixed") }
                Button(onClick = { vm.importWithAi(text) }, enabled = !vm.busy) { Text("Import with AI") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !vm.busy) { Text("Close") } },
    )
}

@Composable
private fun StudyScreen(vm: AppViewModel) {
    val item = vm.currentItem ?: return
    var answer by remember(item.id, item.dimension) { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (item.dimension == "COMPREHENSION") "Understand" else "Active recall", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = vm::leaveSession) { Text("Exit") }
        }
        LinearProgressIndicator(progress = { (vm.sessionIndex + 1f) / vm.session.size }, modifier = Modifier.fillMaxWidth())
        Text("${vm.sessionIndex + 1} of ${vm.session.size}")
        if (item.dimension == "COMPREHENSION" || vm.feedback != null) {
            Text("${item.term}  ${item.phonetic}", style = MaterialTheme.typography.titleMedium)
        }
        StudyPrompt(item, vm, answer, { answer = it })
        if (vm.hints > 0 && vm.feedback == null) {
            Text("Hint: starts with “${item.term.first()}”; common pattern: ${item.phrase.replace(item.term, "____", ignoreCase = true)}")
        }
        if (vm.feedback == null) {
            if (item.dimension == "PRODUCTION") Button(onClick = { vm.submit(answer) }, enabled = answer.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Check answer") }
            OutlinedButton(onClick = vm::showHint, modifier = Modifier.fillMaxWidth()) { Text(if (vm.hints == 0) "Show hint" else "Another hint") }
        } else {
            Card(Modifier.fillMaxWidth()) { Text(vm.feedback!!, Modifier.padding(16.dp)) }
            Button(onClick = vm::nextQuestion, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
        }
    }
}

@Composable
private fun StudyPrompt(item: StudyItem, vm: AppViewModel, answer: String, onAnswer: (String) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (item.dimension == "COMPREHENSION") {
                Text(item.example, style = MaterialTheme.typography.titleLarge)
                Text("What does “${item.term}” mean here?")
                val choices = remember(item.id, vm.words) {
                    (vm.words.map { it.definition }.filter { it != item.definition }.shuffled().take(2) + item.definition).shuffled()
                }
                choices.forEach { choice -> OutlinedButton(onClick = { vm.submit(choice) }, enabled = vm.feedback == null, modifier = Modifier.fillMaxWidth()) { Text(choice) } }
            } else {
                Text(item.definition, style = MaterialTheme.typography.titleLarge)
                Text("Recall the English word. Context: ${item.example.replace(item.term, "____", ignoreCase = true)}")
                OutlinedTextField(value = answer, onValueChange = onAnswer, modifier = Modifier.fillMaxWidth(), label = { Text("Your answer") }, singleLine = true)
            }
        }
    }
}

@Composable
private fun StatisticsScreen(vm: AppViewModel) {
    val s = vm.statistics; val accuracy = if (s.attempts == 0) 0 else (s.correct * 100.0 / s.attempts).roundToInt()
    Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Statistics", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard("Attempts", s.attempts.toString(), Modifier.weight(1f)); MetricCard("Accuracy", "$accuracy%", Modifier.weight(1f))
        }
        StrengthCard("Comprehension", s.comprehensionStrength)
        StrengthCard("Production", s.productionStrength)
        Text("Strength is shown in estimated stable days. The two dimensions change only from their own practice evidence.")
    }
}

@Composable private fun StrengthCard(label: String, strength: Double) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp)) { Text(label, style = MaterialTheme.typography.titleLarge); Text("%.1f stable days".format(strength), style = MaterialTheme.typography.headlineMedium) } }
}

@Composable private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) { Column(Modifier.padding(16.dp)) { Text(label); Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) } }
}

@Composable
private fun ProviderSettingsScreen(vm: AppViewModel) {
    var baseUrl by remember(vm.providerConfig) { mutableStateOf(vm.providerConfig.baseUrl) }
    var model by remember(vm.providerConfig) { mutableStateOf(vm.providerConfig.model) }
    var key by remember(vm.providerConfig) { mutableStateOf(vm.providerConfig.apiKey) }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text("OpenAI-compatible provider")
        OutlinedTextField(baseUrl, { baseUrl = it }, Modifier.fillMaxWidth(), label = { Text("HTTPS base URL") }, singleLine = true)
        OutlinedTextField(model, { model = it }, Modifier.fillMaxWidth(), label = { Text("Model") }, singleLine = true)
        OutlinedTextField(key, { key = it }, Modifier.fillMaxWidth(), label = { Text("API key") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
        Text("The key is encrypted with Android Keystore and excluded from backups.", style = MaterialTheme.typography.bodySmall)
        Button(onClick = { vm.saveProvider(ProviderConfig(baseUrl, model, key)) }, modifier = Modifier.fillMaxWidth()) { Text("Save provider") }
    }
}
