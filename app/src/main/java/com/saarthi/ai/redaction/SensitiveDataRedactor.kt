package com.saarthi.ai.redaction

/**
 * Local Regex-based redaction engine for masking Personally Identifiable
 * Information (PII) and sensitive financial data BEFORE the UI tree
 * is fed to Gemini Nano.
 *
 * PRIVACY CONTRACT:
 * - Runs 100% locally in volatile memory. No data leaves the device.
 * - Applied to every text field and content description extracted from
 *   the AccessibilityNodeInfo tree.
 * - All patterns are compiled once at class load and reused (thread-safe).
 *
 * COVERAGE:
 * - Indian bank account numbers (9-18 digits)
 * - UPI IDs (user@bank)
 * - Aadhaar numbers (12 digits, often spaced as 4-4-4)
 * - PAN card numbers (ABCDE1234F)
 * - Credit/Debit card numbers (13-19 digits, grouped by 4)
 * - IFSC codes (4-letter + 7-digit)
 * - Indian phone numbers (10 digits, +91 prefix variants)
 * - Currency amounts (₹ / Rs / INR prefixed numbers)
 * - Generic long number sequences (6+ consecutive digits)
 * - Email addresses
 */
object SensitiveDataRedactor {

    // ── Compiled Regex Patterns ──────────────────────────────────────────

    /** UPI ID: user@bankname (e.g., "abhay123@oksbi", "user@paytm") */
    private val UPI_ID_PATTERN = Regex(
        """[a-zA-Z0-9._\-]+@[a-zA-Z]{2,}""",
        RegexOption.IGNORE_CASE
    )

    /** Aadhaar: 12 digits, separated by spaces/hyphens or 12 continuous digits */
    private val AADHAAR_PATTERN = Regex(
        """\b\d{4}[\s\-]\d{4}[\s\-]\d{4}\b|\b\d{12}\b"""
    )

    /** PAN Card: 5 letters + 4 digits + 1 letter (e.g., "ABCDE1234F") */
    private val PAN_PATTERN = Regex(
        """\b[A-Z]{5}\d{4}[A-Z]\b""",
        RegexOption.IGNORE_CASE
    )

    /** Credit/Debit card: 15-16 digits, with spaces/hyphens or continuous */
    private val CARD_NUMBER_PATTERN = Regex(
        """\b(?:\d{4}[\s\-]){3}\d{4}\b|\b\d{15,16}\b"""
    )

    /** IFSC Code: 4 uppercase letters + 0 + 6 digits (e.g., "SBIN0001234") */
    private val IFSC_PATTERN = Regex(
        """\b[A-Z]{4}0\d{6}\b""",
        RegexOption.IGNORE_CASE
    )

    /** Indian phone: 10-digit mobile starting with 6-9, with optional +91/0 prefix */
    private val PHONE_PATTERN = Regex(
        """(?:\+91[\s\-]?|\b0)?\b[6-9]\d{9}\b"""
    )

    /** Currency amounts: ₹ / Rs. / Rs / INR followed by numbers (e.g., "₹1,23,456.78", "Rs 5000") */
    private val CURRENCY_AMOUNT_PATTERN = Regex(
        """(?:₹|Rs\.?|INR)\s*[\d,]+(?:\.\d{1,2})?""",
        RegexOption.IGNORE_CASE
    )

    /** Standalone large numbers: 6 or more consecutive digits (likely account/reference numbers) */
    private val LONG_NUMBER_PATTERN = Regex(
        """\b\d{6,}\b"""
    )

    /** Balance-like patterns: number preceded/followed by "balance", "amt", "amount", etc. */
    private val BALANCE_CONTEXT_PATTERN = Regex(
        """(?:balance|amt|amount|total|avl\.?\s*bal|available)\s*[:=]?\s*₹?\s*[\d,]+(?:\.\d{1,2})?""",
        RegexOption.IGNORE_CASE
    )

