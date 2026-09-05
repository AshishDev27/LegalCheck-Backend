package com.example.services.validation

import com.example.dtos.ProductDeclarationDto
import com.example.services.extraction.LabelRegistry
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Serializable
enum class IssueType {
    /** No value at all was found for a field. */
    MISSING,

    /** A printed label was found but its value could not be read. */
    UNREADABLE,

    /** A value was read but does not satisfy the format the field requires. */
    INVALID_FORMAT,

    /** The two panels disagree about the same field. */
    CONFLICT,

    /** The value parses but is implausible (a future manufacture date, an expired product). */
    SUSPICIOUS
}

@Serializable
data class ValidationIssue(
    val field: String,
    val type: IssueType,
    val message: String,
    val detectedValue: String? = null
)

@Serializable
data class ValidationReport(
    val issues: List<ValidationIssue> = emptyList(),
    val validatedFields: List<String> = emptyList()
) {
    fun issuesFor(field: String): List<ValidationIssue> = issues.filter { it.field == field }
    fun hasProblem(field: String): Boolean = issuesFor(field).any { it.type != IssueType.MISSING }
    fun isMissing(field: String): Boolean = issuesFor(field).any { it.type == IssueType.MISSING }
    fun isUnreadable(field: String): Boolean = issuesFor(field).any { it.type == IssueType.UNREADABLE }
}

/**
 * Checks the extracted declaration before any compliance rule runs.
 *
 * The point of this stage is that finding text is not the same as finding a valid declaration:
 * an MRP that will not parse as money, an expiry that precedes manufacture, or a 9 digit FSSAI
 * number must not be allowed to pass a compliance rule just because the field was non-empty.
 */
object DeclarationValidator {

    private val DATE_PATTERNS = listOf(
        "dd/MM/yyyy", "dd-MM-yyyy", "dd.MM.yyyy",
        "MM/yyyy", "MM-yyyy", "MM.yyyy",
        "yyyy-MM-dd", "yyyy/MM/dd", "yyyy-MM", "yyyy/MM",
        "MMM yyyy", "MMM-yyyy", "MMM/yyyy", "MMMM yyyy",
        "dd MMM yyyy", "dd-MMM-yyyy",
        "MM/yy", "MM-yy", "MMM yy", "MMM-yy"
    )

    private val DURATION = Regex("(\\d{1,3})\\s*(days?|weeks?|months?|years?)", RegexOption.IGNORE_CASE)

    fun validate(declaration: ProductDeclarationDto, today: LocalDate = LocalDate.now()): ValidationReport {
        val issues = mutableListOf<ValidationIssue>()
        val validated = mutableListOf<String>()

        declaration.unreadableFields.forEach {
            issues += ValidationIssue(it, IssueType.UNREADABLE, "A label for this field was detected but the value could not be read.")
        }
        declaration.conflicts.forEach {
            issues += ValidationIssue(it.substringBefore(" "), IssueType.CONFLICT, it)
        }

        validateIdentity(declaration, issues, validated)
        validateResponsibleEntity(declaration, issues, validated)
        validateQuantity(declaration, issues, validated)
        validatePrice(declaration, issues, validated)
        val manufactured = validateDates(declaration, issues, validated, today)
        validateExpiry(declaration, issues, validated, today, manufactured)
        validateTraceability(declaration, issues, validated)
        validateContent(declaration, issues, validated)
        validateNutrition(declaration, issues, validated)
        validateConsumerCare(declaration, issues, validated)

        return ValidationReport(issues = issues, validatedFields = validated.distinct())
    }

    private fun ProductDeclarationDto.present(value: String?): Boolean = !value.isNullOrBlank()

    /**
     * Whether the package holds something consumed.
     *
     * An UNKNOWN category means the extractor declined to guess, not that the rules stop applying:
     * a package carrying a nutrition table or an ingredient list is consumed whether or not its
     * type could be named.
     */
    private fun ProductDeclarationDto.isConsumable(): Boolean =
        commodityCategory in setOf("FOOD", "SUPPLEMENT", "COSMETIC", "MEDICINE") ||
            (commodityCategory == "UNKNOWN" &&
                (nutrition != null || !ingredients.isNullOrBlank() || allergens.isNotEmpty()))

