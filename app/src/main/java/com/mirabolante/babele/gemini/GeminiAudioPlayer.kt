package com.mirabolante.babele.gemini

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log

/**
 * Plays Gemini's PCM 24 kHz mono 16-bit audio chunks to one of TWO simultaneous outputs, chosen
 * per chunk:
 *
 * - **Phone speaker** (USAGE_MEDIA, pinned to built-in speaker) — for translations the OTHER
 *   person should hear.
 * - **Glasses** (USAGE_VOICE_COMMUNICATION over BT SCO) — for translations only the wearer hears.
 *
 * Both tracks are created up-front and kept alive, so switching direction between turns is
 * zero-latency. Each uses a 3-second buffer (absorbs the model's ~5x bursts) with a 100 ms start
 * threshold (so short turns start playing immediately instead of waiting to fill 3 s).
 */
class GeminiAudioPlayer(private val context: Context) {
  companion object {
    private const val TAG = "GeminiAudioPlayer"
    private const val SAMPLE_RATE = 24_000
  }

  private val audioManager: AudioManager =
      context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

  private var phoneTrack: AudioTrack? = null
  private var glassesTrack: AudioTrack? = null
  private var ownsCommunicationDevice = false

  /**
   * @param withGlasses when true, also creates the glasses (BT SCO) track for per-turn routing.
   *   When false (phone-only mode), only the phone speaker track is created and everything plays
   *   through it.
   */
  @Synchronized
  fun start(withGlasses: Boolean) {
    if (phoneTrack != null || glassesTrack != null) return
    phoneTrack = buildTrack(routeToGlasses = false)
    if (withGlasses) glassesTrack = buildTrack(routeToGlasses = true)
  }

  private fun buildTrack(routeToGlasses: Boolean): AudioTrack {
    val minBuffer =
        AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
    // 3 seconds of PCM 16-bit mono @ 24kHz = 144000 bytes. Absorbs the model's ~5x bursts.
    val bufferSize = (SAMPLE_RATE * 2 * 3).coerceAtLeast(minBuffer * 4)

    val usage =
        if (routeToGlasses) AudioAttributes.USAGE_VOICE_COMMUNICATION
        else AudioAttributes.USAGE_MEDIA

    val track =
        AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(usage)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

    if (routeToGlasses) {
      val glasses = findGlassesCommunicationDevice()
      if (glasses != null) {
        val ok = audioManager.setCommunicationDevice(glasses)
        Log.d(TAG, "Route=GLASSES setCommunicationDevice(${glasses.productName})=$ok")
        if (ok) {
          ownsCommunicationDevice = true
          track.setPreferredDevice(glasses)
        }
      } else {
        Log.w(TAG, "Glasses BT communication device not found at start")
      }
    } else {
      val speaker = findBuiltInSpeaker()
      if (speaker != null) {
        val ok = track.setPreferredDevice(speaker)
        Log.d(TAG, "Route=PHONE setPreferredDevice(speaker)=$ok")
      } else {
        Log.w(TAG, "Built-in speaker not found")
      }
    }

    // The default MODE_STREAM start threshold == buffer capacity (3 s). Short turns never reach
    // it, so audio stalls. Drop it to ~100 ms so playback starts immediately.
    val applied = track.setStartThresholdInFrames(SAMPLE_RATE / 10)
    Log.d(TAG, "Route=${if (routeToGlasses) "GLASSES" else "PHONE"} startThreshold applied=$applied")

    track.play()
    Log.d(TAG, "AudioTrack started (route=${if (routeToGlasses) "GLASSES" else "PHONE"}, buffer=$bufferSize)")
    return track
  }

  @Synchronized
  fun enqueue(pcm: ByteArray, toGlasses: Boolean) {
    // In phone-only mode glassesTrack is null, so everything falls back to the phone speaker.
    val t = (if (toGlasses) glassesTrack else phoneTrack) ?: phoneTrack ?: return
    try {
      val written = t.write(pcm, 0, pcm.size, AudioTrack.WRITE_BLOCKING)
      if (written < 0) Log.w(TAG, "AudioTrack.write returned $written (toGlasses=$toGlasses)")
    } catch (e: IllegalStateException) {
      Log.w(TAG, "AudioTrack.write failed", e)
    }
  }

  /** Flush both tracks (barge-in): silence whatever is queued without tearing tracks down. */
  @Synchronized
  fun flushAll() {
    listOfNotNull(phoneTrack, glassesTrack).forEach { t ->
      try {
        t.pause()
        t.flush()
        t.play()
      } catch (e: IllegalStateException) {
        Log.w(TAG, "AudioTrack.flush failed", e)
      }
    }
    Log.d(TAG, "AudioTracks flushed (interrupt)")
  }

  @Synchronized
  fun stop() {
    listOfNotNull(phoneTrack, glassesTrack).forEach { t ->
      try {
        t.pause()
        t.flush()
        t.stop()
      } catch (_: IllegalStateException) {}
      t.release()
    }
    phoneTrack = null
    glassesTrack = null
    if (ownsCommunicationDevice) {
      try {
        audioManager.clearCommunicationDevice()
        Log.d(TAG, "Cleared communication device (player-owned)")
      } catch (e: Exception) {
        Log.w(TAG, "clearCommunicationDevice failed", e)
      }
      ownsCommunicationDevice = false
    }
    Log.d(TAG, "AudioTracks stopped")
  }

  private fun findBuiltInSpeaker(): AudioDeviceInfo? =
      audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull {
        it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
      }

  private fun findGlassesCommunicationDevice(): AudioDeviceInfo? {
    val comms = audioManager.availableCommunicationDevices
    val candidates =
        comms.filter {
          it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
              it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
        }
    if (candidates.isEmpty()) return null
    return candidates.firstOrNull {
      val n = it.productName?.toString()?.lowercase() ?: return@firstOrNull false
      "ray-ban" in n || "rayban" in n || "meta" in n
    } ?: candidates.first()
  }
}
