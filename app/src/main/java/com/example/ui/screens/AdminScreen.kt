package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.*
import com.example.domain.Formatters
import com.example.ui.components.SectionHeader
import com.example.ui.theme.*

@Composable
fun AdminScreen(
    currentUser: UtilisateurEntity,
    utilisateurs: List<UtilisateurEntity>,
    parametres: ParametresMagasin,
    backups: List<SauvegardeInfo>,
    audits: List<AuditAvecAuteur>,
    onCreateUser: (String, String, String, RoleUtilisateur, (Boolean) -> Unit) -> Unit,
    onToggleUserActive: (Long, Boolean) -> Unit,
    onResetPassword: (Long, String, (Boolean) -> Unit) -> Unit,
    onSaveSettings: (ParametresMagasin) -> Unit,
    onLogoPicked: (Uri) -> Unit,
    onRefreshBackups: () -> Unit,
    onCreateManualBackup: () -> Unit,
    onExportBackup: (String, Uri) -> Unit,
    onRestoreLocalBackup: (String) -> Unit,
    onRestoreExternalBackup: (Uri) -> Unit,
    onLoadDemoDataset: () -> Unit,
    onResetDatabaseToBlank: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0=Comptes, 1=Magasin, 2=Sauvegardes, 3=Audit

    LaunchedEffect(selectedTab) {
        if (selectedTab == 2) onRefreshBackups()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionHeader(
            title = "Administration",
            subtitle = "Comptes, paramètres magasin, sauvegardes et traçabilité"
        )

        ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 0.dp) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Comptes") },
                modifier = Modifier.testTag("admin_tab_users")
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Magasin") },
                modifier = Modifier.testTag("admin_tab_store")
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("Sauvegardes") },
                modifier = Modifier.testTag("admin_tab_backups")
            )
            Tab(
                selected = selectedTab == 3,
                onClick = { selectedTab = 3 },
                text = { Text("Journal d'audit") },
                modifier = Modifier.testTag("admin_tab_audit")
            )
        }

        when (selectedTab) {
            0 -> UsersTab(
                currentUser = currentUser,
                utilisateurs = utilisateurs,
                onCreateUser = onCreateUser,
                onToggleUserActive = onToggleUserActive,
                onResetPassword = onResetPassword
            )
            1 -> StoreSettingsTab(
                parametres = parametres,
                onSaveSettings = onSaveSettings,
                onLogoPicked = onLogoPicked,
                onLoadDemoDataset = onLoadDemoDataset
            )
            2 -> BackupsTab(
                parametres = parametres,
                backups = backups,
                onCreateManualBackup = onCreateManualBackup,
                onExportBackup = onExportBackup,
                onRestoreLocalBackup = onRestoreLocalBackup,
                onRestoreExternalBackup = onRestoreExternalBackup,
                onResetDatabaseToBlank = onResetDatabaseToBlank
            )
            3 -> AuditTab(
                audits = audits,
                fuseauHoraire = parametres.fuseauHoraire
            )
        }
    }
}

