package com.example.compliance.core

import com.example.dtos.ProductDeclarationDto
import com.example.services.validation.ValidationIssue
import com.example.services.validation.ValidationReport
import kotlinx.serialization.Serializable

@Serializable
enum class ComplianceStatus {
    PASS, WARNING, FAIL, REVIEW_REQUIRED, NOT_APPLICABLE
}

@Serializable
enum class RuleSeverity {
    CRITICAL, MAJOR, MINOR, NONE;

    /** How much of the score this rule is worth. Severity is the only weighting input. */
    val weight: Int
        get() = when (this) {
            CRITICAL -> 3
            MAJOR -> 2
            MINOR -> 1
            NONE -> 0
        }
}

@Serializable
data class ComplianceRule(
    val ruleId: String,
    val ruleNumber: String,
    val title: String,
    val description: String,
    val source: String,
    val sourceVersion: String,
    val effectiveFrom: String,
    val effectiveTo: String? = null,
    val severity: RuleSeverity,
    val requiredEvidence: List<String> = emptyList()
)

/**
 * The outcome of one rule against one declaration.
 *
 * Unlike the previous model, a rule always produces a check - including when it passes - so the
 * app can show the inspector what was verified, not only what failed.
 */
@Serializable
data class RuleCheck(
    val ruleId: String,
    val ruleNumber: String,
    val title: String,
    val field: String,
    val detectedValue: String? = null,
    val expectedRequirement: String,
    val severity: RuleSeverity,
    val status: ComplianceStatus,
    val confidence: Float,
    val explanation: String,
    val sourceReference: String,
    val evidence: String? = null,
    val evidenceSource: String? = null,
    val recommendation: String? = null
)

/** Everything a rule is allowed to look at. Rules never touch OCR text directly. */
data class RuleContext(
    val declaration: ProductDeclarationDto,
    val validation: ValidationReport
) {
    fun confidenceOf(field: String): Float =
        declaration.fieldConfidence[field] ?: declaration.confidence

    fun issues(field: String): List<ValidationIssue> = validation.issuesFor(field)

    fun evidenceOf(fields: List<String>): String? {
        return fields.firstNotNullOfOrNull { declaration.fieldSources[it] } ?: declaration.otherDeclarations.firstOrNull()
    }

    fun sourceOf(fields: List<String>): String? {
        return fields.firstNotNullOfOrNull { declaration.fieldSources[it] }?.let { "BACK" } ?: "FRONT"
    }
}

@Serializable
data class ComplianceResult(
    val inspectionId: Int,
    val score: Int,
    val status: ComplianceStatus,
    /** Every rule that was applied, in the order the engine ran them. */
    val checks: List<RuleCheck> = emptyList(),
    /** The subset of [checks] that did not pass. Kept as its own list for the results screen. */
    val violations: List<RuleCheck> = emptyList(),
    val passedChecks: List<String> = emptyList(),
    val failedChecks: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val missingInformation: List<String> = emptyList(),
    val invalidInformation: List<String> = emptyList(),
    val recommendations: List<String> = emptyList(),
    val declaration: ProductDeclarationDto = ProductDeclarationDto(),
    val extractionConfidence: Float = 0f,
    val analyzedAt: String = "",
    val engineVersion: String = "2.1.0-SIH2026"
)
