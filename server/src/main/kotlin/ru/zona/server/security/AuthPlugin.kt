package ru.zona.server.security

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.routing.RoutingContext
import ru.zona.server.plugins.ApiException

data class UserPrincipal(val userId: Long)

const val AUTH_JWT = "auth-jwt"

fun Application.configureAuth(jwtService: JwtService) {
    install(Authentication) {
        jwt(AUTH_JWT) {
            verifier(jwtService.verifier)
            validate { credential ->
                val uid = credential.payload.getClaim(JwtService.CLAIM_UID).asLong()
                uid?.let { UserPrincipal(it) }
            }
        }
    }
}

/** userId текущего запроса; бросает 401, если токен отсутствует/невалиден. */
fun RoutingContext.requireUserId(): Long {
    val principal = call.authentication.principal<UserPrincipal>()
        ?: throw ApiException(HttpStatusCode.Unauthorized, "Требуется авторизация")
    return principal.userId
}
