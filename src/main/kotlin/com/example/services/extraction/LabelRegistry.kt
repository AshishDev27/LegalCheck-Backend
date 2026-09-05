package com.example.services.extraction

/**
 * How a single declaration field is recognised in OCR text.
 *
 * Extraction is label-driven first (an explicit printed label such as "Net Qty:" is the strongest
 * evidence), pattern-driven second, and only falls back to layout heuristics for the two fields
 * that are almost never labelled on a package: brand and product name.
 */
data class FieldSpec(
    val field: String,
    /** Label regexes, anchored so they only match at the start of a line or after punctuation. */
    val labels: List<Regex> = emptyList(),
    /** Which panel this declaration normally lives on; used to break front/back ties. */
    val preferredSide: String? = null,
    /** How many following lines may be absorbed when the label line carries no value. */
    val continuationLines: Int = 1,
    /** Rejects or rewrites a raw candidate; returning null means "found a label but no usable value". */
    val clean: (String) -> String? = { it.trim().ifBlank { null } }
)

object LabelRegistry {

    private fun label(vararg alternatives: String): List<Regex> =
        alternatives.map { Regex("(?:^|[\\s\\-\\u2013\\u2014(\\[|/])" + it + "\\b", RegexOption.IGNORE_CASE) }

    val UNIT_ALIASES: Map<String, String> = mapOf(
        "gm" to "g", "gms" to "g", "gram" to "g", "grams" to "g", "grm" to "g",
        "kilogram" to "kg", "kilograms" to "kg", "kgs" to "kg",
        "milligram" to "mg", "milligrams" to "mg", "mgs" to "mg",
        "litre" to "l", "litres" to "l", "liter" to "l", "liters" to "l", "lt" to "l", "ltr" to "l", "ltrs" to "l",
        "millilitre" to "ml", "millilitres" to "ml", "milliliter" to "ml", "milliliters" to "ml", "mls" to "ml",
        "piece" to "u", "pieces" to "u", "pcs" to "u", "pc" to "u", "nos" to "u", "no" to "u",
        "unit" to "u", "units" to "u", "n" to "u",
        "tablet" to "u", "tablets" to "u", "capsule" to "u", "capsules" to "u", "serving" to "u", "servings" to "u"
    )

    val STANDARD_UNITS: Set<String> = setOf("kg", "g", "mg", "l", "ml", "m", "cm", "mm", "u")

    private val QUANTITY_PATTERN = Regex(
        "(\\d+(?:[.,]\\d+)?)\\s*(kgs?|kilograms?|gms?|grams?|g|mgs?|milligrams?|" +
            "ltrs?|lt|litres?|liters?|l|mls?|millilitres?|milliliters?|ml|" +
            "cm|mm|m|nos?|pcs?|pieces?|units?|u|n|tablets?|capsules?|servings?)\\b",
        RegexOption.IGNORE_CASE
    )

    private val PRICE_PATTERN = Regex(
        "(?:₹|rs\\.?|inr)\\s*([0-9OolIS][0-9OolIS.,]*)",
        RegexOption.IGNORE_CASE
    )

    private const val MONTH = "jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec"

    private val DATE_PATTERN = Regex(
        "(\\d{1,2}[/\\-.]\\d{1,2}[/\\-.]\\d{2,4}" +
            "|\\d{1,2}[/\\-.]\\d{4}" +
            "|(?:" + MONTH + ")[a-z]*[\\s/\\-.]*\\d{2,4}" +
            "|\\d{4}[/\\-.]\\d{1,2})",
        RegexOption.IGNORE_CASE
    )

    private val DURATION_PATTERN = Regex(
        "(\\d{1,3}\\s*(?:days?|weeks?|months?|years?)\\b[^\\n]{0,40})",
        RegexOption.IGNORE_CASE
    )

