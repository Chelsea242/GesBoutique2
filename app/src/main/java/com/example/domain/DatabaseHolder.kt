package com.example.domain

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.example.data.AppDatabase
import com.example.data.ParametreEntity
import com.example.data.SauvegardeInfo
import com.example.data.ServiceResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DatabaseHolder(
    private val context: Context,
    private val dbName: String = AppDatabase.DB_NAME,
    private val inMemory: Boolean = false
) {
    private val mutex = Mutex()
    private val _databaseFlow = MutableStateFlow(AppDatabase.create(context, dbName, inMemory))
    val databaseFlow: StateFlow<AppDatabase> = _databaseFlow.asStateFlow()

    val currentDb: AppDatabase
        get() = _databaseFlow.value

    private val backupsDir: File
        get() = File(context.filesDir, "backups").apply { mkdirs() }

    private val dbFile: File
        get() = context.getDatabasePath(dbName)

    suspend fun <T> withDbLock(block: suspend (AppDatabase) -> T): T = mutex.withLock {
        block(_databaseFlow.value)
    }

    suspend fun listBackups(): List<SauvegardeInfo> = withContext(Dispatchers.IO) {
        val files = backupsDir.listFiles { f -> f.isFile && f.name.endsWith(".db") } ?: emptyArray()
        files.sortedByDescending { it.lastModified() }.map { file ->
            SauvegardeInfo(
                nomFichier = file.name,
                cheminAbsolu = file.absolutePath,
                dateModificationMillis = file.lastModified(),
                tailleOctets = file.length(),
                estAutomatique = file.name.startsWith("auto_")
            )
        }
    }

    suspend fun createBackup(isAutomatic: Boolean): ServiceResult<SauvegardeInfo> = withContext(Dispatchers.IO) {
        if (inMemory) {
            return@withContext ServiceResult.Erreur("Sauvegarde fichier non disponible en mode mémoire.")
        }
        mutex.withLock {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val prefix = if (isAutomatic) "auto" else "manuelle"
            val targetFile = File(backupsDir, "${prefix}_$timestamp.db")
            val tempFile = File(backupsDir, "${prefix}_$timestamp.tmp")

            try {
                val db = _databaseFlow.value
                // Force WAL checkpoint puis fermeture propre pour copie binaire cohérente (compatible API 24+)
                db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").close()
                db.close()

                if (!dbFile.exists()) {
                    _databaseFlow.value = AppDatabase.create(context, dbName, inMemory)
                    return@withLock ServiceResult.Erreur("Fichier de base de données introuvable.")
                }

                dbFile.copyTo(tempFile, overwrite = true)

                val validationError = validateSqliteBackupFile(tempFile)
                if (validationError != null) {
                    tempFile.delete()
                    _databaseFlow.value = AppDatabase.create(context, dbName, inMemory)
                    return@withLock ServiceResult.Erreur("Sauvegarde invalide : $validationError")
                }

                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }

                // Réouverture de la base active
                val reopened = AppDatabase.create(context, dbName, inMemory)
                _databaseFlow.value = reopened

                if (isAutomatic) {
                    pruneAutoBackups(keepCount = 7)
                }

                ServiceResult.Succes(
                    data = SauvegardeInfo(
                        nomFichier = targetFile.name,
                        cheminAbsolu = targetFile.absolutePath,
                        dateModificationMillis = targetFile.lastModified(),
                        tailleOctets = targetFile.length(),
                        estAutomatique = isAutomatic
                    ),
                    message = "Sauvegarde créée : ${targetFile.name}"
                )
            } catch (e: Exception) {
                tempFile.delete()
                if (!_databaseFlow.value.isOpen) {
                    _databaseFlow.value = AppDatabase.create(context, dbName, inMemory)
                }
                ServiceResult.Erreur("Échec de la sauvegarde : ${e.message ?: "erreur inconnue"}")
            }
        }
    }

    suspend fun runDailyAutoBackupIfDue(todayDateMetier: String): ServiceResult<SauvegardeInfo?> =
        withContext(Dispatchers.IO) {
            if (inMemory) return@withContext ServiceResult.Succes(null)
            val paramDao = _databaseFlow.value.parametreDao()
            val enabled = paramDao.getValue("sauvegarde_auto_active") != "false"
            if (!enabled) return@withContext ServiceResult.Succes(null)
            val lastDate = paramDao.getValue("derniere_sauvegarde_auto_date") ?: ""
            if (lastDate == todayDateMetier) {
                return@withContext ServiceResult.Succes(null)
            }

            val result = createBackup(isAutomatic = true)
            val newDao = _databaseFlow.value.parametreDao()
            when (result) {
                is ServiceResult.Succes -> {
                    newDao.upsertAll(
                        listOf(
                            ParametreEntity("derniere_sauvegarde_auto_date", todayDateMetier),
                            ParametreEntity("derniere_sauvegarde_auto_statut", "Succès (${result.data.nomFichier})")
                        )
                    )
                }
                is ServiceResult.Erreur -> {
                    newDao.upsert(
                        ParametreEntity("derniere_sauvegarde_auto_statut", "Échec : ${result.message}")
                    )
                }
            }
            result
        }

    suspend fun exportBackupToStream(sourceFile: File, outputStream: java.io.OutputStream): ServiceResult<Unit> =
        withContext(Dispatchers.IO) {
            try {
                FileInputStream(sourceFile).use { input ->
                    outputStream.use { out ->
                        input.copyTo(out)
                        out.flush()
                    }
                }
                ServiceResult.Succes(Unit, "Copie externe exportée avec succès.")
            } catch (e: Exception) {
                ServiceResult.Erreur("Impossible d'exporter la sauvegarde : ${e.message}")
            }
        }

    suspend fun restoreFromBackupFile(candidateFile: File): ServiceResult<String> = withContext(Dispatchers.IO) {
        if (inMemory) {
            return@withContext ServiceResult.Erreur("Restauration non disponible en mode mémoire.")
        }

        // 1. Vérifier intégrité et compatibilité du schéma avant de toucher à la base active
        val validationError = validateSqliteBackupFile(candidateFile)
        if (validationError != null) {
            return@withContext ServiceResult.Erreur("Copie invalide refusée : $validationError")
        }

        // 2. Sauvegarde préalable de sécurité
        val preRestoreResult = createBackup(isAutomatic = false)
        if (preRestoreResult is ServiceResult.Erreur) {
            return@withContext ServiceResult.Erreur("Sauvegarde préalable impossible : ${preRestoreResult.message}")
        }

        // 3. Fermer connexions, remplacer et réouvrir
        mutex.withLock {
            try {
                _databaseFlow.value.close()

                // Nettoyer les fichiers WAL/SHM existants
                File(dbFile.absolutePath + "-wal").delete()
                File(dbFile.absolutePath + "-shm").delete()
                File(dbFile.absolutePath + "-journal").delete()

                candidateFile.parentFile?.mkdirs()
                FileInputStream(candidateFile).use { input ->
                    FileOutputStream(dbFile, false).use { out ->
                        input.copyTo(out)
                        out.fd.sync()
                    }
                }

                val reopened = AppDatabase.create(context, dbName, inMemory)
                // Forcer l'ouverture pour vérifier que Room accepte le schéma
                reopened.openHelper.writableDatabase
                _databaseFlow.value = reopened

                ServiceResult.Succes(
                    data = candidateFile.name,
                    message = "Base restaurée avec succès. Reconnexion requise."
                )
            } catch (e: Exception) {
                // En cas d'échec, restaurer la sauvegarde préalable
                if (preRestoreResult is ServiceResult.Succes) {
                    val rollbackFile = File(preRestoreResult.data.cheminAbsolu)
                    if (rollbackFile.exists()) {
                        File(dbFile.absolutePath + "-wal").delete()
                        File(dbFile.absolutePath + "-shm").delete()
                        rollbackFile.copyTo(dbFile, overwrite = true)
                    }
                }
                _databaseFlow.value = AppDatabase.create(context, dbName, inMemory)
                ServiceResult.Erreur("Échec de la restauration, base précédente conservée : ${e.message}")
            }
        }
    }

    suspend fun copyStreamToTempBackupCandidate(inputStream: java.io.InputStream): File =
        withContext(Dispatchers.IO) {
            val temp = File(context.cacheDir, "restore_candidate_${System.currentTimeMillis()}.db")
            inputStream.use { input ->
                FileOutputStream(temp).use { out ->
                    input.copyTo(out)
                }
            }
            temp
        }

    fun validateSqliteBackupFile(file: File): String? {
        if (!file.exists() || !file.isFile || file.length() < 100) {
            return "Fichier absent ou trop petit pour être une base SQLite."
        }
        val header = ByteArray(16)
        try {
            FileInputStream(file).use { it.read(header) }
        } catch (e: Exception) {
            return "Lecture impossible : ${e.message}"
        }
        val magic = String(header, Charsets.US_ASCII)
        if (!magic.startsWith("SQLite format 3")) {
            return "Ce fichier n'est pas une base SQLite valide."
        }

        var sqliteDb: SQLiteDatabase? = null
        return try {
            sqliteDb = SQLiteDatabase.openDatabase(
                file.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY
            )
            // PRAGMA integrity_check
            sqliteDb.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                if (!cursor.moveToFirst() || cursor.getString(0) != "ok") {
                    return "Contrôle d'intégrité SQLite échoué."
                }
            }
            // Vérification des tables requises et compatibilité de version
            val requiredTables = setOf(
                "Utilisateur", "Categorie", "Produit", "Vente", "LigneVente",
                "AnnulationVente", "MouvementStock", "JournalAudit", "Parametre", "VersionSchema"
            )
            val existingTables = mutableSetOf<String>()
            sqliteDb.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table'",
                null
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    existingTables.add(cursor.getString(0))
                }
            }
            val missing = requiredTables - existingTables
            if (missing.isNotEmpty()) {
                return "Schéma incompatible (tables manquantes : ${missing.joinToString(", ")})."
            }
            sqliteDb.rawQuery("SELECT MAX(numero) FROM VersionSchema", null).use { cursor ->
                if (!cursor.moveToFirst() || cursor.isNull(0)) {
                    return "Version de schéma absente."
                }
                val version = cursor.getInt(0)
                if (version > AppDatabase.SCHEMA_VERSION) {
                    return "Version de schéma plus récente ($version > ${AppDatabase.SCHEMA_VERSION})."
                }
            }
            null
        } catch (e: Exception) {
            "Base corrompue ou illisible (${e.message})."
        } finally {
            sqliteDb?.close()
        }
    }

    private fun pruneAutoBackups(keepCount: Int) {
        val autoFiles = backupsDir.listFiles { f ->
            f.isFile && f.name.startsWith("auto_") && f.name.endsWith(".db")
        }?.sortedByDescending { it.lastModified() } ?: return

        if (autoFiles.size > keepCount) {
            autoFiles.drop(keepCount).forEach { it.delete() }
        }
    }

    suspend fun resetToFreshDatabase(): ServiceResult<String> = withContext(Dispatchers.IO) {
        if (!inMemory) {
            val backupRes = createBackup(isAutomatic = false)
            if (backupRes is ServiceResult.Erreur) {
                return@withContext ServiceResult.Erreur("Sauvegarde de sécurité impossible avant réinitialisation : ${backupRes.message}")
            }
        }
        mutex.withLock {
            try {
                _databaseFlow.value.close()
                if (!inMemory) {
                    dbFile.delete()
                    File(dbFile.absolutePath + "-wal").delete()
                    File(dbFile.absolutePath + "-shm").delete()
                    File(dbFile.absolutePath + "-journal").delete()
                }
                val fresh = AppDatabase.create(context, dbName, inMemory)
                fresh.openHelper.writableDatabase
                _databaseFlow.value = fresh
                ServiceResult.Succes("OK", "Base réinitialisée à zéro. Créez le premier administrateur.")
            } catch (e: Exception) {
                _databaseFlow.value = AppDatabase.create(context, dbName, inMemory)
                ServiceResult.Erreur("Impossible de réinitialiser la base : ${e.message}")
            }
        }
    }

    fun close() {
        _databaseFlow.value.close()
    }
}
