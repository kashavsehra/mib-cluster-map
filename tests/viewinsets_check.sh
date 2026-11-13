#!/bin/bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
[ $# -eq 1 ] && [ -r "$1" ] || { echo "usage: $0 <converted P5089 stock lsd.jar>"; exit 2; }
T=$(mktemp -d)
trap 'rm -f "/tmp/clustermap-view-mode-test-$$" "/tmp/clustermap-view-mode-test-$$.new"; rm -rf "$T"' EXIT

"$ROOT/java/viewinsets/build.sh" "$1" "$T"
javap -classpath "$T/ClusterMapViewInsets.jar:$1" -c -p \
    de.esolutions.hmi.widgets.audi.evo.high.widgets.CombiMapController > "$T/controller.txt"
sync_calls=$(grep -c 'local/clustermap/ViewMode.sync' "$T/controller.txt" || true)
[ "$sync_calls" -eq 2 ] || {
    echo "ERROR: expected ViewMode.sync calls in both controller callbacks, found $sync_calls" >&2
    exit 1
}

javac --release 8 -nowarn -encoding UTF-8 -d "$T/classes" \
    "$ROOT/java/viewinsets/src/local/clustermap/ViewMode.java" \
    "$ROOT/java/viewinsets/test/local/clustermap/ViewModeTest.java"
java -Xverify:all -cp "$T/classes" local.clustermap.ViewModeTest \
    "/tmp/clustermap-view-mode-test-$$"
echo "P5089 hook injection and view-mode observer tests passed."
