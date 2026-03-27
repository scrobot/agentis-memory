#!/bin/bash
# SessionStart hook: check Agentis Memory connectivity.
# Reads settings from .claude/agentis-memory.local.md if present.

SETTINGS_FILE=".claude/agentis-memory.local.md"
HOST="localhost"
PORT="6399"

# Parse settings if file exists
if [[ -f "$SETTINGS_FILE" ]]; then
    HOST=$(grep -E "^host:" "$SETTINGS_FILE" | head -1 | awk '{print $2}' || echo "localhost")
    PORT=$(grep -E "^port:" "$SETTINGS_FILE" | head -1 | awk '{print $2}' || echo "6399")
    [[ -z "$HOST" ]] && HOST="localhost"
    [[ -z "$PORT" ]] && PORT="6399"
fi

# Quick PING check
if command -v redis-cli &>/dev/null; then
    RESULT=$(redis-cli -h "$HOST" -p "$PORT" PING 2>/dev/null)
else
    # /dev/tcp fallback
    exec 3<>/dev/tcp/"$HOST"/"$PORT" 2>/dev/null || { exit 0; }
    printf '*1\r\n$4\r\nPING\r\n' >&3
    read -t 2 -r RESULT <&3
    exec 3>&- 2>/dev/null
fi

if [[ "$RESULT" == *"PONG"* ]]; then
    # Get key count
    if command -v redis-cli &>/dev/null; then
        DBSIZE=$(redis-cli -h "$HOST" -p "$PORT" DBSIZE 2>/dev/null | grep -o '[0-9]*')
    else
        DBSIZE="?"
    fi
    echo "Agentis Memory: connected ($HOST:$PORT, $DBSIZE keys)"
else
    echo "Agentis Memory: not available at $HOST:$PORT (start with: docker compose up -d)"
fi
