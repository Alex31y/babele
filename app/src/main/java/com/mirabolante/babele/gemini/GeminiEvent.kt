package com.mirabolante.babele.gemini

sealed class GeminiEvent {
  data class TranscriptDelta(val text: String) : GeminiEvent()

  data class InputTranscriptDelta(val text: String) : GeminiEvent()

  data class AudioChunk(val pcm: ByteArray) : GeminiEvent() {
    override fun equals(other: Any?): Boolean =
        this === other || (other is AudioChunk && pcm.contentEquals(other.pcm))
    override fun hashCode(): Int = pcm.contentHashCode()
  }

  object SessionReady : GeminiEvent()

  object TurnComplete : GeminiEvent()

  object Interrupted : GeminiEvent()

  data class Error(val message: String) : GeminiEvent()

  data class UsageUpdate(
      val promptTokens: Int,
      val responseTokens: Int,
      val totalTokens: Int,
      val cachedTokens: Int,
      val promptTokensByModality: Map<String, Int>,
      val responseTokensByModality: Map<String, Int>,
  ) : GeminiEvent()
}
