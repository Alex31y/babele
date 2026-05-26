package com.mirabolante.babele.gemini

import android.util.Base64
import android.util.Log
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject

/**
 * Gemini Live API client tuned for bidirectional live translation.
 *
 * One persistent WebSocket session with a system instruction that tells the model to translate
 * between two languages. The model's native VAD segments turns; we stream mic PCM in and
 * receive transcribed input, translated output text and translated speech audio.
 *
 * No video, no images. Session resumption keeps the conversation alive past server-side
 * rolling close (goAway) so translations can continue indefinitely.
 */
class GeminiLiveClient(private val apiKey: String) {

  companion object {
    private const val TAG = "GeminiLiveClient"
    // Native-audio Live model. Multilingual output works in a single session when no
    // speechConfig.languageCode is forced and the system instruction directs the model.
    private const val MODEL = "models/gemini-2.5-flash-native-audio-preview-12-2025"
    private const val ENDPOINT =
        "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
    private const val RECONNECT_DELAY_MS = 200L
    private const val MAX_RECONNECT_ATTEMPTS = 3
  }

  private val httpClient: OkHttpClient =
      OkHttpClient.Builder()
          .pingInterval(30, TimeUnit.SECONDS)
          .readTimeout(0, TimeUnit.MILLISECONDS)
          .build()

  /**
   * Open a persistent translation session. Streams [audioFlow] (PCM 16 kHz mono 16-bit LE)
   * into the server as `realtimeInput.audio` and emits GeminiEvents for transcription,
   * translation audio chunks, and lifecycle signals.
   *
   * [systemPrompt] should instruct the model on which two languages to translate between.
   * [languageCode] is optional; pass null to let the model choose the output voice language
   * dynamically (required for bidirectional translation in a single session).
   */
  fun translate(
      systemPrompt: String,
      languageCode: String? = null,
      audioFlow: Flow<ByteArray>,
  ): Flow<GeminiEvent> =
      callbackFlow {
        if (apiKey.isBlank()) {
          Log.e(TAG, "GEMINI_API_KEY blank")
          trySend(GeminiEvent.Error("GEMINI_API_KEY missing in local.properties"))
          close()
          return@callbackFlow
        }

        val audioChannel = Channel<ByteArray>(Channel.UNLIMITED)

        val audioSourceJob =
            launch(Dispatchers.IO) {
              try {
                audioFlow.collect { audioChannel.trySend(it) }
              } catch (e: Throwable) {
                Log.e(TAG, "Audio source collect failed", e)
                trySend(GeminiEvent.Error("Mic: ${e.message ?: e.javaClass.simpleName}"))
              } finally {
                audioChannel.close()
              }
            }

        var sessionHandle: String? = null
        var isFirstSession = true
        var consecutiveFailures = 0

        val reconnectLoopJob =
            launch {
              while (currentCoroutineContext().isActive) {
                val outcome =
                    runSingleSession(
                        languageCode = languageCode,
                        systemPrompt = systemPrompt,
                        sessionHandle = sessionHandle,
                        isFirstSession = isFirstSession,
                        audioChannel = audioChannel,
                        onHandleUpdated = { h -> sessionHandle = h },
                        onEvent = { ev -> trySend(ev) },
                    )
                isFirstSession = false

                when (outcome) {
                  SessionOutcome.TERMINAL -> break
                  SessionOutcome.RECONNECT -> {
                    consecutiveFailures = 0
                    delay(RECONNECT_DELAY_MS)
                  }
                  SessionOutcome.RETRY_AFTER_FAILURE -> {
                    consecutiveFailures++
                    if (consecutiveFailures > MAX_RECONNECT_ATTEMPTS) {
                      Log.e(TAG, "Giving up after $consecutiveFailures failed reconnect attempts")
                      trySend(GeminiEvent.Error("Connection lost, please retry"))
                      break
                    }
                    Log.w(TAG, "Reconnect attempt #$consecutiveFailures after failure")
                    delay(RECONNECT_DELAY_MS * consecutiveFailures)
                  }
                }
              }
              close()
            }

        awaitClose {
          Log.d(TAG, "Flow closed -> tearing down translation session")
          reconnectLoopJob.cancel()
          audioSourceJob.cancel()
          audioChannel.close()
        }
      }
          .buffer(Channel.UNLIMITED)

  private enum class SessionOutcome { TERMINAL, RECONNECT, RETRY_AFTER_FAILURE }

