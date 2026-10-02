package com.elyacine.pocketscan.receipt

import java.text.Normalizer
import java.util.Locale

/** What we could read from a receipt. Any field may be null when it could not be found. */
data class ParsedReceipt(
    val merchant: String?,
    val total: Double?,
    /** ISO date, yyyy-MM-dd. */
    val date: String?,
)

/**
 * Turns the raw OCR text of a receipt into merchant, total and date.
 *
 * Pure Kotlin with no Android dependencies, so it is unit-tested on the JVM.
 * Handles common English, French, German, Spanish and Italian receipt wording,
 * and both "1,234.56" and "1.234,56" number styles.
 */
object ReceiptParser {

    // An amount always has exactly two decimals. The integer part may use . , or ' as thousands separators.
    private val amountRegex = Regex("""(?<![\d.,'])(\d{1,3}(?:[.,']\d{3})+|\d+)[.,](\d{2})(?![\d])""")

    private val dayFirstDate = Regex("""(?<!\d)(\d{1,2})[/.\-](\d{1,2})[/.\-](\d{4}|\d{2})(?![/.\-]?\d)""")
    private val isoDate = Regex("""(?<!\d)(\d{4})[/.\-](\d{1,2})[/.\-](\d{1,2})(?![/.\-]?\d)""")

    /** Keywords that mark the final amount, strongest first. */
    private val strongTotal = Regex(
        """\b(GRAND TOTAL|TOTAL TTC|NET A PAYER|A PAYER|AMOUNT DUE|BALANCE DUE|TOTAL DUE|TO PAY|GESAMTSUMME|ZU ZAHLEN|TOTAL A PAGAR|TOTALE COMPLESSIVO)\b"""
    )
    private val plainTotal = Regex("""\b(TOTAL|TOTALE|SUMME|GESAMT|IMPORTE|MONTANT)\b""")

    /** Lines that look like a total but are not the amount actually paid. */
    private val notTheTotal = Regex(
        """\b(SUB\s?-?\s?TOTAL|SOUS\s?-?\s?TOTAL|ZWISCHENSUMME|SUBTOTALE|TOTAL HT|TOTAL TVA|TOTAL TAX|TVA|VAT|TAX|MWST|IVA|REMISE|DISCOUNT|RABATT|ECONOMIE|SAVINGS?)\b"""
    )

    /** Lines about money handed over or given back, never the total. */
    private val paymentLine = Regex(
        """\b(CASH|ESPECES|RENDU|CHANGE|MONNAIE|RUCKGELD|GEGEBEN|CAMBIO|RESTO|TENDERED|RECU|CARTE|CARD|CB|VISA|MASTERCARD)\b"""
    )

    private val notAMerchant = Regex(
        """\b(TICKET|RECEIPT|RECU|FACTURE|INVOICE|BIENVENUE|WELCOME|MERCI|THANK|TEL|PHONE|DATE|CAISSE|KASSE|SIRET|TVA|VAT)\b"""
    )

    fun parse(text: String): ParsedReceipt {
        val rawLines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val lines = rawLines.map(::normalize)
        return ParsedReceipt(
            merchant = findMerchant(rawLines, lines),
            total = findTotal(lines),
            date = findDate(lines),
        )
    }

    /** Uppercase and strip accents so "À PAYER" and "a payer" match the same keyword. */
    private fun normalize(line: String): String =
        Normalizer.normalize(line, Normalizer.Form.NFD)
            .replace(Regex("""\p{M}+"""), "")
            .uppercase()

    internal fun amountsIn(line: String): List<Double> =
        amountRegex.findAll(line).mapNotNull { match ->
            val whole = match.groupValues[1].replace(Regex("""[.,']"""), "")
            "$whole.${match.groupValues[2]}".toDoubleOrNull()
        }.toList()

    private fun findTotal(lines: List<String>): Double? {
        var best: Pair<Int, Double>? = null // (priority, amount)
        lines.forEachIndexed { i, line ->
            if (notTheTotal.containsMatchIn(line) || paymentLine.containsMatchIn(line)) return@forEachIndexed
            val priority = when {
                strongTotal.containsMatchIn(line) -> 2
                plainTotal.containsMatchIn(line) -> 1
                else -> return@forEachIndexed
            }
            // The amount is usually at the right of the keyword, sometimes on the next line.
            val amount = amountsIn(line).lastOrNull()
                ?: lines.getOrNull(i + 1)?.takeUnless { paymentLine.containsMatchIn(it) }
                    ?.let { amountsIn(it).lastOrNull() }
                ?: return@forEachIndexed
            val current = best
            if (current == null || priority > current.first ||
                (priority == current.first && amount > current.second)
            ) {
                best = priority to amount
            }
        }
        best?.let { return it.second }

        // No keyword found: the largest amount on the receipt is usually the total.
        return lines
            .filterNot { paymentLine.containsMatchIn(it) }
            .flatMap(::amountsIn)
            .maxOrNull()
    }

    private fun findDate(lines: List<String>): String? {
        // Prefer a line that says DATE, then any line.
        val ordered = lines.filter { "DATE" in it } + lines
        for (line in ordered) {
            isoDate.findAll(line).forEach { m ->
                val (y, mo, d) = m.destructured
                toIso(y.toInt(), mo.toInt(), d.toInt())?.let { return it }
            }
            dayFirstDate.findAll(line).forEach { m ->
                val (a, b, y) = m.destructured
                val year = if (y.length == 2) 2000 + y.toInt() else y.toInt()
                // Day first (Europe/Africa). If that is impossible, try month first (US).
                (toIso(year, b.toInt(), a.toInt()) ?: toIso(year, a.toInt(), b.toInt()))?.let { return it }
            }
        }
        return null
    }

    private fun toIso(year: Int, month: Int, day: Int): String? {
        if (year !in 2000..2099 || month !in 1..12 || day !in 1..31) return null
        return String.format(Locale.ROOT, "%04d-%02d-%02d", year, month, day)
    }

    private fun findMerchant(rawLines: List<String>, lines: List<String>): String? {
        for (i in 0 until minOf(6, lines.size)) {
            val line = lines[i]
            val letters = line.count { it.isLetter() }
            if (letters < 3 || letters < line.length / 2) continue
            if (notAMerchant.containsMatchIn(line)) continue
            if (amountsIn(line).isNotEmpty()) continue
            return rawLines[i].take(40)
        }
        return null
    }
}
