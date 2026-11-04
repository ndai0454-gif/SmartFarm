param([ValidateSet('live','mock')][string]$Mode = 'live', [int]$Port = 8000, [string]$Bind = '0.0.0.0', [string]$PublicUrl = '')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$backendDirectory = Join-Path $projectRoot 'vwing-satellite-backend'
$tokenPath = Join-Path $projectRoot '.satellite-token'
if (-not (Test-Path -LiteralPath $tokenPath)) {
    [System.IO.File]::WriteAllText($tokenPath, [Guid]::NewGuid().ToString('N') + [Guid]::NewGuid().ToString('N'))
}
$env:VWING_SAT_TOKEN = [System.IO.File]::ReadAllText($tokenPath).Trim()
$env:VWING_EE_MODE = $Mode
$env:EE_PROJECT = 'gen-lang-client-0598596007'
# Máy tính bảng truy cập qua Wi-Fi nên phải nghe trên mọi địa chỉ (không chỉ 127.0.0.1) và quảng bá địa chỉ LAN.
$lan = (Get-NetIPAddress -AddressFamily IPv4 | Where-Object { $_.IPAddress -match '^(192\.168|10|172\.(1[6-9]|2\d|3[01]))\.' -and $_.InterfaceAlias -notmatch 'vEthernet|WSL|Loopback' } | Select-Object -First 1).IPAddress
if (-not $lan) { $lan = '127.0.0.1' }
if ($PublicUrl) { $env:VWING_PUBLIC_URL = $PublicUrl; Write-Host "Địa chỉ công khai: $PublicUrl" }
else { $env:VWING_PUBLIC_URL = "http://${lan}:$Port"; Write-Host "Nhập vào app: http://${lan}:$Port" }
Write-Host "Backend $Mode, port $Port. Token is stored in $tokenPath (do not commit)."
Push-Location $backendDirectory
try {
    $pythonPathFile = Join-Path $projectRoot '.deepforest-python'
    $backendPython = Join-Path $backendDirectory '.venv\Scripts\python.exe'
    if (Test-Path -LiteralPath $pythonPathFile) {
        $configuredPython = [IO.File]::ReadAllText($pythonPathFile).Trim()
        if (Test-Path -LiteralPath $configuredPython) { $backendPython = $configuredPython }
    }
    & $backendPython -m uvicorn app.main:app --host $Bind --port $Port
}
finally { Pop-Location }
