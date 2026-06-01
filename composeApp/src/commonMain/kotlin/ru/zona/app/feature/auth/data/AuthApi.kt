package ru.zona.app.feature.auth.data

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse

class AuthApi(
    private val client: HttpClient,
    private val baseUrl: String,
) {
    suspend fun register(body: RegisterRequest): HttpResponse =
        client.post("$baseUrl/api/auth/register") { setBody(body) }

    suspend fun login(body: LoginRequest): HttpResponse =
        client.post("$baseUrl/api/auth/login") { setBody(body) }

    suspend fun me(): HttpResponse = client.get("$baseUrl/api/me")
}