    private fun validateIdentity(
        declaration: ProductDeclarationDto,
        issues: MutableList<ValidationIssue>,
        validated: MutableList<String>
    ) {
        val name = declaration.commodityName ?: declaration.productName
        if (name.isNullOrBlank()) {
            if (!declaration.unreadableFields.contains("productName")) {
                issues += ValidationIssue("productName", IssueType.MISSING, "No product or commodity name was detected on either panel.")
            }
        } else if (name.length < 2) {
            issues += ValidationIssue("productName", IssueType.INVALID_FORMAT, "The detected product name is too short to be a real declaration.", name)
        } else {
            validated += "productName"
        }

        if (declaration.brand.isNullOrBlank()) {
            issues += ValidationIssue("brand", IssueType.MISSING, "No brand name was detected on the front panel.")
        } else {
            validated += "brand"
        }
    }

    private fun validateResponsibleEntity(
        declaration: ProductDeclarationDto,
        issues: MutableList<ValidationIssue>,
        validated: MutableList<String>
    ) {
        val entities = listOf(
            "manufacturer" to (declaration.manufacturerName to declaration.manufacturerAddress),
            "packer" to (declaration.packerName to declaration.packerAddress),
            "importer" to (declaration.importerName to declaration.importerAddress)
        )
        val named = entities.filter { !it.second.first.isNullOrBlank() }

        if (named.isEmpty()) {
            issues += ValidationIssue("manufacturer", IssueType.MISSING, "No manufacturer, packer or importer was detected on the package.")
            return
        }

        named.forEach { (role, pair) ->
            val (name, address) = pair
            if (address.isNullOrBlank()) {
                issues += ValidationIssue(
                    role, IssueType.INVALID_FORMAT,
                    "The $role is named but no complete address follows it; an address is part of the declaration.",
                    name
                )
            } else {
                validated += role
            }
        }

        if (!declaration.importerName.isNullOrBlank() && declaration.countryOfOrigin.isNullOrBlank()) {
            issues += ValidationIssue(
                "countryOfOrigin", IssueType.MISSING,
                "An importer is declared, so the country of origin must also be declared."
            )
        }
    }

    private fun validateQuantity(
        declaration: ProductDeclarationDto,
        issues: MutableList<ValidationIssue>,
        validated: MutableList<String>
    ) {
        val quantity = declaration.netQuantity
        if (quantity.isNullOrBlank()) {
            if (!declaration.unreadableFields.contains("netQuantity")) {
                issues += ValidationIssue("netQuantity", IssueType.MISSING, "No net quantity declaration was detected.")
            }
            return
        }

        val amount = quantity.trim().replace(",", "").toDoubleOrNull()
            ?: LabelRegistry.parseQuantity(quantity)?.first?.toDoubleOrNull()

        if (amount == null) {
            issues += ValidationIssue("netQuantity", IssueType.INVALID_FORMAT, "The net quantity is not a readable number.", quantity)
            return
        }
        if (amount <= 0.0) {
            issues += ValidationIssue("netQuantity", IssueType.INVALID_FORMAT, "The net quantity must be greater than zero.", quantity)
            return
        }

        val unit = declaration.netQuantityUnit?.lowercase()?.trim()
        when {
            unit.isNullOrBlank() ->
                issues += ValidationIssue("netQuantityUnit", IssueType.MISSING, "A quantity was detected but without a unit of measure.", quantity)
            !LabelRegistry.STANDARD_UNITS.contains(unit) ->
                issues += ValidationIssue(
                    "netQuantityUnit", IssueType.INVALID_FORMAT,
                    "\"" + unit + "\" is not a standard unit of mass, volume, length or number.", unit
                )
            else -> validated += "netQuantity"
        }
    }

    private fun validatePrice(
        declaration: ProductDeclarationDto,
        issues: MutableList<ValidationIssue>,
        validated: MutableList<String>
    ) {
        val mrp = declaration.mrp
        if (mrp.isNullOrBlank()) {
            if (!declaration.unreadableFields.contains("mrp")) {
                issues += ValidationIssue("mrp", IssueType.MISSING, "No maximum retail price was detected.")
            }
            return
        }

        val amount = mrp.replace(Regex("[^0-9.]"), "").toDoubleOrNull()
        when {
            amount == null ->
                issues += ValidationIssue("mrp", IssueType.INVALID_FORMAT, "The detected MRP is not a readable amount.", mrp)
            amount <= 0.0 ->
                issues += ValidationIssue("mrp", IssueType.INVALID_FORMAT, "The MRP must be greater than zero.", mrp)
            amount > 1_000_000 ->
                issues += ValidationIssue("mrp", IssueType.SUSPICIOUS, "The detected MRP is implausibly large and is likely an OCR error.", mrp)
            else -> validated += "mrp"
        }
    }

