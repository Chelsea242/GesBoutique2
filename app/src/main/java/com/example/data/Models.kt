package com.example.data

enum class RoleUtilisateur(val libelle: String) {
    ADMINISTRATEUR("Administrateur"),
    VENDEUR("Vendeur");

    companion object {
        fun fromString(value: String): RoleUtilisateur =
            entries.firstOrNull { it.name == value } ?: VENDEUR
    }
}

enum class ModePaiement(val libelle: String) {
    ESPECES("Espèces"),
    MOBILE_MONEY("Mobile Money"),
    CARTE("Carte");

    companion object {
        fun fromString(value: String): ModePaiement =
            entries.firstOrNull { it.name == value } ?: ESPECES
    }
}

enum class StatutVente(val libelle: String) {
    VALIDEE("Validée"),
    ANNULEE("Annulée");

    companion object {
        fun fromString(value: String): StatutVente =
            entries.firstOrNull { it.name == value } ?: VALIDEE
    }
}

enum class TypeMouvement(val libelle: String) {
    STOCK_INITIAL("Stock initial"),
    REAPPROVISIONNEMENT("Réapprovisionnement"),
    VENTE_VALIDEE("Vente validée"),
    ANNULATION_VENTE("Annulation de vente"),
    CORRECTION_INVENTAIRE("Correction d'inventaire");

    companion object {
        fun fromString(value: String): TypeMouvement =
            entries.firstOrNull { it.name == value } ?: CORRECTION_INVENTAIRE
    }
}

enum class StatutDisponibilite(val libelle: String) {
    EN_RUPTURE("En rupture"),
    STOCK_FAIBLE("Stock faible"),
    DISPONIBLE("Disponible"),
    ARCHIVE("Archivé")
}

data class ProduitAvecStock(
    val id: Long,
    val reference: String,
    val nom: String,
    val categorieId: Long?,
    val categorieNom: String?,
    val prixVente: Long,
    val seuilAlerte: Int,
    val actif: Boolean,
    val dateCreationUtc: Long,
    val stockDisponible: Int
) {
    val statut: StatutDisponibilite
        get() = when {
            !actif -> StatutDisponibilite.ARCHIVE
            stockDisponible <= 0 -> StatutDisponibilite.EN_RUPTURE
            stockDisponible <= seuilAlerte -> StatutDisponibilite.STOCK_FAIBLE
            else -> StatutDisponibilite.DISPONIBLE
        }
}

data class VenteAvecVendeur(
    val id: Long,
    val numero: String,
    val jetonValidation: String,
    val dateHeureUtc: Long,
    val dateMetier: String,
    val vendeurId: Long,
    val vendeurNom: String,
    val total: Long,
    val modePaiement: String,
    val montantRecu: Long,
    val monnaieRendue: Long,
    val statut: String
)

data class VenteDetailComplet(
    val vente: VenteAvecVendeur,
    val lignes: List<LigneVenteEntity>,
    val annulation: AnnulationAvecAuteur?
)

data class AnnulationAvecAuteur(
    val id: Long,
    val venteId: Long,
    val auteurId: Long,
    val auteurNom: String,
    val dateHeureUtc: Long,
    val motif: String
)

data class MouvementAvecDetails(
    val id: Long,
    val produitId: Long,
    val produitReference: String,
    val produitNom: String,
    val auteurId: Long,
    val auteurNom: String,
    val dateHeureUtc: Long,
    val type: String,
    val quantiteSignee: Int,
    val motif: String,
    val ligneVenteId: Long?,
    val annulationId: Long?
)

data class AuditAvecAuteur(
    val id: Long,
    val auteurId: Long?,
    val auteurNom: String?,
    val dateHeureUtc: Long,
    val action: String,
    val entite: String,
    val entiteId: Long?,
    val details: String
)

data class IndicateursJour(
    val dateMetier: String,
    val montantVentesValidees: Long,
    val nombreVentesValidees: Int,
    val unitesVendues: Int,
    val referencesEnRupture: Int,
    val referencesEnStockFaible: Int
)

data class RapportProduitLigne(
    val produitId: Long,
    val reference: String,
    val nom: String,
    val unitesVendues: Int,
    val montantVendu: Long
)

data class RapportJourLigne(
    val dateMetier: String,
    val nombreVentes: Int,
    val montantVendu: Long
)

data class RapportPaiementLigne(
    val mode: ModePaiement,
    val nombreVentes: Int,
    val montant: Long
)

data class RapportPeriodeComplet(
    val dateDebut: String = "",
    val dateFin: String = "",
    val totalValide: Long = 0L,
    val nombreVentesValidees: Int = 0,
    val totalUnites: Int = 0,
    val panierMoyen: Long = 0L,
    val lignesProduits: List<RapportProduitLigne> = emptyList(),
    val lignesJours: List<RapportJourLigne> = emptyList(),
    val repartitionPaiements: List<RapportPaiementLigne> = emptyList(),
    val nombreAnnulations: Int = 0,
    val montantAnnulations: Long = 0L
)

data class ParametresMagasin(
    val nomCommercial: String = "Mon Magasin",
    val adresse: String = "",
    val telephone: String = "",
    val devise: String = "XAF",
    val fuseauHoraire: String = "Africa/Brazzaville",
    val logoUri: String = "",
    val sauvegardeAutoActive: Boolean = true,
    val derniereSauvegardeAutoDate: String = "",
    val derniereSauvegardeAutoStatut: String = ""
)

data class PanierLigneInput(
    val produitId: Long,
    val reference: String,
    val nom: String,
    val prixUnitaireAttendu: Long,
    val quantite: Int
)

sealed class ValidationVenteResult {
    data class Succes(val vente: VenteDetailComplet, val dejaEnregistree: Boolean = false) : ValidationVenteResult()
    data class PrixModifie(
        val anciensPrix: Map<Long, Long>,
        val nouveauxPrix: Map<Long, Long>,
        val nouveauTotal: Long,
        val message: String
    ) : ValidationVenteResult()
    data class StockInsuffisant(
        val produitId: Long,
        val produitNom: String,
        val stockDisponible: Int,
        val quantiteDemandee: Int
    ) : ValidationVenteResult()
    data class Erreur(val message: String) : ValidationVenteResult()
}

sealed class ServiceResult<out T> {
    data class Succes<T>(val data: T, val message: String = "") : ServiceResult<T>()
    data class Erreur(val message: String) : ServiceResult<Nothing>()
}

data class SauvegardeInfo(
    val nomFichier: String,
    val cheminAbsolu: String,
    val dateModificationMillis: Long,
    val tailleOctets: Long,
    val estAutomatique: Boolean
)
