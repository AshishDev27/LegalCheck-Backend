package com.example.services

import com.example.compliance.core.ComplianceResult
import com.example.compliance.core.ComplianceRuleEngine
import com.example.compliance.core.ComplianceStatus
import com.example.compliance.rules.v2026.LmpcRuleSet
import com.example.dtos.ProductDeclarationDto
import com.example.services.extraction.OcrSide
import com.example.services.extraction.ProductExtractor
import com.example.services.validation.DeclarationValidator
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Exercises the whole chain a scan travels: OCR text of both panels -> one structured product ->
 * validation -> compliance rules -> score. These are the scenarios the feature has to get right.
 */
class ScanPipelineTest {

    private val engine = ComplianceRuleEngine(LmpcRuleSet.all())

    private fun analyze(front: String, back: String): ComplianceResult {
        val declaration = ProductExtractor.extract(
            listOfNotNull(
                front.takeIf { it.isNotBlank() }?.let { OcrSide("FRONT", it) },
                back.takeIf { it.isNotBlank() }?.let { OcrSide("BACK", it) }
            )
        )
        return engine.analyze(1, declaration, DeclarationValidator.validate(declaration))
    }

    private val wellLabelledFront = """
        AMULYA
        Dairy Whitener
        Instant Full Cream Milk Powder
        500 g
    """.trimIndent()

    private val wellLabelledBack = """
        Ingredients: Milk solids, added vitamins A and D
        Net Qty: 500 g
        Manufactured by: Gujarat Co-operative Milk Marketing Federation Ltd
        Amul Dairy Road, Anand, Gujarat, 388001
        Batch No: AMB2201
        Mfg Date: 01/2026
        Best Before: 9 months from packaging
        MRP: Rs. 260 (inclusive of all taxes)
        FSSAI Lic No: 10012345678901
        Consumer Care: Manager, Consumer Care Cell
        Phone: 1800223030
        Country of Origin: India
        Storage: Store in a cool dry place away from sunlight
    """.trimIndent()

    /** Test case 1: a clearly printed package is understood and scored from its real content. */
    @Test
    fun `a clearly labelled package is read, combined and scored`() {
        val result = analyze(wellLabelledFront, wellLabelledBack)
        val product = result.declaration

        assertEquals("AMULYA", product.brand)
        assertEquals("500", product.netQuantity)
        assertEquals("g", product.netQuantityUnit)
        assertEquals("260", product.mrp)
        assertTrue(product.mrpInclusiveOfTaxes)
        assertEquals("10012345678901", product.licenseNumber)
        assertEquals("FOOD", product.commodityCategory)

        assertTrue(result.score >= 90, "a complete package should score highly, was ${result.score}")
        assertTrue(result.failedChecks.isEmpty(), "nothing should fail: ${result.failedChecks}")
        assertTrue(result.passedChecks.isNotEmpty())
    }

    /** Test case 2: a panel that reads badly yields uncertainty, never invented values. */
    @Test
    fun `a poorly read back panel produces missing fields rather than invented ones`() {
        val smudgedBack = """
            Net Qty: 500 g
            MRP: Rs.
            Batch No:
            FSSAI Lic No:
        """.trimIndent()

        val result = analyze(wellLabelledFront, smudgedBack)
        val product = result.declaration

        assertNull(product.mrp, "an unreadable price must not be guessed")
        assertNull(product.batchNumber)
        assertNull(product.licenseNumber)
        assertNull(product.manufacturerName)

        assertTrue(product.unreadableFields.containsAll(listOf("mrp", "batchNumber", "licenseNumber")))

        // An unreadable declaration is sent for review; it is not scored as a trader's violation.
        val mrpCheck = result.checks.single { it.ruleId == "LMPC-2026-R18-MRP" }
        assertEquals(ComplianceStatus.REVIEW_REQUIRED, mrpCheck.status)
        assertTrue(result.invalidInformation.any { it.startsWith("mrp") })
    }

    /** Test case 3: a genuinely absent declaration lowers the score. */
    @Test
    fun `a missing mandatory declaration lowers the score`() {
        val backWithoutPrice = wellLabelledBack
            .lines()
            .filterNot { it.startsWith("MRP") }
            .joinToString("\n")

        val complete = analyze(wellLabelledFront, wellLabelledBack)
        val withoutPrice = analyze(wellLabelledFront, backWithoutPrice)

        assertNull(withoutPrice.declaration.mrp)
        assertTrue(
            withoutPrice.score < complete.score,
            "removing the MRP must cost score: ${withoutPrice.score} vs ${complete.score}"
        )
        assertEquals(ComplianceStatus.FAIL, withoutPrice.status)
        assertTrue(withoutPrice.missingInformation.any { it.startsWith("mrp") })
    }

    /** Test case 4: information printed on both panels is merged into one product. */
    @Test
    fun `overlapping declarations across panels become one product, not two`() {
        val frontWithQuantity = wellLabelledFront + "\nNet Qty: 500 g"

        val result = analyze(frontWithQuantity, wellLabelledBack)
        val product = result.declaration

        assertEquals("500", product.netQuantity)
        assertEquals("FRONT+BACK", product.fieldSources["netQuantity"])
        assertTrue(product.conflicts.isEmpty(), "matching values are corroboration, not a conflict")
    }

    @Test
    fun `a scan with no readable text is not scored as compliant`() {
        val result = analyze("", "")

        assertEquals(0f, result.declaration.confidence)
        assertEquals(ComplianceStatus.REVIEW_REQUIRED, result.status)
        assertTrue(result.passedChecks.isEmpty(), "nothing can pass when nothing was read")
    }

    /**
     * The client posts its own declaration model, which may carry fields this build does not know.
     * The server must accept it: rejecting it is what previously pushed the app onto a stub score.
     */
    @Test
    fun `a declaration carrying unknown fields is still accepted`() {
        val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true; explicitNulls = false }
        val payload = """
            {"productName":"Milk Powder","mrp":"260","aFieldFromANewerClient":"ignore me","confidence":0.9}
        """.trimIndent()

        val declaration = json.decodeFromString<ProductDeclarationDto>(payload)

        assertEquals("Milk Powder", declaration.productName)
        assertEquals("260", declaration.mrp)
    }
}
