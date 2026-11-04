param(
    [string]$DeviceSerial = "emulator-5554",
    [string]$WifiIp = "",
    [switch]$StartEmulator
)

$ErrorActionPreference = "Stop"
$adb = "C:\Users\ndai0\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$androidCli = "C:\Users\ndai0\AppData\AndroidCLI\android.exe"
$hybridApk = Join-Path (Split-Path $PSScriptRoot -Parent) "app\build\outputs\apk\debug\app-debug.apk"
$mobileApk = Join-Path $PSScriptRoot "SmartFarm-Aurora-Mobile.apk"

Write-Host "=== SMARTFARM AURORA - ANDROID INFRASTRUCTURE & EMULATOR LAUNCHER ===" -ForegroundColor Cyan

if ($StartEmulator -and (Test-Path $androidCli)) {
    Write-Host "[1/4] Starting Android emulator medium_phone..." -ForegroundColor Yellow
    & $androidCli emulator start medium_phone
}

if ($WifiIp -ne "") {
    Write-Host "[1/4] Connecting wireless ADB to $WifiIp..." -ForegroundColor Yellow
    & $adb connect $WifiIp
    $DeviceSerial = $WifiIp
}

$devices = & $adb devices
if ($devices -notmatch $DeviceSerial) {
    if (Test-Path $androidCli) {
        Write-Host "[1/4] Device $DeviceSerial not online yet, starting medium_phone..." -ForegroundColor Yellow
        & $androidCli emulator start medium_phone
        $DeviceSerial = "emulator-5554"
    }
}

Write-Host "[2/4] Forwarding Satellite GEE (8000) & VWing/Supabase Sync (8790) to $DeviceSerial..." -ForegroundColor Yellow
& $adb -s $DeviceSerial reverse tcp:8000 tcp:8000
& $adb -s $DeviceSerial reverse tcp:8790 tcp:8790

if (Test-Path $hybridApk) {
    Write-Host "[3/4] Installing SmartFarm Hybrid APK (com.utc.ag)..." -ForegroundColor Yellow
    & $adb -s $DeviceSerial install -r $hybridApk
} elseif (Test-Path $mobileApk) {
    Write-Host "[3/4] Installing SmartFarm Mobile APK..." -ForegroundColor Yellow
    & $adb -s $DeviceSerial install -r $mobileApk
}

Write-Host "[4/4] Launching SmartFarm Dashboard Activity..." -ForegroundColor Green
& $adb -s $DeviceSerial shell am start -n "com.utc.ag/.SmartFarmDashboardActivity"
Write-Host "Done! SmartFarm is running on $DeviceSerial." -ForegroundColor Green
