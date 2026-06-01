package ru.zona.app.core.network

import com.russhwolf.settings.Settings

/** Хранилище JWT-токена. Кроссплатформенно через multiplatform-settings. */
class TokenStorage(private val settings: Settings) {
    var token: String?
        get() = settings.getStringOrNull(KEY)
        set(value) {
            if (value == null) settings.remove(KEY) else settings.putString(KEY, value)
        }

    fun clear() {
        settings.remove(KEY)
    }

    private companion object {
        const val KEY = "zona_auth_token"
    }
}

/** Платформенная фабрика [Settings] (Android: SharedPreferences, Desktop: Preferences, Web: localStorage). */
expect fun createSettings(): Settings
