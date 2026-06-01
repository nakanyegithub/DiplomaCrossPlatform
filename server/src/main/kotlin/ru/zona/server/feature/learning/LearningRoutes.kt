package ru.zona.server.feature.learning

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import ru.zona.server.plugins.ApiException
import ru.zona.server.security.AUTH_JWT
import ru.zona.server.security.requireUserId

fun Route.learningRoutes(service: LearningService) {
    authenticate(AUTH_JWT) {
        get("/api/courses") {
            val q = call.request.queryParameters["q"]
            call.respond(service.catalog(requireUserId(), q))
        }
        get("/api/courses/my") {
            call.respond(service.myCourses(requireUserId()))
        }
        get("/api/courses/teaching") {
            call.respond(service.teachingCourses(requireUserId()))
        }
        get("/api/courses/{id}") {
            val id = call.parameters["id"]?.toLongOrNull() ?: throw ApiException(HttpStatusCode.BadRequest, "id")
            call.respond(service.detail(id, requireUserId()))
        }
        post("/api/courses/{id}/enroll") {
            val id = call.parameters["id"]?.toLongOrNull() ?: throw ApiException(HttpStatusCode.BadRequest, "id")
            call.respond(service.enroll(id, requireUserId()))
        }
        get("/api/lessons/{id}/exercises") {
            val id = call.parameters["id"]?.toLongOrNull() ?: throw ApiException(HttpStatusCode.BadRequest, "id")
            call.respond(service.exercises(id, requireUserId()))
        }
        post("/api/exercises/{id}/submit") {
            val id = call.parameters["id"]?.toLongOrNull() ?: throw ApiException(HttpStatusCode.BadRequest, "id")
            call.respond(service.submitAnswer(id, requireUserId(), call.receive<SubmitAnswerRequest>().answer))
        }

        // teacher authoring
        post("/api/courses") {
            call.respond(service.createCourse(requireUserId(), call.receive<CreateCourseRequest>()))
        }
        post("/api/courses/{id}/lessons") {
            val id = call.parameters["id"]?.toLongOrNull() ?: throw ApiException(HttpStatusCode.BadRequest, "id")
            call.respond(service.addLesson(requireUserId(), id, call.receive<CreateLessonRequest>()))
        }
        post("/api/lessons/{id}/exercises") {
            val id = call.parameters["id"]?.toLongOrNull() ?: throw ApiException(HttpStatusCode.BadRequest, "id")
            call.respond(service.addExercise(requireUserId(), id, call.receive<CreateExerciseRequest>()))
        }
        delete("/api/courses/{id}") {
            val id = call.parameters["id"]?.toLongOrNull() ?: throw ApiException(HttpStatusCode.BadRequest, "id")
            service.deleteCourse(requireUserId(), id); call.respond(io.ktor.http.HttpStatusCode.NoContent)
        }
        delete("/api/lessons/{id}") {
            val id = call.parameters["id"]?.toLongOrNull() ?: throw ApiException(HttpStatusCode.BadRequest, "id")
            service.deleteLesson(requireUserId(), id); call.respond(io.ktor.http.HttpStatusCode.NoContent)
        }
        delete("/api/exercises/{id}") {
            val id = call.parameters["id"]?.toLongOrNull() ?: throw ApiException(HttpStatusCode.BadRequest, "id")
            service.deleteExercise(requireUserId(), id); call.respond(io.ktor.http.HttpStatusCode.NoContent)
        }
    }
}
