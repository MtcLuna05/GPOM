#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="${PRISM_ROOT:-$HOME/.var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher}"
INSTANCE_ID="${PRISM_INSTANCE_ID:-MeatballCraft, Dimensional Ascension}"
MC="$ROOT/instances/$INSTANCE_ID/minecraft"
LOG="$MC/logs/latest.log"
TMP_DIR="${CRO_TMP_DIR:-/tmp}"
LAUNCH_OUT="$TMP_DIR/cro-prism-launch.out"
LAUNCH_ERR="$TMP_DIR/cro-prism-launch.err"
PRISM_GUI_LOG="$TMP_DIR/cro-prism-gui-log.txt"
CONFIG="$MC/config/gpom-early.properties"
ACTIVE_DIR="$MC/config/gpom-parallel-active"
LAST_BREADCRUMB="$MC/config/gpom-parallel-last-threaded.properties"
CRASH_REPORTS="$MC/crash-reports"
MAX_ATTEMPTS="${CRO_AUTO_QUARANTINE_ATTEMPTS:-50}"
WAIT_SECONDS="${CRO_AUTO_QUARANTINE_WAIT_SECONDS:-360}"
POLL_SECONDS="${CRO_AUTO_QUARANTINE_POLL_SECONDS:-5}"
IDLE_SECONDS="${CRO_AUTO_QUARANTINE_IDLE_SECONDS:-120}"
KILL_GRACE_SECONDS="${CRO_AUTO_QUARANTINE_KILL_GRACE_SECONDS:-5}"
PATCH_ON_TIMEOUT="${CRO_AUTO_QUARANTINE_PATCH_ON_TIMEOUT:-0}"
READ_PRISM_GUI_LOG="${CRO_AUTO_QUARANTINE_READ_PRISM_GUI_LOG:-1}"
PRISM_GUI_LOG_CHARS="${CRO_PRISM_GUI_LOG_CHARS:-262144}"
PRISM_GUI_LOG_START_OFFSET=0

has_client() {
  [ -n "$(stale_client_pids | head -n 1)" ]
}

prism_gui_log_count() {
  if [ "$READ_PRISM_GUI_LOG" != "1" ]; then
    return 1
  fi
  python3 "$SCRIPT_DIR/read_prism_gui_log_tail.py" --count 2>/dev/null
}

capture_prism_gui_log_tail() {
  if [ "$READ_PRISM_GUI_LOG" != "1" ]; then
    return 1
  fi
  local tmp="$PRISM_GUI_LOG.tmp"
  if python3 "$SCRIPT_DIR/read_prism_gui_log_tail.py" \
      --start "$PRISM_GUI_LOG_START_OFFSET" \
      --max-chars "$PRISM_GUI_LOG_CHARS" > "$tmp" 2>/dev/null; then
    mv "$tmp" "$PRISM_GUI_LOG"
    return 0
  fi
  rm -f "$tmp"
  return 1
}

