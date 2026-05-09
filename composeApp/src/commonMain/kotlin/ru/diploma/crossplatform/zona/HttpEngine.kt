package ru.diploma.crossplatform.zona

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

expect fun zonaHttpClientEngine(): HttpClientEngine

fun createZonaHttpClient(): HttpClient {
    val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    return HttpClient(zonaHttpClientEngine()) {
        install(HttpTimeout) {
            connectTimeoutMillis = 30_000
            requestTimeoutMillis = 60_000
            socketTimeoutMillis = 60_000
        }
        install(ContentNegotiation) { json(json) }
        install(Logging) { level = LogLevel.NONE }
        expectSuccess = false
    }
}
