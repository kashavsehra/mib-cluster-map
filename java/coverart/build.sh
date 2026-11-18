#!/bin/bash
# Build ClusterMapCoverArt.jar: Audi (MHI2_*_AU37x_* / AU276) Virtual Cockpit
# Now Playing (track title/artist/album + cover art) for CarPlay and Android Auto.
#
#   java/coverart/build.sh [output dir]      default: build/ at the repo root
#
# Ported from chefranov/mhi2-au37x-carplay's coverart_hook.jar (which itself is
# built from luka-dev/mib2q-carplay-rgi).  Ships only the patched HMI classes;
# the stock ones come from lsd.jxe at runtime.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
OUT=${1:-"$ROOT/build"}
mkdir -p "$OUT"
OUT="$(cd "$OUT" && pwd)"
command -v javap >/dev/null && command -v jar >/dev/null || {
  echo "ERROR: javap and jar are needed (any JDK 8 or newer)"; exit 1; }

T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT
mkdir -p "$T/classes"

OURS=(
  'com/mhi2/carplay/framework/Obf.class'
  'com/mhi2/carplay/framework/Log.class'
  'com/mhi2/carplay/framework/CarplayBus.class'
  'com/mhi2/carplay/framework/CarplayBus$1.class'
  'com/mhi2/carplay/framework/CarplayBus$2.class'
  'com/mhi2/carplay/framework/CarplayBus$3.class'
  'com/mhi2/carplay/framework/CarplayBus$Data.class'
  'com/mhi2/carplay/framework/CarplayBus$Listener.class'
  'com/mhi2/carplay/coverart/CoverArt.class'
  'com/mhi2/carplay/coverart/CoverArt$Callback.class'
  'de/audi/app/combi/bap/app/audio/AppConnectorTerminalMode.class'
  'de/audi/app/combi/bap/app/audio/AppConnectorTerminalMode$CoverArtProvider.class'
  'de/audi/app/combi/bap/app/audio/AppConnectorTerminalMode$FocusListener.class'
  'de/audi/app/combi/bap/app/audio/CoverArtProviderMux.class'
  'de/audi/app/terminalmode/combi/TerminalModeBapCombi$EventListener.class'
  'de/audi/app/terminalmode/combi/TerminalModeBapCombi$1.class'
)

SRCS=()
while IFS= read -r f; do SRCS+=("$f"); done < <(find "$HERE/src" "$HERE/stubs" -name '*.java' | sort)
javac --release 8 -nowarn -encoding UTF-8 -d "$T/classes" "${SRCS[@]}"

for c in "${OURS[@]}"; do
  f="$T/classes/$c"
  [ -f "$f" ] || { echo "ERROR: $c was not built"; exit 1; }
done

rm -f "$OUT/ClusterMapCoverArt.jar"
(cd "$T/classes" && jar cf "$OUT/ClusterMapCoverArt.jar" "${OURS[@]}")
echo "Built $OUT/ClusterMapCoverArt.jar"
unzip -l "$OUT/ClusterMapCoverArt.jar" | grep '\.class$' | head -30
