package ru.zona.app.core.di

import io.ktor.client.HttpClient
import ru.zona.app.core.network.TokenStorage
import ru.zona.app.core.network.createHttpClient
import ru.zona.app.core.network.createSettings
import ru.zona.app.core.network.defaultApiBaseUrl
import ru.zona.app.feature.health.data.HealthApi
import ru.zona.app.feature.health.data.HealthRepositoryImpl
import ru.zona.app.feature.health.domain.HealthRepository

/**
 * Composition Root: единая точка сборки зависимостей. Заменяет Hilt — работает на всех платформах.
 * По мере роста сюда добавляются репозитории и use-cases фич.
 */
class AppGraph(
    baseUrl: String = defaultApiBaseUrl(),
) {
    val httpClient: HttpClient = createHttpClient(baseUrl)
    val tokenStorage: TokenStorage = TokenStorage(createSettings())

    // --- feature: health (проверка соединения, Фаза 0) ---
    private val healthApi = HealthApi(httpClient, baseUrl)
    val healthRepository: HealthRepository = HealthRepositoryImpl(healthApi)
}
