package ru.zona.app.feature.health.domain

import ru.zona.app.core.result.Outcome

interface HealthRepository {
    suspend fun ping(): Outcome<String>
}
