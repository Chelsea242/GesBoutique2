package com.example.domain

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.print.pdf.PrintedPdfDocument
import com.example.data.ModePaiement
import com.example.data.ParametresMagasin
import com.example.data.StatutVente
import com.example.data.VenteDetailComplet
import java.io.FileOutputStream

object ReceiptPrinter {

    fun buildPlainTextReceipt(
        detail: VenteDetailComplet,
        params: ParametresMagasin
    ): String {
        val v = detail.vente
        val sb = StringBuilder()
        sb.appendLine("========================================")
        sb.appendLine(params.nomCommercial.uppercase())
        if (params.adresse.isNotBlank()) sb.appendLine(params.adresse)
        if (params.telephone.isNotBlank()) sb.appendLine("Tél : ${params.telephone}")
        sb.appendLine("----------------------------------------")
        sb.appendLine("Ticket   : ${v.numero}")
        sb.appendLine("Date     : ${Formatters.formatDateTime(v.dateHeureUtc, params.fuseauHoraire)}")
        sb.appendLine("Vendeur  : ${v.vendeurNom}")
        sb.appendLine("Statut   : ${StatutVente.fromString(v.statut).libelle}")
        sb.appendLine("----------------------------------------")
        for (l in detail.lignes) {
            sb.appendLine("${l.nomSnapshot} (${l.referenceSnapshot})")
            sb.appendLine("  ${l.quantite} x ${Formatters.formatMontant(l.prixUnitaire, params.devise)} = ${Formatters.formatMontant(l.sousTotal, params.devise)}")
        }
        sb.appendLine("----------------------------------------")
        sb.appendLine("TOTAL         : ${Formatters.formatMontant(v.total, params.devise)}")
        sb.appendLine("Paiement      : ${ModePaiement.fromString(v.modePaiement).libelle}")
        sb.appendLine("Montant reçu  : ${Formatters.formatMontant(v.montantRecu, params.devise)}")
        if (v.modePaiement == ModePaiement.ESPECES.name) {
            sb.appendLine("Monnaie rendue: ${Formatters.formatMontant(v.monnaieRendue, params.devise)}")
        }
        if (detail.annulation != null) {
            sb.appendLine("----------------------------------------")
            sb.appendLine("VENTES ANNULÉE LE ${Formatters.formatDateTime(detail.annulation.dateHeureUtc, params.fuseauHoraire)}")
            sb.appendLine("Par : ${detail.annulation.auteurNom}")
            sb.appendLine("Motif : ${detail.annulation.motif}")
        }
        sb.appendLine("========================================")
        sb.appendLine("Merci pour votre visite !")
        return sb.toString()
    }

