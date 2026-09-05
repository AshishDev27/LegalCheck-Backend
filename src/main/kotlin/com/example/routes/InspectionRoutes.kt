package com.example.routes

import com.example.controllers.InspectionController
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.inspectionRoutes() {
    authenticate("auth-jwt") {
        route("/api/v1/inspections") {
            post {
                InspectionController.create(call)
            }
            get {
                InspectionController.getAll(call)
            }
            get("/{id}") {
                InspectionController.getById(call)
            }
            delete("/{id}") {
                InspectionController.delete(call)
            }
            post("/{id}/images") {
                InspectionController.uploadImage(call)
            }
            post("/{id}/analyze") {
                InspectionController.analyze(call)
            }
            post("/{id}/extract") {
                InspectionController.extract(call)
            }
            post("/{id}/qr") {
                val id = call.parameters["id"]?.toIntOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid ID")
                val request = call.receive<Map<String, String>>()
                com.example.services.InspectionService.saveQrResult(
                    id,
                    request["codeType"] ?: "",
                    request["rawValue"] ?: "",
                    request["format"] ?: ""
                )
                call.respond(HttpStatusCode.OK)
            }
        }
    }
}
