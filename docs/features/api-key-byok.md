# API key — Bring Your Own Key (BYOK)

## Cosa fa

Babele non spedisce nessuna API key. Al primo avvio mostra una **pagina di configurazione forzata** che chiede all'utente di incollare la propria key Gemini. La key è salvata solo sul dispositivo ed è l'**unica** usata per le chiamate (in release non c'è fallback).

## Flusso a parole

1. `BabeleScaffold` legge `ApiKeyStore.hasUserKey()`. Se `false`, mostra `ApiKeyScreen` con `forced = true` (niente pulsante di chiusura).
2. La pagina spiega cos'è una API key, dà istruzioni passo-passo e un bottone **"Get a free key"** che apre `https://aistudio.google.com/apikey`.
3. L'utente incolla la key in un `OutlinedTextField` (tastiera password). Validazione minima: deve iniziare con `AIza`.
4. Su **Save**, `ApiKeyStore.setKey()` la persiste in SharedPreferences; `hasKey` diventa `true` e si passa alla Home.
5. In seguito, l'icona 🔑 nella schermata di scelta riapre `ApiKeyScreen` con `forced = false` (con pulsante di chiusura e "Remove key").
6. `TranslationViewModel.start()` crea il `GeminiLiveClient` con `apiKeyStore.effectiveKey()` per ogni sessione, così legge sempre la key corrente.

## Pure BYOK: niente fallback in release

`ApiKeyStore`:

| Metodo | Comportamento |
| --- | --- |
| `hasUserKey()` | `true` solo se l'utente ha salvato una key → pilota il gate forzato |
| `effectiveKey()` | **Solo** la key dell'utente. Vuota finché non ne salva una. Nessun fallback a `BuildConfig`. |
| `prefillKey()` | Pre-compila il campo: la key di `BuildConfig` **solo su build DEBUG**, vuoto in release |

Conseguenza: una build di **release** non può mai usare o addebitare la key dello sviluppatore. Il gate è basato sulla key utente, quindi la pagina è forzata al primo avvio **anche** su una build debug che ha bakeato una key (lì il campo è solo pre-compilato, basta Save).

## File e simboli chiave

| File / simbolo | Ruolo |
| --- | --- |
| `config/ApiKeyStore.kt` | Storage SharedPreferences, `hasUserKey` / `effectiveKey` / `prefillKey` |
| `ui/ApiKeyScreen.kt` | Pagina BYOK: spiegazioni, link AI Studio, campo, save/clear |
| `ui/MainScaffold.kt` | Gating: forza la pagina finché `!hasUserKey` |
| `gemini/GeminiLiveClient` (costruttore) | Riceve la key come parametro obbligatorio (niente default BuildConfig) |

## Gotcha / decisioni di design

- **Gate sulla key utente, non su BuildConfig**: onora "forzata finché l'utente non setta una sua key" anche in debug.
- **Validazione leggera** (`startsWith("AIza")`): evita errori di copia-incolla ovvi, senza una chiamata di rete bloccante. Una key sbagliata produce comunque un errore visibile a runtime dal `GeminiLiveClient`.
- **Privacy**: la key vive solo in SharedPreferences ed esce solo verso l'API di Google. Nessun backend dell'app.
- **Sicurezza**: la key va dritta nella URL WSS (`?key=...`). Accettabile per uso personale/BYOK; per un setup hardened servirebbe un ephemeral token.

## Estendere

- **Validazione reale al salvataggio**: una chiamata di test (es. `GET /v1beta/models`) per confermare che la key funziona prima di salvarla.
- **Più provider**: astrarre lo store per supportare altre chiavi/endpoint.
- **Cifratura a riposo**: usare `EncryptedSharedPreferences` se si vuole proteggere la key da accesso locale con root.
