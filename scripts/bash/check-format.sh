#!/bin/bash
#
# check-format.sh - C/C++ code style checker using Artistic Style (AStyle)
#
# Usage:
#   ./scripts/bash/check-format.sh          # check only, exit 1 on style issues
#   ./scripts/bash/check-format.sh --fix    # reformat the files in place
#
# Description:
#   Checks the firmware sources (*.c, *.cpp, *.h) against the project style in
#   scripts/config/.astyle.cfg. Third-party code is skipped: the RAK3172 driver
#   submodule and the fetched west/PlatformIO workspaces and build folders.
#
#   The check runs AStyle with --dry-run, so no file is touched.
#
# Requirements:
#   - astyle (>= 3.0) on PATH
#   - Run from anywhere inside the repository

set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

OPTIONS="scripts/config/.astyle.cfg"

# Own firmware sources only
SOURCE_DIRS=(
    firmware/ESP32_LoRa/src
    firmware/ESP32_LoRa/include
    firmware/ESP32_BLE/src
    firmware/nRF5340/app
)

mapfile -t FILES < <(find "${SOURCE_DIRS[@]}" -type f \( -name "*.c" -o -name "*.cpp" -o -name "*.h" \) 2>/dev/null | sort)

if [ ${#FILES[@]} -eq 0 ]; then
    echo "No C/C++ files found to check"
    exit 0
fi

if [ "${1:-}" = "--fix" ]; then
    astyle --options="${OPTIONS}" "${FILES[@]}"
    exit 0
fi

# --dry-run reports "Formatted  <file>" for every file that would change
# (LC_ALL=C: astyle localizes this line, e.g. "Formatiert" in German).
UNFORMATTED=$(LC_ALL=C LANG=C astyle --options="${OPTIONS}" --dry-run "${FILES[@]}" | sed -n 's/^Formatted  *//p')

if [ -n "${UNFORMATTED}" ]; then
    echo "Code style issues found in:"
    echo "${UNFORMATTED}" | sed 's/^/  /'
    echo ""
    echo "To fix them, run: ./scripts/bash/check-format.sh --fix"
    exit 1
fi

echo "All ${#FILES[@]} files are properly formatted."
