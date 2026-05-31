package ru.zona.app.feature.health.data

import io.ktor.client.call.body
import ru.zona.app.core.network.safeApiCall
import ru.zona.app.core.result.Outcome
import ru.zona.app.core.result.map
import ru.zona.app.feature.health.domain.HealthRepository

class HealthRepositoryImpl(
    private val api: HealthApi,
) : HealthRepository {
    override suspend fun ping(): Outcome<String> =
        safeApiCall(
            request = { api.health() },
            decode = { it.body<HealthDto>() },
        ).map { it.name }
}
