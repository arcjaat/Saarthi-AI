package com.saarthi.ai.redaction

import org.junit.Assert.*
import org.junit.Test

class SensitiveDataRedactorTest {

    @Test
    fun testUpiIdRedaction() {
        val input = "Pay to abhay123@oksbi or rohit.kumar@paytm"
        val output = SensitiveDataRedactor.redact(input)
        assertEquals("Pay to [REDACTED_UPI] or [REDACTED_UPI]", output)
    }

    @Test
    fun testAadhaarRedaction() {
        val input = "My Aadhaar is 1234 5678 9012"
        val output = SensitiveDataRedactor.redact(input)
        assertEquals("My Aadhaar is [REDACTED_AADHAAR]", output)
    }

    @Test
    fun testPanRedaction() {
        val input = "Customer PAN: ABCDE1234F verified"
        val output = SensitiveDataRedactor.redact(input)
        assertEquals("Customer PAN: [REDACTED_PAN] verified", output)
    }

    @Test
    fun testCardNumberRedaction() {
        val input = "Card: 4111 2222 3333 4444"
        val output = SensitiveDataRedactor.redact(input)
        assertEquals("Card: [REDACTED_CARD]", output)
    }

    @Test
    fun testCurrencyAndBalanceRedaction() {
        val input = "Account balance: ₹45,230.50 available"
        val output = SensitiveDataRedactor.redact(input)
        assertTrue(output!!.contains("[REDACTED_BALANCE]") || output.contains("[REDACTED_AMOUNT]"))
        assertFalse(output.contains("45,230.50"))
    }

    @Test
    fun testPhoneNumberRedaction() {
        val input = "Call +91 9876543210 for support"
        val output = SensitiveDataRedactor.redact(input)
        assertEquals("Call [REDACTED_PHONE] for support", output)
    }

    @Test
    fun testGenericLongNumberRedaction() {
        val input = "Ref no: 9876543210123"
        val output = SensitiveDataRedactor.redact(input)
        assertEquals("Ref no: [REDACTED_NUMBER]", output)
    }

    @Test
    fun testCleanTextNotModified() {
        val input = "Check Account Balance"
        val output = SensitiveDataRedactor.redact(input)
        assertEquals(input, output)
    }
}
