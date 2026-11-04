# Reconnect an already paired tablet without duplicate/stale mDNS transports.
[CmdletBinding()]
param([string]$TabletSerial = 'HA24RANR')
$ErrorActionPreference = 'Stop'
$adbPath = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
if (-not (Test-Path -LiteralPath $adbPath)) { throw "SDK adb missing: $adbPath" }
function Enable-VWingReverse([string]$DeviceSerial) {
    # 127.0.0.1 in VWing refers to the tablet; bridge it to this computer after each reconnect.
    foreach ($servicePort in @(8000, 8790)) {
        & $adbPath -s $DeviceSerial reverse "tcp:$servicePort" "tcp:$servicePort"
        if ($LASTEXITCODE -ne 0) { throw "Cannot forward VWing port $servicePort for $DeviceSerial." }
    }
    Write-Host 'VWing connected: use http://127.0.0.1:8000 for Satellite (ADB connection required).'
}
$usbOnline = @(& $adbPath devices -l) | Where-Object { $_ -match "^$([regex]::Escape($TabletSerial))\s+device\b" }
if ($usbOnline) { $usbOnline; Enable-VWingReverse $TabletSerial; return }
$services = @(& $adbPath mdns services)
$endpoints = @($services | Where-Object { $_ -match [regex]::Escape("adb-$TabletSerial-") -and $_ -match '_adb-tls-connect\._tcp' } | ForEach-Object { if ($_ -match '(\d+\.\d+\.\d+\.\d+:\d+)\s*$') { $Matches[1] } } | Select-Object -Unique)
if (-not $endpoints.Count) { throw 'No paired tablet advertised. Open Wireless debugging on the tablet and retry.' }
# Per-process only; does not alter global Windows environment settings.
$env:ADB_MDNS_AUTO_CONNECT = '0'
Remove-Item Env:ADB_LIBUSB -ErrorAction SilentlyContinue
& $adbPath kill-server
& $adbPath start-server
if ($LASTEXITCODE -ne 0) { throw 'ADB server failed to start.' }
foreach ($endpoint in $endpoints) {
    & $adbPath connect $endpoint
    if ($LASTEXITCODE -ne 0) { continue }
    $serial = (& $adbPath -s $endpoint shell getprop ro.serialno 2>$null | Out-String).Trim()
    if ($LASTEXITCODE -eq 0 -and $serial -eq $TabletSerial) {
        Write-Host "Connected: $TabletSerial at $endpoint (Android Studio uses ADB port 5037)."
        Enable-VWingReverse $endpoint
        & $adbPath devices -l
        return
    }
    & $adbPath disconnect $endpoint
}
throw 'None of the discovered endpoints accepted the paired tablet connection.'