    /**
     * Lance l'impression Android sans jamais modifier ni dupliquer la vente en base.
     */
    fun printReceipt(
        context: Context,
        detail: VenteDetailComplet,
        params: ParametresMagasin
    ): Result<Unit> {
        return try {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                ?: return Result.failure(IllegalStateException("Service d'impression indisponible sur cet appareil."))
            val jobName = "Ticket_${detail.vente.numero}"
            val adapter = ReceiptPrintAdapter(context, detail, params)
            printManager.print(jobName, adapter, PrintAttributes.Builder().build())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private class ReceiptPrintAdapter(
        private val context: Context,
        private val detail: VenteDetailComplet,
        private val params: ParametresMagasin
    ) : PrintDocumentAdapter() {

        private var pdfDocument: PrintedPdfDocument? = null

        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes,
            cancellationSignal: CancellationSignal?,
            callback: LayoutResultCallback,
            extras: Bundle?
        ) {
            pdfDocument = PrintedPdfDocument(context, newAttributes)
            if (cancellationSignal?.isCanceled == true) {
                callback.onLayoutCancelled()
                return
            }
            val info = PrintDocumentInfo.Builder("ticket_${detail.vente.numero}.pdf")
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(1)
                .build()
            callback.onLayoutFinished(info, true)
        }

        override fun onWrite(
            pages: Array<out PageRange>?,
            destination: ParcelFileDescriptor,
            cancellationSignal: CancellationSignal?,
            callback: WriteResultCallback
        ) {
            val doc = pdfDocument ?: return
            try {
                val page = doc.startPage(0)
                if (cancellationSignal?.isCanceled == true) {
                    callback.onWriteCancelled()
                    doc.close()
                    pdfDocument = null
                    return
                }
                drawReceiptPage(page)
                doc.finishPage(page)
                FileOutputStream(destination.fileDescriptor).use { out ->
                    doc.writeTo(out)
                }
                callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (e: Exception) {
                callback.onWriteFailed(e.message)
            } finally {
                doc.close()
                pdfDocument = null
            }
        }

        private fun drawReceiptPage(page: PdfDocument.Page) {
            val canvas = page.canvas
            val left = 36f
            val right = page.info.pageWidth - 36f
            var y = 48f

            val titlePaint = Paint().apply {
                color = Color.BLACK
                textSize = 16f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val bodyPaint = Paint().apply {
                color = Color.DKGRAY
                textSize = 11f
                typeface = Typeface.DEFAULT
            }
            val boldPaint = Paint().apply {
                color = Color.BLACK
                textSize = 11.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val rightAlignPaint = Paint(boldPaint).apply {
                textAlign = Paint.Align.RIGHT
            }
            val linePaint = Paint().apply {
                color = Color.LTGRAY
                strokeWidth = 1f
            }

            canvas.drawText(params.nomCommercial.uppercase(), left, y, titlePaint)
            y += 18f
            if (params.adresse.isNotBlank()) {
                canvas.drawText(params.adresse, left, y, bodyPaint)
                y += 14f
            }
            if (params.telephone.isNotBlank()) {
                canvas.drawText("Tél : ${params.telephone}", left, y, bodyPaint)
                y += 14f
            }
            y += 6f
            canvas.drawLine(left, y, right, y, linePaint)
            y += 18f

            val v = detail.vente
            canvas.drawText("Ticket : ${v.numero}", left, y, boldPaint)
            y += 15f
            canvas.drawText("Date : ${Formatters.formatDateTime(v.dateHeureUtc, params.fuseauHoraire)}", left, y, bodyPaint)
            y += 15f
            canvas.drawText("Vendeur : ${v.vendeurNom}", left, y, bodyPaint)
            y += 15f
            canvas.drawText("Statut : ${StatutVente.fromString(v.statut).libelle}", left, y, boldPaint)
            y += 10f
            canvas.drawLine(left, y, right, y, linePaint)
            y += 18f

            for (l in detail.lignes) {
                if (y > page.info.pageHeight - 90f) break
                canvas.drawText("${l.nomSnapshot} (${l.referenceSnapshot})", left, y, boldPaint)
                y += 14f
                canvas.drawText(
                    "${l.quantite} x ${Formatters.formatMontant(l.prixUnitaire, params.devise)}",
                    left + 8f,
                    y,
                    bodyPaint
                )
                canvas.drawText(
                    Formatters.formatMontant(l.sousTotal, params.devise),
                    right,
                    y,
                    rightAlignPaint
                )
                y += 16f
            }

            canvas.drawLine(left, y, right, y, linePaint)
            y += 20f
            canvas.drawText("TOTAL", left, y, titlePaint)
            canvas.drawText(Formatters.formatMontant(v.total, params.devise), right, y, Paint(titlePaint).apply {
                textAlign = Paint.Align.RIGHT
            })
            y += 18f
            canvas.drawText("Mode : ${ModePaiement.fromString(v.modePaiement).libelle}", left, y, bodyPaint)
            canvas.drawText("Reçu : ${Formatters.formatMontant(v.montantRecu, params.devise)}", right, y, rightAlignPaint)
            y += 15f
            if (v.modePaiement == ModePaiement.ESPECES.name) {
                canvas.drawText("Monnaie rendue", left, y, bodyPaint)
                canvas.drawText(Formatters.formatMontant(v.monnaieRendue, params.devise), right, y, rightAlignPaint)
                y += 15f
            }

            if (detail.annulation != null) {
                y += 8f
                val cancelPaint = Paint(boldPaint).apply { color = Color.RED }
                canvas.drawText("VENTE ANNULÉE — ${detail.annulation.motif}", left, y, cancelPaint)
                y += 16f
            }

            y += 16f
            canvas.drawText("Merci pour votre confiance !", left, y, bodyPaint)
        }
    }
}
