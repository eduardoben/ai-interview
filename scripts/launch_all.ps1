$emulator = "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe"
$workDir = "$env:LOCALAPPDATA\Android\Sdk\emulator"

$avds = @(
    "Pixel_10_Pro_XL",
    "Galaxy_S25_Ultra",
    "Galaxy_Tab_S7_Plus",
    "Galaxy_A54_5G"
)

foreach ($avd in $avds) {
    Write-Host "Starting emulator: $avd..."
    $proc = Start-Process -FilePath $emulator -ArgumentList "-avd", $avd -WorkingDirectory $workDir -PassThru
    Write-Host "Started $avd (PID $($proc.Id))"
    Start-Sleep -Seconds 3
}

Write-Host "All 4 emulators dispatched."

