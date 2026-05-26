# Rilevamento lingua e routing per-turno

## Cosa fa

È la logica centrale di Babele: per **ogni turno** decide chi ha parlato (utente o interlocutore) e di conseguenza **su quale cassa** far uscire la traduzione.

- Parla l'**utente** (lingua **X**) → traduzione in **Y** → **speaker telefono** (la sente l'altro)
- Parla l'**altro** (lingua **Y**) → traduzione in **X** → **casse occhiali** (la sente solo l'utente)

La decisione è automatica: si rileva la lingua del testo di input con **ML Kit Language Identification** (on-device, offline).

## Flusso a parole

1. Mentre l'utente parla, arrivano `InputTranscriptDelta` → accumulati in `pendingSource`.
2. Quando arriva il **primo `AudioChunk`** del turno (il modello inizia a tradurre), si risolve la direzione una sola volta:
   - `resolveSpeaker()` prende `pendingSource` (testo sorgente, già completo perché il VAD ha chiuso il turno) e chiama `identifyLanguage(text)`.
   - ML Kit ritorna un codice BCP-47 corto ("it", "en", "uk", … o "und").
   - Si confronta col prefisso di `languageX.bcp47` e `languageY.bcp47`:
     - match con X → `spokenByUser = true`
     - match con Y → `spokenByUser = false`
     - "und" o nessun match → fallback a `lastSpokenByUser` (l'ultima direzione nota)
3. `turnSpokenByUser` viene fissato per tutto il turno; `turnRouteResolved = true`.
4. Ogni `AudioChunk` del turno viene instradato: `geminiPlayer.enqueue(pcm, toGlasses = !turnSpokenByUser)`.
5. Su `turnComplete` si resetta `turnRouteResolved` per il turno successivo.

## Perché rilevare l'input e non l'output

La trascrizione **input** è completa **prima** che il modello inizi a rispondere (il VAD chiude il turno dopo 200 ms di silenzio, poi parte la generazione). Quindi al primo chunk audio `pendingSource` è già pieno e affidabile. Rilevare l'output costringerebbe ad aspettare abbastanza testo tradotto, ritardando la decisione del route.

## Wrapping asincrono di ML Kit

`identifyLanguage` di ML Kit ritorna un `Task<String>`. Viene avvolto in `suspendCancellableCoroutine` per usarlo come `suspend fun` dentro il collector, senza dipendenze extra:

```kotlin
private suspend fun identifyLanguage(text: String): String =
    suspendCancellableCoroutine { cont ->
      languageIdentifier.identifyLanguage(text)
        .addOnSuccessListener { code -> if (cont.isActive) cont.resume(code ?: "und") }
        .addOnFailureListener { if (cont.isActive) cont.resume("und") }
    }
```

La detection di una stringa corta è veloce (~10-50 ms); il primo chunk audio del turno viene ritardato solo di questo, impercettibile.

## File e simboli chiave

| File / simbolo | Ruolo |
| --- | --- |
| `translation/TranslationViewModel.resolveSpeaker` | Decide X vs Y dal testo input |
| `translation/TranslationViewModel.identifyLanguage` | Wrapper suspend di ML Kit |
| `translation/TranslationViewModel.turnSpokenByUser` / `turnRouteResolved` / `lastSpokenByUser` | Stato di routing per-turno |
| `translation/LanguageOption.bcp47` | Codice lingua usato per il match (prefisso prima di "-") |
| `gemini/GeminiAudioPlayer.enqueue(pcm, toGlasses)` | Instrada il chunk sulla cassa giusta |

## Gotcha / decisioni di design

- **Decisione una volta per turno**: si risolve al primo `AudioChunk`, non ad ogni chunk, così tutti i chunk del turno vanno sulla stessa cassa e non si paga la detection ripetutamente.
- **Fallback a `lastSpokenByUser`**: per utterance cortissime ("ok", "sì") o lingue simili (IT/ES) ML Kit può restituire "und" o sbagliare. Tenere l'ultima direzione è il comportamento meno sorprendente in una conversazione che alterna i turni.
- **`toGlasses = !spokenByUser`**: utente parla (X) → traduzione per l'altro → telefono (NON occhiali). Altro parla (Y) → traduzione per l'utente → occhiali.
- **Lingue uguali bloccate**: `start()` rifiuta X == Y con errore "Scegli due lingue diverse".

## Estendere

- **Detection più robusta**: usare `identifyPossibleLanguages` e scegliere fra X/Y quello con confidenza più alta, invece del solo top-1.
- **Override manuale**: aggiungere un tap "ho parlato io / ha parlato l'altro" per correggere al volo i casi ambigui.
- **Memoria del parlante**: pesare la decisione anche sul ritmo dei turni (chi ha parlato l'ultima volta tende ad alternarsi).
