# Repair only the Lenovo TB321FU ADB WinUSB interface; never changes MTP or app data.
[CmdletBinding()]
param([string]$InstanceId = 'USB\VID_17EF&PID_7ED7&MI_01\6&8D0FA0A&0&0001')
$ErrorActionPreference = 'Stop'
$repairRoot = Join-Path (Split-Path $PSScriptRoot -Parent) 'logs\tablet-usb-repair'
New-Item -ItemType Directory -Path $repairRoot -Force | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
Start-Transcript -Path (Join-Path $repairRoot "$stamp.log") | Out-Null
try {
    $principal = New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
    if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
        throw 'Run this script as Administrator: registering a USB interface requires administrator rights.'
    }
    if ($InstanceId -notlike 'USB\VID_17EF&PID_7ED7&MI_01\*') { throw 'Unexpected device; refusing to modify it.' }
    $device = Get-PnpDevice -InstanceId $InstanceId -PresentOnly
    $service = (Get-PnpDeviceProperty -InstanceId $InstanceId -KeyName DEVPKEY_Device_Service).Data
    $compatible = (Get-PnpDeviceProperty -InstanceId $InstanceId -KeyName DEVPKEY_Device_CompatibleIds).Data
    if ($service -ne 'WINUSB' -or -not ($compatible -match 'Class_ff&SubClass_42&Prot_01')) {
        throw 'The target is not the expected WinUSB ADB interface.'
    }
    $nativeKey = "HKLM\SYSTEM\CurrentControlSet\Enum\$InstanceId\Device Parameters"
    $psKey = "Registry::HKEY_LOCAL_MACHINE\SYSTEM\CurrentControlSet\Enum\$InstanceId\Device Parameters"
    $backup = Join-Path $repairRoot "$stamp-device-parameters.reg"
    & reg.exe export $nativeKey $backup /y
    if ($LASTEXITCODE -ne 0) { throw 'Registry backup failed; no changes applied.' }
    $old = Get-ItemProperty -LiteralPath $psKey
    $guid = '{F72FE0D4-CBCB-407D-8814-9ED673D0DD6B}'
    $guids = @($old.DeviceInterfaceGUIDs) + @($old.DeviceInterfaceGUID) + @($guid)
    $guids = @($guids | Where-Object { $_ } | Select-Object -Unique)
    New-ItemProperty -LiteralPath $psKey -Name DeviceInterfaceGUIDs -PropertyType MultiString -Value $guids -Force | Out-Null
    Write-Host "Registered Android ADB interface GUID. Backup: $backup"
    & pnputil.exe /restart-device $InstanceId
    if ($LASTEXITCODE -ne 0) { throw 'Interface registered, but device restart failed. Unplug/replug the tablet USB cable.' }
    Write-Host 'DONE. The coordinator will verify adb devices and Android Studio.'
} catch {
    Write-Error $_ -ErrorAction Continue
    exit 1
} finally {
    Stop-Transcript | Out-Null
}
