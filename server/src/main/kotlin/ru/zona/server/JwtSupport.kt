package ru.zona.server

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.JWTVerifier
import io.ktor.server.auth.Principal
import io.ktor.server.auth.principal
import io.ktor.server.routing.RoutingContext
import java.util.Date

data class ZonaPrincipal(
    val userId: Long,
    val role: UserRole,
) : Principal

class JwtSupport(secret: String) {
    private val algorithm = Algorithm.HMAC256(secret.toByteArray())

    val verifier: JWTVerifier =
        JWT
            .require(algorithm)
            .build()

    fun token(
        userId: Long,
        role: UserRole,
        ttlMs: Long = 7L * 24 * 60 * 60 * 1000,
    ): String =
        JWT
            .create()
            .withSubject(userId.toString())
            .withClaim("role", role.name)
            .withExpiresAt(Date(System.currentTimeMillis() + ttlMs))
            .sign(algorithm)
}

fun RoutingContext.zonaPrincipal(): ZonaPrincipal? = call.principal<ZonaPrincipal>()

fun RoutingContext.requirePrincipal(): ZonaPrincipal =
    zonaPrincipal() ?: throw IllegalStateException("unauthorized")
