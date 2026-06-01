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

    val authDao = AuthDao()
    ZonaSeed.seedIfEmpty(authDao)
    val authService = AuthService(authDao, jwtService)

    routing {
        get("/health") {
            call.respond(HealthDto(status = "ok", name = "Zona Server"))
        }
        authRoutes(authService)
    }
}
