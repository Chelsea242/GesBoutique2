package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.domain.DatabaseHolder
import com.example.domain.Formatters
import com.example.domain.StoreRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

data class CartItem(
    val produitId: Long,
    val reference: String,
    val nom: String,
    val prixUnitaire: Long,
    val quantite: Int,
    val stockConnuAuPanier: Int
) {
    val sousTotal: Long get() = prixUnitaire * quantite
}

data class FeedbackEvent(
    val message: String,
    val isError: Boolean = false
)

enum class StockFilterMode(val libelle: String) {
    TOUS("Tous"),
    EN_RUPTURE("En rupture"),
    STOCK_FAIBLE("Stock faible"),
    DISPONIBLE("Disponibles")
}

enum class ProductActiveFilter(val libelle: String) {
    ACTIFS("Actifs"),
    ARCHIVES("Archivés"),
    TOUS("Tous")
}

enum class ProductSortOption(val libelle: String) {
    NOM_ASC("Nom (A→Z)"),
    REF_ASC("Référence"),
    PRIX_DESC("Prix décroissant"),
    STOCK_ASC("Stock croissant")
}

enum class HistoryPeriodFilter(val libelle: String) {
    AUJOURDHUI("Aujourd'hui"),
    SEPT_JOURS("7 jours"),
    TOUTES("Toutes")
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val dbHolder = DatabaseHolder(application.applicationContext)
    val repository = StoreRepository(dbHolder)

    val currentUser: StateFlow<UtilisateurEntity?> = repository.sessionUser

