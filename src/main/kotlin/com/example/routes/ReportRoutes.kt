package com.example.routes

import com.example.services.ReportService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.reportRoutes() {
    authenticate("auth-jwt") {
        get("/api/v1/inspections/{id}/report") {
            val id = call.parameters["id"]?.toIntOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid ID")
            try {
                val reportBytes = ReportService.generateReport(id)
                call.response.header(
                    HttpHeaders.ContentDisposition,
                    ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, "report_$id.pdf").toString()
                )
                call.respondBytes(reportBytes, ContentType.Application.Pdf)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "Error generating report: ${e.message}")
            }
        }
    }
}