    val EMAIL_PATTERN = Regex("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}")
    val PHONE_PATTERN = Regex("(?:\\+?91[\\s\\-]?)?(?:1800[\\s\\-]?\\d{3}[\\s\\-]?\\d{3,4}|[6-9]\\d{9})")
    val FSSAI_PATTERN = Regex("\\b(\\d{14})\\b")

    fun parseQuantity(text: String): Pair<String, String>? {
        val match = QUANTITY_PATTERN.find(TextNormalizer.repairDigits(text)) ?: return null
        val amount = match.groupValues[1].replace(",", "")
        val numeric = amount.toDoubleOrNull() ?: return null
        if (numeric <= 0.0) return null
        val rawUnit = match.groupValues[2].lowercase()
        val unit = UNIT_ALIASES[rawUnit] ?: rawUnit
        return amount to unit
    }

    fun parsePrice(text: String): String? {
        val match = PRICE_PATTERN.find(text) ?: return null
        val repaired = TextNormalizer.repairDigits(match.groupValues[1]).replace(",", "").trimEnd('.')
        val amount = repaired.toDoubleOrNull() ?: return null
        if (amount <= 0.0) return null
        return if (amount == amount.toLong().toDouble()) amount.toLong().toString() else amount.toString()
    }

    fun findDate(text: String): String? = DATE_PATTERN.find(text)?.value?.trim()

    fun findDuration(text: String): String? = DURATION_PATTERN.find(text)?.value?.trim()

    /** Lines that only carry marketing or instruction copy and must never become a field value. */
    private val NOISE_MARKERS = listOf(
        "details of", "read the", "as mentioned", "see below", "see above", "refer to",
        "printed on", "printed below", "given below", "shown below", "mentioned below",
        "stated below", "listed below", "for more", "scan the", "visit ", "follow us",
        "www.", "http", "best served", "shake well", "terms and conditions"
    )

    /**
     * Words that begin a sentence about a declaration rather than the declaration itself.
     * "Manufacturer: are printed below" must not become a manufacturer name.
     */
    private val SENTENCE_STARTERS = setOf(
        "are", "is", "was", "the", "of", "and", "for", "see", "as", "on", "in", "at", "to",
        "by", "from", "with", "this", "that", "these", "those", "please", "details", "detail",
        "printed", "given", "shown", "mentioned", "stated", "listed", "refer", "read", "check"
    )

    fun isNoise(line: String): Boolean {
        val lower = line.lowercase()
        return NOISE_MARKERS.any { lower.contains(it) }
    }

    private fun entityName(raw: String): String? {
        val cleaned = raw.trim().trim(',', '.', '-', ':')
        if (cleaned.length < 3 || isNoise(cleaned)) return null
        if (cleaned.count { it.isLetter() } < 3) return null
        val firstWord = cleaned.substringBefore(' ').lowercase().trim('.', ',')
        if (firstWord in SENTENCE_STARTERS) return null
        return cleaned
    }

    private fun freeText(raw: String): String? {
        val cleaned = raw.trim().trim(',', ':', '-')
        return if (cleaned.length < 2) null else cleaned
    }

    private fun firstToken(raw: String, range: IntRange): String? =
        raw.trim().split(" ").firstOrNull()?.trim('.', ',', ':', ';')
            ?.takeIf { it.length in range && it.any(Char::isLetterOrDigit) }

