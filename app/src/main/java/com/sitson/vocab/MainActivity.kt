package com.sitson.vocab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
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
import com.sitson.vocab.domain.GraduationPolicy
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { VocabApp() }
    }
}

private enum class AppTab(val label: String) { TODAY("Today"), WORDS("Words"), STATS("Statistics"), SETTINGS("Settings") }
private enum class DimensionOption(val key: String, val label: String) {
    ADAPTIVE("", "Adaptive"), CONTEXT("CONTEXT_COMPREHENSION", "New context"),
    ISOLATED("ISOLATED_MEANING", "Word alone"), PRODUCTION("PRODUCTION", "Production"),
}

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
    var reviewStyle by remember { mutableStateOf("") }
    var reviewDimension by remember { mutableStateOf(DimensionOption.ADAPTIVE) }
    var reviewMode by remember { mutableStateOf(InteractionMode.FLASHCARD) }
    var learnDimension by remember { mutableStateOf(DimensionOption.ADAPTIVE) }
    var learnMode by remember { mutableStateOf(InteractionMode.FLASHCARD) }
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
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Daily mixed plan", style = MaterialTheme.typography.titleLarge)
                Text("Fills your quota with highest-priority reviews first, then new items. Uses the review settings below.")
                Button(
                    onClick = { vm.startDailyPlan(reviewQuantity.toIntOrNull() ?: 20, reviewStyle.trim(), reviewDimension.key, reviewMode) },
                    enabled = stats.words > 0 && (reviewQuantity.toIntOrNull() ?: 0) > 0,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Start daily plan") }
            }
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
                OutlinedTextField(
                    value = reviewStyle, onValueChange = { reviewStyle = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Material style (optional)") }, placeholder = { Text("science, romance…") }, singleLine = true,
                )
                if (vm.materialStyles.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        vm.materialStyles.take(3).forEach { style -> TextButton(onClick = { reviewStyle = style }) { Text(style) } }
                    }
                }
                SessionOptions(reviewDimension, { reviewDimension = it }, reviewMode, { reviewMode = it })
                Button(
                    onClick = { vm.startSession(true, reviewQuantity.toIntOrNull() ?: 20, reviewStyle.trim(), reviewDimension.key, reviewMode) },
                    enabled = stats.attempts > 0 && (reviewQuantity.toIntOrNull() ?: 0) > 0,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Start review now") }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Learn new words", style = MaterialTheme.typography.titleLarge)
                Text("Introduces unpractised senses in context, then asks you to produce the word.")
                SessionOptions(learnDimension, { learnDimension = it }, learnMode, { learnMode = it })
                Button(onClick = { vm.startSession(false, dimension = learnDimension.key, mode = learnMode) }, enabled = stats.words > 0, modifier = Modifier.fillMaxWidth()) { Text("Start new learning") }
            }
        }
        if (stats.words == 0) OutlinedButton(onClick = onAddWords, modifier = Modifier.fillMaxWidth()) { Text("Add your first words") }
    }
}

@Composable
private fun SessionOptions(
    dimension: DimensionOption,
    onDimension: (DimensionOption) -> Unit,
    mode: InteractionMode,
    onMode: (InteractionMode) -> Unit,
) {
    Text("Dimension", style = MaterialTheme.typography.labelLarge)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DimensionOption.entries.take(2).forEach { option -> FilterChip(selected = dimension == option, onClick = { onDimension(option) }, label = { Text(option.label) }) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DimensionOption.entries.drop(2).forEach { option -> FilterChip(selected = dimension == option, onClick = { onDimension(option) }, label = { Text(option.label) }) }
        }
    }
    Text("Interaction", style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(selected = mode == InteractionMode.FLASHCARD, onClick = { onMode(InteractionMode.FLASHCARD) }, label = { Text("Flashcard") })
        FilterChip(selected = mode == InteractionMode.BILINGUAL, onClick = { onMode(InteractionMode.BILINGUAL) }, label = { Text("English ↔ Chinese") })
    }
}

