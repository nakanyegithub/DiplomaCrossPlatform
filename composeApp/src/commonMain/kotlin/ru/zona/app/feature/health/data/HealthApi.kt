package ru.zona.app.feature.health.data

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import kotlinx.serialization.Serializable

@Serializable
data class HealthDto(val status: String, val name: String)

class HealthApi(
    private val client: HttpClient,
    private val baseUrl: String,
) {
    suspend fun health(): HttpResponse = client.get("$baseUrl/health")
}
