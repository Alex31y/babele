package com.mirabolante.babele.translation

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentifier
import com.mirabolante.babele.config.ApiKeyStore
import com.mirabolante.babele.gemini.GeminiAudioPlayer
import com.mirabolante.babele.gemini.GeminiEvent
import com.mirabolante.babele.gemini.GeminiLiveClient
import com.mirabolante.babele.gemini.GeminiMicInput
import kotlin.coroutines.resume
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

class TranslationViewModel(application: Application) : AndroidViewModel(application) {
  companion object {
    private const val TAG = "TranslationVM"
  }

  private val _uiState = MutableStateFlow(TranslationUiState())
  val uiState: StateFlow<TranslationUiState> = _uiState.asStateFlow()

  private val geminiPlayer = GeminiAudioPlayer(application)
  private val micInput = GeminiMicInput(application)
  private val languageIdentifier: LanguageIdentifier = LanguageIdentification.getClient()
  private val apiKeyStore = ApiKeyStore(application)

  private var sessionJob: Job? = null
  private var nextTurnId = 1L
  private var pendingSource = StringBuilder()
  private var pendingTranslation = StringBuilder()

  // Per-turn routing decision, resolved once on the first audio chunk of the turn.
  private var turnRouteResolved = false
  private var turnSpokenByUser = true // X spoken (user) -> translate to Y -> phone
  private var lastSpokenByUser = true // fallback when detection is inconclusive

  fun setLanguageX(option: LanguageOption) {
    if (uiState.value.isActive) return
    _uiState.update { it.copy(languageX = option) }
  }

  fun setLanguageY(option: LanguageOption) {
    if (uiState.value.isActive) return
    _uiState.update { it.copy(languageY = option) }
  }

  fun swapLanguages() {
    if (uiState.value.isActive) return
    _uiState.update { it.copy(languageX = it.languageY, languageY = it.languageX) }
  }

  fun setAudioMode(mode: AudioMode) {
    if (uiState.value.isActive) return
    _uiState.update { it.copy(audioMode = mode) }
  }

  fun start() {
    if (sessionJob != null) return
    val state = _uiState.value

    if (state.languageX == state.languageY) {
      _uiState.update {
        it.copy(status = TranslationStatus.ERROR, errorMessage = "Pick two different languages")
      }
      return
    }
    val glassesMode = state.audioMode == AudioMode.GLASSES
    if (glassesMode && !micInput.hasGlassesMic()) {
      _uiState.update {
        it.copy(
            status = TranslationStatus.ERROR,
            errorMessage = "Glasses mic not found. Pair the Ray-Ban Meta as a Bluetooth headset, or switch to Phone mode.",
        )
      }
      return
    }

    pendingSource.clear()
    pendingTranslation.clear()
    turnRouteResolved = false
    lastSpokenByUser = true
    _uiState.update {
      it.copy(status = TranslationStatus.STARTING, errorMessage = null, turns = persistentListOf())
    }

    if (glassesMode) micInput.enterCommunicationMode()
    geminiPlayer.start(withGlasses = glassesMode)

    val prompt = buildTranslationPrompt(state.languageX, state.languageY)
    val micFlow = if (glassesMode) micInput.audioFlow() else micInput.phoneAudioFlow()
    Log.d(TAG, "Starting translation ${state.languageX.bcp47} <-> ${state.languageY.bcp47}, mode=${state.audioMode}")

    val client = GeminiLiveClient(apiKeyStore.effectiveKey())
    sessionJob =
        viewModelScope.launch {
          try {
            client
                .translate(systemPrompt = prompt, languageCode = null, audioFlow = micFlow)
                .collect { event -> handleEvent(event) }
          } catch (e: Throwable) {
            Log.e(TAG, "Session collect failed", e)
            _uiState.update {
              it.copy(status = TranslationStatus.ERROR, errorMessage = e.message ?: e.javaClass.simpleName)
            }
          } finally {
            cleanupAudio()
          }
        }
  }

  fun stop() {
    sessionJob?.cancel()
    sessionJob = null
    cleanupAudio()
    _uiState.update { it.copy(status = TranslationStatus.IDLE, errorMessage = null) }
  }

  fun clearError() {
    _uiState.update { it.copy(errorMessage = null, status = TranslationStatus.IDLE) }
  }

  private fun cleanupAudio() {
    geminiPlayer.stop()
    micInput.exitCommunicationMode()
  }

