package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.example.data.RoleUtilisateur
import com.example.data.UtilisateurEntity
import com.example.ui.screens.*
import com.example.ui.theme.*
import kotlinx.coroutines.launch

object AppRoutes {
    const val DASHBOARD = "dashboard"
    const val NEW_SALE = "new_sale"
    const val PRODUCTS = "products"
    const val STOCKS = "stocks"
    const val HISTORY = "history"
    const val REPORTS = "reports"
    const val ADMIN = "admin"
}

private data class NavDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val adminOnly: Boolean = false
)

private val ALL_DESTINATIONS = listOf(
    NavDestination(AppRoutes.DASHBOARD, "Tableau de bord", Icons.Filled.Dashboard),
    NavDestination(AppRoutes.NEW_SALE, "Nouvelle vente", Icons.Filled.PointOfSale),
    NavDestination(AppRoutes.PRODUCTS, "Produits", Icons.Filled.Inventory),
    NavDestination(AppRoutes.STOCKS, "Stocks", Icons.Filled.Warehouse),
    NavDestination(AppRoutes.HISTORY, "Historique", Icons.Filled.ReceiptLong),
    NavDestination(AppRoutes.REPORTS, "Rapports", Icons.Filled.BarChart, adminOnly = true),
    NavDestination(AppRoutes.ADMIN, "Administration", Icons.Filled.AdminPanelSettings, adminOnly = true)
)

