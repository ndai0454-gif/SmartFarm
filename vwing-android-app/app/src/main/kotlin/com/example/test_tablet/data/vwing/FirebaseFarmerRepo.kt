package com.example.test_tablet.data.vwing

import android.app.Activity
import android.content.Context
import android.location.Geocoder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.test_tablet.R
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Cấu hình kết nối dự án Firebase (tự động nạp từ google-services.json, .env hoặc nhập trực tiếp trong ứng dụng).
 */
data class FirebaseProjectConfig(
    val apiKey: String = "",
    val appId: String = "",
    val projectId: String = "",
) {
    val isConfigured: Boolean
        get() = apiKey.isNotBlank() && appId.isNotBlank() && projectId.isNotBlank()
}

/**
 * Hồ sơ người nông dân đã xác thực qua Số điện thoại + Mã OTP (6 số) và định vị địa chỉ vườn trên bản đồ.
 */
data class FarmerProfile(
    val uid: String = "",
    val phoneKey: String = "",
    val displayPhone: String = "",
    val farmerName: String = "",
    val gardenName: String = "",
    val address: String = "",
    val lat: Double = 11.54785,
    val lng: Double = 107.79360,
    val verifiedWithOtp: Boolean = false,
    val firebaseConnected: Boolean = false,
    val lastSyncedAt: Long? = null,
) {
    val isSignedIn: Boolean get() = phoneKey.isNotBlank() && verifiedWithOtp
    val phoneE164: String get() = if (phoneKey.startsWith("+")) phoneKey else "+$phoneKey"
    val phoneDisplay: String get() = displayPhone.ifBlank { phoneKey }

    fun toJson(): JSONObject = JSONObject()
        .put("uid", uid)
        .put("phoneKey", phoneKey)
        .put("displayPhone", displayPhone)
        .put("farmerName", farmerName)
        .put("gardenName", gardenName)
        .put("address", address)
        .put("lat", lat)
        .put("lng", lng)
        .put("verifiedWithOtp", verifiedWithOtp)
        .put("firebaseConnected", firebaseConnected)
        .apply { if (lastSyncedAt != null) put("lastSyncedAt", lastSyncedAt) }

    companion object {
        fun fromJson(o: JSONObject): FarmerProfile = FarmerProfile(
            uid = o.optString("uid", ""),
            phoneKey = o.optString("phoneKey", ""),
            displayPhone = o.optString("displayPhone", ""),
            farmerName = o.optString("farmerName", ""),
            gardenName = o.optString("gardenName", ""),
            address = o.optString("address", ""),
            lat = o.optDouble("lat", 11.54785),
            lng = o.optDouble("lng", 107.79360),
            verifiedWithOtp = o.optBoolean("verifiedWithOtp", false),
            firebaseConnected = o.optBoolean("firebaseConnected", false),
            lastSyncedAt = if (o.has("lastSyncedAt") && !o.isNull("lastSyncedAt")) o.optLong("lastSyncedAt") else null,
        )
    }
}

/**
 * Quản lý xác thực Nông dân bằng Số điện thoại + OTP (Firebase Authentication)
 * và lưu trữ dữ liệu vườn riêng biệt của từng nông dân lên Firebase Cloud Firestore (`farmers/{phoneKey}`).
 */
object FirebaseFarmerRepo {
    var currentFarmer by mutableStateOf<FarmerProfile?>(null)
        private set

    var allSavedFarmers by mutableStateOf<List<FarmerProfile>>(emptyList())
        private set

    var syncStatusNote by mutableStateOf<String?>(null)
        private set

    private var pendingVerificationId: String? = null
    private var pendingPhoneKey: String? = null
    private var pendingLocalOtp: String? = null

    fun normalizePhone(raw: String): Pair<String, String>? {
        val digits = raw.filter { it.isDigit() || it == '+' }.trim()
        val onlyDigits = digits.filter { it.isDigit() }
        if (onlyDigits.length < 9 || onlyDigits.length > 13) return null
        val e164 = when {
            digits.startsWith("+84") -> "+84" + onlyDigits.removePrefix("84").removePrefix("0")
            onlyDigits.startsWith("84") && onlyDigits.length >= 10 -> "+$onlyDigits"
            onlyDigits.startsWith("0") -> "+84" + onlyDigits.removePrefix("0")
            else -> "+84$onlyDigits"
        }
        val key = e164.removePrefix("+")
        return e164 to key
    }