native_gl_fatal_seen() {
  capture_prism_gui_log_tail || true
  grep -Eiq \
    'FATAL ERROR in native method: Thread\[#.*GPOM FML [^]]+ - [^],]+,[^]]*\]: No context is current or a function that is not available in the current context was called\. The JVM will abort execution\.' \
    "$LOG" "$LAUNCH_OUT" "$LAUNCH_ERR" "$PRISM_GUI_LOG" 2>/dev/null
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
  local stop_reason="${1:-unknown}"
  local attempt_started_at="${2:-0}"
  LOG="$LOG" \
  CONFIG="$CONFIG" \
  ACTIVE_DIR="$ACTIVE_DIR" \
  LAST_BREADCRUMB="$LAST_BREADCRUMB" \
  CRASH_REPORTS="$CRASH_REPORTS" \
  LAUNCH_OUT="$LAUNCH_OUT" \
  LAUNCH_ERR="$LAUNCH_ERR" \
  PRISM_GUI_LOG="$PRISM_GUI_LOG" \
  STOP_REASON="$stop_reason" \
  ATTEMPT_STARTED_AT="$attempt_started_at" \
  PATCH_ON_TIMEOUT="$PATCH_ON_TIMEOUT" \
  python3 - <<'PY'
from pathlib import Path
import os
import re
import sys

log = Path(os.environ["LOG"])
config = Path(os.environ["CONFIG"])
active_dir = Path(os.environ["ACTIVE_DIR"])
last_breadcrumb = Path(os.environ["LAST_BREADCRUMB"])
crash_reports = Path(os.environ["CRASH_REPORTS"])
launch_out = Path(os.environ["LAUNCH_OUT"])
launch_err = Path(os.environ["LAUNCH_ERR"])
prism_gui_log = Path(os.environ["PRISM_GUI_LOG"])
stop_reason = os.environ.get("STOP_REASON", "unknown")
patch_on_timeout = os.environ.get("PATCH_ON_TIMEOUT", "0").lower() in {"1", "true", "yes", "on"}
try:
    attempt_started_at = float(os.environ.get("ATTEMPT_STARTED_AT", "0"))
except ValueError:
    attempt_started_at = 0.0

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


def key_to_phase(key):
    for phase, phase_key in phase_to_key.items():
        if key == phase_key:
            return phase
    return None


def phase_from_thread_label(label):
    for needle, phase in (
        ("LoadComplete", "FMLLoadCompleteEvent"),
        ("PostInitialization", "FMLPostInitializationEvent"),
        ("PreInitialization", "FMLPreInitializationEvent"),
        ("Initialization", "FMLInitializationEvent"),
        ("Construction", "FMLConstructionEvent"),
    ):
        if needle in label:
            return phase
    return None


def phase_from_text(text):
    for phase in (
        "FMLLoadCompleteEvent",
        "FMLPostInitializationEvent",
        "FMLPreInitializationEvent",
        "FMLInitializationEvent",
        "FMLConstructionEvent",
    ):
        if phase in text:
            return phase
    for marker, phase in (
        ("loadComplete(", "FMLLoadCompleteEvent"),
        (".postInit(", "FMLPostInitializationEvent"),
        (" postInit(", "FMLPostInitializationEvent"),
        (".preInit(", "FMLPreInitializationEvent"),
        (" preInit(", "FMLPreInitializationEvent"),
        (".init(", "FMLInitializationEvent"),
        (" init(", "FMLInitializationEvent"),
    ):
        if marker in text:
            return phase
    return None


def has_threaded_failure_evidence(text):
    return (
        "FmlParallelLoadingScheduler$DispatchTask" in text
        or re.search(r"\[GPOM FML [^\]]+/(?:ERROR|FATAL|WARN)\]", text) is not None
        or "Threaded OpenGL failure in " in text
        or "Auto-quarantined threaded OpenGL failure in " in text
    )


def native_gl_fatal_candidate(text, source):
    native = re.search(
        r"FATAL ERROR in native method: Thread\[#\d+,GPOM FML ([^,\]]+?) - ([^,\]]+),[^\]]*\]: "
        r"No context is current or a function that is not available in the current context was called\. "
        r"The JVM will abort execution\.",
        text,
    )
    if not native:
        return None
    return {
        "modId": norm(native.group(2)),
        "phase": phase_from_thread_label("GPOM FML " + native.group(1)),
        "source": source + ":native-gl-fatal",
    }


def candidate_from_text(text, source):
    native = native_gl_fatal_candidate(text, source)
    if native:
        return native

    auto = re.search(
        r"Auto-quarantined threaded OpenGL failure in ([^\s(]+).*? to (fml\.parallel\.[a-zA-Z]+\.denylist)",
        text,
        re.S,
    )
    if auto:
        key = auto.group(2)
        return {
            "modId": norm(auto.group(1)),
            "key": key,
            "phase": key_to_phase(key),
            "source": source + ":auto-gl",
        }

    manual = re.search(
        r"Threaded OpenGL failure in ([^\s(]+).*? during (FML\w+Event)",
        text,
        re.S,
    )
    if manual:
        return {
            "modId": norm(manual.group(1)),
            "phase": manual.group(2),
            "source": source + ":manual-gl",
        }

    crashed_mod = re.search(r"LoaderExceptionModCrash: Caught exception from .+ \(([A-Za-z0-9_.|:-]+)\)", text)
    if crashed_mod and has_threaded_failure_evidence(text):
        return {
            "modId": norm(crashed_mod.group(1)),
            "phase": phase_from_text(text),
            "source": source + ":threaded-mod-crash",
        }

    explicit_thread = None
    thread_re = re.compile(r"\[(GPOM FML [^\]/]+ - ([^\]/]+))/(?:ERROR|FATAL|WARN)\]")
    for line in text.splitlines()[-500:]:
        match = thread_re.search(line)
        if match:
            explicit_thread = {
                "modId": norm(match.group(2)),
                "phase": phase_from_thread_label(match.group(1)),
                "source": source + ":thread-log",
            }
    return explicit_thread


def candidate_from_log():
    try:
        text = log.read_text(errors="replace")
    except FileNotFoundError:
        return None
    return candidate_from_text(text, "latest.log")


def candidate_from_process_output():
    for path, source in (
        (prism_gui_log, "prism-gui-log"),
        (launch_err, "prism-stderr"),
        (launch_out, "prism-stdout"),
        (log, "latest.log"),
    ):
        try:
            text = path.read_text(errors="replace")
        except FileNotFoundError:
            continue
        candidate = candidate_from_text(text, source)
        if candidate:
            return candidate
    return None


def candidate_from_crash_reports():
    if not crash_reports.is_dir():
        return None
    candidates = []
    for path in crash_reports.glob("crash-*.txt"):
        try:
            mtime = path.stat().st_mtime
        except OSError:
            continue
        if attempt_started_at > 0 and mtime < attempt_started_at - 2:
            continue
        candidates.append((mtime, path))
    for _, path in sorted(candidates, reverse=True):
        try:
            text = path.read_text(errors="replace")
        except OSError:
            continue
        candidate = candidate_from_text(text, "crash-report:" + path.name)
        if candidate:
            return candidate
    return None


def active_props_for_mod(mod_id):
    wanted = norm(mod_id)
    if active_dir.is_dir():
        for path in active_dir.glob("*.properties"):
            props = read_props(path)
            if norm(props.get("modId")) == wanted:
                return props
    props = read_props(last_breadcrumb)
    return props if norm(props.get("modId")) == wanted else {}


if stop_reason == "timeout" and not patch_on_timeout:
    print("timeout-no-quarantine")
    sys.exit(5)

candidate = candidate_from_crash_reports() or candidate_from_process_output() or candidate_from_log()
if not candidate:
    print("no-candidate")
    sys.exit(2)

config_props = read_props(config)
active_props = active_props_for_mod(candidate.get("modId"))
merged = dict(active_props)
merged.update({k: v for k, v in candidate.items() if v})
phase = merged.get("phase")
key = merged.get("key") or merged.get("denylistKey") or phase_to_key.get(phase)
mod_id = norm(merged.get("modId"))
if not key or mod_id in ignored:
    print(f"bad-candidate source={merged.get('source')!r} phase={phase!r} key={key!r} mod={mod_id!r}")
    sys.exit(3)

include_related = config_props.get("fml.parallel.autoQuarantineGlErrors.includeRelatedMods", "false").lower() in {"1", "true", "yes", "on"}
related_values = [part for part in (merged.get("related") or "").split(",")] if include_related else []
mods = []
for value in [mod_id] + related_values:
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
print(f"candidate source={merged.get('source')} phase={phase} key={key} mods={','.join(mods)} changed={changed}")
sys.exit(0 if changed else 4)
PY
}

