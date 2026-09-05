package com.example.compliance.rules.v2026

import com.example.compliance.core.ComplianceRule
import com.example.compliance.core.ComplianceStatus
import com.example.compliance.core.IRule
import com.example.compliance.core.RuleCheck
import com.example.compliance.core.RuleContext
import com.example.compliance.core.RuleSeverity
import com.example.compliance.core.RuleSourceRegistry
import com.example.services.validation.IssueType

/**
 * The mandatory declarations of the Legal Metrology (Packaged Commodities) Rules.
 *
 * Every rule follows the same shape: read the field the rule is about, then decide between
 * PASS, WARNING, FAIL and REVIEW_REQUIRED using the validation report. A field that is present
 * but did not validate never counts as a pass, and a field that could not be read is sent for
 * review rather than being reported as a violation the trader is responsible for.
 */
abstract class DeclarationRule(
    ruleId: String,
    ruleNumber: String,
    title: String,
    description: String,
    severity: RuleSeverity,
    protected val field: String,
    protected val requirement: String,
    protected val recommendation: String
) : IRule {

    override val metadata = ComplianceRule(
        ruleId = ruleId,
        ruleNumber = ruleNumber,
        title = title,
        description = description,
        source = RuleSourceRegistry.LMPC_RULES_2011,
        sourceVersion = RuleSourceRegistry.AMENDMENT_2026,
        effectiveFrom = "2026-05-29",
        severity = severity
    )

    /** The value this rule inspects, or null when the package does not carry it. */
    protected abstract fun valueOf(context: RuleContext): String?

    /** Validation issues that decide the outcome; defaults to the issues for [field]. */
    protected open fun relevantIssues(context: RuleContext) = context.issues(field)

    /**
     * The declaration field names this rule reads, used to find the package line the finding rests
     * on. A rule that inspects several fields under one name - "manufacturer" covering the
     * manufacturer, packer and importer - overrides this so its evidence can still be located.
     */
    protected open fun evidenceFields(): List<String> = listOf(field)

    override fun evaluate(context: RuleContext): RuleCheck {
        val value = valueOf(context)
        val issues = relevantIssues(context)
        val confidence = context.confidenceOf(field)

        val unreadable = issues.firstOrNull { it.type == IssueType.UNREADABLE }
        val conflict = issues.firstOrNull { it.type == IssueType.CONFLICT }
        val invalid = issues.firstOrNull { it.type == IssueType.INVALID_FORMAT }
        val suspicious = issues.firstOrNull { it.type == IssueType.SUSPICIOUS }

        return when {
            unreadable != null -> context.check(
                ComplianceStatus.REVIEW_REQUIRED, value, confidence, unreadable.message,
                "Re-scan the panel carrying this declaration in better light, or enter the value manually."
            )

            value.isNullOrBlank() -> context.check(
                ComplianceStatus.FAIL, null, confidence,
                "The mandatory declaration was not found on any scanned panel.", recommendation
            )

            invalid != null -> context.check(ComplianceStatus.FAIL, value, confidence, invalid.message, recommendation)

            suspicious != null -> context.check(
                ComplianceStatus.WARNING, value, confidence, suspicious.message,
                "Verify this value against the physical package before acting on it."
            )

            conflict != null -> context.check(
                ComplianceStatus.WARNING, value, confidence, conflict.message,
                "The front and back panels disagree; confirm which value is printed on the package."
            )

            confidence < 0.6f -> context.check(
                ComplianceStatus.REVIEW_REQUIRED, value, confidence,
                "A value was read but with low confidence, so it cannot be confirmed automatically.",
                "Confirm this field on the review screen before the report is issued."
            )

            else -> context.check(
                ComplianceStatus.PASS, value, confidence,
                "The declaration is present and valid.", null
            )
        }
    }

    private fun RuleContext.check(
        status: ComplianceStatus,
        detected: String?,
        confidence: Float,
        explanation: String,
        recommendation: String?
    ) = RuleCheck(
        ruleId = metadata.ruleId,
        ruleNumber = metadata.ruleNumber,
        title = metadata.title,
        field = field,
        detectedValue = detected,
        expectedRequirement = requirement,
        severity = metadata.severity,
        status = status,
        confidence = confidence,
        explanation = explanation,
        sourceReference = RuleSourceRegistry.getOfficialCitation(metadata.ruleNumber, metadata.source),
        recommendation = recommendation,
        evidence = evidenceOf(evidenceFields()),
        evidenceSource = sourceOf(evidenceFields())
    )

    /**
     * Whether the package holds something consumed, which is what the ingredient, expiry and
     * allergen rules are actually about.
     *
     * An UNKNOWN category is not a licence to skip those rules. When the extractor could not name
     * the category but the package plainly carries a nutrition table, an ingredient list or an
     * allergen declaration, the product is consumed and the rules apply.
     */
    protected fun isConsumable(context: RuleContext): Boolean {
        val declaration = context.declaration
        if (declaration.commodityCategory in setOf("FOOD", "SUPPLEMENT", "COSMETIC", "MEDICINE")) return true
        return declaration.commodityCategory == "UNKNOWN" &&
            (declaration.nutrition != null ||
                !declaration.ingredients.isNullOrBlank() ||
                declaration.allergens.isNotEmpty())
    }
}

