package ai.havencore.companion.data

enum class ThemeMode(val key: String) {
    System("system"),
    Light("light"),
    Dark("dark"),
    ;

    companion object {
        fun fromKey(key: String?): ThemeMode = entries.firstOrNull { it.key == key } ?: System
    }
}
