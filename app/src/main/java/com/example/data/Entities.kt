package com.example.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "Utilisateur",
    indices = [Index(value = ["identifiant"], unique = true)]
)
data class UtilisateurEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nom: String,
    val identifiant: String,
    val motDePasseHash: String,
    val sel: String,
    val parametresHachage: String,
    val role: String, // ADMINISTRATEUR | VENDEUR
    val actif: Boolean = true
)

@Entity(
    tableName = "Categorie",
    indices = [Index(value = ["nom"], unique = true)]
)
data class CategorieEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nom: String
)

@Entity(
    tableName = "Produit",
    foreignKeys = [
        ForeignKey(
            entity = CategorieEntity::class,
            parentColumns = ["id"],
            childColumns = ["categorieId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["reference"], unique = true),
        Index(value = ["nom"]),
        Index(value = ["categorieId"])
    ]
)
data class ProduitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val reference: String,
    val nom: String,
    val categorieId: Long? = null,
    val prixVente: Long, // Entier en FCFA XAF (> 0)
    val seuilAlerte: Int, // Entier >= 0
    val actif: Boolean = true,
    val dateCreationUtc: Long
)

@Entity(
    tableName = "Vente",
    foreignKeys = [
        ForeignKey(
            entity = UtilisateurEntity::class,
            parentColumns = ["id"],
            childColumns = ["vendeurId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["numero"], unique = true),
        Index(value = ["jetonValidation"], unique = true),
        Index(value = ["dateMetier", "statut", "vendeurId"]),
        Index(value = ["vendeurId"])
    ]
)
data class VenteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val numero: String,
    val jetonValidation: String,
    val dateHeureUtc: Long,
    val dateMetier: String, // Format yyyy-MM-dd dans le fuseau configuré du magasin
    val vendeurId: Long,
    val total: Long,
    val modePaiement: String, // ESPECES | MOBILE_MONEY | CARTE
    val montantRecu: Long,
    val monnaieRendue: Long,
    val statut: String // VALIDEE | ANNULEE
)

@Entity(
    tableName = "LigneVente",
    foreignKeys = [
        ForeignKey(
            entity = VenteEntity::class,
            parentColumns = ["id"],
            childColumns = ["venteId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = ProduitEntity::class,
            parentColumns = ["id"],
            childColumns = ["produitId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["venteId", "produitId"], unique = true),
        Index(value = ["produitId"])
    ]
)
data class LigneVenteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val venteId: Long,
    val produitId: Long,
    val referenceSnapshot: String,
    val nomSnapshot: String,
    val quantite: Int,
    val prixUnitaire: Long,
    val sousTotal: Long
)

@Entity(
    tableName = "AnnulationVente",
    foreignKeys = [
        ForeignKey(
            entity = VenteEntity::class,
            parentColumns = ["id"],
            childColumns = ["venteId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = UtilisateurEntity::class,
            parentColumns = ["id"],
            childColumns = ["auteurId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["venteId"], unique = true),
        Index(value = ["auteurId"])
    ]
)
data class AnnulationVenteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val venteId: Long,
    val auteurId: Long,
    val dateHeureUtc: Long,
    val motif: String
)

@Entity(
    tableName = "MouvementStock",
    foreignKeys = [
        ForeignKey(
            entity = ProduitEntity::class,
            parentColumns = ["id"],
            childColumns = ["produitId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = UtilisateurEntity::class,
            parentColumns = ["id"],
            childColumns = ["auteurId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = LigneVenteEntity::class,
            parentColumns = ["id"],
            childColumns = ["ligneVenteId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = AnnulationVenteEntity::class,
            parentColumns = ["id"],
            childColumns = ["annulationId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["produitId", "dateHeureUtc"]),
        Index(value = ["auteurId"]),
        Index(value = ["ligneVenteId"]),
        Index(value = ["annulationId"]),
        Index(value = ["type", "ligneVenteId"], unique = true)
    ]
)
data class MouvementStockEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val produitId: Long,
    val auteurId: Long,
    val dateHeureUtc: Long,
    val type: String, // STOCK_INITIAL | REAPPROVISIONNEMENT | VENTE_VALIDEE | ANNULATION_VENTE | CORRECTION_INVENTAIRE
    val quantiteSignee: Int,
    val motif: String,
    val ligneVenteId: Long? = null,
    val annulationId: Long? = null
)

@Entity(
    tableName = "JournalAudit",
    foreignKeys = [
        ForeignKey(
            entity = UtilisateurEntity::class,
            parentColumns = ["id"],
            childColumns = ["auteurId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["auteurId"]),
        Index(value = ["dateHeureUtc"])
    ]
)
data class JournalAuditEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val auteurId: Long? = null,
    val dateHeureUtc: Long,
    val action: String,
    val entite: String,
    val entiteId: Long? = null,
    val details: String
)

@Entity(tableName = "Parametre")
data class ParametreEntity(
    @PrimaryKey val cle: String,
    val valeur: String
)

@Entity(tableName = "VersionSchema")
data class VersionSchemaEntity(
    @PrimaryKey val numero: Int,
    val dateApplicationUtc: Long,
    val description: String
)
