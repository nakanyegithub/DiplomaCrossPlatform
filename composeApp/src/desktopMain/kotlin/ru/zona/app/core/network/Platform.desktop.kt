package ru.zona.app.core.network

import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.Settings
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import java.util.prefs.Preferences

actual fun defaultApiBaseUrl(): String = "http://localhost:8080"

actual fun httpClientEngine(): HttpClientEngine = CIO.create()

actual fun createSettings(): Settings {
    val prefs = Preferences.userRoot().node("ru/zona/app")
    return PreferencesSettings(prefs)
}
