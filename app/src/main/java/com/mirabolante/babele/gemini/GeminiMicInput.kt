package com.mirabolante.babele.gemini

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioRouting
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn

/**
 * Captures PCM 16 kHz mono audio from the Ray-Ban Meta glasses microphone via Bluetooth SCO.
 *
 * The glasses must be paired as a standard BT headset (Settings → Bluetooth). We use the API 31+
 * `availableCommunicationDevices` / `setCommunicationDevice` flow so BT communication endpoints
 * are visible even before SCO is active. There is no phone-mic fallback by design.
 */
class GeminiMicInput(private val context: Context) {
  companion object {
    private const val TAG = "GeminiMicInput"
    private const val SAMPLE_RATE = 16_000
    private const val CHUNK_BYTES = 3200 // 100 ms @ 16 kHz mono 16-bit
  }

  class MicNotFoundException : IllegalStateException("Glasses microphone not found")

  class MicPermissionMissingException :
      SecurityException("RECORD_AUDIO permission not granted")

  private val audioManager: AudioManager =
      context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

  private val _lastDeviceName = MutableStateFlow<String?>(null)
  val lastDeviceName: StateFlow<String?> = _lastDeviceName.asStateFlow()

  private var ownsMode = false
  private var previousMode = AudioManager.MODE_NORMAL

  @Synchronized
  fun enterCommunicationMode() {
    if (ownsMode) return
    previousMode = audioManager.mode
    audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
    ownsMode = true
    Log.d(TAG, "Entered MODE_IN_COMMUNICATION (was $previousMode)")
  }

  @Synchronized
  fun exitCommunicationMode() {
    if (!ownsMode) return
    try {
      audioManager.mode = previousMode
      Log.d(TAG, "Exited MODE_IN_COMMUNICATION (restored to $previousMode)")
    } catch (e: Exception) {
      Log.w(TAG, "Restoring AudioManager.mode failed", e)
    }
    ownsMode = false
  }

  fun hasGlassesMic(): Boolean = findGlassesCommunicationDevice() != null

  fun audioFlow(): Flow<ByteArray> = callbackFlow {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
        PackageManager.PERMISSION_GRANTED) {
      throw MicPermissionMissingException()
    }

    val device = findGlassesCommunicationDevice() ?: throw MicNotFoundException()
    val selectedName = device.productName?.toString() ?: "Bluetooth headset"
    _lastDeviceName.value = selectedName
    Log.d(
        TAG,
        "Selected glasses device: $selectedName (id=${device.id}, type=${typeToString(device.type)})",
    )

    if (audioManager.mode != AudioManager.MODE_IN_COMMUNICATION) {
      Log.w(TAG, "AudioManager.mode=${audioManager.mode} at mic-open. Calling enterCommunicationMode() as fallback.")
      enterCommunicationMode()
    }

    val routed = audioManager.setCommunicationDevice(device)
    Log.d(TAG, "setCommunicationDevice -> $routed")
    if (!routed) {
      throw IllegalStateException("setCommunicationDevice returned false")
    }

    // Allow the SCO/BLE link to come up before reading. Without this delay, the first chunks
    // come from BUILTIN_MIC and routing stays there until the next routing event.
    Log.d(TAG, "Waiting for BT link to come up...")
    delay(800)

    val minBuffer =
        AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
    val bufferBytes = (minBuffer * 4).coerceAtLeast(CHUNK_BYTES * 4)