class Rule6CommodityNamePresence : DeclarationRule(
    ruleId = "LMPC-2026-R6-NAME",
    ruleNumber = "6(1)(a)",
    title = "Generic name of the commodity",
    description = "The common or generic name of the commodity contained in the package shall be declared.",
    severity = RuleSeverity.CRITICAL,
    field = "productName",
    requirement = "The common or generic name of the commodity must be declared on the principal display panel.",
    recommendation = "Declare the common or generic name of the commodity on the front of the package."
) {
    override fun valueOf(context: RuleContext) =
        context.declaration.commodityName ?: context.declaration.productName

    override fun evidenceFields() = listOf("productName", "commodityName")
}

class Rule10ManufacturerDetails : DeclarationRule(
    ruleId = "LMPC-2026-R10-MFG",
    ruleNumber = "10",
    title = "Manufacturer, packer or importer details",
    description = "The name and complete address of the manufacturer, packer or importer shall be declared.",
    severity = RuleSeverity.CRITICAL,
    field = "manufacturer",
    requirement = "Name and complete address of the manufacturer, packer or importer.",
    recommendation = "Print the full name and complete address of the manufacturer, packer or importer on the package."
) {
    override fun valueOf(context: RuleContext): String? {
        val declaration = context.declaration
        val entity = listOfNotNull(
            declaration.manufacturerName?.let { it to declaration.manufacturerAddress },
            declaration.packerName?.let { it to declaration.packerAddress },
            declaration.importerName?.let { it to declaration.importerAddress }
        ).firstOrNull() ?: return null
        return listOfNotNull(entity.first, entity.second).joinToString(", ")
    }

    override fun relevantIssues(context: RuleContext) =
        context.issues("manufacturer") + context.issues("packer") + context.issues("importer")

    override fun evidenceFields() = listOf("manufacturerName", "packerName", "importerName")
}

class Rule12NetQuantityUnit : DeclarationRule(
    ruleId = "LMPC-2026-R12-QTY",
    ruleNumber = "12",
    title = "Net quantity in standard units",
    description = "Net quantity shall be declared in terms of the standard unit of mass, measure or number.",
    severity = RuleSeverity.CRITICAL,
    field = "netQuantity",
    requirement = "Net quantity declared with a standard unit (kg, g, l, ml, m, cm, or number).",
    recommendation = "Declare the net quantity using a standard unit of mass, measure or number."
) {
    override fun valueOf(context: RuleContext): String? {
        val declaration = context.declaration
        val quantity = declaration.netQuantity ?: return null
        return listOfNotNull(quantity, declaration.netQuantityUnit).joinToString(" ")
    }

    override fun relevantIssues(context: RuleContext) =
        context.issues("netQuantity") + context.issues("netQuantityUnit")

    override fun evidenceFields() = listOf("netQuantity")
}

class Rule18MrpPresence : DeclarationRule(
    ruleId = "LMPC-2026-R18-MRP",
    ruleNumber = "18",
    title = "Maximum retail price",
    description = "Every package shall bear the maximum retail price inclusive of all taxes.",
    severity = RuleSeverity.CRITICAL,
    field = "mrp",
    requirement = "Maximum retail price in rupees.",
    recommendation = "Print the maximum retail price on the package."
) {
    override fun valueOf(context: RuleContext) = context.declaration.mrp?.let { "Rs. " + it }
}

