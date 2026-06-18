#!/usr/bin/env bash
#
# No-network smoke check (CI mirror of
# [es.saniexam.app.build.NoNetworkGuardTest]). Enforces the
# project-wide no-network rule from the project root.
#
# Usage:   tools/check-no-network.sh
# Exit 0:  rule holds.
# Exit 1:  rule violated.
#
# The Gradle test and this script MUST stay in sync. The shared
# rule lives in the JUnit guard and is re-implemented here in
# pure POSIX grep.
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &> /dev/null && pwd)"
PROJECT_ROOT="$(cd -- "${SCRIPT_DIR}/.." &> /dev/null && pwd)"

MANIFEST="${PROJECT_ROOT}/app/src/main/AndroidManifest.xml"
MAIN_SRC_DIR="${PROJECT_ROOT}/app/src/main"

if [[ ! -f "${MANIFEST}" ]]; then
    echo "Refused: AndroidManifest.xml missing at ${MANIFEST}" >&2
    exit 1
fi

# 1. Manifest: no INTERNET permission.
if grep -q "android.permission.INTERNET" "${MANIFEST}"; then
    echo "Refused: AndroidManifest.xml declares android.permission.INTERNET." >&2
    exit 1
fi

# 2. Production source: no HTTP / WorkManager references.
NETWORK_NEEDLES=(
    "HttpClient"
    "okhttp"
    "retrofit"
    "WorkManager"
    "URLConnection"
    "HttpURLConnection"
    "OkHttpClient"
)

OFFENDERS=()
for needle in "${NETWORK_NEEDLES[@]}"; do
    # `git ls-files` would be ideal but this script must work
    # without a git index. The `find` filter is a reasonable
    # substitute; build/ + assets/ + test/ are excluded.
    HITS="$(grep -RIn \
        --include='*.kt' \
        --include='*.java' \
        --exclude-dir=build \
        --exclude-dir=assets \
        --exclude-dir=test \
        "${needle}" \
        "${MAIN_SRC_DIR}" 2>/dev/null || true)"
    if [[ -n "${HITS}" ]]; then
        OFFENDERS+=("${needle}: ${HITS}")
    fi
done

if (( ${#OFFENDERS[@]} > 0 )); then
    echo "Refused: no-network rule violations in ${MAIN_SRC_DIR}:" >&2
    for offender in "${OFFENDERS[@]}"; do
        echo "  ${offender}" >&2
    done
    exit 1
fi

echo "check-no-network: PASS."
exit 0
