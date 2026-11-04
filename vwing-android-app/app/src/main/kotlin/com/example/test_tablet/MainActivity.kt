package com.example.test_tablet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.example.test_tablet.ui.farmer.FarmerApp
import com.example.test_tablet.ui.ops.OpsApp
import com.example.test_tablet.ui.vwing.VWingApp
import com.example.test_tablet.ui.theme.FarmTheme
import com.example.test_tablet.ui.theme.OpsTheme

/** VWing là giao diện chính; FarmInsights và FlightOps mở như công cụ từ Thiết lập vườn. */
private enum class Tool { Farmer, Ops }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        setContent { AppRoot() }
    }
}

@Composable
fun AppRoot() {
    var tool by rememberSaveable { mutableStateOf<Tool?>(null) }
    val activity = androidx.compose.ui.platform.LocalContext.current as? ComponentActivity
    androidx.compose.runtime.LaunchedEffect(tool) {
        // Giao diện sáng (VWing, FarmInsights) dùng biểu tượng thanh trạng thái tối; FlightOps nền tối dùng biểu tượng sáng.
        val transparent = android.graphics.Color.TRANSPARENT
        val style = if (tool == Tool.Ops) androidx.activity.SystemBarStyle.dark(transparent) else androidx.activity.SystemBarStyle.light(transparent, transparent)
        activity?.enableEdgeToEdge(style, style)
    }

    when (tool) {
        null -> VWingApp(
            onOpenFarmer = { tool = Tool.Farmer },
            onOpenOps = { tool = Tool.Ops },
            startOnLanding = false,
        )
        Tool.Farmer -> FarmTheme {
            FarmerApp(onExit = { tool = null })
        }
        Tool.Ops -> OpsTheme {
            OpsApp(onExit = { tool = null }, onOpenFarmerApp = { tool = Tool.Farmer })
        }
    }
}