    /** Email addresses */
    private val EMAIL_PATTERN = Regex(
        """[a-zA-Z0-9._%+\-]+@[a-zA-Z0-9.\-]+\.[a-zA-Z]{2,}"""
    )

    /** Numbers in XX-masked display format from apps (e.g., "XX1234", "****5678") */
    private val MASKED_NUMBER_PATTERN = Regex(
        """(?:[X*]{2,}\d{2,}|\d{2,}[X*]{2,})""",
        RegexOption.IGNORE_CASE
    )

    // ── Ordered pattern-replacement pairs ────────────────────────────────
    // Order matters: more specific patterns (UPI, Aadhaar, PAN) come first
    // before the generic long-number catch-all.

    private data class RedactionRule(
        val pattern: Regex,
        val replacement: String
    )

    private val RULES: List<RedactionRule> = listOf(
        RedactionRule(BALANCE_CONTEXT_PATTERN, "[REDACTED_BALANCE]"),
        RedactionRule(EMAIL_PATTERN, "[REDACTED_EMAIL]"),
        RedactionRule(UPI_ID_PATTERN, "[REDACTED_UPI]"),
        RedactionRule(CARD_NUMBER_PATTERN, "[REDACTED_CARD]"),
        RedactionRule(AADHAAR_PATTERN, "[REDACTED_AADHAAR]"),
        RedactionRule(PAN_PATTERN, "[REDACTED_PAN]"),
        RedactionRule(IFSC_PATTERN, "[REDACTED_IFSC]"),
        RedactionRule(CURRENCY_AMOUNT_PATTERN, "[REDACTED_AMOUNT]"),
        RedactionRule(PHONE_PATTERN, "[REDACTED_PHONE]"),
        RedactionRule(MASKED_NUMBER_PATTERN, "[REDACTED_NUMBER]"),
        RedactionRule(LONG_NUMBER_PATTERN, "[REDACTED_NUMBER]")
    )

    // ── Public API ───────────────────────────────────────────────────────

    /**
     * Redacts all sensitive PII and financial data from the input string.
     *
     * @param input Raw text extracted from an AccessibilityNodeInfo field.
     * @return Sanitized string safe to feed to Gemini Nano.
     *         Returns null if input is null.
     *
     * Example:
     *   Input:  "Savings A/C 91234567890 Balance ₹4,52,300.50"
     *   Output: "Savings A/C [REDACTED_NUMBER] [REDACTED_BALANCE]"
     */
    fun redact(input: String?): String? {
        if (input.isNullOrBlank()) return input

        var sanitized: String = input
        for (rule in RULES) {
            sanitized = rule.pattern.replace(sanitized, rule.replacement)
        }

        return sanitized
    }

    /**
     * Checks if a given string contains any potentially sensitive data.
     * Useful for logging decisions without exposing actual data.
     */
    fun containsSensitiveData(input: String?): Boolean {
        if (input.isNullOrBlank()) return false
        return RULES.any { it.pattern.containsMatchIn(input) }
    }

    /**
     * Returns a redaction summary for debugging/audit purposes.
     * Reports how many matches of each category were found, WITHOUT
     * revealing the actual matched content.
     *
     * Example output: "BALANCE:1, PHONE:2, LONG_NUMBER:3"
     */
    fun auditSummary(input: String?): String {
        if (input.isNullOrBlank()) return "CLEAN"

        val labels = listOf(
            "BALANCE", "EMAIL", "UPI", "AADHAAR", "PAN",
            "CARD", "IFSC", "CURRENCY", "PHONE", "MASKED_NUM", "LONG_NUM"
        )

        val counts = RULES.mapIndexed { index, rule ->
            val count = rule.pattern.findAll(input).count()
            if (count > 0) "${labels[index]}:$count" else null
        }.filterNotNull()

        return if (counts.isEmpty()) "CLEAN" else counts.joinToString(", ")
    }
}
