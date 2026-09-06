package com.sitson.vocab

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sitson.vocab.data.*
import com.sitson.vocab.domain.*
import com.sitson.vocab.provider.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val database = VocabDatabase.get(application)
    private val repository = VocabRepository(database)
    private val backupManager = BackupManager(database)
    private val providerStore = SecureProviderStore(application)
    private val mutation = Mutex()
    var runtime by mutableStateOf(LearningRuntime()); private set
    var words by mutableStateOf<List<WordSenseEntity>>(emptyList()); private set
    var schedules by mutableStateOf<List<ProgressEntity>>(emptyList()); private set
    var statuses by mutableStateOf<Map<Long, LearningStatus>>(emptyMap()); private set
    var attempts by mutableStateOf<List<AttemptEntity>>(emptyList()); private set
    var statistics by mutableStateOf(AppStatistics()); private set
    var providerConfig by mutableStateOf(providerStore.load()); private set
    var message by mutableStateOf<String?>(null); private set
    var working by mutableStateOf(false); private set
    var generating by mutableStateOf(false); private set
    var ready by mutableStateOf(false); private set
    var studyOpen by mutableStateOf(false); private set
    var draft by mutableStateOf(""); private set
    private var foreground = false
    private var lastTick = SystemClock.elapsedRealtime()
    val card get() = runtime.session?.card
    val budgetReached get() = runtime.day(repository.today()).activeMillis >= runtime.dailyMinutes * 60_000L
    val todayWork get() = runtime.day(repository.today())

    init {
        viewModelScope.launch {
            runCatching { mutation.withLock { accept(repository.initialize()); refreshData() } }
                .onSuccess { ready = true }
                .onFailure { message = "读取学习数据失败：${it.message}。请重新打开应用。" }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(1000)
                if (ready && foreground && studyOpen && !working) {
                    runCatching { mutation.withLock {
                        checkpoint()
                        if (card == null && runtime.session?.finishReason == "WAIT") accept(repository.next())
                    } }.onFailure { message = "学习时间暂未保存，请稍后重试。" }
                }
            }
        }
    }
    private fun accept(r: LearningRuntime) {
        if (r.session?.card?.key != card?.key || r.session?.card?.phase != card?.phase) draft = r.session?.card?.draft.orEmpty()
        runtime = r
    }
    private suspend fun refreshData() {
        words = repository.words(); schedules = repository.schedules(); statuses = repository.statuses(); attempts = repository.attempts(); statistics = repository.statistics()
    }
    private suspend fun checkpoint() {
        val tick = SystemClock.elapsedRealtime()
        val delta = if (foreground && studyOpen) (tick - lastTick).coerceIn(0, 5000) else 0L
        lastTick = tick
        val c = card ?: return
        accept(repository.checkpoint(c.key, delta, draft))
    }
    fun setForeground(active: Boolean) {
        if (!active) {
            val elapsed = if (foreground && studyOpen) (SystemClock.elapsedRealtime() - lastTick).coerceIn(0, 5000) else 0
            val key = card?.key; val text = draft
            foreground = false
            viewModelScope.launch { mutation.withLock { if (key != null) runCatching { accept(repository.checkpoint(key, elapsed, text)) } } }
        } else foreground = true
        lastTick = SystemClock.elapsedRealtime()
    }
    private fun act(block: suspend () -> Unit) {
        if (working || !ready) return
        working = true
        viewModelScope.launch {
            try { mutation.withLock { checkpoint(); block() } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = "操作未完成，已保存的数据会保留，可以重试：${e.message}" }
            finally { lastTick = SystemClock.elapsedRealtime(); working = false }
        }
    }
    fun start(extra: Boolean = false) = act {
        accept(repository.start(extra)); studyOpen = true
        // The local session is available immediately. At most one reserved batch per start, no retry loop.
        replenish()
    }
    fun pause() = act { studyOpen = false; refreshData() }
    fun next() = act { accept(repository.next()); refreshData() }
    fun reveal() { val key = card?.key ?: return; act { accept(repository.reveal(key)) } }
    fun hint() { val key = card?.key ?: return; act { accept(repository.hint(key)) } }
    fun uncertain() { val key = card?.key ?: return; act { accept(repository.uncertain(key)) } }
    fun changeDraft(value: String) { draft = value.take(1000) }
    fun submit(value: String = draft) { val key = card?.key ?: return; act { accept(repository.submit(key, value)); refreshData() } }
    fun grade(outcome: Outcome) { val key = card?.key ?: return; act { accept(repository.grade(key, outcome)); refreshData() } }
    fun report() { val key = card?.key ?: return; act { accept(repository.report(key)); refreshData() } }
    fun undo() = act { accept(repository.undo()); studyOpen = true; refreshData() }
    fun setPreferences(minutes: Int = runtime.dailyMinutes, automatic: Boolean = runtime.autoAi, calls: Int = runtime.maxDailyCalls) = act {
        accept(repository.settings(minutes, automatic, calls))
    }
    fun clearMessage() { message = null }
    fun abilityLabel(wordId: Long, dimension: MasteryDimension): String {
        val p = schedules.firstOrNull { it.wordId == wordId && it.dimension == dimension.name }
        if (p != null && p.attempts > 0 && p.dueAt <= System.currentTimeMillis()) return "待巩固"
        val records = attempts.filter { it.wordId == wordId }.mapNotNull(repository::evidence)
        if (GraduationPolicy.verified(dimension, records)) return "已有跨日、跨语境验证"
        return if (records.any { it.dimension == dimension && it.source == EvidenceSource.OBSERVED }) "练习中，继续验证" else "待验证"
    }
    fun nextReviewLabel(wordId: Long, dimension: MasteryDimension): String? = schedules
        .firstOrNull { it.wordId == wordId && it.dimension == dimension.name && it.attempts > 0 }?.let {
            java.time.Instant.ofEpochMilli(it.dueAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
        }


    fun addWords(text: String) = act {
        val content = text.trim()
        require(content.isNotBlank()) { "请先粘贴词汇。" }
        if ('|' in content || content.startsWith("[")) {
            val parsed = if (content.startsWith("[")) WordImporter.aiJson(content) else WordImporter.fixedFormat(content)
            val (added, skipped) = repository.import(parsed)
            message = "已导入 $added 个义项，跳过 $skipped 个重复项。"
        } else {
            val added = repository.addPending(content)
            message = "已加入 $added 条待补充词汇。可导入离线词包，或在配置服务后补充释义。"
        }
        accept(repository.runtime()); refreshData()
    }
    fun importWithAi(text: String) {
        if (generating || text.isBlank()) return
        ProviderConfigValidator.validate(providerConfig)?.let { message = "请先在更多设置中配置 AI 服务。"; return }
        generating = true
        viewModelScope.launch {
            try {
                val response = OpenAiCompatibleClient(providerConfig).generate("You are a precise vocabulary lexicographer. Return only the requested JSON.", WordImporter.aiPrompt(text.take(15000)))
                val items = WordImporter.aiJson(response)
                mutation.withLock { val (added, _) = repository.import(items); accept(repository.runtime()); refreshData(); message = "已补充 $added 个义项。" }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = "补充未完成，待补充词汇已保留：${e.message}" }
            finally { generating = false }
        }
    }
    fun replenish() {
        if (generating || ProviderConfigValidator.validate(providerConfig) != null) return
        generating = true
        viewModelScope.launch {
            try {
                val selected = mutation.withLock { val batch = repository.reserveGeneration(); accept(repository.runtime()); batch }
                if (selected.isNotEmpty()) {
                    val response = OpenAiCompatibleClient(providerConfig).generate("Generate natural, unambiguous materials for the exact provided sense IDs. Return structured JSON only.", MaterialImporter.prompt(selected, "general"))
                    mutation.withLock { repository.addGeneratedMaterials(MaterialImporter.parse(response)); refreshData() }
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { message = "新语料暂未补充，本地材料仍可继续学习。" }
            finally { generating = false }
        }
    }
    fun saveProvider(config: ProviderConfig) {
        ProviderConfigValidator.validate(config)?.let { message = "服务设置未保存：$it"; return }
        providerStore.save(config); providerConfig = config; message = "服务设置已保存。"
    }
    fun exportBackup(uri: Uri) = act {
        val json = backupManager.export(providerConfig)
        withContext(Dispatchers.IO) {
            getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(json) } ?: error("无法写入所选文件。")
        }
        message = "备份已导出，包含服务密钥，请妥善保存。"
    }
    fun restoreBackup(uri: Uri) = act {
        require(!generating) { "请等当前内容补充完成后再恢复备份。" }
        val json = withContext(Dispatchers.IO) {
            getApplication<Application>().contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                val chars = CharArray(25_000_001); var size = 0
                while (size < chars.size) { val n = reader.read(chars, size, chars.size - size); if (n < 0) break; size += n }
                require(size <= 25_000_000) { "备份超过 25 MB。" }; String(chars, 0, size)
            } ?: error("无法读取所选文件。")
        }
        val backup = backupManager.restore(json)
        providerStore.save(backup.provider); providerConfig = backup.provider
        accept(repository.initialize()); draft = card?.draft.orEmpty(); studyOpen = false; refreshData()
        message = "已恢复 ${backup.words.size} 个义项及学习记录。"
    }
}
