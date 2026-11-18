package com.mhi2.carplay.coverart;

import com.mhi2.carplay.framework.CarplayBus;
import com.mhi2.carplay.framework.Log;

public class CoverArt
implements CarplayBus.Listener {
    private static final String TAG = "CoverArt";
    public static final String COVERART_PATH = "/var/app/icab/tmp/37/coverart.png";
    private static CoverArt instance;
    private volatile boolean running;
    private volatile long lastCrc = 0L;
    private volatile int artId = 0;
    private volatile String currentPath = "/var/app/icab/tmp/37/coverart.png";
    private Callback callback;

    public static synchronized CoverArt getInstance() {
        if (instance == null) {
            instance = new CoverArt();
        }
        return instance;
    }

    private CoverArt() {
    }

    public void start(Callback callback) {
        if (this.running) {
            return;
        }
        this.running = true;
        this.callback = callback;
        CarplayBus carplayBus = CarplayBus.getInstance();
        carplayBus.on(16, this);
        carplayBus.on(17, this);
        carplayBus.on(1, this);
        carplayBus.start();
        Log.i(TAG, "Started (subscribed to EVT_COVERART + EVT_COVERART_NONE + EVT_HELLO)");
    }

    public void stop() {
        if (!this.running) {
            return;
        }
        this.running = false;
        CarplayBus carplayBus = CarplayBus.getInstance();
        carplayBus.off(16);
        carplayBus.off(17);
        carplayBus.off(1);
        Log.i(TAG, "Stopped");
    }

    public boolean isRunning() {
        return this.running;
    }

    public boolean hasCoverArt() {
        return this.lastCrc != 0L;
    }

    public String getPath() {
        return this.currentPath;
    }

    public int getArtId() {
        return this.artId;
    }

    public void reset() {
        long l = this.lastCrc;
        this.lastCrc = 0L;
        this.artId = 0;
        this.currentPath = COVERART_PATH;
        Log.i(TAG, "State reset (prevCrc=" + Long.toHexString(l) + ")");
        if (this.callback != null) {
            try {
                this.callback.onSessionReset();
            }
            catch (Exception exception) {
                Log.e(TAG, "onSessionReset callback error", exception);
            }
        }
    }

    public void resetSession() {
        this.reset();
    }

    public void onFrame(int n, int n2, byte[] byArray, int n3) {
        if (n == 1) {
            Log.i(TAG, "EVT_HELLO - new session, resetting state");
            this.reset();
            return;
        }
        if (n == 17) {
            CarplayBus.Data data = CarplayBus.parseText(byArray, n3);
            String string = data.str("reason", "none");
            long l = this.lastCrc;
            this.lastCrc = 0L;
            this.artId = 0;
            this.currentPath = COVERART_PATH;
            Log.i(TAG, "No cover art for this track (" + string + "), prevCrc=" + Long.toHexString(l));
            if (this.callback != null) {
                try {
                    this.callback.onNoCoverArt(string);
                }
                catch (Exception exception) {
                    Log.e(TAG, "onNoCoverArt callback error", exception);
                }
            }
            return;
        }
        if (n != 16) {
            return;
        }
        CarplayBus.Data data = CarplayBus.parseText(byArray, n3);
        long l = data.num64("crc", 0L);
        if (l == 0L) {
            return;
        }
        boolean bl = l == this.lastCrc;
        this.lastCrc = l;
        this.artId = (int)l;
        String string = data.str("path", COVERART_PATH);
        if (string != null && string.length() > 0) {
            this.currentPath = string;
        }
        Log.i(TAG, (bl ? "Same cover art again: crc=" : "New cover art: crc=") + Long.toHexString(l) + " artId=" + this.artId + " path=" + this.currentPath);
        if (this.callback != null) {
            try {
                this.callback.onNewCoverArt(this.currentPath, this.artId);
            }
            catch (Exception exception) {
                Log.e(TAG, "Callback error", exception);
            }
        }
    }

    public static interface Callback {
        public void onNewCoverArt(String var1, int var2);

        public void onNoCoverArt(String var1);

        public void onSessionReset();
    }
}
