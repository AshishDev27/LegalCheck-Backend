package com.example.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.healthRoutes() {
    get("/api/v1/health") {
        call.respond(HttpStatusCode.OK, mapOf("status" to "UP"))
    }
}
