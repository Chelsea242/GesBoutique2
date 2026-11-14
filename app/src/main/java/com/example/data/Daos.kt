package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface UtilisateurDao {
    @Query("SELECT COUNT(*) FROM Utilisateur")
    suspend fun countUsers(): Int

    @Query("SELECT COUNT(*) FROM Utilisateur")
    fun observeUserCount(): Flow<Int>

    @Query("SELECT * FROM Utilisateur ORDER BY nom ASC")
    fun observeAllUsers(): Flow<List<UtilisateurEntity>>

    @Query("SELECT * FROM Utilisateur ORDER BY nom ASC")
    suspend fun getAllUsers(): List<UtilisateurEntity>

    @Query("SELECT * FROM Utilisateur WHERE identifiant = :identifiant LIMIT 1")
    suspend fun findByIdentifiant(identifiant: String): UtilisateurEntity?

    @Query("SELECT * FROM Utilisateur WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): UtilisateurEntity?

    @Query("SELECT COUNT(*) FROM Utilisateur WHERE role = 'ADMINISTRATEUR' AND actif = 1")
    suspend fun countActiveAdmins(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(utilisateur: UtilisateurEntity): Long

    @Update
    suspend fun update(utilisateur: UtilisateurEntity)
}

@Dao
interface CategorieDao {
    @Query("SELECT * FROM Categorie ORDER BY nom ASC")
    fun observeAll(): Flow<List<CategorieEntity>>

    @Query("SELECT * FROM Categorie ORDER BY nom ASC")
    suspend fun getAll(): List<CategorieEntity>

    @Query("SELECT * FROM Categorie WHERE LOWER(TRIM(nom)) = LOWER(TRIM(:nom)) LIMIT 1")
    suspend fun findByNom(nom: String): CategorieEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(categorie: CategorieEntity): Long
}

@Dao
interface ProduitDao {
    @Query(
        """
        SELECT p.id, p.reference, p.nom, p.categorieId, c.nom AS categorieNom,
               p.prixVente, p.seuilAlerte, p.actif, p.dateCreationUtc,
               COALESCE(SUM(m.quantiteSignee), 0) AS stockDisponible
        FROM Produit p
        LEFT JOIN Categorie c ON c.id = p.categorieId
        LEFT JOIN MouvementStock m ON m.produitId = p.id
        GROUP BY p.id
        ORDER BY p.nom ASC
        """
    )
    fun observeProduitsAvecStock(): Flow<List<ProduitAvecStock>>

    @Query(
        """
        SELECT p.id, p.reference, p.nom, p.categorieId, c.nom AS categorieNom,
               p.prixVente, p.seuilAlerte, p.actif, p.dateCreationUtc,
               COALESCE(SUM(m.quantiteSignee), 0) AS stockDisponible
        FROM Produit p
        LEFT JOIN Categorie c ON c.id = p.categorieId
        LEFT JOIN MouvementStock m ON m.produitId = p.id
        GROUP BY p.id
        ORDER BY p.nom ASC
        """
    )
    suspend fun getProduitsAvecStock(): List<ProduitAvecStock>

    @Query("SELECT * FROM Produit WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): ProduitEntity?

    @Query("SELECT * FROM Produit WHERE LOWER(TRIM(reference)) = LOWER(TRIM(:reference)) LIMIT 1")
    suspend fun findByReference(reference: String): ProduitEntity?

    @Query("SELECT COALESCE(SUM(quantiteSignee), 0) FROM MouvementStock WHERE produitId = :produitId")
    suspend fun getStockDisponible(produitId: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(produit: ProduitEntity): Long

    @Update
    suspend fun update(produit: ProduitEntity)
}

@Dao
interface MouvementStockDao {
    @Query(
        """
        SELECT m.id, m.produitId, p.reference AS produitReference, p.nom AS produitNom,
               m.auteurId, u.nom AS auteurNom, m.dateHeureUtc, m.type, m.quantiteSignee,
               m.motif, m.ligneVenteId, m.annulationId
        FROM MouvementStock m
        INNER JOIN Produit p ON p.id = m.produitId
        INNER JOIN Utilisateur u ON u.id = m.auteurId
        ORDER BY m.dateHeureUtc DESC, m.id DESC
        """
    )
    fun observeAllMouvements(): Flow<List<MouvementAvecDetails>>

    @Query(
        """
        SELECT m.id, m.produitId, p.reference AS produitReference, p.nom AS produitNom,
               m.auteurId, u.nom AS auteurNom, m.dateHeureUtc, m.type, m.quantiteSignee,
               m.motif, m.ligneVenteId, m.annulationId
        FROM MouvementStock m
        INNER JOIN Produit p ON p.id = m.produitId
        INNER JOIN Utilisateur u ON u.id = m.auteurId
        WHERE m.produitId = :produitId
        ORDER BY m.dateHeureUtc DESC, m.id DESC
        """
    )
    suspend fun getMouvementsForProduit(produitId: Long): List<MouvementAvecDetails>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(mouvement: MouvementStockEntity): Long
}

@Dao
interface VenteDao {
    @Query(
        """
        SELECT v.id, v.numero, v.jetonValidation, v.dateHeureUtc, v.dateMetier,
               v.vendeurId, u.nom AS vendeurNom, v.total, v.modePaiement,
               v.montantRecu, v.monnaieRendue, v.statut
        FROM Vente v
        INNER JOIN Utilisateur u ON u.id = v.vendeurId
        ORDER BY v.dateHeureUtc DESC, v.id DESC
        """
    )
    fun observeAllVentes(): Flow<List<VenteAvecVendeur>>

    @Query(
        """
        SELECT v.id, v.numero, v.jetonValidation, v.dateHeureUtc, v.dateMetier,
               v.vendeurId, u.nom AS vendeurNom, v.total, v.modePaiement,
               v.montantRecu, v.monnaieRendue, v.statut
        FROM Vente v
        INNER JOIN Utilisateur u ON u.id = v.vendeurId
        WHERE v.vendeurId = :vendeurId
        ORDER BY v.dateHeureUtc DESC, v.id DESC
        """
    )
    fun observeVentesByVendeur(vendeurId: Long): Flow<List<VenteAvecVendeur>>

    @Query(
        """
        SELECT v.id, v.numero, v.jetonValidation, v.dateHeureUtc, v.dateMetier,
               v.vendeurId, u.nom AS vendeurNom, v.total, v.modePaiement,
               v.montantRecu, v.monnaieRendue, v.statut
        FROM Vente v
        INNER JOIN Utilisateur u ON u.id = v.vendeurId
        WHERE v.id = :venteId LIMIT 1
        """
    )
    suspend fun getVenteAvecVendeur(venteId: Long): VenteAvecVendeur?

    @Query("SELECT * FROM Vente WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): VenteEntity?

    @Query("SELECT * FROM Vente WHERE jetonValidation = :jeton LIMIT 1")
    suspend fun findByJeton(jeton: String): VenteEntity?

    @Query("SELECT COUNT(*) FROM Vente WHERE dateMetier = :dateMetier")
    suspend fun countByDateMetier(dateMetier: String): Int

    @Query("SELECT * FROM LigneVente WHERE venteId = :venteId ORDER BY id ASC")
    suspend fun getLignesForVente(venteId: Long): List<LigneVenteEntity>

    @Query(
        """
        SELECT a.id, a.venteId, a.auteurId, u.nom AS auteurNom, a.dateHeureUtc, a.motif
        FROM AnnulationVente a
        INNER JOIN Utilisateur u ON u.id = a.auteurId
        WHERE a.venteId = :venteId LIMIT 1
        """
    )
    suspend fun getAnnulationForVente(venteId: Long): AnnulationAvecAuteur?

    @Query(
        """
        SELECT COALESCE(SUM(total), 0)
        FROM Vente
        WHERE dateMetier = :dateMetier AND statut = 'VALIDEE'
        """
    )
    fun observeMontantVentesJourAll(dateMetier: String): Flow<Long>

    @Query(
        """
        SELECT COALESCE(SUM(total), 0)
        FROM Vente
        WHERE dateMetier = :dateMetier AND statut = 'VALIDEE' AND vendeurId = :vendeurId
        """
    )
    fun observeMontantVentesJourVendeur(dateMetier: String, vendeurId: Long): Flow<Long>

    @Query(
        """
        SELECT COUNT(*)
        FROM Vente
        WHERE dateMetier = :dateMetier AND statut = 'VALIDEE'
        """
    )
    fun observeNombreVentesJourAll(dateMetier: String): Flow<Int>

    @Query(
        """
        SELECT COUNT(*)
        FROM Vente
        WHERE dateMetier = :dateMetier AND statut = 'VALIDEE' AND vendeurId = :vendeurId
        """
    )
    fun observeNombreVentesJourVendeur(dateMetier: String, vendeurId: Long): Flow<Int>

    @Query(
        """
        SELECT COALESCE(SUM(l.quantite), 0)
        FROM LigneVente l
        INNER JOIN Vente v ON v.id = l.venteId
        WHERE v.dateMetier = :dateMetier AND v.statut = 'VALIDEE'
        """
    )
    fun observeUnitesVenduesJourAll(dateMetier: String): Flow<Int>

    @Query(
        """
        SELECT COALESCE(SUM(l.quantite), 0)
        FROM LigneVente l
        INNER JOIN Vente v ON v.id = l.venteId
        WHERE v.dateMetier = :dateMetier AND v.statut = 'VALIDEE' AND v.vendeurId = :vendeurId
        """
    )
    fun observeUnitesVenduesJourVendeur(dateMetier: String, vendeurId: Long): Flow<Int>

    @Query(
        """
        SELECT l.produitId AS produitId,
               MAX(l.referenceSnapshot) AS reference,
               MAX(l.nomSnapshot) AS nom,
               SUM(l.quantite) AS unitesVendues,
               SUM(l.sousTotal) AS montantVendu
        FROM LigneVente l
        INNER JOIN Vente v ON v.id = l.venteId
        WHERE v.statut = 'VALIDEE'
          AND v.dateMetier >= :dateDebut
          AND v.dateMetier <= :dateFin
        GROUP BY l.produitId
        ORDER BY montantVendu DESC, nom ASC
        """
    )
    suspend fun getRapportProduits(dateDebut: String, dateFin: String): List<RapportProduitLigne>

    @Query(
        """
        SELECT v.id, v.numero, v.jetonValidation, v.dateHeureUtc, v.dateMetier,
               v.vendeurId, u.nom AS vendeurNom, v.total, v.modePaiement,
               v.montantRecu, v.monnaieRendue, v.statut
        FROM Vente v
        INNER JOIN Utilisateur u ON u.id = v.vendeurId
        WHERE v.dateMetier >= :dateDebut AND v.dateMetier <= :dateFin
        ORDER BY v.dateHeureUtc DESC, v.id DESC
        """
    )
    suspend fun getVentesSurPeriode(dateDebut: String, dateFin: String): List<VenteAvecVendeur>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertVente(vente: VenteEntity): Long

    @Update
    suspend fun updateVente(vente: VenteEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLigne(ligne: LigneVenteEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAnnulation(annulation: AnnulationVenteEntity): Long
}

@Dao
interface JournalAuditDao {
    @Query(
        """
        SELECT j.id, j.auteurId, u.nom AS auteurNom, j.dateHeureUtc,
               j.action, j.entite, j.entiteId, j.details
        FROM JournalAudit j
        LEFT JOIN Utilisateur u ON u.id = j.auteurId
        ORDER BY j.dateHeureUtc DESC, j.id DESC
        LIMIT :limit
        """
    )
    fun observeRecentAudits(limit: Int = 300): Flow<List<AuditAvecAuteur>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(audit: JournalAuditEntity): Long
}

@Dao
interface ParametreDao {
    @Query("SELECT * FROM Parametre")
    fun observeAll(): Flow<List<ParametreEntity>>

    @Query("SELECT * FROM Parametre")
    suspend fun getAll(): List<ParametreEntity>

    @Query("SELECT valeur FROM Parametre WHERE cle = :cle LIMIT 1")
    suspend fun getValue(cle: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(parametre: ParametreEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(parametres: List<ParametreEntity>)
}

@Dao
interface VersionSchemaDao {
    @Query("SELECT * FROM VersionSchema ORDER BY numero DESC")
    suspend fun getAll(): List<VersionSchemaEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(version: VersionSchemaEntity)
}