  private suspend fun handleEvent(event: GeminiEvent) {
    when (event) {
      GeminiEvent.SessionReady -> {
        _uiState.update { it.copy(status = TranslationStatus.LISTENING) }
      }
      is GeminiEvent.InputTranscriptDelta -> {
        pendingSource.append(event.text)
        _uiState.update {
          it.copy(
              status = TranslationStatus.LISTENING,
              turns = upsertPendingTurn(it.turns.toList()).toPersistentList(),
          )
        }
      }
      is GeminiEvent.TranscriptDelta -> {
        pendingTranslation.append(event.text)
        _uiState.update {
          it.copy(
              status = TranslationStatus.TRANSLATING,
              turns = upsertPendingTurn(it.turns.toList()).toPersistentList(),
          )
        }
      }
      is GeminiEvent.AudioChunk -> {
        if (!turnRouteResolved) {
          turnSpokenByUser = resolveSpeaker()
          turnRouteResolved = true
          lastSpokenByUser = turnSpokenByUser
          Log.d(TAG, "Turn route resolved: spokenByUser=$turnSpokenByUser -> ${if (turnSpokenByUser) "PHONE (X->Y)" else "GLASSES (Y->X)"}")
        }
        // User spoke (X) -> translation in Y for the other person -> phone speaker.
        // Other spoke (Y) -> translation in X for the wearer -> glasses.
        val toGlasses = !turnSpokenByUser
        withContext(Dispatchers.IO) { geminiPlayer.enqueue(event.pcm, toGlasses) }
      }
      GeminiEvent.Interrupted -> {
        geminiPlayer.flushAll()
      }
      GeminiEvent.TurnComplete -> {
        finalizePendingTurn()
        turnRouteResolved = false
      }
      is GeminiEvent.Error -> {
        _uiState.update { it.copy(status = TranslationStatus.ERROR, errorMessage = event.message) }
      }
      is GeminiEvent.UsageUpdate -> {}
    }
  }

  /**
   * Decide whether the current turn was spoken by the user (language X) or the other person
   * (language Y), by running on-device language identification over the input transcription.
   * Returns true if X (user). Falls back to the last direction when detection is inconclusive.
   */
  private suspend fun resolveSpeaker(): Boolean {
    val text = pendingSource.toString().trim()
    if (text.length < 2) return lastSpokenByUser
    val detected = identifyLanguage(text)
    if (detected == "und") return lastSpokenByUser
    val state = _uiState.value
    val xPrefix = state.languageX.bcp47.substringBefore("-").lowercase()
    val yPrefix = state.languageY.bcp47.substringBefore("-").lowercase()
    return when (detected.lowercase()) {
      xPrefix -> true
      yPrefix -> false
      else -> lastSpokenByUser
    }
  }

  private suspend fun identifyLanguage(text: String): String =
      suspendCancellableCoroutine { cont ->
        languageIdentifier
            .identifyLanguage(text)
            .addOnSuccessListener { code -> if (cont.isActive) cont.resume(code ?: "und") }
            .addOnFailureListener { if (cont.isActive) cont.resume("und") }
      }

  private fun upsertPendingTurn(existing: List<TranslationTurn>): List<TranslationTurn> {
    val source = pendingSource.toString()
    val translation = pendingTranslation.toString()
    if (source.isEmpty() && translation.isEmpty()) return existing
    val last = existing.lastOrNull()
    return if (last != null && !last.isFinal) {
      existing.dropLast(1) +
          last.copy(sourceText = source, translatedText = translation, spokenByUser = turnSpokenByUser)
    } else {
      existing +
          TranslationTurn(
              id = nextTurnId++,
              sourceText = source,
              translatedText = translation,
              isFinal = false,
              spokenByUser = turnSpokenByUser,
          )
    }
  }

  private fun finalizePendingTurn() {
    val source = pendingSource.toString()
    val translation = pendingTranslation.toString()
    pendingSource.clear()
    pendingTranslation.clear()
    if (source.isEmpty() && translation.isEmpty()) {
      _uiState.update { it.copy(status = TranslationStatus.LISTENING) }
      return
    }
    _uiState.update { state ->
      val last = state.turns.lastOrNull()
      val newTurns =
          if (last != null && !last.isFinal) {
            state.turns
                .toList()
                .dropLast(1)
                .plus(
                    last.copy(
                        sourceText = source,
                        translatedText = translation,
                        isFinal = true,
                        spokenByUser = turnSpokenByUser,
                    )
                )
                .toPersistentList()
          } else {
            (state.turns +
                    TranslationTurn(
                        id = nextTurnId++,
                        sourceText = source,
                        translatedText = translation,
                        isFinal = true,
                        spokenByUser = turnSpokenByUser,
                    ))
                .toPersistentList()
          }
      state.copy(turns = newTurns, status = TranslationStatus.LISTENING)
    }
  }

  private fun buildTranslationPrompt(x: LanguageOption, y: LanguageOption): String =
      """
      You are a professional real-time interpreter between ${x.nameForPrompt} and ${y.nameForPrompt}.

      Rules:
      - When you hear ${x.nameForPrompt}, translate it into ${y.nameForPrompt} and speak it out loud.
      - When you hear ${y.nameForPrompt}, translate it into ${x.nameForPrompt} and speak it out loud.
      - Always produce a spoken translation. Never skip a turn unless the input is entirely empty.
      - Output ONLY the translation itself. Do not introduce yourself, do not explain, do not add commentary.
      - Preserve tone, register, intent, and emotion. Translate idioms naturally.
      - Use a natural conversational pace.
      """
          .trimIndent()

  override fun onCleared() {
    super.onCleared()
    sessionJob?.cancel()
    cleanupAudio()
    languageIdentifier.close()
  }

  class Factory(private val application: Application) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return TranslationViewModel(application) as T
    }
  }
}
