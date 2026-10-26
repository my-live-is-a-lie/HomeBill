package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.data.model.UiState
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.authStateFlow
import com.example.ui.auth.signOutUser
import com.example.ui.screens.AnalyticsScreen
import com.example.ui.screens.BillAssistantScreen
import com.example.ui.screens.CategoryDetailScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.ScannerScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.BillChatViewModel
import com.example.ui.viewmodel.HomeBillsViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.serialization.Serializable

@Serializable data object DashboardRoute

@Serializable data class ScannerRoute(val preselectedCategoryId: String? = null)

@Serializable data object AnalyticsRoute

@Serializable data class AssistantRoute(val initialPrompt: String? = null)

@Serializable data class CategoryDetailRoute(val categoryId: String)

@Serializable data object SettingsRoute

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    com.example.data.remote.UserGeminiKeyStore.init(this)
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    setContent {
      MyApplicationTheme {
        AppRootAuthGate()
      }
    }
  }
}

@Composable
fun AppRootAuthGate() {
  var isAuthResolved by remember { mutableStateOf(false) }
  var currentUserId by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(Unit) {
    // Allow ViewRootImpl / SurfaceSyncGroup to commit the initial window frame (<16ms)
    // before initializing Firebase Auth or Play Services IPC on the main thread.
    withFrameNanos {}
    val auth = Firebase.auth
    currentUserId = auth.currentUser?.uid
    isAuthResolved = true
    auth
      .authStateFlow()
      .distinctUntilChanged { old, new -> old?.uid == new?.uid }
      .collect { user ->
        currentUserId = user?.uid
        isAuthResolved = true
      }
  }

  val uid = currentUserId
  when {
    !isAuthResolved -> {
      Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        content = {},
      )
    }
    uid == null -> {
      AuthScreen(onAuthSuccess = {})
    }
    else -> {
      AuthenticatedAppShell(currentUserId = uid)
    }
  }
}

