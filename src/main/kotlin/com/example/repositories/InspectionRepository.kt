package com.example.repositories

import com.example.compliance.core.ComplianceResult
import com.example.config.DatabaseFactory.dbQuery
import com.example.dtos.ProductDeclarationDto
import com.example.models.ComplianceResultTable
import com.example.models.InspectionImageTable
import com.example.models.InspectionTable
import com.example.models.ProductDeclarationTable
import com.example.models.QrResultTable
import com.example.models.ReportTable
import com.example.models.ViolationTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime

object InspectionRepository {
    suspend fun create(userId: Int, productName: String) = dbQuery {
        InspectionTable.insert {
            it[InspectionTable.userId] = userId
            it[InspectionTable.productName] = productName
            it[InspectionTable.status] = "PENDING"
            it[InspectionTable.createdAt] = LocalDateTime.now()
            it[InspectionTable.updatedAt] = LocalDateTime.now()
        } get InspectionTable.id
    }

    suspend fun findById(id: Int) = dbQuery {
        (InspectionTable leftJoin ComplianceResultTable)
            .selectAll().where { InspectionTable.id eq id }
            .map { it.toInspectionResult() }
            .singleOrNull()
    }

    suspend fun findAllByUserId(userId: Int) = dbQuery {
        (InspectionTable leftJoin ComplianceResultTable)
            .selectAll().where { InspectionTable.userId eq userId }
            .orderBy(InspectionTable.createdAt to SortOrder.DESC)
            .map { it.toInspectionResult() }
    }

    suspend fun delete(id: Int) = dbQuery {
        InspectionImageTable.deleteWhere { inspectionId eq id }
        QrResultTable.deleteWhere { inspectionId eq id }
        ProductDeclarationTable.deleteWhere { inspectionId eq id }
        ReportTable.deleteWhere { inspectionId eq id }

        val resultRows = ComplianceResultTable.selectAll().where { ComplianceResultTable.inspectionId eq id }.map { it[ComplianceResultTable.id] }
        resultRows.forEach { resultId ->
            ViolationTable.deleteWhere { complianceResultId eq resultId }
        }
        ComplianceResultTable.deleteWhere { inspectionId eq id }
        InspectionTable.deleteWhere { InspectionTable.id eq id }
    }

    suspend fun addImage(inspectionId: Int, imageUrl: String, type: String, ocrText: String? = null) = dbQuery {
        InspectionImageTable.insert {
            it[InspectionImageTable.inspectionId] = inspectionId
            it[InspectionImageTable.imageUrl] = imageUrl
            it[InspectionImageTable.type] = type
            it[InspectionImageTable.ocrText] = ocrText
            it[InspectionImageTable.createdAt] = LocalDateTime.now()
            it[InspectionImageTable.updatedAt] = LocalDateTime.now()
        } get InspectionImageTable.id
    }

    suspend fun findImagesByInspectionId(inspectionId: Int) = dbQuery {
        InspectionImageTable.selectAll().where { InspectionImageTable.inspectionId eq inspectionId }
            .orderBy(InspectionImageTable.createdAt to SortOrder.ASC)
            .map { it.toImageResult() }
    }

    suspend fun findQrByInspectionId(inspectionId: Int) = dbQuery {
        QrResultTable.selectAll().where { QrResultTable.inspectionId eq inspectionId }
            .map {
                mapOf(
                    "codeType" to it[QrResultTable.codeType],
                    "rawValue" to it[QrResultTable.rawValue],
                    "format" to it[QrResultTable.format]
                )
            }
    }

    /**
     * Stores the reviewed declaration field by field, so a later audit can see exactly which
     * values were detected, which were absent and how confident the extraction was.
     */
    suspend fun saveDeclaration(inspectionId: Int, declaration: ProductDeclarationDto) = dbQuery {
        ProductDeclarationTable.deleteWhere { ProductDeclarationTable.inspectionId eq inspectionId }

        declarationFields(declaration).forEach { (fieldName, value) ->
            ProductDeclarationTable.insert {
                it[ProductDeclarationTable.inspectionId] = inspectionId
                it[ProductDeclarationTable.fieldName] = fieldName
                it[detectedValue] = value?.take(60_000)
                it[isPresent] = !value.isNullOrBlank()
                it[confidence] = (declaration.fieldConfidence[fieldName] ?: 0f).toDouble()
                it[createdAt] = LocalDateTime.now()
                it[updatedAt] = LocalDateTime.now()
            }
        }

        InspectionTable.update({ InspectionTable.id eq inspectionId }) {
            it[status] = "ANALYZING"
            it[updatedAt] = LocalDateTime.now()
        }
    }

    suspend fun saveComplianceResult(inspectionId: Int, result: ComplianceResult) = dbQuery {
        val existing = ComplianceResultTable.selectAll()
            .where { ComplianceResultTable.inspectionId eq inspectionId }
            .map { it[ComplianceResultTable.id] }
            .singleOrNull()

        existing?.let { resultId ->
            ViolationTable.deleteWhere { complianceResultId eq resultId }
            ComplianceResultTable.deleteWhere { ComplianceResultTable.id eq resultId }
        }

        val resultId = ComplianceResultTable.insert {
            it[ComplianceResultTable.inspectionId] = inspectionId
            it[overallScore] = result.score.toDouble()
            it[status] = result.status.name
            it[analyzedAt] = LocalDateTime.now()
            it[createdAt] = LocalDateTime.now()
            it[updatedAt] = LocalDateTime.now()
        } get ComplianceResultTable.id

        result.violations.forEach { violation ->
            ViolationTable.insert {
                it[complianceResultId] = resultId
                it[ruleId] = violation.ruleId
                it[description] = violation.title + ": " + violation.explanation
                it[severity] = violation.severity.name
                it[suggestion] = violation.recommendation
                it[createdAt] = LocalDateTime.now()
                it[updatedAt] = LocalDateTime.now()
            }
        }

        InspectionTable.update({ InspectionTable.id eq inspectionId }) {
            it[status] = "COMPLETED"
            it[updatedAt] = LocalDateTime.now()
        }
    }

