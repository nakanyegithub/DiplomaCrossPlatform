package ru.zona.app.feature.profile.data

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse

class ProfileApi(
    private val client: HttpClient,
    private val baseUrl: String,
) {
    suspend fun me(): HttpResponse = client.get("$baseUrl/api/me")

    suspend fun updateProfile(body: UpdateProfileRequest): HttpResponse =
        client.patch("$baseUrl/api/me") { setBody(body) }
}
