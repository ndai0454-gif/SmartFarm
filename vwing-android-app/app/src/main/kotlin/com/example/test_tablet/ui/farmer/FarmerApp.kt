package com.example.test_tablet.ui.farmer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

private enum class FarmerTab(val label: String, val icon: ImageVector) {
    Home("Trang chủ", Icons.Filled.Home),
    Map("Bản đồ", Icons.Filled.Place),
    Analytics("Thống kê", Icons.Filled.DateRange),
    Profile("Hồ sơ", Icons.Filled.Person),
}

private enum class FarmerSub { None, Recommendation, Eudr }

/** FarmInsights: 4 tab chính + 2 màn phụ (đề xuất liều, hồ sơ EUDR) mở từ tab Hồ sơ / bản đồ. */
@Composable
fun FarmerApp(onExit: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(FarmerTab.Home) }
    var sub by rememberSaveable { mutableStateOf(FarmerSub.None) }

    BackHandler(enabled = sub != FarmerSub.None) { sub = FarmerSub.None }
    BackHandler(enabled = sub == FarmerSub.None && tab != FarmerTab.Home) { tab = FarmerTab.Home }
    BackHandler(enabled = sub == FarmerSub.None && tab == FarmerTab.Home) { onExit() }

    when (sub) {
        FarmerSub.Recommendation -> RecommendationScreen(onBack = { sub = FarmerSub.None })
        FarmerSub.Eudr -> EudrScreen(onBack = { sub = FarmerSub.None })
        FarmerSub.None -> Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                NavigationBar {
                    FarmerTab.entries.forEach { item ->
                        NavigationBarItem(
                            selected = tab == item,
                            onClick = { tab = item },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                        )
                    }
                }
            },
        ) { padding ->
            val openRecommendation = { sub = FarmerSub.Recommendation }
            when (tab) {
                FarmerTab.Home -> DashboardScreen(padding, onOpenAlert = { tab = FarmerTab.Map })
                FarmerTab.Map -> FarmMapScreen(padding, onOpenRecommendation = openRecommendation)
                FarmerTab.Analytics -> AnalyticsScreen(padding)
                FarmerTab.Profile -> ProfileScreen(
                    padding,
                    onOpenRecommendation = openRecommendation,
                    onOpenEudr = { sub = FarmerSub.Eudr },
                    onSwitchApp = onExit,
                )
            }
        }
    }
}
