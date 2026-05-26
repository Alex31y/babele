# Setup e pairing

## Cosa fa

Gestisce i permessi Android necessari e guida l'utente ad accoppiare i Ray-Ban Meta come cuffia Bluetooth. A differenza di CameraAccess **non c'è registrazione DAT**: gli occhiali servono solo come dispositivo audio Bluetooth standard.

## Flusso a parole

1. All'avvio `MainActivity.onStart()` controlla i permessi e, se mancano, li richiede via `RequestMultiplePermissions`.
2. I permessi richiesti (`MainActivity.PERMISSIONS`): `BLUETOOTH`, `BLUETOOTH_CONNECT`, `INTERNET`, `RECORD_AUDIO`, `MODIFY_AUDIO_SETTINGS`.
3. `permissionsGrantedState` (un `mutableStateOf`) diventa `true` quando tutti i permessi sono concessi; pilota la navigazione.
4. `HomeScreen` mostra l'onboarding in 3 step (accoppia occhiali → scegli lingue → parla) e un bottone **"Apri impostazioni Bluetooth"** che lancia `Settings.ACTION_BLUETOOTH_SETTINGS`.
5. Il bottone **"Continua"** porta alla `TranslationScreen` (solo se i permessi sono concessi).

## Pairing degli occhiali

I Ray-Ban Meta vanno accoppiati **due volte** dal punto di vista del sistema:

- Nell'app **Meta AI** (per l'uso normale degli occhiali) — non richiesto da Babele.
- Come **cuffia Bluetooth** in Android Settings → Bluetooth — **questo sì** è necessario, perché Babele accede al microfono e alle casse via profilo SCO standard.

Quando accoppiati come cuffia, gli occhiali compaiono in `AudioManager.availableCommunicationDevices` come `TYPE_BLUETOOTH_SCO` con `productName` tipo "RB Meta 01TJ".

## File e simboli chiave

| File / simbolo | Ruolo |
| --- | --- |
| `MainActivity.PERMISSIONS` | Array dei permessi Android richiesti |
| `MainActivity.permissionCheckLauncher` | Launcher per la richiesta multipla |
| `MainActivity.hasAllPermissions()` | Pre-check sincrono dei permessi |
| `ui/HomeScreen.kt` | Onboarding + deep link a impostazioni BT |
| `ui/MainScaffold.kt` | `BabeleScaffold`: instrada Home ↔ Translation in base a `permissionsGranted` |

## Gotcha / decisioni di design

- **Niente `RECORD_AUDIO` opzionale**: serve sempre, è il cuore dell'app. Se negato, la traduzione non parte.
- **`MODIFY_AUDIO_SETTINGS`**: necessario per `setCommunicationDevice` / cambio `AudioManager.mode`.
- **Niente fallback al mic del telefono**: se gli occhiali non sono accoppiati come cuffia BT, `GeminiMicInput.hasGlassesMic()` restituisce `false` e la sessione mostra errore. Scelta di prodotto: il mic è quello degli occhiali, punto.
- **Permessi controllati in `onStart`, non `onCreate`**: così tornando dalle impostazioni BT i permessi vengono riconfermati.

## Estendere

- **Onboarding più ricco**: aggiungere il rilevamento automatico della presenza degli occhiali in `availableCommunicationDevices` e mostrare un badge "occhiali connessi" prima del bottone Continua.
- **Deep link diretto al device**: alcuni OEM supportano l'apertura della pagina del singolo device BT — utile per velocizzare il pairing.
