package com.sitson.vocab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import com.sitson.vocab.data.LearningCard
import com.sitson.vocab.data.WordImporter
import com.sitson.vocab.domain.*
import com.sitson.vocab.provider.ProviderConfig

class MainActivity : ComponentActivity() {
    private lateinit var vm: AppViewModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vm = ViewModelProvider(this)[AppViewModel::class.java]
        enableEdgeToEdge()
        setContent { VocabApp(vm) }
    }
    override fun onResume() { super.onResume(); if (::vm.isInitialized) vm.setForeground(true) }
    override fun onPause() { if (::vm.isInitialized) vm.setForeground(false); super.onPause() }
}

private enum class AppTab(val label: String, val symbol: String) { TODAY("今天", "◷"), WORDS("词库", "▤"), MINE("我的", "○") }
private val LightColors = lightColorScheme(primary = Color(0xFF28624B), secondary = Color(0xFF60784E), background = Color(0xFFFAFBF7), surface = Color(0xFFFAFBF7), surfaceVariant = Color(0xFFEAF1DF))
private val DarkColors = darkColorScheme(primary = Color(0xFFACD299), secondary = Color(0xFFB9CCA6), background = Color(0xFF1E2E26), surface = Color(0xFF1E2E26), surfaceVariant = Color(0xFF354B34))

@Composable
fun VocabApp(vm: AppViewModel) {
    var tab by rememberSaveable { mutableStateOf(AppTab.TODAY) }
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors) {
        BackHandler(enabled = vm.studyOpen) { vm.pause() }
        Scaffold(bottomBar = {
            if (!vm.studyOpen) NavigationBar {
                AppTab.entries.forEach { t -> NavigationBarItem(selected = tab == t, onClick = { tab = t },
                    icon = { Text(t.symbol) }, label = { Text(t.label) }) }
            }
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
                vm.message?.let { Notice(it, vm::clearMessage) }
                when {
                    !vm.ready -> Column(Modifier.padding(24.dp)) { Text("正在读取学习记录…"); LinearProgressIndicator(Modifier.fillMaxWidth()) }
                    vm.studyOpen -> if (vm.card != null) StudyScreen(vm) else SummaryScreen(vm)
                    tab == AppTab.TODAY -> TodayScreen(vm, onTime = { tab = AppTab.MINE })
                    tab == AppTab.WORDS -> WordsScreen(vm)
                    else -> MineScreen(vm)
                }
            }
        }
    }
}
@Composable private fun Notice(message: String, dismiss: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(message, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = dismiss) { Text("知道了") }
        }
    }
}
@Composable private fun Primary(text: String, enabled: Boolean = true, action: () -> Unit) {
    Button(onClick = action, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(text) }
}
@Composable private fun HintText(text: String) { Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }

@Composable private fun TodayScreen(vm: AppViewModel, onTime: () -> Unit) {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("今天", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            TextButton(onClick = onTime) { Text("每天 ${vm.runtime.dailyMinutes} 分钟") }
        }
        Text("把认识的词，\n变成用得出的词。", style = MaterialTheme.typography.headlineMedium)
        HintText(if (vm.statistics.due > 0) "先巩固旧内容，再学一点新的。" else "跟着眼前这一步，慢慢积累自己的表达。")
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text(when { vm.card != null -> "接着上次，继续就好。"; vm.budgetReached -> "今天先到这里。"; else -> "从一小轮开始。" }, style = MaterialTheme.typography.titleLarge)
                HintText(if (vm.card != null) "题目和作答状态都已保留。" else if (vm.budgetReached) "给记忆一点时间。明天会继续安排。" else "每轮最多 5 步，随时都可以收工。")
                Primary(if (vm.card != null) "继续学习" else if (vm.budgetReached) "自愿再练 5 分钟" else "开始今天的学习", !vm.working) { vm.start(extra = vm.budgetReached) }
            }
        }
        HintText("今天已学 ${vm.todayWork.activeMillis / 60_000} 分钟 · ${vm.statistics.due} 个义项建议巩固")
        if (vm.statistics.senses == 0) HintText("词库为空。可在词库导入词包或添加词汇。")
        if (vm.runtime.lastUndoKey != null) TextButton(onClick = vm::undo, enabled = !vm.working) { Text("撤销上一次评分") }
        HorizontalDivider()
        Text("认识，还要能用出来", style = MaterialTheme.typography.titleMedium)
        HintText("复习中会穿插短表达。没想起来也没关系，先看清用法，之后再巩固。")
    }
}

