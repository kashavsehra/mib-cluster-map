# Audi (MHI2_*_AU37x_* / AU276) Virtual Cockpit Now Playing

Track title / artist / album plus cover art on the Virtual Cockpit's media
screen, for both CarPlay and Android Auto.  Audi's HMI feeds these through
the TerminalMode BAP pipeline (TerminalModeBapCombi + AppConnectorTerminalMode
+ CoverArtProviderMux), which is different from the VW path used by
thirdparty/mib2-android-auto-vc/.

## Binaries

This directory holds the prebuilt native hook that extracts CarPlay cover art
from the HARMAN iAP2 stream:

  - libcarplay_hook.so  (from chefranov/mhi2-au37x-carplay, bin/)

Obtain it from that project's release and place it here.  It is loaded via
LD_PRELOAD on the CarPlay child (IPL_CONFIG_DIR_DIO_MANAGER) in
smartphone_integrator.json, exactly as chefranov's install.sh does.  It does
NOT touch gal (Android Auto), stream-player, or dmdt.

## Java

The Java side is rebuilt in this repository:

  - java/coverart/  ->  build/ClusterMapCoverArt.jar

It ships only the patched HMI classes (TerminalModeBapCombi$EventListener,
AppConnectorTerminalMode, CoverArtProviderMux, AppConnectorTerminalMode$CoverArtProvider,
plus com.mhi2.carplay.framework.* and com.mhi2.carplay.coverart.CoverArt); the stock
classes come from lsd.jxe at runtime.  On an Audi unit every *.jar under
lsd/jars/ is on the classpath, so dropping ClusterMapCoverArt.jar there is enough.

CarPlay cover art arrives over the loopback TCP bus (127.0.0.1:19810) that
libcarplay_hook.so publishes (EVT_COVERART / EVT_COVERART_NONE / EVT_HELLO).
Android Auto now-playing on Audi uses the same TerminalMode pipeline; the
existing DSI Android Auto 2 path (thirdparty/mib2-android-auto-vc) feeds it on
units where that interface is present.

## Adaptation setting

The unit needs Module 17 adaptation Picture_Upload_Download = active (Audi
default on AU37x).  No other adaptation changes are required.

## Credits

Ported from chefranov/mhi2-au37x-carplay (coverart_hook.jar), which is itself
derived from luka-dev/mib2q-carplay-rgi.  See those projects for the original
implementation and licensing.
