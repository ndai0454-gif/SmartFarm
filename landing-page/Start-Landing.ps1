param([int]$Port = 4173)
$ErrorActionPreference = 'Stop'
Write-Host "VWing landing page: http://127.0.0.1:$Port"
python -m http.server $Port --bind 127.0.0.1 --directory $PSScriptRoot