    val SPECS: List<FieldSpec> = listOf(
        FieldSpec(
            field = "manufacturerName",
            labels = label(
                "manufactured\\s*(?:&|and)?\\s*(?:packed|marketed)?\\s*by", "manufacturer",
                "mfd\\.?\\s*by", "mfg\\.?\\s*by", "mfr", "manufactured\\s*at"
            ),
            preferredSide = "BACK",
            continuationLines = 4,
            clean = ::entityName
        ),
        FieldSpec(
            field = "packerName",
            labels = label("packed\\s*(?:&|and)?\\s*(?:marketed)?\\s*by", "packer", "marketed\\s*by", "pkd\\.?\\s*by"),
            preferredSide = "BACK",
            continuationLines = 4,
            clean = ::entityName
        ),
        FieldSpec(
            field = "importerName",
            labels = label("imported\\s*(?:&|and)?\\s*(?:marketed|distributed)?\\s*by", "importer"),
            preferredSide = "BACK",
            continuationLines = 4,
            clean = ::entityName
        ),
        FieldSpec(
            field = "countryOfOrigin",
            labels = label("country\\s*of\\s*origin", "made\\s*in", "product\\s*of", "origin"),
            preferredSide = "BACK",
            clean = { raw -> raw.trim().trim('.', ',').takeIf { it.length in 2..40 && it.any(Char::isLetter) } }
        ),
        FieldSpec(
            field = "netQuantity",
            labels = label(
                "net\\s*(?:qty|quantity|wt|weight|content|contents|vol|volume)",
                "quantity", "net\\s*wt\\.?"
            ),
            preferredSide = "BACK",
            clean = { raw -> parseQuantity(raw)?.let { it.first + " " + it.second } }
        ),
        FieldSpec(
            field = "mrp",
            labels = label("m\\.?\\s*r\\.?\\s*p\\.?", "maximum\\s*retail\\s*price", "retail\\s*price", "price"),
            preferredSide = "BACK",
            clean = { raw -> parsePrice(raw) ?: parsePrice("₹" + raw) }
        ),
        FieldSpec(
            field = "unitSalePrice",
            labels = label("unit\\s*sale\\s*price", "price\\s*per\\s*(?:unit|gm|g|kg|ml|l)"),
            preferredSide = "BACK",
            clean = { raw -> parsePrice(raw) ?: parsePrice("₹" + raw) }
        ),
        FieldSpec(
            field = "manufacturingDate",
            labels = label(
                "mfg\\.?\\s*(?:date|dt)?", "mfd\\.?\\s*(?:date|dt|on)?", "date\\s*of\\s*(?:manufacture|mfg)",
                "manufactured\\s*on"
            ),
            preferredSide = "BACK",
            clean = { raw -> findDate(TextNormalizer.repairDigits(raw)) }
        ),
        FieldSpec(
            field = "packingDate",
            labels = label("date\\s*of\\s*pack(?:ing|ed)?", "packed\\s*on", "pkd\\.?\\s*(?:date|dt|on)?"),
            preferredSide = "BACK",
            clean = { raw -> findDate(TextNormalizer.repairDigits(raw)) }
        ),
        FieldSpec(
            field = "expiryDate",
            labels = label("exp(?:iry|ires)?\\.?\\s*(?:date|dt|on)?", "date\\s*of\\s*expiry", "use\\s*before"),
            preferredSide = "BACK",
            clean = { raw ->
                val repaired = TextNormalizer.repairDigits(raw)
                findDate(repaired) ?: findDuration(repaired)
            }
        ),
        FieldSpec(
            field = "bestBefore",
            labels = label("best\\s*before(?:\\s*end)?"),
            preferredSide = "BACK",
            clean = { raw ->
                val repaired = TextNormalizer.repairDigits(raw)
                findDuration(repaired) ?: findDate(repaired) ?: freeText(repaired)
            }
        ),
        FieldSpec(
            field = "useBy",
            labels = label("use\\s*by", "consume\\s*(?:before|by)"),
            preferredSide = "BACK",
            clean = { raw ->
                val repaired = TextNormalizer.repairDigits(raw)
                findDate(repaired) ?: findDuration(repaired)
            }
        ),
        FieldSpec(
            field = "batchNumber",
            labels = label("batch\\s*(?:no|number|code)?\\.?", "b\\.?\\s*no", "bn"),
            preferredSide = "BACK",
            clean = { raw -> firstToken(raw, 2..24) }
        ),
        FieldSpec(
            field = "lotNumber",
            labels = label("lot\\s*(?:no|number)?\\.?"),
            preferredSide = "BACK",
            clean = { raw -> firstToken(raw, 2..24) }
        ),
        FieldSpec(
            field = "licenseNumber",
            labels = label(
                "fssai\\s*(?:lic(?:ence|ense)?)?\\s*(?:no|number)?\\.?", "lic(?:ence|ense)?\\s*(?:no|number)?\\.?",
                "reg(?:istration)?\\s*(?:no|number)\\.?"
            ),
            preferredSide = "BACK",
            clean = { raw ->
                val token = raw.trim().split(" ").firstOrNull().orEmpty()
                TextNormalizer.repairDigits(token).trim('.', ',', ':')
                    .takeIf { it.length in 6..24 && it.any(Char::isDigit) }
            }
        ),
        FieldSpec(
            field = "ingredients",
            labels = label("ingredients?(?:\\s*list)?", "composition", "contains"),
            preferredSide = "BACK",
            continuationLines = 6,
            clean = ::freeText
        ),
        FieldSpec(
            field = "nutritionInfo",
            labels = label("nutrition(?:al)?\\s*(?:information|info|facts)?", "nutritive\\s*value", "per\\s*serving"),
            preferredSide = "BACK",
            continuationLines = 10,
            clean = ::freeText
        ),
        FieldSpec(
            field = "usageInstructions",
            labels = label(
                "directions?\\s*(?:for|of)?\\s*use", "how\\s*to\\s*use", "recommended\\s*(?:use|dosage)",
                "suggested\\s*use", "dosage", "usage"
            ),
            preferredSide = "BACK",
            continuationLines = 4,
            clean = ::freeText
        ),
        FieldSpec(
            field = "warnings",
            labels = label(
                "warning", "caution", "allergen\\s*(?:information|advice)?",
                "storage\\s*(?:instructions|conditions?)?", "keep\\s*(?:out\\s*of|away)"
            ),
            preferredSide = "BACK",
            continuationLines = 3,
            clean = ::freeText
        ),
        FieldSpec(
            field = "consumerCareName",
            labels = label(
                "consumer\\s*(?:care|complaints?)", "customer\\s*(?:care|service|support)",
                "for\\s*(?:any\\s*)?(?:queries|complaints|feedback)", "grievance"
            ),
            preferredSide = "BACK",
            continuationLines = 3,
            clean = ::freeText
        ),
        FieldSpec(
            field = "commodityName",
            labels = label(
                "common\\s*(?:or\\s*generic\\s*)?name", "generic\\s*name", "product\\s*(?:name|type)",
                "name\\s*of\\s*(?:the\\s*)?(?:product|commodity)"
            ),
            clean = ::entityName
        ),
        FieldSpec(
            field = "brand",
            labels = label("brand(?:\\s*name)?", "marketed\\s*under"),
            preferredSide = "FRONT",
            clean = ::entityName
        ),
        FieldSpec(
            field = "variant",
            labels = label("flavou?r", "variant", "shade"),
            preferredSide = "FRONT",
            clean = ::freeText
        ),
        FieldSpec(
            field = "dimensions",
            labels = label("dimensions?"),
            preferredSide = "BACK",
            clean = ::freeText
        ),
        FieldSpec(
            field = "consumerCareEmail",
            labels = label("e-?mail", "email\\s*id"),
            preferredSide = "BACK",
            clean = { raw -> EMAIL_PATTERN.find(raw)?.value }
        ),
        FieldSpec(
            field = "consumerCarePhone",
            labels = label(
                "ph(?:one)?\\.?\\s*(?:no)?", "tel(?:ephone)?", "toll\\s*free",
                "contact\\s*(?:no|number)?", "helpline", "mobile"
            ),
            preferredSide = "BACK",
            clean = { raw -> PHONE_PATTERN.find(TextNormalizer.repairDigits(raw))?.value }
        )
    )
}
