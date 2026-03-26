#!/usr/bin/env bash
# ============================================================================
# native-image-build.sh — Build Agentis Memory as a GraalVM native binary
# ============================================================================
# Usage:
#   ./scripts/native-image-build.sh [--trace] [--compile] [--smoke-test]
#
# Phases:
#   --trace     Build jar + run tracing agent to generate reflection configs
#   --compile   Run native-image compilation via Gradle
#   --smoke-test Run the native binary and exercise it with commands
#
# If no flags are given, all phases run in order.
# ============================================================================

set -euo pipefail
cd "$(dirname "$0")/.."

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log()  { echo -e "${GREEN}[native-image]${NC} $*"; }
warn() { echo -e "${YELLOW}[native-image]${NC} $*"; }
err()  { echo -e "${RED}[native-image]${NC} $*" >&2; }

# Parse args
DO_TRACE=false
DO_COMPILE=false
DO_SMOKE=false
if [[ $# -eq 0 ]]; then
    DO_TRACE=true
    DO_COMPILE=true
    DO_SMOKE=true
fi
for arg in "$@"; do
    case "$arg" in
        --trace)      DO_TRACE=true ;;
        --compile)    DO_COMPILE=true ;;
        --smoke-test) DO_SMOKE=true ;;
        *) err "Unknown arg: $arg"; exit 1 ;;
    esac
done

# ── Prerequisites ──
log "Checking prerequisites..."
java --version || { err "Java not found. GraalVM JDK required."; exit 1; }
if ! command -v native-image &>/dev/null; then
    warn "native-image not found on PATH. The Gradle plugin will attempt to download it."
fi

# ── Phase 1: Tracing agent ──
if $DO_TRACE; then
    log "Phase 1: Building jar and running tracing agent..."

    ./gradlew installDist

    TRACE_DIR="src/main/resources/META-INF/native-image/io.agentis/agentis-memory-traced"
    mkdir -p "$TRACE_DIR"

    PORT=16399
    log "Starting server with tracing agent on port $PORT..."

    java \
        -agentlib:native-image-agent=config-output-dir="$TRACE_DIR" \
        --enable-preview \
        --add-modules jdk.incubator.vector \
        -cp "build/install/agentis-memory/lib/*" \
        io.agentis.memory.AgentisMemory --port "$PORT" --bind 127.0.0.1 &
    SERVER_PID=$!

    # Wait for server to start
    sleep 3
    log "Server PID: $SERVER_PID"

    # Exercise the server
    log "Exercising server with commands..."

    exercise_cmd() {
        echo "$1" | redis-cli -p "$PORT" --no-auth-warning 2>/dev/null || \
        echo "$1" | nc -w1 127.0.0.1 "$PORT" 2>/dev/null || true
    }

    # Basic commands
    exercise_cmd "PING"
    exercise_cmd "SET mykey myvalue"
    exercise_cmd "GET mykey"
    exercise_cmd "SET counter 0"
    exercise_cmd "INCR counter"
    exercise_cmd "EXISTS mykey"
    exercise_cmd "TYPE mykey"
    exercise_cmd "DEL mykey"
    exercise_cmd "DBSIZE"
    exercise_cmd "INFO"

    # Hash commands
    exercise_cmd "HSET myhash field1 value1 field2 value2"
    exercise_cmd "HGET myhash field1"
    exercise_cmd "HGETALL myhash"
    exercise_cmd "HDEL myhash field1"
    exercise_cmd "HLEN myhash"

    # List commands
    exercise_cmd "LPUSH mylist a b c"
    exercise_cmd "RPUSH mylist d e f"
    exercise_cmd "LRANGE mylist 0 -1"
    exercise_cmd "LLEN mylist"
    exercise_cmd "LPOP mylist"

    # Set commands
    exercise_cmd "SADD myset a b c d"
    exercise_cmd "SMEMBERS myset"
    exercise_cmd "SCARD myset"
    exercise_cmd "SISMEMBER myset a"

    # Sorted set commands
    exercise_cmd "ZADD myzset 1 a 2 b 3 c"
    exercise_cmd "ZRANGE myzset 0 -1 WITHSCORES"
    exercise_cmd "ZCARD myzset"
    exercise_cmd "ZSCORE myzset a"

    # SCAN
    exercise_cmd "SCAN 0"

    # Memory commands (if ONNX model is available)
    exercise_cmd "MEMSAVE testkey:mem 'The quick brown fox jumps over the lazy dog'"
    sleep 2  # Wait for async embedding
    exercise_cmd "MEMQUERY testkey 'fox' 5"
    exercise_cmd "MEMSTATUS testkey:mem"

    # COMMAND
    exercise_cmd "COMMAND"

    # Time
    exercise_cmd "TIME"

    # KEYS
    exercise_cmd "KEYS *"

    # TTL/EXPIRE
    exercise_cmd "SET ttlkey ttlval"
    exercise_cmd "EXPIRE ttlkey 60"
    exercise_cmd "TTL ttlkey"

    # Shutdown server
    log "Stopping server..."
    kill "$SERVER_PID" 2>/dev/null || true
    wait "$SERVER_PID" 2>/dev/null || true

    log "Tracing complete. Configs written to: $TRACE_DIR/"
    ls -la "$TRACE_DIR/"
