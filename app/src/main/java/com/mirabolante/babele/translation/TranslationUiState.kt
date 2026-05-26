package com.mirabolante.babele.translation

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

enum class TranslationStatus { IDLE, STARTING, LISTENING, TRANSLATING, ERROR }

/** GLASSES: mic + per-turn routing on the Ray-Ban Meta. PHONE: phone mic + phone speaker only. */
enum class AudioMode { GLASSES, PHONE }

data class TranslationTurn(
    val id: Long,
    val sourceText: String,
    val translatedText: String,
    val isFinal: Boolean,
    // true if the user (language X) spoke this turn → translation went to the phone speaker.
    // false if the other person (language Y) spoke → translation went to the glasses.
    val spokenByUser: Boolean,
)

data class TranslationUiState(
    // X = the user's own language. The user hears Y→X translations on the GLASSES.
    val languageX: LanguageOption = LanguageOption.ITALIAN,
    // Y = the other person's language. They hear X→Y translations on the PHONE speaker.
    val languageY: LanguageOption = LanguageOption.ENGLISH,
    val audioMode: AudioMode = AudioMode.GLASSES,
    val status: TranslationStatus = TranslationStatus.IDLE,
    val turns: ImmutableList<TranslationTurn> = persistentListOf(),
    val errorMessage: String? = null,
) {
  val isActive: Boolean = status != TranslationStatus.IDLE && status != TranslationStatus.ERROR
}
