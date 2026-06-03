#!/usr/bin/env bash
set -euo pipefail

ROOT="/home/luna/.var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher"
INSTANCE_ID="MeatballCraft, Dimensional Ascension"
INSTANCE="$ROOT/instances/$INSTANCE_ID"
MC="$INSTANCE/minecraft"
LOG="$MC/logs/latest.log"
JAVA="${CRO_JAVA:-$ROOT/java/eclipse_temurin_jre25.0.3+9/bin/java}"
PLAYER="${CRO_OFFLINE_NAME:-GPOMTest}"
UUID="${CRO_OFFLINE_UUID:-00000000-0000-0000-0000-000000000000}"
NATIVES="$INSTANCE/natives"
CLEANROOM="$ROOT/libraries/com/cleanroommc/cleanroom/0.3.24-alpha/cleanroom-0.3.24-alpha.jar"
MINECRAFT_CLIENT="$ROOT/libraries/com/mojang/minecraft/1.12.2/minecraft-1.12.2-client.jar"

if [ "${CRO_KILL_STALE:-0}" = "1" ]; then
  ps -eo pid,args | awk '/java.*com\.cleanroommc\.boot\.MainClient|java.*net\.minecraft\.launchwrapper\.Launch/ && !/awk/ {print $1}' | xargs -r kill 2>/dev/null || true
  sleep 1
fi

mkdir -p "$MC/logs" "$MC/crash-reports" /tmp/cro-manual-natives
: > "$LOG"

CLASSPATH=$(
  ROOT="$ROOT" INSTANCE="$INSTANCE" CLEANROOM="$CLEANROOM" MINECRAFT_CLIENT="$MINECRAFT_CLIENT" python3 - <<'PY'
from pathlib import Path
import json
import os
import zipfile

root = Path(os.environ["ROOT"])
instance = Path(os.environ["INSTANCE"])
cleanroom = Path(os.environ["CLEANROOM"])
minecraft_client = Path(os.environ["MINECRAFT_CLIENT"])

with zipfile.ZipFile(cleanroom) as jar:
    manifest = jar.read("META-INF/MANIFEST.MF").decode("utf-8")

lines = []
for line in manifest.splitlines():
    if line.startswith(" ") and lines:
        lines[-1] += line[1:]
    else:
        lines.append(line)

entries = [cleanroom, minecraft_client]
seen = {cleanroom, minecraft_client}
for line in lines:
    if line.startswith("Class-Path:"):
        for entry in line.split(":", 1)[1].strip().split():
            if entry == "minecraft_server.1.12.2.jar":
                continue
            path = root / entry
            if path.exists() and path not in seen:
                entries.append(path)
                seen.add(path)

for patch in ("net.minecraft.json", "net.minecraftforge.json", "org.lwjgl3.json"):
    path = instance / "patches" / patch
    if not path.exists():
        continue
    data = json.loads(path.read_text())
    main_jar = data.get("mainJar", {}).get("downloads", {}).get("artifact", {}).get("path")
    if main_jar:
        jar = root / "libraries" / main_jar
        if jar.exists() and jar not in seen:
            entries.append(jar)
            seen.add(jar)
    for library in data.get("libraries", []):
        artifact = library.get("downloads", {}).get("artifact", {})
        artifact_path = artifact.get("path")
        if not artifact_path:
            name = library.get("name", "")
            parts = name.split(":")
            if len(parts) < 3:
                continue
            group, artifact_id, version = parts[:3]
            classifier = parts[3] if len(parts) > 3 else None
            filename = f"{artifact_id}-{version}"
            if classifier:
                filename += f"-{classifier}"
            filename += ".jar"
            artifact_path = "/".join(group.split(".") + [artifact_id, version, filename])
        jar = root / "libraries" / artifact_path
        if jar.exists() and jar not in seen:
            entries.append(jar)
            seen.add(jar)

print(":".join(str(path) for path in entries))
PY
)

export MC_VERSION="1.12.2"
export assetIndex="1.12"
export assetDirectory="$ROOT/assets"
export mainClass="top.outlands.foundation.boot.Foundation"
export tweakClass="net.minecraftforge.fml.common.launcher.FMLTweaker"

cd "$MC"
nohup setsid "$JAVA" \
  -Xms8192m \
  -Xmx12288m \
  -XX:+UseZGC \
  -XX:+UseCompactObjectHeaders \
  -Dfile.encoding=UTF-8 \
  -Djava.library.path="$NATIVES:/tmp/cro-manual-natives" \
  -Dfml.ignoreInvalidMinecraftCertificates=true \
  -Dfml.ignorePatchDiscrepancies=true \
  -cp "$CLASSPATH" \
  com.cleanroommc.boot.MainClient \
  --username "$PLAYER" \
  --uuid "$UUID" \
  --accessToken "offline" \
  --userType "legacy" \
  --version "1.12.2" \
  --gameDir "$MC" \
  --assetsDir "$ROOT/assets" \
  --assetIndex "1.12" \
  --width 854 \
  --height 480 \
  </dev/null >/tmp/cro-manual-launch.out 2>/tmp/cro-manual-launch.err &

echo $! > /tmp/cro-manual-launch.pid

TIMEOUT_SECONDS="${CRO_LOG_TIMEOUT_SECONDS:-90}"
for i in $(seq 1 "$TIMEOUT_SECONDS"); do
  if [ -s "$LOG" ]; then
    echo "log-started:$i"
    exit 0
  fi
  if ! kill -0 "$(cat /tmp/cro-manual-launch.pid)" 2>/dev/null; then
    echo "client-exited-before-log:$i" >&2
    printf '%s\n' '--- manual stdout ---' >&2
    tail -120 /tmp/cro-manual-launch.out >&2 2>/dev/null || true
    printf '%s\n' '--- manual stderr ---' >&2
    tail -160 /tmp/cro-manual-launch.err >&2 2>/dev/null || true
    exit 1
  fi
  if (( i % 15 == 0 )); then
    echo "waiting:$i"
    ps -eo pid,args | awk '/java.*com\.cleanroommc\.boot\.MainClient|java.*net\.minecraft\.launchwrapper\.Launch/ && !/awk/ {print $1, substr($0,1,220)}' | tail -10
  fi
  sleep 1
done

echo "no-log-after-${TIMEOUT_SECONDS}s" >&2
printf '%s\n' '--- manual stdout ---' >&2
tail -120 /tmp/cro-manual-launch.out >&2 2>/dev/null || true
printf '%s\n' '--- manual stderr ---' >&2
tail -160 /tmp/cro-manual-launch.err >&2 2>/dev/null || true
exit 1
