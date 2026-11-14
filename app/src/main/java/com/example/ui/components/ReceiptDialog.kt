package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.ModePaiement
import com.example.data.ParametresMagasin
import com.example.data.VenteDetailComplet
import com.example.domain.Formatters
import com.example.domain.ReceiptPrinter
import com.example.ui.theme.*

@Composable
fun ReceiptDialog(
    detail: VenteDetailComplet,
    parametres: ParametresMagasin,
    onDismiss: () -> Unit,
    onPrintFeedback: (String, Boolean) -> Unit
) {
    val context = LocalContext.current
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    val v = detail.vente

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 520.dp)
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(20.dp),
            color = AppSurface,
            tonalElevation = 4.dp
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Justificatif de vente",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary
                        )
                        Text(
                            text = v.numero,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
                            color = TextMuted
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SaleStatusBadge(v.statut)
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("close_receipt_button")
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Fermer l'aperçu du ticket")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = AppOutlineSubtle)

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = parametres.nomCommercial.uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    if (parametres.adresse.isNotBlank()) {
                        Text(parametres.adresse, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                    if (parametres.telephone.isNotBlank()) {
                        Text("Tél : ${parametres.telephone}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }

                    Surface(
                        color = AppBackground,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, AppOutlineSubtle)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Date :", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                                Text(
                                    Formatters.formatDateTime(v.dateHeureUtc, parametres.fuseauHoraire),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextPrimary
                                )
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Vendeur :", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                                Text(v.vendeurNom, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Mode :", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                                Text(
                                    ModePaiement.fromString(v.modePaiement).libelle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextPrimary
                                )
                            }
                        }
                    }

                    Text("Articles", style = MaterialTheme.typography.titleSmall, color = TextPrimary)

                    detail.lignes.forEach { ligne ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = ligne.nomSnapshot,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "${ligne.referenceSnapshot} • ${ligne.quantite} × ${Formatters.formatMontant(ligne.prixUnitaire, parametres.devise)}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
                                    color = TextMuted
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = Formatters.formatMontant(ligne.sousTotal, parametres.devise),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = JetBrainsMono,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                textAlign = TextAlign.End,
                                color = TextPrimary
                            )
                        }
                    }

                    HorizontalDivider(color = AppOutlineSubtle)

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("TOTAL", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Text(
                            text = Formatters.formatMontant(v.total, parametres.devise),
                            style = MaterialTheme.typography.titleLarge.copy(fontFamily = JetBrainsMono),
                            color = ActionBlue
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Montant reçu", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        Text(
                            text = Formatters.formatMontant(v.montantRecu, parametres.devise),
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMono),
                            color = TextPrimary
                        )
                    }
                    if (v.modePaiement == ModePaiement.ESPECES.name) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Monnaie rendue", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                            Text(
                                text = Formatters.formatMontant(v.monnaieRendue, parametres.devise),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = JetBrainsMono,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = DisponibleText
                            )
                        }
                    }

                    if (detail.annulation != null) {
                        Surface(
                            color = RuptureBackground,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, RuptureBorder)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    "VENTE ANNULÉE",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = RuptureText
                                )
                                Text(
                                    "Par ${detail.annulation.auteurNom} le ${Formatters.formatDateTime(detail.annulation.dateHeureUtc, parametres.fuseauHoraire)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = RuptureText
                                )
                                Text(
                                    "Motif : ${detail.annulation.motif}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextPrimary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val text = ReceiptPrinter.buildPlainTextReceipt(detail, parametres)
                            clipboard.setText(AnnotatedString(text))
                            onPrintFeedback("Ticket copié dans le presse-papiers.", false)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("copy_receipt_button")
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copier")
                    }
                    Button(
                        onClick = {
                            val res = ReceiptPrinter.printReceipt(context, detail, parametres)
                            res.onFailure {
                                onPrintFeedback(
                                    "Impression indisponible (${it.message}). La vente reste enregistrée et réimprimable.",
                                    true
                                )
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("print_receipt_button")
                    ) {
                        Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Imprimer")
                    }
                }
            }
        }
    }
}
