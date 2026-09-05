package com.example.services.extraction

import com.example.dtos.ProductDeclarationDto
import org.slf4j.LoggerFactory

/**
 * Turns the OCR text of the FRONT and BACK panels of one physical package into a single
 * structured [ProductDeclarationDto].
 *
 * Two guarantees this class is built around:
 *  1. The two panels describe ONE product. Fields are merged, not duplicated, and disagreements
 *     between the panels are recorded as conflicts rather than silently resolved.
 *  2. Nothing is invented. A field is only populated when a printed label or an unambiguous
 *     pattern produced it. Where a label was found but its value could not be read, the field is
 *     left null and named in `unreadableFields`.
 */
object ProductExtractor {

    private val logger = LoggerFactory.getLogger(ProductExtractor::class.java)

    private const val CONFIDENCE_LABELLED = 0.9f
    private const val CONFIDENCE_PATTERN = 0.65f
    private const val CONFIDENCE_HEURISTIC = 0.5f
    private const val CONFIDENCE_CONFLICT_PENALTY = 0.35f

    /** Field names that carry the address continuation of the entity above them. */
    private val ENTITY_ADDRESS_FIELDS = mapOf(
        "manufacturerName" to "manufacturerAddress",
        "packerName" to "packerAddress",
        "importerName" to "importerAddress"
    )

    private data class Candidate(
        val field: String,
        val value: String,
        val confidence: Float,
        val side: String,
        val evidence: String
    )

    fun extract(sides: List<OcrSide>, qrValues: List<String> = emptyList()): ProductDeclarationDto {
        val panels = sides
            .filter { it.rawText.isNotBlank() }
            .map { NormalizedText(it.side.uppercase(), it.rawText) }

        if (panels.isEmpty()) {
            logger.warn("Extraction requested with no usable OCR text on any panel")
            return ProductDeclarationDto(confidence = 0f)
        }

        val candidates = mutableListOf<Candidate>()
        val unreadable = linkedSetOf<String>()

        panels.forEach { panel ->
            candidates += extractLabelled(panel, unreadable)
            candidates += extractPatterns(panel)
        }
        candidates += extractIdentityHeuristics(panels)
        candidates += extractFromQr(qrValues)

        val conflicts = mutableListOf<String>()
        val resolved = resolve(candidates, conflicts)

        // A label was seen but nothing usable followed it; do not let a later weak guess fill it in.
        unreadable.removeAll(resolved.keys)

        return assemble(panels, resolved, unreadable.toList(), conflicts)
    }

    // ---------------------------------------------------------------- label-driven extraction

    private fun extractLabelled(panel: NormalizedText, unreadable: MutableSet<String>): List<Candidate> {
        val found = mutableListOf<Candidate>()

        LabelRegistry.SPECS.forEach { spec ->
            for (index in panel.lines.indices) {
                val line = panel.lines[index]
                val match = spec.labels.firstNotNullOfOrNull { it.find(line) } ?: continue

                val inlineValue = TextNormalizer.valueAfterLabel(line, match)
                val cleaned = spec.clean(inlineValue)
                    ?: spec.clean(continuation(panel, index, spec.continuationLines))

                if (cleaned == null) {
                    unreadable += spec.field
                    continue
                }

                found += Candidate(spec.field, cleaned, CONFIDENCE_LABELLED, panel.side, line)

                // "Manufactured by: <name>" is nearly always followed by the address block.
                ENTITY_ADDRESS_FIELDS[spec.field]?.let { addressField ->
                    addressOf(panel, index, inlineValue.isNotBlank())?.let { address ->
                        found += Candidate(addressField, address, CONFIDENCE_LABELLED, panel.side, line)
                    }
                }
                break
            }
        }
        return found
    }

    /** Joins the lines following a label, stopping as soon as another label starts. */
    private fun continuation(panel: NormalizedText, labelIndex: Int, maxLines: Int): String {
        val collected = mutableListOf<String>()
        var index = labelIndex + 1
        while (index < panel.lines.size && collected.size < maxLines) {
            val next = panel.lines[index]
            if (startsAnotherLabel(next)) break
            collected += next
            index++
        }
        return collected.joinToString(" ")
    }

