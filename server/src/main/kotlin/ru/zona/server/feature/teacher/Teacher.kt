package ru.zona.server.feature.teacher

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import ru.zona.server.db.TeacherApplicationDocs
import ru.zona.server.db.TeacherApplications
import ru.zona.server.db.Users
import ru.zona.server.plugins.ApiException
import ru.zona.server.security.AUTH_JWT
import ru.zona.server.security.requireUserId

@Serializable
data class TeacherDto(
    val id: Long,
    val displayName: String,
    val headline: String,
    val bio: String,
    val avatarUrl: String?,
    val pricePerHourCents: Long?,
    val ratingAvg: Double?,
    val ratingCount: Long,
)

@Serializable
data class DocDto(val id: Long, val fileName: String, val description: String)

@Serializable
data class TeacherApplicationDto(
    val id: Long,
    val userId: Long,
    val userName: String,
    val motivation: String,
    val headline: String,
    val status: String,
    val adminMessage: String?,
    val createdAt: Long,
    val documents: List<DocDto>,
)

@Serializable
data class SubmitApplicationRequest(
    val motivation: String,
    val headline: String = "",
    val documents: List<DocInput> = emptyList(),
)

@Serializable
data class DocInput(val fileName: String, val description: String = "")

@Serializable
data class AdminDecisionRequest(val message: String? = null)

class TeacherService {
    fun listTeachers(): List<TeacherDto> =
        transaction {
            Users.selectAll().where { Users.role eq "TEACHER" }
                .map {
                    val rc = it[Users.ratingCount]
                    TeacherDto(
                        id = it[Users.id],
                        displayName = it[Users.displayName],
                        headline = it[Users.headline],
                        bio = it[Users.bio],
                        avatarUrl = it[Users.avatarUrl],
                        pricePerHourCents = it[Users.pricePerHourCents],
                        ratingAvg = if (rc > 0) it[Users.ratingSum].toDouble() / rc else null,
                        ratingCount = rc,
                    )
                }
        }

    fun myApplication(userId: Long): TeacherApplicationDto? =
        transaction {
            TeacherApplications.selectAll().where { TeacherApplications.userId eq userId }
                .orderBy(TeacherApplications.createdAt to SortOrder.DESC)
                .firstOrNull()?.let { appDto(it[TeacherApplications.id]) }
        }

    fun submit(userId: Long, req: SubmitApplicationRequest): TeacherApplicationDto =
        transaction {
            val role = Users.selectAll().where { Users.id eq userId }.first()[Users.role]
            if (role == "TEACHER") throw ApiException(HttpStatusCode.Conflict, "Вы уже преподаватель")
            if (req.motivation.isBlank()) throw ApiException(HttpStatusCode.UnprocessableEntity, "Расскажите о себе")
            // Запрещаем дубль активной заявки.
            val pending =
                TeacherApplications.selectAll()
                    .where { (TeacherApplications.userId eq userId) and (TeacherApplications.status eq "PENDING") }
                    .limit(1).count() > 0
            if (pending) throw ApiException(HttpStatusCode.Conflict, "Заявка уже на рассмотрении")
            val id =
                TeacherApplications.insert {
                    it[TeacherApplications.userId] = userId
                    it[motivation] = req.motivation.trim()
                    it[headline] = req.headline.trim()
                    it[status] = "PENDING"
                    it[createdAt] = System.currentTimeMillis()
                }[TeacherApplications.id]
            req.documents.filter { it.fileName.isNotBlank() }.forEach { d ->
                TeacherApplicationDocs.insert {
                    it[applicationId] = id
                    it[fileName] = d.fileName.trim()
                    it[description] = d.description.trim()
                }
            }
            appDto(id)!!
        }

    fun pendingApplications(): List<TeacherApplicationDto> =
        transaction {
            TeacherApplications.selectAll().where { TeacherApplications.status eq "PENDING" }
                .orderBy(TeacherApplications.createdAt to SortOrder.ASC)
                .mapNotNull { appDto(it[TeacherApplications.id]) }
        }