@Composable
private fun WordsScreen(vm: AppViewModel) {
    var showImport by remember { mutableStateOf(false) }
    var showRefresh by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column { Text("Words", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold); Text("${vm.words.size} distinct senses") }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { showRefresh = true }, enabled = vm.words.isNotEmpty()) { Text("Refresh") }
                Button(onClick = { showImport = true }) { Text("Add") }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (vm.words.isEmpty()) Text("No vocabulary yet. Tap Add to paste a list or extract words with AI.")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(vm.words, key = { it.id }) { word ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(word.term, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(if (word.status == "MASTERED") "Mastered" else "Learning", color = if (word.status == "MASTERED") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary)
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
    if (showRefresh) RefreshMaterialsDialog(vm, onDismiss = { showRefresh = false })
}

@Composable
private fun ImportDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var style by remember { mutableStateOf("general") }
    AlertDialog(
        onDismissRequest = { if (!vm.busy) onDismiss() },
        title = { Text("Import vocabulary") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Offline fixed format — one sense per line. Definition must be Chinese:")
                Text(WordImporter.FORMAT_HELP, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth(),
                    minLines = 7, label = { Text("Vocabulary list or any source text") },
                    placeholder = { Text("subtle | /ˈsʌtəl/ | 细微而不易察觉的 | subtle difference | There is a subtle difference between them.") },
                )
                OutlinedTextField(style, { style = it }, Modifier.fillMaxWidth(), label = { Text("Material style") }, placeholder = { Text("science, romance, academic…") }, singleLine = true)
                Text("Fixed-format import stays offline. AI import sends this text to your configured provider and extracts separate senses, collocations, and examples.", style = MaterialTheme.typography.bodySmall)
                if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.importFixed(text, style); onDismiss() }, enabled = !vm.busy) { Text("Import fixed") }
                Button(onClick = { vm.importWithAi(text, style) }, enabled = !vm.busy) { Text("Import with AI") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !vm.busy) { Text("Close") } },
    )
}

@Composable
private fun RefreshMaterialsDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    var style by remember { mutableStateOf("general") }
    AlertDialog(
        onDismissRequest = { if (!vm.busy) onDismiss() },
        title = { Text("Refresh material library") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("AI will add new sentences, phrases, collocations, and diagnostic proper nouns. Existing material and usage history are preserved.")
            OutlinedTextField(style, { style = it }, Modifier.fillMaxWidth(), label = { Text("Style keywords") }, placeholder = { Text("science, romance…") }, singleLine = true)
            if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        } },
        confirmButton = { Button(onClick = { vm.refreshMaterials(style) }, enabled = !vm.busy && style.isNotBlank()) { Text("Generate") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !vm.busy) { Text("Close") } },
    )
}

@Composable
private fun StudyScreen(vm: AppViewModel) {
    val item = vm.currentItem ?: return
    var revealed by remember(item.id, item.dimension, vm.interactionMode) { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(when (item.dimension) {
                "CONTEXT_COMPREHENSION" -> "Understand in a new context"
                "ISOLATED_MEANING" -> "Know the word itself"
                else -> "Active recall"
            }, style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = vm::leaveSession) { Text("Exit") }
        }
        LinearProgressIndicator(progress = { (vm.sessionIndex + 1f) / vm.session.size }, modifier = Modifier.fillMaxWidth())
        Text("${vm.sessionIndex + 1} of ${vm.session.size}")
        if (item.dimension != "PRODUCTION" || vm.feedback != null) {
            Text("${item.term}  ${item.phonetic}", style = MaterialTheme.typography.titleMedium)
        }
        StudyPrompt(item, vm.interactionMode, revealed, onToggle = { revealed = !revealed })
        if (vm.feedback == null) {
            Text("Self-grade from memory. You may reveal the back first, or grade without revealing.")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.selfGrade("AGAIN") }, enabled = !vm.grading, modifier = Modifier.weight(1f)) { Text("不会") }
                OutlinedButton(onClick = { vm.selfGrade("HARD") }, enabled = !vm.grading, modifier = Modifier.weight(1f)) { Text("困难") }
                Button(onClick = { vm.selfGrade("KNOW") }, enabled = !vm.grading, modifier = Modifier.weight(1f)) { Text("会") }
            }
            TextButton(onClick = vm::skipCard, enabled = !vm.grading, modifier = Modifier.fillMaxWidth()) { Text("跳过（不改变进度）") }
        } else {
            Card(Modifier.fillMaxWidth()) { Text(vm.feedback!!, Modifier.padding(16.dp)) }
            Button(onClick = vm::nextQuestion, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
        }
    }
}

