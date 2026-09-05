package com.example.compliance

import com.example.compliance.core.ComplianceRuleEngine
import com.example.compliance.core.ComplianceStatus
import com.example.compliance.rules.v2026.LmpcRuleSet
import com.example.dtos.ProductDeclarationDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ComplianceRuleEngineTest {

    private val engine = ComplianceRuleEngine(LmpcRuleSet.all())

    /** A general commodity carrying every declaration the rules require of it. */
    private fun compliantGeneralPackage() = ProductDeclarationDto(
        commodityCategory = "GENERAL",
        productName = "Milk Powder",
        brand = "Amul",
        commodityName = "Milk Powder",
        manufacturerName = "Gujarat Co-operative Milk Marketing Federation Ltd",
        manufacturerAddress = "Amul Dairy Road, Anand, Gujarat, 388001",
        countryOfOrigin = "India",
        netQuantity = "500",
        netQuantityUnit = "g",
        mrp = "250",
        mrpInclusiveOfTaxes = true,
        manufacturingDate = "01/2026",
        batchNumber = "AMB-2201",
        consumerCareName = "Consumer Care Cell",
        consumerCarePhone = "1800223030",
        confidence = 0.92f,
        fieldConfidence = mapOf(
            "productName" to 0.9f, "manufacturer" to 0.9f, "netQuantity" to 0.9f,
            "mrp" to 0.9f, "manufacturingDate" to 0.9f, "batchNumber" to 0.9f,
            "consumerCare" to 0.9f, "countryOfOrigin" to 0.9f, "brand" to 0.9f
        )
    )

    @Test
    fun `a fully declared package scores 100 and passes`() {
        val result = engine.analyze(1, compliantGeneralPackage())

        assertEquals(100, result.score)
        assertEquals(ComplianceStatus.PASS, result.status)
        assertTrue(result.violations.isEmpty())
        assertTrue(result.passedChecks.isNotEmpty(), "passing checks must be reported, not only failures")
    }

    @Test
    fun `a missing generic name is a critical failure`() {
        val result = engine.analyze(1, compliantGeneralPackage().copy(productName = null, commodityName = null))

        assertEquals(ComplianceStatus.FAIL, result.status)
        assertTrue(result.score < 100)
        assertTrue(result.violations.any { it.ruleId == "LMPC-2026-R6-NAME" })
        assertTrue(result.missingInformation.any { it.startsWith("productName") })
    }

    @Test
    fun `an MRP without the tax wording is a warning, not a failure`() {
        val result = engine.analyze(1, compliantGeneralPackage().copy(mrpInclusiveOfTaxes = false))

        assertEquals(ComplianceStatus.WARNING, result.status)
        assertTrue(result.score in 90..99)
        assertTrue(result.warnings.any { it.contains("inclusive of all taxes") })
    }

    @Test
    fun `a non standard quantity unit fails the quantity rule`() {
        val result = engine.analyze(1, compliantGeneralPackage().copy(netQuantityUnit = "scoops"))

        assertTrue(result.violations.any { it.ruleId == "LMPC-2026-R12-QTY" })
        assertTrue(result.invalidInformation.any { it.startsWith("netQuantityUnit") })
    }

    @Test
    fun `a weak extraction is sent for review rather than judged`() {
        val result = engine.analyze(1, compliantGeneralPackage().copy(confidence = 0.4f))

        assertEquals(ComplianceStatus.REVIEW_REQUIRED, result.status)
    }

    @Test
    fun `an unreadable field is flagged for review and never counted as compliant`() {
        val declaration = compliantGeneralPackage().copy(
            licenseNumber = null,
            unreadableFields = listOf("licenseNumber"),
            commodityCategory = "FOOD",
            ingredients = "Skimmed milk powder, sugar",
            expiryDate = "12/2027",
            warnings = "Store in a cool dry place"
        )

        val result = engine.analyze(1, declaration)
        val licenceCheck = result.checks.single { it.ruleId == "LMPC-2026-LICENCE" }

        assertEquals(ComplianceStatus.REVIEW_REQUIRED, licenceCheck.status)
        assertFalse(result.passedChecks.contains(licenceCheck.title))
    }

    @Test
    fun `rules that do not apply to the product are not scored`() {
        val general = engine.analyze(1, compliantGeneralPackage())
        val food = engine.analyze(1, compliantGeneralPackage().copy(commodityCategory = "FOOD"))

        assertFalse(general.checks.any { it.ruleId == "LMPC-2026-ING" })
        assertTrue(food.checks.any { it.ruleId == "LMPC-2026-ING" })
    }

    @Test
    fun `the score falls as declarations are removed`() {
        val full = engine.analyze(1, compliantGeneralPackage()).score
        val withoutBatch = engine.analyze(1, compliantGeneralPackage().copy(batchNumber = null)).score
        val withoutBatchAndMrp = engine.analyze(1, compliantGeneralPackage().copy(batchNumber = null, mrp = null)).score

        assertTrue(full > withoutBatch, "removing a declaration must lower the score")
        assertTrue(withoutBatch > withoutBatchAndMrp, "removing a critical declaration must lower it further")
    }
}
