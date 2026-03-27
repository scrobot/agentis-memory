#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# ─── Usage ────────────────────────────────────────────────────────────────────
usage() {
  cat <<'EOF'
Usage: bash rerun.sh [OPTIONS]

Options:
  -s, --scenario SCENARIO   Run only this scenario (repeatable). Can be:
                             name (e.g. "sets") or path (e.g. "scenarios/sets.cfg")
  -S, --server SERVER       Run only this server (repeatable).
                             Values: agentis, redis, dragonfly, lux
  -p, --pipeline DEPTH      Run only this pipeline depth (repeatable). E.g. 1, 10, 50, 100
      --no-pipeline         Skip pipeline benchmarks entirely
      --no-scenario         Skip scenario benchmarks entirely
      --no-warmup           Skip warmup phase
      --no-report           Skip report generation
  -h, --help                Show this help

Examples:
  bash rerun.sh -s sets                        # sets scenario, all servers
  bash rerun.sh -s sets -s hashes              # sets + hashes, all servers
  bash rerun.sh -s strings -S agentis -S redis # strings, only agentis vs redis
  bash rerun.sh --no-scenario -p 100           # only pipeline=100, all servers
  bash rerun.sh                                # everything (default)
EOF
  exit 0
}

# ─── Config ───────────────────────────────────────────────────────────────────
ALL_SERVERS=("agentis-memory:6399" "redis:6379" "dragonfly:6379" "lux:6379")
ALL_SERVER_NAMES=("agentis_memory" "redis" "dragonfly" "lux")
ALL_SCENARIOS=("strings" "hashes" "lists" "sorted-sets" "sets" "mixed-workload")
ALL_PIPELINES=(1 10 50 100)

CONTAINER_RESULTS="/tmp/bench_results"

# ─── Parse args ───────────────────────────────────────────────────────────────
FILTER_SCENARIOS=()
FILTER_SERVERS=()
FILTER_PIPELINES=()
RUN_SCENARIOS=true
RUN_PIPELINES=true
RUN_WARMUP=false
RUN_REPORT=true

while [[ $# -gt 0 ]]; do
  case "$1" in
    -s|--scenario)
      # Accept "sets", "scenarios/sets.cfg", or full path
      val="${2##*/}"; val="${val%.cfg}"
      FILTER_SCENARIOS+=("$val"); shift 2 ;;
    -S|--server)
      case "${2,,}" in
        agentis*) FILTER_SERVERS+=(0) ;;
        redis)    FILTER_SERVERS+=(1) ;;
        dragon*)  FILTER_SERVERS+=(2) ;;
        lux)      FILTER_SERVERS+=(3) ;;
        *) echo "Unknown server: $2"; exit 1 ;;
      esac; shift 2 ;;
    -p|--pipeline)    FILTER_PIPELINES+=("$2"); shift 2 ;;
    --no-pipeline)    RUN_PIPELINES=false; shift ;;
    --no-scenario)    RUN_SCENARIOS=false; shift ;;
    --no-warmup)      RUN_WARMUP=false; shift ;;
    --no-report)      RUN_REPORT=false; shift ;;
    -h|--help)        usage ;;
    *) echo "Unknown option: $1"; usage ;;
  esac
done

# Apply defaults if no filters specified
SCENARIOS=("${FILTER_SCENARIOS[@]:-${ALL_SCENARIOS[@]}}")
PIPELINES=("${FILTER_PIPELINES[@]:-${ALL_PIPELINES[@]}}")