    /** Returns the parsed manufacture/packing date, when one could be read. */
    private fun validateDates(
        declaration: ProductDeclarationDto,
        issues: MutableList<ValidationIssue>,
        validated: MutableList<String>,
        today: LocalDate
    ): LocalDate? {
        val raw = declaration.manufacturingDate ?: declaration.packingDate
        if (raw.isNullOrBlank()) {
            if (!declaration.unreadableFields.contains("manufacturingDate")) {
                issues += ValidationIssue("manufacturingDate", IssueType.MISSING, "No date of manufacture or packing was detected.")
            }
            return null
        }

        val parsed = parseDate(raw)
        if (parsed == null) {
            issues += ValidationIssue("manufacturingDate", IssueType.INVALID_FORMAT, "\"" + raw + "\" could not be read as a date.", raw)
            return null
        }
        if (parsed.isAfter(today)) {
            issues += ValidationIssue("manufacturingDate", IssueType.SUSPICIOUS, "The date of manufacture is in the future.", raw)
            return parsed
        }
        if (parsed.isBefore(today.minusYears(15))) {
            issues += ValidationIssue("manufacturingDate", IssueType.SUSPICIOUS, "The date of manufacture is more than 15 years old and is likely misread.", raw)
            return parsed
        }
        validated += "manufacturingDate"
        return parsed
    }

    private fun validateExpiry(
        declaration: ProductDeclarationDto,
        issues: MutableList<ValidationIssue>,
        validated: MutableList<String>,
        today: LocalDate,
        manufactured: LocalDate?
    ) {
        val raw = declaration.expiryDate ?: declaration.useBy ?: declaration.bestBefore
        if (raw.isNullOrBlank()) {
            if (!declaration.unreadableFields.contains("expiryDate")) {
                issues += ValidationIssue("expiryDate", IssueType.MISSING, "No expiry, use-by or best-before declaration was detected.")
            }
            return
        }

        // "Best before 24 months from packaging" is a valid declaration, not a date.
        val duration = DURATION.find(raw)
        if (duration != null) {
            validated += "expiryDate"
            if (manufactured == null) {
                issues += ValidationIssue(
                    "expiryDate", IssueType.SUSPICIOUS,
                    "Shelf life is declared as a duration but no manufacture date was read, so the actual expiry cannot be established.",
                    raw
                )
            }
            return
        }

        val parsed = parseDate(raw)
        when {
            parsed == null ->
                issues += ValidationIssue("expiryDate", IssueType.INVALID_FORMAT, "\"" + raw + "\" could not be read as an expiry date or shelf life.", raw)
            manufactured != null && parsed.isBefore(manufactured) ->
                issues += ValidationIssue("expiryDate", IssueType.INVALID_FORMAT, "The expiry date precedes the date of manufacture.", raw)
            parsed.isBefore(today) ->
                issues += ValidationIssue("expiryDate", IssueType.SUSPICIOUS, "The product is past its declared expiry date.", raw)
            else -> validated += "expiryDate"
        }
    }

    private fun validateTraceability(
        declaration: ProductDeclarationDto,
        issues: MutableList<ValidationIssue>,
        validated: MutableList<String>
    ) {
        val batch = declaration.batchNumber ?: declaration.lotNumber
        if (batch.isNullOrBlank()) {
            issues += ValidationIssue("batchNumber", IssueType.MISSING, "No batch or lot number was detected.")
        } else if (!batch.any(Char::isLetterOrDigit)) {
            issues += ValidationIssue("batchNumber", IssueType.INVALID_FORMAT, "The detected batch number contains no usable characters.", batch)
        } else {
            validated += "batchNumber"
        }

        val license = declaration.licenseNumber
        val needsFssai = declaration.commodityCategory in setOf("FOOD", "SUPPLEMENT") ||
            (declaration.commodityCategory == "UNKNOWN" && declaration.nutrition != null)
        when {
            license.isNullOrBlank() ->
                if (!declaration.unreadableFields.contains("licenseNumber")) {
                    issues += ValidationIssue("licenseNumber", IssueType.MISSING, "No licence or registration number was detected.")
                }
            needsFssai && !license.matches(Regex("\\d{14}")) ->
                issues += ValidationIssue(
                    "licenseNumber", IssueType.INVALID_FORMAT,
                    "An FSSAI licence number must be exactly 14 digits; \"" + license + "\" is not.", license
                )
            else -> validated += "licenseNumber"
        }
    }

