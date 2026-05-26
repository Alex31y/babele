# UI e navigazione

## Cosa fa

UI in Jetpack Compose, Material3, una sola Activity. Due schermate: **Home** (onboarding/permessi) e **Translation** (la traduzione vera).

## Navigazione

`BabeleScaffold` (in `ui/MainScaffold.kt`) tiene un flag `inTranslation` (rememberSaveable) e instrada:

- `!inTranslation || !permissionsGranted` → `HomeScreen`
- altrimenti → `TranslationScreen` con un `TranslationViewModel` ottenuto via `viewModel()`

Il bottone "Continua" della Home setta `inTranslation = true` (solo se i permessi sono concessi). Il back della Translation chiama `viewModel.stop()` e torna alla Home.

## HomeScreen

- Titolo + icona + sottotitolo.
- Tre `TipCard` di onboarding (accoppia occhiali / scegli lingue / parla).
- Bottone outline **"Apri impostazioni Bluetooth"** (`Settings.ACTION_BLUETOOTH_SETTINGS`).
- Bottone primario **"Continua"**.

## TranslationScreen

Layout `Column` con weight:

1. **Top bar**: back + titolo.
2. **Riga lingue**: due `LanguageTile` affiancate con uno `SwapHoriz` al centro.
   - Tile sinistra = **X** (etichetta "Tu", icona 👓 occhiali) — la tua lingua, la senti sugli occhiali.
   - Tile destra = **Y** (etichetta "Interlocutore", icona 📱 telefono) — la lingua dell'altro, esce dal telefono.
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
| `ui/MainScaffold.BabeleScaffold` | Navigazione Home ↔ Translation |
| `ui/HomeScreen.kt` | Onboarding + permessi |
| `ui/TranslationScreen.kt` | Schermata principale, bolle direzionali, selettori lingua |
| `ui/LanguagePickerSheet.kt` | Griglia bandiere |
| `ui/SwitchButton.kt` | Bottone primario/distruttivo riutilizzabile |
| `ui/theme/` | Color/Type/Shape/Theme Material3 (seed #0064E0) |
| `translation/LanguageOption.kt` | 25 lingue: BCP-47, nome, bandiera emoji, nome per il prompt |

## Gotcha / decisioni di design

- **Lingue immutabili a sessione attiva**: il system prompt non si può cambiare a WebSocket aperta, quindi i selettori sono disabilitati durante la traduzione.
- **Nessuna persistenza**: chiudendo la schermata i turni si perdono.
- **Tutte le stringhe in italiano** (`res/values/strings.xml`).

## Estendere

- **Localizzazione**: aggiungere `values-en/strings.xml` ecc. per l'UI multilingue.
- **Storico persistente**: salvare i turni in Room per rivedere conversazioni passate.
- **Preset coppie lingua**: bottoni rapidi per le combinazioni più usate.