@Composable private fun StudyScreen(vm: AppViewModel) {
    val c = vm.card ?: return
    val steps = vm.runtime.session?.steps ?: 0
    Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("本轮 ${if (c.phase == "FEEDBACK") steps else steps + 1} / 5", style = MaterialTheme.typography.labelLarge)
            TextButton(onClick = vm::pause, enabled = !vm.working) { Text("先收工") }
        }
        LinearProgressIndicator(progress = { steps / 5f }, modifier = Modifier.fillMaxWidth())
        Text(if (c.kind == ExerciseKind.TEACH) "认识一个新表达" else if (c.guided) "刚学过，轻轻回忆一次" else c.dimension.label,
            style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        when (c.phase) {
            "FEEDBACK" -> {
                Text(c.feedback, style = MaterialTheme.typography.titleMedium)
                AnswerCard(c)
                Primary(if (steps >= 5) "完成这一轮" else "继续", !vm.working, vm::next)
                TextButton(onClick = vm::report, enabled = !vm.working) { Text("这道题有问题") }
            }
            "REVEALED", "UNJUDGED" -> {
                if (c.phase == "UNJUDGED") {
                    HintText("你的回答：${c.draft}")
                    HintText("表达可能不止一种。请核对参考答案；系统不会仅因字符串不同就判你答错。")
                }
                AnswerCard(c)
                HintText(if (c.hints > 0) "用过提示后，这次有没有想起来？" else "回想看答案之前，你是哪种情况？")
                // Vertical full-width controls continue to fit with large system fonts.
                OutlinedButton(onClick = { vm.grade(Outcome.AGAIN) }, enabled = !vm.working, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("没想起") }
                if (c.hints > 0) Primary("借提示想起", !vm.working) { vm.grade(Outcome.ASSISTED) }
                else {
                    OutlinedButton(onClick = { vm.grade(Outcome.HARD) }, enabled = !vm.working, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("费劲想起") }
                    Primary("独立想起", !vm.working) { vm.grade(Outcome.GOOD) }
                }
                if (c.phase == "UNJUDGED") TextButton(onClick = { vm.grade(Outcome.ALTERNATIVE) }, enabled = !vm.working) { Text("我的表达也成立，稍后再练目标表达") }
                HintText("看到答案才觉得熟悉，就选“没想起”。")
            }
            else -> {
                if (c.kind == ExerciseKind.TEACH) {
                    AnswerCard(c)
                    HintText("先记住一个意思和一个搭配就够了。")
                    Primary("看懂了，试着回忆一下", !vm.working) { vm.grade(Outcome.TAUGHT) }
                } else {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            // No hidden back or answer semantics in the composition while the front is shown.
                            Text(c.prompt, style = MaterialTheme.typography.headlineSmall)
                            HintText(when { c.kind == ExerciseKind.CHOICE -> "这句话更接近哪个意思？"; c.dimension == MasteryDimension.PRODUCTION -> "先想出空缺的英文表达。"; else -> "在心里说说，这里的表达是什么意思？" })
                            if (c.hints > 0) {
                                HintText(if (c.dimension == MasteryDimension.PRODUCTION) "提示：以 ${c.answer.firstOrNull() ?: '…'} 开头。" else "提示：${c.definition}")
                                HintText("这次算有提示练习，之后再试独立回忆。")
                            }
                        }
                    }
                    when (c.kind) {
                        ExerciseKind.INPUT -> {
                            OutlinedTextField(value = vm.draft, onValueChange = vm::changeDraft, label = { Text("填入英文表达") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                                enabled = !vm.working, keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { vm.submit() }))
                            Primary("提交答案", !vm.working && vm.draft.isNotBlank()) { vm.submit() }
                        }
                        ExerciseKind.CHOICE -> c.choices.forEach { answer ->
                            OutlinedButton(onClick = { vm.submit(answer) }, enabled = !vm.working, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(answer) }
                        }
                        else -> Primary("核对答案", !vm.working, vm::reveal)
                    }
                    if (c.kind != ExerciseKind.SELF) FilterChip(selected = c.uncertain, onClick = vm::uncertain, label = { Text(if (c.uncertain) "已标记不确定" else "有点不确定") }, enabled = !vm.working)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { vm.grade(Outcome.AGAIN) }, enabled = !vm.working) { Text("想不起来") }
                        TextButton(onClick = vm::hint, enabled = !vm.working && c.hints == 0) { Text(if (c.hints > 0) "已用提示" else "给一点提示") }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { vm.grade(Outcome.SKIP) }, enabled = !vm.working) { Text("跳过") }
                        TextButton(onClick = vm::report, enabled = !vm.working) { Text("题目有问题") }
                    }
                }
            }
        }
        if (vm.runtime.lastUndoKey != null) TextButton(onClick = vm::undo, enabled = !vm.working) { Text("撤销上一次评分") }
        if (vm.working) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}
