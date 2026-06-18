#!/usr/bin/env pwsh
#
# No-network smoke check (PowerShell mirror of
# [es.saniexam.app.build.NoNetworkGuardTest]). Enforces the
# project-wide no-network rule from the project root.
#
# Usage:   pwsh tools/check-no-network.ps1
# Exit 0:  rule holds.
# Exit 1:  rule violated.
$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Resolve-Path "$ScriptDir/.." | Select-Object -ExpandProperty Path
$Manifest = Join-Path $ProjectRoot "app/src/main/AndroidManifest.xml"
$MainSrc = Join-Path $ProjectRoot "app/src/main"

if (-not (Test-Path $Manifest)) {
    Write-Error "Refused: AndroidManifest.xml missing at $Manifest"
    exit 1
}

# 1. Manifest: no INTERNET permission.
$manifestContent = Get-Content $Manifest -Raw
if ($manifestContent -match "android\.permission\.INTERNET") {
    Write-Error "Refused: AndroidManifest.xml declares android.permission.INTERNET."
    exit 1
}

# 2. Production source: no HTTP / WorkManager references.
$needles = @(
    "HttpClient",
    "okhttp",
    "retrofit",
    "WorkManager",
    "URLConnection",
    "HttpURLConnection",
    "OkHttpClient"
)
$offenders = New-Object System.Collections.Generic.List[string]
foreach ($needle in $needles) {
    $hits = Get-ChildItem -Path $MainSrc -Recurse -Include "*.kt", "*.java" `
        | Where-Object { $_.FullName -notmatch "[/\\]build[/\\]" `
                       -and $_.FullName -notmatch "[/\\]assets[/\\]" `
                       -and $_.FullName -notmatch "[/\\]test[/\\]" }
    foreach ($file in $hits) {
        $content = Get-Content $file.FullName -Raw
        if ($content -match [regex]::Escape($needle)) {
            $offenders.Add("$($file.FullName): contains '$needle'")
        }
    }
}

if ($offenders.Count -gt 0) {
    Write-Error "Refused: no-network rule violations in $MainSrc :"
    foreach ($offender in $offenders) {
        Write-Error "  $offender"
    }
    exit 1
}

Write-Output "check-no-network: PASS."
exit 0