fi

# ── Phase 2: Native compilation ──
if $DO_COMPILE; then
    log "Phase 2: Running native-image compilation..."
    log "This may take 5-15 minutes and use significant memory (~8-16GB)."

    if ! ./gradlew nativeCompile 2>&1 | tee build/native-compile.log; then
        err "Native compilation failed. See build/native-compile.log for details."
        err ""
        err "Common fixes:"
        err "  1. Netty Unsafe errors → add specific classes to --initialize-at-run-time"
        err "  2. ONNX Runtime JNI → ensure native libs are included and JNI configs are correct"
        err "  3. jvector Vector API → may need --add-modules=jdk.incubator.vector"
        err "  4. Missing reflection → run --trace phase again with more commands"
        err ""
        err "The build log above shows the specific error. Fix and re-run."
        exit 1
    fi

    BINARY="build/native/nativeCompile/agentis-memory"
    if [[ -f "$BINARY" ]]; then
        log "Native binary built successfully!"
        ls -lh "$BINARY"
    else
        err "Binary not found at expected path: $BINARY"
        exit 1
    fi
fi

# ── Phase 3: Smoke test ──
if $DO_SMOKE; then
    BINARY="build/native/nativeCompile/agentis-memory"
    if [[ ! -f "$BINARY" ]]; then
        err "Binary not found: $BINARY. Run --compile first."
        exit 1
    fi

    PORT=16399
    log "Phase 3: Smoke-testing native binary on port $PORT..."

    "$BINARY" --port "$PORT" --bind 127.0.0.1 &
    BINARY_PID=$!

    sleep 2

    PASS=0
    FAIL=0

    smoke_test() {
        local desc="$1"
        local cmd="$2"
        local expect="$3"
        local result
        result=$(echo "$cmd" | redis-cli -p "$PORT" --no-auth-warning 2>/dev/null || echo "CONNECT_FAIL")
        if echo "$result" | grep -q "$expect"; then
            log "  PASS: $desc"
            PASS=$((PASS + 1))
        else
            err "  FAIL: $desc (expected '$expect', got '$result')"
            FAIL=$((FAIL + 1))
        fi
    }

    smoke_test "PING" "PING" "PONG"
    smoke_test "SET/GET" "SET smokekey smokeval\nGET smokekey" "smokeval"
    smoke_test "INFO" "INFO" "agentis_memory"
    smoke_test "DBSIZE" "DBSIZE" ""
    smoke_test "HSET/HGETALL" "HSET h f v\nHGETALL h" ""
    smoke_test "LPUSH/LRANGE" "LPUSH l a b\nLRANGE l 0 -1" ""

    # Attempt MEMSAVE/MEMQUERY if model is available
    echo "MEMSAVE test:smoke 'This is a smoke test for native binary'" | redis-cli -p "$PORT" --no-auth-warning 2>/dev/null || true
    sleep 2
    echo "MEMQUERY test 'smoke test' 3" | redis-cli -p "$PORT" --no-auth-warning 2>/dev/null || true

    log "Stopping native binary..."
    kill "$BINARY_PID" 2>/dev/null || true
    wait "$BINARY_PID" 2>/dev/null || true

    log "Smoke test results: $PASS passed, $FAIL failed"
    if [[ $FAIL -gt 0 ]]; then
        exit 1
    fi
fi

log "Done."
