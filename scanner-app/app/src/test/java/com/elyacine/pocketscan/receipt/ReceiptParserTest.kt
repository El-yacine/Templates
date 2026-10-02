package com.elyacine.pocketscan.receipt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReceiptParserTest {

    @Test
    fun englishSupermarketReceipt() {
        val text = """
            FRESH MART
            123 Main Street
            Tel 555-0101
            Date: 03/15/2026 14:32
            Milk            2.49
            Bread           3.10
            SUBTOTAL        5.59
            TAX             0.45
            TOTAL           6.04
            CASH           10.00
            CHANGE          3.96
        """.trimIndent()

        val r = ReceiptParser.parse(text)
        assertEquals("FRESH MART", r.merchant)
        assertEquals(6.04, r.total!!, 0.001)
        assertEquals("2026-03-15", r.date)
    }

    @Test
    fun frenchReceiptWithCommaDecimalsAndAccents() {
        val text = """
            Carrefour Market
            SIRET 123 456 789
            12/09/2026
            BAGUETTE        1,20
            FROMAGE        12,45
            SOUS-TOTAL     13,65
            TOTAL HT       11,38
            TVA             2,27
            NET À PAYER    13,65
            CB             13,65
        """.trimIndent()

        val r = ReceiptParser.parse(text)
        assertEquals("Carrefour Market", r.merchant)
        assertEquals(13.65, r.total!!, 0.001)
        assertEquals("2026-09-12", r.date)
    }

    @Test
    fun thousandsSeparatorsInBothStyles() {
        assertEquals(listOf(1234.56), ReceiptParser.amountsIn("TOTAL 1.234,56 EUR"))
        assertEquals(listOf(1234.56), ReceiptParser.amountsIn("TOTAL $1,234.56"))
        assertEquals(listOf(12.5), ReceiptParser.amountsIn("12.50 MAD"))
    }

    @Test
    fun totalOnTheNextLine() {
        val text = """
            Pharmacie Centrale
            TOTAL
            48,90
        """.trimIndent()
        assertEquals(48.90, ReceiptParser.parse(text).total!!, 0.001)
    }

    @Test
    fun fallsBackToLargestAmountWhenNoKeyword() {
        val text = """
            Cafe Luna
            Espresso   2.20
            Croissant  1.80
            Sum        4.00
            Cash      20.00
        """.trimIndent()
        assertEquals(4.00, ReceiptParser.parse(text).total!!, 0.001)
    }

    @Test
    fun isoDateAndPhoneNumberIsNotADate() {
        val text = """
            BIG STORE
            Tel 06-12-34-56-78
            2026-10-01
            TOTAL 9.99
        """.trimIndent()
        assertEquals("2026-10-01", ReceiptParser.parse(text).date)
    }

    @Test
    fun emptyTextGivesNothing() {
        val r = ReceiptParser.parse("")
        assertNull(r.merchant)
        assertNull(r.total)
        assertNull(r.date)
    }
}