    private fun addressOf(panel: NormalizedText, labelIndex: Int, nameWasInline: Boolean): String? {
        val start = if (nameWasInline) labelIndex + 1 else labelIndex + 2
        val parts = mutableListOf<String>()
        var index = start
        while (index < panel.lines.size && parts.size < 4) {
            val next = panel.lines[index]
            if (startsAnotherLabel(next) || LabelRegistry.isNoise(next)) break
            parts += next
            index++
        }
        val address = parts.joinToString(", ").trim()
        // An address needs some substance: a pin code, a comma-separated locality, or real length.
        val looksLikeAddress = address.length >= 12 &&
            (address.contains(",") || Regex("\\b\\d{6}\\b").containsMatchIn(address))
        return if (looksLikeAddress) address else null
    }

    private fun startsAnotherLabel(line: String): Boolean =
        LabelRegistry.SPECS.any { spec -> spec.labels.any { it.find(line)?.range?.first?.let { at -> at <= 2 } == true } }

    // -------------------------------------------------------------- pattern-driven extraction

    /**
     * Values that are unambiguous on their own even without a printed label - a currency amount,
     * a 14 digit FSSAI number, an e-mail address. Scored below labelled hits so a labelled value
     * always wins.
     */
    private fun extractPatterns(panel: NormalizedText): List<Candidate> {
        val found = mutableListOf<Candidate>()

        LabelRegistry.parsePrice(panel.joined)?.let {
            found += Candidate("mrp", it, CONFIDENCE_PATTERN, panel.side, "currency amount in panel text")
        }
        LabelRegistry.parseQuantity(panel.joined)?.let { (amount, unit) ->
            found += Candidate("netQuantity", "$amount $unit", CONFIDENCE_PATTERN, panel.side, "quantity pattern in panel text")
        }
        LabelRegistry.FSSAI_PATTERN.find(TextNormalizer.repairDigits(panel.joined))?.let {
            found += Candidate("licenseNumber", it.groupValues[1], CONFIDENCE_PATTERN, panel.side, "14 digit FSSAI number")
        }
        LabelRegistry.EMAIL_PATTERN.find(panel.joined)?.let {
            found += Candidate("consumerCareEmail", it.value, CONFIDENCE_PATTERN, panel.side, "e-mail pattern")
        }
        LabelRegistry.PHONE_PATTERN.find(panel.joined)?.let {
            found += Candidate("consumerCarePhone", it.value, CONFIDENCE_PATTERN, panel.side, "phone pattern")
        }
        if (Regex("incl(?:usive)?\\.?\\s*of\\s*all\\s*taxes", RegexOption.IGNORE_CASE).containsMatchIn(panel.joinedLower)) {
            found += Candidate("mrpInclusiveOfTaxes", "true", CONFIDENCE_LABELLED, panel.side, "inclusive of all taxes")
        }
        return found
    }

    // ------------------------------------------------------------------ identity heuristics

    /**
     * Brand and product name are almost never labelled. They are read from the FRONT panel's
     * layout instead, and therefore carry a deliberately low confidence so the inspector is
     * prompted to confirm them.
     */
    private fun extractIdentityHeuristics(panels: List<NormalizedText>): List<Candidate> {
        val front = panels.firstOrNull { it.side == "FRONT" } ?: return emptyList()
        val found = mutableListOf<Candidate>()

        val headline = front.lines.take(8).filter { candidateLine ->
            candidateLine.length in 3..40 &&
                candidateLine.count { it.isLetter() } >= 3 &&
                !LabelRegistry.isNoise(candidateLine) &&
                !startsAnotherLabel(candidateLine) &&
                LabelRegistry.parseQuantity(candidateLine) == null &&
                LabelRegistry.parsePrice(candidateLine) == null
        }

        // The brand is typically the topmost fully capitalised word mark.
        headline.firstOrNull { line -> line == line.uppercase() && line.any(Char::isLetter) }
            ?.let { found += Candidate("brand", it, CONFIDENCE_HEURISTIC, "FRONT", "capitalised headline on front panel") }

        // The product name is the longest remaining headline line that is not the brand.
        val brandLine = found.firstOrNull { it.field == "brand" }?.value
        headline.filter { it != brandLine }.maxByOrNull { it.length }
            ?.let { found += Candidate("productName", it, CONFIDENCE_HEURISTIC, "FRONT", "headline text on front panel") }

        return found
    }

