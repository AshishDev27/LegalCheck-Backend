package com.example.routes

import com.example.dtos.VerificationResult
import com.example.repositories.InspectionRepository
import com.example.services.InspectionService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.verificationRoutes() {
    // Public endpoint - no authentication required
    get("/api/v1/verify/{code}") {
        val verificationCode = call.parameters["code"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing verification code")

        val inspectionId = verificationCode.toIntOrNull()
        if (inspectionId == null) {
            call.respond(HttpStatusCode.NotFound, "Invalid verification code format")
            return@get
        }

        try {
            val inspection = InspectionService.getInspection(inspectionId)
            val analysis = InspectionRepository.findComplianceResult(inspectionId)

            // A verification is a public statement about an official record, so it reports the
            // stored verdict or says none exists. It never presents a placeholder as a finding.
            val result = if (analysis == null) {
                VerificationResult(
                    inspectionId = inspection.id,
                    productName = inspection.productName,
                    inspectionDate = inspection.createdAt,
                    complianceScore = 0,
                    complianceStatus = "NOT_ANALYSED",
                    verificationStatus = "VERIFIED",
                    message = "This inspection is an official record, but its compliance analysis has not been completed yet."
                )
            } else {
                VerificationResult(
                    inspectionId = inspection.id,
                    productName = inspection.productName,
                    inspectionDate = inspection.createdAt,
                    complianceScore = analysis.score.toInt(),
                    complianceStatus = analysis.status,
                    verificationStatus = "VERIFIED",
                    message = "This inspection was officially conducted by the Legal Metrology Department."
                )
            }

            call.respond(HttpStatusCode.OK, result)
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.NotFound,
                VerificationResult(
                    inspectionId = 0,
                    productName = "Unknown",
                    inspectionDate = "",
                    complianceScore = 0,
                    complianceStatus = "UNKNOWN",
                    verificationStatus = "INVALID",
                    message = "The provided verification code does not match any official records."
                )
            )
        }
    }
}
