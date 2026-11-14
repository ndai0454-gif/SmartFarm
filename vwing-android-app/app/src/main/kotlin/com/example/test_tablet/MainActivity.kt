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
    val activity = androidx.compose.ui.platform.LocalContext.current as? ComponentActivity
    val initialTool = when (activity?.intent?.getStringExtra("export_tool")) {
        "farmer" -> Tool.Farmer
        "ops" -> Tool.Ops
        else -> null
    }
    val startLandingExtra = activity?.intent?.getBooleanExtra("export_landing", false) ?: false
    var tool by rememberSaveable { mutableStateOf<Tool?>(initialTool) }
    val isProTheme = com.example.test_tablet.ui.vwing.VC.isPro
    androidx.compose.runtime.LaunchedEffect(tool, isProTheme) {
        // Tự động đồng bộ biểu tượng thanh trạng thái theo chế độ nền Sáng (Free Daylight) hoặc Tối (Enterprise Aurora / FlightOps).
        val transparent = android.graphics.Color.TRANSPARENT
        val useDarkBar = tool == Tool.Ops || (tool == null && isProTheme)
        val style = if (useDarkBar) androidx.activity.SystemBarStyle.dark(transparent) else androidx.activity.SystemBarStyle.light(transparent, transparent)
        activity?.enableEdgeToEdge(style, style)
    }

    when (tool) {
        null -> VWingApp(
            onOpenFarmer = { tool = Tool.Farmer },
            onOpenOps = { tool = Tool.Ops },
            startOnLanding = startLandingExtra,
        )
        Tool.Farmer -> FarmTheme {
            FarmerApp(onExit = { tool = null })
        }
        Tool.Ops -> OpsTheme {
            OpsApp(onExit = { tool = null }, onOpenFarmerApp = { tool = Tool.Farmer })
        }
    }
}
