package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.*
import com.example.domain.Formatters
import com.example.ui.CartItem
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.PillTabItem
import com.example.ui.components.ReceiptDialog
import com.example.ui.components.SectionHeader
import com.example.ui.components.SegmentedPillTabs
import com.example.ui.components.StockStatusBadge
import com.example.ui.theme.*

@Composable
fun NewSaleScreen(
    produits: List<ProduitAvecStock>,
    categories: List<CategorieEntity>,
    parametres: ParametresMagasin,
    cartItems: List<CartItem>,
    paymentMode: ModePaiement,
    cashReceivedText: String,
    isValidating: Boolean,
    saleError: String?,
    priceChangePrompt: ValidationVenteResult.PrixModifie?,
    completedReceipt: VenteDetailComplet?,
    onAddToCart: (ProduitAvecStock) -> Unit,
    onUpdateQuantity: (Long, Int) -> Unit,
    onRemoveFromCart: (Long) -> Unit,
    onClearCart: () -> Unit,
    onSetPaymentMode: (ModePaiement) -> Unit,
    onSetCashText: (String) -> Unit,
    onValidateSale: () -> Unit,
    onConfirmPriceChange: () -> Unit,
    onDismissPriceChange: () -> Unit,
    onDismissReceipt: () -> Unit,
    onFeedback: (String, Boolean) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf<Long?>(null) }
    var mobileTabIndex by remember { mutableIntStateOf(0) } // 0 = Produits, 1 = Panier

    val activeProducts = remember(produits, searchQuery, selectedCategoryId) {
        produits.filter { p ->
            p.actif &&
                (selectedCategoryId == null || p.categorieId == selectedCategoryId) &&
                (searchQuery.isBlank() ||
                    p.nom.contains(searchQuery, ignoreCase = true) ||
                    p.reference.contains(searchQuery, ignoreCase = true))
        }
    }

    val total = remember(cartItems) { cartItems.sumOf { it.sousTotal } }
    val totalUnits = remember(cartItems) { cartItems.sumOf { it.quantite } }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWide = maxWidth >= 780.dp

        if (isWide) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(modifier = Modifier.weight(1.25f).fillMaxHeight()) {
                    ProductCatalogPane(
                        searchQuery = searchQuery,
                        onSearchChange = { searchQuery = it },
                        categories = categories,
                        selectedCategoryId = selectedCategoryId,
                        onSelectCategory = { selectedCategoryId = it },
                        products = activeProducts,
                        cartItems = cartItems,
                        devise = parametres.devise,
                        onAddToCart = onAddToCart
                    )
                }
                Surface(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    color = AppSurface,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, AppOutlineSubtle)
                ) {
                    CartAndPaymentPane(
                        cartItems = cartItems,
                        total = total,
                        devise = parametres.devise,
                        paymentMode = paymentMode,
                        cashReceivedText = cashReceivedText,
                        isValidating = isValidating,
                        saleError = saleError,
                        onUpdateQuantity = onUpdateQuantity,
                        onRemoveFromCart = onRemoveFromCart,
                        onClearCart = onClearCart,
                        onSetPaymentMode = onSetPaymentMode,
                        onSetCashText = onSetCashText,
                        onValidateSale = onValidateSale
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SectionHeader(
                    title = "Nouvelle vente",
                    subtitle = "$totalUnits article(s) • Total : ${Formatters.formatMontant(total, parametres.devise)}"
                )

                SegmentedPillTabs(
                    items = listOf(
                        PillTabItem(
                            label = "Produits",
                            icon = Icons.Filled.Inventory2,
                            badge = "${activeProducts.size}",
                            testTag = "sale_tab_catalog"
                        ),
                        PillTabItem(
                            label = "Panier",
                            icon = Icons.Filled.ShoppingCart,
                            badge = "$totalUnits",
                            testTag = "sale_tab_cart"
                        )
                    ),
                    selectedIndex = mobileTabIndex,
                    onSelect = { mobileTabIndex = it }
                )

                if (mobileTabIndex == 0) {
                    Box(modifier = Modifier.weight(1f)) {
                        ProductCatalogPane(
                            searchQuery = searchQuery,
                            onSearchChange = { searchQuery = it },
                            categories = categories,
                            selectedCategoryId = selectedCategoryId,
                            onSelectCategory = { selectedCategoryId = it },
                            products = activeProducts,
                            cartItems = cartItems,
                            devise = parametres.devise,
                            onAddToCart = onAddToCart
                        )
                    }
                    if (cartItems.isNotEmpty()) {
                        Button(
                            onClick = { mobileTabIndex = 1 },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("go_to_cart_button")
                        ) {
                            Icon(Icons.Filled.ShoppingCartCheckout, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Voir le panier • ${Formatters.formatMontant(total, parametres.devise)}")
                        }
                    }
                } else {
                    Surface(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        color = AppSurface,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, AppOutlineSubtle)
                    ) {
                        CartAndPaymentPane(
                            cartItems = cartItems,
                            total = total,
                            devise = parametres.devise,
                            paymentMode = paymentMode,
                            cashReceivedText = cashReceivedText,
                            isValidating = isValidating,
                            saleError = saleError,
                            onUpdateQuantity = onUpdateQuantity,
                            onRemoveFromCart = onRemoveFromCart,
                            onClearCart = onClearCart,
                            onSetPaymentMode = onSetPaymentMode,
                            onSetCashText = onSetCashText,
                            onValidateSale = onValidateSale
                        )
                    }
                }
            }
        }
    }

    if (priceChangePrompt != null) {
        AlertDialog(
            onDismissRequest = onDismissPriceChange,
            title = { Text("Prix catalogue mis à jour") },
            text = { Text(priceChangePrompt.message) },
            confirmButton = {
                Button(
                    onClick = onConfirmPriceChange,
                    modifier = Modifier.testTag("confirm_price_change_button")
                ) {
                    Text("Valider au nouveau total")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissPriceChange) {
                    Text("Annuler")
                }
            }
        )
    }

    if (completedReceipt != null) {
        ReceiptDialog(
            detail = completedReceipt,
            parametres = parametres,
            onDismiss = onDismissReceipt,
            onPrintFeedback = onFeedback
        )
    }
}

