package com.example.models

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

object InspectionTable : Table("inspections") {
    val id = integer("id").autoIncrement()
    val userId = integer("user_id").references(UserTable.id).index()
    val productName = varchar("product_name", 255)
    val status = varchar("status", 50).index() // PENDING, COMPLETED, FAILED
    val createdAt = datetime("created_at").default(LocalDateTime.now())
    val updatedAt = datetime("updated_at").default(LocalDateTime.now())

    override val primaryKey = PrimaryKey(id)
}

object InspectionImageTable : Table("inspection_images") {
    val id = integer("id").autoIncrement()
    val inspectionId = integer("inspection_id").references(InspectionTable.id).index()
    val imageUrl = varchar("image_url", 512)
    val type = varchar("type", 50) // FRONT, BACK, ADDITIONAL, etc.
    val ocrText = text("ocr_text").nullable()
    val createdAt = datetime("created_at").default(LocalDateTime.now())
    val updatedAt = datetime("updated_at").default(LocalDateTime.now())

    override val primaryKey = PrimaryKey(id)
}

object QrResultTable : Table("qr_results") {
    val id = integer("id").autoIncrement()
    val inspectionId = integer("inspection_id").references(InspectionTable.id).index()
    val codeType = varchar("code_type", 100)
    val rawValue = text("raw_value")
    val format = varchar("format", 100)
    val sourceImageId = integer("source_image_id").references(InspectionImageTable.id).nullable()
    val createdAt = datetime("created_at").default(LocalDateTime.now())

    override val primaryKey = PrimaryKey(id)
}

object ProductDeclarationTable : Table("product_declarations") {
    val id = integer("id").autoIncrement()
    val inspectionId = integer("inspection_id").references(InspectionTable.id).index()
    val fieldName = varchar("field_name", 255)
    val detectedValue = text("detected_value").nullable()
    val isPresent = bool("is_present")
    val confidence = double("confidence").default(0.0)
    val createdAt = datetime("created_at").default(LocalDateTime.now())
    val updatedAt = datetime("updated_at").default(LocalDateTime.now())

    override val primaryKey = PrimaryKey(id)
}

object ComplianceResultTable : Table("compliance_results") {
    val id = integer("id").autoIncrement()
    val inspectionId = integer("inspection_id").references(InspectionTable.id).uniqueIndex()
    val overallScore = double("overall_score")
    val status = varchar("status", 50) // PASS, FAIL, WARNING
    val analyzedAt = datetime("analyzed_at").default(LocalDateTime.now())
    val createdAt = datetime("created_at").default(LocalDateTime.now())
    val updatedAt = datetime("updated_at").default(LocalDateTime.now())

    override val primaryKey = PrimaryKey(id)
}

object ViolationTable : Table("violations") {
    val id = integer("id").autoIncrement()
    val complianceResultId = integer("compliance_result_id").references(ComplianceResultTable.id).index()
    val ruleId = varchar("rule_id", 100)
    val description = text("description")
    val severity = varchar("severity", 50) // LOW, MEDIUM, HIGH, CRITICAL
    val suggestion = text("suggestion").nullable()
    val createdAt = datetime("created_at").default(LocalDateTime.now())
    val updatedAt = datetime("updated_at").default(LocalDateTime.now())

    override val primaryKey = PrimaryKey(id)
}

object ReportTable : Table("reports") {
    val id = integer("id").autoIncrement()
    val inspectionId = integer("inspection_id").references(InspectionTable.id).uniqueIndex()
    val reportUrl = varchar("report_url", 512)
    val generatedAt = datetime("generated_at").default(LocalDateTime.now())
    val createdAt = datetime("created_at").default(LocalDateTime.now())
    val updatedAt = datetime("updated_at").default(LocalDateTime.now())

    override val primaryKey = PrimaryKey(id)
}
