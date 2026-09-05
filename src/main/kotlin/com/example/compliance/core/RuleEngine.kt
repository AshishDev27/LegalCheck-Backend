package com.example.compliance.core

import com.example.dtos.ProductDeclarationDto
import com.example.services.validation.DeclarationValidator
import com.example.services.validation.IssueType
import com.example.services.validation.ValidationReport
import java.time.LocalDateTime

interface IRule {
    val metadata: ComplianceRule

    /** Whether this rule applies to the product at all (ingredients do not apply to a hand tool). */
    fun appliesTo(context: RuleContext): Boolean = true

    fun evaluate(context: RuleContext): RuleCheck
}

/**
 * The single compliance engine for the service.
 *
 * The score is derived entirely from the rules that actually ran against the extracted data:
 * each applicable rule contributes its severity weight, a pass earns all of it, a warning or a
 * review earns half, a failure earns none. There is no fixed baseline and no hardcoded score.
 */
class ComplianceRuleEngine(private val rules: List<IRule>) {

    companion object {
        /** Below this, the extraction itself is too weak to make a compliance claim from. */
        const val REVIEW_CONFIDENCE_THRESHOLD = 0.55f
    }

    fun analyze(
        inspectionId: Int,
        declaration: ProductDeclarationDto,
        validation: ValidationReport = DeclarationValidator.validate(declaration)
    ): ComplianceResult {
        val context = RuleContext(declaration, validation)
        val applicable = rules.filter { it.appliesTo(context) }
        val checks = applicable.map { it.evaluate(context) }

        val score = calculateScore(checks)
        val status = determineStatus(checks, score, declaration.confidence)

        val missing = validation.issues
            .filter { it.type == IssueType.MISSING }
            .map { it.field + ": " + it.message }
            .distinct()

        val invalid = validation.issues
            .filter { it.type in setOf(IssueType.INVALID_FORMAT, IssueType.SUSPICIOUS, IssueType.UNREADABLE, IssueType.CONFLICT) }
            .map { it.field + ": " + it.message }
            .distinct()

        return ComplianceResult(
            inspectionId = inspectionId,
            score = score,
            status = status,
            checks = checks,
            violations = checks.filter { it.status != ComplianceStatus.PASS },
            passedChecks = checks.filter { it.status == ComplianceStatus.PASS }.map { it.title },
            failedChecks = checks.filter { it.status == ComplianceStatus.FAIL }.map { it.title + " - " + it.explanation },
            warnings = checks
                .filter { it.status == ComplianceStatus.WARNING || it.status == ComplianceStatus.REVIEW_REQUIRED }
                .map { it.title + " - " + it.explanation },
            missingInformation = missing,
            invalidInformation = invalid,
            recommendations = checks.mapNotNull { it.recommendation }.distinct(),
            declaration = declaration,
            extractionConfidence = declaration.confidence,
            analyzedAt = LocalDateTime.now().toString()
        )
    }

    private fun calculateScore(checks: List<RuleCheck>): Int {
        val scored = checks.filter { it.status != ComplianceStatus.NOT_APPLICABLE }
        val totalWeight = scored.sumOf { it.severity.weight }
        if (totalWeight == 0) return 0

        val earned = scored.sumOf { check ->
            when (check.status) {
                ComplianceStatus.PASS -> check.severity.weight.toDouble()
                ComplianceStatus.WARNING, ComplianceStatus.REVIEW_REQUIRED -> check.severity.weight / 2.0
                else -> 0.0
            }
        }
        return Math.round(earned / totalWeight * 100).toInt().coerceIn(0, 100)
    }

    private fun determineStatus(checks: List<RuleCheck>, score: Int, extractionConfidence: Float): ComplianceStatus {
        // A weak extraction cannot support a compliance verdict either way.
        if (extractionConfidence < REVIEW_CONFIDENCE_THRESHOLD) return ComplianceStatus.REVIEW_REQUIRED

        val criticalFailure = checks.any {
            it.status == ComplianceStatus.FAIL && it.severity == RuleSeverity.CRITICAL
        }

        return when {
            criticalFailure -> ComplianceStatus.FAIL
            checks.any { it.status == ComplianceStatus.FAIL } && score < 70 -> ComplianceStatus.FAIL
            checks.any { it.status == ComplianceStatus.REVIEW_REQUIRED } -> ComplianceStatus.REVIEW_REQUIRED
            checks.any { it.status != ComplianceStatus.PASS && it.status != ComplianceStatus.NOT_APPLICABLE } -> ComplianceStatus.WARNING
            else -> ComplianceStatus.PASS
        }
    }
}
