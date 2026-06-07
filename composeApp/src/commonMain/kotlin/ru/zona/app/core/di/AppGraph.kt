package ru.zona.app.core.di

import io.ktor.client.HttpClient
import ru.zona.app.core.network.TokenStorage
import ru.zona.app.core.network.createHttpClient
import ru.zona.app.core.network.createSettings
import ru.zona.app.core.network.defaultApiBaseUrl
import ru.zona.app.feature.auth.data.AuthApi
import ru.zona.app.feature.auth.data.AuthRepositoryImpl
import ru.zona.app.feature.auth.domain.AuthRepository
import ru.zona.app.feature.chat.ChatApi
import ru.zona.app.feature.chat.ChatRepository
import ru.zona.app.feature.chat.ChatRepositoryImpl
import ru.zona.app.feature.flashcards.FlashcardApi
import ru.zona.app.feature.flashcards.FlashcardRepository
import ru.zona.app.feature.flashcards.FlashcardRepositoryImpl
import ru.zona.app.feature.learning.data.LearningApi
import ru.zona.app.feature.learning.data.LearningRepositoryImpl
import ru.zona.app.feature.learning.domain.LearningRepository
import ru.zona.app.feature.profile.data.ProfileApi
import ru.zona.app.feature.profile.data.ProfileRepositoryImpl
import ru.zona.app.feature.profile.domain.ProfileRepository
import ru.zona.app.feature.sessions.SessionApi
import ru.zona.app.feature.sessions.SessionRepository
import ru.zona.app.feature.sessions.SessionRepositoryImpl
import ru.zona.app.feature.teacher.TeacherApi
import ru.zona.app.feature.teacher.TeacherRepository
import ru.zona.app.feature.teacher.TeacherRepositoryImpl
import ru.zona.app.feature.wallet.WalletApi
import ru.zona.app.feature.wallet.WalletRepository
import ru.zona.app.feature.wallet.WalletRepositoryImpl

/**
 * Composition Root: единая точка сборки зависимостей. Заменяет Hilt — работает на всех платформах.
 */
class AppGraph(
    baseUrl: String = defaultApiBaseUrl(),
) {
    val tokenStorage: TokenStorage = TokenStorage(createSettings())
    val httpClient: HttpClient = createHttpClient(baseUrl) { tokenStorage.token }

    val authRepository: AuthRepository = AuthRepositoryImpl(AuthApi(httpClient, baseUrl), tokenStorage)
    val profileRepository: ProfileRepository = ProfileRepositoryImpl(ProfileApi(httpClient, baseUrl))
    val certificateRepository: ru.zona.app.feature.profile.CertificateRepository =
        ru.zona.app.feature.profile.CertificateRepositoryImpl(ru.zona.app.feature.profile.CertificateApi(httpClient, baseUrl))
    val learningRepository: LearningRepository = LearningRepositoryImpl(LearningApi(httpClient, baseUrl))
    val walletRepository: WalletRepository = WalletRepositoryImpl(WalletApi(httpClient, baseUrl))
    val flashcardRepository: FlashcardRepository = FlashcardRepositoryImpl(FlashcardApi(httpClient, baseUrl))
    val sessionRepository: SessionRepository = SessionRepositoryImpl(SessionApi(httpClient, baseUrl))
    val chatRepository: ChatRepository = ChatRepositoryImpl(ChatApi(httpClient, baseUrl))
    val teacherRepository: TeacherRepository = TeacherRepositoryImpl(TeacherApi(httpClient, baseUrl))
}
