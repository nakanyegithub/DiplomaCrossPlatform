package ru.zona.server.feature.auth

import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import ru.zona.server.security.AUTH_JWT
import ru.zona.server.security.requireUserId
import io.ktor.server.auth.authenticate

fun Route.authRoutes(service: AuthService) {
    route("/api/auth") {
        post("/register") {
            call.respond(service.register(call.receive<RegisterRequest>()))
        }
        post("/login") {
            call.respond(service.login(call.receive<LoginRequest>()))
        }
    }
    authenticate(AUTH_JWT) {
        get("/api/me") {
            call.respond(service.me(requireUserId()))
        }
        patch("/api/me") {
            call.respond(service.updateProfile(requireUserId(), call.receive<UpdateProfileRequest>()))
        }
    }
}