    val hasAnyUser: StateFlow<Boolean?> = repository.userCountFlow
        .map<Int, Boolean?> { it > 0 }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val parametres: StateFlow<ParametresMagasin> = repository.parametresFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ParametresMagasin())

    val indicateursJour: StateFlow<IndicateursJour> = repository.observeIndicateursJour()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            IndicateursJour(Formatters.todayDateMetier(), 0L, 0, 0, 0, 0)
        )

    val produits: StateFlow<List<ProduitAvecStock>> = repository.produitsAvecStockFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<CategorieEntity>> = repository.categoriesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mouvements: StateFlow<List<MouvementAvecDetails>> = repository.mouvementsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val ventes: StateFlow<List<VenteAvecVendeur>> = repository.ventesVisibleFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val utilisateurs: StateFlow<List<UtilisateurEntity>> = repository.allUsersFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val audits: StateFlow<List<AuditAvecAuteur>> = repository.auditsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _backups = MutableStateFlow<List<SauvegardeInfo>>(emptyList())
    val backups: StateFlow<List<SauvegardeInfo>> = _backups.asStateFlow()

    // Navigation & filtres partagés (pour liens rapides depuis Tableau de bord)
    private val _stockFilter = MutableStateFlow(StockFilterMode.TOUS)
    val stockFilter: StateFlow<StockFilterMode> = _stockFilter.asStateFlow()

    private val _historyPeriodFilter = MutableStateFlow(HistoryPeriodFilter.TOUTES)
    val historyPeriodFilter: StateFlow<HistoryPeriodFilter> = _historyPeriodFilter.asStateFlow()

    // Panier Nouvelle Vente
    private val _cartItems = MutableStateFlow<List<CartItem>>(emptyList())
    val cartItems: StateFlow<List<CartItem>> = _cartItems.asStateFlow()

    private val _cartToken = MutableStateFlow(UUID.randomUUID().toString())
    private val _paymentMode = MutableStateFlow(ModePaiement.ESPECES)
    val paymentMode: StateFlow<ModePaiement> = _paymentMode.asStateFlow()

    private val _cashReceivedText = MutableStateFlow("")
    val cashReceivedText: StateFlow<String> = _cashReceivedText.asStateFlow()

    private val _isValidatingSale = MutableStateFlow(false)
    val isValidatingSale: StateFlow<Boolean> = _isValidatingSale.asStateFlow()

    private val _saleError = MutableStateFlow<String?>(null)
    val saleError: StateFlow<String?> = _saleError.asStateFlow()

    private val _priceChangePrompt = MutableStateFlow<ValidationVenteResult.PrixModifie?>(null)
    val priceChangePrompt: StateFlow<ValidationVenteResult.PrixModifie?> = _priceChangePrompt.asStateFlow()

    private val _completedReceipt = MutableStateFlow<VenteDetailComplet?>(null)
    val completedReceipt: StateFlow<VenteDetailComplet?> = _completedReceipt.asStateFlow()

    // Rapports
    private val _rapportDebut = MutableStateFlow(Formatters.todayDateMetier())
    val rapportDebut: StateFlow<String> = _rapportDebut.asStateFlow()
    private val _rapportFin = MutableStateFlow(Formatters.todayDateMetier())
    val rapportFin: StateFlow<String> = _rapportFin.asStateFlow()
    private val _rapportLignes = MutableStateFlow<List<RapportProduitLigne>>(emptyList())
    val rapportLignes: StateFlow<List<RapportProduitLigne>> = _rapportLignes.asStateFlow()
    private val _rapportAnnulations = MutableStateFlow(0 to 0L)
    val rapportAnnulations: StateFlow<Pair<Int, Long>> = _rapportAnnulations.asStateFlow()

    // Auth UI error
    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()
    private val _isAuthLoading = MutableStateFlow(false)
    val isAuthLoading: StateFlow<Boolean> = _isAuthLoading.asStateFlow()

    private val _feedback = MutableSharedFlow<FeedbackEvent>(extraBufferCapacity = 8)
    val feedback: SharedFlow<FeedbackEvent> = _feedback.asSharedFlow()

    init {
        viewModelScope.launch {
            repository.runStartupAutoBackupIfDue()
        }
    }

    fun setStockFilter(mode: StockFilterMode) {
        _stockFilter.value = mode
    }

    fun setHistoryPeriodFilter(mode: HistoryPeriodFilter) {
        _historyPeriodFilter.value = mode
    }

    fun clearAuthError() {
        _authError.value = null
    }

    // --- Auth ---

    fun createInitialAdmin(nom: String, identifiant: String, motDePasse: String, avecDemo: Boolean) {
        viewModelScope.launch {
            _isAuthLoading.value = true
            _authError.value = null
            when (val res = repository.createInitialAdmin(nom, identifiant, motDePasse, avecDemo)) {
                is ServiceResult.Succes -> {
                    _feedback.tryEmit(FeedbackEvent(res.message))
                    refreshBackups()
                }
                is ServiceResult.Erreur -> _authError.value = res.message
            }
            _isAuthLoading.value = false
        }
    }

    fun login(identifiant: String, motDePasse: String) {
        viewModelScope.launch {
            _isAuthLoading.value = true
            _authError.value = null
            when (val res = repository.login(identifiant, motDePasse)) {
                is ServiceResult.Succes -> {
                    _feedback.tryEmit(FeedbackEvent(res.message))
                    if (res.data.role == RoleUtilisateur.ADMINISTRATEUR.name) {
                        refreshBackups()
                        loadReport(_rapportDebut.value, _rapportFin.value)
                    }
                }
                is ServiceResult.Erreur -> _authError.value = res.message
            }
            _isAuthLoading.value = false
        }
    }

    fun logout() {
        viewModelScope.launch {
            clearCart()
            repository.logout()
        }
    }

    // --- Panier & Vente ---

    fun addToCart(product: ProduitAvecStock) {
        _saleError.value = null
        if (!product.actif) {
            _saleError.value = "Produit archivé : vente impossible."
            return
        }
        if (product.stockDisponible <= 0) {
            _saleError.value = "« ${product.nom} » est en rupture de stock."
            return
        }
        val current = _cartItems.value.toMutableList()
        val index = current.indexOfFirst { it.produitId == product.id }
        if (index >= 0) {
            val existing = current[index]
            if (existing.quantite + 1 > product.stockDisponible) {
                _saleError.value = "Stock disponible insuffisant pour « ${product.nom} » (max ${product.stockDisponible})."
                return
            }
            current[index] = existing.copy(
                quantite = existing.quantite + 1,
                prixUnitaire = product.prixVente,
                stockConnuAuPanier = product.stockDisponible
            )
        } else {
            current.add(
                CartItem(
                    produitId = product.id,
                    reference = product.reference,
                    nom = product.nom,
                    prixUnitaire = product.prixVente,
                    quantite = 1,
                    stockConnuAuPanier = product.stockDisponible
                )
            )
        }
        _cartItems.value = current
        _cartToken.value = UUID.randomUUID().toString()
    }

    fun updateCartQuantity(produitId: Long, newQuantity: Int) {
        _saleError.value = null
        if (newQuantity <= 0) {
            removeFromCart(produitId)
            return
        }
        val productStock = produits.value.firstOrNull { it.id == produitId }?.stockDisponible ?: Int.MAX_VALUE
        if (newQuantity > productStock) {
            _saleError.value = "Quantité limitée au stock disponible ($productStock)."
            return
        }
        _cartItems.value = _cartItems.value.map {
            if (it.produitId == produitId) it.copy(quantite = newQuantity, stockConnuAuPanier = productStock) else it
        }
        _cartToken.value = UUID.randomUUID().toString()
    }

    fun removeFromCart(produitId: Long) {
        _saleError.value = null
        _cartItems.value = _cartItems.value.filterNot { it.produitId == produitId }
        _cartToken.value = UUID.randomUUID().toString()
    }

    fun clearCart() {
        _cartItems.value = emptyList()
        _cashReceivedText.value = ""
        _saleError.value = null
        _priceChangePrompt.value = null
        _cartToken.value = UUID.randomUUID().toString()
    }

    fun setPaymentMode(mode: ModePaiement) {
        _paymentMode.value = mode
        _saleError.value = null
    }

    fun setCashReceivedText(text: String) {
        _cashReceivedText.value = text.filter { it.isDigit() }
        _saleError.value = null
    }

    fun submitSale() {
        if (_isValidatingSale.value) return
        val items = _cartItems.value
        if (items.isEmpty()) {
            _saleError.value = "Ajoutez au moins un produit au panier."
            return
        }
        val total = items.sumOf { it.sousTotal }
        val mode = _paymentMode.value
        val cashInput = if (mode == ModePaiement.ESPECES) {
            _cashReceivedText.value.toLongOrNull() ?: 0L
        } else {
            total
        }
        if (mode == ModePaiement.ESPECES && cashInput < total) {
            _saleError.value = "La somme remise est inférieure au total (${Formatters.formatMontant(total, parametres.value.devise)})."
            return
        }

        viewModelScope.launch {
            _isValidatingSale.value = true
            _saleError.value = null
            val inputs = items.map {
                PanierLigneInput(
                    produitId = it.produitId,
                    reference = it.reference,
                    nom = it.nom,
                    prixUnitaireAttendu = it.prixUnitaire,
                    quantite = it.quantite
                )
            }
            when (val res = repository.validateSale(inputs, mode, cashInput, _cartToken.value)) {
                is ValidationVenteResult.Succes -> {
                    _completedReceipt.value = res.vente
                    clearCart()
                    _feedback.tryEmit(FeedbackEvent("Vente ${res.vente.vente.numero} enregistrée."))
                }
                is ValidationVenteResult.PrixModifie -> {
                    _priceChangePrompt.value = res
                }
                is ValidationVenteResult.StockInsuffisant -> {
                    _saleError.value = "Stock insuffisant pour « ${res.produitNom} » : disponible ${res.stockDisponible}, demandé ${res.quantiteDemandee}."
                }
                is ValidationVenteResult.Erreur -> {
                    _saleError.value = res.message
                }
            }
            _isValidatingSale.value = false
        }
    }

    fun confirmUpdatedPricesAndRetry() {
        val prompt = _priceChangePrompt.value ?: return
        _cartItems.value = _cartItems.value.map { item ->
            val updatedPrice = prompt.nouveauxPrix[item.produitId] ?: item.prixUnitaire
            item.copy(prixUnitaire = updatedPrice)
        }
        _cartToken.value = UUID.randomUUID().toString()
        _priceChangePrompt.value = null
        submitSale()
    }

    fun dismissPriceChangePrompt() {
        _priceChangePrompt.value = null
    }

    fun dismissCompletedReceipt() {
        _completedReceipt.value = null
    }

    // --- Produits & Catégories ---

    fun createCategory(nom: String, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            when (val res = repository.createCategory(nom)) {
                is ServiceResult.Succes -> {
                    _feedback.tryEmit(FeedbackEvent(res.message))
                    onDone(true)
                }
                is ServiceResult.Erreur -> {
                    _feedback.tryEmit(FeedbackEvent(res.message, isError = true))
                    onDone(false)
                }
            }
        }
    }

    fun saveProduct(
        editingId: Long?,
        reference: String,
        nom: String,
        categorieId: Long?,
        prixVente: Long,
        seuilAlerte: Int,
        stockInitial: Int,
        onResult: (ServiceResult<*>) -> Unit
    ) {
        viewModelScope.launch {
            val res = if (editingId == null) {
                repository.createProduct(reference, nom, categorieId, prixVente, seuilAlerte, stockInitial)
            } else {
                repository.updateProduct(editingId, reference, nom, categorieId, prixVente, seuilAlerte)
            }
            when (res) {
                is ServiceResult.Succes -> _feedback.tryEmit(FeedbackEvent(res.message))
                is ServiceResult.Erreur -> _feedback.tryEmit(FeedbackEvent(res.message, isError = true))
            }
            onResult(res)
        }
    }

    fun toggleProductActive(produitId: Long, actif: Boolean) {
        viewModelScope.launch {
            when (val res = repository.setProductActive(produitId, actif)) {
                is ServiceResult.Succes -> _feedback.tryEmit(FeedbackEvent(res.message))
                is ServiceResult.Erreur -> _feedback.tryEmit(FeedbackEvent(res.message, isError = true))
            }
        }
    }

    // --- Stocks ---

    fun restockProduct(produitId: Long, quantite: Int, motif: String, onResult: (ServiceResult<*>) -> Unit) {
        viewModelScope.launch {
            val res = repository.restockProduct(produitId, quantite, motif)
            when (res) {
                is ServiceResult.Succes -> _feedback.tryEmit(FeedbackEvent(res.message))
                is ServiceResult.Erreur -> _feedback.tryEmit(FeedbackEvent(res.message, isError = true))
            }
            onResult(res)
        }
    }

    fun correctInventory(produitId: Long, quantiteComptee: Int, motif: String, onResult: (ServiceResult<*>) -> Unit) {
        viewModelScope.launch {
            val res = repository.inventoryCorrection(produitId, quantiteComptee, motif)
            when (res) {
                is ServiceResult.Succes -> _feedback.tryEmit(FeedbackEvent(res.message))
                is ServiceResult.Erreur -> _feedback.tryEmit(FeedbackEvent(res.message, isError = true))
            }
            onResult(res)
        }
    }

    // --- Historique & Annulation ---

    suspend fun loadSaleDetail(venteId: Long): VenteDetailComplet? {
        return repository.getVenteDetail(venteId)
    }

    fun cancelSale(venteId: Long, motif: String, onSuccess: (VenteDetailComplet) -> Unit) {
        viewModelScope.launch {
            when (val res = repository.cancelSale(venteId, motif)) {
                is ServiceResult.Succes -> {
                    _feedback.tryEmit(FeedbackEvent(res.message))
                    onSuccess(res.data)
                }
                is ServiceResult.Erreur -> {
                    _feedback.tryEmit(FeedbackEvent(res.message, isError = true))
                }
            }
        }
    }

    // --- Rapports & CSV ---

    fun loadReport(dateDebut: String, dateFin: String) {
        _rapportDebut.value = dateDebut
        _rapportFin.value = dateFin
        viewModelScope.launch {
            when (val res = repository.getRapportPeriode(dateDebut, dateFin)) {
                is ServiceResult.Succes -> {
                    _rapportLignes.value = res.data
                    val cancelRes = repository.getCancelledSummaryOnPeriod(dateDebut, dateFin)
                    if (cancelRes is ServiceResult.Succes) {
                        _rapportAnnulations.value = cancelRes.data
                    }
                }
                is ServiceResult.Erreur -> _feedback.tryEmit(FeedbackEvent(res.message, isError = true))
            }
        }
    }

    fun exportSalesCsvToUri(uri: Uri, ventesFiltered: List<VenteAvecVendeur>) {
        viewModelScope.launch {
            val out = getApplication<Application>().contentResolver.openOutputStream(uri)
            if (out == null) {
                _feedback.tryEmit(FeedbackEvent("Impossible d'ouvrir le fichier cible.", isError = true))
                return@launch
            }
            when (val res = repository.exportSalesCsv(ventesFiltered, out)) {
                is ServiceResult.Succes -> _feedback.tryEmit(FeedbackEvent(res.message))
                is ServiceResult.Erreur -> _feedback.tryEmit(FeedbackEvent(res.message, isError = true))
            }
        }
    }

    fun exportStockCsvToUri(uri: Uri, produitsFiltered: List<ProduitAvecStock>) {
        viewModelScope.launch {
            val out = getApplication<Application>().contentResolver.openOutputStream(uri)
            if (out == null) {
                _feedback.tryEmit(FeedbackEvent("Impossible d'ouvrir le fichier cible.", isError = true))
                return@launch
            }
            when (val res = repository.exportStockCsv(produitsFiltered, out)) {
                is ServiceResult.Succes -> _feedback.tryEmit(FeedbackEvent(res.message))
                is ServiceResult.Erreur -> _feedback.tryEmit(FeedbackEvent(res.message, isError = true))
            }
        }
    }

    fun exportReportCsvToUri(uri: Uri) {
        viewModelScope.launch {
            val out = getApplication<Application>().contentResolver.openOutputStream(uri)
            if (out == null) {
                _feedback.tryEmit(FeedbackEvent("Impossible d'ouvrir le fichier cible.", isError = true))
                return@launch
            }
            when (val res = repository.exportReportCsv(_rapportLignes.value, _rapportDebut.value, _rapportFin.value, out)) {
                is ServiceResult.Succes -> _feedback.tryEmit(FeedbackEvent(res.message))
                is ServiceResult.Erreur -> _feedback.tryEmit(FeedbackEvent(res.message, isError = true))
            }
        }
    }

    // --- Administration : Utilisateurs, Paramètres, Sauvegardes ---

    fun createUser(nom: String, identifiant: String, motDePasse: String, role: RoleUtilisateur, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            when (val res = repository.createUser(nom, identifiant, motDePasse, role)) {
                is ServiceResult.Succes -> {
                    _feedback.tryEmit(FeedbackEvent(res.message))
                    onDone(true)
                }
                is ServiceResult.Erreur -> {
                    _feedback.tryEmit(FeedbackEvent(res.message, isError = true))
                    onDone(false)
                }
            }
        }
    }

    fun toggleUserActive(userId: Long, actif: Boolean) {
        viewModelScope.launch {
            when (val res = repository.toggleUserActive(userId, actif)) {
                is ServiceResult.Succes -> _feedback.tryEmit(FeedbackEvent(res.message))
                is ServiceResult.Erreur -> _feedback.tryEmit(FeedbackEvent(res.message, isError = true))
            }
        }
    }

    fun resetUserPassword(userId: Long, nouveauMdp: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            when (val res = repository.resetUserPassword(userId, nouveauMdp)) {
                is ServiceResult.Succes -> {
                    _feedback.tryEmit(FeedbackEvent(res.message))
                    onDone(true)
                }
                is ServiceResult.Erreur -> {
                    _feedback.tryEmit(FeedbackEvent(res.message, isError = true))
                    onDone(false)
                }
            }
        }
    }

    fun saveStoreSettings(params: ParametresMagasin) {
        viewModelScope.launch {
            when (val res = repository.updateStoreSettings(params)) {
                is ServiceResult.Succes -> _feedback.tryEmit(FeedbackEvent(res.message))
                is ServiceResult.Erreur -> _feedback.tryEmit(FeedbackEvent(res.message, isError = true))
            }
        }
    }

    fun saveStoreLogoFromPicker(uri: Uri) {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val logoFile = java.io.File(context.filesDir, "store_logo.img")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    java.io.FileOutputStream(logoFile).use { out ->
                        input.copyTo(out)
                    }
                }
                val updated = parametres.value.copy(logoUri = Uri.fromFile(logoFile).toString())
                saveStoreSettings(updated)
            } catch (e: Exception) {
                _feedback.tryEmit(FeedbackEvent("Impossible d'enregistrer le logo : ${e.message}", isError = true))
            }
        }
    }

    fun refreshBackups() {
        viewModelScope.launch {
            when (val res = repository.listBackups()) {
                is ServiceResult.Succes -> _backups.value = res.data
                is ServiceResult.Erreur -> Unit
            }
        }
    }

    fun createManualBackup() {
        viewModelScope.launch {
            when (val res = repository.createManualBackup()) {
                is ServiceResult.Succes -> {
                    _feedback.tryEmit(FeedbackEvent(res.message))
                    refreshBackups()
                }
                is ServiceResult.Erreur -> _feedback.tryEmit(FeedbackEvent(res.message, isError = true))
            }
        }
    }

    fun exportBackupToUri(backupPath: String, uri: Uri) {
        viewModelScope.launch {
            val out = getApplication<Application>().contentResolver.openOutputStream(uri)
            if (out == null) {
                _feedback.tryEmit(FeedbackEvent("Impossible d'ouvrir la destination.", isError = true))
                return@launch
            }
            when (val res = repository.exportBackupFile(backupPath, out)) {
                is ServiceResult.Succes -> _feedback.tryEmit(FeedbackEvent(res.message))
                is ServiceResult.Erreur -> _feedback.tryEmit(FeedbackEvent(res.message, isError = true))
            }
        }
    }

    fun restoreLocalBackup(backupPath: String) {
        viewModelScope.launch {
            when (val res = repository.restoreFromLocalBackup(backupPath)) {
                is ServiceResult.Succes -> _feedback.tryEmit(FeedbackEvent(res.message))
                is ServiceResult.Erreur -> _feedback.tryEmit(FeedbackEvent(res.message, isError = true))
            }
        }
    }

    fun restoreExternalBackup(uri: Uri) {
        viewModelScope.launch {
            val input = getApplication<Application>().contentResolver.openInputStream(uri)
            if (input == null) {
                _feedback.tryEmit(FeedbackEvent("Impossible de lire le fichier sélectionné.", isError = true))
                return@launch
            }
            when (val res = repository.restoreFromExternalStream(input)) {
                is ServiceResult.Succes -> _feedback.tryEmit(FeedbackEvent(res.message))
                is ServiceResult.Erreur -> _feedback.tryEmit(FeedbackEvent(res.message, isError = true))
            }
        }
    }

    fun loadDemoDataset() {
        viewModelScope.launch {
            when (val res = repository.loadDemoDataset()) {
                is ServiceResult.Succes -> _feedback.tryEmit(FeedbackEvent(res.message))
                is ServiceResult.Erreur -> _feedback.tryEmit(FeedbackEvent(res.message, isError = true))
            }
        }
    }

    fun resetDatabaseToBlank() {
        viewModelScope.launch {
            clearCart()
            when (val res = repository.resetDatabaseToBlank()) {
                is ServiceResult.Succes -> _feedback.tryEmit(FeedbackEvent(res.message))
                is ServiceResult.Erreur -> _feedback.tryEmit(FeedbackEvent(res.message, isError = true))
            }
        }
    }

    fun emitFeedback(message: String, isError: Boolean = false) {
        _feedback.tryEmit(FeedbackEvent(message, isError))
    }

    override fun onCleared() {
        super.onCleared()
        dbHolder.close()
    }
}
