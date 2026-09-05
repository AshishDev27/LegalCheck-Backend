package com.example.services

import com.example.services.extraction.OcrSide
import com.example.services.extraction.ProductExtractor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProductExtractorTest {

    private val creatineFront = """
        MUSCLEBLAZE
        Creatine Monohydrate
        Unflavoured
        250 g
    """.trimIndent()

    private val creatineBack = """
        Ingredients:
        Creatine Monohydrate 5g
        Net Qty: 250 g
        Manufactured by: Bright Lifecare Pvt Ltd
        Plot No 1, Sector 2, IMT Manesar
        Gurugram, Haryana, 122050
        Batch No: ABC123
        Mfg Date: 08/2025
        Best Before: 24 months from manufacture
        MRP: Rs. 999 (inclusive of all taxes)
        FSSAI Lic No: 10012345678901
        Consumer Care: Manager, Consumer Care Cell
        Email: care@brightlifecare.com
        Country of Origin: India
        Warning: Not for use by persons under 18 years
    """.trimIndent()

    @Test
    fun `front and back are combined into one product`() {
        val declaration = ProductExtractor.extract(
            listOf(OcrSide("FRONT", creatineFront), OcrSide("BACK", creatineBack))
        )

        assertEquals("Creatine Monohydrate 5g", declaration.ingredients)
        assertEquals("250", declaration.netQuantity)
        assertEquals("g", declaration.netQuantityUnit)
        assertEquals("Bright Lifecare Pvt Ltd", declaration.manufacturerName)
        assertEquals("ABC123", declaration.batchNumber)
        assertEquals("08/2025", declaration.manufacturingDate)
        assertEquals("999", declaration.mrp)
        assertTrue(declaration.mrpInclusiveOfTaxes)
        assertEquals("10012345678901", declaration.licenseNumber)
        assertEquals("India", declaration.countryOfOrigin)
        assertEquals("SUPPLEMENT", declaration.commodityCategory)
        assertNotNull(declaration.manufacturerAddress)
        assertTrue(declaration.manufacturerAddress.orEmpty().contains("Gurugram"))
    }

    @Test
    fun `identity comes from the front panel`() {
        val declaration = ProductExtractor.extract(
            listOf(OcrSide("FRONT", creatineFront), OcrSide("BACK", creatineBack))
        )

        assertEquals("MUSCLEBLAZE", declaration.brand)
        assertEquals("FRONT", declaration.fieldSources["brand"])
        assertEquals("Creatine Monohydrate", declaration.productName)
    }

    @Test
    fun `nothing is invented when a panel yields no text`() {
        val declaration = ProductExtractor.extract(listOf(OcrSide("FRONT", "   "), OcrSide("BACK", "")))

        assertNull(declaration.productName)
        assertNull(declaration.mrp)
        assertNull(declaration.licenseNumber)
        assertNull(declaration.manufacturerName)
        assertEquals(0f, declaration.confidence)
    }

    @Test
    fun `a label with an unreadable value leaves the field empty and records it`() {
        val back = """
            Net Qty: 250 g
            MRP: Rs. 499
            FSSAI Lic No: ####
            Batch No:
        """.trimIndent()

        val declaration = ProductExtractor.extract(listOf(OcrSide("BACK", back)))

        assertNull(declaration.licenseNumber, "an unreadable licence number must not be guessed")
        assertTrue(declaration.unreadableFields.contains("licenseNumber"))
        assertEquals("499", declaration.mrp)
    }

    @Test
    fun `panels that disagree produce a conflict instead of a silent overwrite`() {
        val front = "SUNRISE\nPure Honey\nMRP Rs. 150"
        val back = "Net Qty: 500 g\nMRP: Rs. 250\nManufactured by: Sunrise Foods Ltd\nMain Road, Pune, 411001"

        val declaration = ProductExtractor.extract(listOf(OcrSide("FRONT", front), OcrSide("BACK", back)))

        assertEquals("250", declaration.mrp, "the panel the declaration belongs on wins")
        assertTrue(declaration.conflicts.any { it.startsWith("mrp") })
        assertTrue(declaration.fieldConfidence.getValue("mrp") < 0.9f, "a conflicted field loses confidence")
    }

    @Test
    fun `overlapping information across panels is merged, not duplicated`() {
        val front = "AMUL\nMilk Powder\nNet Qty: 500 g"
        val back = "Net Weight: 500 g\nMRP: Rs. 250\nManufactured by: Amul Dairy\nAnand, Gujarat, 388001"

        val declaration = ProductExtractor.extract(listOf(OcrSide("FRONT", front), OcrSide("BACK", back)))

        assertEquals("500", declaration.netQuantity)
        assertEquals("FRONT+BACK", declaration.fieldSources["netQuantity"])
        assertTrue(declaration.conflicts.none { it.startsWith("netQuantity") })
    }

    @Test
    fun `units are normalised to their standard form`() {
        val declaration = ProductExtractor.extract(listOf(OcrSide("BACK", "Net Quantity: 1.5 Litres")))

        assertEquals("1.5", declaration.netQuantity)
        assertEquals("l", declaration.netQuantityUnit)
    }

    @Test
    fun `instruction copy is never mistaken for a manufacturer`() {
        val back = "Manufacturer details of the packer are printed below\nNet Qty: 100 g"

        val declaration = ProductExtractor.extract(listOf(OcrSide("BACK", back)))

        assertNull(declaration.manufacturerName)
    }

    @Test
    fun `raw text from both panels is retained for review`() {
        val declaration = ProductExtractor.extract(
            listOf(OcrSide("FRONT", creatineFront), OcrSide("BACK", creatineBack))
        )

        assertNotNull(declaration.rawFrontText)
        assertNotNull(declaration.rawBackText)
        assertTrue(declaration.otherDeclarations.any { it.startsWith("FRONT: ") })
        assertTrue(declaration.otherDeclarations.any { it.startsWith("BACK: ") })
    }
}
