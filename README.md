# Babele

**Real-time voice translator for Android — works standalone on your phone, or hands-free with Ray-Ban Meta glasses.**

Babele listens to speech, translates between two languages with Google's **Gemini Live API**, and speaks the translation out loud. Two people, two languages, one natural conversation.

> ⚠️ **Hobby project.** Babele is an independent experiment. It is **not** affiliated with, endorsed by, or supported by Google or Meta. "Ray-Ban Meta" and "Gemini" are trademarks of their respective owners.

---

## What it does

You pick **your language (X)** and **the other person's language (Y)**. Then you just talk:

- You speak **X** → Babele translates to **Y** and plays it so the other person hears it.
- The other person speaks **Y** → Babele translates to **X** and plays it back to you.

Babele detects which language was spoken (on-device) and routes the audio automatically.

### Two modes

| Mode | Mic | Speaker | Use case |
| --- | --- | --- | --- |
| **Phone only** | Phone mic | Phone speaker | No glasses needed. The phone is the interpreter — set it between two people. |
| **With glasses** | Ray-Ban Meta mic (BT SCO) | Your translations on the **glasses**, the other person's on the **phone speaker** | Hands-free, semi-private: you hear replies in your ear, the other person hears theirs on the phone. |

You choose the mode on the first screen and can switch it anytime from the translation screen.

---

## Bring Your Own Key (BYOK)

Babele does **not** ship an API key. On first launch it asks you to paste your **own** Gemini API key, which is:

- stored **only on your device** (SharedPreferences),
- sent **only** to Google's Gemini API,
- billed to **your** Google account, so you stay in control.

Getting a key is free and takes about a minute — the in-app setup screen walks you through it, or grab one at [Google AI Studio](https://aistudio.google.com/apikey).

---

## Requirements

- Android phone, **API 31+ (Android 12+)**, arm64.
- A **Gemini API key** (free to start).
- *(Optional)* **Ray-Ban Meta** glasses paired as a **Bluetooth headset** for glasses mode.

## Build

```bash
# Android Studio's bundled JDK works well:
export JAVA_HOME="/path/to/Android Studio/jbr"   # Windows: $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"

./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Create `local.properties` (git-ignored) with at least the SDK path:

```
sdk.dir=/path/to/Android/Sdk
# Optional, DEBUG builds only — pre-fills the key field so you don't retype it while developing:
GEMINI_API_KEY=AIza...
```

> **Release builds never use a baked key.** `GEMINI_API_KEY` is only a debug convenience; in release the user's own key (entered in-app) is the only one used. Don't ship your key.

## Tech stack

- **Kotlin** + **Jetpack Compose** (Material 3), single Activity, no DI framework.
- **Gemini Live API** over an OkHttp WebSocket (bidirectional audio + transcription).
- **ML Kit Language Identification** (on-device, offline) to detect who's speaking and route audio.
- **AudioRecord / AudioTrack** for mic capture and playback; BT SCO for the glasses path.
- No Meta DAT SDK — Babele is audio-only and treats the glasses as a standard Bluetooth headset.

## How it works (in 30 seconds)

1. The mic streams 16 kHz PCM to Gemini Live over a WebSocket.
2. The model's voice-activity detection segments turns; a **mic gate** mutes the mic while the model speaks to avoid feedback loops.
3. Per turn, ML Kit identifies the input language → decides the direction → picks the output device (phone speaker or glasses).
4. Translated PCM (24 kHz) is played on the chosen `AudioTrack`.

Full architecture and design notes live in [`docs/`](docs/README.md).

## Documentation

- [Overview & source map](docs/README.md)
- [API key (BYOK)](docs/features/api-key-byok.md)
- [Setup & pairing](docs/features/setup-and-pairing.md)
- [Glasses microphone](docs/features/glasses-microphone.md)
- [Gemini Live translation](docs/features/gemini-live-translation.md)
- [Language detection & routing](docs/features/language-detection-routing.md)
- [Audio output (dual route + modes)](docs/features/audio-output.md)
- [UI & navigation](docs/features/ui.md)

## Privacy

- Your API key is stored locally and never leaves the device except in calls to Google's API.
- Audio is streamed to Google's Gemini API for translation. Babele itself has **no backend** and collects nothing.
- Transcripts are kept in memory only and discarded when you leave the screen — no persistence.

## Status & limitations

- Early/experimental. Expect rough edges.
- Glasses mode requires the Ray-Ban Meta to be paired as a Bluetooth **headset** (SCO), separate from the Meta AI app pairing.
- SCO audio to the glasses is narrowband (16 kHz) — voice sounds a bit "telephone-ish".
- Language detection on very short utterances can be ambiguous; Babele falls back to the previous direction.
- No ephemeral-token exchange: the key goes straight to the WSS URL. Fine for personal/BYOK use; not a hardened production setup.

## Contributing

Issues and PRs welcome. Please keep changes focused and describe how you tested them on a real device (audio routing is hard to validate without one).

## License

Choose and add a license before publishing (e.g. MIT). Note that the audio/Gemini integration patterns were derived from Meta's official **CameraAccess** Device Access Toolkit sample — check its license terms if you reuse code from it.

## Acknowledgements

- Patterns adapted from Meta's **CameraAccess** Wearables DAT sample.
- Translation by Google **Gemini** (Live API).
- On-device language ID by Google **ML Kit**.