@Composable
private fun StudyPrompt(item: StudyItem, mode: InteractionMode, revealed: Boolean, onToggle: () -> Unit) {
    Card(Modifier.fillMaxWidth().height(280.dp)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).clickable(onClick = onToggle).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (mode == InteractionMode.BILINGUAL) "English ↔ Chinese" else item.materialType.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelLarge)
            if (!revealed) {
                if (mode == InteractionMode.BILINGUAL) {
                    if (item.dimension == "PRODUCTION") {
                        Text("中文释义", style = MaterialTheme.typography.labelLarge)
                        Text(item.definition, style = MaterialTheme.typography.headlineMedium)
                        Text("回忆对应的英文单词或短语。")
                    } else {
                        Text(item.term, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                        Text(item.phonetic, style = MaterialTheme.typography.titleLarge)
                        Text("回忆对应的中文释义。")
                    }
                } else when (item.dimension) {
                    "CONTEXT_COMPREHENSION" -> {
                        Text(item.materialContent, style = MaterialTheme.typography.titleLarge)
                        Text("What does “${item.term}” mean in this new context?")
                    }
                    "ISOLATED_MEANING" -> {
                        Text(item.term, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                        Text(item.phonetic)
                        Text("Recall its meaning without contextual help.")
                    }
                    else -> {
                        Text(item.definition, style = MaterialTheme.typography.headlineMedium)
                        if (mode == InteractionMode.FLASHCARD) Text("Clue: ${item.materialContent.replace(item.term, "____", ignoreCase = true)}")
                        Text("Recall the English word or phrase.")
                    }
                }
                Spacer(Modifier.height(12.dp)); Text("Tap card to reveal answer · Swipe for long text", color = MaterialTheme.colorScheme.primary)
            } else {
                if (mode == InteractionMode.BILINGUAL && item.dimension != "PRODUCTION") {
                    Text("中文释义", style = MaterialTheme.typography.labelLarge)
                    Text(item.definition, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("${item.term}  ${item.phonetic}")
                    Text(item.phrase, color = MaterialTheme.colorScheme.primary)
                } else {
                    Text("${item.term}  ${item.phonetic}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    if (mode == InteractionMode.BILINGUAL) Text("英文答案", style = MaterialTheme.typography.labelLarge)
                    Text(item.definition, style = MaterialTheme.typography.titleLarge)
                    Text(item.phrase, color = MaterialTheme.colorScheme.primary)
                    if (mode == InteractionMode.FLASHCARD) Text(item.materialExplanation.ifBlank { item.example })
                }
                Spacer(Modifier.height(12.dp)); Text("Tap card to hide answer · Swipe for long text", color = MaterialTheme.colorScheme.primary)
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
        MetricCard("Mastered senses", "${s.mastered} / ${s.words}", Modifier.fillMaxWidth())
        StrengthCard("New-context comprehension", s.contextualStrength, s.contextualMastery, s.contextualRetention)
        StrengthCard("Isolated word meaning", s.isolatedStrength, s.isolatedMastery, s.isolatedRetention)
        StrengthCard("Active production", s.productionStrength, s.productionMastery, s.productionRetention)
        Text("Memory rate decays with time. Mastery additionally requires successful evidence across different materials; each dimension changes independently.")
    }
}

@Composable private fun StrengthCard(label: String, strength: Double, mastery: Double, retention: Double) {
    val level = GraduationPolicy.level(mastery, strength)
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp)) { Text("$label · L$level", style = MaterialTheme.typography.titleLarge); Text("Memory now: ${(retention * 100).roundToInt()}%", style = MaterialTheme.typography.headlineMedium); Text("Mastery: ${(mastery * 100).roundToInt()}% · ${"%.1f".format(strength)} stable days") } }
}

@Composable private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) { Column(Modifier.padding(16.dp)) { Text(label); Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) } }
}

@Composable
private fun ProviderSettingsScreen(vm: AppViewModel) {
    var baseUrl by remember(vm.providerConfig) { mutableStateOf(vm.providerConfig.baseUrl) }
    var model by remember(vm.providerConfig) { mutableStateOf(vm.providerConfig.model) }
    var key by remember(vm.providerConfig) { mutableStateOf(vm.providerConfig.apiKey) }
    var confirmRestore by remember { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(vm::exportBackup)
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::restoreBackup)
    }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text("OpenAI-compatible provider")
        OutlinedTextField(baseUrl, { baseUrl = it }, Modifier.fillMaxWidth(), label = { Text("HTTPS base URL") }, singleLine = true)
        OutlinedTextField(model, { model = it }, Modifier.fillMaxWidth(), label = { Text("Model") }, singleLine = true)
        OutlinedTextField(key, { key = it }, Modifier.fillMaxWidth(), label = { Text("API key") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
        Text("The key is encrypted with Android Keystore on this device. Full exports include it in plain text so another installation can restore it.", style = MaterialTheme.typography.bodySmall)
        Button(onClick = { vm.saveProvider(ProviderConfig(baseUrl, model, key)) }, modifier = Modifier.fillMaxWidth()) { Text("Save provider") }
        Text("Backup & restore", style = MaterialTheme.typography.titleLarge)
        Text("A full JSON backup includes vocabulary, learning progress, materials, usage logs, attempts, provider settings, and the API key.")
        OutlinedButton(
            onClick = { exportLauncher.launch("vocab-full-backup.json") },
            enabled = !vm.busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Export full backup") }
        Button(onClick = { confirmRestore = true }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) { Text("Restore from backup") }
        Text("Warning: exported API keys are plain text inside the backup file. Keep it private.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
    if (confirmRestore) AlertDialog(
        onDismissRequest = { confirmRestore = false },
        title = { Text("Replace all current data?") },
        text = { Text("A valid backup will replace all vocabulary data, learning history, materials, and provider credentials. The operation is transactional.") },
        confirmButton = { Button(onClick = { confirmRestore = false; restoreLauncher.launch(arrayOf("application/json", "text/plain")) }) { Text("Choose backup") } },
        dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("Cancel") } },
    )
}
