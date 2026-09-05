package com.example.services

import com.example.compliance.core.ComplianceResult
import com.example.compliance.core.ComplianceRuleEngine
import com.example.compliance.rules.v2026.LmpcRuleSet
import com.example.dtos.ExtractionRequest
import com.example.dtos.InspectionCreateRequest
import com.example.dtos.InspectionImageResponse
import com.example.dtos.InspectionResponse
import com.example.dtos.ProductDeclarationDto
import com.example.repositories.InspectionRepository
import com.example.services.extraction.OcrSide
import com.example.services.extraction.ProductExtractor
import com.example.services.validation.DeclarationValidator
import org.slf4j.LoggerFactory
import java.io.File
import java.util.UUID

object InspectionService {

    private val logger = LoggerFactory.getLogger(InspectionService::class.java)
    private val ruleEngine = ComplianceRuleEngine(LmpcRuleSet.all())

    /**
     * Runs the compliance rules against a declaration the inspector has reviewed.
     *
     * The declaration is validated first so that a field which is present but unusable cannot
     * pass a rule, and both the declaration and the result are persisted for the audit trail.
     */
    suspend fun analyzeInspection(id: Int, declaration: ProductDeclarationDto): ComplianceResult {
        val validation = DeclarationValidator.validate(declaration)
        val result = ruleEngine.analyze(id, declaration, validation)

        InspectionRepository.saveDeclaration(id, declaration)
        InspectionRepository.saveComplianceResult(id, result)

        logger.info(
            "Inspection {} analysed: score={} status={} checks={} extractionConfidence={}",
            id, result.score, result.status, result.checks.size, declaration.confidence
        )
        return result
    }

    /**
     * Builds one structured product from the OCR text of every panel scanned for this inspection.
     *
     * Text supplied in [request] takes priority (it is the text the device just recognised);
     * anything not supplied falls back to the OCR text stored with the uploaded images. If no
     * panel produced any text at all the caller gets an empty declaration with zero confidence
     * rather than a fabricated one.
     */
    suspend fun extractDeclarations(id: Int, request: ExtractionRequest? = null): ProductDeclarationDto {
        val storedImages = InspectionRepository.findImagesByInspectionId(id)
        val storedText = storedImages
            .filter { !it.ocrText.isNullOrBlank() }
            .associate { it.type.uppercase() to it.ocrText!! }

        val sides = buildList {
            val front = request?.frontOcrText?.takeIf { it.isNotBlank() } ?: storedText["FRONT"]
            val back = request?.backOcrText?.takeIf { it.isNotBlank() } ?: storedText["BACK"]

            front?.let { add(OcrSide("FRONT", it)) }
            back?.let { add(OcrSide("BACK", it)) }

            val additional = request?.additionalOcrText?.filter { it.isNotBlank() }
                ?: listOfNotNull(storedText["ADDITIONAL"])
            additional.forEach { add(OcrSide("ADDITIONAL", it)) }
        }

        if (sides.isEmpty()) {
            logger.warn("Inspection {}: no OCR text available from {} uploaded image(s)", id, storedImages.size)
            return ProductDeclarationDto(confidence = 0f)
        }

        val qrValues = InspectionRepository.findQrByInspectionId(id).mapNotNull { it["rawValue"] }
        val declaration = ProductExtractor.extract(sides, qrValues)

        logger.info(
            "Inspection {}: extracted {} field(s) from {} panel(s), confidence={}",
            id, declaration.fieldConfidence.size, sides.size, declaration.confidence
        )
        return declaration
    }

    suspend fun createInspection(userId: Int, request: InspectionCreateRequest): InspectionResponse {
        val id = InspectionRepository.create(userId, request.productName)
        val result = InspectionRepository.findById(id)!!
        return result.toResponse()
    }

    suspend fun getInspection(id: Int): InspectionResponse {
        val inspection = InspectionRepository.findById(id) ?: throw IllegalArgumentException("Inspection not found")
        val images = InspectionRepository.findImagesByInspectionId(id)
        return inspection.toResponse(images.map { it.toResponse() })
    }

    suspend fun getAllInspections(userId: Int): List<InspectionResponse> =
        InspectionRepository.findAllByUserId(userId).map { it.toResponse() }

    suspend fun uploadImage(
        inspectionId: Int,
        fileBytes: ByteArray,
        fileName: String,
        type: String,
        ocrText: String? = null
    ): InspectionImageResponse {
        val extension = fileName.substringAfterLast(".", "jpg")
        val newFileName = UUID.randomUUID().toString() + "." + extension
        val uploadDir = File("uploads")
        if (!uploadDir.exists()) uploadDir.mkdirs()

        File(uploadDir, newFileName).writeBytes(fileBytes)

        val imageUrl = "/uploads/" + newFileName
        val id = InspectionRepository.addImage(inspectionId, imageUrl, type.uppercase(), ocrText)

        logger.info(
            "Inspection {}: stored {} panel image ({} bytes, {} chars of OCR text)",
            inspectionId, type.uppercase(), fileBytes.size, ocrText?.length ?: 0
        )
        return InspectionImageResponse(id, imageUrl, type.uppercase())
    }

    suspend fun saveQrResult(inspectionId: Int, codeType: String, rawValue: String, format: String) {
        InspectionRepository.saveQrResult(inspectionId, codeType, rawValue, format)
    }

    suspend fun deleteInspection(id: Int) {
        InspectionRepository.delete(id)
    }

    private fun com.example.repositories.InspectionResult.toResponse(
        images: List<InspectionImageResponse> = emptyList()
    ) = InspectionResponse(
        id = id,
        productName = productName,
        status = status,
        createdAt = createdAt,
        complianceStatus = complianceStatus,
        images = images
    )

    private fun com.example.repositories.ImageResult.toResponse() = InspectionImageResponse(
        id = id,
        imageUrl = imageUrl,
        type = type
    )
}
