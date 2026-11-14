import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.secrets)
    alias(libs.plugins.roborazzi)
}

// Khóa Google Maps đọc từ local.properties (không đưa vào kho mã); không có khóa thì dùng giá trị mặc định rỗng nghĩa
secrets {
    defaultPropertiesFileName = "local.defaults.properties"
}

// Đọc cấu hình môi trường Dev từ file .env (để dev quản lý khóa Gemini API, không bắt nông dân nhập)
val devEnvProps = Properties().apply {
    val candidates = listOf(
        rootProject.file(".env"),
        file("E:/New folder/SmartFarm/.env"),
        file("E:/New folder/SmartFarm/ui-prototype/.env")
    )
    for (envFile in candidates) {
        if (envFile.exists()) {
            envFile.readLines(Charsets.UTF_8).forEach { rawLine ->
                val line = rawLine.trim()
                if (line.isNotEmpty() && !line.startsWith("#") && line.contains("=")) {
                    val key = line.substringBefore("=").trim()
                    val value = line.substringAfter("=").trim().removeSurrounding("\"").removeSurrounding("'")
                    if (key.isNotEmpty() && value.isNotEmpty() && !containsKey(key)) {
                        setProperty(key, value)
                    }
                }
            }
        }
    }
}

android {
    namespace = "com.example.test_tablet"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.test_tablet"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        // Giá trị dự phòng để ghép manifest khi chạy kiểm thử đơn vị (plugin secrets đặt khoá thật cho bản build)
        manifestPlaceholders["MAPS_API_KEY"] = "DEFAULT_API_KEY"

        // Nạp khóa Gemini API & Model từ file .env của Dev vào tài nguyên ứng dụng
        val devGeminiKey = devEnvProps.getProperty("GEMINI_API_KEY", "")
        val devGeminiModel = devEnvProps.getProperty("GEMINI_MODEL", "gemini-3.8-flash")
        val devFirebaseApiKey = devEnvProps.getProperty("FIREBASE_API_KEY", "")
        val devFirebaseAppId = devEnvProps.getProperty("FIREBASE_APP_ID", "")
        val devFirebaseProjectId = devEnvProps.getProperty("FIREBASE_PROJECT_ID", "")
        resValue("string", "dev_gemini_api_key", devGeminiKey)
        resValue("string", "dev_gemini_model", devGeminiModel)
        resValue("string", "dev_firebase_api_key", devFirebaseApiKey)
        resValue("string", "dev_firebase_app_id", devFirebaseAppId)
        resValue("string", "dev_firebase_project_id", devFirebaseProjectId)

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Khóa ký bản phát hành đọc từ keystore.properties (không đưa vào kho mã). Mất khóa này = không cập nhật được app đã phát hành.
    val keystoreProps = Properties().apply {
        val f = rootProject.file("keystore.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    signingConfigs {
        if (keystoreProps.getProperty("storeFile") != null) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        resValues = true
    }
    sourceSets {
        getByName("main") {
            assets.srcDirs("src/main/assets", "E:/New folder/SmartFarm/ui-prototype")
        }
    }
    androidResources {
        ignoreAssetsPatterns += listOf("!*figma*", "!*.zip", "!*.md", "!*preview*.png", "!*.mjs")
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true      // Robolectric cần tài nguyên để dựng giao diện Compose trên JVM
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    // QR: zxing tạo/giải mã mã; CameraX cung cấp khung hình camera để quét.
    // Bản đồ vệ tinh Google để vẽ ranh vườn
    implementation(libs.maps.compose)
    implementation(libs.play.services.maps)
    implementation(libs.zxing.core)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    // Firebase Authentication (Phone + OTP) & Cloud Firestore (lưu dữ liệu từng nông dân)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    testImplementation(libs.junit)
    // org.json và kXML2 thật để kiểm thử JVM (trên thiết bị dùng bản của Android)
    testImplementation(libs.json.org)
    testImplementation(libs.kxml2)
    // Kiểm thử giao diện Compose trên JVM (không cần máy thật) + ảnh chụp màn hình so sánh theo nhiều cỡ màn hình (kỹ thuật của android/nowinandroid)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
