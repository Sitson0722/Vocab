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

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = VocabRepository(VocabDatabase.get(application).dao())
    private val providerStore = SecureProviderStore(application)

    var words by mutableStateOf<List<WordSenseEntity>>(emptyList()); private set
    var statistics by mutableStateOf(AppStatistics(0, 0, 0, 0, 0.0, 0.0)); private set
    var providerConfig by mutableStateOf(providerStore.load()); private set
    var message by mutableStateOf<String?>(null); private set
    var busy by mutableStateOf(false); private set
    var session by mutableStateOf<List<StudyItem>>(emptyList()); private set
    var sessionIndex by mutableStateOf(0); private set
    var feedback by mutableStateOf<String?>(null); private set
    var hints by mutableStateOf(0); private set
    private var questionStarted = 0L

    val currentItem get() = session.getOrNull(sessionIndex)

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        words = repository.words()
        statistics = repository.statistics()
    }

    fun clearMessage() { message = null }

    fun importFixed(text: String) = viewModelScope.launch {
        runCatching { WordImporter.fixedFormat(text) }
            .onSuccess { importWords(it) }
            .onFailure { message = it.message ?: "The import could not be parsed." }
    }

    fun importWithAi(text: String) = viewModelScope.launch {
        val validation = ProviderConfigValidator.validate(providerConfig)
        if (validation != null) { message = "Configure AI first: $validation"; return@launch }
        if (text.isBlank()) { message = "Paste some text to extract vocabulary from."; return@launch }
        busy = true
        runCatching {
            val response = OpenAiCompatibleClient(providerConfig).generate(
                "You are a precise vocabulary lexicographer. Follow the requested JSON schema.",
                WordImporter.aiPrompt(text),
            )
            WordImporter.aiJson(response)
        }.onSuccess { importWords(it) }
            .onFailure { message = "AI import failed: ${it.message}" }
        busy = false
    }

    private suspend fun importWords(items: List<ImportedWord>) {
        val (added, duplicates) = repository.import(items)
        message = "Added $added sense${if (added == 1) "" else "s"}. Skipped $duplicates duplicate${if (duplicates == 1) "" else "s"}."
        words = repository.words()
        statistics = repository.statistics()
    }

    fun saveProvider(config: ProviderConfig): Boolean {
        ProviderConfigValidator.validate(config)?.let { message = it; return false }
        providerStore.save(config); providerConfig = config; message = "Provider saved securely."; return true
    }

    fun startSession(review: Boolean, quantity: Int = 20) = viewModelScope.launch {
        session = repository.queue(review, quantity.coerceIn(1, 500))
        sessionIndex = 0; feedback = null; hints = 0; questionStarted = System.currentTimeMillis()
        if (session.isEmpty()) message = if (review) "Nothing is due yet." else "Add words before starting a learning session."
    }

    fun showHint() { if (feedback == null) hints++ }

    fun submit(answer: String) = viewModelScope.launch {
        val item = currentItem ?: return@launch
        if (feedback != null) return@launch
        val normalized = answer.trim().lowercase().replace(Regex("[^a-z0-9' -]"), "")
        val target = if (item.dimension == "COMPREHENSION") item.definition else item.term
        val correct = if (item.dimension == "COMPREHENSION") answer == item.definition
            else normalized == item.term.lowercase() || normalized == item.phrase.lowercase()
        repository.grade(item, correct, hints, System.currentTimeMillis() - questionStarted, answer)
        feedback = if (correct) "Correct — ${item.term}: ${item.definition}" else "Answer: $target\n${item.phrase}\n${item.example}"
        statistics = repository.statistics()
    }

    fun nextQuestion() {
        if (sessionIndex + 1 >= session.size) { session = emptyList(); sessionIndex = 0; message = "Session complete."; refresh(); return }
        sessionIndex++; feedback = null; hints = 0; questionStarted = System.currentTimeMillis()
    }

    fun leaveSession() { session = emptyList(); sessionIndex = 0; feedback = null }
}
