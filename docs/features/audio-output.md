# Audio output — dual route

## Cosa fa

Riproduce i chunk PCM 24 kHz mono 16-bit della voce di Gemini su **uno di due output**, scelto per chunk:

- **Telefono** (`USAGE_MEDIA`, pinnato allo speaker built-in) — per le traduzioni che deve sentire l'**altra persona**.
- **Occhiali** (`USAGE_VOICE_COMMUNICATION` via BT SCO) — per le traduzioni che deve sentire solo l'**utente**.

In **modalità occhiali** entrambi gli `AudioTrack` sono creati all'avvio e tenuti vivi insieme: cambiare direzione tra un turno e l'altro è a **latenza zero**. In **modalità telefono** viene creata solo la traccia telefono e tutto esce da lì.

## Modalità telefono vs occhiali

`start(withGlasses: Boolean)`:
- `withGlasses = true` (modalità occhiali): crea traccia telefono **e** traccia occhiali; `enqueue(pcm, toGlasses)` instrada sulla cassa decisa dal rilevamento lingua.
- `withGlasses = false` (modalità telefono): crea **solo** la traccia telefono. `enqueue(pcm, toGlasses)` fa fallback alla traccia telefono quando quella occhiali è `null`, quindi tutto esce dallo speaker.

La modalità telefono non usa BT SCO né `MODE_IN_COMMUNICATION`: l'audio `USAGE_MEDIA` esce a volume pieno e bassa latenza senza le complicazioni della comm mode.

## Flusso a parole

### Avvio (`start(withGlasses)`)
Crea la traccia telefono (e, se `withGlasses`, quella occhiali) con `buildTrack(routeToGlasses)`:
- **Phone**: `USAGE_MEDIA` + `setPreferredDevice(BUILTIN_SPEAKER)`.
- **Glasses**: `USAGE_VOICE_COMMUNICATION` + `setCommunicationDevice(BT-SCO)` + `setPreferredDevice(BT-SCO)`; memorizza `ownsCommunicationDevice`.

Entrambe: buffer da 3 secondi (144 000 byte) e **start threshold a 100 ms** (vedi gotcha).

### Enqueue (`enqueue(pcm, toGlasses)`)
Scrive il chunk sulla traccia giusta con `write(..., WRITE_BLOCKING)`. Chiamato da `Dispatchers.IO`.

### Flush (`flushAll()`)
Su `GeminiEvent.Interrupted`: `pause() → flush() → play()` su **entrambe** le tracce, per zittire la coda senza ricostruirle.

### Stop (`stop()`)
`pause/flush/stop/release` su entrambe; se `ownsCommunicationDevice`, `clearCommunicationDevice()`.

## Il fix della start threshold (il bug del "delay allucinante")

Su Android API 31+, un `AudioTrack` in `MODE_STREAM` ha una **start threshold di default pari alla capacità del buffer in frame**. Con buffer da 3 secondi (72 000 frame), la riproduzione **non parte finché non ci sono 3 secondi di audio bufferizzati**.

- CameraAccess non se ne accorgeva: le sue risposte durano 20-25 s, quindi la soglia di 3 s è raggiunta subito.
- Babele rompeva: le traduzioni sono corte (1-2 s) e non raggiungono mai i 3 s in un singolo turno → l'audio restava intrappolato nel buffer e usciva turni dopo.

**Fix**: `setStartThresholdInFrames(SAMPLE_RATE / 10)` (= 2400 frame = 100 ms) prima di `play()`. La riproduzione parte appena ci sono 100 ms di audio, ma il buffer resta da 3 secondi per assorbire i burst del modello.

## File e simboli chiave

| File / simbolo | Ruolo |
| --- | --- |
| `gemini/GeminiAudioPlayer.start` | Crea entrambe le tracce |
| `gemini/GeminiAudioPlayer.buildTrack` | Costruisce una traccia per la route data, applica start threshold |
| `gemini/GeminiAudioPlayer.enqueue(pcm, toGlasses)` | Push PCM sulla traccia scelta |
| `gemini/GeminiAudioPlayer.flushAll` | Barge-in: svuota entrambe le code |
| `gemini/GeminiAudioPlayer.stop` | Cleanup, clear comm device |

## Gotcha / decisioni di design

- **Buffer 3 s non shrinkabile**: assorbe i burst ~5× del modello. Più piccolo → underrun sotto jitter.
- **Start threshold 100 ms**: la chiave per la bassa latenza. Non confondere con la dimensione del buffer.
- **Phone = `USAGE_MEDIA`, Glasses = `USAGE_VOICE_COMMUNICATION`**: usage diversi richiedono tracce diverse (l'usage è fissato alla creazione). Per questo servono due tracce, non una con route commutabile.
- **`ownsCommunicationDevice`**: il player e `GeminiMicInput` chiamano entrambi `setCommunicationDevice`; il flag evita che lo `stop()` del player tolga il route al mic ancora attivo.

## Punti di attenzione noti

- **Due tracce simultanee**: su alcuni device la traccia `USAGE_VOICE_COMMUNICATION` sempre aperta potrebbe contendere il focus alla `USAGE_MEDIA`. Se una delle due route diventasse muta, il fallback è ricreare una **singola** traccia al cambio di direzione (più lento, ma evita la contesa).
- **SCO è 16 kHz wideband**: la voce sugli occhiali è a banda più stretta dei 24 kHz dello speaker telefono — suona più "telefonica".

## Estendere

- **Controllo volume in-app**: oggi si usa solo il volume di sistema; aggiungere `setVolume()` per route.
- **Crossfade tra route**: se i turni alternano rapidamente, un breve fade eviterebbe click.
