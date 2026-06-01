package ru.zona.server

/** Конфигурация сервера из переменных окружения с разумными значениями по умолчанию. */
data class ServerConfig(
    val port: Int,
    val jdbcUrl: String,
    val dbUser: String,
    val dbPassword: String,
    val dbDriver: String,
    val jwtSecret: String,
    val jwtIssuer: String,
    val jwtAudience: String,
) {
    companion object {
        fun fromEnv(): ServerConfig {
            val env = System.getenv()
            // По умолчанию — встроенная H2 (запускается без отдельной БД).
            // Для Postgres задайте DATABASE_URL=jdbc:postgresql://localhost:5432/zona и DB_DRIVER.
            val jdbcUrl = env["DATABASE_URL"] ?: "jdbc:h2:mem:zona;DB_CLOSE_DELAY=-1;MODE=PostgreSQL"
            val driver =
                env["DB_DRIVER"]
                    ?: if (jdbcUrl.startsWith("jdbc:postgresql")) "org.postgresql.Driver" else "org.h2.Driver"
            return ServerConfig(
                port = env["PORT"]?.toIntOrNull() ?: 8080,
                jdbcUrl = jdbcUrl,
                dbUser = env["DATABASE_USER"] ?: "sa",
                dbPassword = env["DATABASE_PASSWORD"] ?: "",
                dbDriver = driver,
                jwtSecret = env["JWT_SECRET"] ?: "zona-dev-secret-change-me",
                jwtIssuer = env["JWT_ISSUER"] ?: "zona",
                jwtAudience = env["JWT_AUDIENCE"] ?: "zona-app",
            )
        }
    }
}
