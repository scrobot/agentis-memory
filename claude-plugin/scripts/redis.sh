#!/bin/bash
# Agentis Memory RESP communication helper.
# Tries redis-cli first, falls back to raw /dev/tcp.
#
# Usage:
#   redis.sh <host> <port> <command> [args...]
#
# Examples:
#   redis.sh localhost 6399 PING
#   redis.sh localhost 6399 MEMSAVE "agent:fact" "some text"
#   redis.sh localhost 6399 MEMQUERY "agent" "search query" "5"
#   redis.sh localhost 6399 MEMSTATUS "agent:fact"
#   redis.sh localhost 6399 INFO

set -euo pipefail

HOST="${1:?Usage: redis.sh <host> <port> <command> [args...]}"
PORT="${2:?Usage: redis.sh <host> <port> <command> [args...]}"
shift 2

# Build RESP array from arguments
build_resp() {
    local argc=$#
    local resp="*${argc}\r\n"
    for arg in "$@"; do
        local len=${#arg}
        resp+="\$${len}\r\n${arg}\r\n"
    done
    echo -ne "$resp"
}

# Try redis-cli first (cleaner output, handles all edge cases)
if command -v redis-cli &>/dev/null; then
    redis-cli -h "$HOST" -p "$PORT" --no-auth-warning "$@" 2>&1
    exit $?
fi

# Fallback: raw TCP via /dev/tcp (bash built-in, no external deps)
exec 3<>/dev/tcp/"$HOST"/"$PORT" 2>/dev/null || {
    echo "ERROR: Cannot connect to Agentis Memory at $HOST:$PORT"
    exit 1
}

# Send RESP command
build_resp "$@" >&3

# Read response (simple single-line reader, handles common cases)
read -t 5 -r response <&3 || true
exec 3>&-

# Parse RESP prefix
case "${response:0:1}" in
    +) echo "${response:1}" ;;           # Simple String
    -) echo "ERROR: ${response:1}" ;;    # Error
    :) echo "${response:1}" ;;           # Integer
    \$)                                   # Bulk String
        len="${response:1}"
        if [[ "$len" == "-1" ]]; then
            echo "(nil)"
        else
            # For multi-line responses, re-connect and read properly
            exec 3<>/dev/tcp/"$HOST"/"$PORT"
            build_resp "$@" >&3
            # Skip length line
            read -t 5 -r _ <&3
            # Read data
            read -t 5 -r data <&3
            exec 3>&-
            echo "$data"
        fi
        ;;
    \*)                                   # Array — print raw for now
        echo "$response"
        while read -t 1 -r line <&3 2>/dev/null; do
            echo "$line"
        done
        exec 3>&- 2>/dev/null
        ;;
    *) echo "$response" ;;
esac