/**
 * Kept separate from the MRP presence rule: a package that prints a price but omits the
 * "inclusive of all taxes" wording is a labelling defect, not a missing declaration.
 */
class Rule18MrpTaxPhrase : IRule {
    override val metadata = ComplianceRule(
        ruleId = "LMPC-2026-R18-TAX-PHRASE",
        ruleNumber = "18",
        title = "MRP stated as inclusive of all taxes",
        description = "The retail sale price shall be declared as inclusive of all taxes.",
        source = RuleSourceRegistry.LMPC_RULES_2011,
        sourceVersion = RuleSourceRegistry.AMENDMENT_2026,
        effectiveFrom = "2026-05-29",
        severity = RuleSeverity.MAJOR
    )

    override fun appliesTo(context: RuleContext) = !context.declaration.mrp.isNullOrBlank()

    override fun evaluate(context: RuleContext): RuleCheck {
        val declared = context.declaration.mrpInclusiveOfTaxes
        return RuleCheck(
            ruleId = metadata.ruleId,
            ruleNumber = metadata.ruleNumber,
            title = metadata.title,
            field = "mrp",
            detectedValue = context.declaration.mrp,
            expectedRequirement = "The MRP must be qualified as inclusive of all taxes.",
            severity = metadata.severity,
            status = if (declared) ComplianceStatus.PASS else ComplianceStatus.WARNING,
            confidence = context.confidenceOf("mrp"),
            explanation = if (declared) {
                "The price is declared as inclusive of all taxes."
            } else {
                "The wording \"inclusive of all taxes\" was not found next to the price."
            },
            sourceReference = RuleSourceRegistry.getOfficialCitation(metadata.ruleNumber, metadata.source),
            recommendation = if (declared) null else "Print \"MRP inclusive of all taxes\" alongside the price."
        )
    }
}

class RuleManufacturingDate : DeclarationRule(
    ruleId = "LMPC-2026-R11-DATE",
    ruleNumber = "11",
    title = "Date of manufacture, packing or import",
    description = "The month and year in which the commodity was manufactured, packed or imported shall be declared.",
    severity = RuleSeverity.MAJOR,
    field = "manufacturingDate",
    requirement = "Month and year of manufacture, packing or import.",
    recommendation = "Print the month and year of manufacture or packing on the package."
) {
    override fun valueOf(context: RuleContext) =
        context.declaration.manufacturingDate ?: context.declaration.packingDate

    override fun evidenceFields() = listOf("manufacturingDate", "packingDate")
}

class RuleExpiryDeclaration : DeclarationRule(
    ruleId = "LMPC-2026-R11-EXPIRY",
    ruleNumber = "11",
    title = "Expiry, use-by or best-before declaration",
    description = "Perishable and consumable commodities shall carry a best-before, use-by or expiry declaration.",
    severity = RuleSeverity.MAJOR,
    field = "expiryDate",
    requirement = "An expiry date, use-by date or best-before shelf life.",
    recommendation = "Print the best-before or expiry declaration on the package."
) {
    override fun appliesTo(context: RuleContext) = isConsumable(context)

    override fun valueOf(context: RuleContext) = context.declaration.expiryDate
        ?: context.declaration.useBy
        ?: context.declaration.bestBefore

    override fun evidenceFields() = listOf("expiryDate", "useBy", "bestBefore")
}

class RuleIngredientsDeclaration : DeclarationRule(
    ruleId = "LMPC-2026-ING",
    ruleNumber = "6(1)(b)",
    title = "Ingredient or composition list",
    description = "Consumable commodities shall declare the ingredients or composition contained in the package.",
    severity = RuleSeverity.MAJOR,
    field = "ingredients",
    requirement = "A list of ingredients or the composition of the product.",
    recommendation = "Print the full ingredient or composition list on the package."
) {
    override fun appliesTo(context: RuleContext) = isConsumable(context)
    override fun valueOf(context: RuleContext) = context.declaration.ingredients
}

