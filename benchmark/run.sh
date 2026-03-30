#!/bin/bash
set -euo pipefail

# ─── Usage ────────────────────────────────────────────────────────────────────
usage() {
  cat <<'EOF'
Usage: bash run.sh --ssh user@host [OPTIONS]

Run benchmarks on a remote server, poll for progress, download results.

Required:
  --ssh USER@HOST         Remote server to run benchmarks on

Options:
  -s, --scenario SCENARIO   Run only this scenario (repeatable).
                             Values: strings, hashes, lists, sorted-sets, sets, mixed-workload
  -S, --server SERVER       Run only this server (repeatable).
                             Values: agentis, redis, dragonfly, lux
  -p, --pipeline DEPTH      Run only this pipeline depth (repeatable). E.g. 1, 10, 50, 100
      --no-pipeline         Skip pipeline benchmarks entirely
      --no-scenario         Skip scenario benchmarks entirely
      --no-warmup           Skip warmup phase
      --no-report           Skip report generation
      --no-teardown         Keep Docker Compose stack running after benchmarks
      --poll-interval SEC   Polling interval in seconds (default: 30)
  -h, --help                Show this help

Examples:
  bash run.sh --ssh bench@10.0.0.5                          # full run
  bash run.sh --ssh bench@10.0.0.5 -s strings -S agentis -S redis
  bash run.sh --ssh bench@10.0.0.5 --no-scenario -p 100    # only pipeline=100
EOF
  exit 0
}

# ─── Config ───────────────────────────────────────────────────────────────────
ALL_SERVERS=("agentis-memory:6399" "redis:6379" "dragonfly:6379" "lux:6379")
ALL_SERVER_NAMES=("agentis_memory" "redis" "dragonfly" "lux")
ALL_SCENARIOS=("strings" "hashes" "lists" "sorted-sets" "sets" "mixed-workload")
ALL_PIPELINES=(1 10 50 100)

REMOTE_DIR="/tmp/agentis-bench"
REMOTE_RESULTS="/tmp/bench_results"

# ─── Parse args ───────────────────────────────────────────────────────────────
SSH_TARGET=""
FILTER_SCENARIOS=()
FILTER_SERVERS=()
FILTER_PIPELINES=()
RUN_SCENARIOS=true
RUN_PIPELINES=true
RUN_WARMUP=true
RUN_REPORT=true
TEARDOWN=true
POLL_INTERVAL=30

while [[ $# -gt 0 ]]; do
  case "$1" in
    --ssh)            SSH_TARGET="$2"; shift 2 ;;
    -s|--scenario)
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
    --no-teardown)    TEARDOWN=false; shift ;;
    --poll-interval)  POLL_INTERVAL="$2"; shift 2 ;;
    -h|--help)        usage ;;
    *) echo "Unknown option: $1"; usage ;;
  esac
done

if [[ -z "$SSH_TARGET" ]]; then
  echo "ERROR: --ssh user@host is required"
  echo ""
  usage
fi

# Apply defaults if no filters specified
SCENARIOS=("${FILTER_SCENARIOS[@]:-${ALL_SCENARIOS[@]}}")
PIPELINES=("${FILTER_PIPELINES[@]:-${ALL_PIPELINES[@]}}")