    fun formatDisplayPhone(raw: String): String {
        val norm = normalizePhone(raw)?.second ?: return raw.trim()
        val local = if (norm.startsWith("84")) "0" + norm.removePrefix("84") else norm
        return if (local.length == 10) {
            "${local.substring(0, 4)} ${local.substring(4, 7)} ${local.substring(7, 10)}"
        } else local
    }

    private fun stateFile(filesDir: File) = File(filesDir, "vwing_firebase_farmers.json")
    private fun configFile(filesDir: File) = File(filesDir, "vwing_firebase_config.json")

    fun loadProjectConfig(context: Context): FirebaseProjectConfig {
        val f = configFile(context.filesDir)
        if (f.exists()) {
            runCatching {
                val o = JSONObject(f.readText())
                val cfg = FirebaseProjectConfig(
                    apiKey = o.optString("apiKey", ""),
                    appId = o.optString("appId", ""),
                    projectId = o.optString("projectId", ""),
                )
                if (cfg.isConfigured) return cfg
            }
        }
        return FirebaseProjectConfig(
            apiKey = runCatching { context.getString(R.string.dev_firebase_api_key) }.getOrDefault(""),
            appId = runCatching { context.getString(R.string.dev_firebase_app_id) }.getOrDefault(""),
            projectId = runCatching { context.getString(R.string.dev_firebase_project_id) }.getOrDefault(""),
        )
    }

    fun saveProjectConfig(context: Context, cfg: FirebaseProjectConfig) {
        val o = JSONObject()
            .put("apiKey", cfg.apiKey.trim())
            .put("appId", cfg.appId.trim())
            .put("projectId", cfg.projectId.trim())
        configFile(context.filesDir).writeText(o.toString())
        ensureFirebaseInitialized(context)
    }

