# Traduzione Gemini Live

## Cosa fa

Apre **una singola** sessione WebSocket con `gemini-2.5-flash-native-audio-preview-12-2025` (Live API bidi) configurata come **interprete bidirezionale** tra due lingue X e Y. Riceve i chunk PCM del mic, il server VAD segmenta i turni, e il modello risponde con:

- **Audio** (voce tradotta, PCM 24 kHz) → `GeminiEvent.AudioChunk`
- **Trascrizione output** (`outputTranscription`) → `GeminiEvent.TranscriptDelta` (la traduzione)
- **Trascrizione input** (`inputTranscription`) → `GeminiEvent.InputTranscriptDelta` (cosa è stato detto, nella lingua sorgente)

A differenza di CameraAccess: niente video, niente immagini, niente prompt MAN. Una sola lingua di output non è forzata (`languageCode = null`) così il modello può tradurre in entrambe le direzioni nella stessa sessione.

## Flusso a parole

1. `translate(systemPrompt, languageCode=null, audioFlow)` ritorna un `Flow<GeminiEvent>` costruito con `callbackFlow { … }.buffer(Channel.UNLIMITED)`.
2. Un `audioSourceJob` collega `audioFlow` (mic) a un `Channel` interno UNLIMITED — collezionato **una sola volta** per tutta la vita della sessione, così sopravvive ai reconnect.
3. Un `reconnectLoopJob` apre la WebSocket e, su `goAway` con handle di resumption, riapre trasparentemente con `sessionResumption`.
4. `onOpen` → invia il `setup` JSON.
5. `setupComplete` → emette `GeminiEvent.SessionReady`; sblocca l'`audioPump`.
6. `audioPump` (su `Dispatchers.IO`) drena il channel del mic e inoltra ogni chunk come `realtimeInput.audio` — **a meno che il mic gate sia chiuso** (vedi sotto).
7. `handleServerJson` fa il parsing dei messaggi `serverContent` ed emette gli eventi.
8. Su chiusura pulita / errore → backoff e retry (max 3) o terminazione.

## Mic gate (anti-feedback)

Il gate impedisce che la voce del modello — che esce dalle casse (in particolare quelle degli occhiali, vicinissime al mic) — rientri nel microfono e venga interpretata dal VAD server come "l'utente ha ripreso a parlare", causando un loop di `interrupted` con turni vuoti.

| Evento | Gate | Effetto |
| --- | --- | --- |
| Primo `AudioChunk` del turno modello | **chiuso** (`modelSpeaking=true`) | l'`audioPump` scarta i chunk del mic (`continue`), non li invia al server |
| `turnComplete` | **aperto** (`modelSpeaking=false`) | il mic torna a fluire al server |

Implementato con un `AtomicBoolean modelSpeaking` letto dall'`audioPump` e scritto in `handleServerJson`. I chunk scartati sono contati in `dropped` nel log di `turnComplete`.

## Setup JSON

```json
{ "setup": {
    "model": "models/gemini-2.5-flash-native-audio-preview-12-2025",
    "realtimeInputConfig": {
      "automaticActivityDetection": {
        "silenceDurationMs": 200,
        "endOfSpeechSensitivity": "END_SENSITIVITY_HIGH"
      }
    },
    "generationConfig": { "responseModalities": ["AUDIO"] },
    "outputAudioTranscription": {},
    "inputAudioTranscription": {},
    "contextWindowCompression": { "slidingWindow": {} },
    "sessionResumption": {},
    "systemInstruction": { "parts": [{ "text": "<prompt interprete X<->Y>" }] }
}}
```

Note:
- **`speechConfig.languageCode` omesso** (perché `languageCode=null`): essenziale per la traduzione bidirezionale — il modello sceglie la lingua di output in base a cosa sente.
- **VAD 200 ms / HIGH**: identico a CameraAccess. Risponde appena l'utente fa una pausa. Il mic gate evita che questa aggressività causi barge-in.
- **`contextWindowCompression` + `sessionResumption`**: rimuovono il cap di durata della sessione e permettono la ripresa dopo i `goAway` periodici.

## System prompt (in `TranslationViewModel.buildTranslationPrompt`)

```
You are a professional real-time interpreter between {X} and {Y}.
- When you hear {X}, translate it into {Y} and speak it out loud.
- When you hear {Y}, translate it into {X} and speak it out loud.
- Always produce a spoken translation. Never skip a turn unless the input is entirely empty.
- Output ONLY the translation itself. No introductions, no commentary.
- Preserve tone, register, intent, emotion. Translate idioms naturally.
- Use a natural conversational pace.
```

## File e simboli chiave

| File / simbolo | Ruolo |
| --- | --- |
| `gemini/GeminiLiveClient.translate` | Apre la sessione, ritorna `Flow<GeminiEvent>` |
| `gemini/GeminiLiveClient.buildSetupMessage` | Costruisce il setup JSON (languageCode opzionale) |
| `gemini/GeminiLiveClient.runSingleSession` | Una singola connessione WS + audioPump + mic gate |
| `gemini/GeminiEvent.kt` | `TranscriptDelta`, `InputTranscriptDelta`, `AudioChunk`, `SessionReady`, `TurnComplete`, `Interrupted`, `Error`, `UsageUpdate` |

## Gotcha / decisioni di design

- **`buffer(Channel.UNLIMITED)`**: il modello native-audio fa burst ~5× real-time; senza buffer illimitato si perdono chunk audio.
- **`languageCode = null` per bidirezionale**: forzarlo bloccherebbe l'output su una sola lingua.
- **Mic gate essenziale con output sugli occhiali**: il feedback acustico mic↔casse degli occhiali è altissimo (sono a pochi cm). Senza gate, loop di interruzioni.
- **Chiave BYOK**: il `GeminiLiveClient` riceve la key nel costruttore; `TranslationViewModel` la legge da `ApiKeyStore.effectiveKey()` (la key dell'utente, vedi [api-key-byok](api-key-byok.md)). Esce dritta nella URL WSS: ok per uso personale/BYOK, **non** hardened (servirebbe ephemeral token).

## Estendere

- **Conteggio token**: `GeminiEvent.UsageUpdate` è già emesso ma non mostrato; agganciarlo a un overlay debug.
- **Prompt regolabile**: esporre registro/formalità della traduzione come parametro utente.
- **Più di due lingue**: il prompt è parametrico; per N lingue servirebbe però una logica di routing diversa (vedi [language-detection-routing](language-detection-routing.md)).
