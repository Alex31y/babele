package com.mirabolante.babele.gemini

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log

/**
 * Plays Gemini's PCM 24 kHz mono 16-bit audio chunks. Two routes, chosen at start():
 *
 * - **Phone** (default): built-in speaker, USAGE_MEDIA, pinned via setPreferredDevice.
 * - **Glasses**: Ray-Ban Meta BT SCO, USAGE_VOICE_COMMUNICATION + setCommunicationDevice.
 *
 * Faithful port of the CameraAccess GeminiAudioPlayer routing logic, which was verified working
 * on Ray-Ban Meta + Pixel. Do not "optimize" the 3-second buffer or the USAGE choices — they are
 * load-bearing (see docs/features/audio-output.md in the CameraAccess project).
 */
class GeminiAudioPlayer(private val context: Context) {
  companion object {
    private const val TAG = "GeminiAudioPlayer"
    private const val SAMPLE_RATE = 24_000
  }

  private val audioManager: AudioManager =
      context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

  private var track: AudioTrack? = null
  // True when this player set the communication device (glasses route). On stop() we clear it
  // only if we set it — otherwise we'd yank the comm device out from under GeminiMicInput.
  private var ownsCommunicationDevice: Boolean = false

  @Synchronized
  fun start(routeToGlasses: Boolean = false) {
    if (track != null) return
    val minBuffer =
        AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
    // 3 seconds of PCM 16-bit mono @ 24kHz = 144000 bytes. Absorbs the model's ~5x bursts.
    // Smaller buffers underrun under scheduler jitter — do not shrink.
    val bufferSize = (SAMPLE_RATE * 2 * 3).coerceAtLeast(minBuffer * 4)

    // Phone route uses USAGE_MEDIA so it bypasses BT communication routing and goes to the
    // pinned built-in speaker. Glasses route uses USAGE_VOICE_COMMUNICATION so the AudioTrack
    // follows the BT SCO communication device the player establishes below.
    val usage =
        if (routeToGlasses) AudioAttributes.USAGE_VOICE_COMMUNICATION
        else AudioAttributes.USAGE_MEDIA

    val newTrack =
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
          newTrack.setPreferredDevice(glasses)
        }
      } else {
        Log.w(TAG, "Route=GLASSES requested but no BT communication device found — falling back to phone")
      }
    } else {
      val speaker = findBuiltInSpeaker()
      if (speaker != null) {
        val ok = newTrack.setPreferredDevice(speaker)
        Log.d(TAG, "Route=PHONE setPreferredDevice(speaker)=$ok")
      } else {
        Log.w(TAG, "Built-in speaker not found; audio may route to Bluetooth")
      }
    }

    // The default MODE_STREAM start threshold equals the buffer capacity in frames (3 s here), so
    // playback does not begin until 3 s of audio is buffered. Short translation turns (1-2 s) never
    // reach that, so each turn's audio stalls in the buffer and only drains out turns later — a
    // huge perceived latency. Drop the threshold to ~100 ms so playback starts almost immediately;
    // the 3 s buffer stays as the max capacity that absorbs the model's ~5x bursts.
    val startFrames = SAMPLE_RATE / 10 // 100 ms = 2400 frames
    val applied = newTrack.setStartThresholdInFrames(startFrames)
    Log.d(TAG, "startThresholdInFrames requested=$startFrames applied=$applied")

    newTrack.play()
    track = newTrack
    Log.d(TAG, "AudioTrack started (bufferSize=$bufferSize, route=${if (routeToGlasses) "GLASSES" else "PHONE"})")
  }

  @Synchronized
  fun enqueue(pcm: ByteArray) {
    val t = track ?: return
    try {
      val written = t.write(pcm, 0, pcm.size, AudioTrack.WRITE_BLOCKING)
      if (written < 0) Log.w(TAG, "AudioTrack.write returned $written")
    } catch (e: IllegalStateException) {
      Log.w(TAG, "AudioTrack.write failed", e)
    }
  }

  @Synchronized
  fun flush() {
    val t = track ?: return
    try {
      t.pause()
      t.flush()
      t.play()
      Log.d(TAG, "AudioTrack flushed (interrupt)")
    } catch (e: IllegalStateException) {
      Log.w(TAG, "AudioTrack.flush failed", e)
    }
  }

  @Synchronized
  fun stop() {
    val t = track ?: return
    try {
      t.pause()
      t.flush()
      t.stop()
    } catch (_: IllegalStateException) {
    }
    t.release()
    track = null
    if (ownsCommunicationDevice) {
      try {
        audioManager.clearCommunicationDevice()
        Log.d(TAG, "Cleared communication device (player-owned)")
      } catch (e: Exception) {
        Log.w(TAG, "clearCommunicationDevice failed", e)
      }
      ownsCommunicationDevice = false
    }
    Log.d(TAG, "AudioTrack stopped")
  }

  private fun findBuiltInSpeaker(): AudioDeviceInfo? =
      audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull {
        it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
      }

  /**
   * Pick a BT SCO / BLE communication endpoint that matches the Ray-Ban Meta glasses.
   * Mirrors the device discovery logic in [GeminiMicInput] so both ends of the call share
   * the same physical device.
   */
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
