#!/usr/bin/env bash
#
# Release-pipeline license gate (CI mirror of the Gradle task
# `:app:checkReleasePackLicense`). Refuses release builds
# that bundle a dev-placeholder / unknown / blank license OR a
# manifest without a `category` field.
#
# Usage:   tools/check-pack-license.sh
# Exit 0:  license + category are acceptable.
# Exit 1:  license or category is refused.
#
# The Gradle task and this script MUST stay in sync. The rule is
# implemented in app/build.gradle.kts and mirrored here in pure POSIX
# grep / sed.
#
# The license comparison is case-insensitive: a case-variant like
# `Dev-Placeholder` is still refused. The category comparison
# requires the field to be present and non-blank (spec
# `professional-categories` "Pack-Level Category Field").
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

# Hand-rolled JSON field extractor. POSIX-portable (no jq dependency).
# It tracks object depth and only accepts fields found at depth 1, so
# nested `license` / `category` fields cannot satisfy the release gate.
extract_string() {
    local key="$1"
    awk -v key="${key}" '
        function update_depth(line,    i, ch) {
            for (i = 1; i <= length(line); i++) {
                ch = substr(line, i, 1)
                if (ch == "{") depth++
                if (ch == "}") depth--
            }
        }
        {
            if (depth == 1 && $0 ~ "^[[:space:]]*\\\"" key "\\\"[[:space:]]*:[[:space:]]*\\\"") {
                value = $0
                sub("^[[:space:]]*\\\"" key "\\\"[[:space:]]*:[[:space:]]*\\\"", "", value)
                sub("\\\".*$", "", value)
                print value
                exit
            }
            update_depth($0)
        }
    ' "${MANIFEST}"
}

LICENSE="$(extract_string license)"
CATEGORY="$(extract_string category)"

REFUSED_LICENSES=("dev-placeholder" "unknown")

# `tr` is POSIX; this lowercases the value in-place.
lowercase() {
    printf '%s' "$1" | tr '[:upper:]' '[:lower:]'
}

is_refused_license() {
    local value="$1"
    local lowered
    lowered="$(lowercase "${value}")"
    [[ -z "${lowered// }" ]] && return 0  # blank / whitespace-only
    for refused in "${REFUSED_LICENSES[@]}"; do
        if [[ "${lowered}" == "${refused}" ]]; then
            return 0
        fi
    done
    return 1
}

LICENSE_REFUSED="false"
if is_refused_license "${LICENSE}"; then
    LICENSE_REFUSED="true"
fi

CATEGORY_MISSING="false"
if [[ -z "${CATEGORY// }" ]]; then
    CATEGORY_MISSING="true"
fi

if [[ "${LICENSE_REFUSED}" == "true" || "${CATEGORY_MISSING}" == "true" ]]; then
    cat >&2 <<EOF
Refused: bundled pack manifest fails the release gate.
  license = '${LICENSE}' (refused=${LICENSE_REFUSED})
  category = '${CATEGORY}' (missing=${CATEGORY_MISSING})
Release pipeline gate refuses to ship a public APK when
  - license is in {dev-placeholder, unknown} (any case), blank, or missing, OR
  - the manifest has no \`category\` field (spec \`professional-categories\`).
Replace assets/question-packs/* and pack-manifest.json with a
cleared-of-rights pack carrying \`category: "TCAE"\`.
EOF
    exit 1
fi

echo "check-pack-license: PASS (license='${LICENSE}', category='${CATEGORY}')."
exit 0
