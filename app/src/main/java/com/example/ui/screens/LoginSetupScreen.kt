package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.ui.theme.*

@Composable
fun LoginSetupScreen(
    hasAnyUser: Boolean?,
    storeName: String,
    authError: String?,
    isLoading: Boolean,
    onCreateInitialAdmin: (nom: String, identifiant: String, motDePasse: String, avecDemo: Boolean) -> Unit,
    onLogin: (identifiant: String, motDePasse: String) -> Unit,
    onClearError: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFFEFF6FF), AppBackground)
                )
            )
            .safeDrawingPadding(),
        contentAlignment = Alignment.Center
    ) {
        if (hasAnyUser == null) {
            CircularProgressIndicator(color = ActionBlue)
            return@Box
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // En-tête marque sobre
            Surface(
                color = ActionBlue,
                shape = RoundedCornerShape(18.dp),
                shadowElevation = 4.dp
            ) {
                Icon(
                    imageVector = Icons.Filled.Storefront,
                    contentDescription = "CaisseStock",
                    modifier = Modifier
                        .padding(16.dp)
                        .size(36.dp),
                    tint = Color.White
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = storeName.ifBlank { "CaisseStock" },
                    style = MaterialTheme.typography.headlineLarge,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (hasAnyUser) {
                        "Connexion au poste de caisse et de stock"
                    } else {
                        "Configuration initiale — Créez le premier administrateur"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = AppSurface,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, AppOutlineSubtle),
                shadowElevation = 2.dp
            ) {
                if (!hasAnyUser) {
                    InitialAdminForm(
                        authError = authError,
                        isLoading = isLoading,
                        onSubmit = onCreateInitialAdmin,
                        onClearError = onClearError
                    )
                } else {
                    LoginForm(
                        authError = authError,
                        isLoading = isLoading,
                        onSubmit = onLogin,
                        onClearError = onClearError
                    )
                }
            }

            Text(
                text = "Fonctionnement 100 % hors ligne • Devise : FCFA (XAF)",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
    }
}

@Composable
private fun InitialAdminForm(
    authError: String?,
    isLoading: Boolean,
    onSubmit: (String, String, String, Boolean) -> Unit,
    onClearError: () -> Unit
) {
    var nom by remember { mutableStateOf("") }
    var identifiant by remember { mutableStateOf("") }
    var motDePasse by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var avecDemo by remember { mutableStateOf(true) }
    var localError by remember { mutableStateOf<String?>(null) }

    fun submit() {
        localError = null
        onClearError()
        if (nom.trim().isEmpty()) {
            localError = "Le nom complet est obligatoire."
            return
        }
        if (identifiant.trim().length < 3) {
            localError = "L'identifiant doit contenir au moins 3 caractères."
            return
        }
        if (motDePasse.length < 4) {
            localError = "Le mot de passe doit contenir au moins 4 caractères."
            return
        }
        if (motDePasse != confirmation) {
            localError = "Les deux mots de passe ne correspondent pas."
            return
        }
        onSubmit(nom, identifiant, motDePasse, avecDemo)
    }

    Column(
        modifier = Modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Premier administrateur",
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary
        )
        Text(
            text = "Aucun compte par défaut n'existe. Ce compte disposera de tous les droits d'administration.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )

        OutlinedTextField(
            value = nom,
            onValueChange = { nom = it; localError = null; onClearError() },
            label = { Text("Nom complet *") },
            leadingIcon = { Icon(Icons.Filled.Badge, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("setup_nom_input"),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
        )

        OutlinedTextField(
            value = identifiant,
            onValueChange = { identifiant = it; localError = null; onClearError() },
            label = { Text("Identifiant de connexion *") },
            leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("setup_identifiant_input"),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
        )

        OutlinedTextField(
            value = motDePasse,
            onValueChange = { motDePasse = it; localError = null; onClearError() },
            label = { Text("Mot de passe (min. 4 car.) *") },
            leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = "Afficher ou masquer le mot de passe"
                    )
                }
            },
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("setup_password_input"),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next)
        )

        OutlinedTextField(
            value = confirmation,
            onValueChange = { confirmation = it; localError = null; onClearError() },
            label = { Text("Confirmer le mot de passe *") },
            leadingIcon = { Icon(Icons.Filled.LockReset, contentDescription = null) },
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("setup_confirm_password_input"),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() })
        )

        Surface(
            color = AppBackground,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, AppOutlineSubtle)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Charger des produits de démonstration",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary
                    )
                    Text(
                        "Inclut des produits disponibles, en stock faible et en rupture",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
                Switch(
                    checked = avecDemo,
                    onCheckedChange = { avecDemo = it },
                    modifier = Modifier.testTag("setup_demo_switch")
                )
            }
        }

        val errorToShow = localError ?: authError
        if (!errorToShow.isNullOrBlank()) {
            Surface(
                color = RuptureBackground,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, RuptureBorder)
            ) {
                Text(
                    text = errorToShow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    color = RuptureText,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Button(
            onClick = { submit() },
            enabled = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("setup_submit_button")
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Text("Créer et ouvrir le magasin")
            }
        }
    }
}

@Composable
private fun LoginForm(
    authError: String?,
    isLoading: Boolean,
    onSubmit: (String, String) -> Unit,
    onClearError: () -> Unit
) {
    var identifiant by remember { mutableStateOf("") }
    var motDePasse by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Connexion",
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary
        )

        OutlinedTextField(
            value = identifiant,
            onValueChange = { identifiant = it; onClearError() },
            label = { Text("Identifiant *") },
            leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("login_identifiant_input"),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
        )

        OutlinedTextField(
            value = motDePasse,
            onValueChange = { motDePasse = it; onClearError() },
            label = { Text("Mot de passe *") },
            leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = "Afficher ou masquer le mot de passe"
                    )
                }
            },
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("login_password_input"),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmit(identifiant, motDePasse) })
        )

        if (!authError.isNullOrBlank()) {
            Surface(
                color = RuptureBackground,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, RuptureBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = RuptureText, modifier = Modifier.size(18.dp))
                    Text(
                        text = authError,
                        color = RuptureText,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Button(
            onClick = { onSubmit(identifiant, motDePasse) },
            enabled = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("login_submit_button")
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Text("Se connecter")
            }
        }
    }
}
