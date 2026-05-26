package com.mirabolante.babele.translation

enum class LanguageOption(
    val bcp47: String,
    val displayName: String,
    val flagEmoji: String,
    val nameForPrompt: String,
) {
  ITALIAN("it-IT", "Italiano", "🇮🇹", "Italian"),
  ENGLISH("en-US", "English", "🇬🇧", "English"),
  SPANISH("es-ES", "Español", "🇪🇸", "Spanish"),
  FRENCH("fr-FR", "Français", "🇫🇷", "French"),
  GERMAN("de-DE", "Deutsch", "🇩🇪", "German"),
  PORTUGUESE("pt-PT", "Português", "🇵🇹", "Portuguese"),
  DUTCH("nl-NL", "Nederlands", "🇳🇱", "Dutch"),
  POLISH("pl-PL", "Polski", "🇵🇱", "Polish"),
  TURKISH("tr-TR", "Türkçe", "🇹🇷", "Turkish"),
  RUSSIAN("ru-RU", "Русский", "🇷🇺", "Russian"),
  UKRAINIAN("uk-UA", "Українська", "🇺🇦", "Ukrainian"),
  ARABIC("ar-XA", "العربية", "🇸🇦", "Arabic"),
  HINDI("hi-IN", "हिन्दी", "🇮🇳", "Hindi"),
  JAPANESE("ja-JP", "日本語", "🇯🇵", "Japanese"),
  KOREAN("ko-KR", "한국어", "🇰🇷", "Korean"),
  CHINESE("zh-CN", "中文", "🇨🇳", "Chinese"),
  GREEK("el-GR", "Ελληνικά", "🇬🇷", "Greek"),
  HEBREW("he-IL", "עברית", "🇮🇱", "Hebrew"),
  THAI("th-TH", "ไทย", "🇹🇭", "Thai"),
  VIETNAMESE("vi-VN", "Tiếng Việt", "🇻🇳", "Vietnamese"),
  INDONESIAN("id-ID", "Indonesia", "🇮🇩", "Indonesian"),
  SWEDISH("sv-SE", "Svenska", "🇸🇪", "Swedish"),
  ROMANIAN("ro-RO", "Română", "🇷🇴", "Romanian"),
  CZECH("cs-CZ", "Čeština", "🇨🇿", "Czech"),
  HUNGARIAN("hu-HU", "Magyar", "🇭🇺", "Hungarian");

  companion object {
    val ALL: List<LanguageOption> = entries.toList()
  }
}