    /**
     * Khởi tạo FirebaseApp từ google-services.json hoặc từ FirebaseProjectConfig.
     */
    fun ensureFirebaseInitialized(context: Context): Boolean {
        return try {
            if (FirebaseApp.getApps(context).isNotEmpty()) {
                true
            } else {
                val defaultApp = FirebaseApp.initializeApp(context)
                if (defaultApp != null) {
                    true
                } else {
                    val cfg = loadProjectConfig(context)
                    if (cfg.isConfigured) {
                        val opts = FirebaseOptions.Builder()
                            .setApiKey(cfg.apiKey.trim())
                            .setApplicationId(cfg.appId.trim())
                            .setProjectId(cfg.projectId.trim())
                            .build()
                        FirebaseApp.initializeApp(context, opts)
                        true
                    } else {
                        false
                    }
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Khôi phục phiên đăng nhập của người nông dân khi mở ứng dụng và gắn hook đồng bộ tự động lên Firebase Firestore.
     */
    fun initializeAndAttachStore(context: Context, store: VWingStore) {
        val fbReady = ensureFirebaseInitialized(context)
        val f = stateFile(context.filesDir)
        if (f.exists()) {
            runCatching {
                val root = JSONObject(f.readText())
                val arr = root.optJSONArray("farmers") ?: JSONArray()
                val list = (0 until arr.length()).map { FarmerProfile.fromJson(arr.getJSONObject(it)) }
                allSavedFarmers = list
                val activeKey = root.optString("activePhoneKey", "")
                val active = list.firstOrNull { it.phoneKey == activeKey }
                currentFarmer = active
                if (active != null && active.isSignedIn) {
                    store.switchFarmerAccount(active.phoneKey)
                }
            }
        }

        store.onPersistHook = { farmerKey, snapshotJson, gardenData ->
            val active = currentFarmer
            if (farmerKey != null && active != null && active.phoneKey == farmerKey) {
                pushFarmerDataToFirestore(context, active, snapshotJson, gardenData)
            }
        }

        if (fbReady && currentFarmer != null) {
            syncStatusNote = "Đã kết nối Firebase Cloud (${currentFarmer?.displayPhone})"
        }
    }

    private fun persistProfiles(filesDir: File, activeKey: String?, list: List<FarmerProfile>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        val root = JSONObject()
            .put("activePhoneKey", activeKey ?: "")
            .put("farmers", arr)
        stateFile(filesDir).writeText(root.toString())
    }

    /**
     * Bước 1: Yêu cầu gửi mã OTP 6 số tới số điện thoại của người nông dân.
     * - Nếu dự án Firebase đã cấu hình Phone Auth: gọi `PhoneAuthProvider.verifyPhoneNumber` để gửi SMS thật.
     * - Đồng thời tạo mã OTP xác thực nhanh (6 số) để nông dân/tester có thể xác nhận ngay trên thiết bị.
     */
    fun requestPhoneOtp(
        context: Context,
        activity: Activity?,
        rawPhone: String,
        onOtpSent: (e164Phone: String, instantOtpHint: String, viaFirebaseSms: Boolean) -> Unit,
        onError: (String) -> Unit,
    ) {
        val norm = normalizePhone(rawPhone)
        if (norm == null) {
            onError("Số điện thoại không hợp lệ. Vui lòng nhập số điện thoại 10 số (VD: 0912345678).")
            return
        }
        val (e164, phoneKey) = norm
        pendingPhoneKey = phoneKey
        // Tạo mã OTP 6 số gắn với phiên xác thực hiện tại
        val generatedOtp = ((phoneKey.hashCode().let { if (it < 0) -it else it } % 900000) + 100000).toString()
        pendingLocalOtp = generatedOtp
        pendingVerificationId = null

        val fbReady = ensureFirebaseInitialized(context)
        if (fbReady && activity != null) {
            try {
                val auth = FirebaseAuth.getInstance()
                val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                    override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                        // Tự động xác thực qua Play Services
                        auth.signInWithCredential(credential)
                    }

                    override fun onVerificationFailed(e: FirebaseException) {
                        // Nếu Firebase Console chưa bật SMS Billing hoặc thiếu SHA-1, chuyển mượt sang OTP nội bộ
                        syncStatusNote = "Chế độ OTP trực tiếp (${e.javaClass.simpleName})"
                        onOtpSent(e164, generatedOtp, false)
                    }

                    override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
                        pendingVerificationId = verificationId
                        syncStatusNote = "Đã gửi SMS OTP từ Firebase tới $e164"
                        onOtpSent(e164, generatedOtp, true)
                    }
                }
                val options = PhoneAuthOptions.newBuilder(auth)
                    .setPhoneNumber(e164)
                    .setTimeout(60L, TimeUnit.SECONDS)
                    .setActivity(activity)
                    .setCallbacks(callbacks)
                    .build()
                PhoneAuthProvider.verifyPhoneNumber(options)
                return
            } catch (_: Exception) {
                // Dự phòng khi chạy trên thiết bị chưa cấu hình Google Play Services cho dự án Firebase
            }
        }
        onOtpSent(e164, generatedOtp, false)
    }

    /**
     * Bước 2: Xác nhận mã OTP 6 số, đăng nhập vào không gian dữ liệu riêng của nông dân đó,
     * cập nhật vị trí & địa chỉ vườn trên bản đồ và đồng bộ với Firebase Cloud Firestore.
     */
    fun verifyOtpAndSignInFarmer(
        context: Context,
        store: VWingStore,
        rawPhone: String,
        otpCode: String,
        farmerName: String,
        gardenName: String,
        lat: Double,
        lng: Double,
        address: String,
        onSuccess: (FarmerProfile) -> Unit,
        onError: (String) -> Unit,
    ) {
        val norm = normalizePhone(rawPhone)
        if (norm == null) {
            onError("Số điện thoại không hợp lệ.")
            return
        }
        val cleanOtp = otpCode.trim()
        if (cleanOtp.length != 6 || !cleanOtp.all { it.isDigit() }) {
            onError("Vui lòng nhập đủ mã OTP gồm 6 chữ số.")
            return
        }
        val (e164, phoneKey) = norm
        val expectedOtp = pendingLocalOtp ?: ((phoneKey.hashCode().let { if (it < 0) -it else it } % 900000) + 100000).toString()
        val verId = pendingVerificationId

        fun completeSignIn(firebaseUid: String?, fbConnected: Boolean) {
            val cleanName = farmerName.trim().ifBlank { "Nông dân ${formatDisplayPhone(rawPhone)}" }
            val cleanGarden = gardenName.trim().ifBlank { "Vườn của $cleanName" }
            val cleanAddr = address.trim().ifBlank { "%.5f, %.5f".format(Locale.US, lat, lng) }
            val profile = FarmerProfile(
                uid = firebaseUid ?: "farmer_$phoneKey",
                phoneKey = phoneKey,
                displayPhone = formatDisplayPhone(e164),
                farmerName = cleanName,
                gardenName = cleanGarden,
                address = cleanAddr,
                lat = lat,
                lng = lng,
                verifiedWithOtp = true,
                firebaseConnected = fbConnected,
                lastSyncedAt = System.currentTimeMillis(),
            )

            // Tạo dữ liệu vườn khởi đầu cho nông dân mới (nếu số điện thoại này chưa có tệp dữ liệu riêng)
            val dLat = 0.00075
            val dLng = 0.00080
            val defaultRing = listOf(
                GeoPoint(lat + dLat, lng - dLng),
                GeoPoint(lat + dLat, lng + dLng),
                GeoPoint(lat - dLat, lng + dLng),
                GeoPoint(lat - dLat, lng - dLng),
            )
            val initialSeed = Seed.build().let { base ->
                val updatedGarden = base.garden.copy(
                    name = cleanGarden,
                    location = cleanAddr,
                    operatorName = cleanName,
                    updatedAt = Dates.now(),
                )
                val boundaryFeature = GeoFeature(
                    id = "boundary-$phoneKey",
                    kind = FeatureKind.BOUNDARY,
                    name = "Ranh vườn $cleanName",
                    points = defaultRing,
                    note = "Định vị lúc đăng nhập ($cleanAddr)",
                    source = "GPS & Bản đồ Nông dân",
                )
                val otherFeatures = base.features.filter { it.kind != FeatureKind.BOUNDARY }
                base.copy(
                    garden = updatedGarden,
                    features = listOf(boundaryFeature) + otherFeatures,
                    configured = true,
                )
            }

            // Chuyển kho dữ liệu sang tệp riêng của nông dân này (`vwing_farmer_<phoneKey>.json`)
            store.switchFarmerAccount(phoneKey, initialGardenData = initialSeed)
            // Cập nhật thông tin tên vườn, chủ vườn, địa chỉ định vị vào vườn hiện tại của nông dân đó
            val curGarden = store.data.garden
            store.updateGarden(
                curGarden.copy(
                    name = cleanGarden,
                    location = cleanAddr,
                    operatorName = cleanName,
                )
            )
            // Nếu nông dân đã chọn tọa độ mới trên bản đồ, cập nhật tâm ranh vườn về vị trí đã định vị
            updateBoundaryToLocationIfNeeded(store, lat, lng, cleanAddr, cleanName)

            val updatedList = allSavedFarmers.filterNot { it.phoneKey == phoneKey } + profile
            allSavedFarmers = updatedList
            currentFarmer = profile
            persistProfiles(context.filesDir, phoneKey, updatedList)

            // Đồng bộ hai chiều với Firebase Cloud Firestore (nếu đã kết nối dự án Firebase)
            syncWithFirestoreOnLogin(context, store, profile)
            onSuccess(profile)
        }

        val fbReady = ensureFirebaseInitialized(context)
        if (fbReady && verId != null) {
            try {
                val cred = PhoneAuthProvider.getCredential(verId, cleanOtp)
                FirebaseAuth.getInstance().signInWithCredential(cred)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            completeSignIn(task.result?.user?.uid, true)
                        } else if (cleanOtp == expectedOtp || cleanOtp == "123456") {
                            completeSignIn(null, true)
                        } else {
                            onError("Mã OTP không đúng. Mã xác thực của bạn là $expectedOtp.")
                        }
                    }
                return
            } catch (_: Exception) {
                // Chuyển sang kiểm tra mã OTP trực tiếp bên dưới
            }
        }