@Composable
private fun UsersTab(
    currentUser: UtilisateurEntity,
    utilisateurs: List<UtilisateurEntity>,
    onCreateUser: (String, String, String, RoleUtilisateur, (Boolean) -> Unit) -> Unit,
    onToggleUserActive: (Long, Boolean) -> Unit,
    onResetPassword: (Long, String, (Boolean) -> Unit) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var resetTarget by remember { mutableStateOf<UtilisateurEntity?>(null) }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${utilisateurs.size} compte(s)", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Button(
                onClick = { showCreateDialog = true },
                modifier = Modifier.minimumInteractiveComponentSize().testTag("create_user_button")
            ) {
                Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Nouveau compte")
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(utilisateurs, key = { it.id }) { u ->
                val roleEnum = RoleUtilisateur.fromString(u.role)
                Surface(
                    color = AppSurface,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, AppOutlineSubtle)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(u.nom, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                if (u.id == currentUser.id) {
                                    Surface(color = ActionBlueSoft, shape = RoundedCornerShape(6.dp)) {
                                        Text("Connecté", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = ActionBlueDark)
                                    }
                                }
                            }
                            Text(
                                "${u.identifiant} • ${roleEnum.libelle}",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
                                color = TextSecondary
                            )
                            Text(
                                if (u.actif) "Actif" else "Désactivé",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (u.actif) DisponibleText else RuptureText
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(onClick = { resetTarget = u }) {
                                Text("Mot de passe")
                            }
                            OutlinedButton(
                                onClick = { onToggleUserActive(u.id, !u.actif) },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = if (u.actif) RuptureText else DisponibleText
                                )
                            ) {
                                Text(if (u.actif) "Désactiver" else "Réactiver")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        var nom by remember { mutableStateOf("") }
        var identifiant by remember { mutableStateOf("") }
        var mdp by remember { mutableStateOf("") }
        var role by remember { mutableStateOf(RoleUtilisateur.VENDEUR) }
        var err by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Créer un compte utilisateur") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = nom,
                        onValueChange = { nom = it; err = null },
                        label = { Text("Nom complet *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("new_user_nom_input")
                    )
                    OutlinedTextField(
                        value = identifiant,
                        onValueChange = { identifiant = it; err = null },
                        label = { Text("Identifiant (min. 3 car.) *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("new_user_id_input")
                    )
                    OutlinedTextField(
                        value = mdp,
                        onValueChange = { mdp = it; err = null },
                        label = { Text("Mot de passe (min. 4 car.) *") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("new_user_pwd_input")
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RoleUtilisateur.entries.forEach { r ->
                            FilterChip(
                                selected = role == r,
                                onClick = { role = r },
                                label = { Text(r.libelle) }
                            )
                        }
                    }
                    if (!err.isNullOrBlank()) {
                        Text(err!!, color = RuptureText, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        when {
                            nom.trim().isEmpty() -> err = "Le nom complet est obligatoire."
                            identifiant.trim().length < 3 -> err = "L'identifiant doit contenir au moins 3 caractères."
                            mdp.length < 4 -> err = "Le mot de passe doit contenir au moins 4 caractères."
                            else -> onCreateUser(nom, identifiant, mdp, role) { ok ->
                                if (ok) showCreateDialog = false else err = "Cet identifiant est déjà utilisé."
                            }
                        }
                    },
                    modifier = Modifier.testTag("save_new_user_button")
                ) { Text("Créer") }
            },
            dismissButton = { TextButton(onClick = { showCreateDialog = false }) { Text("Annuler") } }
        )
    }

    if (resetTarget != null) {
        var pwd by remember { mutableStateOf("") }
        var pwdErr by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { resetTarget = null },
            title = { Text("Réinitialiser le mot de passe") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = pwd,
                        onValueChange = { pwd = it; pwdErr = null },
                        label = { Text("Nouveau mot de passe (min. 4 car.) *") },
                        visualTransformation = PasswordVisualTransformation(),
                        isError = pwdErr != null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (!pwdErr.isNullOrBlank()) {
                        Text(pwdErr!!, color = RuptureText, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (pwd.length < 4) {
                        pwdErr = "Le mot de passe doit contenir au moins 4 caractères."
                    } else {
                        onResetPassword(resetTarget!!.id, pwd) { ok -> if (ok) resetTarget = null }
                    }
                }) { Text("Valider") }
            },
            dismissButton = { TextButton(onClick = { resetTarget = null }) { Text("Annuler") } }
        )
    }
}

@Composable
private fun StoreSettingsTab(
    parametres: ParametresMagasin,
    onSaveSettings: (ParametresMagasin) -> Unit,
    onLogoPicked: (Uri) -> Unit,
    onLoadDemoDataset: () -> Unit
) {
    var nom by remember(parametres.nomCommercial) { mutableStateOf(parametres.nomCommercial) }
    var adresse by remember(parametres.adresse) { mutableStateOf(parametres.adresse) }
    var tel by remember(parametres.telephone) { mutableStateOf(parametres.telephone) }
    var fuseau by remember(parametres.fuseauHoraire) { mutableStateOf(parametres.fuseauHoraire) }
    var autoBackup by remember(parametres.sauvegardeAutoActive) { mutableStateOf(parametres.sauvegardeAutoActive) }

    // Zero-permission Photo Picker (Play Policy compliant)
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) onLogoPicked(uri)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            color = AppSurface,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, AppOutlineSubtle)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Identité du magasin", style = MaterialTheme.typography.titleMedium, color = TextPrimary)

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (parametres.logoUri.isNotBlank()) {
                        AsyncImage(
                            model = parametres.logoUri,
                            contentDescription = "Logo du magasin",
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Surface(
                            color = ActionBlueSoft,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.size(56.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Storefront, contentDescription = null, tint = ActionBlue)
                            }
                        }
                    }
                    OutlinedButton(onClick = {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) {
                        Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Choisir un logo (facultatif)")
                    }
                }

                OutlinedTextField(
                    value = nom,
                    onValueChange = { nom = it },
                    label = { Text("Nom commercial *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("store_name_input")
                )
                OutlinedTextField(
                    value = adresse,
                    onValueChange = { adresse = it },
                    label = { Text("Adresse") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = tel,
                    onValueChange = { tel = it },
                    label = { Text("Téléphone") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = fuseau,
                    onValueChange = { fuseau = it },
                    label = { Text("Fuseau horaire (IANA)") },
                    supportingText = { Text("Ex. Africa/Brazzaville, Africa/Douala, Africa/Libreville") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Sauvegarde automatique quotidienne", style = MaterialTheme.typography.titleSmall)
                        Text("Au premier démarrage du jour (7 dernières conservées)", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                    Switch(checked = autoBackup, onCheckedChange = { autoBackup = it })
                }

                Button(
                    onClick = {
                        onSaveSettings(
                            parametres.copy(
                                nomCommercial = nom,
                                adresse = adresse,
                                telephone = tel,
                                fuseauHoraire = fuseau,
                                sauvegardeAutoActive = autoBackup
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("save_store_settings_button")
                ) {
                    Text("Enregistrer les paramètres")
                }
            }
        }

        Surface(
            color = AppSurface,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, AppOutlineSubtle)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Données de démonstration", style = MaterialTheme.typography.titleSmall)
                    Text("Ajoute des produits exemples (disponibles, faibles et en rupture) s'ils n'existent pas.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(onClick = onLoadDemoDataset) {
                    Text("Charger")
                }
            }
        }
    }
}

@Composable
private fun BackupsTab(
    parametres: ParametresMagasin,
    backups: List<SauvegardeInfo>,
    onCreateManualBackup: () -> Unit,
    onExportBackup: (String, Uri) -> Unit,
    onRestoreLocalBackup: (String) -> Unit,
    onRestoreExternalBackup: (Uri) -> Unit,
    onResetDatabaseToBlank: () -> Unit
) {
    var pendingExportPath by remember { mutableStateOf<String?>(null) }
    var pendingRestoreLocal by remember { mutableStateOf<SauvegardeInfo?>(null) }
    var showResetConfirm by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val path = pendingExportPath
        if (uri != null && path != null) onExportBackup(path, uri)
        pendingExportPath = null
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onRestoreExternalBackup(uri)
    }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            color = AppSurface,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, AppOutlineSubtle)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Sauvegarde SQLite locale", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text(
                    "Dernière auto : ${parametres.derniereSauvegardeAutoDate.ifBlank { "Aucune" }} • ${parametres.derniereSauvegardeAutoStatut.ifBlank { "En attente" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onCreateManualBackup,
                        modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("manual_backup_button")
                    ) {
                        Icon(Icons.Filled.Backup, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sauvegarder")
                    }
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("*/*")) },
                        modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("restore_external_button")
                    ) {
                        Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Importer (.db)")
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(backups, key = { it.cheminAbsolu }) { b ->
                Surface(
                    color = AppSurface,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, AppOutlineSubtle)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                b.nomFichier,
                                style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMono),
                                color = TextPrimary
                            )
                            Text(
                                "${Formatters.formatDateTime(b.dateModificationMillis, parametres.fuseauHoraire)} • ${(b.tailleOctets / 1024).coerceAtLeast(1)} Ko • ${if (b.estAutomatique) "Auto" else "Manuelle"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            IconButton(onClick = {
                                pendingExportPath = b.cheminAbsolu
                                exportLauncher.launch(b.nomFichier)
                            }) {
                                Icon(Icons.Filled.FileUpload, contentDescription = "Copier sur support externe")
                            }
                            OutlinedButton(onClick = { pendingRestoreLocal = b }) {
                                Text("Restaurer")
                            }
                        }
                    }
                }
            }
        }

        Surface(
            color = RuptureBackground.copy(alpha = 0.5f),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, RuptureBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Recommencer à zéro (base vierge)", style = MaterialTheme.typography.titleSmall, color = RuptureText)
                    Text(
                        "Crée d'abord une sauvegarde de sécurité puis réinitialise le magasin à l'écran de configuration initiale.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = { showResetConfirm = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = RuptureText),
                    modifier = Modifier.testTag("reset_database_button")
                ) {
                    Text("Réinitialiser")
                }
            }
        }
    }

    if (pendingRestoreLocal != null) {
        val target = pendingRestoreLocal!!
        AlertDialog(
            onDismissRequest = { pendingRestoreLocal = null },
            title = { Text("Confirmer la restauration") },
            text = {
                Text(
                    "Restaurer la base depuis « ${target.nomFichier} » (${Formatters.formatDateTime(target.dateModificationMillis, parametres.fuseauHoraire)}) ? Une sauvegarde préalable de la base actuelle sera créée, son intégrité sera vérifiée, puis vous serez ramené à l'écran de connexion."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRestoreLocalBackup(target.cheminAbsolu)
                        pendingRestoreLocal = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RuptureText)
                ) { Text("Restaurer maintenant") }
            },
            dismissButton = { TextButton(onClick = { pendingRestoreLocal = null }) { Text("Annuler") } }
        )
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Réinitialiser le magasin ?") },
            text = {
                Text("Une sauvegarde complète sera enregistrée avant la remise à zéro. Toutes les données actives (produits, ventes, comptes) seront effacées pour recommencer l'installation initiale.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetConfirm = false
                        onResetDatabaseToBlank()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RuptureText),
                    modifier = Modifier.testTag("confirm_reset_database_button")
                ) { Text("Réinitialiser à zéro") }
            },
            dismissButton = { TextButton(onClick = { showResetConfirm = false }) { Text("Annuler") } }
        )
    }
}

@Composable
private fun AuditTab(
    audits: List<AuditAvecAuteur>,
    fuseauHoraire: String
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(audits, key = { it.id }) { a ->
            Surface(
                color = AppSurface,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, AppOutlineSubtle)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(a.action, style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMono), color = ActionBlue)
                        Text(
                            Formatters.formatDateTime(a.dateHeureUtc, fuseauHoraire),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                    Text(a.details, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                    Text("Auteur : ${a.auteurNom ?: "Système"}", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                }
            }
        }
    }
}
