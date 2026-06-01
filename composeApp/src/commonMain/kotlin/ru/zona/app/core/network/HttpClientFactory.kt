package ru.zona.app.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** Платформенный движок Ktor (OkHttp / OkHttp / JS). */
expect fun httpClientEngine(): HttpClientEngine

val zonaJson: Json =
    Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

/**
 * @param tokenProvider читается заново на каждый запрос, поэтому подхватывает свежий токен
 *        после логина/логаута без пересоздания клиента.
 */
fun createHttpClient(
    baseUrl: String,
    tokenProvider: () -> String?,
): HttpClient =
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
            tokenProvider()?.let { header(HttpHeaders.Authorization, "Bearer $it") }
        }
    }
