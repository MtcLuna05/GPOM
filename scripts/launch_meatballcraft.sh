#!/usr/bin/env bash
set -euo pipefail

ROOT="/home/luna/.var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher"
INSTANCE_ID="MeatballCraft, Dimensional Ascension"
LOG="$ROOT/instances/$INSTANCE_ID/minecraft/logs/latest.log"

# Prism is configured to remain open after launch. Kill stale launcher/game handoff
# processes so --launch cannot silently route to an old GUI instance.
ps -eo pid,args | awk '/org\.prismlauncher\.EntryPoint|java.*PrismLauncher|java.*minecraft|bwrap --args .*prismlauncher|prismrun/ && !/awk/ {print $1}' | xargs -r kill 2>/dev/null || true
sleep 1

: > "$LOG"
flatpak run --command=prismrun org.prismlauncher.PrismLauncher --launch "$INSTANCE_ID" >/tmp/cro-prism-launch.out 2>/tmp/cro-prism-launch.err &
echo $! > /tmp/cro-prism-launch.pid

for i in $(seq 1 140); do
  if [ -s "$LOG" ]; then
    echo "log-started:$i"
    exit 0
  fi
  if (( i % 20 == 0 )); then
    echo "waiting:$i"
    ps -eo pid,args | awk '/org\.prismlauncher\.EntryPoint|java.*PrismLauncher|java.*minecraft|prismrun|bwrap --args .*prismlauncher/ && !/awk/ {print $1, substr($0,1,220)}' | tail -10
  fi
  sleep 1
done

echo "no-log-after-140s" >&2
printf '%s\n' '--- prism stdout ---' >&2
tail -80 /tmp/cro-prism-launch.out >&2 2>/dev/null || true
printf '%s\n' '--- prism stderr ---' >&2
tail -120 /tmp/cro-prism-launch.err >&2 2>/dev/null || true
exit 1
