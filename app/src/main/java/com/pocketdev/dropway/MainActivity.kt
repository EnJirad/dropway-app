package com.pocketdev.dropway

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.pocketdev.dropway.core.model.DeliveryStatus
import com.pocketdev.dropway.ui.components.DropwayBottomNavigation
import com.pocketdev.dropway.ui.components.DropwayTopBar
import com.pocketdev.dropway.ui.components.NavTab
import com.pocketdev.dropway.ui.screens.cod.CodDashboardScreen
import com.pocketdev.dropway.ui.screens.households.HouseholdDetailScreen
import com.pocketdev.dropway.ui.screens.households.HouseholdListScreen
import com.pocketdev.dropway.ui.screens.navigation.DeliveryMapScreen
import com.pocketdev.dropway.ui.screens.profile.ProfileScreen
import com.pocketdev.dropway.ui.screens.scanner.ScannerWorkspaceScreen
import com.pocketdev.dropway.ui.screens.today.TodayDeliveriesScreen
import com.pocketdev.dropway.ui.theme.DropwayBackground
import com.pocketdev.dropway.ui.theme.DropwayTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedText = if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)
        } else null

        setContent {
            DropwayTheme {
                DropwayMainApp(initialSharedText = sharedText)
            }
        }
    }
}

@Composable
fun DropwayMainApp(initialSharedText: String? = null) {
    val deliveryRepo = DropwayApplication.instance.deliveryRepository

    var selectedTab by remember {
        mutableStateOf(if (initialSharedText != null) NavTab.SCANNER else NavTab.HOUSEHOLDS)
    }

    var showTodayWorkspace by remember { mutableStateOf(false) }
    var selectedHouseholdId by remember { mutableStateOf<Long?>(null) }
    var showCodDashboard by remember { mutableStateOf(false) }

    var todayParcelsCount by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        val today = deliveryRepo.getOrCreateToday()
        deliveryRepo.observeParcelsForDay(today.id).collect { list ->
            todayParcelsCount = list.count { it.deliveryStatus != DeliveryStatus.CANCELLED }
        }
    }

    Scaffold(
        topBar = {
            if (!showTodayWorkspace && selectedHouseholdId == null && !showCodDashboard) {
                DropwayTopBar(
                    title = selectedTab.label,
                    todayParcelCount = todayParcelsCount,
                    onTodayClick = { showTodayWorkspace = true },
                    showTodayButton = true
                )
            }
        },
        bottomBar = {
            if (!showTodayWorkspace && selectedHouseholdId == null && !showCodDashboard) {
                DropwayBottomNavigation(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(DropwayBackground)
        ) {
            when {
                showTodayWorkspace -> {
                    TodayDeliveriesScreen(
                        onNavigateBack = { showTodayWorkspace = false },
                        onOpenScanner = {
                            showTodayWorkspace = false
                            selectedTab = NavTab.SCANNER
                        },
                        onOpenMap = {
                            showTodayWorkspace = false
                            selectedTab = NavTab.NAVIGATION
                        }
                    )
                }
                selectedHouseholdId != null -> {
                    HouseholdDetailScreen(
                        householdId = selectedHouseholdId!!,
                        onBack = { selectedHouseholdId = null }
                    )
                }
                showCodDashboard -> {
                    CodDashboardScreen()
                }
                else -> {
                    when (selectedTab) {
                        NavTab.HOUSEHOLDS -> HouseholdListScreen(
                            onHouseholdClick = { id -> selectedHouseholdId = id }
                        )
                        NavTab.SCANNER -> ScannerWorkspaceScreen(
                            initialSharedText = initialSharedText,
                            onParcelSaved = {
                                selectedTab = NavTab.HOUSEHOLDS
                                showTodayWorkspace = true
                            },
                            onBack = { selectedTab = NavTab.HOUSEHOLDS }
                        )
                        NavTab.NAVIGATION -> DeliveryMapScreen(
                            onParcelClick = {
                                showTodayWorkspace = true
                            }
                        )
                        NavTab.PROFILE -> ProfileScreen(
                            onOpenCodDashboard = {
                                showCodDashboard = true
                            }
                        )
                    }
                }
            }
        }
    }
}