@Composable private fun AnswerCard(c: LearningCard) {
    var details by remember(c.key) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(c.term, style = MaterialTheme.typography.headlineLarge)
            HintText(c.phonetic)
            Text(c.definition, style = MaterialTheme.typography.titleLarge)
            Text(c.phrase, color = MaterialTheme.colorScheme.primary)
            if (c.scope == EvidenceScope.PATTERN_COMPLETION) Text("空缺处：${c.answer}", style = MaterialTheme.typography.titleMedium)
            if (c.kind == ExerciseKind.TEACH) Text(c.prompt, style = MaterialTheme.typography.bodyLarge)
            HintText(c.explanation)
            TextButton(onClick = { details = !details }) { Text(if (details) "收起" else "为什么这样练？") }
            if (details) HintText(c.reason)
        }
    }
}

@Composable private fun SummaryScreen(vm: AppViewModel) {
    val s = vm.runtime.session
    val results = vm.attempts.filter { it.attemptKey in s?.attempts.orEmpty() && it.valid }
    val studied = results.filter { it.outcome !in listOf("TAUGHT", "SKIP", "VOID") }.map { it.wordId }.distinct().size
    val new = results.filter { it.outcome == "TAUGHT" }.map { it.wordId }.distinct().size
    val weak = results.filter { it.outcome in listOf("AGAIN", "ASSISTED", "HARD", "ALTERNATIVE") }.map { it.wordId }.distinct().size
    Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text("这一轮，先到这里。", style = MaterialTheme.typography.headlineLarge)
        HintText(when (s?.finishReason) { "TIME" -> "今天的时间已经用得差不多了，给记忆一点休息。"; "EMPTY" -> "当前适合练习的内容已处理。间隔后再来，或在词库补充新内容。"; else -> "完成一小轮，就是进展。" })
        Text("练习了 $studied 个义项\n新认识 $new 个义项", style = MaterialTheme.typography.titleLarge)
        HintText(if (weak > 0) "$weak 个表达需要再巩固。后面会隔开再试，不必现在反复背。" else "之后还会隔一段时间、换个语境再检查。")
        if (s?.finishReason == "ROUND" && !vm.budgetReached) Primary("继续一轮", !vm.working) { vm.start() }
        Primary("今天先收工", !vm.working, vm::pause)
        if (vm.budgetReached) TextButton(onClick = { vm.start(extra = true) }, enabled = !vm.working) { Text("自愿再练 5 分钟") }
        if (vm.runtime.lastUndoKey != null) TextButton(onClick = vm::undo, enabled = !vm.working) { Text("撤销上一次评分") }
        HintText("完成本轮不代表全部义项已掌握。剩余到期内容会继续保留。")
    }
}