    fun approve(adminId: Long, applicationId: Long): TeacherApplicationDto =
        transaction {
            requireAdmin(adminId)
            val app = TeacherApplications.selectAll().where { TeacherApplications.id eq applicationId }.firstOrNull()
                ?: throw ApiException(HttpStatusCode.NotFound, "Заявка не найдена")
            val applicantId = app[TeacherApplications.userId]
            TeacherApplications.update({ TeacherApplications.id eq applicationId }) {
                it[status] = "APPROVED"
                it[decidedAt] = System.currentTimeMillis()
            }
            Users.update({ Users.id eq applicantId }) {
                it[role] = "TEACHER"
                it[headline] = app[TeacherApplications.headline]
            }
            appDto(applicationId)!!
        }

    fun reject(adminId: Long, applicationId: Long, message: String?): TeacherApplicationDto =
        transaction {
            requireAdmin(adminId)
            TeacherApplications.selectAll().where { TeacherApplications.id eq applicationId }.firstOrNull()
                ?: throw ApiException(HttpStatusCode.NotFound, "Заявка не найдена")
            TeacherApplications.update({ TeacherApplications.id eq applicationId }) {
                it[status] = "REJECTED"
                it[adminMessage] = message?.trim()
                it[decidedAt] = System.currentTimeMillis()
            }
            appDto(applicationId)!!
        }

    private fun requireAdmin(userId: Long) {
        val role = Users.selectAll().where { Users.id eq userId }.firstOrNull()?.get(Users.role)
        if (role != "ADMIN") throw ApiException(HttpStatusCode.Forbidden, "Только для администратора")
    }

    private fun appDto(id: Long): TeacherApplicationDto? {
        val a = TeacherApplications.selectAll().where { TeacherApplications.id eq id }.firstOrNull() ?: return null
        val userName = Users.selectAll().where { Users.id eq a[TeacherApplications.userId] }.firstOrNull()?.get(Users.displayName) ?: ""
        val docs = TeacherApplicationDocs.selectAll().where { TeacherApplicationDocs.applicationId eq id }
            .map { DocDto(it[TeacherApplicationDocs.id], it[TeacherApplicationDocs.fileName], it[TeacherApplicationDocs.description]) }
        return TeacherApplicationDto(
            id = id,
            userId = a[TeacherApplications.userId],
            userName = userName,
            motivation = a[TeacherApplications.motivation],
            headline = a[TeacherApplications.headline],
            status = a[TeacherApplications.status],
            adminMessage = a[TeacherApplications.adminMessage],
            createdAt = a[TeacherApplications.createdAt],
            documents = docs,
        )
    }
}

fun Route.teacherRoutes(service: TeacherService) {
    authenticate(AUTH_JWT) {
        get("/api/teachers") { call.respond(service.listTeachers()) }
        get("/api/teacher-application") {
            val app = service.myApplication(requireUserId())
            if (app == null) call.respond(HttpStatusCode.NoContent) else call.respond(app)
        }
        post("/api/teacher-application") {
            call.respond(service.submit(requireUserId(), call.receive<SubmitApplicationRequest>()))
        }
        get("/api/admin/applications") { call.respond(service.pendingApplications()) }
        post("/api/admin/applications/{id}/approve") {
            val id = call.parameters["id"]?.toLongOrNull() ?: throw ApiException(HttpStatusCode.BadRequest, "id")
            call.respond(service.approve(requireUserId(), id))
        }
        post("/api/admin/applications/{id}/reject") {
            val id = call.parameters["id"]?.toLongOrNull() ?: throw ApiException(HttpStatusCode.BadRequest, "id")
            call.respond(service.reject(requireUserId(), id, call.receive<AdminDecisionRequest>().message))
        }
    }
}
