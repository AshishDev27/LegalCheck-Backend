package com.example.services

import com.example.dtos.ProductDeclarationDto
import com.example.services.validation.DeclarationValidator
import com.example.services.validation.IssueType
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeclarationValidatorTest {

    private val today = LocalDate.of(2026, 8, 29)

    private fun declaration(block: ProductDeclarationDto.() -> ProductDeclarationDto) =
        ProductDeclarationDto(
            productName = "Milk Powder",
            brand = "Amul",
            manufacturerName = "Amul Dairy",
            manufacturerAddress = "Anand, Gujarat, 388001",
            netQuantity = "500",
            netQuantityUnit = "g",
            mrp = "250",
            manufacturingDate = "01/2026",
            expiryDate = "01/2027",
            batchNumber = "AMB-1",
            licenseNumber = "10012345678901",
            consumerCarePhone = "1800223030",
            countryOfOrigin = "India"
        ).block()

    @Test
    fun `a complete declaration raises no blocking issues`() {
        val report = DeclarationValidator.validate(declaration { this }, today)

        assertTrue(report.issues.none { it.type == IssueType.MISSING })
        assertTrue(report.issues.none { it.type == IssueType.INVALID_FORMAT })
        assertTrue(report.validatedFields.contains("mrp"))
    }

    @Test
    fun `an expiry before manufacture is invalid`() {
        val report = DeclarationValidator.validate(declaration { copy(expiryDate = "01/2025") }, today)

        assertTrue(report.issues.any { it.field == "expiryDate" && it.type == IssueType.INVALID_FORMAT })
    }

    @Test
    fun `an already expired product is suspicious`() {
        val report = DeclarationValidator.validate(declaration { copy(expiryDate = "01/2026") }, today)

        assertTrue(report.issues.any { it.field == "expiryDate" && it.type == IssueType.SUSPICIOUS })
    }

    @Test
    fun `a future manufacturing date is suspicious`() {
        val report = DeclarationValidator.validate(declaration { copy(manufacturingDate = "01/2030") }, today)

        assertTrue(report.issues.any { it.field == "manufacturingDate" && it.type == IssueType.SUSPICIOUS })
    }

    @Test
    fun `a shelf life expressed as a duration is accepted`() {
        val report = DeclarationValidator.validate(
            declaration { copy(expiryDate = null, bestBefore = "24 months from manufacture") },
            today
        )

        assertTrue(report.validatedFields.contains("expiryDate"))
        assertTrue(report.issues.none { it.field == "expiryDate" && it.type == IssueType.MISSING })
    }

    @Test
    fun `an FSSAI licence that is not fourteen digits is rejected`() {
        val report = DeclarationValidator.validate(
            declaration { copy(commodityCategory = "FOOD", licenseNumber = "12345", ingredients = "Milk solids") },
            today
        )

        assertTrue(report.issues.any { it.field == "licenseNumber" && it.type == IssueType.INVALID_FORMAT })
    }

    @Test
    fun `a non standard quantity unit is rejected`() {
        val report = DeclarationValidator.validate(declaration { copy(netQuantityUnit = "scoops") }, today)

        assertTrue(report.issues.any { it.field == "netQuantityUnit" && it.type == IssueType.INVALID_FORMAT })
    }

    @Test
    fun `an implausible price is flagged rather than accepted`() {
        val report = DeclarationValidator.validate(declaration { copy(mrp = "99999999") }, today)

        assertTrue(report.issues.any { it.field == "mrp" && it.type == IssueType.SUSPICIOUS })
    }

    @Test
    fun `a named entity without an address is not a complete declaration`() {
        val report = DeclarationValidator.validate(declaration { copy(manufacturerAddress = null) }, today)

        assertTrue(report.issues.any { it.field == "manufacturer" && it.type == IssueType.INVALID_FORMAT })
    }

    @Test
    fun `an unreadable field is reported as unreadable, not as missing`() {
        val report = DeclarationValidator.validate(
            declaration { copy(mrp = null, unreadableFields = listOf("mrp")) },
            today
        )

        assertTrue(report.isUnreadable("mrp"))
        assertTrue(report.issues.none { it.field == "mrp" && it.type == IssueType.MISSING })
    }

    @Test
    fun `an import without a country of origin is incomplete`() {
        val report = DeclarationValidator.validate(
            declaration { copy(importerName = "Global Foods Pvt Ltd", importerAddress = "Mumbai, 400001", countryOfOrigin = null) },
            today
        )

        assertTrue(report.issues.any { it.field == "countryOfOrigin" && it.type == IssueType.MISSING })
    }

    @Test
    fun `packaging date formats are parsed`() {
        assertEquals(2026, DeclarationValidator.parseDate("08/2026")?.year)
        assertEquals(2026, DeclarationValidator.parseDate("15-03-2026")?.year)
        assertEquals(2026, DeclarationValidator.parseDate("Aug 2026")?.year)
        assertEquals(null, DeclarationValidator.parseDate("not a date"))
    }
}
