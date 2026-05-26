# UI e navigazione

## Cosa fa

UI in Jetpack Compose, Material3, una sola Activity. Tre schermate: **ApiKey** (BYOK forzata), **Home** (scelta modalità + setup occhiali) e **Translation**.

## Navigazione

`BabeleScaffold` (in `ui/MainScaffold.kt`) instrada con un `when`:

- `!hasKey || showSettings` → `ApiKeyScreen` (forzata finché l'utente non salva una key — vedi [api-key-byok](api-key-byok.md))
- `chosenMode == null` → `HomeScreen`
- altrimenti → `TranslationScreen` con `TranslationViewModel` via `viewModel()`, a cui viene applicata la modalità scelta (`LaunchedEffect { setAudioMode(mode) }`)

Il back della Translation chiama `viewModel.stop()` e azzera `chosenMode`, tornando alla Home.

## HomeScreen — due step interni (mai scroll)

`HomeScreen` ha uno stato interno `HomeStep`:

1. **CHOICE**: solo icona/titolo + "How do you want to use Babele?" + due card **With glasses** / **Phone only**. Un'icona 🔑 in alto a destra riapre la pagina key. Nessuna istruzione, nessuno scroll (centrato con `Spacer(weight)`).
   - Tap **Phone only** → `onStart(PHONE)`, entra diretto in traduzione.
   - Tap **With glasses** → passa allo step GLASSES_SETUP.
2. **GLASSES_SETUP**: istruzioni per gli occhiali (accoppia come cuffia BT, scegli lingue), bottone **Open Bluetooth settings**, bottone **Start** → `onStart(GLASSES)`. Freccia back alla scelta. Nessuno scroll.

## HomeScreen

- Titolo + icona + sottotitolo.
- Tre `TipCard` di onboarding (accoppia occhiali / scegli lingue / parla).
- Bottone outline **"Apri impostazioni Bluetooth"** (`Settings.ACTION_BLUETOOTH_SETTINGS`).
- Bottone primario **"Continua"**.

## TranslationScreen

Layout `Column` con weight:

1. **Top bar**: back + titolo.
2. **Toggle modalità** (`ModeToggle`): segmented control **Occhiali / Telefono**. Cambia da dove arriva il mic e dove esce l'audio. Disabilitato a sessione attiva.
3. **Riga lingue**: due `LanguageTile` affiancate con uno `SwapHoriz` al centro.
   - Tile sinistra = **X** (etichetta "You"). L'icona route è 👓 in modalità occhiali, 📱 in modalità telefono.
   - Tile destra = **Y** (etichetta "Other person", icona 📱 telefono).
   - Tap su una tile apre `LanguagePickerSheet`. Lo swap inverte X↔Y.
   - Disabilitate mentre la sessione è attiva (le lingue si cambiano solo da fermi).
3. **Area conversazione** (`LazyColumn`, weight 1): bolle per turno, con auto-scroll all'ultima.
   - Ogni bolla mostra il testo sorgente (più piccolo, attenuato) sopra la traduzione (più grande, semibold).
   - **Allineamento per direzione**: turni dell'utente (X→Y) a sinistra su `surfaceContainer`; turni dell'altro (Y→X) a destra su `primaryContainer`. Colpo d'occhio su chi ha parlato.
4. **Status pill**: "Pronto" / "Connessione…" / "In ascolto…" / "Traduco…" / errore.
5. **Bottone** Avvia/Termina traduzione (`SwitchButton`, rosso quando attivo).

## LanguagePickerSheet

`ModalBottomSheet` con `LazyVerticalGrid` (3 colonne) di tutte le `LanguageOption`: bandiera + nome. Tap → seleziona e chiude.

## File e simboli chiave

| File / simbolo | Ruolo |
| --- | --- |
| `ui/MainScaffold.BabeleScaffold` | Gating key → Home → Translation |
| `ui/ApiKeyScreen.kt` | Pagina BYOK forzata + istruzioni |
| `ui/HomeScreen.kt` | Scelta modalità (CHOICE) + setup occhiali (GLASSES_SETUP) + icona key |
| `ui/TranslationScreen.kt` | Toggle modalità, selettori lingua, bolle direzionali, start/stop |
| `ui/LanguagePickerSheet.kt` | Griglia bandiere |
| `ui/SwitchButton.kt` | Bottone primario/distruttivo riutilizzabile |
| `ui/theme/` | Color/Type/Shape/Theme Material3 (seed #0064E0) |
| `translation/LanguageOption.kt` | 25 lingue: BCP-47, nome, bandiera emoji, nome per il prompt |

## Gotcha / decisioni di design

- **Lingue e modalità immutabili a sessione attiva**: il system prompt e la sorgente audio non si cambiano a WebSocket aperta, quindi selettori e toggle sono disabilitati durante la traduzione.
- **Mai scroll nella Home**: le due schermate della Home (scelta e setup occhiali) stanno in una pagina, centrate con `Spacer(weight)`. La pagina key invece è scrollabile (contenuto + tastiera).
- **Nessuna persistenza**: chiudendo la schermata i turni si perdono.
- **Tutte le stringhe UI in inglese** (`res/values/strings.xml`).

## Estendere

- **Localizzazione**: aggiungere `values-it/strings.xml` ecc. per l'UI multilingue.
- **Storico persistente**: salvare i turni in Room per rivedere conversazioni passate.
- **Preset coppie lingua**: bottoni rapidi per le combinazioni più usate.
