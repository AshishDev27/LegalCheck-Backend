package com.example.services

import com.example.repositories.InspectionRepository
import com.example.repositories.UserRepository
import com.google.zxing.BarcodeFormat
import com.google.zxing.client.j2se.MatrixToImageWriter
import com.google.zxing.qrcode.QRCodeWriter
import com.lowagie.text.Document
import com.lowagie.text.Element
import com.lowagie.text.Font
import com.lowagie.text.Image
import com.lowagie.text.PageSize
import com.lowagie.text.Paragraph
import com.lowagie.text.pdf.PdfWriter
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object ReportService {

    /**
     * Renders the compliance report for an inspection from what was actually assessed.
     *
     * If no analysis has been stored yet the report says so, rather than printing a score that
     * was never computed - a report is evidence, and inventing figures in one is not acceptable.
     */
    suspend fun generateReport(inspectionId: Int): ByteArray {
        val inspection = InspectionRepository.findById(inspectionId)
            ?: throw IllegalArgumentException("Inspection not found")
        val inspector = UserRepository.findById(inspection.userId)
            ?: throw IllegalArgumentException("Inspector not found")

        val complianceResult = InspectionRepository.findComplianceResult(inspectionId)
        val declarationFields = InspectionRepository.findDeclarationFields(inspectionId)

        val outputStream = ByteArrayOutputStream()
        val document = Document(PageSize.A4)
        PdfWriter.getInstance(document, outputStream)
        document.open()

        val titleFont = Font(Font.HELVETICA, 20f, Font.BOLD)
        val headerFont = Font(Font.HELVETICA, 14f, Font.BOLD)
        val bodyFont = Font(Font.HELVETICA, 12f, Font.NORMAL)

        document.add(
            Paragraph("LegalCheck Compliance Report", titleFont).apply { alignment = Element.ALIGN_CENTER }
        )
        document.add(
            Paragraph(
                "Generated on: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                bodyFont
            )
        )
        document.add(Paragraph(" "))

        document.add(Paragraph("Inspection details", headerFont))
        document.add(Paragraph("Inspection ID: " + inspection.id, bodyFont))
        document.add(Paragraph("Product name: " + inspection.productName, bodyFont))
        document.add(Paragraph("Inspector: " + inspector.fullName + " (ID: " + inspector.inspectorId + ")", bodyFont))
        document.add(Paragraph("Division: " + inspector.division, bodyFont))
        document.add(Paragraph("Status: " + inspection.status, bodyFont))
        document.add(Paragraph(" "))

        document.add(Paragraph("Detected declarations", headerFont))
        if (declarationFields.isEmpty()) {
            document.add(Paragraph("No declarations have been recorded for this inspection yet.", bodyFont))
        } else {
            declarationFields.forEach { field ->
                val value = if (field.isPresent) field.detectedValue.orEmpty() else "Not detected"
                document.add(Paragraph(field.fieldName + ": " + value, bodyFont))
            }
        }
        document.add(Paragraph(" "))

        document.add(Paragraph("Compliance summary", headerFont))
        if (complianceResult == null) {
            document.add(
                Paragraph(
                    "This inspection has not been analysed yet, so no compliance score is available.",
                    bodyFont
                )
            )
        } else {
            document.add(Paragraph("Overall score: " + complianceResult.score.toInt() + "/100", bodyFont))
            document.add(Paragraph("Status: " + complianceResult.status.replace("_", " "), bodyFont))
            document.add(Paragraph("Analysed at: " + complianceResult.analyzedAt, bodyFont))
            document.add(Paragraph(" "))

            document.add(Paragraph("Findings", headerFont))
            if (complianceResult.violations.isEmpty()) {
                document.add(Paragraph("No violations were recorded against this package.", bodyFont))
            } else {
                complianceResult.violations.forEach { violation ->
                    document.add(
                        Paragraph(
                            "- [" + violation.severity + "] " + violation.ruleId + " - " + violation.description,
                            bodyFont
                        )
                    )
                    violation.suggestion?.let {
                        document.add(Paragraph("   Recommendation: " + it, bodyFont))
                    }
                }
            }
        }
        document.add(Paragraph(" "))

        document.add(Paragraph("Verification QR", headerFont))
        val image = Image.getInstance(generateQrCode("https://legalcheck.gov.in/verify/" + inspection.id))
        image.scaleToFit(100f, 100f)
        document.add(image)

        document.close()
        return outputStream.toByteArray()
    }

    private fun generateQrCode(text: String): ByteArray {
        val bitMatrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 200, 200)
        val outputStream = ByteArrayOutputStream()
        MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream)
        return outputStream.toByteArray()
    }
}