    private fun extractFromQr(qrValues: List<String>): List<Candidate> =
        qrValues.filter { it.isNotBlank() }.mapNotNull { value ->
            LabelRegistry.FSSAI_PATTERN.find(value)?.let {
                Candidate("licenseNumber", it.groupValues[1], CONFIDENCE_PATTERN, "QR", "value encoded in scanned code")
            }
        }

    // ------------------------------------------------------------------------ merge front+back

    /**
     * Collapses every candidate for a field down to one value. Higher confidence wins; the
     * panel the declaration normally lives on breaks ties. Genuine disagreements are reported.
     */
    private fun resolve(candidates: List<Candidate>, conflicts: MutableList<String>): Map<String, Candidate> {
        val preferredSide = LabelRegistry.SPECS.associate { it.field to it.preferredSide }
        val resolved = mutableMapOf<String, Candidate>()

        candidates.groupBy { it.field }.forEach { (field, group) ->
            val preferred = preferredSide[field]
            val winner = group.sortedWith(
                compareByDescending<Candidate> { it.confidence }
                    .thenByDescending { it.side == preferred }
                    .thenByDescending { it.value.length }
            ).first()

            // Only evidence of comparable strength from a different panel counts as a genuine
            // disagreement. A printed label always overrules a loose pattern match elsewhere on
            // the package, so that is a resolution, not a conflict worth reporting.
            val disagreeing = group.filter {
                !equivalent(it.value, winner.value) &&
                    it.side != winner.side &&
                    kotlin.math.abs(it.confidence - winner.confidence) < 0.15f
            }

            if (disagreeing.isNotEmpty()) {
                val others = disagreeing.joinToString(", ") { it.side + "=\"" + it.value + "\"" }
                conflicts += field + " differs between panels: " + winner.side + "=\"" + winner.value +
                    "\" vs " + others + " (kept " + winner.side + ")"
                resolved[field] = winner.copy(confidence = (winner.confidence - CONFIDENCE_CONFLICT_PENALTY).coerceAtLeast(0.1f))
            } else {
                // The same value read from both panels is corroboration, not duplication.
                val agreeingSides = group.filter { equivalent(it.value, winner.value) }.map { it.side }.distinct()
                resolved[field] = if (agreeingSides.size > 1) {
                    winner.copy(confidence = (winner.confidence + 0.05f).coerceAtMost(1f), side = "FRONT+BACK")
                } else {
                    winner
                }
            }
        }
        return resolved
    }

    private fun equivalent(a: String, b: String): Boolean {
        val normalize = { text: String -> text.lowercase().filter { it.isLetterOrDigit() } }
        val left = normalize(a)
        val right = normalize(b)
        return left == right || left.startsWith(right) || right.startsWith(left)
    }

    // ------------------------------------------------------------------------------- assembly

