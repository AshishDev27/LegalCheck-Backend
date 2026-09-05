package com.example.routes

import com.example.controllers.AuthController
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*

fun Route.authRoutes() {
    route("/api/v1/auth") {
        post("/register") {
            AuthController.register(call)
        }
        post("/login") {
            AuthController.login(call)
        }
        authenticate("auth-jwt") {
            get("/me") {
                AuthController.getMe(call)
            }
        }
    }
}