@Composable private fun WordsScreen(vm: AppViewModel) {
    var showImport by rememberSaveable { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("词库", style = MaterialTheme.typography.headlineLarge)
            Button(onClick = { showImport = true }) { Text("添加") }
        }
        HintText("已收录 ${vm.words.size} 个义项 · 每个意思分开记录")
        if (vm.runtime.pendingWords.isNotEmpty()) Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("${vm.runtime.pendingWords.size} 条待补充释义")
                HintText(vm.runtime.pendingWords.take(5).joinToString("、"))
                TextButton(onClick = { vm.importWithAi(vm.runtime.pendingWords.joinToString("\n")) }, enabled = !vm.generating) { Text("用已配置的服务补充") }
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(vm.words.groupBy { it.term }.entries.toList(), key = { it.key }) { (term, senses) ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        TextButton(onClick = { expanded = if (expanded == term) "" else term }) { Text(term, style = MaterialTheme.typography.headlineSmall) }
                        HintText("已收录范围：${senses.count { vm.statuses[it.id] == LearningStatus.STEADY }} / ${senses.size} 个义项较稳")
                        senses.forEachIndexed { index, w ->
                            Text("${index + 1}. ${w.definition}")
                            HintText(vm.statuses[w.id]?.label ?: "待学")
                            if (expanded == term) {
                                Text(w.phrase, color = MaterialTheme.colorScheme.primary)
                                HintText(w.example)
                                MasteryDimension.entries.forEach { dim ->
                                    HintText("${dim.label}：${vm.abilityLabel(w.id, dim)}")
                                    vm.nextReviewLabel(w.id, dim)?.let { date -> HintText("下次安排：$date") }
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }
    if (showImport) ImportDialog(vm) { showImport = false }
}
@Composable private fun ImportDialog(vm: AppViewModel, dismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var format by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = dismiss, title = { Text("添加词汇") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            HintText("每行一个单词或短语。也可以粘贴离线 JSON 词包。只有单词时先存入待补充，不会编造释义。")
            OutlinedTextField(text, { text = it }, modifier = Modifier.fillMaxWidth(), minLines = 5, label = { Text("粘贴词汇或词包") })
            TextButton(onClick = { format = !format }) { Text("查看词包格式") }
            if (format) HintText("兼容旧格式：${WordImporter.FORMAT_HELP}\nJSON 词包为包含 term、phonetic、definition、phrase、example 的对象数组，释义用中文。")
        }
    }, confirmButton = { Button(onClick = { vm.addWords(text); dismiss() }, enabled = text.isNotBlank() && !vm.working) { Text("添加") } }, dismissButton = { TextButton(onClick = dismiss) { Text("取消") } })
}

@Composable private fun MineScreen(vm: AppViewModel) {
    var more by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("我的", style = MaterialTheme.typography.headlineLarge)
        Text("每天留一点时间", style = MaterialTheme.typography.titleLarge)
        HintText("默认安排好复习和新学，不用自己配比。")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(5, 10, 15).forEach { minutes -> FilterChip(selected = vm.runtime.dailyMinutes == minutes, onClick = { vm.setPreferences(minutes = minutes) }, label = { Text("$minutes 分钟") }, enabled = !vm.working) }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("学习记录", style = MaterialTheme.typography.titleLarge)
                Text("${vm.statistics.steady} 个义项较稳")
                HintText("看得懂：${vm.statistics.comprehensionVerified} 个义项有延迟验证\n用得出：${vm.statistics.productionVerified} 个义项有延迟验证")
                HintText("判断依据是隔一段时间、换个语境仍能完成。自评和展示次数不会直接变成掌握。")
            }
        }
        TextButton(onClick = { more = !more }) { Text(if (more) "收起更多设置" else "更多设置") }
        if (more) ProviderAndBackupSettings(vm)
    }
}
@Composable private fun ProviderAndBackupSettings(vm: AppViewModel) {
    var baseUrl by remember(vm.providerConfig.baseUrl) { mutableStateOf(vm.providerConfig.baseUrl) }
    var model by remember(vm.providerConfig.model) { mutableStateOf(vm.providerConfig.model) }
    var key by remember(vm.providerConfig.apiKey) { mutableStateOf(vm.providerConfig.apiKey) }
    var restoreConfirm by remember { mutableStateOf(false) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(vm::exportBackup) }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::restoreBackup) }
    Text("可选 AI 服务", style = MaterialTheme.typography.titleLarge)
    HintText("不配置也能使用本地题库。配置后按需补充语料，失败时继续用缓存。")
    OutlinedTextField(baseUrl, { baseUrl = it }, label = { Text("HTTPS 服务地址") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    OutlinedTextField(model, { model = it }, label = { Text("模型") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    OutlinedTextField(key, { key = it }, label = { Text("API 密钥") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true)
    Primary("保存服务", !vm.working) { vm.saveProvider(ProviderConfig(baseUrl, model, key)) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("自动补充语料", modifier = Modifier.weight(1f))
        Switch(checked = vm.runtime.autoAi, onCheckedChange = { vm.setPreferences(automatic = it) }, enabled = !vm.working)
    }
    Text("每天最多 ${vm.runtime.maxDailyCalls} 次后台补充")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(0, 1, 3).forEach { count -> FilterChip(selected = vm.runtime.maxDailyCalls == count, onClick = { vm.setPreferences(calls = count) }, label = { Text("$count 次") }, enabled = !vm.working) }
    }
    HintText("每批最多 5 个义项、2500 输出 token；失败也计入次数，不自动循环重试。主动添加词汇的 AI 提取由你单独发起。")
    HorizontalDivider()
    Text("备份与恢复", style = MaterialTheme.typography.titleLarge)
    HintText("完整备份包含词汇、历史、当前学习会话、设置和服务密钥。导出文件中的密钥是明文，请妥善保存。")
    OutlinedButton(onClick = { export.launch("vocab-full-backup.json") }, enabled = !vm.working, modifier = Modifier.fillMaxWidth()) { Text("导出完整备份") }
    OutlinedButton(onClick = { restoreConfirm = true }, enabled = !vm.working && !vm.generating, modifier = Modifier.fillMaxWidth()) { Text("从备份恢复") }
    if (restoreConfirm) AlertDialog(onDismissRequest = { restoreConfirm = false }, title = { Text("用备份替换当前数据？") }, text = { Text("有效备份会替换词库、历史、学习会话和服务设置。旧版备份也可导入。") },
        confirmButton = { Button(onClick = { restoreConfirm = false; restore.launch(arrayOf("application/json", "text/plain")) }) { Text("选择备份") } }, dismissButton = { TextButton(onClick = { restoreConfirm = false }) { Text("取消") } })
}