    private fun validateContent(
        declaration: ProductDeclarationDto,
        issues: MutableList<ValidationIssue>,
        validated: MutableList<String>
    ) {
        if (!declaration.isConsumable()) return

        val ingredients = declaration.ingredients
        if (ingredients.isNullOrBlank()) {
            if (!declaration.unreadableFields.contains("ingredients")) {
                    val subject = if (declaration.commodityCategory == "UNKNOWN") "consumable" else declaration.commodityCategory.lowercase()
                issues += ValidationIssue("ingredients", IssueType.MISSING, "No ingredient or composition list was detected for a " + subject + " product.")
            }
        } else if (ingredients.length < 4) {
            issues += ValidationIssue("ingredients", IssueType.UNREADABLE, "The ingredient list was detected but is too fragmentary to read.", ingredients)
        } else {
            validated += "ingredients"
        }
    }

    /**
     * Checks the nutrition panel against the rest of the declaration.
     *
     * The serving figures give a cross-check nothing else does: a declared serving size multiplied
     * by the declared number of servings should come back to the net quantity. When it does not,
     * one of the three was misread, and saying which is beyond the scanner - so the discrepancy is
     * reported rather than silently resolved.
     */
    private fun validateNutrition(
        declaration: ProductDeclarationDto,
        issues: MutableList<ValidationIssue>,
        validated: MutableList<String>
    ) {
        val nutrition = declaration.nutrition ?: return

        if (nutrition.nutrients.isEmpty()) {
            issues += ValidationIssue(
                "nutrition", IssueType.UNREADABLE,
                "A nutrition panel was found but none of its rows could be read.",
                nutrition.evidence
            )
            return
        }
        validated += "nutrition"

        val servingSize = declaration.servingSize?.toDoubleOrNull() ?: return
        val servings = declaration.numberOfServings ?: return
        val netQuantity = declaration.netQuantity?.toDoubleOrNull() ?: return
        if (declaration.servingSizeUnit != declaration.netQuantityUnit || netQuantity <= 0.0) return

        val implied = servingSize * servings
        val drift = kotlin.math.abs(implied - netQuantity) / netQuantity
        if (drift > 0.2) {
            issues += ValidationIssue(
                "numberOfServings", IssueType.SUSPICIOUS,
                "The serving size and number of servings imply " + Math.round(implied) +
                    " " + declaration.servingSizeUnit + ", which does not agree with the declared net quantity of " +
                    declaration.netQuantity + " " + declaration.netQuantityUnit + ".",
                declaration.servingSize
            )
        } else {
            validated += "numberOfServings"
        }
    }

    private fun validateConsumerCare(
        declaration: ProductDeclarationDto,
        issues: MutableList<ValidationIssue>,
        validated: MutableList<String>
    ) {
        val hasContact = !declaration.consumerCarePhone.isNullOrBlank() ||
            !declaration.consumerCareEmail.isNullOrBlank()
        val hasName = !declaration.consumerCareName.isNullOrBlank()

        when {
            !hasContact && !hasName ->
                issues += ValidationIssue("consumerCare", IssueType.MISSING, "No consumer care contact details were detected.")
            !hasContact ->
                issues += ValidationIssue(
                    "consumerCare", IssueType.INVALID_FORMAT,
                    "A consumer care cell is named but no phone number or e-mail address was detected.",
                    declaration.consumerCareName
                )
            else -> validated += "consumerCare"
        }
    }

    /** Parses the date formats that actually appear on Indian packaging. */
    fun parseDate(raw: String): LocalDate? {
        val cleaned = raw.trim().replace(Regex("\\s+"), " ")
        for (pattern in DATE_PATTERNS) {
            val formatter = DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH)
            try {
                return if (pattern.contains("d")) {
                    LocalDate.parse(cleaned, formatter)
                } else {
                    YearMonth.parse(cleaned, formatter).atEndOfMonth()
                }
            } catch (_: Exception) {
                // Try the next known packaging date format.
            }
        }
        return null
    }
}
