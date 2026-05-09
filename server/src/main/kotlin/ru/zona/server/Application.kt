package ru.zona.server

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import kotlinx.serialization.json.Json

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    val host = System.getenv("ZONA_BIND_HOST") ?: "0.0.0.0"
    embeddedServer(Netty, port = port, host = host, module = Application::module).start(wait = true)
}

fun Application.module() {
    val jdbcUrl =
        System.getenv("ZONA_JDBC_URL")
            ?: run {
                val dir = System.getenv("ZONA_H2_DIR")?.trimEnd('/', '\\') ?: "./data"
                "jdbc:h2:file:$dir/zona;AUTO_SERVER=TRUE;MODE=PostgreSQL"
            }
    val isH2 = jdbcUrl.startsWith("jdbc:h2")
    val dbUser = System.getenv("ZONA_DB_USER") ?: if (isH2) "sa" else "zona"
    val dbPassword = System.getenv("ZONA_DB_PASSWORD") ?: if (isH2) "" else "zona"
    initDatabase(jdbcUrl, dbUser, dbPassword)
    seedIfEmpty()

    val jwtSecret = System.getenv("ZONA_JWT_SECRET") ?: "dev-zona-secret-change-me-32bytes-min!!"
    val jwt = JwtSupport(jwtSecret)

    val json =
        Json {
            ignoreUnknownKeys = true
            prettyPrint = false
            isLenient = true
        }

    install(ContentNegotiation) {
        json(json)
    }

    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        anyHost()
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            cause.printStackTrace()
            call.respond(
                io.ktor.http.HttpStatusCode.InternalServerError,
                ErrorResponse(cause.message ?: "Ошибка сервера"),
            )
        }
    }

    install(Authentication) {
        jwt("jwt") {
            realm = "Zona"
            verifier(jwt.verifier)
            validate { credential ->
                val sub = credential.payload.subject?.toLongOrNull() ?: return@validate null
                val roleName = credential.payload.getClaim("role").asString() ?: return@validate null
                val role = runCatching { UserRole.valueOf(roleName) }.getOrNull() ?: return@validate null
                ZonaPrincipal(sub, role)
            }
        }
    }

    configureRouting(jwt)
}
