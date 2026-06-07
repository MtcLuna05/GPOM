#!/usr/bin/env bash
set -euo pipefail

ROOT="${PRISM_ROOT:-$HOME/.var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher}"
INSTANCE_ID="${PRISM_INSTANCE_ID:-MeatballCraft, Dimensional Ascension}"
LOG="$ROOT/instances/$INSTANCE_ID/minecraft/logs/latest.log"
TMP_DIR="${CRO_TMP_DIR:-/tmp}"
LAUNCH_OUT="$TMP_DIR/cro-prism-launch.out"
LAUNCH_ERR="$TMP_DIR/cro-prism-launch.err"
LAUNCH_PID="$TMP_DIR/cro-prism-launch.pid"

TIMEOUT_SECONDS="${CRO_LOG_TIMEOUT_SECONDS:-45}"

# Killing Prism during its "launching profile" handoff can kill the game before
# the JVM writes latest.log. Keep cleanup opt-in for genuinely stale launches.
if [ "${CRO_KILL_STALE:-0}" = "1" ]; then
  pids="$(ps -eo pid=,comm=,args= | awk -v id="$INSTANCE_ID" '
    {
      pid = $1
      comm = $2
      args = $0
      sub(/^[[:space:]]*[0-9]+[[:space:]]+[^[:space:]]+[[:space:]]+/, "", args)
    }
    comm ~ /^(awk|bash|sh|grep|python3|sed|cat|ps|sort|tr|head)$/ { next }
    index(args, "org.prismlauncher.EntryPoint") { print pid; next }
    index(args, "com.cleanroommc.boot.MainClient") { print pid; next }
    index(args, "net.minecraft.launchwrapper.Launch") { print pid; next }
    index(args, "prismrun --launch " id) { print pid; next }
  ' | sort -u | tr '\n' ' ')"
  if [ -n "$pids" ]; then
    kill -TERM $pids 2>/dev/null || true
    sleep "${CRO_KILL_GRACE_SECONDS:-5}"
    alive=""
    for pid in $pids; do
      if kill -0 "$pid" 2>/dev/null; then
        alive="$alive $pid"
      fi
    done
    if [ -n "$alive" ]; then
      kill -KILL $alive 2>/dev/null || true
      sleep 1
    fi
  fi
fi

: > "$LOG"
launch_args=(--launch "$INSTANCE_ID")
if [ -n "${CRO_OFFLINE_NAME:-}" ]; then
  launch_args+=(--offline "$CRO_OFFLINE_NAME")
fi

nohup setsid flatpak run --command=prismrun org.prismlauncher.PrismLauncher "${launch_args[@]}" </dev/null >"$LAUNCH_OUT" 2>"$LAUNCH_ERR" &
echo $! > "$LAUNCH_PID"

for i in $(seq 1 "$TIMEOUT_SECONDS"); do
  if [ -s "$LOG" ]; then
    echo "log-started:$i"
    exit 0
  fi
  if (( i % 15 == 0 )); then
    echo "waiting:$i"
    ps -eo pid,args | awk '/org\.prismlauncher\.EntryPoint|java.*PrismLauncher|java.*minecraft|prismrun|bwrap --args .*prismlauncher/ && !/awk/ {print $1, substr($0,1,220)}' | tail -10
  fi
  sleep 1
done

echo "no-log-after-${TIMEOUT_SECONDS}s" >&2
printf '%s\n' '--- prism stdout ---' >&2
tail -80 "$LAUNCH_OUT" >&2 2>/dev/null || true
printf '%s\n' '--- prism stderr ---' >&2
tail -120 "$LAUNCH_ERR" >&2 2>/dev/null || true
exit 1
