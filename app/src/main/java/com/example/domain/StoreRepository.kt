package com.example.domain

import androidx.room.withTransaction
import com.example.data.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class StoreRepository(
    val dbHolder: DatabaseHolder,
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) {
    private val _sessionUser = MutableStateFlow<UtilisateurEntity?>(null)
    val sessionUser: StateFlow<UtilisateurEntity?> = _sessionUser.asStateFlow()

    private fun db(): AppDatabase = dbHolder.currentDb

    val userCountFlow: Flow<Int> = dbHolder.databaseFlow.flatMapLatest { it.utilisateurDao().observeUserCount() }
    val allUsersFlow: Flow<List<UtilisateurEntity>> = dbHolder.databaseFlow.flatMapLatest { it.utilisateurDao().observeAllUsers() }
    val categoriesFlow: Flow<List<CategorieEntity>> = dbHolder.databaseFlow.flatMapLatest { it.categorieDao().observeAll() }
    val produitsAvecStockFlow: Flow<List<ProduitAvecStock>> = dbHolder.databaseFlow.flatMapLatest { it.produitDao().observeProduitsAvecStock() }
    val mouvementsFlow: Flow<List<MouvementAvecDetails>> = dbHolder.databaseFlow.flatMapLatest { it.mouvementStockDao().observeAllMouvements() }
    val auditsFlow: Flow<List<AuditAvecAuteur>> = dbHolder.databaseFlow.flatMapLatest { it.journalAuditDao().observeRecentAudits() }

    val parametresFlow: Flow<ParametresMagasin> = dbHolder.databaseFlow.flatMapLatest { db ->
        db.parametreDao().observeAll().map { mapToParametres(it) }
    }

    val ventesVisibleFlow: Flow<List<VenteAvecVendeur>> = combine(
        dbHolder.databaseFlow,
        _sessionUser
    ) { db, user -> Pair(db, user) }
        .flatMapLatest { (db, user) ->
            when {
                user == null -> flowOf(emptyList())
                user.role == RoleUtilisateur.ADMINISTRATEUR.name -> db.venteDao().observeAllVentes()
                else -> db.venteDao().observeVentesByVendeur(user.id)
            }
        }

    fun observeIndicateursJour(): Flow<IndicateursJour> {
        return combine(
            dbHolder.databaseFlow,
            _sessionUser,
            parametresFlow
        ) { db, user, params -> Triple(db, user, params) }
            .flatMapLatest { (db, user, params) ->
                val today = Formatters.todayDateMetier(params.fuseauHoraire, timeProvider())
                if (user == null) {
                    return@flatMapLatest flowOf(
                        IndicateursJour(today, 0L, 0, 0, 0, 0)
                    )
                }
                val isAdmin = user.role == RoleUtilisateur.ADMINISTRATEUR.name
                val montantFlow = if (isAdmin) {
                    db.venteDao().observeMontantVentesJourAll(today)
                } else {
                    db.venteDao().observeMontantVentesJourVendeur(today, user.id)
                }
                val nombreFlow = if (isAdmin) {
                    db.venteDao().observeNombreVentesJourAll(today)
                } else {
                    db.venteDao().observeNombreVentesJourVendeur(today, user.id)
                }
                val unitesFlow = if (isAdmin) {
                    db.venteDao().observeUnitesVenduesJourAll(today)
                } else {
                    db.venteDao().observeUnitesVenduesJourVendeur(today, user.id)
                }
                val produitsFlow = db.produitDao().observeProduitsAvecStock()

                combine(montantFlow, nombreFlow, unitesFlow, produitsFlow) { montant, nombre, unites, produits ->
                    val actifs = produits.filter { it.actif }
                    val ruptures = actifs.count { it.stockDisponible <= 0 }
                    val stockFaible = actifs.count { it.stockDisponible in 1..it.seuilAlerte }
                    IndicateursJour(
                        dateMetier = today,
                        montantVentesValidees = montant,
                        nombreVentesValidees = nombre,
                        unitesVendues = unites,
                        referencesEnRupture = ruptures,
                        referencesEnStockFaible = stockFaible
                    )
                }
            }
    }

    suspend fun hasAnyUser(): Boolean = db().utilisateurDao().countUsers() > 0

    suspend fun getParametres(): ParametresMagasin {
        return mapToParametres(db().parametreDao().getAll())
    }

    // --- Authentification & Comptes ---

    suspend fun createInitialAdmin(
        nom: String,
        identifiant: String,
        motDePasse: String,
        avecDonneesDemo: Boolean = false
    ): ServiceResult<UtilisateurEntity> {
        val nomTrim = nom.trim()
        val idTrim = identifiant.trim()
        if (nomTrim.isEmpty()) return ServiceResult.Erreur("Le nom complet est obligatoire.")
        if (idTrim.length < 3) return ServiceResult.Erreur("L'identifiant doit contenir au moins 3 caractères.")
        if (motDePasse.length < 4) return ServiceResult.Erreur("Le mot de passe doit contenir au moins 4 caractères.")

        val database = db()
        if (database.utilisateurDao().countUsers() > 0) {
            return ServiceResult.Erreur("Un compte administrateur existe déjà.")
        }

        val hashed = PasswordHasher.hashPassword(motDePasse.toCharArray())
        val created = database.withTransaction {
            val userId = database.utilisateurDao().insert(
                UtilisateurEntity(
                    nom = nomTrim,
                    identifiant = idTrim,
                    motDePasseHash = hashed.hashBase64,
                    sel = hashed.saltBase64,
                    parametresHachage = hashed.parameters,
                    role = RoleUtilisateur.ADMINISTRATEUR.name,
                    actif = true
                )
            )
            val entity = database.utilisateurDao().findById(userId)!!
            database.journalAuditDao().insert(
                JournalAuditEntity(
                    auteurId = entity.id,
                    dateHeureUtc = timeProvider(),
                    action = "CREATION_ADMIN_INITIAL",
                    entite = "Utilisateur",
                    entiteId = entity.id,
                    details = "Création du premier administrateur ${entity.identifiant}"
                )
            )
            entity
        }
        _sessionUser.value = created
        if (avecDonneesDemo) {
            seedDemoDataInternal(created.id)
        }
        return ServiceResult.Succes(created, "Compte administrateur créé.")
    }

    suspend fun login(identifiant: String, motDePasse: String): ServiceResult<UtilisateurEntity> {
        val idTrim = identifiant.trim()
        if (idTrim.isEmpty() || motDePasse.isEmpty()) {
            return ServiceResult.Erreur("Veuillez saisir votre identifiant et votre mot de passe.")
        }
        val database = db()
        val user = database.utilisateurDao().findByIdentifiant(idTrim)
        if (user == null) {
            database.journalAuditDao().insert(
                JournalAuditEntity(
                    auteurId = null,
                    dateHeureUtc = timeProvider(),
                    action = "CONNEXION_REFUSEE",
                    entite = "Utilisateur",
                    entiteId = null,
                    details = "Tentative de connexion avec identifiant inconnu : $idTrim"
                )
            )
            return ServiceResult.Erreur("Identifiant ou mot de passe incorrect.")
        }
        if (!user.actif) {
            database.journalAuditDao().insert(
                JournalAuditEntity(
                    auteurId = user.id,
                    dateHeureUtc = timeProvider(),
                    action = "CONNEXION_REFUSEE",
                    entite = "Utilisateur",
                    entiteId = user.id,
                    details = "Compte désactivé : ${user.identifiant}"
                )
            )
            return ServiceResult.Erreur("Ce compte est désactivé. Contactez un administrateur.")
        }
        val ok = PasswordHasher.verifyPassword(
            motDePasse.toCharArray(),
            user.motDePasseHash,
            user.sel,
            user.parametresHachage
        )
        if (!ok) {
            database.journalAuditDao().insert(
                JournalAuditEntity(
                    auteurId = user.id,
                    dateHeureUtc = timeProvider(),
                    action = "CONNEXION_REFUSEE",
                    entite = "Utilisateur",
                    entiteId = user.id,
                    details = "Mot de passe invalide pour ${user.identifiant}"
                )
            )
            return ServiceResult.Erreur("Identifiant ou mot de passe incorrect.")
        }
        database.journalAuditDao().insert(
            JournalAuditEntity(
                auteurId = user.id,
                dateHeureUtc = timeProvider(),
                action = "CONNEXION",
                entite = "Utilisateur",
                entiteId = user.id,
                details = "Connexion de ${user.nom} (${user.role})"
            )
        )
        _sessionUser.value = user
        return ServiceResult.Succes(user, "Bienvenue, ${user.nom}")
    }

    suspend fun logout() {
        val current = _sessionUser.value
        if (current != null) {
            runCatching {
                db().journalAuditDao().insert(
                    JournalAuditEntity(
                        auteurId = current.id,
                        dateHeureUtc = timeProvider(),
                        action = "DECONNEXION",
                        entite = "Utilisateur",
                        entiteId = current.id,
                        details = "Déconnexion de ${current.identifiant}"
                    )
                )
            }
        }
        _sessionUser.value = null
    }

    suspend fun createUser(
        nom: String,
        identifiant: String,
        motDePasse: String,
        role: RoleUtilisateur
    ): ServiceResult<UtilisateurEntity> {
        val admin = requireAdmin() ?: return ServiceResult.Erreur("Accès refusé : action réservée à l'administrateur.")
        val nomTrim = nom.trim()
        val idTrim = identifiant.trim()
        if (nomTrim.isEmpty()) return ServiceResult.Erreur("Le nom est obligatoire.")
        if (idTrim.length < 3) return ServiceResult.Erreur("L'identifiant doit contenir au moins 3 caractères.")
        if (motDePasse.length < 4) return ServiceResult.Erreur("Le mot de passe doit contenir au moins 4 caractères.")

        val database = db()
        if (database.utilisateurDao().findByIdentifiant(idTrim) != null) {
            return ServiceResult.Erreur("L'identifiant '$idTrim' est déjà utilisé.")
        }

        val hashed = PasswordHasher.hashPassword(motDePasse.toCharArray())
        val created = database.withTransaction {
            val id = database.utilisateurDao().insert(
                UtilisateurEntity(
                    nom = nomTrim,
                    identifiant = idTrim,
                    motDePasseHash = hashed.hashBase64,
                    sel = hashed.saltBase64,
                    parametresHachage = hashed.parameters,
                    role = role.name,
                    actif = true
                )
            )
            val entity = database.utilisateurDao().findById(id)!!
            database.journalAuditDao().insert(
                JournalAuditEntity(
                    auteurId = admin.id,
                    dateHeureUtc = timeProvider(),
                    action = "CREATION_UTILISATEUR",
                    entite = "Utilisateur",
                    entiteId = id,
                    details = "Création du compte ${entity.identifiant} (${entity.role})"
                )
            )
            entity
        }
        return ServiceResult.Succes(created, "Compte '${created.identifiant}' créé.")
    }

    suspend fun toggleUserActive(targetUserId: Long, actif: Boolean): ServiceResult<Unit> {
        val admin = requireAdmin() ?: return ServiceResult.Erreur("Accès refusé : action réservée à l'administrateur.")
        val database = db()
        val target = database.utilisateurDao().findById(targetUserId)
            ?: return ServiceResult.Erreur("Utilisateur introuvable.")

        if (!actif && target.role == RoleUtilisateur.ADMINISTRATEUR.name && target.actif) {
            val activeAdmins = database.utilisateurDao().countActiveAdmins()
            if (activeAdmins <= 1) {
                return ServiceResult.Erreur("Impossible de désactiver le dernier administrateur actif.")
            }
        }

        database.withTransaction {
            database.utilisateurDao().update(target.copy(actif = actif))
            database.journalAuditDao().insert(
                JournalAuditEntity(
                    auteurId = admin.id,
                    dateHeureUtc = timeProvider(),
                    action = if (actif) "ACTIVATION_UTILISATEUR" else "DESACTIVATION_UTILISATEUR",
                    entite = "Utilisateur",
                    entiteId = target.id,
                    details = "${if (actif) "Activation" else "Désactivation"} du compte ${target.identifiant}"
                )
            )
        }
        if (_sessionUser.value?.id == target.id && !actif) {
            _sessionUser.value = null
        }
        return ServiceResult.Succes(Unit, if (actif) "Compte réactivé." else "Compte désactivé.")
    }

    suspend fun resetUserPassword(targetUserId: Long, nouveauMotDePasse: String): ServiceResult<Unit> {
        val admin = requireAdmin() ?: return ServiceResult.Erreur("Accès refusé : action réservée à l'administrateur.")
        if (nouveauMotDePasse.length < 4) {
            return ServiceResult.Erreur("Le mot de passe doit contenir au moins 4 caractères.")
        }
        val database = db()
        val target = database.utilisateurDao().findById(targetUserId)
            ?: return ServiceResult.Erreur("Utilisateur introuvable.")
        val hashed = PasswordHasher.hashPassword(nouveauMotDePasse.toCharArray())
        database.withTransaction {
            database.utilisateurDao().update(
                target.copy(
                    motDePasseHash = hashed.hashBase64,
                    sel = hashed.saltBase64,
                    parametresHachage = hashed.parameters
                )
            )
            database.journalAuditDao().insert(
                JournalAuditEntity(
                    auteurId = admin.id,
                    dateHeureUtc = timeProvider(),
                    action = "REINITIALISATION_MDP",
                    entite = "Utilisateur",
                    entiteId = target.id,
                    details = "Mot de passe modifié pour ${target.identifiant}"
                )
            )
        }
        return ServiceResult.Succes(Unit, "Mot de passe mis à jour pour ${target.identifiant}.")
    }

    // --- Catégories & Produits ---

    suspend fun createCategory(nom: String): ServiceResult<CategorieEntity> {
        val admin = requireAdmin() ?: return ServiceResult.Erreur("Accès refusé : action réservée à l'administrateur.")
        val nomTrim = nom.trim()
        if (nomTrim.isEmpty()) return ServiceResult.Erreur("Le nom de la catégorie est obligatoire.")
        val database = db()
        val existing = database.categorieDao().findByNom(nomTrim)
        if (existing != null) return ServiceResult.Erreur("Cette catégorie existe déjà.")
        val id = database.categorieDao().insert(CategorieEntity(nom = nomTrim))
        database.journalAuditDao().insert(
            JournalAuditEntity(
                auteurId = admin.id,
                dateHeureUtc = timeProvider(),
                action = "CREATION_CATEGORIE",
                entite = "Categorie",
                entiteId = id,
                details = "Catégorie créée : $nomTrim"
            )
        )
        return ServiceResult.Succes(CategorieEntity(id = id, nom = nomTrim), "Catégorie '$nomTrim' créée.")
    }

    suspend fun createProduct(
        reference: String,
        nom: String,
        categorieId: Long?,
        prixVente: Long,
        seuilAlerte: Int,
        stockInitial: Int
    ): ServiceResult<ProduitAvecStock> {
        val admin = requireAdmin() ?: return ServiceResult.Erreur("Accès refusé : action réservée à l'administrateur.")
        val refTrim = reference.trim().uppercase()
        val nomTrim = nom.trim()
        if (refTrim.isEmpty()) return ServiceResult.Erreur("La référence produit est obligatoire.")
        if (nomTrim.isEmpty()) return ServiceResult.Erreur("Le nom du produit est obligatoire.")
        if (prixVente <= 0L) return ServiceResult.Erreur("Le prix de vente doit être strictement positif.")
        if (seuilAlerte < 0) return ServiceResult.Erreur("Le seuil d'alerte doit être positif ou nul.")
        if (stockInitial < 0) return ServiceResult.Erreur("Le stock initial ne peut pas être négatif.")

        val database = db()
        if (database.produitDao().findByReference(refTrim) != null) {
            return ServiceResult.Erreur("La référence '$refTrim' est déjà utilisée.")
        }

        val now = timeProvider()
        val productId = database.withTransaction {
            val id = database.produitDao().insert(
                ProduitEntity(
                    reference = refTrim,
                    nom = nomTrim,
                    categorieId = categorieId,
                    prixVente = prixVente,
                    seuilAlerte = seuilAlerte,
                    actif = true,
                    dateCreationUtc = now
                )
            )
            if (stockInitial > 0) {
                database.mouvementStockDao().insert(
                    MouvementStockEntity(
                        produitId = id,
                        auteurId = admin.id,
                        dateHeureUtc = now,
                        type = TypeMouvement.STOCK_INITIAL.name,
                        quantiteSignee = stockInitial,
                        motif = "Initialisation du stock à la création"
                    )
                )
            }
            database.journalAuditDao().insert(
                JournalAuditEntity(
                    auteurId = admin.id,
                    dateHeureUtc = now,
                    action = "CREATION_PRODUIT",
                    entite = "Produit",
                    entiteId = id,
                    details = "Produit $refTrim ($nomTrim) créé à $prixVente FCFA, stock initial $stockInitial"
                )
            )
            id
        }

        val created = database.produitDao().getProduitsAvecStock().first { it.id == productId }
        return ServiceResult.Succes(created, "Produit '$nomTrim' enregistré.")
    }

    suspend fun updateProduct(
        produitId: Long,
        reference: String,
        nom: String,
        categorieId: Long?,
        prixVente: Long,
        seuilAlerte: Int
    ): ServiceResult<Unit> {
        val admin = requireAdmin() ?: return ServiceResult.Erreur("Accès refusé : action réservée à l'administrateur.")
        val refTrim = reference.trim().uppercase()
        val nomTrim = nom.trim()
        if (refTrim.isEmpty()) return ServiceResult.Erreur("La référence produit est obligatoire.")
        if (nomTrim.isEmpty()) return ServiceResult.Erreur("Le nom du produit est obligatoire.")
        if (prixVente <= 0L) return ServiceResult.Erreur("Le prix de vente doit être strictement positif.")
        if (seuilAlerte < 0) return ServiceResult.Erreur("Le seuil d'alerte doit être positif ou nul.")

        val database = db()
        val existing = database.produitDao().findById(produitId)
            ?: return ServiceResult.Erreur("Produit introuvable.")
        val refOwner = database.produitDao().findByReference(refTrim)
        if (refOwner != null && refOwner.id != produitId) {
            return ServiceResult.Erreur("La référence '$refTrim' appartient déjà à un autre produit.")
        }

        database.withTransaction {
            database.produitDao().update(
                existing.copy(
                    reference = refTrim,
                    nom = nomTrim,
                    categorieId = categorieId,
                    prixVente = prixVente,
                    seuilAlerte = seuilAlerte
                )
            )
            database.journalAuditDao().insert(
                JournalAuditEntity(
                    auteurId = admin.id,
                    dateHeureUtc = timeProvider(),
                    action = "MODIFICATION_PRODUIT",
                    entite = "Produit",
                    entiteId = produitId,
                    details = "Produit $refTrim modifié : nom='$nomTrim', prix=$prixVente FCFA, seuil=$seuilAlerte"
                )
            )
        }
        return ServiceResult.Succes(Unit, "Produit '$nomTrim' mis à jour.")
    }

    suspend fun setProductActive(produitId: Long, actif: Boolean): ServiceResult<Unit> {
        val admin = requireAdmin() ?: return ServiceResult.Erreur("Accès refusé : action réservée à l'administrateur.")
        val database = db()
        val existing = database.produitDao().findById(produitId)
            ?: return ServiceResult.Erreur("Produit introuvable.")
        database.withTransaction {
            database.produitDao().update(existing.copy(actif = actif))
            database.journalAuditDao().insert(
                JournalAuditEntity(
                    auteurId = admin.id,
                    dateHeureUtc = timeProvider(),
                    action = if (actif) "REACTIVATION_PRODUIT" else "ARCHIVAGE_PRODUIT",
                    entite = "Produit",
                    entiteId = produitId,
                    details = "${if (actif) "Réactivation" else "Archivage"} de ${existing.reference} (${existing.nom})"
                )
            )
        }
        return ServiceResult.Succes(Unit, if (actif) "Produit réactivé." else "Produit archivé.")
    }

    // --- Mouvements de stock & Inventaire ---

    suspend fun restockProduct(produitId: Long, quantite: Int, motif: String): ServiceResult<Int> {
        val admin = requireAdmin() ?: return ServiceResult.Erreur("Accès refusé : action réservée à l'administrateur.")
        if (quantite <= 0) return ServiceResult.Erreur("La quantité de réapprovisionnement doit être strictement positive.")
        val motifTrim = motif.trim()
        if (motifTrim.isEmpty()) return ServiceResult.Erreur("Le motif ou la référence de livraison est obligatoire.")

        val database = db()
        val produit = database.produitDao().findById(produitId)
            ?: return ServiceResult.Erreur("Produit introuvable.")
        if (!produit.actif) {
            return ServiceResult.Erreur("Le réapprovisionnement d'un produit archivé nécessite sa réactivation préalable.")
        }

        val nouveauStock = database.withTransaction {
            val now = timeProvider()
            database.mouvementStockDao().insert(
                MouvementStockEntity(
                    produitId = produitId,
                    auteurId = admin.id,
                    dateHeureUtc = now,
                    type = TypeMouvement.REAPPROVISIONNEMENT.name,
                    quantiteSignee = quantite,
                    motif = motifTrim
                )
            )
            val stock = database.produitDao().getStockDisponible(produitId)
            database.journalAuditDao().insert(
                JournalAuditEntity(
                    auteurId = admin.id,
                    dateHeureUtc = now,
                    action = "REAPPROVISIONNEMENT",
                    entite = "Produit",
                    entiteId = produitId,
                    details = "${produit.reference} : +$quantite unités ($motifTrim). Nouveau stock : $stock"
                )
            )
            stock
        }
        return ServiceResult.Succes(nouveauStock, "Stock augmenté de +$quantite (disponible : $nouveauStock).")
    }

    suspend fun inventoryCorrection(produitId: Long, quantiteComptee: Int, motif: String): ServiceResult<Int> {
        val admin = requireAdmin() ?: return ServiceResult.Erreur("Accès refusé : action réservée à l'administrateur.")
        if (quantiteComptee < 0) return ServiceResult.Erreur("La quantité comptée ne peut pas être négative.")
        val motifTrim = motif.trim()
        if (motifTrim.isEmpty()) return ServiceResult.Erreur("Le motif de correction d'inventaire est obligatoire.")

        val database = db()
        val produit = database.produitDao().findById(produitId)
            ?: return ServiceResult.Erreur("Produit introuvable.")

        var ancienStock = 0
        var ecart = 0
        val nouveauStock = database.withTransaction {
            ancienStock = database.produitDao().getStockDisponible(produitId)
            ecart = quantiteComptee - ancienStock
            if (ecart == 0) {
                return@withTransaction ancienStock
            }
            val now = timeProvider()
            database.mouvementStockDao().insert(
                MouvementStockEntity(
                    produitId = produitId,
                    auteurId = admin.id,
                    dateHeureUtc = now,
                    type = TypeMouvement.CORRECTION_INVENTAIRE.name,
                    quantiteSignee = ecart,
                    motif = "$motifTrim (Ancien : $ancienStock, Compté : $quantiteComptee, Écart : ${if (ecart > 0) "+$ecart" else ecart})"
                )
            )
            database.journalAuditDao().insert(
                JournalAuditEntity(
                    auteurId = admin.id,
                    dateHeureUtc = now,
                    action = "CORRECTION_INVENTAIRE",
                    entite = "Produit",
                    entiteId = produitId,
                    details = "${produit.reference} : ancien=$ancienStock, compté=$quantiteComptee, écart=$ecart ($motifTrim)"
                )
            )
            quantiteComptee
        }

        if (ecart == 0) {
            return ServiceResult.Succes(nouveauStock, "Aucun écart constaté (stock inchangé à $nouveauStock).")
        }
        return ServiceResult.Succes(
            nouveauStock,
            "Correction enregistrée (écart ${if (ecart > 0) "+$ecart" else ecart}) — stock à $nouveauStock."
        )
    }

    suspend fun getMouvementsForProduit(produitId: Long): List<MouvementAvecDetails> {
        return db().mouvementStockDao().getMouvementsForProduit(produitId)
    }

    // --- Validation atomique des ventes & Annulation ---

    private class PriceChangedRollbackException(
        val anciensPrix: Map<Long, Long>,
        val nouveauxPrix: Map<Long, Long>,
        val nouveauTotal: Long
    ) : RuntimeException()

    private class InsufficientStockRollbackException(
        val produitId: Long,
        val produitNom: String,
        val stockDisponible: Int,
        val quantiteDemandee: Int
    ) : RuntimeException()

    private class ValidationRollbackException(override val message: String) : RuntimeException(message)

    suspend fun validateSale(
        lignes: List<PanierLigneInput>,
        modePaiement: ModePaiement,
        montantRecuInput: Long,
        jetonValidation: String,
        onBeforeCommitHook: (() -> Unit)? = null
    ): ValidationVenteResult {
        val vendeur = _sessionUser.value
            ?: return ValidationVenteResult.Erreur("Session expirée. Veuillez vous reconnecter.")
        if (lignes.isEmpty()) {
            return ValidationVenteResult.Erreur("Le panier est vide.")
        }
        if (lignes.any { it.quantite <= 0 }) {
            return ValidationVenteResult.Erreur("Toutes les quantités doivent être strictement positives.")
        }

        val database = db()

        // Idempotence : si ce jeton a déjà été validé, renvoyer la vente existante sans créer de doublon
        val existingByToken = database.venteDao().findByJeton(jetonValidation)
        if (existingByToken != null) {
            val detail = getVenteDetail(existingByToken.id)
            if (detail != null) {
                return ValidationVenteResult.Succes(detail, dejaEnregistree = true)
            }
        }

        val params = getParametres()
        val now = timeProvider()
        val dateMetier = Formatters.todayDateMetier(params.fuseauHoraire, now)

        return try {
            val createdVenteId = database.withTransaction {
                // Double vérification idempotence à l'intérieur de la transaction
                val insideToken = database.venteDao().findByJeton(jetonValidation)
                if (insideToken != null) {
                    return@withTransaction insideToken.id
                }

                // Relecture des produits et stocks en transaction
                val anciensPrix = mutableMapOf<Long, Long>()
                val nouveauxPrix = mutableMapOf<Long, Long>()
                var prixModifie = false
                var totalCalcule = 0L

                val verifiedLines = mutableListOf<Triple<ProduitEntity, Int, Long>>()

                for (input in lignes) {
                    val produit = database.produitDao().findById(input.produitId)
                        ?: throw ValidationRollbackException("Produit introuvable : ${input.nom}")
                    if (!produit.actif) {
                        throw ValidationRollbackException("Le produit '${produit.nom}' est archivé et ne peut plus être vendu.")
                    }
                    if (produit.prixVente <= 0L) {
                        throw ValidationRollbackException("Prix invalide pour '${produit.nom}'.")
                    }

                    val stockDispo = database.produitDao().getStockDisponible(produit.id)
                    if (stockDispo < input.quantite) {
                        throw InsufficientStockRollbackException(
                            produitId = produit.id,
                            produitNom = produit.nom,
                            stockDisponible = stockDispo,
                            quantiteDemandee = input.quantite
                        )
                    }

                    anciensPrix[produit.id] = input.prixUnitaireAttendu
                    nouveauxPrix[produit.id] = produit.prixVente
                    if (produit.prixVente != input.prixUnitaireAttendu) {
                        prixModifie = true
                    }

                    val sousTotal = produit.prixVente * input.quantite
                    totalCalcule += sousTotal
                    verifiedLines.add(Triple(produit, input.quantite, sousTotal))
                }

                if (prixModifie) {
                    throw PriceChangedRollbackException(anciensPrix, nouveauxPrix, totalCalcule)
                }

                val montantRecu = when (modePaiement) {
                    ModePaiement.ESPECES -> {
                        if (montantRecuInput < totalCalcule) {
                            throw ValidationRollbackException(
                                "Somme remise insuffisante (${Formatters.formatMontant(montantRecuInput, params.devise)} < ${Formatters.formatMontant(totalCalcule, params.devise)})."
                            )
                        }
                        montantRecuInput
                    }
                    ModePaiement.MOBILE_MONEY, ModePaiement.CARTE -> totalCalcule
                }
                val monnaieRendue = if (modePaiement == ModePaiement.ESPECES) {
                    montantRecu - totalCalcule
                } else {
                    0L
                }

                val seq = database.venteDao().countByDateMetier(dateMetier) + 1
                val compactDate = dateMetier.replace("-", "")
                val numero = "V-$compactDate-${seq.toString().padStart(4, '0')}-${jetonValidation.take(4).uppercase()}"

                val venteId = database.venteDao().insertVente(
                    VenteEntity(
                        numero = numero,
                        jetonValidation = jetonValidation,
                        dateHeureUtc = now,
                        dateMetier = dateMetier,
                        vendeurId = vendeur.id,
                        total = totalCalcule,
                        modePaiement = modePaiement.name,
                        montantRecu = montantRecu,
                        monnaieRendue = monnaieRendue,
                        statut = StatutVente.VALIDEE.name
                    )
                )

                for ((produit, qte, sousTotal) in verifiedLines) {
                    val ligneId = database.venteDao().insertLigne(
                        LigneVenteEntity(
                            venteId = venteId,
                            produitId = produit.id,
                            referenceSnapshot = produit.reference,
                            nomSnapshot = produit.nom,
                            quantite = qte,
                            prixUnitaire = produit.prixVente,
                            sousTotal = sousTotal
                        )
                    )
                    database.mouvementStockDao().insert(
                        MouvementStockEntity(
                            produitId = produit.id,
                            auteurId = vendeur.id,
                            dateHeureUtc = now,
                            type = TypeMouvement.VENTE_VALIDEE.name,
                            quantiteSignee = -qte,
                            motif = "Vente $numero",
                            ligneVenteId = ligneId
                        )
                    )
                }

                database.journalAuditDao().insert(
                    JournalAuditEntity(
                        auteurId = vendeur.id,
                        dateHeureUtc = now,
                        action = "VENTE_VALIDEE",
                        entite = "Vente",
                        entiteId = venteId,
                        details = "Vente $numero (${verifiedLines.size} lignes) : $totalCalcule FCFA (${modePaiement.libelle})"
                    )
                )

                // Point d'injection pour test de rollback (T16)
                onBeforeCommitHook?.invoke()

                venteId
            }

            val detail = getVenteDetail(createdVenteId)!!
            ValidationVenteResult.Succes(detail, dejaEnregistree = existingByToken != null)
        } catch (e: PriceChangedRollbackException) {
            ValidationVenteResult.PrixModifie(
                anciensPrix = e.anciensPrix,
                nouveauxPrix = e.nouveauxPrix,
                nouveauTotal = e.nouveauTotal,
                message = "Le prix catalogue d'un ou plusieurs produits a changé. Nouveau total : ${Formatters.formatMontant(e.nouveauTotal, params.devise)}. Veuillez confirmer."
            )
        } catch (e: InsufficientStockRollbackException) {
            ValidationVenteResult.StockInsuffisant(
                produitId = e.produitId,
                produitNom = e.produitNom,
                stockDisponible = e.stockDisponible,
                quantiteDemandee = e.quantiteDemandee
            )
        } catch (e: ValidationRollbackException) {
            ValidationVenteResult.Erreur(e.message)
        } catch (e: Exception) {
            ValidationVenteResult.Erreur("Erreur lors de l'enregistrement de la vente : ${e.message ?: "transaction annulée"}")
        }
    }

    suspend fun getVenteDetail(venteId: Long): VenteDetailComplet? {
        val currentUser = _sessionUser.value ?: return null
        val database = db()
        val vente = database.venteDao().getVenteAvecVendeur(venteId) ?: return null
        if (currentUser.role != RoleUtilisateur.ADMINISTRATEUR.name && vente.vendeurId != currentUser.id) {
            return null
        }
        val lignes = database.venteDao().getLignesForVente(venteId)
        val annulation = database.venteDao().getAnnulationForVente(venteId)
        return VenteDetailComplet(vente = vente, lignes = lignes, annulation = annulation)
    }

    suspend fun cancelSale(venteId: Long, motif: String): ServiceResult<VenteDetailComplet> {
        val admin = requireAdmin() ?: return ServiceResult.Erreur("Accès refusé : l'annulation est réservée à l'administrateur.")
        val motifTrim = motif.trim()
        if (motifTrim.isEmpty()) {
            return ServiceResult.Erreur("Un motif d'annulation est obligatoire.")
        }

        val database = db()
        val now = timeProvider()
        return try {
            database.withTransaction {
                val vente = database.venteDao().findById(venteId)
                    ?: throw ValidationRollbackException("Vente introuvable.")
                if (vente.statut == StatutVente.ANNULEE.name || database.venteDao().getAnnulationForVente(venteId) != null) {
                    throw ValidationRollbackException("Cette vente a déjà été annulée.")
                }
                val lignes = database.venteDao().getLignesForVente(venteId)
                if (lignes.isEmpty()) {
                    throw ValidationRollbackException("Aucune ligne associée à cette vente.")
                }

                val annulationId = database.venteDao().insertAnnulation(
                    AnnulationVenteEntity(
                        venteId = venteId,
                        auteurId = admin.id,
                        dateHeureUtc = now,
                        motif = motifTrim
                    )
                )

                database.venteDao().updateVente(vente.copy(statut = StatutVente.ANNULEE.name))

                for (ligne in lignes) {
                    database.mouvementStockDao().insert(
                        MouvementStockEntity(
                            produitId = ligne.produitId,
                            auteurId = admin.id,
                            dateHeureUtc = now,
                            type = TypeMouvement.ANNULATION_VENTE.name,
                            quantiteSignee = ligne.quantite,
                            motif = "Annulation vente ${vente.numero} : $motifTrim",
                            ligneVenteId = ligne.id,
                            annulationId = annulationId
                        )
                    )
                }

                database.journalAuditDao().insert(
                    JournalAuditEntity(
                        auteurId = admin.id,
                        dateHeureUtc = now,
                        action = "ANNULATION_VENTE",
                        entite = "Vente",
                        entiteId = venteId,
                        details = "Annulation intégrale de ${vente.numero} (${vente.total} FCFA) : $motifTrim"
                    )
                )
            }
            val updated = getVenteDetail(venteId)!!
            ServiceResult.Succes(updated, "Vente ${updated.vente.numero} annulée et stocks rétablis.")
        } catch (e: ValidationRollbackException) {
            ServiceResult.Erreur(e.message)
        } catch (e: Exception) {
            ServiceResult.Erreur("Impossible d'annuler la vente : ${e.message}")
        }
    }

    // --- Rapports & Exports CSV ---

    suspend fun getRapportPeriode(dateDebut: String, dateFin: String): ServiceResult<List<RapportProduitLigne>> {
        requireAdmin() ?: return ServiceResult.Erreur("Accès refusé : les rapports globaux sont réservés à l'administrateur.")
        if (dateDebut > dateFin) return ServiceResult.Erreur("La date de début doit précéder la date de fin.")
        return ServiceResult.Succes(db().venteDao().getRapportProduits(dateDebut, dateFin))
    }

    suspend fun getCancelledSummaryOnPeriod(dateDebut: String, dateFin: String): ServiceResult<Pair<Int, Long>> {
        requireAdmin() ?: return ServiceResult.Erreur("Accès refusé.")
        if (dateDebut > dateFin) return ServiceResult.Succes(0 to 0L)
        val cancelled = db().venteDao().getVentesSurPeriode(dateDebut, dateFin)
            .filter { it.statut == StatutVente.ANNULEE.name }
        return ServiceResult.Succes(cancelled.size to cancelled.sumOf { it.total })
    }

    suspend fun exportSalesCsv(
        ventes: List<VenteAvecVendeur>,
        outputStream: OutputStream
    ): ServiceResult<Unit> {
        requireAdmin() ?: return ServiceResult.Erreur("Accès refusé : export réservé à l'administrateur.")
        val params = getParametres()
        val header = listOf("Numéro", "Date métier", "Date/Heure", "Vendeur", "Mode paiement", "Total (${params.devise})", "Reçu", "Monnaie", "Statut")
        val rows = ventes.map { v ->
            listOf(
                Formatters.escapeCsvTextCell(v.numero),
                Formatters.escapeCsvTextCell(v.dateMetier),
                Formatters.escapeCsvTextCell(Formatters.formatDateTime(v.dateHeureUtc, params.fuseauHoraire)),
                Formatters.escapeCsvTextCell(v.vendeurNom),
                Formatters.escapeCsvTextCell(ModePaiement.fromString(v.modePaiement).libelle),
                Formatters.escapeCsvNumericCell(v.total),
                Formatters.escapeCsvNumericCell(v.montantRecu),
                Formatters.escapeCsvNumericCell(v.monnaieRendue),
                Formatters.escapeCsvTextCell(StatutVente.fromString(v.statut).libelle)
            )
        }
        return writeCsvStream(Formatters.buildCsvWithBom(header, rows), outputStream)
    }

    suspend fun exportStockCsv(
        produits: List<ProduitAvecStock>,
        outputStream: OutputStream
    ): ServiceResult<Unit> {
        requireAdmin() ?: return ServiceResult.Erreur("Accès refusé : export réservé à l'administrateur.")
        val params = getParametres()
        val header = listOf("Référence", "Nom", "Catégorie", "Prix (${params.devise})", "Stock disponible", "Seuil alerte", "Statut", "État catalogue")
        val rows = produits.map { p ->
            listOf(
                Formatters.escapeCsvTextCell(p.reference),
                Formatters.escapeCsvTextCell(p.nom),
                Formatters.escapeCsvTextCell(p.categorieNom ?: "Sans catégorie"),
                Formatters.escapeCsvNumericCell(p.prixVente),
                Formatters.escapeCsvNumericCell(p.stockDisponible),
                Formatters.escapeCsvNumericCell(p.seuilAlerte),
                Formatters.escapeCsvTextCell(p.statut.libelle),
                Formatters.escapeCsvTextCell(if (p.actif) "Actif" else "Archivé")
            )
        }
        return writeCsvStream(Formatters.buildCsvWithBom(header, rows), outputStream)
    }

    suspend fun exportReportCsv(
        lignes: List<RapportProduitLigne>,
        dateDebut: String,
        dateFin: String,
        outputStream: OutputStream
    ): ServiceResult<Unit> {
        requireAdmin() ?: return ServiceResult.Erreur("Accès refusé : export réservé à l'administrateur.")
        val params = getParametres()
        val header = listOf("Période", "Référence", "Produit", "Unités vendues", "Montant (${params.devise})")
        val periode = "$dateDebut au $dateFin"
        val rows = lignes.map { r ->
            listOf(
                Formatters.escapeCsvTextCell(periode),
                Formatters.escapeCsvTextCell(r.reference),
                Formatters.escapeCsvTextCell(r.nom),
                Formatters.escapeCsvNumericCell(r.unitesVendues),
                Formatters.escapeCsvNumericCell(r.montantVendu)
            )
        }
        return writeCsvStream(Formatters.buildCsvWithBom(header, rows), outputStream)
    }

    private fun writeCsvStream(content: String, outputStream: OutputStream): ServiceResult<Unit> {
        return try {
            outputStream.use { out ->
                out.write(content.toByteArray(Charsets.UTF_8))
                out.flush()
            }
            ServiceResult.Succes(Unit, "Export CSV enregistré.")
        } catch (e: Exception) {
            ServiceResult.Erreur("Erreur d'export CSV : ${e.message}")
        }
    }

    // --- Paramètres, Sauvegardes & Restauration ---

    suspend fun updateStoreSettings(params: ParametresMagasin): ServiceResult<Unit> {
        val admin = requireAdmin() ?: return ServiceResult.Erreur("Accès refusé : action réservée à l'administrateur.")
        if (params.nomCommercial.trim().isEmpty()) {
            return ServiceResult.Erreur("Le nom commercial du magasin est obligatoire.")
        }
        val database = db()
        database.withTransaction {
            database.parametreDao().upsertAll(
                listOf(
                    ParametreEntity("magasin_nom", params.nomCommercial.trim()),
                    ParametreEntity("magasin_adresse", params.adresse.trim()),
                    ParametreEntity("magasin_telephone", params.telephone.trim()),
                    ParametreEntity("devise", params.devise.ifBlank { "XAF" }),
                    ParametreEntity("fuseau_horaire", params.fuseauHoraire.ifBlank { "Africa/Brazzaville" }),
                    ParametreEntity("logo_uri", params.logoUri),
                    ParametreEntity("sauvegarde_auto_active", params.sauvegardeAutoActive.toString())
                )
            )
            database.journalAuditDao().insert(
                JournalAuditEntity(
                    auteurId = admin.id,
                    dateHeureUtc = timeProvider(),
                    action = "MODIFICATION_PARAMETRES",
                    entite = "Parametre",
                    entiteId = null,
                    details = "Paramètres magasin mis à jour (${params.nomCommercial.trim()}, ${params.fuseauHoraire})"
                )
            )
        }
        return ServiceResult.Succes(Unit, "Paramètres du magasin enregistrés.")
    }

    suspend fun listBackups(): ServiceResult<List<SauvegardeInfo>> {
        requireAdmin() ?: return ServiceResult.Erreur("Accès refusé : sauvegardes réservées à l'administrateur.")
        return ServiceResult.Succes(dbHolder.listBackups())
    }

    suspend fun createManualBackup(): ServiceResult<SauvegardeInfo> {
        val admin = requireAdmin() ?: return ServiceResult.Erreur("Accès refusé : sauvegardes réservées à l'administrateur.")
        val result = dbHolder.createBackup(isAutomatic = false)
        if (result is ServiceResult.Succes) {
            db().journalAuditDao().insert(
                JournalAuditEntity(
                    auteurId = admin.id,
                    dateHeureUtc = timeProvider(),
                    action = "SAUVEGARDE_MANUELLE",
                    entite = "Sauvegarde",
                    entiteId = null,
                    details = "Sauvegarde manuelle créée : ${result.data.nomFichier}"
                )
            )
        }
        return result
    }

    suspend fun exportBackupFile(backupPath: String, outputStream: OutputStream): ServiceResult<Unit> {
        requireAdmin() ?: return ServiceResult.Erreur("Accès refusé : action réservée à l'administrateur.")
        return dbHolder.exportBackupToStream(File(backupPath), outputStream)
    }

    suspend fun restoreFromLocalBackup(backupPath: String): ServiceResult<String> {
        val admin = requireAdmin() ?: return ServiceResult.Erreur("Accès refusé : restauration réservée à l'administrateur.")
        val result = dbHolder.restoreFromBackupFile(File(backupPath))
        if (result is ServiceResult.Succes) {
            runCatching {
                db().journalAuditDao().insert(
                    JournalAuditEntity(
                        auteurId = null,
                        dateHeureUtc = timeProvider(),
                        action = "RESTAURATION_BASE",
                        entite = "Sauvegarde",
                        entiteId = null,
                        details = "Base restaurée par ${admin.identifiant} depuis ${result.data}"
                    )
                )
            }
            _sessionUser.value = null
        }
        return result
    }

    suspend fun restoreFromExternalStream(inputStream: InputStream): ServiceResult<String> {
        val admin = requireAdmin() ?: return ServiceResult.Erreur("Accès refusé : restauration réservée à l'administrateur.")
        val tempFile = dbHolder.copyStreamToTempBackupCandidate(inputStream)
        return try {
            val result = dbHolder.restoreFromBackupFile(tempFile)
            if (result is ServiceResult.Succes) {
                runCatching {
                    db().journalAuditDao().insert(
                        JournalAuditEntity(
                            auteurId = null,
                            dateHeureUtc = timeProvider(),
                            action = "RESTAURATION_BASE_EXTERNE",
                            entite = "Sauvegarde",
                            entiteId = null,
                            details = "Base restaurée par ${admin.identifiant} depuis fichier externe"
                        )
                    )
                }
                _sessionUser.value = null
            }
            result
        } finally {
            tempFile.delete()
        }
    }

    suspend fun runStartupAutoBackupIfDue() {
        val params = getParametres()
        val today = Formatters.todayDateMetier(params.fuseauHoraire, timeProvider())
        dbHolder.runDailyAutoBackupIfDue(today)
    }

    suspend fun loadDemoDataset(): ServiceResult<Unit> {
        val admin = requireAdmin() ?: return ServiceResult.Erreur("Accès refusé : action réservée à l'administrateur.")
        return seedDemoDataInternal(admin.id)
    }

    suspend fun resetDatabaseToBlank(): ServiceResult<String> {
        requireAdmin() ?: return ServiceResult.Erreur("Accès refusé : action réservée à l'administrateur.")
        val result = dbHolder.resetToFreshDatabase()
        if (result is ServiceResult.Succes) {
            _sessionUser.value = null
        }
        return result
    }

    private suspend fun seedDemoDataInternal(adminId: Long): ServiceResult<Unit> {
        val database = db()
        val now = timeProvider()
        database.withTransaction {
            val catAliment = database.categorieDao().findByNom("Alimentation")?.id
                ?: database.categorieDao().insert(CategorieEntity(nom = "Alimentation"))
            val catBoissons = database.categorieDao().findByNom("Boissons")?.id
                ?: database.categorieDao().insert(CategorieEntity(nom = "Boissons"))
            val catHygiene = database.categorieDao().findByNom("Hygiène")?.id
                ?: database.categorieDao().insert(CategorieEntity(nom = "Hygiène"))
            val catPapeterie = database.categorieDao().findByNom("Papeterie")?.id
                ?: database.categorieDao().insert(CategorieEntity(nom = "Papeterie"))

            val demoItems = listOf(
                DemoProd("AL-001", "Riz Parfumé 5 kg", catAliment, 4500L, 5, 18),
                DemoProd("AL-002", "Huile Végétale 1 L", catAliment, 1600L, 6, 4),
                DemoProd("BO-001", "Eau Minérale 1,5 L", catBoissons, 500L, 10, 32),
                DemoProd("BO-002", "Jus d'Ananas 1 L", catBoissons, 1200L, 5, 0),
                DemoProd("HY-001", "Savon Ménage 250 g", catHygiene, 650L, 8, 12),
                DemoProd("HY-002", "Dentifrice Menthe", catHygiene, 900L, 4, 3),
                DemoProd("PA-001", "Cahier 200 pages A4", catPapeterie, 850L, 5, 25),
                DemoProd("PA-002", "Stylo Bille Bleu", catPapeterie, 150L, 15, 0)
            )
            for (d in demoItems) {
                if (database.produitDao().findByReference(d.ref) == null) {
                    val pId = database.produitDao().insert(
                        ProduitEntity(
                            reference = d.ref,
                            nom = d.nom,
                            categorieId = d.catId,
                            prixVente = d.prix,
                            seuilAlerte = d.seuil,
                            actif = true,
                            dateCreationUtc = now
                        )
                    )
                    if (d.stock > 0) {
                        database.mouvementStockDao().insert(
                            MouvementStockEntity(
                                produitId = pId,
                                auteurId = adminId,
                                dateHeureUtc = now,
                                type = TypeMouvement.STOCK_INITIAL.name,
                                quantiteSignee = d.stock,
                                motif = "Stock initial démonstration"
                            )
                        )
                    }
                }
            }
            database.journalAuditDao().insert(
                JournalAuditEntity(
                    auteurId = adminId,
                    dateHeureUtc = now,
                    action = "CHARGEMENT_DEMO",
                    entite = "Produit",
                    entiteId = null,
                    details = "Catalogue de démonstration chargé"
                )
            )
        }
        return ServiceResult.Succes(Unit, "Catalogue de démonstration prêt.")
    }

    private data class DemoProd(
        val ref: String,
        val nom: String,
        val catId: Long,
        val prix: Long,
        val seuil: Int,
        val stock: Int
    )

    private fun requireAdmin(): UtilisateurEntity? {
        val current = _sessionUser.value ?: return null
        return if (current.role == RoleUtilisateur.ADMINISTRATEUR.name && current.actif) current else null
    }

    private fun mapToParametres(rows: List<ParametreEntity>): ParametresMagasin {
        val map = rows.associate { it.cle to it.valeur }
        return ParametresMagasin(
            nomCommercial = map["magasin_nom"]?.takeIf { it.isNotBlank() } ?: "Mon Magasin",
            adresse = map["magasin_adresse"] ?: "",
            telephone = map["magasin_telephone"] ?: "",
            devise = map["devise"]?.takeIf { it.isNotBlank() } ?: "XAF",
            fuseauHoraire = map["fuseau_horaire"]?.takeIf { it.isNotBlank() } ?: "Africa/Brazzaville",
            logoUri = map["logo_uri"] ?: "",
            sauvegardeAutoActive = map["sauvegarde_auto_active"] != "false",
            derniereSauvegardeAutoDate = map["derniere_sauvegarde_auto_date"] ?: "",
            derniereSauvegardeAutoStatut = map["derniere_sauvegarde_auto_statut"] ?: ""
        )
    }
}
