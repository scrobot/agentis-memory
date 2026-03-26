#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# ─── Config ───────────────────────────────────────────────────────────────────
SERVERS=("agentis-memory:6399" "redis:6379" "dragonfly:6379" "lux:6379")
SERVER_NAMES=("agentis_memory" "redis" "dragonfly" "lux")
SCENARIOS=("strings" "hashes" "lists" "sorted-sets" "sets" "mixed-workload")
PIPELINES=(1 10 50 100)

RESULTS_DIR="$SCRIPT_DIR/results"
REPORTS_DIR="$SCRIPT_DIR/reports"
CONTAINER_RESULTS="/tmp/bench_results"

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
log "=== Quick Re-run (servers already up) ==="

# Create results dir inside container
docker compose exec -T memtier mkdir -p "$CONTAINER_RESULTS"

# ─── Warmup ───────────────────────────────────────────────────────────────────
log "Warming up servers..."
for i in "${!SERVERS[@]}"; do
  IFS=: read -r host port <<< "${SERVERS[$i]}"
  log "  Warming up $host:$port..."
  docker compose exec -T memtier memtier_benchmark \
    -s "$host" -p "$port" \
    --protocol=redis --requests=10000 --threads=2 --clients=10 \
    --ratio=1:1 --data-size=64 --hide-histogram > /dev/null 2>&1 || true
  flush_server "$host" "$port"
done

# ─── Scenario benchmarks ──────────────────────────────────────────────────────
log "Running scenario benchmarks..."
for scenario in "${SCENARIOS[@]}"; do
  log "  Scenario: $scenario"
  for i in "${!SERVERS[@]}"; do
    IFS=: read -r host port <<< "${SERVERS[$i]}"
    name="${SERVER_NAMES[$i]}"
    log "    -> $name"

    cfg_args=$(grep -v '^\s*#' "scenarios/${scenario}.cfg" | grep -v '^\s*$' | tr '\n' ' ')
    run_memtier "$host" "$port" "$cfg_args" "${name}_${scenario}"
    flush_server "$host" "$port"
  done
done

# ─── Pipeline benchmarks ──────────────────────────────────────────────────────
log "Running pipeline benchmarks..."
for pipeline in "${PIPELINES[@]}"; do
  log "  Pipeline depth: $pipeline"
  for i in "${!SERVERS[@]}"; do
    IFS=: read -r host port <<< "${SERVERS[$i]}"
    name="${SERVER_NAMES[$i]}"
    log "    -> $name (pipeline=$pipeline)"

    run_memtier "$host" "$port" \
      "--protocol=redis --threads=4 --clients=50 --requests=100000 --ratio=1:10 --data-size=256 --pipeline=$pipeline" \
      "${name}_pipeline_${pipeline}"
    flush_server "$host" "$port"
  done
done

# ─── Extract results from container ──────────────────────────────────────────
log "Extracting results from container..."

# Remove old results on host (may need sudo for root-owned dirs)
rm -rf /tmp/bench_extract 2>/dev/null || true
mkdir -p /tmp/bench_extract

docker cp bench-memtier:"$CONTAINER_RESULTS"/. /tmp/bench_extract/

log "Results saved to /tmp/bench_extract/"
ls -la /tmp/bench_extract/

# Copy to proper structure
for name in "${SERVER_NAMES[@]}"; do
  mkdir -p /tmp/bench_extract/"$name"
  for f in /tmp/bench_extract/${name}_*.json; do
    [ -f "$f" ] || continue
    base=$(basename "$f" | sed "s/^${name}_//")
    cp "$f" /tmp/bench_extract/"$name"/"$base"
  done
done

log "Structured results in /tmp/bench_extract/{agentis_memory,redis,dragonfly,lux}/"

# ─── Visualize ────────────────────────────────────────────────────────────────
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

log "=== Done ==="