  private suspend fun runSingleSession(
      languageCode: String?,
      systemPrompt: String,
      sessionHandle: String?,
      isFirstSession: Boolean,
      audioChannel: Channel<ByteArray>,
      onHandleUpdated: (String) -> Unit,
      onEvent: (GeminiEvent) -> Unit,
  ): SessionOutcome = coroutineScope {
    val request = Request.Builder().url("$ENDPOINT?key=$apiKey").build()
    val setupDone = CompletableDeferred<Unit>()
    val outcome = CompletableDeferred<SessionOutcome>()

    var audioChunks = 0
    var transcriptChars = 0
    var turnCount = 0
    // Mic gate: while the model is producing a response, we drop incoming mic chunks instead
    // of forwarding them to the server. Without this, ambient mic noise during the model's
    // playback gets parsed as "user resumed speaking" by server VAD, which cancels the response.
    // Set on first sign of a model response, cleared on TurnComplete.
    val modelSpeaking = AtomicBoolean(false)
    var droppedWhileGated = 0

    val listener =
        object : WebSocketListener() {
          override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.d(
                TAG,
                "WebSocket open - HTTP ${response.code}, model=$MODEL, resuming=${sessionHandle != null}",
            )
            webSocket.send(buildSetupMessage(languageCode, systemPrompt, sessionHandle))
          }

          override fun onMessage(webSocket: WebSocket, text: String) {
            handleServerJson(webSocket, text)
          }

          override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
            handleServerJson(webSocket, bytes.utf8())
          }

