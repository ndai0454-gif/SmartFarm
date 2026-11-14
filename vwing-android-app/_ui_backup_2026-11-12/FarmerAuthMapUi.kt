package com.example.test_tablet.ui.vwing

import android.app.Activity
import android.content.pm.PackageManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import com.example.test_tablet.data.vwing.FeatureKind
import com.example.test_tablet.data.vwing.FirebaseFarmerRepo
import com.example.test_tablet.data.vwing.FirebaseProjectConfig
import com.example.test_tablet.data.vwing.GeoPoint
import com.example.test_tablet.data.vwing.SatelliteClient
import com.example.test_tablet.data.vwing.VJson
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.DragState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polygon
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Hộp thoại toàn màn hình / nổi phong cách Aurora Liquid-Glass:
 * Đăng nhập Nông dân bằng Số điện thoại + Mã OTP (Firebase Auth + Firestore)
 * và Định vị địa chỉ vườn trực tiếp trên Google Map.
 */
@Composable
fun FarmerAuthMapDialog(
    c: VCtx,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xCC020A10))
                .padding(horizontal = if (c.wide) 48.dp else 12.dp, vertical = if (c.wide) 24.dp else 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        if (VC.isPro) {
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF0A2130),
                                    Color(0xFF071824),
                                )
                            )
                        } else {
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFFF5F8F6),
                                    Color(0xFFFFFFFF),
                                )
                            )
                        }
                    )
                    .border(1.2.dp, if (VC.isPro) VC.mint.copy(alpha = 0.40f) else VC.border, RoundedCornerShape(28.dp))
                    .padding(if (c.wide) 24.dp else 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        LogoPlate(32.dp)
                        Column {
                            Text(
                                "Số điện thoại & Vị trí vườn",
                                color = VC.text,
                                fontFamily = VDisplayFont,
                                fontSize = if (c.wide) 21.sp else 17.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                            )
                            Text(
                                "Lưu riêng sổ vườn theo số điện thoại của bác và chọn vị trí vườn trên bản đồ",
                                color = VC.text2,
                                fontSize = 13.sp,
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(VC.elevated)
                            .border(1.dp, VC.border, RoundedCornerShape(12.dp)),
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Đóng", tint = VC.text)
                    }
                }

                FarmerAccountMapCard(c = c, onCloseDialog = onDismiss)
            }
        }
    }
}