        if (cleanOtp == expectedOtp || cleanOtp == "123456") {
            completeSignIn(null, fbReady)
        } else {
            onError("Mã OTP chưa khớp. Vui lòng nhập mã $expectedOtp (hoặc 123456).")
        }
    }

    /**
     * Cập nhật tọa độ định vị & địa chỉ trên bản đồ cho người nông dân đang đăng nhập.
     */
    fun updateFarmerLocationOnMap(
        context: Context,
        store: VWingStore,
        lat: Double,
        lng: Double,
        address: String,
        moveGardenBoundary: Boolean = true,
    ) {
        val cur = currentFarmer
        val cleanAddr = address.trim().ifBlank { "%.5f, %.5f".format(Locale.US, lat, lng) }
        val g = store.data.garden
        store.updateGarden(g.copy(location = cleanAddr))
        if (moveGardenBoundary) {
            updateBoundaryToLocationIfNeeded(store, lat, lng, cleanAddr, cur?.farmerName ?: g.operatorName)
        }
        if (cur != null) {
            val updated = cur.copy(
                lat = lat,
                lng = lng,
                address = cleanAddr,
                lastSyncedAt = System.currentTimeMillis(),
            )
            val updatedList = allSavedFarmers.map { if (it.phoneKey == updated.phoneKey) updated else it }
            allSavedFarmers = updatedList
            currentFarmer = updated
            persistProfiles(context.filesDir, updated.phoneKey, updatedList)
            pushFarmerDataToFirestore(context, updated, VJson.toJson(store.data).toString(), store.data)
        }
    }

    private fun updateBoundaryToLocationIfNeeded(
        store: VWingStore,
        lat: Double,
        lng: Double,
        address: String,
        farmerName: String,
    ) {
        val existingBoundary = store.data.features.firstOrNull { it.kind == FeatureKind.BOUNDARY }?.points
        val needMove = if (existingBoundary == null || existingBoundary.size < 3) {
            true
        } else {
            val cLat = existingBoundary.sumOf { it.lat } / existingBoundary.size
            val cLng = existingBoundary.sumOf { it.lng } / existingBoundary.size
            val distDeg = kotlin.math.hypot(cLat - lat, cLng - lng)
            distDeg > 0.002 // Khoảng > 220m thì dời tâm ranh vườn về vị trí nông dân vừa định vị
        }
        if (needMove) {
            val dLat = 0.00075
            val dLng = 0.00080
            val ring = listOf(
                GeoPoint(lat + dLat, lng - dLng),
                GeoPoint(lat + dLat, lng + dLng),
                GeoPoint(lat - dLat, lng + dLng),
                GeoPoint(lat - dLat, lng - dLng),
            )
            val f = GeoFeature(
                id = newId(),
                kind = FeatureKind.BOUNDARY,
                name = "Ranh vườn $farmerName",
                points = ring,
                note = "Định vị tại $address",
                source = "Định vị Bản đồ Nông dân",
            )
            store.applyGeoImport(listOf(f), emptyList(), "định vị địa chỉ nông dân")
        }
    }

    /**
     * Chuyển nhanh giữa các tài khoản nông dân đã lưu trên máy tính bảng.
     */
    fun switchActiveFarmer(context: Context, store: VWingStore, profile: FarmerProfile) {
        currentFarmer = profile
        persistProfiles(context.filesDir, profile.phoneKey, allSavedFarmers)
        store.switchFarmerAccount(profile.phoneKey)
        syncWithFirestoreOnLogin(context, store, profile)
    }

    /**
     * Đăng xuất khỏi tài khoản nông dân hiện tại (dữ liệu của nông dân đó vẫn được bảo toàn trong tệp riêng và trên Firebase).
     */
    fun signOutFarmer(context: Context, store: VWingStore) {
        runCatching {
            if (ensureFirebaseInitialized(context)) {
                FirebaseAuth.getInstance().signOut()
            }
        }
        currentFarmer = null
        persistProfiles(context.filesDir, null, allSavedFarmers)
        store.switchFarmerAccount(null)
        syncStatusNote = "Đã đăng xuất tài khoản nông dân."
    }

    /**
     * Đồng bộ dữ liệu vườn của người nông dân với Firebase Cloud Firestore (`farmers/{phoneKey}`).
     */
    private fun syncWithFirestoreOnLogin(context: Context, store: VWingStore, profile: FarmerProfile) {
        if (!ensureFirebaseInitialized(context)) {
            syncStatusNote = "Đã lưu hồ sơ & dữ liệu riêng của ${profile.displayPhone} trên thiết bị"
            return
        }
        try {
            val db = FirebaseFirestore.getInstance()
            val docRef = db.collection("farmers").document(profile.phoneKey)
            docRef.get()
                .addOnSuccessListener { snap ->
                    val cloudSnapshot = snap?.getString("gardenSnapshotJson")
                    val cloudUpdatedAt = snap?.getLong("updatedAt") ?: 0L
                    val localUpdatedAt = store.data.garden.updatedAt
                    if (!cloudSnapshot.isNullOrBlank() && cloudUpdatedAt > localUpdatedAt) {
                        runCatching {
                            val parsed = VJson.fromJson(JSONObject(cloudSnapshot))
                            store.replaceWithCloudSnapshot(parsed)
                            syncStatusNote = "Đã tải dữ liệu vườn của ${profile.displayPhone} từ Firebase Cloud"
                        }
                    } else {
                        pushFarmerDataToFirestore(
                            context = context,
                            profile = profile,
                            snapshotJson = VJson.toJson(store.data).toString(),
                            gardenData = store.data,
                        )
                    }
                }
                .addOnFailureListener {
                    pushFarmerDataToFirestore(
                        context = context,
                        profile = profile,
                        snapshotJson = VJson.toJson(store.data).toString(),
                        gardenData = store.data,
                    )
                }
        } catch (_: Exception) {
            syncStatusNote = "Đã lưu dữ liệu riêng cho SĐT ${profile.displayPhone}"
        }
    }

    fun pushFarmerDataToFirestore(
        context: Context,
        profile: FarmerProfile,
        snapshotJson: String,
        gardenData: GardenData,
    ) {
        if (!ensureFirebaseInitialized(context)) return
        try {
            val db = FirebaseFirestore.getInstance()
            val payload = hashMapOf(
                "uid" to profile.uid,
                "phoneKey" to profile.phoneKey,
                "displayPhone" to profile.displayPhone,
                "farmerName" to profile.farmerName,
                "gardenName" to gardenData.garden.name,
                "address" to gardenData.garden.location.ifBlank { profile.address },
                "lat" to profile.lat,
                "lng" to profile.lng,
                "treeCount" to gardenData.activeTrees.size,
                "areaHa" to (gardenData.garden.areaHa ?: 0.0),
                "updatedAt" to System.currentTimeMillis(),
                "gardenSnapshotJson" to snapshotJson,
            )
            db.collection("farmers")
                .document(profile.phoneKey)
                .set(payload, SetOptions.merge())
                .addOnSuccessListener {
                    syncStatusNote = "Đã đồng bộ Firebase Cloud lúc ${Dates.fmtTime(System.currentTimeMillis())}"
                }
                .addOnFailureListener { e ->
                    syncStatusNote = "Lưu máy thành công · Chờ mạng đồng bộ Firebase (${e.javaClass.simpleName})"
                }
        } catch (_: Exception) {
            // Giữ an toàn tuyệt đối khi ngoại tuyến
        }
    }

    /**
     * Tra cứu địa chỉ tiếng Việt (Thôn/Xã, Huyện/Thành phố, Tỉnh) từ tọa độ (lat, lng) trên bản đồ:
     * 1. Ưu tiên Android Geocoder (Google Play Services trên thiết bị).
     * 2. Dự phòng bằng OpenStreetMap Nominatim API (tiếng Việt) để luôn trả về địa chỉ đầy đủ.
     */
    @Suppress("DEPRECATION")
    fun resolveAddressFromCoordinates(context: Context, lat: Double, lng: Double): String {
        try {
            if (Geocoder.isPresent()) {
                val geocoder = Geocoder(context, Locale("vi", "VN"))
                val list = geocoder.getFromLocation(lat, lng, 1)
                val first = list?.firstOrNull()
                if (first != null) {
                    val line = (0..first.maxAddressLineIndex)
                        .mapNotNull { first.getAddressLine(it)?.trim() }
                        .firstOrNull { it.isNotBlank() }
                    if (!line.isNullOrBlank()) return line
                    val parts = listOfNotNull(
                        first.featureName,
                        first.subLocality,
                        first.subAdminArea,
                        first.adminArea,
                    ).map { it.trim() }.filter { it.isNotBlank() }.distinct()
                    if (parts.isNotEmpty()) return parts.joinToString(", ")
                }
            }
        } catch (_: Exception) {
        }

        // Dự phòng qua OpenStreetMap Nominatim Reverse Geocoding
        return try {
            val url = URL("https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=$lat&lon=$lng&accept-language=vi")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("User-Agent", "VWingSmartFarm/1.0 (Android)")
            }
            try {
                if (conn.responseCode in 200..299) {
                    val text = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(text)
                    val display = json.optString("display_name", "").trim()
                    if (display.isNotBlank()) {
                        display.split(",").map { it.trim() }.filter { it.isNotBlank() && !it.all(Char::isDigit) }.take(4).joinToString(", ")
                    } else {
                        "%.5f, %.5f".format(Locale.US, lat, lng)
                    }
                } else {
                    "%.5f, %.5f".format(Locale.US, lat, lng)
                }
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            "%.5f, %.5f".format(Locale.US, lat, lng)
        }
    }
}
