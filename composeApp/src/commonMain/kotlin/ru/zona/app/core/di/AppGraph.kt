package ru.zona.app.core.di

import io.ktor.client.HttpClient
import ru.zona.app.core.network.TokenStorage
import ru.zona.app.core.network.createHttpClient
import ru.zona.app.core.network.createSettings
import ru.zona.app.core.network.defaultApiBaseUrl
import ru.zona.app.feature.auth.data.AuthApi
import ru.zona.app.feature.auth.data.AuthRepositoryImpl
import ru.zona.app.feature.auth.domain.AuthRepository
import ru.zona.app.feature.profile.data.ProfileApi
import ru.zona.app.feature.profile.data.ProfileRepositoryImpl
import ru.zona.app.feature.profile.domain.ProfileRepository
import ru.zona.app.feature.health.data.HealthApi
import ru.zona.app.feature.health.data.HealthRepositoryImpl
import ru.zona.app.feature.health.domain.HealthRepository

/**
 * Composition Root: единая точка сборки зависимостей. Заменяет Hilt — работает на всех платформах.
 */
class AppGraph(
    baseUrl: String = defaultApiBaseUrl(),
) {
    val tokenStorage: TokenStorage = TokenStorage(createSettings())
    val httpClient: HttpClient = createHttpClient(baseUrl) { tokenStorage.token }

    // health (Фаза 0)
    private val healthApi = HealthApi(httpClient, baseUrl)
    val healthRepository: HealthRepository = HealthRepositoryImpl(healthApi)

    // auth (Фаза 1)
    private val authApi = AuthApi(httpClient, baseUrl)
    val authRepository: AuthRepository = AuthRepositoryImpl(authApi, tokenStorage)

    // profile (Фаза 1)
    private val profileApi = ProfileApi(httpClient, baseUrl)
    val profileRepository: ProfileRepository = ProfileRepositoryImpl(profileApi)
}