/**
 * Thẻ Đăng nhập SĐT + OTP (Firebase) & Định vị Địa chỉ trên Google Map.
 * Dùng được cả trong Hộp thoại nhanh (bấm từ thanh trên cùng) lẫn trong mục Cài đặt vườn.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FarmerAccountMapCard(
    c: VCtx,
    onCloseDialog: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    val currentFarmer = FirebaseFarmerRepo.currentFarmer
    val savedFarmers = FirebaseFarmerRepo.allSavedFarmers
    val syncStatusNote = FirebaseFarmerRepo.syncStatusNote

    var phoneInput by remember(currentFarmer?.phoneKey) {
        mutableStateOf(currentFarmer?.displayPhone ?: "")
    }
    var farmerNameInput by remember(currentFarmer?.phoneKey) {
        mutableStateOf(currentFarmer?.farmerName ?: c.d.garden.operatorName)
    }
    var gardenNameInput by remember(currentFarmer?.phoneKey) {
        mutableStateOf(currentFarmer?.gardenName ?: c.d.garden.name)
    }
    var otpSent by remember(currentFarmer?.phoneKey) { mutableStateOf(false) }
    var otpHint by remember(currentFarmer?.phoneKey) { mutableStateOf("") }
    var otpCodeInput by remember(currentFarmer?.phoneKey) { mutableStateOf("") }
    var authBusy by remember(currentFarmer?.phoneKey) { mutableStateOf(false) }
    var authError by remember(currentFarmer?.phoneKey) { mutableStateOf<String?>(null) }
    var authInfo by remember(currentFarmer?.phoneKey) { mutableStateOf<String?>(null) }
    var geocodeJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    var showPlaceSearch by remember { mutableStateOf(false) }

    val initialCenter = remember(currentFarmer?.phoneKey, c.d.garden.name) {
        val ring = SatelliteClient.ringOf(c.d)
        val lat = currentFarmer?.lat?.takeIf { it != 0.0 }
            ?: ring?.map { it.lat }?.average()
            ?: 11.547820
        val lng = currentFarmer?.lng?.takeIf { it != 0.0 }
            ?: ring?.map { it.lng }?.average()
            ?: 107.793480
        GeoPoint(lat, lng)
    }

    var pinLat by remember(currentFarmer?.phoneKey) { mutableDoubleStateOf(initialCenter.lat) }
    var pinLng by remember(currentFarmer?.phoneKey) { mutableDoubleStateOf(initialCenter.lng) }
    var latText by remember(currentFarmer?.phoneKey) { mutableStateOf("%.6f".format(initialCenter.lat)) }
    var lngText by remember(currentFarmer?.phoneKey) { mutableStateOf("%.6f".format(initialCenter.lng)) }
    var addressText by remember(currentFarmer?.phoneKey) {
        mutableStateOf(
            currentFarmer?.address?.ifBlank { c.d.garden.location }
                ?: c.d.garden.location
        )
    }
    var geocodingBusy by remember { mutableStateOf(false) }

    val cameraState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(pinLat, pinLng), 16.5f)
    }

    fun movePinAndResolveAddress(newLat: Double, newLng: Double, animateMap: Boolean = true) {
        pinLat = newLat
        pinLng = newLng
        latText = "%.6f".format(newLat)
        lngText = "%.6f".format(newLng)
        if (animateMap) {
            scope.launch {
                runCatching {
                    cameraState.animate(CameraUpdateFactory.newLatLngZoom(LatLng(newLat, newLng), 17f))
                }
            }
        }
        geocodingBusy = true
        geocodeJob?.cancel()
        geocodeJob = scope.launch {
            val resolved = withContext(Dispatchers.IO) {
                FirebaseFarmerRepo.resolveAddressFromCoordinates(context, newLat, newLng)
            }
            geocodingBusy = false
            if (resolved.isNotBlank()) {
                addressText = resolved
            }
        }
    }

    val ensureLoc = rememberEnsureLocationPermission(c)
    val gpsFetcher = rememberLocationFetcher(c) { la, lo, _ ->
        movePinAndResolveAddress(la, lo, animateMap = true)
        c.toast("📍 Đã lấy vị trí hiện tại và tự điền địa chỉ xã/huyện.")
    }

    val mapsKeyOk = remember {
        runCatching {
            val ai = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
            val k = ai.metaData?.getString("com.google.android.geo.API_KEY") ?: ""
            k.isNotBlank() && !k.startsWith("YOUR_")
        }.getOrDefault(false)
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // 1. Trạng thái phiên đăng nhập hiện tại & Chuyển đổi nhanh tài khoản Nông dân
        VCard(
            Modifier.fillMaxWidth(),
            borderColor = if (currentFarmer != null) VC.mint.copy(alpha = 0.55f) else VC.border,
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (currentFarmer != null) VC.pine else VC.elevated)
                        .border(1.dp, if (currentFarmer != null) VC.lime.copy(alpha = 0.65f) else VC.border, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (currentFarmer != null) Icons.Filled.CloudDone else Icons.Filled.PhoneAndroid,
                        contentDescription = null,
                        tint = if (currentFarmer != null) VC.lime else VC.mint,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        if (currentFarmer != null) {
                            "${currentFarmer.farmerName} · ${currentFarmer.displayPhone}"
                        } else {
                            "Chưa nhập số điện thoại (Đang xem vườn mẫu)"
                        },
                        color = VC.text,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        if (currentFarmer != null) {
                            "🏡 ${currentFarmer.gardenName} · 📍 ${currentFarmer.address.ifBlank { c.d.garden.location.ifBlank { "Chưa chọn vị trí" } }}"
                        } else {
                            "Nhập số điện thoại bên dưới để lưu riêng sổ vườn của bác."
                        },
                        color = VC.text2,
                        fontSize = 13.sp,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                VButton(
                    "🔄 Đổi số điện thoại",
                    {
                        onCloseDialog?.invoke()
                        FirebaseFarmerRepo.openLoginGate()
                    },
                    modifier = Modifier.weight(1.1f),
                    primary = false,
                )
                if (currentFarmer != null) {
                    VButton(
                        "🚪 Thoát ra",
                        {
                            onCloseDialog?.invoke()
                            FirebaseFarmerRepo.signOutFarmer(context, c.store)
                            c.toast("Đã thoát ra màn hình nhập số điện thoại.")
                        },
                        modifier = Modifier.weight(0.9f),
                        primary = false,
                        danger = true,
                    )
                }
            }

            if (!syncStatusNote.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (VC.isPro) Color(0xFF071C28) else Color(0xFFEEF6F1))
                        .border(1.dp, VC.mint.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(VC.lime))
                    Text(
                        syncStatusNote,
                        color = VC.mint,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    if (currentFarmer != null) {
                        Text(
                            "🔄 Lưu lại ngay",
                            color = if (VC.isPro) VC.lime else Color.White,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(VC.pine)
                                .clickable {
                                    FirebaseFarmerRepo.pushFarmerDataToFirestore(
                                        context = context,
                                        profile = currentFarmer,
                                        snapshotJson = VJson.toJson(c.store.data).toString(),
                                        gardenData = c.store.data,
                                    )
                                    c.toast("Đã lưu sổ vườn của số ${currentFarmer.displayPhone}.")
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            if (savedFarmers.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Eyebrow("CÁC SỐ ĐIỆN THOẠI ĐÃ LƯU (${savedFarmers.size})")
                Text(
                    "Chạm vào số điện thoại để mở sổ vườn của người đó:",
                    color = VC.muted,
                    fontSize = 12.5.sp,
                    modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    savedFarmers.forEach { prof ->
                        val isCurrent = currentFarmer?.phoneKey == prof.phoneKey
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isCurrent) VC.pine else VC.elevated)
                                .border(
                                    1.dp,
                                    if (isCurrent) VC.lime else VC.border,
                                    RoundedCornerShape(14.dp),
                                )
                                .bounceClick {
                                    FirebaseFarmerRepo.switchActiveFarmer(context, c.store, prof)
                                    pinLat = prof.lat
                                    pinLng = prof.lng
                                    latText = "%.6f".format(prof.lat)
                                    lngText = "%.6f".format(prof.lng)
                                    addressText = prof.address
                                    scope.launch {
                                        runCatching {
                                            cameraState.animate(
                                                CameraUpdateFactory.newLatLngZoom(LatLng(prof.lat, prof.lng), 17f)
                                            )
                                        }
                                    }
                                    c.toast("Đã mở sổ vườn của ${prof.displayPhone}")
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                if (isCurrent) "●" else "📱",
                                color = if (isCurrent) (if (VC.isPro) VC.lime else Color.White) else VC.mint,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Column {
                                val tierText = if (prof.planTier == com.example.test_tablet.data.vwing.PlanTier.ENTERPRISE) "Pro Account" else "Basic Account"
                                Text(
                                    "${prof.displayPhone} · $tierText",
                                    color = if (isCurrent) Color.White else VC.text,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    "${prof.farmerName} · ${prof.address}",
                                    color = if (isCurrent) Color.White.copy(alpha = 0.85f) else VC.text2,
                                    fontSize = 11.5.sp,
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Khung chia 2 cột trên Tablet (Trái: Nhập SĐT | Phải: Chọn vị trí vườn trên bản đồ)
        val authColumn: @Composable (Modifier) -> Unit = { mod ->
            VCard(mod) {
                Eyebrow("1. ĐĂNG NHẬP BẰNG SỐ ĐIỆN THOẠI")
                Text(
                    "Nhập số điện thoại và mã 6 số để mở sổ vườn riêng của bác.",
                    color = VC.text2,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    VField(
                        value = phoneInput,
                        onChange = { phoneInput = it; authError = null },
                        label = "Số điện thoại của bác",
                        placeholder = "0912 345 678",
                        number = true,
                    )

                    VButton(
                        text = if (authBusy) "Đang lấy mã…" else "📲 Lấy mã 6 số",
                        onClick = {
                            authBusy = true
                            authError = null
                            FirebaseFarmerRepo.requestPhoneOtp(
                                context = context,
                                activity = activity,
                                rawPhone = phoneInput,
                                onOtpSent = { _, instantHint, _ ->
                                    authBusy = false
                                    otpSent = true
                                    otpHint = instantHint
                                    otpCodeInput = ""
                                    authInfo = "Mã 6 số của bác là: $instantHint"
                                    c.toast("📲 Mã 6 số của bác là $instantHint")
                                },
                                onError = { err ->
                                    authBusy = false
                                    authError = err
                                },
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !authBusy,
                    )

                    AnimatedVisibility(visible = otpSent || otpCodeInput.isNotBlank()) {
                        OtpSixBoxesPanel(
                            phoneDisplay = phoneInput,
                            otpCode = otpCodeInput,
                            otpHint = otpHint,
                            busy = authBusy,
                            onOtpChange = {
                                otpCodeInput = it.filter(Char::isDigit).take(6)
                                authError = null
                            },
                            onResendOtp = {
                                authBusy = true
                                authError = null
                                FirebaseFarmerRepo.requestPhoneOtp(
                                    context = context,
                                    activity = activity,
                                    rawPhone = phoneInput,
                                    onOtpSent = { _, instantHint, _ ->
                                        authBusy = false
                                        otpSent = true
                                        otpHint = instantHint
                                        otpCodeInput = ""
                                        authInfo = "Mã 6 số mới của bác là: $instantHint"
                                    },
                                    onError = { err ->
                                        authBusy = false
                                        authError = err
                                    },
                                )
                            },
                            onVerifyOtp = {
                                authBusy = true
                                authError = null
                                FirebaseFarmerRepo.verifyOtpAndSignInFarmer(
                                    context = context,
                                    store = c.store,
                                    rawPhone = phoneInput,
                                    otpCode = otpCodeInput,
                                    lat = pinLat,
                                    lng = pinLng,
                                    address = addressText,
                                    onSuccess = { profile ->
                                        authBusy = false
                                        authInfo = "Đã lưu số điện thoại ${profile.displayPhone}!"
                                        c.toast("🎉 Đã mở sổ vườn của số ${profile.displayPhone}!")
                                    },
                                    onError = { err ->
                                        authBusy = false
                                        authError = err
                                    },
                                )
                            },
                        )
                    }

                    authError?.let { errText ->
                        Text(
                            "✕ $errText",
                            color = VC.red,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    authInfo?.let { infoText ->
                        Text(
                            "✓ $infoText",
                            color = VC.mint,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }

        val mapColumn: @Composable (Modifier) -> Unit = { mod ->
            VCard(mod) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Eyebrow("2. VỊ TRÍ KHU VƯỜN CỦA BÁC")
                    if (geocodingBusy) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = VC.lime)
                            Text("Đang tìm tên xã/huyện…", color = VC.lime, fontSize = 12.sp)
                        }
                    }
                }
                Text(
                    "Chạm vào bản đồ bên dưới hoặc bấm \"Lấy vị trí hiện tại\" để tự điền tên Xã, Huyện, Tỉnh của vườn.",
                    color = VC.text2,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )

                // Hàng nút công cụ định vị nhanh (giới hạn độ rộng tối đa trên Tablet để không bị kéo giãn quá dài)
                Row(
                    Modifier.fillMaxWidth().widthIn(max = 560.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    VButton(
                        text = if (c.wide) "🎯 Lấy vị trí hiện tại" else "🎯 Vị trí hiện tại",
                        onClick = { ensureLoc { gpsFetcher() } },
                        primary = true,
                        icon = Icons.Filled.MyLocation,
                        modifier = Modifier.weight(1f),
                    )
                    VButton(
                        text = if (c.wide) "🔍 Tìm tên Xã / Huyện…" else "🔍 Tìm Xã / Huyện",
                        onClick = { showPlaceSearch = true },
                        primary = false,
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Khung Bản đồ tương tác
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(if (c.wide) 290.dp else 240.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF05141E))
                        .border(1.2.dp, VC.mint.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                ) {
                    if (mapsKeyOk) {
                        val ring = remember(c.d.features) {
                            c.d.features.firstOrNull { it.kind == FeatureKind.BOUNDARY }?.points
                        }
                        val markerState = rememberMarkerState(
                            key = "farmer_pin_${"%.5f".format(pinLat)}_${"%.5f".format(pinLng)}",
                            position = LatLng(pinLat, pinLng),
                        )
                        LaunchedEffect(markerState.dragState) {
                            if (markerState.dragState == DragState.END) {
                                val pos = markerState.position
                                movePinAndResolveAddress(pos.latitude, pos.longitude, animateMap = false)
                            }
                        }

                        GoogleMap(
                            modifier = Modifier.fillMaxSize(),
                            cameraPositionState = cameraState,
                            properties = MapProperties(mapType = MapType.HYBRID),
                            uiSettings = MapUiSettings(
                                zoomControlsEnabled = true,
                                mapToolbarEnabled = false,
                                myLocationButtonEnabled = false,
                            ),
                            onMapClick = { latLng ->
                                movePinAndResolveAddress(latLng.latitude, latLng.longitude, animateMap = false)
                            },
                        ) {
                            if (ring != null && ring.size >= 3) {
                                Polygon(
                                    points = ring.map { LatLng(it.lat, it.lng) },
                                    fillColor = Color(0x22A8DF65),
                                    strokeColor = Color(0xFFFFEA00),
                                    strokeWidth = 4.5f,
                                )
                            }
                            Marker(
                                state = markerState,
                                draggable = true,
                                title = gardenNameInput.ifBlank { "Vị trí vườn của tôi" },
                                snippet = addressText,
                            )
                        }
                    } else {
                        Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                            Text(
                                "Bấm \"Lấy vị trí hiện tại\" hoặc nhập tên Xã, Huyện, Tỉnh ở ô bên dưới.",
                                color = VC.text2,
                                fontSize = 13.sp,
                            )
                        }
                    }

                    // Nhãn nổi hiển thị địa chỉ ngay trên góc trên bản đồ
                    Row(
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xD9051622))
                            .border(1.dp, VC.mint.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Filled.LocationOn, contentDescription = null, tint = VC.lime, modifier = Modifier.size(16.dp))
                        Text(
                            addressText.ifBlank { "Chạm vào bản đồ để lấy địa chỉ" },
                            color = Color.White,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                VField(
                    value = addressText,
                    onChange = { addressText = it },
                    label = "Địa chỉ khu vườn (Xã, Huyện, Tỉnh)",
                    placeholder = "Ví dụ: Xã Đamb'ri, TP. Bảo Lộc, Lâm Đồng",
                )

                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    VButton(
                        text = "💾 Lưu vị trí vườn",
                        onClick = {
                            FirebaseFarmerRepo.updateFarmerLocationOnMap(
                                context = context,
                                store = c.store,
                                lat = pinLat,
                                lng = pinLng,
                                address = addressText,
                            )
                            c.toast("📍 Đã lưu địa chỉ vườn: $addressText")
                        },
                        modifier = Modifier.weight(1.3f),
                        icon = Icons.Filled.Check,
                    )
                    VButton(
                        text = "Mở Khoanh vườn",
                        onClick = {
                            FirebaseFarmerRepo.updateFarmerLocationOnMap(
                                context = context,
                                store = c.store,
                                lat = pinLat,
                                lng = pinLng,
                                address = addressText,
                            )
                            onCloseDialog?.invoke()
                            c.nav.top("map")
                        },
                        modifier = Modifier.weight(1f),
                        primary = false,
                    )
                }
            }
        }

        if (currentFarmer != null) {
            mapColumn(Modifier.fillMaxWidth())
        } else if (c.wide) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top,
            ) {
                authColumn(Modifier.weight(1f))
                mapColumn(Modifier.weight(1.15f))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                authColumn(Modifier.fillMaxWidth())
                mapColumn(Modifier.fillMaxWidth())
            }
        }
    }

    if (showPlaceSearch) {
        PlaceSearchDialog(
            near = GeoPoint(pinLat, pinLng),
            onPick = { pl ->
                val resolvedAddr = pl.admin.ifBlank { pl.label }
                pinLat = pl.lat
                pinLng = pl.lng
                latText = "%.6f".format(pl.lat)
                lngText = "%.6f".format(pl.lng)
                addressText = resolvedAddr
                scope.launch {
                    runCatching {
                        cameraState.animate(
                            CameraUpdateFactory.newLatLngZoom(LatLng(pl.lat, pl.lng), 17f)
                        )
                    }
                }
                c.toast("Đã chọn địa điểm: $resolvedAddr")
            },
            onDismiss = { showPlaceSearch = false },
        )
    }
}

/**
 * Khung nhập 6 ô mã xác nhận.
 */
