package com.mirabolante.babele.translation

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mirabolante.babele.gemini.GeminiAudioPlayer
import com.mirabolante.babele.gemini.GeminiEvent
import com.mirabolante.babele.gemini.GeminiLiveClient
import com.mirabolante.babele.gemini.GeminiMicInput
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TranslationViewModel(application: Application) : AndroidViewModel(application) {
  companion object {
    private const val TAG = "TranslationVM"
  }

  private val _uiState = MutableStateFlow(TranslationUiState())
  val uiState: StateFlow<TranslationUiState> = _uiState.asStateFlow()

  private val geminiClient = GeminiLiveClient()
  private val geminiPlayer = GeminiAudioPlayer(application)
  private val micInput = GeminiMicInput(application)

  private var sessionJob: Job? = null
  private var nextTurnId = 1L
  private var pendingSource = StringBuilder()
  private var pendingTranslation = StringBuilder()

  fun setTargetLanguage(option: LanguageOption) {
    if (uiState.value.isActive) return
    _uiState.update { it.copy(targetLanguage = option) }
  }

  /** Toggle phone speaker vs glasses speaker. Applies to the NEXT session start. */
  fun setUseGlassesAudio(useGlasses: Boolean) {
    if (uiState.value.isActive) return
    _uiState.update { it.copy(useGlassesAudio = useGlasses) }
  }

  fun start() {
    if (sessionJob != null) return
    val state = _uiState.value

    if (!micInput.hasGlassesMic()) {
      _uiState.update {
        it.copy(
            status = TranslationStatus.ERROR,
            errorMessage = "Microfono occhiali non trovato. Accoppia i Ray-Ban Meta come cuffie BT.",
        )
      }
      return
    }

    pendingSource.clear()
    pendingTranslation.clear()
    _uiState.update {
      it.copy(
          status = TranslationStatus.STARTING,
          errorMessage = null,
          turns = persistentListOf(),
      )
    }

    micInput.enterCommunicationMode()
    geminiPlayer.start(routeToGlasses = state.useGlassesAudio)

    val prompt = buildTranslationPrompt(state.targetLanguage)
    Log.d(TAG, "Starting translation into ${state.targetLanguage.bcp47}, glassesAudio=${state.useGlassesAudio}")

    sessionJob =
        viewModelScope.launch {
          try {
            geminiClient
                .translate(
                    systemPrompt = prompt,
                    languageCode = state.targetLanguage.bcp47,
                    audioFlow = micInput.audioFlow(),
                )
                .collect { event -> handleEvent(event) }
          } catch (e: Throwable) {
            Log.e(TAG, "Session collect failed", e)
            _uiState.update {
              it.copy(
                  status = TranslationStatus.ERROR,
                  errorMessage = e.message ?: e.javaClass.simpleName,
              )
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
        withContext(Dispatchers.IO) { geminiPlayer.enqueue(event.pcm) }
      }
      GeminiEvent.Interrupted -> {
        geminiPlayer.flush()
      }
      GeminiEvent.TurnComplete -> {
        finalizePendingTurn()
      }
      is GeminiEvent.Error -> {
        _uiState.update { it.copy(status = TranslationStatus.ERROR, errorMessage = event.message) }
      }
      is GeminiEvent.UsageUpdate -> {}
    }
  }

  private fun upsertPendingTurn(existing: List<TranslationTurn>): List<TranslationTurn> {
    val source = pendingSource.toString()
    val translation = pendingTranslation.toString()
    if (source.isEmpty() && translation.isEmpty()) return existing
    val last = existing.lastOrNull()
    return if (last != null && !last.isFinal) {
      existing.dropLast(1) + last.copy(sourceText = source, translatedText = translation)
    } else {
      existing +
          TranslationTurn(
              id = nextTurnId++,
              sourceText = source,
              translatedText = translation,
              isFinal = false,
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
                .plus(last.copy(sourceText = source, translatedText = translation, isFinal = true))
                .toPersistentList()
          } else {
            (state.turns +
                    TranslationTurn(
                        id = nextTurnId++,
                        sourceText = source,
                        translatedText = translation,
                        isFinal = true,
                    ))
                .toPersistentList()
          }
      state.copy(turns = newTurns, status = TranslationStatus.LISTENING)
    }
  }

  private fun buildTranslationPrompt(target: LanguageOption): String =
      """
      You are a simultaneous interpreter. Translate everything you hear into ${target.nameForPrompt}.

      Rules:
      - Whatever language the speaker uses, translate it into ${target.nameForPrompt} and speak it out loud.
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
  }

  class Factory(private val application: Application) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return TranslationViewModel(application) as T
    }
  }
}
