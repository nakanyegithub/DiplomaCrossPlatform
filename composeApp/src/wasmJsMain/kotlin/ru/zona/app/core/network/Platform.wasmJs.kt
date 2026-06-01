package ru.zona.app.core.network

import com.russhwolf.settings.Settings
import com.russhwolf.settings.StorageSettings
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.js.Js

actual fun defaultApiBaseUrl(): String = "http://localhost:8080"

actual fun httpClientEngine(): HttpClientEngine = Js.create()

actual fun createSettings(): Settings = StorageSettings()