@Composable
fun AuthenticatedAppShell(currentUserId: String) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  val vm: HomeBillsViewModel =
    viewModel(
      key = currentUserId,
      factory = HomeBillsViewModel.factory(currentUserId),
    )

  val categoriesState by vm.categoriesState.collectAsStateWithLifecycle()
  val allBillsState by vm.allBillsState.collectAsStateWithLifecycle()
  val filteredBillsState by vm.filteredBillsState.collectAsStateWithLifecycle()
  val searchQuery by vm.searchQuery.collectAsStateWithLifecycle()
  val searchScope by vm.searchScope.collectAsStateWithLifecycle()
  val analyticsSummary by vm.analyticsSummary.collectAsStateWithLifecycle()
  val budgetDashboardState by vm.budgetDashboardState.collectAsStateWithLifecycle()
  val scanState by vm.scanState.collectAsStateWithLifecycle()
  val isSaving by vm.isSaving.collectAsStateWithLifecycle()
  val isReclassifying by vm.isReclassifying.collectAsStateWithLifecycle()
  val snackbarMessage by vm.snackbarMessage.collectAsStateWithLifecycle()

  val chatVm: BillChatViewModel = viewModel(key = "chat_$currentUserId")
  val chatMessages by chatVm.messages.collectAsStateWithLifecycle()
  val isChatTyping by chatVm.isTyping.collectAsStateWithLifecycle()

  val snackbarHostState = remember { SnackbarHostState() }
  LaunchedEffect(snackbarMessage) {
    val msg = snackbarMessage
    if (!msg.isNullOrBlank()) {
      snackbarHostState.showSnackbar(msg)
      vm.clearSnackbar()
    }
  }

  val navController = rememberNavController()
  val backStackEntry by navController.currentBackStackEntryAsState()
  val currentDest = backStackEntry?.destination

  val isDashboard = currentDest?.hasRoute<DashboardRoute>() == true
  val isScanner = currentDest?.hasRoute<ScannerRoute>() == true
  val isAnalytics = currentDest?.hasRoute<AnalyticsRoute>() == true
  val isAssistant = currentDest?.hasRoute<AssistantRoute>() == true
  val isSettings = currentDest?.hasRoute<SettingsRoute>() == true
  val showPrimaryNav = isDashboard || isScanner || isAnalytics || isAssistant || isSettings

  val categories = (categoriesState as? UiState.Success)?.data.orEmpty()
  val allBills = (allBillsState as? UiState.Success)?.data.orEmpty()

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val isExpanded = maxWidth >= 600.dp

    Scaffold(
      contentWindowInsets = WindowInsets(0, 0, 0, 0),
      snackbarHost = { SnackbarHost(snackbarHostState) },
      bottomBar = {
        if (!isExpanded && showPrimaryNav) {
          NavigationBar(
            containerColor = MaterialTheme.colorScheme.background,
          ) {
            NavigationBarItem(
              selected = isDashboard,
              onClick = {
                navController.navigate(DashboardRoute) {
                  popUpTo(DashboardRoute) { inclusive = false }
                  launchSingleTop = true
                }
              },
              icon = {
                Icon(
                  imageVector =
                    if (isDashboard) {
                      Icons.AutoMirrored.Filled.ReceiptLong
                    } else {
                      Icons.AutoMirrored.Outlined.ReceiptLong
                    },
                  contentDescription = stringResource(R.string.nav_home),
                )
              },
              label = { Text(stringResource(R.string.nav_home)) },
              modifier = Modifier.testTag("nav_home_tab"),
            )
            NavigationBarItem(
              selected = isScanner,
              onClick = {
                navController.navigate(ScannerRoute()) {
                  popUpTo(DashboardRoute) { inclusive = false }
                  launchSingleTop = true
                }
              },
              icon = {
                Icon(
                  imageVector =
                    if (isScanner) Icons.Filled.DocumentScanner else Icons.Outlined.DocumentScanner,
                  contentDescription = stringResource(R.string.nav_scan),
                )
              },
              label = { Text(stringResource(R.string.nav_scan)) },
              modifier = Modifier.testTag("nav_scan_tab"),
            )
            NavigationBarItem(
              selected = isAnalytics,
              onClick = {
                navController.navigate(AnalyticsRoute) {
                  popUpTo(DashboardRoute) { inclusive = false }
                  launchSingleTop = true
                }
              },
              icon = {
                Icon(
                  imageVector = if (isAnalytics) Icons.Filled.BarChart else Icons.Outlined.BarChart,
                  contentDescription = stringResource(R.string.nav_analytics),
                )
              },
              label = { Text(stringResource(R.string.nav_analytics)) },
              modifier = Modifier.testTag("nav_analytics_tab"),
            )
            NavigationBarItem(
              selected = isAssistant,
              onClick = {
                navController.navigate(AssistantRoute()) {
                  popUpTo(DashboardRoute) { inclusive = false }
                  launchSingleTop = true
                }
              },
              icon = {
                Icon(
                  imageVector =
                    if (isAssistant) Icons.Filled.AutoAwesome else Icons.Outlined.AutoAwesome,
                  contentDescription = stringResource(R.string.nav_assistant),
                )
              },
              label = { Text(stringResource(R.string.nav_assistant)) },
              modifier = Modifier.testTag("nav_assistant_tab"),
            )
            NavigationBarItem(
              selected = isSettings,
              onClick = {
                navController.navigate(SettingsRoute) {
                  popUpTo(DashboardRoute) { inclusive = false }
                  launchSingleTop = true
                }
              },
              icon = {
                Icon(
                  imageVector = if (isSettings) Icons.Filled.Settings else Icons.Outlined.Settings,
                  contentDescription = stringResource(R.string.nav_settings),
                )
              },
              label = { Text(stringResource(R.string.nav_settings)) },
              modifier = Modifier.testTag("nav_settings_tab"),
            )
          }
        }
      },
    ) { scaffoldPadding ->
      Row(modifier = Modifier.fillMaxSize().padding(scaffoldPadding)) {
        if (isExpanded && showPrimaryNav) {
          NavigationRail(
            modifier = Modifier.fillMaxHeight(),
            containerColor = MaterialTheme.colorScheme.background,
          ) {
            NavigationRailItem(
              selected = isDashboard,
              onClick = {
                navController.navigate(DashboardRoute) {
                  popUpTo(DashboardRoute) { inclusive = false }
                  launchSingleTop = true
                }
              },
              icon = {
                Icon(
                  imageVector =
                    if (isDashboard) {
                      Icons.AutoMirrored.Filled.ReceiptLong
                    } else {
                      Icons.AutoMirrored.Outlined.ReceiptLong
                    },
                  contentDescription = stringResource(R.string.nav_home),
                )
              },
              label = { Text(stringResource(R.string.nav_home)) },
            )
            NavigationRailItem(
              selected = isScanner,
              onClick = {
                navController.navigate(ScannerRoute()) {
                  popUpTo(DashboardRoute) { inclusive = false }
                  launchSingleTop = true
                }
              },
              icon = {
                Icon(
                  imageVector =
                    if (isScanner) Icons.Filled.DocumentScanner else Icons.Outlined.DocumentScanner,
                  contentDescription = stringResource(R.string.nav_scan),
                )
              },
              label = { Text(stringResource(R.string.nav_scan)) },
            )
            NavigationRailItem(
              selected = isAnalytics,
              onClick = {
                navController.navigate(AnalyticsRoute) {
                  popUpTo(DashboardRoute) { inclusive = false }
                  launchSingleTop = true
                }
              },
              icon = {
                Icon(
                  imageVector = if (isAnalytics) Icons.Filled.BarChart else Icons.Outlined.BarChart,
                  contentDescription = stringResource(R.string.nav_analytics),
                )
              },
              label = { Text(stringResource(R.string.nav_analytics)) },
            )
            NavigationRailItem(
              selected = isAssistant,
              onClick = {
                navController.navigate(AssistantRoute()) {
                  popUpTo(DashboardRoute) { inclusive = false }
                  launchSingleTop = true
                }
              },
              icon = {
                Icon(
                  imageVector =
                    if (isAssistant) Icons.Filled.AutoAwesome else Icons.Outlined.AutoAwesome,
                  contentDescription = stringResource(R.string.nav_assistant),
                )
              },
              label = { Text(stringResource(R.string.nav_assistant)) },
            )
            NavigationRailItem(
              selected = isSettings,
              onClick = {
                navController.navigate(SettingsRoute) {
                  popUpTo(DashboardRoute) { inclusive = false }
                  launchSingleTop = true
                }
              },
              icon = {
                Icon(
                  imageVector = if (isSettings) Icons.Filled.Settings else Icons.Outlined.Settings,
                  contentDescription = stringResource(R.string.nav_settings),
                )
              },
              label = { Text(stringResource(R.string.nav_settings)) },
            )
          }
        }

        Box(modifier = Modifier.weight(1f).fillMaxSize()) {
          NavHost(
            navController = navController,
            startDestination = DashboardRoute,
          ) {
            composable<DashboardRoute> {
              DashboardScreen(
                categoriesState = categoriesState,
                allBillsState = allBillsState,
                filteredBillsState = filteredBillsState,
                searchQuery = searchQuery,
                searchScope = searchScope,
                analyticsSummary = analyticsSummary,
                currentUserId = currentUserId,
                onSearchQueryChange = vm::updateSearchQuery,
                onSearchScopeChange = vm::updateSearchScope,
                onClearSearch = vm::clearSearch,
                onSaveBill = { bill, threshold, catBills ->
                  vm.saveBill(
                    bill = bill,
                    categoryThresholdPercent = threshold,
                    existingCategoryBills = catBills,
                  )
                },
                onOpenCategory = { catId ->
                  navController.navigate(CategoryDetailRoute(catId))
                },
                onOpenScanner = {
                  navController.navigate(ScannerRoute())
                },
                onOpenAssistant = {
                  navController.navigate(AssistantRoute()) {
                    popUpTo(DashboardRoute) { inclusive = false }
                    launchSingleTop = true
                  }
                },
                onCreateCategory = { name, uType, unit, threshold ->
                  vm.createCategory(name, uType, unit, threshold) { created ->
                    navController.navigate(CategoryDetailRoute(created.id))
                  }
                },
                onSignOut = {
                  signOutUser(
                    credentialManager = CredentialManager.create(context),
                    scope = scope,
                  )
                },
                budgetDashboardState = budgetDashboardState,
                onOpenAnalyticsDashboard = {
                  navController.navigate(AnalyticsRoute) {
                    popUpTo(DashboardRoute) { inclusive = false }
                    launchSingleTop = true
                  }
                },
                onSaveHouseholdBudget = { limit, curr, warn ->
                  vm.saveHouseholdBudget(limit, curr, warn)
                },
              )
            }

            composable<AssistantRoute> { entry ->
              val route = entry.toRoute<AssistantRoute>()
              LaunchedEffect(route.initialPrompt) {
                chatVm.consumeInitialPromptIfNeeded(
                  prompt = route.initialPrompt,
                  categories = categories,
                  allBills = allBills,
                  analyticsSummary = analyticsSummary,
                )
              }
              BillAssistantScreen(
                messages = chatMessages,
                isTyping = isChatTyping,
                categories = categories,
                allBills = allBills,
                analyticsSummary = analyticsSummary,
                onSendMessage = { prompt ->
                  chatVm.sendMessage(
                    prompt = prompt,
                    categories = categories,
                    allBills = allBills,
                    analyticsSummary = analyticsSummary,
                  )
                },
                onRetryLastMessage = {
                  chatVm.retryLastUserMessage(
                    categories = categories,
                    allBills = allBills,
                    analyticsSummary = analyticsSummary,
                  )
                },
                onClearChat = { chatVm.clearConversation() },
                onOpenSettings = {
                  navController.navigate(SettingsRoute) {
                    popUpTo(DashboardRoute) { inclusive = false }
                    launchSingleTop = true
                  }
                },
              )
            }

            composable<ScannerRoute> { entry ->
              val route = entry.toRoute<ScannerRoute>()
              ScannerScreen(
                scanState = scanState,
                categories = categories,
                allBills = allBills,
                preselectedCategoryId = route.preselectedCategoryId,
                isSaving = isSaving,
                isReclassifying = isReclassifying,
                onImageSelected = { uri ->
                  vm.scanBillImage(context, uri, route.preselectedCategoryId)
                },
                onUpdateDraft = { updated -> vm.updateScannedDraft(updated) },
                onReclassifyDraft = { vm.reclassifyScannedDraft() },
                onSaveScannedBill = { draft, chosenCatId ->
                  vm.saveScannedBillToCategory(draft, chosenCatId) { savedCatId ->
                    navController.navigate(CategoryDetailRoute(savedCatId)) {
                      popUpTo(DashboardRoute) { inclusive = false }
                    }
                  }
                },
                onResetScan = { vm.resetScanState() },
              )
            }

            composable<AnalyticsRoute> {
              AnalyticsScreen(
                categories = categories,
                allBills = allBills,
                summary = analyticsSummary,
                budgetDashboardState = budgetDashboardState,
                onSelectMonth = { ym -> vm.selectDashboardMonth(ym) },
                onSaveHouseholdBudget = { limit, curr, warn ->
                  vm.saveHouseholdBudget(limit, curr, warn)
                },
                onOpenCategory = { catId ->
                  navController.navigate(CategoryDetailRoute(catId))
                },
              )
            }

            composable<SettingsRoute> {
              SettingsScreen()
            }

            composable<CategoryDetailRoute> { entry ->
              val route = entry.toRoute<CategoryDetailRoute>()
              val category = categories.firstOrNull { it.id == route.categoryId }
              val catBills =
                remember(allBills, route.categoryId) {
                  allBills.filter { it.categoryId == route.categoryId }
                }
              CategoryDetailScreen(
                category = category,
                categoryBills = catBills,
                currentUserId = currentUserId,
                onNavigateBack = { navController.popBackStack() },
                onScanForCategory = { catId ->
                  navController.navigate(ScannerRoute(preselectedCategoryId = catId))
                },
                onSaveBill = { bill ->
                  vm.saveBill(
                    bill = bill,
                    categoryThresholdPercent = category?.alertThresholdPercent ?: 25.0,
                    existingCategoryBills = catBills,
                  )
                },
                onDeleteBill = { billId -> vm.deleteBill(billId) },
                onUpdateCategory = { updated -> vm.updateCategory(updated) },
                onDeleteCategory = { catId ->
                  vm.deleteCategory(catId) { navController.popBackStack() }
                },
              )
            }
          }
        }
      }
    }
  }
}
