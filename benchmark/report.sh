#!/bin/bash
set -euo pipefail

# ─── Usage ────────────────────────────────────────────────────────────────────
usage() {
  cat <<'EOF'
Usage: bash report.sh <results_dir>

Generate benchmark report from downloaded results.

Examples:
  bash report.sh ./bench_20260330_141500
  bash report.sh ./bench_20260330_141500  # re-run after fixing generate_report.py
EOF
  exit 0
}

if [[ $# -lt 1 || "$1" == "-h" || "$1" == "--help" ]]; then
  usage
fi

RESULTS_DIR="$1"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [[ ! -d "$RESULTS_DIR" ]]; then
  echo "ERROR: Results directory not found: $RESULTS_DIR"
  exit 1
fi

log() { echo "[$(date '+%H:%M:%S')] $*"; }

log "Generating report from $RESULTS_DIR..."

if ! command -v python3 &>/dev/null; then
  echo "ERROR: python3 is required"
  exit 1
fi

pip3 install -q -r "$SCRIPT_DIR/visualize/requirements.txt" 2>/dev/null
python3 "$SCRIPT_DIR/visualize/generate_report.py" "$RESULTS_DIR" "$RESULTS_DIR/report"

log "Done."
log "Report: $RESULTS_DIR/report/report.html"
