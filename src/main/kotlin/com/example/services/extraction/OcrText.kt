package com.example.services.extraction

/** One scanned panel of the package, with the raw OCR text exactly as the device produced it. */
data class OcrSide(
    val side: String,          // FRONT, BACK, ADDITIONAL
    val rawText: String
)

/**
 * Text of one panel prepared for label-based extraction.
 *
 * `lines` keeps the original casing (values are echoed back to the inspector verbatim) while
 * `lower` is the lower-cased mirror used for label matching.
 */
class NormalizedText(val side: String, rawText: String) {
    val raw: String = rawText

    val lines: List<String> = rawText
        .lines()
        .map { it.replace(' ', ' ').replace(Regex("\\s+"), " ").trim() }
        .filter { it.isNotEmpty() }

    val lower: List<String> = lines.map { it.lowercase() }
    val joined: String = lines.joinToString("\n")
    val joinedLower: String = joined.lowercase()

    val alphaNumericCount: Int = raw.count { it.isLetterOrDigit() }

    /**
     * A rough but honest quality signal for the OCR pass. The on-device recogniser exposes no
     * per-character confidence, so quality is derived from how much usable text came back and
     * how much of it is fragmentary noise. A blurry or badly lit panel yields little of either.
     */
    val quality: Float = run {
        if (lines.isEmpty() || alphaNumericCount == 0) {
            0f
        } else {
            val volume = (alphaNumericCount / 250f).coerceAtMost(1f)
            val junkRatio = lines.count { it.length < 3 }.toFloat() / lines.size
            (volume * (1f - junkRatio * 0.5f)).coerceIn(0f, 1f)
        }
    }
}

object TextNormalizer {
    private val digitLookalikes = mapOf(
        'O' to '0', 'o' to '0', 'D' to '0', 'Q' to '0',
        'l' to '1', 'I' to '1', '|' to '1',
        'S' to '5', 's' to '5',
        'B' to '8', 'Z' to '2', 'z' to '2', 'g' to '9'
    )

    /**
     * Repairs the usual OCR letter/digit confusions.
     *
     * The repair is applied token by token and only to a token that is already predominantly
     * numeric, so "9O9" becomes "909" while the unit in "250 g" is left alone - repairing across
     * a whole line would turn that unit into a digit and destroy the quantity.
     */
    fun repairDigits(candidate: String): String =
        candidate.split(" ").joinToString(" ") { token ->
            val digits = token.count { it.isDigit() }
            val letters = token.count { it.isLetter() }
            if (digits >= 2 && digits > letters) {
                token.map { digitLookalikes[it] ?: it }.joinToString("")
            } else {
                token
            }
        }

    /** Strips a matched label and any separator punctuation that follows it. */
    fun valueAfterLabel(line: String, labelMatch: MatchResult): String =
        line.substring(labelMatch.range.last + 1)
            .trimStart(' ', ':', '-', '–', '—', '.', '>', '=', ')', ']', '*', '#')
            .trim()
}
