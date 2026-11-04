package com.example.test_tablet.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.test_tablet.ui.farmer.AnalyticsScreen
import com.example.test_tablet.ui.farmer.DashboardScreen
import com.example.test_tablet.ui.farmer.EudrScreen
import com.example.test_tablet.ui.farmer.FarmMapScreen
import com.example.test_tablet.ui.farmer.ProfileScreen
import com.example.test_tablet.ui.farmer.RecommendationScreen
import com.example.test_tablet.ui.ops.CalibrationScreen
import com.example.test_tablet.ui.ops.FlightPlanScreen
import com.example.test_tablet.ui.ops.QaScreen
import com.example.test_tablet.ui.ops.UploadStatusScreen
import com.example.test_tablet.ui.theme.FarmTheme
import com.example.test_tablet.ui.theme.OpsTheme

private const val SPEC = "spec:width=360dp,height=780dp"
private val NoPadding = PaddingValues(0.dp)

@Preview(name = "Farmer 1 - Dashboard", device = SPEC, showBackground = true)
@Composable
private fun FarmerDashboardPreview() = FarmTheme { DashboardScreen(NoPadding) {} }

@Preview(name = "Farmer 2 - Farm map", device = SPEC, showBackground = true)
@Composable
private fun FarmerMapPreview() = FarmTheme { FarmMapScreen(NoPadding) {} }

@Preview(name = "Farmer 3 - Analytics", device = SPEC, showBackground = true)
@Composable
private fun FarmerAnalyticsPreview() = FarmTheme { AnalyticsScreen(NoPadding) }

@Preview(name = "Farmer 4 - Recommendation", device = SPEC, showBackground = true)
@Composable
private fun FarmerRecommendationPreview() = FarmTheme { RecommendationScreen(onBack = {}) }

@Preview(name = "Farmer 5 - EUDR", device = SPEC, showBackground = true)
@Composable
private fun FarmerEudrPreview() = FarmTheme { EudrScreen(onBack = {}) }

@Preview(name = "Farmer 6 - Profile", device = SPEC, showBackground = true)
@Composable
private fun FarmerProfilePreview() = FarmTheme { ProfileScreen(NoPadding, {}, {}, {}) }

@Preview(name = "Ops 1 - Flight plan", device = SPEC, showBackground = true)
@Composable
private fun OpsFlightPlanPreview() = OpsTheme { FlightPlanScreen(NoPadding) }

@Preview(name = "Ops 2 - Upload status", device = SPEC, showBackground = true)
@Composable
private fun OpsUploadPreview() = OpsTheme { UploadStatusScreen(NoPadding) {} }

@Preview(name = "Ops 3 - Calibration", device = SPEC, showBackground = true)
@Composable
private fun OpsCalibrationPreview() = OpsTheme { CalibrationScreen(NoPadding) }

@Preview(name = "Ops 4 - QA", device = SPEC, showBackground = true)
@Composable
private fun OpsQaPreview() = OpsTheme { QaScreen(NoPadding) }
