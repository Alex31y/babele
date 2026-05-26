package com.mirabolante.babele.translation

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

enum class TranslationStatus { IDLE, STARTING, LISTENING, TRANSLATING, ERROR }

data class TranslationTurn(
    val id: Long,
    val sourceText: String,
    val translatedText: String,
    val isFinal: Boolean,
)

data class TranslationUiState(
    val targetLanguage: LanguageOption = LanguageOption.ENGLISH,
    val useGlassesAudio: Boolean = false,
    val status: TranslationStatus = TranslationStatus.IDLE,
    val turns: ImmutableList<TranslationTurn> = persistentListOf(),
    val errorMessage: String? = null,
) {
  val isActive: Boolean = status != TranslationStatus.IDLE && status != TranslationStatus.ERROR
}
