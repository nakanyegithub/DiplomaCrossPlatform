package ru.zona.server.feature.profile

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import ru.zona.server.db.UserCertificates
import ru.zona.server.plugins.ApiException
import ru.zona.server.security.AUTH_JWT
import ru.zona.server.security.requireUserId

@Serializable
data class CertificateDto(val id: Long, val fileName: String, val createdAt: Long)

@Serializable
data class AddCertificateRequest(val fileName: String)

class CertificateService {
    fun list(userId: Long): List<CertificateDto> =
        transaction {
            UserCertificates.selectAll().where { UserCertificates.userId eq userId }
                .orderBy(UserCertificates.createdAt to SortOrder.DESC)
                .map { CertificateDto(it[UserCertificates.id], it[UserCertificates.fileName], it[UserCertificates.createdAt]) }
        }

    fun add(userId: Long, fileName: String): CertificateDto =
        transaction {
            if (fileName.isBlank()) throw ApiException(HttpStatusCode.UnprocessableEntity, "Пустое имя файла")
            val now = System.currentTimeMillis()
            val id = UserCertificates.insert {
                it[UserCertificates.userId] = userId
                it[UserCertificates.fileName] = fileName.trim()
                it[createdAt] = now
            }[UserCertificates.id]
            CertificateDto(id, fileName.trim(), now)
        }

    fun remove(userId: Long, certId: Long) {
        transaction {
            UserCertificates.deleteWhere { (UserCertificates.id eq certId) and (UserCertificates.userId eq userId) }
        }
    }
}

fun Route.certificateRoutes(service: CertificateService) {
    authenticate(AUTH_JWT) {
        get("/api/me/certificates") { call.respond(service.list(requireUserId())) }
        post("/api/me/certificates") {
            call.respond(service.add(requireUserId(), call.receive<AddCertificateRequest>().fileName))
        }
        delete("/api/me/certificates/{id}") {
            val id = call.parameters["id"]?.toLongOrNull() ?: throw ApiException(HttpStatusCode.BadRequest, "id")
            service.remove(requireUserId(), id); call.respond(HttpStatusCode.NoContent)
        }
    }
}