    /** The stored verdict for an inspection, so a report prints what was actually assessed. */
    suspend fun findComplianceResult(inspectionId: Int): StoredComplianceResult? = dbQuery {
        val row = ComplianceResultTable.selectAll()
            .where { ComplianceResultTable.inspectionId eq inspectionId }
            .singleOrNull() ?: return@dbQuery null

        val resultId = row[ComplianceResultTable.id]
        val violations = ViolationTable.selectAll()
            .where { ViolationTable.complianceResultId eq resultId }
            .map {
                StoredViolation(
                    ruleId = it[ViolationTable.ruleId],
                    description = it[ViolationTable.description],
                    severity = it[ViolationTable.severity],
                    suggestion = it[ViolationTable.suggestion]
                )
            }

        StoredComplianceResult(
            score = row[ComplianceResultTable.overallScore],
            status = row[ComplianceResultTable.status],
            analyzedAt = row[ComplianceResultTable.analyzedAt].toString(),
            violations = violations
        )
    }

    /** The declaration fields recorded for an inspection, in the order they were stored. */
    suspend fun findDeclarationFields(inspectionId: Int): List<StoredDeclarationField> = dbQuery {
        ProductDeclarationTable.selectAll()
            .where { ProductDeclarationTable.inspectionId eq inspectionId }
            .map {
                StoredDeclarationField(
                    fieldName = it[ProductDeclarationTable.fieldName],
                    detectedValue = it[ProductDeclarationTable.detectedValue],
                    isPresent = it[ProductDeclarationTable.isPresent],
                    confidence = it[ProductDeclarationTable.confidence]
                )
            }
    }

    suspend fun saveQrResult(inspectionId: Int, codeType: String, rawValue: String, format: String) = dbQuery {
        QrResultTable.insert {
            it[QrResultTable.inspectionId] = inspectionId
            it[QrResultTable.codeType] = codeType
            it[QrResultTable.rawValue] = rawValue
            it[QrResultTable.format] = format
            it[createdAt] = LocalDateTime.now()
        }
    }

    private fun declarationFields(declaration: ProductDeclarationDto): List<Pair<String, String?>> = listOf(
        "commodityCategory" to declaration.commodityCategory,
        "productName" to declaration.productName,
        "brand" to declaration.brand,
        "variant" to declaration.variant,
        "commodityName" to declaration.commodityName,
        "manufacturerName" to declaration.manufacturerName,
        "manufacturerAddress" to declaration.manufacturerAddress,
        "packerName" to declaration.packerName,
        "packerAddress" to declaration.packerAddress,
        "importerName" to declaration.importerName,
        "importerAddress" to declaration.importerAddress,
        "countryOfOrigin" to declaration.countryOfOrigin,
        "netQuantity" to declaration.netQuantity,
        "netQuantityUnit" to declaration.netQuantityUnit,
        "mrp" to declaration.mrp,
        "unitSalePrice" to declaration.unitSalePrice,
        "dimensions" to declaration.dimensions,
        "batchNumber" to declaration.batchNumber,
        "lotNumber" to declaration.lotNumber,
        "licenseNumber" to declaration.licenseNumber,
        "manufacturingDate" to declaration.manufacturingDate,
        "packingDate" to declaration.packingDate,
        "bestBefore" to declaration.bestBefore,
        "expiryDate" to declaration.expiryDate,
        "useBy" to declaration.useBy,
        "ingredients" to declaration.ingredients,
        "nutrition" to declaration.nutrition?.evidence,
        "usageInstructions" to declaration.usageInstructions,
        "warnings" to declaration.warnings,
        "consumerCareName" to declaration.consumerCareName,
        "consumerCarePhone" to declaration.consumerCarePhone,
        "consumerCareEmail" to declaration.consumerCareEmail
    )

    private fun ResultRow.toInspectionResult() = InspectionResult(
        id = this[InspectionTable.id],
        userId = this[InspectionTable.userId],
        productName = this[InspectionTable.productName],
        status = this[InspectionTable.status],
        createdAt = this[InspectionTable.createdAt].toString(),
        complianceStatus = this.getOrNull(ComplianceResultTable.status)
    )

    private fun ResultRow.toImageResult() = ImageResult(
        id = this[InspectionImageTable.id],
        imageUrl = this[InspectionImageTable.imageUrl],
        type = this[InspectionImageTable.type],
        ocrText = this[InspectionImageTable.ocrText]
    )
}

data class InspectionResult(
    val id: Int,
    val userId: Int,
    val productName: String,
    val status: String,
    val createdAt: String,
    val complianceStatus: String? = null
)

data class StoredComplianceResult(
    val score: Double,
    val status: String,
    val analyzedAt: String,
    val violations: List<StoredViolation>
)

data class StoredViolation(
    val ruleId: String,
    val description: String,
    val severity: String,
    val suggestion: String?
)

data class StoredDeclarationField(
    val fieldName: String,
    val detectedValue: String?,
    val isPresent: Boolean,
    val confidence: Double
)

data class ImageResult(
    val id: Int,
    val imageUrl: String,
    val type: String,
    val ocrText: String? = null
)