if [[ ${#FILTER_SERVERS[@]} -eq 0 ]]; then
  SERVER_INDICES=(0 1 2 3)
else
  SERVER_INDICES=("${FILTER_SERVERS[@]}")
fi

# ─── Helpers ──────────────────────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CALLER_DIR="$(pwd)"
TIMESTAMP="$(date '+%Y%m%d_%H%M%S')"
LOCAL_OUTPUT="$CALLER_DIR/bench_$TIMESTAMP"

log() { echo "[$(date '+%H:%M:%S')] $*"; }

remote() { ssh -o StrictHostKeyChecking=accept-new "$SSH_TARGET" "$@"; }

# ─── Build expected results list ─────────────────────────────────────────────
EXPECTED_FILES=()
if $RUN_SCENARIOS; then
  for scenario in "${SCENARIOS[@]}"; do
    for i in "${SERVER_INDICES[@]}"; do
      EXPECTED_FILES+=("${ALL_SERVER_NAMES[$i]}_${scenario}.json")
    done
  done
fi
if $RUN_PIPELINES; then
  for pipeline in "${PIPELINES[@]}"; do
    for i in "${SERVER_INDICES[@]}"; do
      EXPECTED_FILES+=("${ALL_SERVER_NAMES[$i]}_pipeline_${pipeline}.json")
    done
  done
fi
TOTAL_EXPECTED=${#EXPECTED_FILES[@]}

# ─── Generate worker script ─────────────────────────────────────────────────
generate_worker() {
  cat <<WORKER_EOF
#!/bin/bash
set -uo pipefail

REMOTE_DIR="$REMOTE_DIR"
RESULTS="$REMOTE_RESULTS"
CONTAINER_RESULTS="/tmp/bench_container_results"
TEARDOWN=$TEARDOWN

log() { echo "[\$(date '+%H:%M:%S')] \$*" >> "\$RESULTS/worker.log"; }

flush_server() {
  local host="\$1"; local port="\$2"
  cd "\$REMOTE_DIR" && docker compose exec -T redis redis-cli -h "\$host" -p "\$port" FLUSHALL > /dev/null 2>&1 || true
}

run_bench() {
  local host="\$1"; local port="\$2"; local extra_args="\$3"; local result_name="\$4"
  local container_out="\$CONTAINER_RESULTS/\${result_name}.json"

  log "Running: \$result_name"
  cd "\$REMOTE_DIR" && docker compose exec -T memtier memtier_benchmark \\
    -s "\$host" -p "\$port" \\
    \$extra_args \\
    --json-out-file="\$container_out" \\
    --hide-histogram \\
    >> "\$RESULTS/worker.log" 2>&1 || true

  # Immediately copy result out of container
  docker cp bench-memtier:"\$container_out" "\$RESULTS/\${result_name}.json" 2>/dev/null || true
  log "Done: \$result_name"
}

# ─── Setup ───────────────────────────────────────────────────────────────────
rm -rf "\$RESULTS"
mkdir -p "\$RESULTS"
log "Worker started"

cd "\$REMOTE_DIR"
docker compose exec -T memtier mkdir -p "\$CONTAINER_RESULTS"

WORKER_EOF

  # Warmup
  if $RUN_WARMUP; then
    cat <<WARMUP_BLOCK
log "Warming up servers..."
WARMUP_BLOCK
    for i in "${SERVER_INDICES[@]}"; do
      local host port
      IFS=: read -r host port <<< "${ALL_SERVERS[$i]}"
      cat <<WARMUP_ITEM
log "  Warmup: ${ALL_SERVER_NAMES[$i]}"
cd "\$REMOTE_DIR" && docker compose exec -T memtier memtier_benchmark \\
  -s $host -p $port \\
  --protocol=redis --requests=10000 --threads=2 --clients=10 \\
  --ratio=1:1 --data-size=64 --hide-histogram > /dev/null 2>&1 || true
flush_server "$host" "$port"
WARMUP_ITEM
    done
  fi

  # Scenarios
  if $RUN_SCENARIOS; then
    for scenario in "${SCENARIOS[@]}"; do
      local cfg_file="$SCRIPT_DIR/scenarios/${scenario}.cfg"
      if [[ ! -f "$cfg_file" ]]; then continue; fi
      local cfg_args
      cfg_args=$(grep -v '^\s*#' "$cfg_file" | grep -v '^\s*$' | tr '\n' ' ')

      for i in "${SERVER_INDICES[@]}"; do
        local host port
        IFS=: read -r host port <<< "${ALL_SERVERS[$i]}"
        local name="${ALL_SERVER_NAMES[$i]}"
        cat <<SCENARIO_ITEM
run_bench "$host" "$port" "$cfg_args" "${name}_${scenario}"
flush_server "$host" "$port"
SCENARIO_ITEM
      done
    done
  fi

  # Pipelines
  if $RUN_PIPELINES; then
    for pipeline in "${PIPELINES[@]}"; do
      for i in "${SERVER_INDICES[@]}"; do
        local host port
        IFS=: read -r host port <<< "${ALL_SERVERS[$i]}"
        local name="${ALL_SERVER_NAMES[$i]}"
        cat <<PIPELINE_ITEM
run_bench "$host" "$port" "--protocol=redis --threads=4 --clients=50 --requests=100000 --ratio=1:10 --data-size=256 --pipeline=$pipeline" "${name}_pipeline_${pipeline}"
flush_server "$host" "$port"
PIPELINE_ITEM
      done
    done
  fi

  # Teardown + DONE marker
  cat <<FINISH_BLOCK

if \$TEARDOWN; then
  log "Tearing down Docker stack..."
  cd "\$REMOTE_DIR" && docker compose down || true
fi

log "Worker finished"
touch "\$RESULTS/DONE"
FINISH_BLOCK
}

# ─── Upload and launch ──────────────────────────────────────────────────────
log "=== Agentis Benchmark ==="
log "Remote: $SSH_TARGET"
log "Scenarios: ${SCENARIOS[*]}"
log "Servers: $(for i in "${SERVER_INDICES[@]}"; do echo -n "${ALL_SERVER_NAMES[$i]} "; done)"
$RUN_PIPELINES && log "Pipelines: ${PIPELINES[*]}" || log "Pipelines: skipped"
log "Expected results: $TOTAL_EXPECTED"

log "Uploading benchmark files to $SSH_TARGET:$REMOTE_DIR..."
remote "rm -rf $REMOTE_DIR && mkdir -p $REMOTE_DIR"
rsync -az --delete \
  "$SCRIPT_DIR/docker-compose.yml" \
  "$SCRIPT_DIR/scenarios" \
  "$SCRIPT_DIR/lux" \
  "$SCRIPT_DIR/visualize" \
  "$SSH_TARGET:$REMOTE_DIR/"

# Generate and upload worker script
WORKER_SCRIPT=$(generate_worker)
echo "$WORKER_SCRIPT" | remote "cat > $REMOTE_DIR/worker.sh && chmod +x $REMOTE_DIR/worker.sh"

log "Starting Docker Compose stack on remote..."
remote "cd $REMOTE_DIR && docker compose up -d"

log "Waiting for all services to become healthy..."
for attempt in {1..60}; do
  if remote "cd $REMOTE_DIR && \
    docker compose exec -T redis redis-cli -h agentis-memory -p 6399 PING >/dev/null 2>&1 && \
    docker compose exec -T redis redis-cli -h redis -p 6379 PING >/dev/null 2>&1 && \
    docker compose exec -T redis redis-cli -h dragonfly -p 6379 PING >/dev/null 2>&1 && \
    docker compose exec -T redis redis-cli -h lux -p 6379 PING >/dev/null 2>&1"; then
    log "All servers are up."
    break
  fi
  if [ "$attempt" -eq 60 ]; then
    log "ERROR: Timed out waiting for servers."
    remote "cd $REMOTE_DIR && docker compose logs"
    exit 1
  fi
  sleep 5
done

log "Launching benchmark worker (detached)..."
remote "nohup bash $REMOTE_DIR/worker.sh > /dev/null 2>&1 &"
log "Worker launched. Polling every ${POLL_INTERVAL}s for results..."

# ─── Poll for results ───────────────────────────────────────────────────────
prev_completed=0
while true; do
  sleep "$POLL_INTERVAL"

  # Count completed result files on remote
  completed_files=$(remote "ls $REMOTE_RESULTS/*.json 2>/dev/null | wc -l" 2>/dev/null || echo "0")
  completed_files=$(echo "$completed_files" | tr -d '[:space:]')

  # Check for DONE marker
  is_done=$(remote "test -f $REMOTE_RESULTS/DONE && echo yes || echo no" 2>/dev/null || echo "no")

  if [[ "$completed_files" != "$prev_completed" ]]; then
    # Show which new files appeared
    log "Progress: $completed_files/$TOTAL_EXPECTED results"
    prev_completed="$completed_files"
  fi

  if [[ "$is_done" == "yes" ]]; then
    log "Worker finished. All benchmarks complete."
    break
  fi

  # Check if worker is still alive
  worker_alive=$(remote "pgrep -f 'worker.sh' >/dev/null 2>&1 && echo yes || echo no" 2>/dev/null || echo "unknown")
  if [[ "$worker_alive" == "no" && "$is_done" != "yes" ]]; then
    log "WARNING: Worker process died before completing. Downloading partial results."
    break
  fi
done

# ─── Download results ───────────────────────────────────────────────────────
log "Downloading results to $LOCAL_OUTPUT..."
mkdir -p "$LOCAL_OUTPUT"

# Structure results into subdirectories on remote before downloading
remote "$(cat <<'STRUCT_SCRIPT'
cd /tmp/bench_results
for name in agentis_memory redis dragonfly lux; do
  mkdir -p "$name"
  for f in ${name}_*.json; do
    [ -f "$f" ] || continue
    base=$(echo "$f" | sed "s/^${name}_//")
    cp "$f" "$name/$base"
  done
done
STRUCT_SCRIPT
)"

rsync -az "$SSH_TARGET:$REMOTE_RESULTS/" "$LOCAL_OUTPUT/"

# ─── Generate report locally ─────────────────────────────────────────────────
if $RUN_REPORT; then
  bash "$SCRIPT_DIR/report.sh" "$LOCAL_OUTPUT" || {
    log "WARNING: Report generation failed. Re-run manually:"
    log "  bash benchmark/report.sh $LOCAL_OUTPUT"
  }
fi

# ─── Done ────────────────────────────────────────────────────────────────────
log "=== Done ==="
log "Results: $LOCAL_OUTPUT"
if [[ -f "$LOCAL_OUTPUT/report/report.html" ]]; then
  log "Report:  $LOCAL_OUTPUT/report/report.html"
fi
