# Máy chủ tìm địa điểm Photon riêng (dữ liệu OpenStreetMap Việt Nam) cho VWing. Chạy ở 127.0.0.1:2322; máy chủ vệ tinh gọi vào đây
# qua biến PHOTON_URL (Start-Public.ps1 tự đặt). Cài đặt và cập nhật dữ liệu: xem PHOTON.md.
param([string]$Dir = 'E:\Photon', [int]$Port = 2322, [string]$Heap = '2G')
$ErrorActionPreference = 'Stop'
$jar = Get-ChildItem $Dir -Filter 'photon-*.jar' | Sort-Object Name | Select-Object -Last 1
if (-not $jar) { throw "Không thấy photon-*.jar trong $Dir (xem PHOTON.md)." }
if (-not (Test-Path (Join-Path $Dir 'photon_data'))) { throw "Chưa có cơ sở dữ liệu photon_data trong $Dir (xem PHOTON.md)." }
Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue }
Push-Location $Dir
try { & java "-Xmx$Heap" -jar $jar.FullName serve -listen-ip 127.0.0.1 -listen-port $Port }
finally { Pop-Location }
