package com.example

import com.example.routes.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.routing.*
import java.io.File

fun Application.configureRouting() {
    routing {
        healthRoutes()
        authRoutes()
        inspectionRoutes()
        reportRoutes()
        verificationRoutes()
        
        staticFiles("/uploads", File("uploads"))
    }
}
