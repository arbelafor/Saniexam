#!/usr/bin/env bash
#
# Release-pipeline license gate (CI mirror of the Gradle task
# `:app:checkReleasePackLicense`). Refuses release builds that
# bundle a dev-placeholder / unknown / blank license.
#
# Usage:   tools/check-pack-license.sh
# Exit 0:  license is acceptable.
# Exit 1:  license is refused.
#
# The Gradle task and this script MUST stay in sync. The shared
# rule lives in [es.saniexam.app.build.PackLicenseGate] (Kotlin)
# and is re-implemented in this file in pure POSIX grep / sed.
#
# This script does NOT modify the manifest. A future PR that
# ships a cleared-of-rights pack updates the manifest's
# `license` field and the same script re-runs to confirm the
# gate is happy.
set -euo pipefail

# Resolve the project root from the script's own path so the
# script works regardless of `pwd`.
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &> /dev/null && pwd)"
PROJECT_ROOT="$(cd -- "${SCRIPT_DIR}/.." &> /dev/null && pwd)"

MANIFEST="${PROJECT_ROOT}/app/src/main/assets/pack-manifest.json"

if [[ ! -f "${MANIFEST}" ]]; then
    echo "Refused: pack-manifest.json missing at ${MANIFEST}" >&2
    exit 1
fi

# Hand-rolled JSON field extractor. POSIX-portable (no jq
# dependency).
extract_license() {
    grep -E '"license"\s*:\s*"' "${MANIFEST}" \
        | head -n1 \
        | sed -E 's/.*"license"\s*:\s*"([^"]*)".*/\1/'
}

LICENSE="$(extract_license)"

REFUSED_LICENSES=("dev-placeholder" "unknown")

is_refused() {
    local value="$1"
    [[ -z "${value// }" ]] && return 0  # blank / whitespace-only
    for refused in "${REFUSED_LICENSES[@]}"; do
        if [[ "${value}" == "${refused}" ]]; then
            return 0
        fi
    done
    return 1
}

if is_refused "${LICENSE}"; then
    cat >&2 <<EOF
Refused: bundled pack manifest license is '${LICENSE}'.
Release pipeline gate (PR7) refuses to ship a public APK
with a license in {dev-placeholder, unknown} or blank.
Replace assets/question-packs/* and pack-manifest.json with a
cleared-of-rights pack, or pin a known license
(MIT / CC-BY-* / cleared-of-rights / Apache-2.0).
EOF
    exit 1
fi

echo "check-pack-license: PASS (license='${LICENSE}')."
exit 0