@Composable
private fun ProductCatalogPane(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    categories: List<CategorieEntity>,
    selectedCategoryId: Long?,
    onSelectCategory: (Long?) -> Unit,
    products: List<ProduitAvecStock>,
    cartItems: List<CartItem>,
    devise: String,
    onAddToCart: (ProduitAvecStock) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Rechercher par nom ou référence...") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Effacer la recherche")
                    }
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("sale_search_input"),
            shape = RoundedCornerShape(12.dp)
        )

        if (categories.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = selectedCategoryId == null,
                        onClick = { onSelectCategory(null) },
                        label = { Text("Toutes") }
                    )
                }
                items(categories, key = { it.id }) { cat ->
                    FilterChip(
                        selected = selectedCategoryId == cat.id,
                        onClick = { onSelectCategory(if (selectedCategoryId == cat.id) null else cat.id) },
                        label = { Text(cat.nom) }
                    )
                }
            }
        }

        if (products.isEmpty()) {
            EmptyStateCard(
                icon = Icons.Filled.SearchOff,
                title = "Aucun produit trouvé",
                description = "Modifiez votre recherche ou vérifiez les produits actifs dans le catalogue."
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(products, key = { it.id }) { product ->
                    val isRupture = product.stockDisponible <= 0
                    val inCartQty = cartItems.firstOrNull { it.produitId == product.id }?.quantite ?: 0

                    Surface(
                        color = if (isRupture) RuptureBackground else AppSurface,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, if (isRupture) RuptureBorder else AppOutlineSubtle)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = product.nom,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (inCartQty > 0) {
                                        Surface(color = ActionBlueSoft, shape = RoundedCornerShape(6.dp)) {
                                            Text(
                                                "Panier : $inCartQty",
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = ActionBlueDark
                                            )
                                        }
                                    }
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = product.reference,
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
                                        color = TextMuted
                                    )
                                    Text(
                                        text = "Stock : ${product.stockDisponible}",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = JetBrainsMono,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = if (isRupture) RuptureText else TextSecondary
                                    )
                                }
                                StockStatusBadge(product.statut)
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = Formatters.formatMontant(product.prixVente, devise),
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontFamily = JetBrainsMono,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = TextPrimary
                                )
                                Button(
                                    onClick = { onAddToCart(product) },
                                    enabled = !isRupture,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("add_to_cart_${product.reference}")
                                ) {
                                    Icon(Icons.Filled.AddShoppingCart, contentDescription = "Ajouter ${product.nom}", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (isRupture) "Rupture" else "Ajouter")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CartAndPaymentPane(
    cartItems: List<CartItem>,
    total: Long,
    devise: String,
    paymentMode: ModePaiement,
    cashReceivedText: String,
    isValidating: Boolean,
    saleError: String?,
    onUpdateQuantity: (Long, Int) -> Unit,
    onRemoveFromCart: (Long) -> Unit,
    onClearCart: () -> Unit,
    onSetPaymentMode: (ModePaiement) -> Unit,
    onSetCashText: (String) -> Unit,
    onValidateSale: () -> Unit
) {
    val cashAmount = cashReceivedText.toLongOrNull() ?: 0L
    val changeDue = if (paymentMode == ModePaiement.ESPECES && cashAmount >= total) cashAmount - total else 0L
    val cashInsufficient = paymentMode == ModePaiement.ESPECES && total > 0 && cashAmount < total
    var qtyEditTarget by remember { mutableStateOf<CartItem?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Panier en cours", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            if (cartItems.isNotEmpty()) {
                TextButton(
                    onClick = onClearCart,
                    modifier = Modifier.testTag("clear_cart_button")
                ) {
                    Text("Vider", color = RuptureText)
                }
            }
        }

        if (cartItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                EmptyStateCard(
                    icon = Icons.Filled.ShoppingCart,
                    title = "Panier vide",
                    description = "Sélectionnez des produits dans le catalogue pour démarrer une vente."
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(cartItems, key = { it.produitId }) { item ->
                    Surface(
                        color = AppBackground,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, AppOutlineSubtle)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.nom, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                    Text(
                                        "${item.reference} • ${Formatters.formatMontant(item.prixUnitaire, devise)} / u",
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
                                        color = TextMuted
                                    )
                                }
                                IconButton(
                                    onClick = { onRemoveFromCart(item.produitId) },
                                    modifier = Modifier.testTag("remove_cart_${item.reference}")
                                ) {
                                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Retirer ${item.nom}", tint = RuptureText)
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    FilledTonalIconButton(
                                        onClick = { onUpdateQuantity(item.produitId, item.quantite - 1) },
                                        modifier = Modifier.size(40.dp).testTag("dec_qty_${item.reference}")
                                    ) {
                                        Icon(Icons.Filled.Remove, contentDescription = "Diminuer quantité")
                                    }
                                    Surface(
                                        onClick = { qtyEditTarget = item },
                                        color = AppSurface,
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, AppOutline),
                                        modifier = Modifier.testTag("edit_qty_${item.reference}")
                                    ) {
                                        Text(
                                            text = "${item.quantite}",
                                            modifier = Modifier
                                                .widthIn(min = 40.dp)
                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                            textAlign = TextAlign.Center,
                                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMono)
                                        )
                                    }
                                    FilledTonalIconButton(
                                        onClick = { onUpdateQuantity(item.produitId, item.quantite + 1) },
                                        modifier = Modifier.size(40.dp).testTag("inc_qty_${item.reference}")
                                    ) {
                                        Icon(Icons.Filled.Add, contentDescription = "Augmenter quantité")
                                    }
                                }
                                Text(
                                    text = Formatters.formatMontant(item.sousTotal, devise),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontFamily = JetBrainsMono,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = TextPrimary
                                )
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = AppOutlineSubtle)

        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total à payer", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text(
                    text = Formatters.formatMontant(total, devise),
                    style = MaterialTheme.typography.headlineMedium.copy(fontFamily = JetBrainsMono),
                    color = ActionBlue,
                    modifier = Modifier.testTag("cart_total_text")
                )
            }

            Text("Mode de paiement *", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModePaiement.entries.forEach { mode ->
                    FilterChip(
                        selected = paymentMode == mode,
                        onClick = { onSetPaymentMode(mode) },
                        label = { Text(mode.libelle, maxLines = 1) },
                        modifier = Modifier.weight(1f).testTag("payment_mode_${mode.name}")
                    )
                }
            }

            if (paymentMode == ModePaiement.ESPECES) {
                OutlinedTextField(
                    value = cashReceivedText,
                    onValueChange = onSetCashText,
                    label = { Text("Somme remise ($devise) *") },
                    trailingIcon = {
                        if (total > 0) {
                            TextButton(
                                onClick = { onSetCashText(total.toString()) },
                                modifier = Modifier.testTag("cash_exact_button")
                            ) {
                                Text("Exact")
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = cashInsufficient && cashReceivedText.isNotEmpty(),
                    supportingText = {
                        when {
                            cashReceivedText.isEmpty() -> Text("Saisissez le montant reçu en espèces")
                            cashInsufficient -> Text("Somme insuffisante (manque ${Formatters.formatMontant(total - cashAmount, devise)})", color = RuptureText)
                            else -> Text("Monnaie à rendre : ${Formatters.formatMontant(changeDue, devise)}", color = DisponibleText, fontWeight = FontWeight.SemiBold)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cash_received_input")
                )
            }

            if (!saleError.isNullOrBlank()) {
                Surface(
                    color = RuptureBackground,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, RuptureBorder)
                ) {
                    Text(
                        text = saleError,
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        color = RuptureText,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Button(
                onClick = onValidateSale,
                enabled = cartItems.isNotEmpty() && !isValidating && !cashInsufficient,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("validate_sale_button")
            ) {
                if (isValidating) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Validation...")
                } else {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Valider la vente")
                }
            }
        }
    }

    if (qtyEditTarget != null) {
        val target = qtyEditTarget!!
        var qtyInput by remember(target.produitId) { mutableStateOf(target.quantite.toString()) }
        var qtyErr by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { qtyEditTarget = null },
            title = { Text("Quantité — ${target.nom}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = qtyInput,
                        onValueChange = { qtyInput = it.filter { ch -> ch.isDigit() }; qtyErr = null },
                        label = { Text("Quantité (> 0) *") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = qtyErr != null,
                        supportingText = { if (qtyErr != null) Text(qtyErr!!, color = RuptureText) },
                        modifier = Modifier.fillMaxWidth().testTag("cart_qty_dialog_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = qtyInput.toIntOrNull() ?: 0
                        if (parsed <= 0) {
                            qtyErr = "La quantité doit être supérieure à 0."
                        } else {
                            onUpdateQuantity(target.produitId, parsed)
                            qtyEditTarget = null
                        }
                    },
                    modifier = Modifier.testTag("cart_qty_dialog_confirm")
                ) { Text("Appliquer") }
            },
            dismissButton = {
                TextButton(onClick = { qtyEditTarget = null }) { Text("Annuler") }
            }
        )
    }
}
