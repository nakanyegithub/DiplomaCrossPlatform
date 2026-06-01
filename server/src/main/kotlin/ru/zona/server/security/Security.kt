package ru.zona.server.security

import at.favre.lib.crypto.bcrypt.BCrypt
import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import ru.zona.server.ServerConfig

object PasswordHasher {
    fun hash(raw: String): String = BCrypt.withDefaults().hashToString(12, raw.toCharArray())

    fun verify(raw: String, hash: String): Boolean =
        BCrypt.verifyer().verify(raw.toCharArray(), hash).verified
}

/** Выпуск и проверка JWT. userId хранится в claim "uid". */
class JwtService(private val config: ServerConfig) {
    private val algorithm = Algorithm.HMAC256(config.jwtSecret)
    private val validityMs = 1000L * 60 * 60 * 24 * 30 // 30 дней

    val verifier: JWTVerifier =
        JWT.require(algorithm)
            .withIssuer(config.jwtIssuer)
            .withAudience(config.jwtAudience)
            .build()

    fun issue(userId: Long): String =
        JWT.create()
            .withIssuer(config.jwtIssuer)
            .withAudience(config.jwtAudience)
            .withClaim(CLAIM_UID, userId)
            .withExpiresAt(java.util.Date(System.currentTimeMillis() + validityMs))
            .sign(algorithm)

    companion object {
        const val CLAIM_UID = "uid"
    }
}
