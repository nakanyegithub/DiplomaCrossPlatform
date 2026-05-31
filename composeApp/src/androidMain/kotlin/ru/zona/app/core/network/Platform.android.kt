package ru.zona.app.core.network

import android.annotation.SuppressLint
import android.content.Context
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp

/** Контекст приложения, выставляется в [ru.zona.app.MainActivity] до сборки графа. */
@SuppressLint("StaticFieldLeak")
object AndroidAppContext {
    lateinit var context: Context
}

actual fun defaultApiBaseUrl(): String = "http://10.0.2.2:8080"

actual fun httpClientEngine(): HttpClientEngine = OkHttp.create()

actual fun createSettings(): Settings {
    val prefs = AndroidAppContext.context.getSharedPreferences("zona", Context.MODE_PRIVATE)
    return SharedPreferencesSettings(prefs)
}