    @SuppressLint("MissingPermission")
    val recorder =
        AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferBytes,
        )
    val pinned = recorder.setPreferredDevice(device)
    Log.d(TAG, "AudioRecord built (state=${recorder.state}, setPreferredDevice=$pinned)")

    val routingListener =
        AudioRouting.OnRoutingChangedListener { router ->
          val r = (router as? AudioRecord)?.routedDevice ?: return@OnRoutingChangedListener
          val label = "${r.productName} (${typeToString(r.type)})"
          _lastDeviceName.value = label
          Log.d(TAG, "Routing changed: $label")
          val isGlasses =
              r.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                  r.type == AudioDeviceInfo.TYPE_BLE_HEADSET
          if (!isGlasses) {
            val glasses = findGlassesCommunicationDevice()
            if (glasses != null) {
              val ok = audioManager.setCommunicationDevice(glasses)
              Log.d(TAG, "Re-asserting glasses route after drift -> $ok")
            }
          }
        }
    recorder.addOnRoutingChangedListener(routingListener, null)

    try {
      recorder.startRecording()
      val routedDev = recorder.routedDevice
      val routedLabel =
          if (routedDev != null) "${routedDev.productName} (${typeToString(routedDev.type)})"
          else "$selectedName (?)"
      _lastDeviceName.value = routedLabel
      Log.d(TAG, "AudioRecord recording, routedDevice=$routedLabel")

      val buf = ByteArray(CHUNK_BYTES)
      var statsChunks = 0
      var statsBytes = 0
      var lastStatsMs = System.currentTimeMillis()
      while (!isClosedForSend) {
        val read = recorder.read(buf, 0, buf.size, AudioRecord.READ_BLOCKING)
        if (read > 0) {
          val chunk = if (read == buf.size) buf.copyOf() else buf.copyOf(read)
          statsChunks++
          statsBytes += read
          trySend(chunk)
          val now = System.currentTimeMillis()
          if (now - lastStatsMs >= 1000) {
            Log.d(TAG, "mic 1s: chunks=$statsChunks bytes=$statsBytes")
            statsChunks = 0
            statsBytes = 0
            lastStatsMs = now
          }
        } else if (read < 0) {
          Log.w(TAG, "AudioRecord.read returned $read, stopping")
          break
        }
      }
    } catch (e: Throwable) {
      Log.e(TAG, "Mic capture loop failed", e)
      throw e
    } finally {
      try { recorder.removeOnRoutingChangedListener(routingListener) } catch (_: Throwable) {}
      try { recorder.stop() } catch (_: IllegalStateException) {}
      recorder.release()
      try { audioManager.clearCommunicationDevice() } catch (e: Exception) {
        Log.w(TAG, "clearCommunicationDevice failed", e)
      }
      Log.d(TAG, "Mic capture stopped")
    }

    awaitClose {}
  }.flowOn(Dispatchers.IO)

  /**
   * Phone-only capture: records from the phone's built-in microphone. No Bluetooth, no SCO, no
   * MODE_IN_COMMUNICATION — so playback through the phone speaker stays at full media volume and
   * low latency. Used when the user runs Babele as a standalone phone translator (no glasses).
   */
  fun phoneAudioFlow(): Flow<ByteArray> = callbackFlow {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
        PackageManager.PERMISSION_GRANTED) {
      throw MicPermissionMissingException()
    }
    _lastDeviceName.value = "Phone mic"

    val minBuffer =
        AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
    val bufferBytes = (minBuffer * 4).coerceAtLeast(CHUNK_BYTES * 4)

    @SuppressLint("MissingPermission")
    val recorder =
        AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferBytes,
        )
    Log.d(TAG, "Phone AudioRecord built (state=${recorder.state})")

    try {
      recorder.startRecording()
      val buf = ByteArray(CHUNK_BYTES)
      var statsChunks = 0
      var statsBytes = 0
      var lastStatsMs = System.currentTimeMillis()
      while (!isClosedForSend) {
        val read = recorder.read(buf, 0, buf.size, AudioRecord.READ_BLOCKING)
        if (read > 0) {
          trySend(if (read == buf.size) buf.copyOf() else buf.copyOf(read))
          statsChunks++
          statsBytes += read
          val now = System.currentTimeMillis()
          if (now - lastStatsMs >= 1000) {
            Log.d(TAG, "phone mic 1s: chunks=$statsChunks bytes=$statsBytes")
            statsChunks = 0
            statsBytes = 0
            lastStatsMs = now
          }
        } else if (read < 0) {
          Log.w(TAG, "Phone AudioRecord.read returned $read, stopping")
          break
        }
      }
    } catch (e: Throwable) {
      Log.e(TAG, "Phone mic capture loop failed", e)
      throw e
    } finally {
      try { recorder.stop() } catch (_: IllegalStateException) {}
      recorder.release()
      Log.d(TAG, "Phone mic capture stopped")
    }

    awaitClose {}
  }.flowOn(Dispatchers.IO)

  internal fun findGlassesCommunicationDevice(): AudioDeviceInfo? {
    val comms = audioManager.availableCommunicationDevices
    Log.d(TAG, "availableCommunicationDevices (${comms.size}):")
    comms.forEach { d ->
      Log.d(
          TAG,
          "  - id=${d.id} type=${typeToString(d.type)} product=${d.productName} address=${d.address}",
      )
    }

    val btCandidates =
        comms.filter {
          it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
              it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
        }
    if (btCandidates.isEmpty()) {
      Log.w(TAG, "No BT communication device available. Pair the glasses as a BT headset.")
      return null
    }
    val named =
        btCandidates.firstOrNull {
          val n = it.productName?.toString()?.lowercase() ?: return@firstOrNull false
          "ray-ban" in n || "rayban" in n || "meta" in n
        }
    return named ?: btCandidates.first()
  }

  private fun typeToString(type: Int): String =
      when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "BUILTIN_EARPIECE"
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "BUILTIN_SPEAKER"
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> "BUILTIN_MIC"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "WIRED_HEADSET"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "WIRED_HEADPHONES"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "BLUETOOTH_SCO"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "BLUETOOTH_A2DP"
        AudioDeviceInfo.TYPE_BLE_HEADSET -> "BLE_HEADSET"
        AudioDeviceInfo.TYPE_BLE_SPEAKER -> "BLE_SPEAKER"
        AudioDeviceInfo.TYPE_USB_DEVICE -> "USB_DEVICE"
        AudioDeviceInfo.TYPE_USB_HEADSET -> "USB_HEADSET"
        else -> "UNKNOWN($type)"
      }
}
