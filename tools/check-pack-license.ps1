#!/usr/bin/env pwsh
#
# Release-pipeline license gate (PowerShell mirror of the Gradle task
# :app:checkReleasePackLicense). Refuses release builds that
# bundle a dev-placeholder / unknown / blank license OR a
# manifest without a `category` field.
#
# Usage:   pwsh tools/check-pack-license.ps1
# Exit 0:  license + category are acceptable.
# Exit 1:  license or category is refused.
#
# The Gradle task, this script, and the bash mirror MUST stay in
# sync. The rule is implemented in app/build.gradle.kts and mirrored
# here in pure PowerShell so Windows CI runners
# (where bash is not on PATH by default) can enforce the same gate.
#
# The license comparison is case-insensitive: a case-variant like
# `Dev-Placeholder` is still refused. The category comparison
# requires the field to be present and non-blank (spec
# `professional-categories` "Pack-Level Category Field").
$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Resolve-Path "$ScriptDir/.." | Select-Object -ExpandProperty Path
$Manifest = Join-Path $ProjectRoot "app/src/main/assets/pack-manifest.json"

if (-not (Test-Path $Manifest)) {
    Write-Error "Refused: pack-manifest.json missing at $Manifest"
    exit 1
}

$content = Get-Content $Manifest -Raw
try {
    $manifestJson = $content | ConvertFrom-Json
} catch {
    Write-Error "Refused: pack-manifest.json is not valid JSON. $($_.Exception.Message)"
    exit 1
}

$license = if ($manifestJson.PSObject.Properties.Name -contains "license" -and $manifestJson.license -is [string]) { $manifestJson.license.Trim() } else { "" }
$category = if ($manifestJson.PSObject.Properties.Name -contains "category" -and $manifestJson.category -is [string]) { $manifestJson.category.Trim() } else { "" }

$refused = @("dev-placeholder", "unknown")
$normalizedLicense = $license.ToLowerInvariant()
$isLicenseRefused = [string]::IsNullOrWhiteSpace($license) -or ($refused -contains $normalizedLicense)
$isCategoryMissing = [string]::IsNullOrWhiteSpace($category)

if ($isLicenseRefused -or $isCategoryMissing) {
    $msg = @"
Refused: bundled pack manifest fails the release gate.
  license = '$license' (refused=$isLicenseRefused)
  category = '$category' (missing=$isCategoryMissing)
Release pipeline gate refuses to ship a public APK when
  - license is in {dev-placeholder, unknown} (any case), blank, or missing, OR
  - the manifest has no `category` field (spec `professional-categories`).
Replace assets/question-packs/* and pack-manifest.json with a
cleared-of-rights pack carrying `category: "TCAE"`.
"@
    Write-Error $msg
    exit 1
}

Write-Output "check-pack-license: PASS (license='$license', category='$category')."
exit 0
