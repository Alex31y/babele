# Babele — documentazione

**Babele** è un interprete vocale in tempo reale per occhiali **Ray-Ban Meta**. Il microfono degli occhiali cattura il parlato, il modello **Gemini Live API** traduce tra due lingue, e l'audio tradotto esce dalla cassa giusta a seconda di chi sta parlando.

## L'idea in una frase

Due persone, due lingue: **X** (la tua) e **Y** (quella dell'interlocutore che non parli).

- Quando parli **tu** (lingua X) → la traduzione in **Y** esce dallo **speaker del telefono**, così la sente l'altra persona.
- Quando parla **l'altro** (lingua Y) → la traduzione in **X** esce dalle **casse degli occhiali**, così la senti solo tu.

La direzione di ogni turno è decisa **automaticamente** rilevando la lingua dell'input (ML Kit on-device).

## Differenze rispetto a CameraAccess

Babele nasce riusando i pattern audio/Gemini di CameraAccess, ma:

- **Niente DAT SDK**: Babele è solo audio. Non usa la fotocamera né lo stream video WARP. Gli occhiali servono solo come microfono + casse Bluetooth. Questo elimina la dipendenza Maven GitHub `mwdat-*` e il flusso di registrazione DAT.
- **Niente pairing DAT**: gli occhiali vanno accoppiati come **normale cuffia Bluetooth** dalle impostazioni Android.
- **Traduzione bidirezionale a sessione singola** invece di Q&A con la fotocamera.
- **Routing audio per-turno** basato sulla lingua rilevata (la novità principale).

## Stack

- **Linguaggio**: Kotlin 2.1.20
- **UI**: Jetpack Compose (Material3)
- **DI**: nessuna — `ViewModelProvider.Factory` manuale
- **Networking**: OkHttp WebSocket (Gemini Live bidi WSS)
- **JSON**: `org.json.JSONObject`
- **Audio out**: due `AudioTrack` PCM 16-bit mono @ 24 kHz (telefono + occhiali)
- **Audio in**: `AudioRecord` PCM 16 kHz mono via BT SCO
- **Language ID**: ML Kit `language-id` (on-device, offline)
- **AGP**: 8.6.0 | **compileSdk**: 35 | **minSdk**: 31 | **targetSdk**: 34

## Build & run

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

`local.properties` (non committato) deve contenere:

```
sdk.dir=C:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
GEMINI_API_KEY=AIza...
```

`app/build.gradle.kts` legge `GEMINI_API_KEY` e lo espone come `BuildConfig.GEMINI_API_KEY` (fallback alla env var).

## Indice delle feature

| Doc | Contenuto |
| --- | --- |
| [features/setup-and-pairing.md](features/setup-and-pairing.md) | Permessi Android, pairing BT degli occhiali, schermata Home |
| [features/glasses-microphone.md](features/glasses-microphone.md) | Cattura audio dal mic degli occhiali via BT SCO |
| [features/gemini-live-translation.md](features/gemini-live-translation.md) | Client WebSocket Gemini Live, setup JSON, VAD, mic gate, session resumption |
| [features/language-detection-routing.md](features/language-detection-routing.md) | Rilevamento lingua per-turno (ML Kit) e decisione della cassa |
| [features/audio-output.md](features/audio-output.md) | Player audio dual-route (telefono + occhiali), start threshold, buffer |
| [features/ui.md](features/ui.md) | Navigazione, schermate, selezione lingue |

## Mappa sorgenti

```
app/src/main/java/com/mirabolante/babele/
├── MainActivity.kt                 Entry point, permessi runtime
├── gemini/
│   ├── GeminiLiveClient.kt         WebSocket Gemini Live, setup + parsing, mic gate
│   ├── GeminiAudioPlayer.kt        Due AudioTrack (telefono + occhiali), routing per chunk
│   ├── GeminiMicInput.kt           Cattura mic occhiali via BT SCO
│   └── GeminiEvent.kt              Sealed class degli eventi del flusso
├── translation/
│   ├── TranslationViewModel.kt     Orchestrazione, detection lingua, routing per-turno
│   ├── TranslationUiState.kt       Stato UI + TranslationTurn
│   └── LanguageOption.kt           Enum delle 25 lingue (BCP-47 + bandiera + nome)
└── ui/
    ├── MainScaffold.kt             Navigazione Home ↔ Translation
    ├── HomeScreen.kt               Onboarding + apri impostazioni BT
    ├── TranslationScreen.kt        Due lingue, chat bilingue, start/stop
    ├── LanguagePickerSheet.kt      Griglia bandiere
    ├── SwitchButton.kt             Bottone riutilizzabile
    └── theme/                      Material3 theme
```
