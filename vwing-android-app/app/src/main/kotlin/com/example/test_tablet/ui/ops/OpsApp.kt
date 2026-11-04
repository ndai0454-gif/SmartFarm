package com.example.test_tablet.ui.ops

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Create
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

private enum class OpsTab(val label: String, val icon: ImageVector) {
    Plan("Kế hoạch", Icons.Filled.Place),
    Upload("Tải lên", Icons.AutoMirrored.Filled.Send),
    Calibrate("Hiệu chuẩn", Icons.Filled.Create),
    Qa("Rà soát ảnh", Icons.Filled.CheckCircle),
}

/** FlightOps: 4 tab — lập kế hoạch bay, trạng thái tải lên, chấm điểm hiệu chuẩn SAD, QA ảnh thô. */
@Composable
fun OpsApp(onExit: () -> Unit, onOpenFarmerApp: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(OpsTab.Plan) }

    BackHandler(enabled = tab != OpsTab.Plan) { tab = OpsTab.Plan }
    BackHandler(enabled = tab == OpsTab.Plan) { onExit() }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                OpsTab.entries.forEach { item ->
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
        when (tab) {
            OpsTab.Plan -> FlightPlanScreen(padding)
            OpsTab.Upload -> UploadStatusScreen(padding, onOpenFarmerApp)
            OpsTab.Calibrate -> CalibrationScreen(padding)
            OpsTab.Qa -> QaScreen(padding)
        }
    }
}
