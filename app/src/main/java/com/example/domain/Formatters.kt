package com.example.domain

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Formatters {
    private val frenchSymbols = DecimalFormatSymbols(Locale.FRANCE).apply {
        groupingSeparator = ' '
    }
    private val integerFormatter = DecimalFormat("#,##0", frenchSymbols)

    private val dateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.FRANCE)
    private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRANCE)
    private val isoDateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun formatMontant(montant: Long, devise: String = "XAF"): String {
        val suffix = if (devise == "XAF") "FCFA" else devise
        return "${integerFormatter.format(montant)} $suffix"
    }

    fun formatEntier(valeur: Int): String = integerFormatter.format(valeur)

    fun formatDateTime(utcMillis: Long, zoneIdStr: String = "Africa/Brazzaville"): String {
        val zone = safeZoneId(zoneIdStr)
        return Instant.ofEpochMilli(utcMillis).atZone(zone).format(dateTimeFormatter)
    }

    fun formatDateMetierReadable(dateMetier: String): String {
        return try {
            LocalDate.parse(dateMetier, isoDateFormatter).format(dateFormatter)
        } catch (_: Exception) {
            dateMetier
        }
    }

    fun todayDateMetier(zoneIdStr: String = "Africa/Brazzaville", nowMillis: Long = System.currentTimeMillis()): String {
        val zone = safeZoneId(zoneIdStr)
        return Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate().format(isoDateFormatter)
    }

    fun safeZoneId(zoneIdStr: String): ZoneId {
        return try {
            ZoneId.of(zoneIdStr)
        } catch (_: Exception) {
            ZoneId.systemDefault()
        }
    }

    /**
     * Protège les cellules texte CSV contre l'injection de formules (=, +, -, @, tabulation, CR)
     * et échappe les guillemets / séparateurs selon RFC 4180 (séparateur ';').
     */
    fun escapeCsvTextCell(raw: String): String {
        val normalized = raw.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ')
        val sanitized = if (normalized.isNotEmpty() && normalized[0] in charArrayOf('=', '+', '-', '@', '\t', '\r')) {
            "'$normalized"
        } else {
            normalized
        }
        val escaped = sanitized.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    fun escapeCsvNumericCell(value: Number): String = value.toString()

    fun buildCsvWithBom(header: List<String>, rows: List<List<String>>): String {
        val sb = StringBuilder()
        sb.append('\uFEFF') // UTF-8 BOM pour ouverture propre des accents
        sb.append(header.joinToString(";") { escapeCsvTextCell(it) }).append("\r\n")
        for (row in rows) {
            sb.append(row.joinToString(";")).append("\r\n")
        }
        return sb.toString()
    }
}
