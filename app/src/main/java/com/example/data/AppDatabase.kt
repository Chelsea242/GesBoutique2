package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        UtilisateurEntity::class,
        CategorieEntity::class,
        ProduitEntity::class,
        VenteEntity::class,
        LigneVenteEntity::class,
        AnnulationVenteEntity::class,
        MouvementStockEntity::class,
        JournalAuditEntity::class,
        ParametreEntity::class,
        VersionSchemaEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun utilisateurDao(): UtilisateurDao
    abstract fun categorieDao(): CategorieDao
    abstract fun produitDao(): ProduitDao
    abstract fun mouvementStockDao(): MouvementStockDao
    abstract fun venteDao(): VenteDao
    abstract fun journalAuditDao(): JournalAuditDao
    abstract fun parametreDao(): ParametreDao
    abstract fun versionSchemaDao(): VersionSchemaDao

    companion object {
        const val DB_NAME = "caisse_stock.db"
        const val SCHEMA_VERSION = 1

        fun create(context: Context, dbName: String = DB_NAME, inMemory: Boolean = false): AppDatabase {
            val callback = object : Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    installConstraintsAndDefaults(db)
                }

                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    db.execSQL("PRAGMA foreign_keys = ON;")
                }
            }

            val builder = if (inMemory) {
                Room.inMemoryDatabaseBuilder(context.applicationContext, AppDatabase::class.java)
            } else {
                Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, dbName)
            }

            return builder
                .addCallback(callback)
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .build()
        }

        private fun installConstraintsAndDefaults(db: SupportSQLiteDatabase) {
            // Contraintes d'intégrité métier (Section 7 du cahier des charges)
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS trg_produit_check_insert
                BEFORE INSERT ON Produit
                BEGIN
                    SELECT CASE WHEN NEW.prixVente <= 0 THEN RAISE(ABORT, 'Le prix de vente doit être strictement positif') END;
                    SELECT CASE WHEN NEW.seuilAlerte < 0 THEN RAISE(ABORT, 'Le seuil d alerte doit être positif ou nul') END;
                END;
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS trg_produit_check_update
                BEFORE UPDATE ON Produit
                BEGIN
                    SELECT CASE WHEN NEW.prixVente <= 0 THEN RAISE(ABORT, 'Le prix de vente doit être strictement positif') END;
                    SELECT CASE WHEN NEW.seuilAlerte < 0 THEN RAISE(ABORT, 'Le seuil d alerte doit être positif ou nul') END;
                END;
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS trg_ligne_vente_check_insert
                BEFORE INSERT ON LigneVente
                BEGIN
                    SELECT CASE WHEN NEW.quantite <= 0 THEN RAISE(ABORT, 'La quantité vendue doit être strictement positive') END;
                    SELECT CASE WHEN NEW.prixUnitaire <= 0 THEN RAISE(ABORT, 'Le prix unitaire doit être strictement positif') END;
                END;
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS trg_vente_check_insert
                BEFORE INSERT ON Vente
                BEGIN
                    SELECT CASE WHEN NEW.total <= 0 THEN RAISE(ABORT, 'Le total de vente doit être strictement positif') END;
                    SELECT CASE WHEN NEW.montantRecu < NEW.total THEN RAISE(ABORT, 'Le montant reçu est insuffisant') END;
                    SELECT CASE WHEN NEW.modePaiement NOT IN ('ESPECES', 'MOBILE_MONEY', 'CARTE') THEN RAISE(ABORT, 'Mode de paiement invalide') END;
                    SELECT CASE WHEN NEW.statut NOT IN ('VALIDEE', 'ANNULEE') THEN RAISE(ABORT, 'Statut de vente invalide') END;
                END;
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS trg_mouvement_stock_check_insert
                BEFORE INSERT ON MouvementStock
                BEGIN
                    SELECT CASE
                        WHEN NEW.type NOT IN ('STOCK_INITIAL', 'REAPPROVISIONNEMENT', 'VENTE_VALIDEE', 'ANNULATION_VENTE', 'CORRECTION_INVENTAIRE')
                        THEN RAISE(ABORT, 'Type de mouvement invalide')
                    END;
                    SELECT CASE
                        WHEN (SELECT COALESCE(SUM(quantiteSignee), 0) FROM MouvementStock WHERE produitId = NEW.produitId) + NEW.quantiteSignee < 0
                        THEN RAISE(ABORT, 'Stock négatif interdit')
                    END;
                END;
                """.trimIndent()
            )

            // Version de schéma initiale
            val now = System.currentTimeMillis()
            db.execSQL(
                "INSERT OR IGNORE INTO VersionSchema (numero, dateApplicationUtc, description) VALUES (?, ?, ?)",
                arrayOf<Any>(SCHEMA_VERSION, now, "Création initiale du schéma gestion de magasin v1.0")
            )

            // Paramètres par défaut
            val defaults = listOf(
                "magasin_nom" to "Mon Magasin",
                "magasin_adresse" to "",
                "magasin_telephone" to "",
                "devise" to "XAF",
                "fuseau_horaire" to "Africa/Brazzaville",
                "logo_uri" to "",
                "sauvegarde_auto_active" to "true",
                "derniere_sauvegarde_auto_date" to "",
                "derniere_sauvegarde_auto_statut" to ""
            )
            defaults.forEach { (k, v) ->
                db.execSQL(
                    "INSERT OR IGNORE INTO Parametre (cle, valeur) VALUES (?, ?)",
                    arrayOf<Any>(k, v)
                )
            }
        }
    }
}
