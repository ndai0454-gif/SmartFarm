# Đăng ký (hoặc gỡ) các tác vụ Windows chạy máy chủ VWing mỗi khi bạn đăng nhập. Không cần quyền quản trị.
#   .\Install-AutoStart.ps1            # đăng ký cả hai máy chủ và chạy ngay
#   .\Install-AutoStart.ps1 -Remove    # gỡ cả hai tác vụ
#   .\Install-AutoStart.ps1 -Only Sync # chỉ máy chủ đồng bộ (hoặc -Only Satellite)
param([switch]$Remove, [ValidateSet('All','Satellite','Sync')][string]$Only = 'All', [ValidateSet('live','mock')][string]$Mode = 'live')
$tasks = @(
    @{ Key = 'Satellite'; Name = 'VWing Satellite Backend'; Script = 'Run-SatelliteForever.ps1'; Args = "-Mode $Mode"; Desc = 'Chạy máy chủ vệ tinh VWing (FastAPI, cổng 8000) khi đăng nhập và tự khởi động lại nếu bị tắt.' },
    @{ Key = 'Sync'; Name = 'VWing Sync Server'; Script = 'Run-SyncServerForever.ps1'; Args = ''; Desc = 'Chạy máy chủ đồng bộ VWing (Node, cổng 8790) khi đăng nhập và tự khởi động lại nếu bị tắt.' }
) | Where-Object { $Only -eq 'All' -or $_.Key -eq $Only }
$user = "$env:USERDOMAIN\$env:USERNAME"
foreach ($t in $tasks) {
    if ($Remove) {
        Stop-ScheduledTask -TaskName $t.Name -ErrorAction SilentlyContinue
        Unregister-ScheduledTask -TaskName $t.Name -Confirm:$false -ErrorAction SilentlyContinue
        Write-Host "Đã gỡ tác vụ '$($t.Name)'."; continue
    }
    $script = Join-Path $PSScriptRoot $t.Script
    $action = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File `"$script`" $($t.Args)".TrimEnd()
    $trigger = New-ScheduledTaskTrigger -AtLogOn -User $user
    $settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -StartWhenAvailable `
        -ExecutionTimeLimit ([TimeSpan]::Zero) -MultipleInstances IgnoreNew -RestartCount 5 -RestartInterval (New-TimeSpan -Minutes 1)
    $principal = New-ScheduledTaskPrincipal -UserId $user -LogonType Interactive -RunLevel Limited
    Register-ScheduledTask -TaskName $t.Name -Action $action -Trigger $trigger -Settings $settings -Principal $principal -Description $t.Desc -Force | Out-Null
    Write-Host "Đã đăng ký tác vụ '$($t.Name)' (chạy khi đăng nhập)."
}
