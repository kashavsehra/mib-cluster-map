#!/bin/bash
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
STOCK=${1:-}
OUT=${2:-"$ROOT/build"}
[ -r "$STOCK" ] || { echo "usage: $0 <converted stock lsd.jar> [output dir]"; exit 2; }
command -v javac >/dev/null && command -v java >/dev/null &&
command -v javap >/dev/null && command -v jar >/dev/null &&
command -v python3 >/dev/null || {
    echo "ERROR: a JDK (8 or newer) is needed"; exit 1; }
mkdir -p "$OUT"
OUT="$(cd "$OUT" && pwd)"
T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT

mkdir -p "$T/classes/de/esolutions/hmi/widgets/audi/evo/high/widgets"
javac --release 8 -nowarn -encoding UTF-8 -d "$T/classes" \
    "$HERE/src/local/clustermap/ViewMode.java"
python3 "$ROOT/tools/classfile_transform.py" inject-view-insets "$STOCK" \
    "$T/classes/de/esolutions/hmi/widgets/audi/evo/high/widgets/CombiMapController.class"
python3 "$ROOT/tools/classfile_transform.py" strip-frames \
    "$T/classes/local/clustermap/ViewMode.class"

for c in \
    de/esolutions/hmi/widgets/audi/evo/high/widgets/CombiMapController.class \
    local/clustermap/ViewMode.class; do
    f="$T/classes/$c"
    [ -f "$f" ] || { echo "ERROR: $c was not built"; exit 1; }
    version=$(javap -v "$f" | sed -n 's/^ *major version: //p')
    [ "$version" = 46 ] || { echo "ERROR: $c is class version $version, not 46"; exit 1; }
    bad=$(javap -c -p "$f" | grep -E \
        'StackMapTable|StringBuilder|invokedynamic|InvokeDynamic|MethodHandle|BootstrapMethods' || true)
    [ -z "$bad" ] || { echo "ERROR: $c has unsupported class-library or bytecode features"; exit 1; }
done

rm -f "$OUT/ClusterMapViewInsets.jar"
(cd "$T/classes" && jar cf "$OUT/ClusterMapViewInsets.jar" \
    de/esolutions/hmi/widgets/audi/evo/high/widgets/CombiMapController.class \
    local/clustermap/ViewMode.class)
echo "Built $OUT/ClusterMapViewInsets.jar from the supplied stock HMI classes"
