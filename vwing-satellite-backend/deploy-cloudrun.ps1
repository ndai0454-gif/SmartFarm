param(
    [string]$ProjectId = "mobile-513411",
    [string]$Region = "asia-southeast1",
    [string]$ServiceName = "vwing-satellite-backend",
    [string]$SatToken = "7d8de592cacc475fb528c798cad5cb0dbde137013ae744a799297ba7980a0738"
)

$ErrorActionPreference = "Stop"

$gcloud = Get-Command gcloud -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source
if (-not $gcloud) {
    $candidates = @(
        "$env:LOCALAPPDATA\Google\Cloud SDK\google-cloud-sdk\bin\gcloud.cmd",
        "C:\Program Files (x86)\Google\Cloud SDK\google-cloud-sdk\bin\gcloud.cmd",
        "C:\Program Files\Google\Cloud SDK\google-cloud-sdk\bin\gcloud.cmd"
    )
    $gcloud = $candidates | Where-Object { Test-Path $_ } | Select-Object -First 1
}
if (-not $gcloud) {
    Write-Error "Chua tim thay gcloud CLI. Hay cai bang: winget install -e --id Google.CloudSDK"
}

Write-Host "1. Cau hinh project $ProjectId..." -ForegroundColor Cyan
& $gcloud config set project $ProjectId

Write-Host "2. Bat Cloud Run, Cloud Build, Artifact Registry, Earth Engine APIs..." -ForegroundColor Cyan
& $gcloud services enable run.googleapis.com cloudbuild.googleapis.com artifactregistry.googleapis.com earthengine.googleapis.com --project $ProjectId

Write-Host "3. Cap quyen Earth Engine cho Cloud Run Service Account..." -ForegroundColor Cyan
$projectNumber = (& $gcloud projects describe $ProjectId --format="value(projectNumber)").Trim()
$saEmail = "$projectNumber-compute@developer.gserviceaccount.com"
& $gcloud projects add-iam-policy-binding $ProjectId --member="serviceAccount:$saEmail" --role="roles/earthengine.viewer" --quiet | Out-Null
& $gcloud projects add-iam-policy-binding $ProjectId --member="serviceAccount:$saEmail" --role="roles/serviceusage.serviceUsageConsumer" --quiet | Out-Null

Write-Host "4. Deploy len Cloud Run ($Region)..." -ForegroundColor Cyan
Set-Location $PSScriptRoot
& $gcloud run deploy $ServiceName `
    --source . `
    --region $Region `
    --platform managed `
    --allow-unauthenticated `
    --memory 2Gi `
    --cpu 2 `
    --timeout 180 `
    --set-env-vars "VWING_EE_MODE=live,EE_PROJECT=$ProjectId,VWING_SAT_TOKEN=$SatToken"

$url = (& $gcloud run services describe $ServiceName --region $Region --format="value(status.url)").Trim()
Write-Host "5. Cap nhat VWING_PUBLIC_URL = $url ..." -ForegroundColor Cyan
& $gcloud run services update $ServiceName --region $Region --update-env-vars "VWING_PUBLIC_URL=$url"

Write-Host "`nHOAN TAT! Dia chi Cloud Run cua ban la: $url" -ForegroundColor Green
Write-Host "Hay gui URL nay cho tro ly de cap nhat vao app Android VWing!" -ForegroundColor Yellow
