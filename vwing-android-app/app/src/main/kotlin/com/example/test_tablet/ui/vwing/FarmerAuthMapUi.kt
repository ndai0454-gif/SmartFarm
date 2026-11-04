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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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

private data class QuickAddressPreset(
    val label: String,
    val address: String,
    val lat: Double,
    val lng: Double,
)

private val QUICK_ADDRESS_PRESETS = listOf(
    QuickAddressPreset("📍 Bảo Lộc · Lâm Đồng", "Xã Đamb'ri, TP. Bảo Lộc, Lâm Đồng", 11.547820, 107.793480),
    QuickAddressPreset("📍 Cai Lậy · Tiền Giang", "Xã Ngũ Hiệp, Huyện Cai Lậy, Tiền Giang", 10.336500, 106.108200),
    QuickAddressPreset("📍 Cư M'gar · Đắk Lắk", "Thị trấn Quảng Phú, Huyện Cư M'gar, Đắk Lắk", 12.825400, 108.078900),
    QuickAddressPreset("📍 Chợ Lách · Bến Tre", "Xã Sơn Định, Huyện Chợ Lách, Bến Tre", 10.258900, 106.142300),
)

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
                                "Tài khoản Nông dân · Firebase & Định vị Map",
                                color = VC.text,
                                fontFamily = VDisplayFont,
                                fontSize = if (c.wide) 21.sp else 17.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                            )
                            Text(
                                "Đăng nhập bằng SĐT + mã OTP · Lưu dữ liệu riêng từng nhà vườn · Ghim địa chỉ trên bản đồ vệ tinh",
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
        mutableStateOf(currentFarmer?.displayPhone ?: "0912 345 678")
    }
    var farmerNameInput by remember(currentFarmer?.phoneKey) {
        mutableStateOf(currentFarmer?.farmerName ?: c.d.garden.operatorName.ifBlank { "Bác Năm Bảo Lộc" })
    }
    var gardenNameInput by remember(currentFarmer?.phoneKey) {
        mutableStateOf(currentFarmer?.gardenName ?: c.d.garden.name.ifBlank { "Vườn Sầu Riêng Bác Năm" })
    }
    var otpSent by remember { mutableStateOf(false) }
    var otpHint by remember { mutableStateOf("123456") }
    var otpCodeInput by remember { mutableStateOf("") }
    var authBusy by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }
    var authInfo by remember { mutableStateOf<String?>(null) }

    var showFirebaseConfig by remember { mutableStateOf(false) }
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
                ?: c.d.garden.location.ifBlank { "Xã Đamb'ri, TP. Bảo Lộc, Lâm Đồng" }
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
        scope.launch {
            val resolved = withContext(Dispatchers.IO) {
                FirebaseFarmerRepo.resolveAddressFromCoordinates(context, newLat, newLng)
            }
            geocodingBusy = false
            addressText = resolved
        }
    }

    val ensureLoc = rememberEnsureLocationPermission(c)
    val gpsFetcher = rememberLocationFetcher(c) { la, lo, _ ->
        movePinAndResolveAddress(la, lo, animateMap = true)
        c.toast("📍 Đã lấy tọa độ GPS thực tế và tra địa chỉ tự động.")
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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f),
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
                                "Chưa đăng nhập SĐT Nông dân (Đang dùng vườn mặc định)"
                            },
                            color = VC.text,
                            fontSize = 16.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Text(
                            if (currentFarmer != null) {
                                "🏡 ${currentFarmer.gardenName} · 📍 ${currentFarmer.address.ifBlank { c.d.garden.location.ifBlank { "Chưa ghim địa chỉ" } }}"
                            } else {
                                "Đăng nhập bằng Số điện thoại + mã OTP bên dưới để tách riêng dữ liệu vườn của từng người nông dân."
                            },
                            color = VC.text2,
                            fontSize = 13.sp,
                        )
                    }
                }
                if (currentFarmer != null) {
                    VButton(
                        "Đăng xuất",
                        {
                            FirebaseFarmerRepo.signOutFarmer(context, c.store)
                            c.toast("Đã đăng xuất về vườn mặc định.")
                        },
                        primary = false,
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
                            "🔄 Đồng bộ ngay",
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
                                    c.toast("Đã đồng bộ dữ liệu vườn của ${currentFarmer.displayPhone} lên Firebase.")
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            if (savedFarmers.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Eyebrow("CÁC TÀI KHOẢN NÔNG DÂN ĐÃ LƯU TRÊN MÁY (${savedFarmers.size})")
                Text(
                    "Chạm vào một số điện thoại để chuyển ngay sang dữ liệu vườn, cây trồng và địa chỉ riêng của người nông dân đó:",
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
                                    c.toast("Đã chuyển sang tài khoản ${prof.farmerName} (${prof.displayPhone})")
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
                                Text(
                                    "${prof.farmerName} · ${prof.displayPhone}",
                                    color = if (isCurrent) Color.White else VC.text,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    "${prof.gardenName} · ${prof.address.take(28)}",
                                    color = if (isCurrent) Color.White.copy(alpha = 0.85f) else VC.text2,
                                    fontSize = 11.5.sp,
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Khung chia 2 cột trên Tablet (Trái: Đăng nhập SĐT + OTP Firebase | Phải: Bản đồ Google Map định vị địa chỉ)
        val authColumn: @Composable (Modifier) -> Unit = { mod ->
            VCard(mod) {
                Eyebrow("1. ĐĂNG NHẬP SỐ ĐIỆN THOẠI & XÁC NHẬN MÃ OTP")
                Text(
                    "Mỗi số điện thoại nông dân có một kho dữ liệu vườn riêng biệt (Cây trồng, Ranh vườn, Nhật ký chăm sóc, Địa chỉ) được lưu trên máy và đồng bộ lên Firebase Firestore.",
                    color = VC.text2,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    VField(
                        value = phoneInput,
                        onChange = { phoneInput = it; authError = null },
                        label = "Số điện thoại nông dân *",
                        placeholder = "Ví dụ: 0912 345 678 hoặc +84912345678",
                    )
                    VField(
                        value = farmerNameInput,
                        onChange = { farmerNameInput = it },
                        label = "Họ tên chủ vườn",
                        placeholder = "Ví dụ: Bác Năm Bảo Lộc",
                    )
                    VField(
                        value = gardenNameInput,
                        onChange = { gardenNameInput = it },
                        label = "Tên khu vườn của bác",
                        placeholder = "Ví dụ: Vườn Sầu Riêng Bác Năm",
                    )

                    Row(
                        Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        VButton(
                            text = if (authBusy) "Đang gửi OTP…" else "📲 Nhận mã OTP (6 số)",
                            onClick = {
                                authBusy = true
                                authError = null
                                FirebaseFarmerRepo.requestPhoneOtp(
                                    context = context,
                                    activity = activity,
                                    rawPhone = phoneInput,
                                    onOtpSent = { e164, instantHint, viaSms ->
                                        authBusy = false
                                        otpSent = true
                                        otpHint = instantHint
                                        otpCodeInput = instantHint
                                        authInfo = if (viaSms) {
                                            "Đã gửi SMS OTP từ Firebase tới $e164."
                                        } else {
                                            "Đã tạo mã OTP 6 số cho $e164: $instantHint"
                                        }
                                        c.toast("📲 Đã gửi mã OTP ($instantHint) cho $phoneInput")
                                    },
                                    onError = { err ->
                                        authBusy = false
                                        authError = err
                                    },
                                )
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !authBusy,
                        )
                        VButton(
                            text = "Điền mẫu Bác Tư",
                            onClick = {
                                phoneInput = "0988 777 666"
                                farmerNameInput = "Bác Tư Cai Lậy"
                                gardenNameInput = "Vườn Sầu Riêng Ri6 Bác Tư"
                                movePinAndResolveAddress(10.336500, 106.108200, animateMap = true)
                                c.toast("Đã điền mẫu Bác Tư (0988 777 666) · Cai Lậy, Tiền Giang. Bấm Nhận mã OTP để đăng nhập.")
                            },
                            primary = false,
                        )
                    }

                    AnimatedVisibility(visible = otpSent || otpCodeInput.isNotBlank()) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (VC.isPro) Color(0xFF0A2A22) else Color(0xFFECFDF3))
                                .border(1.dp, VC.lime.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "🔐 Nhập mã xác nhận OTP (6 chữ số)",
                                    color = VC.lime,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    "Mã OTP: $otpHint",
                                    color = if (VC.isPro) VC.mint else Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(VC.pine)
                                        .clickable { otpCodeInput = otpHint }
                                        .padding(horizontal = 8.dp, vertical = 3.dp),
                                )
                            }

                            VField(
                                value = otpCodeInput,
                                onChange = { otpCodeInput = it.filter(Char::isDigit).take(6); authError = null },
                                label = "Mã OTP 6 số gửi về SĐT $phoneInput",
                                placeholder = "Nhập 6 chữ số (VD: $otpHint)",
                                number = true,
                            )

                            VButton(
                                text = if (authBusy) "Đang xác thực OTP…" else "✅ Xác nhận OTP & Đăng nhập Vườn",
                                onClick = {
                                    authBusy = true
                                    authError = null
                                    FirebaseFarmerRepo.verifyOtpAndSignInFarmer(
                                        context = context,
                                        store = c.store,
                                        rawPhone = phoneInput,
                                        otpCode = otpCodeInput,
                                        farmerName = farmerNameInput,
                                        gardenName = gardenNameInput,
                                        lat = pinLat,
                                        lng = pinLng,
                                        address = addressText,
                                        onSuccess = { profile ->
                                            authBusy = false
                                            authInfo = "Đã đăng nhập tài khoản ${profile.farmerName} (${profile.displayPhone}) và đồng bộ dữ liệu riêng!"
                                            c.toast("🎉 Đã đăng nhập SĐT ${profile.displayPhone} (${profile.farmerName}) & đồng bộ Firebase!")
                                        },
                                        onError = { err ->
                                            authBusy = false
                                            authError = err
                                        },
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                icon = Icons.Filled.Check,
                                enabled = !authBusy,
                            )
                        }
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

                    Spacer(Modifier.height(6.dp))
                    Divider2()

                    // Cấu hình khóa dự án Firebase (tùy chọn cho kỹ sư / chủ dự án)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { showFirebaseConfig = !showFirebaseConfig }
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "⚙ Cấu hình kết nối Firebase Project (FirebaseOptions)",
                            color = VC.muted,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            if (showFirebaseConfig) "Thu gọn" else "Mở",
                            color = VC.mint,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    AnimatedVisibility(visible = showFirebaseConfig) {
                        val savedCfg = remember { FirebaseFarmerRepo.loadProjectConfig(context) }
                        var apiKey by remember { mutableStateOf(savedCfg.apiKey) }
                        var appId by remember { mutableStateOf(savedCfg.appId) }
                        var projectId by remember { mutableStateOf(savedCfg.projectId) }
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(VC.elevated)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                "Mặc định ứng dụng đọc khóa từ tệp google-services.json hoặc .env (FIREBASE_API_KEY, FIREBASE_APP_ID, FIREBASE_PROJECT_ID). Bạn cũng có thể dán trực tiếp khóa dự án Firebase tại đây:",
                                color = VC.text2,
                                fontSize = 12.sp,
                            )
                            VField(projectId, { projectId = it }, "Firebase Project ID (VD: smartfarm-vwing)")
                            VField(apiKey, { apiKey = it }, "Firebase Web/Android API Key (AIza...)")
                            VField(appId, { appId = it }, "Firebase App ID (1:...:android:...)")
                            VButton(
                                "Lưu cấu hình Firebase & Kết nối",
                                {
                                    FirebaseFarmerRepo.saveProjectConfig(
                                        context,
                                        FirebaseProjectConfig(
                                            apiKey = apiKey.trim(),
                                            appId = appId.trim(),
                                            projectId = projectId.trim(),
                                        )
                                    )
                                    val ok = FirebaseFarmerRepo.ensureFirebaseInitialized(context)
                                    c.toast(
                                        if (ok) "Đã kết nối Firebase Project ($projectId) thành công!"
                                        else "Đã lưu cấu hình. Kiểm tra lại API Key / App ID nếu muốn đồng bộ Cloud."
                                    )
                                },
                                primary = false,
                            )
                        }
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
                    Eyebrow("2. ĐỊNH VỊ ĐỊA CHỈ VƯỜN TRÊN BẢN ĐỒ GOOGLE MAP")
                    if (geocodingBusy) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = VC.lime)
                            Text("Đang tra địa chỉ…", color = VC.lime, fontSize = 12.sp)
                        }
                    }
                }
                Text(
                    "Chạm trực tiếp lên bản đồ vệ tinh bên dưới hoặc bấm \"Định vị GPS của tôi\" để ghim vị trí vườn và tự động điền địa chỉ Xã/Huyện/Tỉnh.",
                    color = VC.text2,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )

                // Hàng nút công cụ định vị nhanh
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    VButton(
                        text = "🎯 Định vị GPS của tôi",
                        onClick = { ensureLoc { gpsFetcher() } },
                        primary = true,
                        icon = Icons.Filled.MyLocation,
                    )
                    VButton(
                        text = "🔍 Tìm tên Xã / Huyện…",
                        onClick = { showPlaceSearch = true },
                        primary = false,
                    )
                    QUICK_ADDRESS_PRESETS.forEach { preset ->
                        Row(
                            Modifier
                                .heightIn(min = 38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(VC.elevated)
                                .border(1.dp, VC.border, RoundedCornerShape(12.dp))
                                .bounceClick {
                                    pinLat = preset.lat
                                    pinLng = preset.lng
                                    latText = "%.6f".format(preset.lat)
                                    lngText = "%.6f".format(preset.lng)
                                    addressText = preset.address
                                    scope.launch {
                                        runCatching {
                                            cameraState.animate(
                                                CameraUpdateFactory.newLatLngZoom(LatLng(preset.lat, preset.lng), 17f)
                                            )
                                        }
                                    }
                                    c.toast("Đã ghim bản đồ tới: ${preset.address}")
                                }
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(preset.label, color = VC.text, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Khung Bản đồ Google Map Vệ tinh tương tác
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
                                "Bản đồ vệ tinh sẵn sàng khi cấu hình MAPS_API_KEY. Bạn vẫn có thể dùng Định vị GPS hoặc nhập tọa độ bên dưới.",
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
                    label = "Địa chỉ vườn đã định vị trên Map (Xã/Phường, Huyện/Thị xã, Tỉnh)",
                    placeholder = "Ví dụ: Xã Đamb'ri, TP. Bảo Lộc, Lâm Đồng",
                )

                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    VField(
                        value = latText,
                        onChange = {
                            latText = it
                            it.toDoubleOrNull()?.let { v -> if (v in -90.0..90.0) pinLat = v }
                        },
                        label = "Vĩ độ (Lat)",
                        modifier = Modifier.weight(1f),
                        number = true,
                    )
                    VField(
                        value = lngText,
                        onChange = {
                            lngText = it
                            it.toDoubleOrNull()?.let { v -> if (v in -180.0..180.0) pinLng = v }
                        },
                        label = "Kinh độ (Lng)",
                        modifier = Modifier.weight(1f),
                        number = true,
                    )
                }

                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    VButton(
                        text = "💾 Lưu Địa chỉ & Đồng bộ Firebase",
                        onClick = {
                            FirebaseFarmerRepo.updateFarmerLocationOnMap(
                                context = context,
                                store = c.store,
                                lat = pinLat,
                                lng = pinLng,
                                address = addressText,
                            )
                            c.toast("📍 Đã lưu địa chỉ \"$addressText\" & đồng bộ Firebase!")
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

        if (c.wide) {
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
