package ru.zona.app.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** Платформенный движок Ktor (OkHttp / CIO / JS). */
expect fun httpClientEngine(): HttpClientEngine

val zonaJson: Json =
    Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

fun createHttpClient(baseUrl: String): HttpClient =
    HttpClient(httpClientEngine()) {
        expectSuccess = false
        install(ContentNegotiation) { json(zonaJson) }
        install(HttpTimeout) {
            requestTimeoutMillis = 20_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 20_000
        }
        defaultRequest {
            contentType(ContentType.Application.Json)
        }
    }
