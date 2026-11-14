package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import com.example.domain.DatabaseHolder
import com.example.domain.ReceiptPrinter
import com.example.domain.StoreRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CahierDesChargesAcceptanceTest {

    private lateinit var context: Context
    private lateinit var dbName: String
    private lateinit var dbHolder: DatabaseHolder
    private var fakeNowMillis: Long = 1_794_650_000_000L // ~14 nov 2026
    private lateinit var repo: StoreRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dbName = "test_magasin_${UUID.randomUUID()}.db"
        dbHolder = DatabaseHolder(context, dbName = dbName, inMemory = false)
        repo = StoreRepository(dbHolder) { fakeNowMillis }
    }

    @After
    fun tearDown() {
        dbHolder.close()
        context.deleteDatabase(dbName)
        File(context.filesDir, "backups").deleteRecursively()
    }

    private suspend fun setupAdmin(): UtilisateurEntity {
        val res = repo.createInitialAdmin("Admin Test", "admin", "secret123", avecDonneesDemo = false)
        assertTrue(res is ServiceResult.Succes)
        return (res as ServiceResult.Succes).data
    }

    @Test
    fun `T01_T02_creation_produit_stock_initial_et_reference_dupliquee`() = runBlocking {
        setupAdmin()

        // T01: Créer un produit avec 10 unités
        val res1 = repo.createProduct("REF-001", "Savon Extra", null, 2000L, seuilAlerte = 5, stockInitial = 10)
        assertTrue(res1 is ServiceResult.Succes)
        val prod = (res1 as ServiceResult.Succes).data
        assertEquals(10, prod.stockDisponible)
        val mvts = repo.getMouvementsForProduit(prod.id)
        assertEquals(1, mvts.size)
        assertEquals(TypeMouvement.STOCK_INITIAL.name, mvts[0].type)
        assertEquals(10, mvts[0].quantiteSignee)

        // T02: Créer une référence déjà utilisée
        val res2 = repo.createProduct("ref-001", "Doublon", null, 1500L, seuilAlerte = 2, stockInitial = 0)
        assertTrue(res2 is ServiceResult.Erreur)
    }

    @Test
    fun `T03_T04_T05_T06_cycle_ventes_rupture_reapprovisionnement_et_inventaire`() = runBlocking {
        setupAdmin()
        val prod = (repo.createProduct("P-100", "Riz 5kg", null, 2000L, seuilAlerte = 5, stockInitial = 10) as ServiceResult.Succes).data

        // T03: Vendre 3 unités à 2 000 FCFA -> vente 6 000 FCFA, stock à 7
        val v1 = repo.validateSale(
            listOf(PanierLigneInput(prod.id, prod.reference, prod.nom, 2000L, 3)),
            ModePaiement.MOBILE_MONEY,
            6000L,
            UUID.randomUUID().toString()
        )
        assertTrue(v1 is ValidationVenteResult.Succes)
        assertEquals(6000L, (v1 as ValidationVenteResult.Succes).vente.vente.total)
        assertEquals(7, repo.produitsAvecStockFlow.first().first { it.id == prod.id }.stockDisponible)

        // T04: Vendre 8 unités avec stock à 7 -> bloquée, aucune écriture partielle
        val v2 = repo.validateSale(
            listOf(PanierLigneInput(prod.id, prod.reference, prod.nom, 2000L, 8)),
            ModePaiement.CARTE,
            16000L,
            UUID.randomUUID().toString()
        )
        assertTrue(v2 is ValidationVenteResult.StockInsuffisant)
        assertEquals(7, repo.produitsAvecStockFlow.first().first { it.id == prod.id }.stockDisponible)
        assertEquals(1, repo.ventesVisibleFlow.first().size)

        // T05: Vendre les 7 unités restantes -> Stock à 0, En rupture, nouvelle vente bloquée
        val v3 = repo.validateSale(
            listOf(PanierLigneInput(prod.id, prod.reference, prod.nom, 2000L, 7)),
            ModePaiement.ESPECES,
            15000L,
            UUID.randomUUID().toString()
        )
        assertTrue(v3 is ValidationVenteResult.Succes)
        assertEquals(1000L, (v3 as ValidationVenteResult.Succes).vente.vente.monnaieRendue)
        val afterRupture = repo.produitsAvecStockFlow.first().first { it.id == prod.id }
        assertEquals(0, afterRupture.stockDisponible)
        assertEquals(StatutDisponibilite.EN_RUPTURE, afterRupture.statut)

        val vBlocked = repo.validateSale(
            listOf(PanierLigneInput(prod.id, prod.reference, prod.nom, 2000L, 1)),
            ModePaiement.CARTE,
            2000L,
            UUID.randomUUID().toString()
        )
        assertTrue(vBlocked is ValidationVenteResult.StockInsuffisant)

        // T06: Réapprovisionner de 5, seuil à 5 -> Stock à 5, Stock faible
        val restock = repo.restockProduct(prod.id, 5, "BL-042")
        assertTrue(restock is ServiceResult.Succes)
        val afterRestock = repo.produitsAvecStockFlow.first().first { it.id == prod.id }
        assertEquals(5, afterRestock.stockDisponible)
        assertEquals(StatutDisponibilite.STOCK_FAIBLE, afterRestock.statut)

        // Exemple de contrôle page 3 : correction d'inventaire (5 -> 4 crée mouvement -1)
        val corr = repo.inventoryCorrection(prod.id, 4, "Casse constatée")
        assertTrue(corr is ServiceResult.Succes)
        assertEquals(4, (corr as ServiceResult.Succes).data)
        val mvts = repo.getMouvementsForProduit(prod.id)
        assertEquals(-1, mvts.first().quantiteSignee)
    }

    @Test
    fun `T07_T08_T12_T16_panier_invalide_idempotence_especes_et_rollback`() = runBlocking {
        setupAdmin()
        val p1 = (repo.createProduct("A1", "Article 1", null, 1000L, 2, 10) as ServiceResult.Succes).data
        val p2 = (repo.createProduct("A2", "Article 2", null, 500L, 2, 1) as ServiceResult.Succes).data

        // T07: Une ligne valide + une ligne en dépassement de stock -> rien enregistré
        val invalidSale = repo.validateSale(
            listOf(
                PanierLigneInput(p1.id, p1.reference, p1.nom, 1000L, 2),
                PanierLigneInput(p2.id, p2.reference, p2.nom, 500L, 3)
            ),
            ModePaiement.CARTE,
            3500L,
            UUID.randomUUID().toString()
        )
        assertTrue(invalidSale is ValidationVenteResult.StockInsuffisant)
        assertEquals(10, repo.produitsAvecStockFlow.first().first { it.id == p1.id }.stockDisponible)
        assertEquals(0, repo.ventesVisibleFlow.first().size)

        // T12: Espèces insuffisant bloqué, puis suffisant accepté avec monnaie exacte
        val cashTooLow = repo.validateSale(
            listOf(PanierLigneInput(p1.id, p1.reference, p1.nom, 1000L, 2)),
            ModePaiement.ESPECES,
            1500L,
            UUID.randomUUID().toString()
        )
        assertTrue(cashTooLow is ValidationVenteResult.Erreur)

        val token = UUID.randomUUID().toString()
        val cashOk = repo.validateSale(
            listOf(PanierLigneInput(p1.id, p1.reference, p1.nom, 1000L, 2)),
            ModePaiement.ESPECES,
            5000L,
            token
        )
        assertTrue(cashOk is ValidationVenteResult.Succes)
        assertEquals(3000L, (cashOk as ValidationVenteResult.Succes).vente.vente.monnaieRendue)

        // T08: Double-clic avec même jetonValidation -> aucune deuxième vente ni deuxième sortie
        val duplicateClick = repo.validateSale(
            listOf(PanierLigneInput(p1.id, p1.reference, p1.nom, 1000L, 2)),
            ModePaiement.ESPECES,
            5000L,
            token
        )
        assertTrue(duplicateClick is ValidationVenteResult.Succes)
        assertTrue((duplicateClick as ValidationVenteResult.Succes).dejaEnregistree)
        assertEquals(8, repo.produitsAvecStockFlow.first().first { it.id == p1.id }.stockDisponible)
        assertEquals(1, repo.ventesVisibleFlow.first().size)

        // T16: Simuler une erreur de base pendant la validation -> transaction annulée
        val rollbackSale = repo.validateSale(
            listOf(PanierLigneInput(p1.id, p1.reference, p1.nom, 1000L, 2)),
            ModePaiement.CARTE,
            2000L,
            UUID.randomUUID().toString(),
            onBeforeCommitHook = { throw IllegalStateException("Erreur SQLite simulée") }
        )
        assertTrue(rollbackSale is ValidationVenteResult.Erreur)
        assertEquals(8, repo.produitsAvecStockFlow.first().first { it.id == p1.id }.stockDisponible)
        assertEquals(1, repo.ventesVisibleFlow.first().size)
    }

    @Test
    fun `T09_T10_T11_annulation_unique_snapshot_prix_et_rapports_multi_jours`() = runBlocking {
        setupAdmin()
        val prod = (repo.createProduct("S-01", "Sucre 1kg", null, 1000L, 2, 20) as ServiceResult.Succes).data

        // Jour 1 : vente de 4 unités à 1 000 FCFA
        val jour1Start = 1_794_650_000_000L
        fakeNowMillis = jour1Start
        val sale1 = (repo.validateSale(
            listOf(PanierLigneInput(prod.id, prod.reference, prod.nom, 1000L, 4)),
            ModePaiement.CARTE,
            4000L,
            UUID.randomUUID().toString()
        ) as ValidationVenteResult.Succes).vente
        val jour1Date = sale1.vente.dateMetier

        // T10: Changer prix et nom du produit -> ancien ticket inchangé
        repo.updateProduct(prod.id, "S-01", "Sucre Roux 1kg", null, 1500L, 2)
        val oldTicket = repo.getVenteDetail(sale1.vente.id)!!
        assertEquals("Sucre 1kg", oldTicket.lignes[0].nomSnapshot)
        assertEquals(1000L, oldTicket.lignes[0].prixUnitaire)
        assertEquals(4000L, oldTicket.vente.total)

        // Relecture prix si panier avait encore l'ancien prix 1000L
        val priceChanged = repo.validateSale(
            listOf(PanierLigneInput(prod.id, "S-01", "Sucre 1kg", 1000L, 2)),
            ModePaiement.CARTE,
            2000L,
            UUID.randomUUID().toString()
        )
        assertTrue(priceChanged is ValidationVenteResult.PrixModifie)
        assertEquals(3000L, (priceChanged as ValidationVenteResult.PrixModifie).nouveauTotal)

        // Jour 2 (+24h) : nouvelle vente au nouveau prix 1500L
        fakeNowMillis = jour1Start + 86_400_000L
        val sale2 = (repo.validateSale(
            listOf(PanierLigneInput(prod.id, "S-01", "Sucre Roux 1kg", 1500L, 2)),
            ModePaiement.CARTE,
            3000L,
            UUID.randomUUID().toString()
        ) as ValidationVenteResult.Succes).vente
        val jour2Date = sale2.vente.dateMetier
        assertNotEquals(jour1Date, jour2Date)

        // T09 + T11: Annuler sale1 au Jour 2 -> stock rétabli (+4), jour 1 recalculé à 0, seconde annulation refusée
        val cancel1 = repo.cancelSale(sale1.vente.id, "Erreur de saisie client")
        assertTrue(cancel1 is ServiceResult.Succes)
        assertEquals(18, repo.produitsAvecStockFlow.first().first { it.id == prod.id }.stockDisponible)

        val cancel2 = repo.cancelSale(sale1.vente.id, "Deuxième essai")
        assertTrue(cancel2 is ServiceResult.Erreur)

        val rapportJour1 = (repo.getRapportPeriode(jour1Date, jour1Date) as ServiceResult.Succes).data
        assertTrue(rapportJour1.isEmpty())

        val rapportJour2 = (repo.getRapportPeriode(jour2Date, jour2Date) as ServiceResult.Succes).data
        assertEquals(1, rapportJour2.size)
        assertEquals(2, rapportJour2[0].unitesVendues)
        assertEquals(3000L, rapportJour2[0].montantVendu)
    }

    @Test
    fun `T13_T14_T15_persistance_droits_vendeur_et_sauvegarde_restauration`() = runBlocking {
        setupAdmin()
        val prod = (repo.createProduct("BK-1", "Café 250g", null, 3000L, 3, 12) as ServiceResult.Succes).data
        val sale = (repo.validateSale(
            listOf(PanierLigneInput(prod.id, prod.reference, prod.nom, 3000L, 2)),
            ModePaiement.ESPECES,
            6000L,
            UUID.randomUUID().toString()
        ) as ValidationVenteResult.Succes).vente

        // T15a: Créer une sauvegarde valide avant modification
        val backupRes = repo.createManualBackup()
        assertTrue(backupRes is ServiceResult.Succes)
        val backupPath = (backupRes as ServiceResult.Succes).data.cheminAbsolu

        // Créer un utilisateur Vendeur
        val createVendeur = repo.createUser("Jean Vendeur", "vendeur1", "pass123", RoleUtilisateur.VENDEUR)
        assertTrue(createVendeur is ServiceResult.Succes)

        // T14: Se connecter comme vendeur et vérifier le refus côté service métier
        repo.logout()
        val loginVendeur = repo.login("vendeur1", "pass123")
        assertTrue(loginVendeur is ServiceResult.Succes)
        assertTrue(repo.createProduct("X1", "Interdit", null, 500L, 1, 1) is ServiceResult.Erreur)
        assertTrue(repo.restockProduct(prod.id, 5, "Test") is ServiceResult.Erreur)
        assertTrue(repo.cancelSale(sale.vente.id, "Test") is ServiceResult.Erreur)
        assertTrue(repo.getRapportPeriode("2026-01-01", "2026-12-31") is ServiceResult.Erreur)
        assertTrue(repo.createManualBackup() is ServiceResult.Erreur)

        // Reconnecter en Admin
        repo.logout()
        assertTrue(repo.login("admin", "secret123") is ServiceResult.Succes)

        // T15b: Tester une copie invalide -> refusée sans perte de la base active
        val invalidFile = File(context.cacheDir, "corrupt_backup.db").apply {
            writeText("NOT_A_SQLITE_FILE_1234567890_INVALID_CONTENT_00000000000000000000000000000000000000000000")
        }
        val badRestore = repo.restoreFromLocalBackup(invalidFile.absolutePath)
        assertTrue(badRestore is ServiceResult.Erreur)
        invalidFile.delete()

        // Restaurer la sauvegarde valide (qui ne contenait pas encore vendeur1)
        val goodRestore = repo.restoreFromLocalBackup(backupPath)
        assertTrue(goodRestore is ServiceResult.Succes)
        // Après restauration, la session est déconnectée (retour connexion)
        assertNull(repo.sessionUser.value)

        // T13: Reconnexion après restauration et vérification persistance + ticket réimprimable
        assertTrue(repo.login("admin", "secret123") is ServiceResult.Succes)
        assertEquals(10, repo.produitsAvecStockFlow.first().first { it.id == prod.id }.stockDisponible)
        val restoredDetail = repo.getVenteDetail(sale.vente.id)
        assertNotNull(restoredDetail)
        val ticketText = ReceiptPrinter.buildPlainTextReceipt(restoredDetail!!, repo.getParametres())
        assertTrue(ticketText.contains("Café 250g"))
        assertTrue(ticketText.contains("6 000 FCFA") || ticketText.contains("6\u00A0000 FCFA"))
    }

    @Test
    fun `T18_export_csv_avec_accents_et_protection_anti_formules`() = runBlocking {
        setupAdmin()
        repo.createProduct("CSV-1", "=HYPERLINK(\"http://evil\")", null, 1200L, 2, 5)
        repo.createProduct("CSV-2", "Café Crème Épicé", null, 800L, 2, 10)

        val produits = repo.produitsAvecStockFlow.first()
        val out = ByteArrayOutputStream()
        val exportRes = repo.exportStockCsv(produits, out)
        assertTrue(exportRes is ServiceResult.Succes)

        val bytes = out.toByteArray()
        // Vérification BOM UTF-8
        assertEquals(0xEF.toByte(), bytes[0])
        assertEquals(0xBB.toByte(), bytes[1])
        assertEquals(0xBF.toByte(), bytes[2])

        val csvContent = String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        assertTrue(csvContent.contains("Café Crème Épicé"))
        // La cellule commençant par '=' doit être préfixée par '\''
        assertTrue(csvContent.contains("\"'=HYPERLINK(\"\"http://evil\"\")\""))
    }

    @Test
    fun `reinitialisation_base_vierge_cree_sauvegarde_et_revient_a_installation_initiale`() = runBlocking {
        setupAdmin()
        repo.createProduct("RST-1", "Article Test", null, 500L, 1, 5)
        assertTrue(repo.hasAnyUser())

        val resetRes = repo.resetDatabaseToBlank()
        assertTrue(resetRes is ServiceResult.Succes)
        assertNull(repo.sessionUser.value)
        assertFalse(repo.hasAnyUser())
        assertTrue(repo.produitsAvecStockFlow.first().isEmpty())
        assertTrue(dbHolder.listBackups().isNotEmpty())
    }

    @Test
    fun `rapport_complet_agrege_paiements_jours_panier_moyen_et_exclut_annulations`() = runBlocking {
        setupAdmin()
        val p1 = (repo.createProduct("RP-1", "Eau 1.5L", null, 500L, 5, 50) as ServiceResult.Succes).data
        val p2 = (repo.createProduct("RP-2", "Jus 1L", null, 1500L, 5, 50) as ServiceResult.Succes).data

        val jour1 = fakeNowMillis
        // Vente 1 : 2 x Eau (1000) Espèces
        val s1 = (repo.validateSale(
            listOf(PanierLigneInput(p1.id, p1.reference, p1.nom, 500L, 2)),
            ModePaiement.ESPECES,
            1000L,
            UUID.randomUUID().toString()
        ) as ValidationVenteResult.Succes).vente
        val date1 = s1.vente.dateMetier

        // Vente 2 (sera annulée) : 2 x Jus (3000) Carte
        val s2Cancelled = (repo.validateSale(
            listOf(PanierLigneInput(p2.id, p2.reference, p2.nom, 1500L, 2)),
            ModePaiement.CARTE,
            3000L,
            UUID.randomUUID().toString()
        ) as ValidationVenteResult.Succes).vente
        assertTrue(repo.cancelSale(s2Cancelled.vente.id, "Annulation test") is ServiceResult.Succes)

        // Vente 3 (Jour 2) : 2 x Jus (3000) Mobile Money
        fakeNowMillis = jour1 + 86_400_000L
        val s3 = (repo.validateSale(
            listOf(PanierLigneInput(p2.id, p2.reference, p2.nom, 1500L, 2)),
            ModePaiement.MOBILE_MONEY,
            3000L,
            UUID.randomUUID().toString()
        ) as ValidationVenteResult.Succes).vente
        val date2 = s3.vente.dateMetier

        val res = repo.getRapportComplet(date1, date2)
        assertTrue(res is ServiceResult.Succes)
        val r = (res as ServiceResult.Succes).data

        assertEquals(4000L, r.totalValide)
        assertEquals(2, r.nombreVentesValidees)
        assertEquals(4, r.totalUnites)
        assertEquals(2000L, r.panierMoyen)
        assertEquals(1, r.nombreAnnulations)
        assertEquals(3000L, r.montantAnnulations)
        assertEquals(2, r.lignesProduits.size)
        assertEquals(2, r.lignesJours.size)

        val especes = r.repartitionPaiements.first { it.mode == ModePaiement.ESPECES }
        val momo = r.repartitionPaiements.first { it.mode == ModePaiement.MOBILE_MONEY }
        val carte = r.repartitionPaiements.first { it.mode == ModePaiement.CARTE }
        assertEquals(1000L, especes.montant)
        assertEquals(3000L, momo.montant)
        assertEquals(0L, carte.montant)
    }
}
