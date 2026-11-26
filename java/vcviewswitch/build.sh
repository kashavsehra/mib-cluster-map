#!/bin/sh
# Build VCViewSwitch.jar (A3 8V Virtual Cockpit Classic/Large/Sport view sync)
# against minimal compile-time stubs of the Audi MIB2 DSI/HMI classes, so no
# proprietary firmware JAR is needed.
#
# Usage: java/vcviewswitch/build.sh <out_dir>
set -eu

HERE=$(cd "$(dirname "$0")" && pwd)
OUT=${1:-"$HERE/../../build"}
mkdir -p "$OUT"

WORK=$(mktemp -d "${TMPDIR:-/tmp}/vcviewswitch.XXXXXX")
trap 'rm -rf "$WORK"' 0 1 2 15

STUBS="$WORK/stubs"
CLASSES="$WORK/classes"
mkdir -p \
    "$STUBS/de/audi/app/terminalmode" \
    "$STUBS/org/dsi/ifc/base" \
    "$STUBS/org/dsi/ifc/global" \
    "$STUBS/org/dsi/ifc/androidauto2" \
    "$CLASSES"

cat > "$STUBS/de/audi/app/terminalmode/IContext.java" <<'EOF'
package de.audi.app.terminalmode;
public interface IContext {}
EOF

cat > "$STUBS/org/dsi/ifc/base/DSIListener.java" <<'EOF'
package org.dsi.ifc.base;
public interface DSIListener {}
EOF

cat > "$STUBS/org/dsi/ifc/global/ResourceLocator.java" <<'EOF'
package org.dsi.ifc.global;
public class ResourceLocator {}
EOF

cat > "$STUBS/org/dsi/ifc/androidauto2/TrackData.java" <<'EOF'
package org.dsi.ifc.androidauto2;
public class TrackData {}
EOF

cat > "$STUBS/org/dsi/ifc/androidauto2/PlaybackInfo.java" <<'EOF'
package org.dsi.ifc.androidauto2;
public class PlaybackInfo {}
EOF

cat > "$STUBS/org/dsi/ifc/androidauto2/CallState.java" <<'EOF'
package org.dsi.ifc.androidauto2;
public class CallState {}
EOF

cat > "$STUBS/org/dsi/ifc/androidauto2/TelephonyState.java" <<'EOF'
package org.dsi.ifc.androidauto2;
public class TelephonyState {}
EOF

cat > "$STUBS/org/dsi/ifc/androidauto2/DSIAndroidAuto2Listener.java" <<'EOF'
package org.dsi.ifc.androidauto2;
import org.dsi.ifc.base.DSIListener;
import org.dsi.ifc.global.ResourceLocator;
public interface DSIAndroidAuto2Listener extends DSIListener {
    void updateNowPlayingData(TrackData a, int b);
    void updateCoverArtUrl(ResourceLocator a, int b);
    void updateNavigationNextTurnEvent(String a, int b, int c, int d, int e, int f);
    void updateNavigationNextTurnDistance(int a, int b, int c);
    void updatePlayposition(int a, int b);
    void videoFocusRequestNotification(int a, int b);
    void videoAvailable(boolean a, int b);
    void audioFocusRequestNotification(int a, int b);
    void audioAvailable(int a, boolean b, int c);
    void voiceSessionNotification(int a, int b);
    void microphoneRequestNotification(int a, int b);
    void navFocusRequestNotification(int a, int b);
    void updateCallState(CallState[] a, int b);
    void updateTelephonyState(TelephonyState a, int b);
    void updatePlaybackState(PlaybackInfo a, int b);
    void setExternalDestination(double a, double b, String c, String d, int e);
    void bluetoothPairingRequest(String a, int b);
    void asyncException(int a, String b, int c);
}
EOF

# Detect highest supported legacy target (JDK 8 supports 1.3; JDK 9+ supports 1.8 / --release 8;
# IBM J9 on QNX 6.5 accepts class format <= 48.0 / 49.0, so prefer 1.4 or 1.5 when javac supports it).
TARGET_FLAGS=""
if javac -source 1.4 -target 1.4 -version >/dev/null 2>&1; then
    TARGET_FLAGS="-source 1.4 -target 1.4"
elif javac -source 1.5 -target 1.5 -version >/dev/null 2>&1; then
    TARGET_FLAGS="-source 1.5 -target 1.5"
elif javac -source 1.8 -target 1.8 -version >/dev/null 2>&1; then
    TARGET_FLAGS="-source 1.8 -target 1.8"
fi

# Compile stubs first so they are NOT packaged into VCViewSwitch.jar
STUB_CLASSES="$WORK/stub_classes"
mkdir -p "$STUB_CLASSES"
# shellcheck disable=SC2086
javac $TARGET_FLAGS -d "$STUB_CLASSES" \
    $(find "$STUBS" -name '*.java')

# Compile real sources against the stubs
# shellcheck disable=SC2086
javac $TARGET_FLAGS -cp "$STUB_CLASSES" -d "$CLASSES" \
    "$HERE/src/com/mhi2/vcview/VCViewWatcher.java" \
    "$HERE/src/de/audi/app/terminalmode/smartphone/androidauto2/AndroidAuto2ListenerDistributor.java"

# Package only our compiled classes (never the compile-time stubs)
jar cf "$OUT/VCViewSwitch.jar" -C "$CLASSES" .
echo "Built $OUT/VCViewSwitch.jar"