          override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Log.w(TAG, "WebSocket closing: code=$code reason='$reason'")
            webSocket.close(1000, null)
          }

          override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.w(TAG, "WebSocket closed: code=$code reason='$reason'")
            if (!outcome.isCompleted) {
              if (code == 1000) {
                outcome.complete(SessionOutcome.TERMINAL)
              } else {
                onEvent(GeminiEvent.Error("WebSocket closed: $code $reason"))
                outcome.complete(SessionOutcome.RETRY_AFTER_FAILURE)
              }
            }
          }

          override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(
                TAG,
                "WebSocket failure (HTTP=${response?.code} body=${response?.body?.string()})",
                t,
            )
            if (!outcome.isCompleted) {
              onEvent(GeminiEvent.Error(t.message ?: "WebSocket failure"))
              outcome.complete(SessionOutcome.RETRY_AFTER_FAILURE)
            }
          }

          private fun handleServerJson(webSocket: WebSocket, payload: String) {
            try {
              val json = JSONObject(payload)

              if (json.has("setupComplete")) {
                if (isFirstSession) onEvent(GeminiEvent.SessionReady)
                if (!setupDone.isCompleted) setupDone.complete(Unit)
                return
              }

              json.optJSONObject("usageMetadata")?.let { meta ->
                onEvent(parseUsageMetadata(meta))
              }

              json.optJSONObject("sessionResumptionUpdate")?.let { upd ->
                val resumable = upd.optBoolean("resumable", false)
                val newHandle = upd.optString("newHandle", "")
                if (resumable && newHandle.isNotEmpty()) {
                  Log.d(TAG, "sessionResumptionUpdate: handle=${newHandle.take(12)}…")
                  onHandleUpdated(newHandle)
                }
              }

              if (json.has("goAway")) {
                val timeLeft = json.optJSONObject("goAway")?.optString("timeLeft") ?: "?"
                Log.w(TAG, "goAway received (timeLeft=$timeLeft) — initiating reconnect")
                if (!outcome.isCompleted) outcome.complete(SessionOutcome.RECONNECT)
                webSocket.close(1000, "goAway")
                return
              }

              val serverContent = json.optJSONObject("serverContent") ?: return

              serverContent.optJSONObject("outputTranscription")?.optString("text")?.let { t ->
                if (t.isNotEmpty()) {
                  transcriptChars += t.length
                  onEvent(GeminiEvent.TranscriptDelta(t))
                }
              }

              serverContent.optJSONObject("inputTranscription")?.optString("text")?.let { t ->
                if (t.isNotEmpty()) onEvent(GeminiEvent.InputTranscriptDelta(t))
              }

              if (serverContent.optBoolean("interrupted", false)) {
                Log.d(TAG, "server: interrupted")
                onEvent(GeminiEvent.Interrupted)
              }

              serverContent.optJSONObject("modelTurn")?.optJSONArray("parts")?.let { parts ->
                for (i in 0 until parts.length()) {
                  val part = parts.optJSONObject(i) ?: continue
                  val inline = part.optJSONObject("inlineData") ?: continue
                  val mime = inline.optString("mimeType")
                  val data = inline.optString("data")
                  if (data.isEmpty()) {
                    Log.w(TAG, "modelTurn part with empty data (mime=$mime). Keys: ${part.keys().asSequence().toList()}")
                    continue
                  }
                  if (mime.startsWith("audio/")) {
                    val pcm = Base64.decode(data, Base64.DEFAULT)
                    if (audioChunks == 0) Log.d(TAG, "FIRST audio chunk this turn: mime=$mime bytes=${pcm.size}")
                    audioChunks++
                    if (modelSpeaking.compareAndSet(false, true)) {
                      Log.d(TAG, "Mic gate CLOSED (model speaking)")
                    }
                    onEvent(GeminiEvent.AudioChunk(pcm))
                  } else {
                    Log.w(TAG, "modelTurn part with non-audio mime: $mime (bytes=${data.length})")
                  }
                }
              }

              if (serverContent.optBoolean("turnComplete", false)) {
                turnCount++
                Log.d(
                    TAG,
                    "turnComplete #$turnCount - audio=$audioChunks chunks, transcript=$transcriptChars chars, dropped=$droppedWhileGated",
                )
                audioChunks = 0
                transcriptChars = 0
                droppedWhileGated = 0
                if (modelSpeaking.compareAndSet(true, false)) {
                  Log.d(TAG, "Mic gate OPEN (turn complete)")
                }
                onEvent(GeminiEvent.TurnComplete)
              }
            } catch (e: Exception) {
              Log.e(TAG, "Failed to parse server message: ${payload.take(200)}", e)
              onEvent(GeminiEvent.Error(e.message ?: "parse error"))
            }
          }
        }

    val ws = httpClient.newWebSocket(request, listener)

    val audioPump =
        launch(Dispatchers.IO) {
          try {
            setupDone.await()
            Log.d(TAG, "Setup complete — mic→ws pipe open")
            for (pcm in audioChannel) {
              if (modelSpeaking.get()) {
                droppedWhileGated++
                continue
              }
              val b64 = Base64.encodeToString(pcm, Base64.NO_WRAP)
              val ok = ws.send(buildRealtimeInputAudioMessage(b64))
              if (!ok) break
            }
          } catch (_: kotlinx.coroutines.CancellationException) {
          } catch (e: Throwable) {
            Log.e(TAG, "Mic pump failed", e)
          }
        }

    val result = outcome.await()
    audioPump.cancel()
    ws.close(1000, "session end: $result")
    result
  }

  private fun buildSetupMessage(
      languageCode: String?,
      systemInstruction: String,
      sessionHandle: String?,
  ): String {
    val generationConfig = JSONObject().put("responseModalities", JSONArray().put("AUDIO"))
    if (languageCode != null) {
      generationConfig.put("speechConfig", JSONObject().put("languageCode", languageCode))
    }
    val sysParts = JSONArray().put(JSONObject().put("text", systemInstruction))
    // Aggressive VAD (same as CameraAccess): respond as soon as the user pauses. 200 ms means
    // the model starts replying within ~half a second of the user finishing. The mic gate
    // prevents the model's own audio from triggering a barge-in interrupt.
    val automaticActivityDetection =
        JSONObject()
            .put("silenceDurationMs", 200)
            .put("endOfSpeechSensitivity", "END_SENSITIVITY_HIGH")
    val realtimeInputConfig =
        JSONObject().put("automaticActivityDetection", automaticActivityDetection)
    val contextWindowCompression = JSONObject().put("slidingWindow", JSONObject())
    val sessionResumption =
        JSONObject().also { obj -> if (sessionHandle != null) obj.put("handle", sessionHandle) }
    val setup =
        JSONObject()
            .put("model", MODEL)
            .put("realtimeInputConfig", realtimeInputConfig)
            .put("generationConfig", generationConfig)
            .put("outputAudioTranscription", JSONObject())
            .put("inputAudioTranscription", JSONObject())
            .put("contextWindowCompression", contextWindowCompression)
            .put("sessionResumption", sessionResumption)
            .put("systemInstruction", JSONObject().put("parts", sysParts))
    return JSONObject().put("setup", setup).toString()
  }

  private fun buildRealtimeInputAudioMessage(audioBase64: String): String {
    val audio = JSONObject().put("data", audioBase64).put("mimeType", "audio/pcm;rate=16000")
    val realtimeInput = JSONObject().put("audio", audio)
    return JSONObject().put("realtimeInput", realtimeInput).toString()
  }

  private fun parseUsageMetadata(meta: JSONObject): GeminiEvent.UsageUpdate {
    val prompt = meta.optInt("promptTokenCount", 0)
    val response = meta.optInt("responseTokenCount", 0)
    val total = meta.optInt("totalTokenCount", 0)
    val cached = meta.optInt("cachedContentTokenCount", 0)
    val promptByMod = parseModalityCounts(meta.optJSONArray("promptTokensDetails"))
    val responseByMod = parseModalityCounts(meta.optJSONArray("responseTokensDetails"))
    return GeminiEvent.UsageUpdate(
        promptTokens = prompt,
        responseTokens = response,
        totalTokens = total,
        cachedTokens = cached,
        promptTokensByModality = promptByMod,
        responseTokensByModality = responseByMod,
    )
  }

  private fun parseModalityCounts(arr: JSONArray?): Map<String, Int> {
    if (arr == null || arr.length() == 0) return emptyMap()
    val out = mutableMapOf<String, Int>()
    for (i in 0 until arr.length()) {
      val obj = arr.optJSONObject(i) ?: continue
      val modality = obj.optString("modality")
      if (modality.isEmpty()) continue
      val count = obj.optInt("tokenCount", 0)
      out[modality] = (out[modality] ?: 0) + count
    }
    return out
  }
}
