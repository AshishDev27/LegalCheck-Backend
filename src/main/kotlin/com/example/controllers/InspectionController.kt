package com.example.controllers

import com.example.dtos.ExtractionRequest
import com.example.dtos.InspectionCreateRequest
import com.example.dtos.ProductDeclarationDto
import com.example.services.InspectionService
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.utils.io.jvm.javaio.*

object InspectionController {

    /** Multipart images are capped so a malformed upload cannot exhaust the server's heap. */
    private const val MAX_IMAGE_BYTES = 12 * 1024 * 1024

    suspend fun create(call: ApplicationCall) {
        val userId = call.getUserId()
        val request = call.receive<InspectionCreateRequest>()
        call.respond(HttpStatusCode.Created, InspectionService.createInspection(userId, request))
    }

    suspend fun getById(call: ApplicationCall) {
        call.respond(HttpStatusCode.OK, InspectionService.getInspection(call.inspectionId()))
    }

    suspend fun delete(call: ApplicationCall) {
        val id = call.inspectionId()
        InspectionService.deleteInspection(id)
        call.respond(HttpStatusCode.NoContent)
    }

    suspend fun getAll(call: ApplicationCall) {
        call.respond(HttpStatusCode.OK, InspectionService.getAllInspections(call.getUserId()))
    }

    suspend fun uploadImage(call: ApplicationCall) {
        val inspectionId = call.inspectionId()
        val multipart = call.receiveMultipart()

        var fileBytes: ByteArray? = null
        var fileName: String? = null
        var type = "FRONT"
        var ocrText: String? = null

        multipart.forEachPart { part ->
            when (part) {
                is PartData.FileItem -> {
                    fileBytes = part.provider().toInputStream().readBytes()
                    fileName = part.originalFileName
                }
                is PartData.FormItem -> when (part.name) {
                    "type" -> type = part.value
                    "ocrText" -> ocrText = part.value
                }
                else -> Unit
            }
            part.dispose()
        }

        val bytes = fileBytes
        if (bytes == null || bytes.isEmpty()) {
            return call.respond(HttpStatusCode.BadRequest, mapOf("message" to "No image file was included in the upload."))
        }
        if (bytes.size > MAX_IMAGE_BYTES) {
            return call.respond(HttpStatusCode.PayloadTooLarge, mapOf("message" to "The uploaded image exceeds the 12 MB limit."))
        }

        val response = InspectionService.uploadImage(
            inspectionId = inspectionId,
            fileBytes = bytes,
            fileName = fileName ?: "panel.jpg",
            type = type,
            ocrText = ocrText?.takeIf { it.isNotBlank() }
        )
        call.respond(HttpStatusCode.Created, response)
    }

    suspend fun analyze(call: ApplicationCall) {
        val id = call.inspectionId()
        val declaration = call.receive<ProductDeclarationDto>()
        call.respond(HttpStatusCode.OK, InspectionService.analyzeInspection(id, declaration))
    }

    /**
     * Combines the scanned panels into one product. A body carrying fresh OCR text is optional:
     * without it the server falls back to the text stored alongside the uploaded images.
     */
    suspend fun extract(call: ApplicationCall) {
        val id = call.inspectionId()
        val request = try {
            call.receiveNullable<ExtractionRequest>()
        } catch (_: Exception) {
            null
        }
        call.respond(HttpStatusCode.OK, InspectionService.extractDeclarations(id, request))
    }

    private fun ApplicationCall.inspectionId(): Int =
        parameters["id"]?.toIntOrNull() ?: throw IllegalArgumentException("Invalid inspection id")

    private fun ApplicationCall.getUserId(): Int {
        val principal = principal<JWTPrincipal>()
        return principal?.payload?.getClaim("userId")?.asString()?.toIntOrNull()
            ?: throw IllegalArgumentException("Unauthorized")
    }
}
