#!/bin/bash
set -euo pipefail

# ─── Usage ────────────────────────────────────────────────────────────────────
usage() {
  cat <<'EOF'
Usage: bash run.sh --ssh user@host [OPTIONS]

Run benchmarks on a remote server via SSH, then download results locally.

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
CONTAINER_RESULTS="/tmp/bench_results"

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

# ─── Upload benchmark files to remote ────────────────────────────────────────
log "=== Agentis Benchmark ==="
log "Remote: $SSH_TARGET"
log "Scenarios: ${SCENARIOS[*]}"
log "Servers: $(for i in "${SERVER_INDICES[@]}"; do echo -n "${ALL_SERVER_NAMES[$i]} "; done)"
$RUN_PIPELINES && log "Pipelines: ${PIPELINES[*]}" || log "Pipelines: skipped"

log "Uploading benchmark files to $SSH_TARGET:$REMOTE_DIR..."
remote "rm -rf $REMOTE_DIR && mkdir -p $REMOTE_DIR"
rsync -az --delete \
  "$SCRIPT_DIR/docker-compose.yml" \
  "$SCRIPT_DIR/scenarios" \
  "$SCRIPT_DIR/lux" \
  "$SCRIPT_DIR/visualize" \
  "$SSH_TARGET:$REMOTE_DIR/"

# ─── Start stack on remote ───────────────────────────────────────────────────
log "Starting Docker Compose stack on remote..."
remote "cd $REMOTE_DIR && docker compose up -d"

log "Waiting for all services to become healthy..."
for i in {1..60}; do
  if remote "cd $REMOTE_DIR && \
    docker compose exec -T redis redis-cli -h agentis-memory -p 6399 PING >/dev/null 2>&1 && \
    docker compose exec -T redis redis-cli -h redis -p 6379 PING >/dev/null 2>&1 && \
    docker compose exec -T redis redis-cli -h dragonfly -p 6379 PING >/dev/null 2>&1 && \
    docker compose exec -T redis redis-cli -h lux -p 6379 PING >/dev/null 2>&1"; then
    log "All servers are up."
    break
  fi
  if [ "$i" -eq 60 ]; then
    log "ERROR: Timed out waiting for servers."
    remote "cd $REMOTE_DIR && docker compose logs"
    exit 1
  fi
  sleep 5
done

# Create results directory inside memtier container
remote "cd $REMOTE_DIR && docker compose exec -T memtier mkdir -p $CONTAINER_RESULTS"

# ─── Helper functions (run on remote) ────────────────────────────────────────
run_memtier_remote() {
  local host="$1"; local port="$2"; local extra_args="$3"; local result_name="$4"
  local container_out="$CONTAINER_RESULTS/${result_name}.json"

  remote "cd $REMOTE_DIR && docker compose exec -T memtier memtier_benchmark \
    -s $host -p $port \
    $extra_args \
    --json-out-file=$container_out \
    --hide-histogram \
    2>&1 | tail -12" || true
}

flush_remote() {
  local host="$1"; local port="$2"
  remote "cd $REMOTE_DIR && docker compose exec -T redis redis-cli -h $host -p $port FLUSHALL" > /dev/null 2>&1 || true
}

# ─── Warmup ───────────────────────────────────────────────────────────────────
if $RUN_WARMUP; then
  log "Warming up servers..."
  for i in "${SERVER_INDICES[@]}"; do
    IFS=: read -r host port <<< "${ALL_SERVERS[$i]}"
    log "  Warming up ${ALL_SERVER_NAMES[$i]}..."
    remote "cd $REMOTE_DIR && docker compose exec -T memtier memtier_benchmark \
      -s $host -p $port \
      --protocol=redis --requests=10000 --threads=2 --clients=10 \
      --ratio=1:1 --data-size=64 --hide-histogram" > /dev/null 2>&1 || true
    flush_remote "$host" "$port"
  done
fi

# ─── Scenario benchmarks ──────────────────────────────────────────────────────
if $RUN_SCENARIOS; then
  log "Running scenario benchmarks..."
  for scenario in "${SCENARIOS[@]}"; do
    cfg_file="scenarios/${scenario}.cfg"
    # Read config locally (it was uploaded via rsync)
    if [[ ! -f "$SCRIPT_DIR/$cfg_file" ]]; then
      log "  WARNING: $cfg_file not found, skipping"
      continue
    fi
    log "  Scenario: $scenario"
    cfg_args=$(grep -v '^\s*#' "$SCRIPT_DIR/$cfg_file" | grep -v '^\s*$' | tr '\n' ' ')

    for i in "${SERVER_INDICES[@]}"; do
      IFS=: read -r host port <<< "${ALL_SERVERS[$i]}"
      name="${ALL_SERVER_NAMES[$i]}"
      log "    -> $name"

      run_memtier_remote "$host" "$port" "$cfg_args" "${name}_${scenario}"
      flush_remote "$host" "$port"
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

      run_memtier_remote "$host" "$port" \
        "--protocol=redis --threads=4 --clients=50 --requests=100000 --ratio=1:10 --data-size=256 --pipeline=$pipeline" \
        "${name}_pipeline_${pipeline}"
      flush_remote "$host" "$port"
    done
  done
fi

# ─── Extract results from remote container ───────────────────────────────────
log "Extracting results from remote..."
remote "rm -rf /tmp/bench_extract && mkdir -p /tmp/bench_extract && \
  cd $REMOTE_DIR && docker cp bench-memtier:$CONTAINER_RESULTS/. /tmp/bench_extract/"

# Structure into subdirectories on remote
remote "$(cat <<'REMOTE_SCRIPT'
cd /tmp/bench_extract
for name in agentis_memory redis dragonfly lux; do
  mkdir -p "$name"
  for f in ${name}_*.json; do
    [ -f "$f" ] || continue
    base=$(echo "$f" | sed "s/^${name}_//")
    mv "$f" "$name/$base"
  done
done
REMOTE_SCRIPT
)"

# ─── Generate report on remote ──────────────────────────────────────────────
if $RUN_REPORT; then
  log "Generating report on remote..."
  remote "cd $REMOTE_DIR && \
    pip3 install -q -r visualize/requirements.txt 2>/dev/null && \
    python3 visualize/generate_report.py /tmp/bench_extract /tmp/bench_extract/report" || {
    log "WARNING: Report generation failed. Results will still be downloaded."
  }
fi

# ─── Teardown remote stack ───────────────────────────────────────────────────
if $TEARDOWN; then
  log "Tearing down remote Docker stack..."
  remote "cd $REMOTE_DIR && docker compose down" || true
fi

# ─── Download results to local machine ───────────────────────────────────────
log "Downloading results to $LOCAL_OUTPUT..."
mkdir -p "$LOCAL_OUTPUT"
rsync -az "$SSH_TARGET:/tmp/bench_extract/" "$LOCAL_OUTPUT/"

# ─── Done ────────────────────────────────────────────────────────────────────
log "=== Done ==="
log "Results: $LOCAL_OUTPUT"
if [[ -f "$LOCAL_OUTPUT/report/report.html" ]]; then
  log "Report:  $LOCAL_OUTPUT/report/report.html"
fi