for attempt in $(seq 1 "$MAX_ATTEMPTS"); do
  echo "auto-quarantine-attempt:$attempt/$MAX_ATTEMPTS"
  kill_stale_clients "before-attempt-$attempt"
  : > "$PRISM_GUI_LOG"
  PRISM_GUI_LOG_START_OFFSET="$(prism_gui_log_count || echo 0)"
  attempt_started_at="$(date +%s)"
  CRO_KILL_STALE=0 "$SCRIPT_DIR/launch_meatballcraft.sh"

  elapsed=0
  idle_elapsed=0
  stop_reason=""
  last_log_size=0
  if [ -e "$LOG" ]; then
    last_log_size="$(wc -c < "$LOG" 2>/dev/null || echo 0)"
  fi
  while [ "$elapsed" -lt "$WAIT_SECONDS" ]; do
    if grep -q 'Startup took' "$LOG" 2>/dev/null; then
      echo "startup-complete-attempt:$attempt"
      exit 0
    fi
    if native_gl_fatal_seen; then
      echo "native-gl-fatal-attempt:$attempt elapsed=${elapsed}s"
      kill_stale_clients "native-gl-fatal-attempt-$attempt"
      stop_reason="native-gl-fatal"
      break
    fi
    if [ -s "$LOG" ] && ! has_client; then
      echo "client-exited-attempt:$attempt elapsed=${elapsed}s"
      kill_stale_clients "exited-attempt-$attempt"
      stop_reason="client-exited"
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
      stop_reason="idle"
      break
    fi
    sleep "$POLL_SECONDS"
    elapsed=$((elapsed + POLL_SECONDS))
  done

  if [ "$elapsed" -ge "$WAIT_SECONDS" ]; then
    echo "timeout-attempt:$attempt elapsed=${elapsed}s"
    kill_stale_clients "timeout-attempt-$attempt"
    stop_reason="timeout"
  fi

  set +e
  patch_output="$(analyze_and_patch "$stop_reason" "$attempt_started_at" 2>&1)"
  patch_status=$?
  set -e
  echo "$patch_output"
  if [ "$patch_status" -eq 0 ]; then
    continue
  fi
  if [ "$patch_status" -eq 4 ]; then
    echo "auto-quarantine candidate was already denylisted; stopping instead of looping" >&2
    exit "$patch_status"
  fi
  if [ "$patch_status" -eq 5 ]; then
    echo "auto-quarantine stopped on timeout without crash evidence; no denylist change was made" >&2
    exit 0
  fi
  echo "auto-quarantine could not identify a threaded failing mod; no denylist change was made" >&2
  exit "$patch_status"
done

echo "auto-quarantine exhausted attempts" >&2
kill_stale_clients "exhausted"
exit 1