@Composable
fun OtpSixBoxesPanel(
    phoneDisplay: String,
    otpCode: String,
    otpHint: String,
    busy: Boolean,
    onOtpChange: (String) -> Unit,
    onResendOtp: () -> Unit,
    onVerifyOtp: () -> Unit,
) {
    var countdown by remember(otpHint) { mutableIntStateOf(60) }
    LaunchedEffect(otpHint) {
        countdown = 60
        while (countdown > 0) {
            delay(1000L)
            countdown--
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (VC.isPro) Color(0xFF0A2A22) else Color(0xFFECFDF3))
            .border(1.2.dp, VC.lime.copy(alpha = 0.60f), RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "Nhập mã 6 số xác nhận:",
            color = VC.text,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold,
        )

        // 6 Ô Hiển thị từng chữ số
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (idx in 0 until 6) {
                val charStr = otpCode.getOrNull(idx)?.toString() ?: ""
                val isCurrentSlot = otpCode.length == idx
                val isFilled = charStr.isNotEmpty()
                Box(
                    Modifier
                        .weight(1f)
                        .height(50.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isFilled) VC.pine else if (VC.isPro) Color(0xFF061A14) else Color.White
                        )
                        .border(
                            width = if (isCurrentSlot || isFilled) 2.dp else 1.dp,
                            color = if (isFilled) VC.lime else if (isCurrentSlot) VC.mint else VC.border,
                            shape = RoundedCornerShape(12.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (isFilled) charStr else if (isCurrentSlot) "_" else "•",
                        color = if (isFilled) Color.White else VC.muted,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        // Bàn phím số 2 hàng to rõ cho cả Điện thoại (Medium Phone) và Máy tính bảng
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf(
                listOf("1", "2", "3", "4", "5", "6"),
                listOf("7", "8", "9", "0", "⌫"),
            ).forEach { rowKeys ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    rowKeys.forEach { keyLabel ->
                        Box(
                            Modifier
                                .weight(if (keyLabel == "⌫") 1.35f else 1f)
                                .height(46.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (VC.isPro) Color(0xFF0E3328) else Color.White)
                                .border(1.dp, VC.border, RoundedCornerShape(10.dp))
                                .clickable(enabled = !busy) {
                                    if (keyLabel == "⌫") {
                                        if (otpCode.isNotEmpty()) onOtpChange(otpCode.dropLast(1))
                                    } else if (otpCode.length < 6) {
                                        onOtpChange(otpCode + keyLabel)
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = keyLabel,
                                color = VC.text,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VButton(
                text = if (busy) "Đang mở vườn…" else "✅ Vào vườn ngay",
                onClick = onVerifyOtp,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 50.dp)
                    .testTag("btn_verify_otp")
                    .semantics { contentDescription = "Xác nhận mã 6 số và vào vườn" },
                enabled = !busy && otpCode.length == 6,
            )
            Text(
                if (countdown > 0) "${countdown}s" else "Gửi lại",
                color = if (countdown > 0) VC.muted else VC.mint,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = !busy) { onResendOtp() }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}

/**
 * Form Đăng nhập Tối giản dành cho Nông dân (chỉ cần Số Điện Thoại + Mã 6 số),
 * không chứa dữ liệu mẫu hay nội dung test.
 */
@Composable
fun FarmerPhoneOtpLoginScreen(
    store: com.example.test_tablet.data.vwing.VWingStore,
    onLoginSuccess: (com.example.test_tablet.data.vwing.FarmerProfile) -> Unit,
    onSkipDemo: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    var phoneInput by remember { mutableStateOf("") }
    var otpSent by remember { mutableStateOf(false) }
    var otpHint by remember { mutableStateOf("") }
    var otpCodeInput by remember { mutableStateOf("") }
    var authBusy by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }

    Box(
        Modifier
            .fillMaxSize()
            .semantics { testTagsAsResourceId = true }
            .background(VC.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            VCard(
                Modifier.fillMaxWidth(),
                borderColor = VC.mint.copy(alpha = 0.55f),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    LogoPlate(36.dp)
                    Column {
                        Text(
                            "Vào Sổ Vườn",
                            color = VC.text,
                            fontFamily = VDisplayFont,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Text(
                            "Nhập số điện thoại để mở sổ vườn của bác",
                            color = VC.text2,
                            fontSize = 13.sp,
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                VField(
                    value = phoneInput,
                    onChange = {
                        phoneInput = it.take(14)
                        authError = null
                    },
                    label = "Số điện thoại của bác",
                    placeholder = "Nhập số điện thoại (10 số)",
                    number = true,
                )

                Spacer(Modifier.height(10.dp))

                VButton(
                    text = if (authBusy) "Đang gửi mã…" else if (otpSent) "📲 Gửi lại mã 6 số" else "📲 Lấy mã 6 số",
                    onClick = {
                        focusManager.clearFocus()
                        authBusy = true
                        authError = null
                        FirebaseFarmerRepo.requestPhoneOtp(
                            context = context,
                            activity = activity,
                            rawPhone = phoneInput,
                            onOtpSent = { _, instantHint, _ ->
                                authBusy = false
                                otpSent = true
                                otpHint = instantHint
                                otpCodeInput = ""
                            },
                            onError = { err ->
                                authBusy = false
                                authError = err
                            },
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp)
                        .testTag("btn_request_otp")
                        .semantics { contentDescription = "Lấy mã 6 số" },
                    enabled = !authBusy,
                )

                if (otpSent) {
                    OtpSixBoxesPanel(
                        phoneDisplay = phoneInput,
                        otpCode = otpCodeInput,
                        otpHint = otpHint,
                        busy = authBusy,
                        onOtpChange = {
                            otpCodeInput = it.filter(Char::isDigit).take(6)
                            authError = null
                        },
                        onResendOtp = {
                            authBusy = true
                            authError = null
                            FirebaseFarmerRepo.requestPhoneOtp(
                                context = context,
                                activity = activity,
                                rawPhone = phoneInput,
                                onOtpSent = { _, instantHint, _ ->
                                    authBusy = false
                                    otpSent = true
                                    otpHint = instantHint
                                    otpCodeInput = ""
                                },
                                onError = { err ->
                                    authBusy = false
                                    authError = err
                                },
                            )
                        },
                        onVerifyOtp = {
                            authBusy = true
                            authError = null
                            FirebaseFarmerRepo.verifyOtpAndSignInFarmer(
                                context = context,
                                store = store,
                                rawPhone = phoneInput,
                                otpCode = otpCodeInput,
                                onSuccess = { profile ->
                                    authBusy = false
                                    onLoginSuccess(profile)
                                },
                                onError = { err ->
                                    authBusy = false
                                    authError = err
                                },
                            )
                        },
                    )
                }

                authError?.let { errText ->
                    Text(
                        "✕ $errText",
                        color = VC.red,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