class RuleConsumerCareDetails : DeclarationRule(
    ruleId = "LMPC-2026-R10-CARE",
    ruleNumber = "10(1)(c)",
    title = "Consumer care contact details",
    description = "The package shall carry the contact details of the person who can be contacted with consumer complaints.",
    severity = RuleSeverity.MAJOR,
    field = "consumerCare",
    requirement = "A consumer care name with a contactable phone number or e-mail address.",
    recommendation = "Print a consumer care phone number or e-mail address on the package."
) {
    override fun valueOf(context: RuleContext): String? {
        val declaration = context.declaration
        return listOfNotNull(
            declaration.consumerCareName,
            declaration.consumerCarePhone,
            declaration.consumerCareEmail
        ).ifEmpty { null }?.joinToString(", ")
    }

    override fun evidenceFields() = listOf("consumerCareName", "consumerCarePhone", "consumerCareEmail")
}

class RuleCountryOfOrigin : DeclarationRule(
    ruleId = "LMPC-2026-ORIGIN",
    ruleNumber = "10",
    title = "Country of origin",
    description = "Imported packages shall declare the country of origin of the commodity.",
    severity = RuleSeverity.MAJOR,
    field = "countryOfOrigin",
    requirement = "The country in which the commodity was manufactured or produced.",
    recommendation = "Declare the country of origin on the package."
) {
    override fun valueOf(context: RuleContext) = context.declaration.countryOfOrigin
}

class RuleBatchIdentification : DeclarationRule(
    ruleId = "LMPC-2026-BATCH",
    ruleNumber = "10",
    title = "Batch or lot identification",
    description = "The package shall carry a batch, lot or code number allowing the consignment to be traced.",
    severity = RuleSeverity.MINOR,
    field = "batchNumber",
    requirement = "A batch, lot or code number.",
    recommendation = "Print a batch or lot number on the package for traceability."
) {
    override fun valueOf(context: RuleContext) =
        context.declaration.batchNumber ?: context.declaration.lotNumber

    override fun evidenceFields() = listOf("batchNumber", "lotNumber")
}

class RuleLicenceNumber : DeclarationRule(
    ruleId = "LMPC-2026-LICENCE",
    ruleNumber = "10",
    title = "Licence or registration number",
    description = "Food and nutraceutical packages shall carry the FSSAI licence number of the manufacturer or packer.",
    severity = RuleSeverity.MAJOR,
    field = "licenseNumber",
    requirement = "A 14 digit FSSAI licence number for food and supplement products.",
    recommendation = "Print the 14 digit FSSAI licence number on the package."
) {
    /**
     * A licence is required of a food business, so the rule follows the food evidence rather than
     * only a confidently named category: a package whose category could not be established but
     * which carries a nutrition table or an ingredient list is still a food package.
     */
    override fun appliesTo(context: RuleContext): Boolean {
        val declaration = context.declaration
        if (declaration.commodityCategory in setOf("FOOD", "SUPPLEMENT")) return true
        return declaration.commodityCategory == "UNKNOWN" &&
            (declaration.nutrition != null || !declaration.ingredients.isNullOrBlank())
    }

    override fun valueOf(context: RuleContext) = context.declaration.licenseNumber
}

class RuleSafetyWarnings : DeclarationRule(
    ruleId = "LMPC-2026-WARN",
    ruleNumber = "6(1)(f)",
    title = "Warnings and storage instructions",
    description = "Where relevant, the package shall carry warnings, allergen advice or storage instructions.",
    severity = RuleSeverity.MINOR,
    field = "warnings",
    requirement = "Any applicable warning, allergen advice or storage instruction.",
    recommendation = "Print applicable warnings, allergen advice or storage instructions on the package."
) {
    override fun appliesTo(context: RuleContext) = isConsumable(context)
    override fun valueOf(context: RuleContext) = context.declaration.warnings
}

/** The rule set applied to every inspection, in the order it is reported to the inspector. */
object LmpcRuleSet {
    fun all(): List<IRule> = listOf(
        Rule6CommodityNamePresence(),
        Rule10ManufacturerDetails(),
        Rule12NetQuantityUnit(),
        Rule18MrpPresence(),
        Rule18MrpTaxPhrase(),
        RuleManufacturingDate(),
        RuleExpiryDeclaration(),
        RuleIngredientsDeclaration(),
        RuleConsumerCareDetails(),
        RuleCountryOfOrigin(),
        RuleBatchIdentification(),
        RuleLicenceNumber(),
        RuleSafetyWarnings()
    )
}
