package com.sitson.vocab

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sitson.vocab.data.AppStatistics
import com.sitson.vocab.data.ImportedWord
import com.sitson.vocab.data.StudyItem
import com.sitson.vocab.data.VocabDatabase
import com.sitson.vocab.data.VocabRepository
import com.sitson.vocab.data.WordImporter
import com.sitson.vocab.data.WordSenseEntity
import com.sitson.vocab.provider.OpenAiCompatibleClient
import com.sitson.vocab.provider.ProviderConfig
import com.sitson.vocab.provider.ProviderConfigValidator
import com.sitson.vocab.provider.SecureProviderStore
import kotlinx.coroutines.launch

enum class InteractionMode { FLASHCARD, BILINGUAL }

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = VocabRepository(VocabDatabase.get(application).dao())
    private val providerStore = SecureProviderStore(application)

    var words by mutableStateOf<List<WordSenseEntity>>(emptyList()); private set
    var statistics by mutableStateOf(AppStatistics(0, 0, 0, 0, 0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)); private set
    var materialStyles by mutableStateOf<List<String>>(emptyList()); private set
    var providerConfig by mutableStateOf(providerStore.load()); private set
    var message by mutableStateOf<String?>(null); private set
    var busy by mutableStateOf(false); private set
    var session by mutableStateOf<List<StudyItem>>(emptyList()); private set
    var sessionIndex by mutableStateOf(0); private set
    var feedback by mutableStateOf<String?>(null); private set
    var hints by mutableStateOf(0); private set
    var interactionMode by mutableStateOf(InteractionMode.FLASHCARD); private set
    var grading by mutableStateOf(false); private set
    private var questionStarted = 0L
    private var knownCount = 0
    private var hardCount = 0
    private var againCount = 0
    private var skippedCount = 0

    val currentItem get() = session.getOrNull(sessionIndex)

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        words = repository.words()
        statistics = repository.statistics()
        materialStyles = repository.styles()
    }

    fun clearMessage() { message = null }

    fun importFixed(text: String, style: String) = viewModelScope.launch {
        runCatching { WordImporter.fixedFormat(text) }
            .onSuccess { importWords(it, style) }
            .onFailure { message = it.message ?: "The import could not be parsed." }
    }

    fun importWithAi(text: String, style: String) = viewModelScope.launch {
        val validation = ProviderConfigValidator.validate(providerConfig)
        if (validation != null) { message = "Configure AI first: $validation"; return@launch }
        if (text.isBlank()) { message = "Paste some text to extract vocabulary from."; return@launch }
        busy = true
        runCatching {
            val response = OpenAiCompatibleClient(providerConfig).generate(
                "You are a precise vocabulary lexicographer. Follow the requested JSON schema.",
                WordImporter.aiPrompt(text, style),
            )
            WordImporter.aiJson(response)
        }.onSuccess { importWords(it, style) }
            .onFailure { message = "AI import failed: ${it.message}" }
        busy = false
    }

    private suspend fun importWords(items: List<ImportedWord>, style: String) {
        val (added, duplicates) = repository.import(items, style)
        message = "Added $added sense${if (added == 1) "" else "s"}. Skipped $duplicates duplicate${if (duplicates == 1) "" else "s"}."
        words = repository.words()
        statistics = repository.statistics()
        materialStyles = repository.styles()
    }

    fun refreshMaterials(style: String) = viewModelScope.launch { refreshMaterialsInternal(style, announce = true) }

    private suspend fun refreshMaterialsInternal(style: String, announce: Boolean) {
        ProviderConfigValidator.validate(providerConfig)?.let { if (announce) message = "Configure AI first: $it"; return }
        if (words.isEmpty()) { if (announce) message = "Add words before generating materials."; return }
        busy = true
        runCatching {
            val response = OpenAiCompatibleClient(providerConfig).generate(
                "You create varied, unambiguous vocabulary-learning material as strict JSON.",
                com.sitson.vocab.data.MaterialImporter.prompt(words.take(30), style),
            )
            repository.addGeneratedMaterials(com.sitson.vocab.data.MaterialImporter.parse(response))
        }.onSuccess { (accepted, rejected) ->
            if (announce) message = "Added $accepted fresh materials; rejected $rejected invalid or duplicate items."
            materialStyles = repository.styles()
        }.onFailure { if (announce) message = "Material refresh failed: ${it.message}" }
        busy = false
    }

    fun saveProvider(config: ProviderConfig): Boolean {
        ProviderConfigValidator.validate(config)?.let { message = it; return false }
        providerStore.save(config); providerConfig = config; message = "Provider saved securely."; return true
    }

    fun startSession(
        review: Boolean,
        quantity: Int = 20,
        style: String = "",
        dimension: String = "",
        mode: InteractionMode = InteractionMode.FLASHCARD,
    ) = viewModelScope.launch {
        interactionMode = mode
        initializeSession(repository.queue(review, quantity.coerceIn(1, 500), style, dimension))
        if (session.isEmpty()) message = if (review) "No studied items match this material style yet." else "Add words before starting a learning session."
        if (review && repository.shouldRefreshMaterials() && ProviderConfigValidator.validate(providerConfig) == null) {
            // Never delay the local session for network generation.
            viewModelScope.launch { refreshMaterialsInternal(style, announce = false) }
        }
    }

    fun startDailyPlan(quantity: Int, style: String, dimension: String, mode: InteractionMode) = viewModelScope.launch {
        interactionMode = mode
        initializeSession(repository.dailyQueue(quantity.coerceIn(1, 500), style, dimension))
        if (session.isEmpty()) message = "No learning or review items match these settings."
    }

    private suspend fun initializeSession(items: List<StudyItem>) {
        session = items; sessionIndex = 0; feedback = null; hints = 0
        knownCount = 0; hardCount = 0; againCount = 0; skippedCount = 0
        questionStarted = System.currentTimeMillis()
        session.firstOrNull()?.let { repository.recordMaterialShown(it) }
    }

    fun showHint() { if (feedback == null) hints++ }

    fun submit(answer: String) = viewModelScope.launch {
        val item = currentItem ?: return@launch
        if (feedback != null) return@launch
        val normalized = answer.trim().lowercase().replace(Regex("[^a-z0-9' -]"), "")
        val target = if (item.dimension != "PRODUCTION") item.definition else item.term
        val correct = if (item.dimension != "PRODUCTION") answer == item.definition
            else normalized == item.term.lowercase() || normalized == item.phrase.lowercase()
        repository.grade(item, correct, hints, System.currentTimeMillis() - questionStarted, answer)
        feedback = if (correct) "Correct — ${item.term}: ${item.definition}" else "Answer: $target\n${item.phrase}\n${item.example}"
        statistics = repository.statistics()
    }

    fun selfGrade(rating: String) = viewModelScope.launch {
        val item = currentItem ?: return@launch
        if (feedback != null || grading) return@launch
        grading = true
        val correct = rating != "AGAIN"
        val effortPenalty = if (rating == "HARD") 1 else 0
        try {
            repository.grade(item, correct, effortPenalty, System.currentTimeMillis() - questionStarted, "SELF_$rating")
            when (rating) { "AGAIN" -> againCount++; "HARD" -> hardCount++; else -> knownCount++ }
            statistics = repository.statistics()
            advanceAfterGrade()
        } finally {
            grading = false
        }
    }

    fun skipCard() {
        if (grading) return
        skippedCount++
        advanceAfterGrade()
    }

    fun nextQuestion() {
        advanceAfterGrade()
    }

    private fun advanceAfterGrade() {
        if (sessionIndex + 1 >= session.size) {
            session = emptyList(); sessionIndex = 0
            message = "Session complete — 会 $knownCount · 困难 $hardCount · 不会 $againCount · 跳过 $skippedCount"
            refresh(); return
        }
        sessionIndex++; feedback = null; hints = 0; questionStarted = System.currentTimeMillis()
        viewModelScope.launch { currentItem?.let { repository.recordMaterialShown(it) } }
    }

    fun leaveSession() { session = emptyList(); sessionIndex = 0; feedback = null }
}
