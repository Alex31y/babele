# Babele — documentazione

**Babele** è un interprete vocale in tempo reale per occhiali **Ray-Ban Meta**. Il microfono degli occhiali cattura il parlato, il modello **Gemini Live API** traduce tra due lingue, e l'audio tradotto esce dalla cassa giusta a seconda di chi sta parlando.

## L'idea in una frase

Due persone, due lingue: **X** (la tua) e **Y** (quella dell'interlocutore che non parli). La direzione di ogni turno è decisa **automaticamente** rilevando la lingua dell'input (ML Kit on-device).

## Due modalità

| Modalità | Mic | Casse |
| --- | --- | --- |
| **Telefono** | Mic del telefono | Speaker del telefono (entrambe le direzioni) — il telefono è l'interprete, niente occhiali |
| **Occhiali** | Mic dei Ray-Ban Meta (BT SCO) | Le tue traduzioni (Y→X) sugli **occhiali**, quelle per l'altro (X→Y) sullo **speaker del telefono** |

In modalità **Occhiali**:
- Parli **tu** (X) → traduzione in **Y** → **speaker telefono** (la sente l'altro)
- Parla **l'altro** (Y) → traduzione in **X** → **casse occhiali** (la senti solo tu)

La modalità si sceglie nella schermata iniziale e si può cambiare dopo dal toggle nella schermata di traduzione.

## BYOK — bring your own key

Babele **non** include una API key. Al primo avvio una pagina forzata chiede all'utente di incollare la **propria** key Gemini, salvata solo sul dispositivo. Vedi [api-key-byok](features/api-key-byok.md).

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
- **Audio out**: `AudioTrack` PCM 16-bit mono @ 24 kHz (telefono; più traccia occhiali in modalità occhiali)
- **Audio in**: `AudioRecord` PCM 16 kHz mono (mic telefono, oppure occhiali via BT SCO)
- **Language ID**: ML Kit `language-id` (on-device, offline)
- **Key storage**: SharedPreferences (BYOK)
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
| [features/api-key-byok.md](features/api-key-byok.md) | Bring Your Own Key: pagina forzata, storage, gating |
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
├── config/
│   └── ApiKeyStore.kt              BYOK: storage SharedPreferences della key utente
├── gemini/
│   ├── GeminiLiveClient.kt         WebSocket Gemini Live, setup + parsing, mic gate
│   ├── GeminiAudioPlayer.kt        AudioTrack telefono (+ traccia occhiali), routing per chunk
│   ├── GeminiMicInput.kt           Cattura mic: occhiali via BT SCO o mic telefono
│   └── GeminiEvent.kt              Sealed class degli eventi del flusso
├── translation/
│   ├── TranslationViewModel.kt     Orchestrazione, detection lingua, routing per-turno
│   ├── TranslationUiState.kt       Stato UI + TranslationTurn + AudioMode
│   └── LanguageOption.kt           Enum delle 25 lingue (BCP-47 + bandiera + nome)
└── ui/
    ├── MainScaffold.kt             Gating key → Home → Translation
    ├── ApiKeyScreen.kt             Pagina BYOK forzata + istruzioni
    ├── HomeScreen.kt               Scelta modalità (occhiali/telefono) + setup occhiali
    ├── TranslationScreen.kt        Toggle modalità, due lingue, chat bilingue, start/stop
    ├── LanguagePickerSheet.kt      Griglia bandiere
    ├── SwitchButton.kt             Bottone riutilizzabile
    └── theme/                      Material3 theme
```
