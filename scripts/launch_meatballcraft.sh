#!/usr/bin/env bash
set -euo pipefail

ROOT="/home/luna/.var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher"
INSTANCE_ID="MeatballCraft, Dimensional Ascension"
LOG="$ROOT/instances/$INSTANCE_ID/minecraft/logs/latest.log"

TIMEOUT_SECONDS="${CRO_LOG_TIMEOUT_SECONDS:-45}"

# Killing Prism during its "launching profile" handoff can kill the game before
# the JVM writes latest.log. Keep cleanup opt-in for genuinely stale launches.
if [ "${CRO_KILL_STALE:-0}" = "1" ]; then
  ps -eo pid,args | awk '/org\.prismlauncher\.EntryPoint|java.*minecraft|prismrun/ && !/awk/ {print $1}' | xargs -r kill 2>/dev/null || true
  sleep 1
fi

: > "$LOG"
flatpak run --command=prismrun org.prismlauncher.PrismLauncher --launch "$INSTANCE_ID" >/tmp/cro-prism-launch.out 2>/tmp/cro-prism-launch.err &
echo $! > /tmp/cro-prism-launch.pid

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
tail -80 /tmp/cro-prism-launch.out >&2 2>/dev/null || true
printf '%s\n' '--- prism stderr ---' >&2
tail -120 /tmp/cro-prism-launch.err >&2 2>/dev/null || true
exit 1