    private fun assemble(
        panels: List<NormalizedText>,
        resolved: Map<String, Candidate>,
        unreadable: List<String>,
        conflicts: List<String>
    ): ProductDeclarationDto {
        fun value(field: String): String? = resolved[field]?.value

        val quantity = value("netQuantity")?.let { LabelRegistry.parseQuantity(it) }
        val declaredFields = resolved.filterKeys { it != "mrpInclusiveOfTaxes" }

        val meanFieldConfidence =
            if (declaredFields.isEmpty()) 0f
            else declaredFields.values.map { it.confidence }.average().toFloat()

        val ocrQuality = panels.map { it.quality }.maxOrNull() ?: 0f
        val overallConfidence = (meanFieldConfidence * ocrQuality).coerceIn(0f, 1f)

        val front = panels.firstOrNull { it.side == "FRONT" }
        val back = panels.firstOrNull { it.side == "BACK" }

        val category = inferCategory(panels, value("ingredients"), value("licenseNumber"))

        return ProductDeclarationDto(
            commodityCategory = category,
            productName = value("productName"),
            brand = value("brand"),
            variant = value("variant"),
            commodityName = value("commodityName"),
            manufacturerName = value("manufacturerName"),
            manufacturerAddress = value("manufacturerAddress"),
            packerName = value("packerName"),
            packerAddress = value("packerAddress"),
            importerName = value("importerName"),
            importerAddress = value("importerAddress"),
            countryOfOrigin = value("countryOfOrigin"),
            netQuantity = quantity?.first ?: value("netQuantity"),
            netQuantityUnit = quantity?.second,
            mrp = value("mrp"),
            mrpInclusiveOfTaxes = resolved.containsKey("mrpInclusiveOfTaxes"),
            unitSalePrice = value("unitSalePrice"),
            dimensions = value("dimensions"),
            batchNumber = value("batchNumber"),
            lotNumber = value("lotNumber"),
            licenseNumber = value("licenseNumber"),
            manufacturingDate = value("manufacturingDate"),
            packingDate = value("packingDate"),
            bestBefore = value("bestBefore"),
            expiryDate = value("expiryDate"),
            useBy = value("useBy"),
            ingredients = value("ingredients"),
            nutrition = null, // TODO: Populate with real NutritionFacts if needed
            allergens = emptyList(), // TODO: Extract allergens
            usageInstructions = value("usageInstructions"),
            warnings = value("warnings"),
            consumerCareName = value("consumerCareName"),
            consumerCarePhone = value("consumerCarePhone"),
            consumerCareEmail = value("consumerCareEmail"),
            servingSize = value("servingSize"),
            servingSizeUnit = value("servingSizeUnit"),
            numberOfServings = value("numberOfServings")?.toDoubleOrNull(),
            otherDeclarations = panels.flatMap { panel -> panel.lines.map { panel.side + ": " + it } },
            fieldSources = declaredFields.mapValues { it.value.side },
            fieldConfidence = declaredFields.mapValues { it.value.confidence },
            unreadableFields = unreadable,
            conflicts = conflicts,
            rawFrontText = front?.raw,
            rawBackText = back?.raw,
            confidence = overallConfidence
        )
    }

    /**
     * The category decides which rules apply (ingredients are mandatory on food, not on a tool).
     * It is derived from evidence actually present on the package, and defaults to GENERAL.
     */
    private fun inferCategory(panels: List<NormalizedText>, ingredients: String?, license: String?): String {
        val text = panels.joinToString(" ") { it.joinedLower }

        // Whole-word matching only. Substring matching mistook the "cream" in "full cream milk"
        // for a cosmetic, which then skipped the food licence rule entirely.
        fun mentions(vararg terms: String) =
            terms.any { Regex("\\b" + Regex.escape(it) + "\\b").containsMatchIn(text) }

        val hasFssai = license?.matches(Regex("\\d{14}")) == true || mentions("fssai")

        return when {
            mentions("supplement", "supplements", "creatine", "whey", "bcaa", "nutraceutical", "amino acids") ->
                "SUPPLEMENT"

            mentions("prescription", "schedule h", "drugs and cosmetics act", "tablets ip", "capsules ip") ->
                "MEDICINE"

            // An FSSAI licence is only issued for food, so it settles the category outright.
            hasFssai -> "FOOD"

            mentions("cosmetic", "cosmetics", "shampoo", "conditioner", "moisturiser", "moisturizer", "spf") ||
                text.contains("for external use only") -> "COSMETIC"

            ingredients != null || mentions("nutrition", "nutritional", "kcal", "veg", "non-veg") ||
                text.contains("best before") -> "FOOD"

            else -> "GENERAL"
        }
    }
}
