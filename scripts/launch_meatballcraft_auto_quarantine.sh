#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="${PRISM_ROOT:-$HOME/.var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher}"
INSTANCE_ID="${PRISM_INSTANCE_ID:-MeatballCraft, Dimensional Ascension}"
MC="$ROOT/instances/$INSTANCE_ID/minecraft"
LOG="$MC/logs/latest.log"
CONFIG="$MC/config/gpom-early.properties"
ACTIVE_DIR="$MC/config/gpom-parallel-active"
LAST_BREADCRUMB="$MC/config/gpom-parallel-last-threaded.properties"
MAX_ATTEMPTS="${CRO_AUTO_QUARANTINE_ATTEMPTS:-50}"
WAIT_SECONDS="${CRO_AUTO_QUARANTINE_WAIT_SECONDS:-360}"
POLL_SECONDS="${CRO_AUTO_QUARANTINE_POLL_SECONDS:-5}"
IDLE_SECONDS="${CRO_AUTO_QUARANTINE_IDLE_SECONDS:-120}"
KILL_GRACE_SECONDS="${CRO_AUTO_QUARANTINE_KILL_GRACE_SECONDS:-5}"

has_client() {
  [ -n "$(stale_client_pids | head -n 1)" ]
}

stale_client_pids() {
  ps -eo pid=,comm=,args= | awk -v id="$INSTANCE_ID" '
    {
      pid = $1
      comm = $2
      args = $0
      sub(/^[[:space:]]*[0-9]+[[:space:]]+[^[:space:]]+[[:space:]]+/, "", args)
    }
    comm ~ /^(awk|bash|sh|grep|python3|sed|cat|ps|sort|tr|head)$/ { next }
    index(args, "launch_meatballcraft_auto_quarantine.sh") { next }
    index(args, "org.prismlauncher.EntryPoint") { print pid; next }
    index(args, "com.cleanroommc.boot.MainClient") { print pid; next }
    index(args, "net.minecraft.launchwrapper.Launch") { print pid; next }
    index(args, "prismrun --launch " id) { print pid; next }
  '
}

kill_stale_clients() {
  local reason="${1:-unknown}"
  local pids
  pids="$(stale_client_pids | sort -u | tr '\n' ' ')"
  if [ -z "$pids" ]; then
    return 0
  fi

  echo "kill-stale:$reason pids=$pids"
  kill -TERM $pids 2>/dev/null || true
  sleep "$KILL_GRACE_SECONDS"

  local alive=""
  local pid
  for pid in $pids; do
    if kill -0 "$pid" 2>/dev/null; then
      alive="$alive $pid"
    fi
  done
  if [ -n "$alive" ]; then
    echo "kill-stale-force:$reason pids=$alive"
    kill -KILL $alive 2>/dev/null || true
    sleep 1
  fi
}

