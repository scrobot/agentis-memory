#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# ─── Config ───────────────────────────────────────────────────────────────────
SERVERS=("agentis-memory:6399" "redis:6379" "dragonfly:6379" "lux:6379")
SERVER_NAMES=("agentis_memory" "redis" "dragonfly" "lux")
SCENARIOS=("strings" "hashes" "lists" "sorted-sets" "sets" "mixed-workload")
PIPELINES=(1 10 50 100)

RESULTS_DIR="results"
REPORTS_DIR="reports"

# ─── Helpers ──────────────────────────────────────────────────────────────────
log() { echo "[$(date '+%H:%M:%S')] $*"; }

run_memtier() {
  local host="$1"; local port="$2"; local extra_args="$3"
  local local_out="$4"  # path on host
  local container_out="/tmp/memtier_result.json"

  docker compose exec -T memtier memtier_benchmark \
    -s "$host" -p "$port" \
    $extra_args \
    --json-out-file="$container_out" \
    --hide-histogram \
    2>&1 | tail -12 || true

  # Copy result from container to host
  docker cp bench-memtier:"$container_out" "$local_out" 2>/dev/null || true
}

flush_server() {
  local host="$1"; local port="$2"
  docker compose exec -T redis redis-cli -h "$host" -p "$port" FLUSHALL > /dev/null 2>&1 || true
}

ping_server() {
  local host="$1"; local port="$2"
  docker compose exec -T redis redis-cli -h "$host" -p "$port" PING > /dev/null 2>&1
}

# ─── Preflight ────────────────────────────────────────────────────────────────
log "=== Agentis Memory Benchmark Suite ==="
log "Targets: ${SERVERS[*]}"

# Create result dirs
for name in "${SERVER_NAMES[@]}"; do
  mkdir -p "$RESULTS_DIR/$name"
done
mkdir -p "$REPORTS_DIR"

# ─── Start stack ──────────────────────────────────────────────────────────────
log "Starting Docker Compose stack..."
docker compose up -d --build

log "Waiting for all services to become healthy..."
docker compose ps

# Wait explicitly (healthchecks may not be enough for slow builds)
for i in {1..60}; do
  if ping_server agentis-memory 6399 && \
     ping_server redis        6379 && \
     ping_server dragonfly    6379 && \
     ping_server lux          6379; then
    log "All servers are up."
    break
  fi
  if [ $i -eq 60 ]; then
    log "ERROR: Timed out waiting for servers."
    docker compose logs
    exit 1
  fi
  sleep 5
done

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
    local_out="$RESULTS_DIR/${name}/${scenario}.json"
    log "    -> $name"

    # Read scenario cfg and pass as args (strip comments/empty lines)
    cfg_args=$(grep -v '^\s*#' "scenarios/${scenario}.cfg" | grep -v '^\s*$' | tr '\n' ' ')

    run_memtier "$host" "$port" "$cfg_args" "$local_out"
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
    local_out="$RESULTS_DIR/${name}/pipeline_${pipeline}.json"
    log "    -> $name"

    run_memtier "$host" "$port" \
      "--protocol=redis --threads=4 --clients=50 --requests=100000 --ratio=1:10 --data-size=256 --pipeline=$pipeline" \
      "$local_out"
    flush_server "$host" "$port"
  done
done

# ─── Visualize ────────────────────────────────────────────────────────────────
log "=== Generating report ==="
if command -v python3 &>/dev/null; then
  cd visualize
  pip3 install -q -r requirements.txt
  python3 generate_report.py "../$RESULTS_DIR" "../$REPORTS_DIR"
  cd ..
  log "Report: $REPORTS_DIR/report.html"
else
  log "python3 not found — skipping visualization. Run manually:"
  log "  cd benchmark/visualize && pip3 install -r requirements.txt && python3 generate_report.py ../results/ ../reports/"
fi

log "=== Done ==="
