package ru.zona.server

import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import ru.zona.server.db.ZonaDatabase
import ru.zona.server.db.ZonaSeed
import ru.zona.server.feature.auth.AuthDao
import ru.zona.server.feature.auth.AuthService
import ru.zona.server.feature.auth.authRoutes
import ru.zona.server.feature.chat.ChatService
import ru.zona.server.feature.chat.chatRoutes
import ru.zona.server.feature.flashcards.FlashcardService
import ru.zona.server.feature.flashcards.flashcardRoutes
import ru.zona.server.feature.learning.LearningService
import ru.zona.server.feature.learning.learningRoutes
import ru.zona.server.feature.sessions.SessionService
import ru.zona.server.feature.sessions.sessionRoutes
import ru.zona.server.feature.teacher.TeacherService
import ru.zona.server.feature.teacher.teacherRoutes
import ru.zona.server.feature.profile.CertificateService
import ru.zona.server.feature.profile.certificateRoutes
import ru.zona.server.feature.wallet.WalletService
import ru.zona.server.feature.wallet.walletRoutes
import ru.zona.server.plugins.configurePlugins
import ru.zona.server.security.JwtService
import ru.zona.server.security.configureAuth

@Serializable
data class HealthDto(val status: String, val name: String)

fun main() {
    val config = ServerConfig.fromEnv()
    ZonaDatabase.init(config)
    embeddedServer(Netty, port = config.port, host = "0.0.0.0") {
        module(config)
    }.start(wait = true)
}

fun Application.module(config: ServerConfig) {
    configurePlugins()

    val jwtService = JwtService(config)
    configureAuth(jwtService)

    // Composition root (server): сборка слоёв фич.
    val authDao = AuthDao()
    val authService = AuthService(authDao, jwtService)
    val walletService = WalletService()
    val learningService = LearningService(walletService)
    val flashcardService = FlashcardService()
    val chatService = ChatService()
    val sessionService = SessionService(walletService, chatService)
    val teacherService = TeacherService()
    val certificateService = CertificateService()

    ZonaSeed.seedIfEmpty(authDao, learningService, flashcardService, sessionService, walletService)

    routing {
        get("/health") { call.respond(HealthDto(status = "ok", name = "Zona Server")) }
        authRoutes(authService)
        walletRoutes(walletService)
        learningRoutes(learningService)
        flashcardRoutes(flashcardService)
        sessionRoutes(sessionService)
        chatRoutes(chatService)
        teacherRoutes(teacherService)
        certificateRoutes(certificateService)
    }
}
