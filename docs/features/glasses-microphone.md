# Microfono degli occhiali

## Cosa fa

Cattura audio PCM **16 kHz mono 16-bit LE** dal microfono dei Ray-Ban Meta via **Bluetooth SCO** e lo espone come `Flow<ByteArray>` di chunk da ~100 ms. È il porting fedele di `GeminiMicInput` di CameraAccess.

Il DAT SDK non espone un'API microfono dedicata: gli occhiali sono raggiungibili come normale dispositivo audio Bluetooth quando accoppiati come cuffia. Si usa l'API moderna (API 31+) `availableCommunicationDevices` + `setCommunicationDevice()` perché elenca gli endpoint BT di comunicazione anche prima che SCO sia attivo (il vecchio `getDevices(GET_DEVICES_INPUTS)` mostra il mic BT solo *dopo* che SCO è già instradato — problema dell'uovo e della gallina).

## Flusso a parole

### Avvio modalità comunicazione (`enterCommunicationMode`)
Imposta `AudioManager.mode = MODE_IN_COMMUNICATION` una volta all'inizio della sessione. Idempotente. Tiene su il link SCO in modo affidabile mentre il mic registra. Pareggiato da `exitCommunicationMode` alla fine.

### Cattura (`audioFlow()` — cold `callbackFlow` su `Dispatchers.IO`)
1. Verifica permesso `RECORD_AUDIO` → altrimenti `MicPermissionMissingException`.
2. `findGlassesCommunicationDevice()` cerca un endpoint `TYPE_BLUETOOTH_SCO` / `TYPE_BLE_HEADSET` con nome "ray-ban"/"rayban"/"meta" (fallback: primo BT disponibile). Se nessuno → `MicNotFoundException`.
3. `setCommunicationDevice(device)` instrada input+output di comunicazione sugli occhiali.
4. **`delay(800)`**: dà tempo al link SCO di salire fisicamente. `setCommunicationDevice` ritorna subito ma la negoziazione SCO è asincrona (~500ms-1s). Senza l'attesa, l'`AudioRecord` legge i primi chunk dal `BUILTIN_MIC` e ci resta.
5. Costruisce `AudioRecord(VOICE_COMMUNICATION, 16kHz, MONO, PCM_16BIT)` e lo pinna al device con `setPreferredDevice`.
6. Registra un `OnRoutingChangedListener`: se il route va su un device non-BT (drift), ri-asserisce `setCommunicationDevice(glasses)`.
7. Loop `recorder.read(...READ_BLOCKING)` → `trySend(chunk)` di chunk da 3200 byte (100 ms). Statistiche aggregate ogni secondo (`mic 1s: chunks=N bytes=B`).
8. In `finally`: rimuove il listener, ferma e rilascia il recorder, `clearCommunicationDevice()`.

## File e simboli chiave

| File / simbolo | Ruolo |
| --- | --- |
| `gemini/GeminiMicInput.audioFlow` | Cold flow dei chunk PCM mic |
| `gemini/GeminiMicInput.enterCommunicationMode` / `exitCommunicationMode` | Lifecycle di `MODE_IN_COMMUNICATION` |
| `gemini/GeminiMicInput.hasGlassesMic` | Pre-check sincrono: gli occhiali sono esposti come device BT? |
| `gemini/GeminiMicInput.findGlassesCommunicationDevice` | Selezione endpoint BT degli occhiali |
| `gemini/GeminiMicInput.lastDeviceName` | `StateFlow` del device mic attivo, per debug/UI |

## Gotcha / decisioni di design

- **`flowOn(Dispatchers.IO)` obbligatorio**: `setCommunicationDevice`, costruzione `AudioRecord` e il loop `read` bloccante non devono girare sul thread del collector (Main) — causerebbe ANR.
- **`delay(800)` non è negoziabile**: è il tempo di setup SCO. È un ritardo *una tantum* all'avvio della sessione, non per-turno; non incide sulla latenza di traduzione.
- **`MODE_IN_COMMUNICATION` settato una volta sola** all'avvio: cambiarlo a metà sessione interromperebbe l'`AudioTrack` in riproduzione.
- **Niente fallback al mic del telefono**: per scelta di prodotto.
- **Format 16 kHz**: è il sample rate che la Live API si aspetta per `realtimeInput.audio` (`audio/pcm;rate=16000`).

## Estendere

- **Riduzione rumore**: applicare un high-pass o un noise gate ai chunk prima di inviarli, utile in ambienti rumorosi.
- **Indicatore di livello**: esporre l'RMS dei chunk come `StateFlow` per una VU-meter in UI.