@Composable
fun CaisseStockApp(viewModel: MainViewModel) {
    val hasAnyUser by viewModel.hasAnyUser.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val parametres by viewModel.parametres.collectAsStateWithLifecycle()
    val authError by viewModel.authError.collectAsStateWithLifecycle()
    val isAuthLoading by viewModel.isAuthLoading.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.feedback.collect { event ->
            snackbarHostState.showSnackbar(
                message = event.message,
                duration = SnackbarDuration.Short
            )
        }
    }

    if (currentUser == null) {
        LoginSetupScreen(
            hasAnyUser = hasAnyUser,
            storeName = parametres.nomCommercial,
            authError = authError,
            isLoading = isAuthLoading,
            onCreateInitialAdmin = viewModel::createInitialAdmin,
            onLogin = viewModel::login,
            onClearError = viewModel::clearAuthError
        )
    } else {
        AuthenticatedShell(
            viewModel = viewModel,
            currentUser = currentUser!!,
            snackbarHostState = snackbarHostState
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedShell(
    viewModel: MainViewModel,
    currentUser: UtilisateurEntity,
    snackbarHostState: SnackbarHostState
) {
    val isAdmin = currentUser.role == RoleUtilisateur.ADMINISTRATEUR.name
    val visibleDestinations = remember(isAdmin) {
        ALL_DESTINATIONS.filter { !it.adminOnly || isAdmin }
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: AppRoutes.DASHBOARD

    val parametres by viewModel.parametres.collectAsStateWithLifecycle()
    val indicateurs by viewModel.indicateursJour.collectAsStateWithLifecycle()
    val produits by viewModel.produits.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val mouvements by viewModel.mouvements.collectAsStateWithLifecycle()
    val ventes by viewModel.ventes.collectAsStateWithLifecycle()
    val utilisateurs by viewModel.utilisateurs.collectAsStateWithLifecycle()
    val audits by viewModel.audits.collectAsStateWithLifecycle()
    val backups by viewModel.backups.collectAsStateWithLifecycle()

    val stockFilter by viewModel.stockFilter.collectAsStateWithLifecycle()
    val historyPeriodFilter by viewModel.historyPeriodFilter.collectAsStateWithLifecycle()
    val cartItems by viewModel.cartItems.collectAsStateWithLifecycle()
    val paymentMode by viewModel.paymentMode.collectAsStateWithLifecycle()
    val cashReceivedText by viewModel.cashReceivedText.collectAsStateWithLifecycle()
    val isValidatingSale by viewModel.isValidatingSale.collectAsStateWithLifecycle()
    val saleError by viewModel.saleError.collectAsStateWithLifecycle()
    val priceChangePrompt by viewModel.priceChangePrompt.collectAsStateWithLifecycle()
    val completedReceipt by viewModel.completedReceipt.collectAsStateWithLifecycle()

    val rapportDebut by viewModel.rapportDebut.collectAsStateWithLifecycle()
    val rapportFin by viewModel.rapportFin.collectAsStateWithLifecycle()
    val rapportLignes by viewModel.rapportLignes.collectAsStateWithLifecycle()
    val rapportAnnulations by viewModel.rapportAnnulations.collectAsStateWithLifecycle()

    fun navigateTo(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpanded = maxWidth >= 760.dp

        val drawerContent: @Composable () -> Unit = {
            ModalDrawerSheet(
                modifier = Modifier.width(272.dp),
                drawerContainerColor = AppSurface
            ) {
                DrawerContentBody(
                    storeName = parametres.nomCommercial,
                    logoUri = parametres.logoUri,
                    currentUser = currentUser,
                    destinations = visibleDestinations,
                    currentRoute = currentRoute,
                    onSelectRoute = { route ->
                        navigateTo(route)
                        scope.launch { drawerState.close() }
                    },
                    onLogout = {
                        scope.launch { drawerState.close() }
                        viewModel.logout()
                    }
                )
            }
        }

        if (isExpanded) {
            PermanentNavigationDrawer(
                drawerContent = {
                    PermanentDrawerSheet(
                        modifier = Modifier.width(260.dp),
                        drawerContainerColor = AppSurface
                    ) {
                        DrawerContentBody(
                            storeName = parametres.nomCommercial,
                            logoUri = parametres.logoUri,
                            currentUser = currentUser,
                            destinations = visibleDestinations,
                            currentRoute = currentRoute,
                            onSelectRoute = ::navigateTo,
                            onLogout = viewModel::logout
                        )
                    }
                }
            ) {
                Scaffold(
                    containerColor = AppBackground,
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    contentWindowInsets = WindowInsets.safeDrawing
                ) { innerPadding ->
                    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                        AppNavHost(
                            navController = navController,
                            isAdmin = isAdmin,
                            currentUser = currentUser,
                            viewModel = viewModel,
                            parametres = parametres,
                            indicateurs = indicateurs,
                            produits = produits,
                            categories = categories,
                            mouvements = mouvements,
                            ventes = ventes,
                            utilisateurs = utilisateurs,
                            audits = audits,
                            backups = backups,
                            stockFilter = stockFilter,
                            historyPeriodFilter = historyPeriodFilter,
                            cartItems = cartItems,
                            paymentMode = paymentMode,
                            cashReceivedText = cashReceivedText,
                            isValidatingSale = isValidatingSale,
                            saleError = saleError,
                            priceChangePrompt = priceChangePrompt,
                            completedReceipt = completedReceipt,
                            rapportDebut = rapportDebut,
                            rapportFin = rapportFin,
                            rapportLignes = rapportLignes,
                            rapportAnnulations = rapportAnnulations,
                            onNavigate = ::navigateTo
                        )
                    }
                }
            }
        } else {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = drawerContent
            ) {
                val bottomRoutes = listOf(
                    AppRoutes.DASHBOARD,
                    AppRoutes.NEW_SALE,
                    AppRoutes.STOCKS,
                    AppRoutes.HISTORY
                )
                Scaffold(
                    containerColor = AppBackground,
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    topBar = {
                        TopAppBar(
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = AppSurface,
                                titleContentColor = TextPrimary
                            ),
                            navigationIcon = {
                                IconButton(
                                    onClick = { scope.launch { drawerState.open() } },
                                    modifier = Modifier.testTag("open_drawer_button")
                                ) {
                                    Icon(Icons.Filled.Menu, contentDescription = "Ouvrir le menu")
                                }
                            },
                            title = {
                                Column {
                                    Text(
                                        parametres.nomCommercial,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "${currentUser.nom} (${RoleUtilisateur.fromString(currentUser.role).libelle})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextMuted
                                    )
                                }
                            },
                            actions = {
                                IconButton(
                                    onClick = viewModel::logout,
                                    modifier = Modifier.testTag("topbar_logout_button")
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Logout,
                                        contentDescription = "Déconnexion",
                                        tint = TextSecondary
                                    )
                                }
                            }
                        )
                    },
                    bottomBar = {
                        NavigationBar(
                            containerColor = AppSurface,
                            windowInsets = WindowInsets.navigationBars
                        ) {
                            visibleDestinations.filter { it.route in bottomRoutes }.forEach { dest ->
                                val shortLabel = when (dest.route) {
                                    AppRoutes.DASHBOARD -> "Accueil"
                                    AppRoutes.NEW_SALE -> "Vente"
                                    AppRoutes.STOCKS -> "Stocks"
                                    else -> "Historique"
                                }
                                NavigationBarItem(
                                    selected = currentRoute == dest.route,
                                    onClick = { navigateTo(dest.route) },
                                    icon = { Icon(dest.icon, contentDescription = dest.label) },
                                    label = { Text(shortLabel, maxLines = 1) },
                                    modifier = Modifier.testTag("bottom_nav_${dest.route}")
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                        AppNavHost(
                            navController = navController,
                            isAdmin = isAdmin,
                            currentUser = currentUser,
                            viewModel = viewModel,
                            parametres = parametres,
                            indicateurs = indicateurs,
                            produits = produits,
                            categories = categories,
                            mouvements = mouvements,
                            ventes = ventes,
                            utilisateurs = utilisateurs,
                            audits = audits,
                            backups = backups,
                            stockFilter = stockFilter,
                            historyPeriodFilter = historyPeriodFilter,
                            cartItems = cartItems,
                            paymentMode = paymentMode,
                            cashReceivedText = cashReceivedText,
                            isValidatingSale = isValidatingSale,
                            saleError = saleError,
                            priceChangePrompt = priceChangePrompt,
                            completedReceipt = completedReceipt,
                            rapportDebut = rapportDebut,
                            rapportFin = rapportFin,
                            rapportLignes = rapportLignes,
                            rapportAnnulations = rapportAnnulations,
                            onNavigate = ::navigateTo
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerContentBody(
    storeName: String,
    logoUri: String,
    currentUser: UtilisateurEntity,
    destinations: List<NavDestination>,
    currentRoute: String,
    onSelectRoute: (String) -> Unit,
    onLogout: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (logoUri.isNotBlank()) {
                AsyncImage(
                    model = logoUri,
                    contentDescription = "Logo magasin",
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Surface(
                    color = ActionBlue,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Storefront, contentDescription = null, tint = AppSurface)
                    }
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(storeName, style = MaterialTheme.typography.titleMedium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${currentUser.nom} • ${RoleUtilisateur.fromString(currentUser.role).libelle}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        HorizontalDivider(color = AppOutlineSubtle)
        Spacer(modifier = Modifier.height(4.dp))

        destinations.forEach { dest ->
            NavigationDrawerItem(
                label = { Text(dest.label) },
                selected = currentRoute == dest.route,
                onClick = { onSelectRoute(dest.route) },
                icon = { Icon(dest.icon, contentDescription = null) },
                modifier = Modifier.testTag("drawer_nav_${dest.route}")
            )
        }

        Spacer(modifier = Modifier.weight(1f))
        HorizontalDivider(color = AppOutlineSubtle)
        NavigationDrawerItem(
            label = { Text("Déconnexion", color = RuptureText) },
            selected = false,
            onClick = onLogout,
            icon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = RuptureText) },
            modifier = Modifier.testTag("drawer_logout_button")
        )
    }
}

@Composable
private fun AppNavHost(
    navController: androidx.navigation.NavHostController,
    isAdmin: Boolean,
    currentUser: UtilisateurEntity,
    viewModel: MainViewModel,
    parametres: com.example.data.ParametresMagasin,
    indicateurs: com.example.data.IndicateursJour,
    produits: List<com.example.data.ProduitAvecStock>,
    categories: List<com.example.data.CategorieEntity>,
    mouvements: List<com.example.data.MouvementAvecDetails>,
    ventes: List<com.example.data.VenteAvecVendeur>,
    utilisateurs: List<UtilisateurEntity>,
    audits: List<com.example.data.AuditAvecAuteur>,
    backups: List<com.example.data.SauvegardeInfo>,
    stockFilter: StockFilterMode,
    historyPeriodFilter: HistoryPeriodFilter,
    cartItems: List<CartItem>,
    paymentMode: com.example.data.ModePaiement,
    cashReceivedText: String,
    isValidatingSale: Boolean,
    saleError: String?,
    priceChangePrompt: com.example.data.ValidationVenteResult.PrixModifie?,
    completedReceipt: com.example.data.VenteDetailComplet?,
    rapportDebut: String,
    rapportFin: String,
    rapportLignes: List<com.example.data.RapportProduitLigne>,
    rapportAnnulations: Pair<Int, Long>,
    onNavigate: (String) -> Unit
) {
    NavHost(
        navController = navController,
        startDestination = AppRoutes.DASHBOARD,
        modifier = Modifier.fillMaxSize()
    ) {
        composable(AppRoutes.DASHBOARD) {
            DashboardScreen(
                currentUser = currentUser,
                parametres = parametres,
                indicateurs = indicateurs,
                produits = produits,
                onOpenNewSale = { onNavigate(AppRoutes.NEW_SALE) },
                onOpenTodaySales = {
                    viewModel.setHistoryPeriodFilter(HistoryPeriodFilter.AUJOURDHUI)
                    onNavigate(AppRoutes.HISTORY)
                },
                onOpenStocksWithFilter = { mode ->
                    viewModel.setStockFilter(mode)
                    onNavigate(AppRoutes.STOCKS)
                },
                onOpenProducts = { onNavigate(AppRoutes.PRODUCTS) }
            )
        }
        composable(AppRoutes.NEW_SALE) {
            NewSaleScreen(
                produits = produits,
                categories = categories,
                parametres = parametres,
                cartItems = cartItems,
                paymentMode = paymentMode,
                cashReceivedText = cashReceivedText,
                isValidating = isValidatingSale,
                saleError = saleError,
                priceChangePrompt = priceChangePrompt,
                completedReceipt = completedReceipt,
                onAddToCart = viewModel::addToCart,
                onUpdateQuantity = viewModel::updateCartQuantity,
                onRemoveFromCart = viewModel::removeFromCart,
                onClearCart = viewModel::clearCart,
                onSetPaymentMode = viewModel::setPaymentMode,
                onSetCashText = viewModel::setCashReceivedText,
                onValidateSale = viewModel::submitSale,
                onConfirmPriceChange = viewModel::confirmUpdatedPricesAndRetry,
                onDismissPriceChange = viewModel::dismissPriceChangePrompt,
                onDismissReceipt = viewModel::dismissCompletedReceipt,
                onFeedback = viewModel::emitFeedback
            )
        }
        composable(AppRoutes.PRODUCTS) {
            ProductsScreen(
                isAdmin = isAdmin,
                produits = produits,
                categories = categories,
                parametres = parametres,
                onCreateCategory = viewModel::createCategory,
                onSaveProduct = viewModel::saveProduct,
                onToggleActive = viewModel::toggleProductActive
            )
        }
        composable(AppRoutes.STOCKS) {
            StocksScreen(
                isAdmin = isAdmin,
                produits = produits,
                mouvements = mouvements,
                parametres = parametres,
                stockFilter = stockFilter,
                onSetStockFilter = viewModel::setStockFilter,
                onRestock = viewModel::restockProduct,
                onCorrectInventory = viewModel::correctInventory
            )
        }
        composable(AppRoutes.HISTORY) {
            HistoryScreen(
                currentUser = currentUser,
                ventes = ventes,
                utilisateurs = utilisateurs,
                parametres = parametres,
                periodFilter = historyPeriodFilter,
                onSetPeriodFilter = viewModel::setHistoryPeriodFilter,
                onLoadSaleDetail = viewModel::loadSaleDetail,
                onCancelSale = viewModel::cancelSale,
                onExportSalesCsv = viewModel::exportSalesCsvToUri,
                onFeedback = viewModel::emitFeedback
            )
        }
        composable(AppRoutes.REPORTS) {
            if (isAdmin) {
                ReportsScreen(
                    parametres = parametres,
                    dateDebut = rapportDebut,
                    dateFin = rapportFin,
                    lignes = rapportLignes,
                    annulationsResume = rapportAnnulations,
                    produits = produits,
                    onLoadReport = viewModel::loadReport,
                    onExportReportCsv = viewModel::exportReportCsvToUri,
                    onExportStockCsv = viewModel::exportStockCsvToUri
                )
            } else {
                LaunchedEffect(Unit) { onNavigate(AppRoutes.DASHBOARD) }
            }
        }
        composable(AppRoutes.ADMIN) {
            if (isAdmin) {
                AdminScreen(
                    currentUser = currentUser,
                    utilisateurs = utilisateurs,
                    parametres = parametres,
                    backups = backups,
                    audits = audits,
                    onCreateUser = viewModel::createUser,
                    onToggleUserActive = viewModel::toggleUserActive,
                    onResetPassword = viewModel::resetUserPassword,
                    onSaveSettings = viewModel::saveStoreSettings,
                    onLogoPicked = viewModel::saveStoreLogoFromPicker,
                    onRefreshBackups = viewModel::refreshBackups,
                    onCreateManualBackup = viewModel::createManualBackup,
                    onExportBackup = viewModel::exportBackupToUri,
                    onRestoreLocalBackup = viewModel::restoreLocalBackup,
                    onRestoreExternalBackup = viewModel::restoreExternalBackup,
                    onLoadDemoDataset = viewModel::loadDemoDataset,
                    onResetDatabaseToBlank = viewModel::resetDatabaseToBlank
                )
            } else {
                LaunchedEffect(Unit) { onNavigate(AppRoutes.DASHBOARD) }
            }
        }
    }
}