if [[ ${#FILTER_SERVERS[@]} -eq 0 ]]; then
  SERVER_INDICES=(0 1 2 3)
else
  SERVER_INDICES=("${FILTER_SERVERS[@]}")
fi

# ─── Helpers ──────────────────────────────────────────────────────────────────
log() { echo "[$(date '+%H:%M:%S')] $*"; }

flush_server() {
  local host="$1"; local port="$2"
  docker compose exec -T redis redis-cli -h "$host" -p "$port" FLUSHALL > /dev/null 2>&1 || true
}

run_memtier() {
  local host="$1"; local port="$2"; local extra_args="$3"; local result_name="$4"
  local container_out="$CONTAINER_RESULTS/${result_name}.json"

  docker compose exec -T memtier memtier_benchmark \
    -s "$host" -p "$port" \
    $extra_args \
    --json-out-file="$container_out" \
    --hide-histogram \
    2>&1 | tail -12 || true
}

# ─── Preflight ────────────────────────────────────────────────────────────────
log "=== Agentis Benchmark ==="
log "Scenarios: ${SCENARIOS[*]}"
log "Servers: $(for i in "${SERVER_INDICES[@]}"; do echo -n "${ALL_SERVER_NAMES[$i]} "; done)"
$RUN_PIPELINES && log "Pipelines: ${PIPELINES[*]}" || log "Pipelines: skipped"

docker compose exec -T memtier mkdir -p "$CONTAINER_RESULTS"

# ─── Warmup ───────────────────────────────────────────────────────────────────
if $RUN_WARMUP; then
  log "Warming up servers..."
  for i in "${SERVER_INDICES[@]}"; do
    IFS=: read -r host port <<< "${ALL_SERVERS[$i]}"
    log "  Warming up ${ALL_SERVER_NAMES[$i]}..."
    docker compose exec -T memtier memtier_benchmark \
      -s "$host" -p "$port" \
      --protocol=redis --requests=10000 --threads=2 --clients=10 \
      --ratio=1:1 --data-size=64 --hide-histogram > /dev/null 2>&1 || true
    flush_server "$host" "$port"
  done
fi

# ─── Scenario benchmarks ──────────────────────────────────────────────────────
if $RUN_SCENARIOS; then
  log "Running scenario benchmarks..."
  for scenario in "${SCENARIOS[@]}"; do
    cfg_file="scenarios/${scenario}.cfg"
    if [[ ! -f "$cfg_file" ]]; then
      log "  WARNING: $cfg_file not found, skipping"
      continue
    fi
    log "  Scenario: $scenario"
    for i in "${SERVER_INDICES[@]}"; do
      IFS=: read -r host port <<< "${ALL_SERVERS[$i]}"
      name="${ALL_SERVER_NAMES[$i]}"
      log "    -> $name"

      cfg_args=$(grep -v '^\s*#' "$cfg_file" | grep -v '^\s*$' | tr '\n' ' ')
      run_memtier "$host" "$port" "$cfg_args" "${name}_${scenario}"
      flush_server "$host" "$port"
    done
  done
fi

# ─── Pipeline benchmarks ──────────────────────────────────────────────────────
if $RUN_PIPELINES; then
  log "Running pipeline benchmarks..."
  for pipeline in "${PIPELINES[@]}"; do
    log "  Pipeline depth: $pipeline"
    for i in "${SERVER_INDICES[@]}"; do
      IFS=: read -r host port <<< "${ALL_SERVERS[$i]}"
      name="${ALL_SERVER_NAMES[$i]}"
      log "    -> $name (pipeline=$pipeline)"

      run_memtier "$host" "$port" \
        "--protocol=redis --threads=4 --clients=50 --requests=100000 --ratio=1:10 --data-size=256 --pipeline=$pipeline" \
        "${name}_pipeline_${pipeline}"
      flush_server "$host" "$port"
    done
  done
fi

# ─── Extract results from container ──────────────────────────────────────────
log "Extracting results from container..."
rm -rf /tmp/bench_extract 2>/dev/null || true
mkdir -p /tmp/bench_extract

docker cp bench-memtier:"$CONTAINER_RESULTS"/. /tmp/bench_extract/

# Structure into subdirs
for name in "${ALL_SERVER_NAMES[@]}"; do
  mkdir -p /tmp/bench_extract/"$name"
  for f in /tmp/bench_extract/${name}_*.json; do
    [ -f "$f" ] || continue
    base=$(basename "$f" | sed "s/^${name}_//")
    cp "$f" /tmp/bench_extract/"$name"/"$base"
  done
done

log "Results in /tmp/bench_extract/"
ls /tmp/bench_extract/*.json 2>/dev/null | head -20

# ─── Visualize ────────────────────────────────────────────────────────────────
if $RUN_REPORT; then
  log "=== Generating report ==="
  if command -v python3 &>/dev/null; then
    cd visualize
    pip3 install -q -r requirements.txt 2>/dev/null
    python3 generate_report.py /tmp/bench_extract /tmp/bench_extract/report
    cd ..
    log "Report: /tmp/bench_extract/report/"
  else
    log "python3 not found — skipping visualization."
  fi
fi

log "=== Done ==="
