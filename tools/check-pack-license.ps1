#!/usr/bin/env pwsh
#
# Release-pipeline license gate (PowerShell mirror of
# [es.saniexam.app.build.PackLicenseGate] and the Gradle task
# :app:checkReleasePackLicense). Refuses release builds that
# bundle a dev-placeholder / unknown / blank license.
#
# Usage:   pwsh tools/check-pack-license.ps1
# Exit 0:  license is acceptable.
# Exit 1:  license is refused.
#
# The Gradle task, this script, and the bash mirror MUST stay in
# sync. The shared rule lives in
# [es.saniexam.app.build.PackLicenseGate] (Kotlin) and is
# re-implemented here in pure PowerShell so Windows CI runners
# (where bash is not on PATH by default) can enforce the same gate.
$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Resolve-Path "$ScriptDir/.." | Select-Object -ExpandProperty Path
$Manifest = Join-Path $ProjectRoot "app/src/main/assets/pack-manifest.json"

if (-not (Test-Path $Manifest)) {
    Write-Error "Refused: pack-manifest.json missing at $Manifest"
    exit 1
}

# Hand-rolled JSON field extractor. POSIX-portable (no ConvertFrom-Json
# version skew).
$content = Get-Content $Manifest -Raw
$match = [regex]::Match($content, '"license"\s*:\s*"([^"]*)"')
$license = if ($match.Success) { $match.Groups[1].Value.Trim() } else { "" }

$refused = @("dev-placeholder", "unknown")
$isRefused = [string]::IsNullOrWhiteSpace($license) -or ($refused -contains $license)

if ($isRefused) {
    $msg = @"
Refused: bundled pack manifest license is '$license'.
Release pipeline gate (PR7) refuses to ship a public APK
with a license in {dev-placeholder, unknown} or blank.
Replace assets/question-packs/* and pack-manifest.json with a
cleared-of-rights pack, or pin a known license
(MIT / CC-BY-* / cleared-of-rights / Apache-2.0).
"@
    Write-Error $msg
    exit 1
}

Write-Output "check-pack-license: PASS (license='$license')."
exit 0