analyze_and_patch() {
  LOG="$LOG" CONFIG="$CONFIG" ACTIVE_DIR="$ACTIVE_DIR" LAST_BREADCRUMB="$LAST_BREADCRUMB" python3 - <<'PY'
from pathlib import Path
import os
import re
import sys

log = Path(os.environ["LOG"])
config = Path(os.environ["CONFIG"])
active_dir = Path(os.environ["ACTIVE_DIR"])
last_breadcrumb = Path(os.environ["LAST_BREADCRUMB"])

phase_to_key = {
    "FMLConstructionEvent": "fml.parallel.construct.denylist",
    "FMLPreInitializationEvent": "fml.parallel.preInit.denylist",
    "FMLInitializationEvent": "fml.parallel.init.denylist",
    "FMLPostInitializationEvent": "fml.parallel.postInit.denylist",
    "FMLLoadCompleteEvent": "fml.parallel.loadComplete.denylist",
}

ignored = {"", "*", "minecraft", "mcp", "fml", "forge"}

def norm(value):
    return (value or "").strip().lower()

def read_props(path):
    result = {}
    try:
        for line in path.read_text(errors="replace").splitlines():
            if not line or line.lstrip().startswith("#") or "=" not in line:
                continue
            key, value = line.split("=", 1)
            result[key.strip()] = value.strip()
    except FileNotFoundError:
        pass
    return result

def candidate_from_log():
    try:
        lines = log.read_text(errors="replace").splitlines()
    except FileNotFoundError:
        return None

    explicit_thread = None
    thread_re = re.compile(r"\[(GPOM FML [^\]/]+ - ([^\]/]+))/(?:ERROR|FATAL|WARN)\]")
    for line in lines[-300:]:
        match = thread_re.search(line)
        if match:
            explicit_thread = {"modId": norm(match.group(2))}

    start_re = re.compile(r"Starting threaded (FML\w+Event) for ([a-zA-Z0-9_.-]+) ")
    last_start = None
    for line in lines:
        match = start_re.search(line)
        if match:
            last_start = {"phase": match.group(1), "modId": norm(match.group(2))}

    if explicit_thread and last_start:
        explicit_thread.setdefault("phase", last_start.get("phase"))
        return explicit_thread
    return explicit_thread or last_start

def newest_active_candidate():
    candidates = []
    if active_dir.is_dir():
        for path in active_dir.glob("*.properties"):
            props = read_props(path)
            if props.get("modId"):
                candidates.append((path.stat().st_mtime, props))
    if candidates:
        return sorted(candidates, key=lambda item: item[0])[-1][1]
    props = read_props(last_breadcrumb)
    return props if props.get("modId") else None

candidate = newest_active_candidate() or candidate_from_log()
if not candidate:
    print("no-candidate")
    sys.exit(2)

last_props = read_props(last_breadcrumb)
active_props = {}
if active_dir.is_dir():
    for path in active_dir.glob("*.properties"):
        props = read_props(path)
        if norm(props.get("modId")) == norm(candidate.get("modId")):
            active_props = props
            break

merged = dict(last_props)
merged.update(active_props)
merged.update({k: v for k, v in candidate.items() if v})
phase = merged.get("phase")
key = merged.get("denylistKey") or phase_to_key.get(phase)
mod_id = norm(merged.get("modId"))
if not key or mod_id in ignored:
    print(f"bad-candidate phase={phase!r} key={key!r} mod={mod_id!r}")
    sys.exit(3)

mods = []
for value in [mod_id] + [part for part in (merged.get("related") or "").split(",")]:
    value = norm(value)
    if value not in ignored and value not in mods:
        mods.append(value)

lines = config.read_text().splitlines() if config.exists() else []
prefix = key + "="
found = False
changed = False
for index, line in enumerate(lines):
    if not line.startswith(prefix):
        continue
    found = True
    current = []
    for part in line[len(prefix):].split(","):
        part = norm(part)
        if part and part not in current:
            current.append(part)
    for mod in mods:
        if mod not in current:
            current.append(mod)
            changed = True
    lines[index] = prefix + ",".join(current)
    break

if not found:
    if lines and lines[-1]:
        lines.append("")
    lines.append(prefix + ",".join(mods))
    changed = True

config.write_text("\n".join(lines) + "\n")
for path in list(active_dir.glob("*.properties")) if active_dir.is_dir() else []:
    try:
        path.unlink()
    except OSError:
        pass

print(f"candidate phase={phase} key={key} mods={','.join(mods)} changed={changed}")
sys.exit(0 if changed else 4)
PY
}

for attempt in $(seq 1 "$MAX_ATTEMPTS"); do
  echo "auto-quarantine-attempt:$attempt/$MAX_ATTEMPTS"
  kill_stale_clients "before-attempt-$attempt"
  CRO_KILL_STALE=0 "$SCRIPT_DIR/launch_meatballcraft.sh"

  elapsed=0
  idle_elapsed=0
  last_log_size=0
  if [ -e "$LOG" ]; then
    last_log_size="$(wc -c < "$LOG" 2>/dev/null || echo 0)"
  fi
  while [ "$elapsed" -lt "$WAIT_SECONDS" ]; do
    if grep -q 'Startup took' "$LOG" 2>/dev/null; then
      echo "startup-complete-attempt:$attempt"
      exit 0
    fi
    if [ -s "$LOG" ] && ! has_client; then
      echo "client-exited-attempt:$attempt elapsed=${elapsed}s"
      kill_stale_clients "exited-attempt-$attempt"
      break
    fi
    current_log_size=0
    if [ -e "$LOG" ]; then
      current_log_size="$(wc -c < "$LOG" 2>/dev/null || echo 0)"
    fi
    if [ "$current_log_size" -gt "$last_log_size" ]; then
      last_log_size="$current_log_size"
      idle_elapsed=0
    else
      idle_elapsed=$((idle_elapsed + POLL_SECONDS))
    fi
    if [ -s "$LOG" ] && has_client && [ "$idle_elapsed" -ge "$IDLE_SECONDS" ]; then
      echo "client-idle-attempt:$attempt elapsed=${elapsed}s idle=${idle_elapsed}s"
      kill_stale_clients "idle-attempt-$attempt"
      break
    fi
    sleep "$POLL_SECONDS"
    elapsed=$((elapsed + POLL_SECONDS))
  done

  if [ "$elapsed" -ge "$WAIT_SECONDS" ]; then
    echo "timeout-attempt:$attempt elapsed=${elapsed}s"
    kill_stale_clients "timeout-attempt-$attempt"
  fi

  set +e
  patch_output="$(analyze_and_patch 2>&1)"
  patch_status=$?
  set -e
  echo "$patch_output"
  if [ "$patch_status" -eq 0 ] || [ "$patch_status" -eq 4 ]; then
    continue
  fi
  echo "auto-quarantine could not identify a threaded failing mod" >&2
  exit "$patch_status"
done

echo "auto-quarantine exhausted attempts" >&2
kill_stale_clients "exhausted"
exit 1
