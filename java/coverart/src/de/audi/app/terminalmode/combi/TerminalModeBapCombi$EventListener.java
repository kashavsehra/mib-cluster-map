package de.audi.app.terminalmode.combi;

import com.mhi2.carplay.coverart.CoverArt;
import com.mhi2.carplay.framework.Log;
import de.audi.app.combi.bap.app.audio.AppConnectorTerminalMode;
import de.audi.app.terminalmode.events.DefaultEventListener;
import de.audi.app.terminalmode.events.TrackDataChangedEvent;
import de.audi.app.terminalmode.events.TrackPlayPositionEvent;
import de.audi.atip.interapp.bap.data.TimeStamp;
import de.audi.atip.interapp.combi.bap.audio.CombiBAPServiceTerminalMode;
import de.audi.atip.interapp.combi.bap.audio.data.CombiBAPCurrentStationInfo;
import de.audi.atip.interapp.combi.bap.audio.data.PlayPosition;
import de.audi.atip.log.LogChannel;
import java.lang.reflect.Field;

class TerminalModeBapCombi$EventListener extends DefaultEventListener implements CoverArt.Callback, AppConnectorTerminalMode.FocusListener {
    private static final String TAG = "TMEventListener";
    private final TerminalModeBapCombi this$0;
    private volatile String lastTrackKey = null;
    private volatile String currentTitle = "";
    private volatile String currentArtist = "";
    private volatile String currentAlbum = "";
    private volatile boolean sentWithoutCover = false;
    private volatile boolean pendingCoverRetry = false;
    private static final int MAX_COVER_RETRIES = 10;
    private volatile int coverRetries = 0;
    private volatile boolean retriesExhaustedLogged = false;
    private volatile boolean waitingForFocus = false;
    private volatile String coverOwnerTrackKey = null;
    private static final long SKIP_BURST_MS = 400L;
    private volatile long lastTrackChangeMs = 0L;

    private TerminalModeBapCombi$EventListener(TerminalModeBapCombi terminalModeBapCombi) {
        this.this$0 = terminalModeBapCombi;
    }

    TerminalModeBapCombi$EventListener(TerminalModeBapCombi terminalModeBapCombi, TerminalModeBapCombi$1 var2) {
        this(terminalModeBapCombi);
        this.startCoverArt();
    }

    private void startCoverArt() {
        AppConnectorTerminalMode.setFocusListener(this);
        CoverArt.getInstance().start(this);
    }

    private boolean coverBelongsToCurrentTrack() {
        return this.coverOwnerTrackKey == null || this.coverOwnerTrackKey.equals(this.lastTrackKey);
    }

    private boolean inSkipBurst() {
        long l = System.currentTimeMillis() - this.lastTrackChangeMs;
        return this.lastTrackChangeMs != 0L && l < 400L;
    }

    public void onAudioFocusGained() {
        if (!this.waitingForFocus) {
            return;
        }
        this.waitingForFocus = false;
        if (!this.haveTrackInfo()) {
            return;
        }
        CoverArt coverArt = CoverArt.getInstance();
        if (!coverArt.hasCoverArt()) {
            return;
        }
        Log.i(TAG, "audio focus gained - sending the cover that was held back");
        if (!this.pushCurrentWithCover(coverArt)) {
            this.armCoverRetryAfterPush("push after focus gained not delivered");
        }
    }

    public void onNewCoverArt(String string, int n) {
        Log.i(TAG, "Cover art updated: " + string + " id=" + n);
        if (!this.haveTrackInfo()) {
            return;
        }
        this.coverOwnerTrackKey = null;
        if (this.inSkipBurst()) {
            this.armCoverRetry("skip burst - deferring the picture");
            return;
        }
        if (!this.pushCurrentWithCover(CoverArt.getInstance())) {
            this.armCoverRetryAfterPush("late cover art not delivered");
        }
    }

    public void onNoCoverArt(String string) {
        Log.i(TAG, "No cover art for this track (" + string + ")");
        this.coverOwnerTrackKey = null;
        this.pendingCoverRetry = false;
        this.waitingForFocus = false;
        this.coverRetries = 0;
        this.retriesExhaustedLogged = false;
        if (!this.haveTrackInfo()) {
            return;
        }
        if (this.getBapService() == null) {
            return;
        }
        this.sentWithoutCover = true;
        this.sendNowPlaying(this.currentTitle, this.currentArtist, this.currentAlbum, null, 0);
    }

    public void onSessionReset() {
        Log.i(TAG, "Session reset - clearing track state");
        this.lastTrackKey = null;
        this.sentWithoutCover = false;
        this.pendingCoverRetry = false;
        this.waitingForFocus = false;
        this.coverOwnerTrackKey = null;
        this.coverRetries = 0;
        this.retriesExhaustedLogged = false;
        this.lastTrackChangeMs = 0L;
    }

    private boolean haveTrackInfo() {
        return this.currentTitle.length() > 0 || this.currentArtist.length() > 0 || this.currentAlbum.length() > 0;
    }

    private boolean pushCurrentWithCover(CoverArt coverArt) {
        int n = coverArt.getArtId();
        if (this.sentWithoutCover) {
            Log.i(TAG, "Late cover art - using modified id=" + ++n);
        }
        AppConnectorTerminalMode.armPushProbe();
        this.sendNowPlaying(this.currentTitle, this.currentArtist, this.currentAlbum, coverArt.getPath(), n);
        if (!AppConnectorTerminalMode.pushWasDelivered()) {
            return false;
        }
        this.coverOwnerTrackKey = this.lastTrackKey;
        this.sentWithoutCover = false;
        this.pendingCoverRetry = false;
        this.waitingForFocus = false;
        this.coverRetries = 0;
        this.retriesExhaustedLogged = false;
        return true;
    }

    private void armCoverRetryAfterPush(String string) {
        if (AppConnectorTerminalMode.pushSkippedForFocus()) {
            if (!this.waitingForFocus) {
                this.waitingForFocus = true;
                Log.i(TAG, "cover held until audio focus arrives (" + string + ")");
            }
            this.pendingCoverRetry = true;
            return;
        }
        this.armCoverRetry(string);
    }

    private void armCoverRetry(String string) {
        if (this.coverRetries >= 10) {
            if (!this.retriesExhaustedLogged) {
                this.retriesExhaustedLogged = true;
                Log.w(TAG, "cover retries exhausted (" + string + ") - waiting for the next track");
            }
            return;
        }
        ++this.coverRetries;
        this.pendingCoverRetry = true;
        Log.i(TAG, "cover retry " + this.coverRetries + "/" + 10 + " armed: " + string);
    }

    public void updateNowPlayingData(TrackDataChangedEvent trackDataChangedEvent) {
        String string = trackDataChangedEvent.getTitle();
        String string2 = trackDataChangedEvent.getArtist();
        String string3 = trackDataChangedEvent.getAlbum();
        if (string == null) {
            string = "";
        }
        if (string2 == null) {
            string2 = "";
        }
        if (string3 == null) {
            string3 = "";
        }
        if (string.length() == 0 && string2.length() == 0 && string3.length() == 0) {
            return;
        }
        if ("%".equals(string) && "%".equals(string2) && "%".equals(string3)) {
            return;
        }
        String string4 = string.trim();
        String string5 = string2.trim();
        String string6 = string3.trim();
        if (string4.length() == 0 && string5.length() == 0 && string6.length() == 0) {
            return;
        }
        this.currentTitle = string4;
        this.currentArtist = string5;
        this.currentAlbum = string6;
        String string7 = string4 + "|" + string5 + "|" + string6;
        boolean bl = this.inSkipBurst();
        if (!string7.equals(this.lastTrackKey)) {
            this.lastTrackChangeMs = System.currentTimeMillis();
        }
        this.lastTrackKey = string7;
        Log.d(TAG, "Track: " + string4 + " - " + string5);
        this.coverRetries = 0;
        this.retriesExhaustedLogged = false;
        CoverArt coverArt = CoverArt.getInstance();
        if (coverArt.hasCoverArt() && !bl && this.coverBelongsToCurrentTrack()) {
            this.sentWithoutCover = false;
            if (!this.pushCurrentWithCover(coverArt)) {
                this.sentWithoutCover = true;
                this.armCoverRetryAfterPush("cover push not delivered");
            }
        } else if (coverArt.hasCoverArt()) {
            this.sentWithoutCover = true;
            this.sendNowPlaying(string4, string5, string6, null, 0);
            this.armCoverRetry("skip burst - deferring the picture");
        } else {
            this.sentWithoutCover = true;
            this.sendNowPlaying(string4, string5, string6, null, 0);
        }
    }

    public void updatePlayPosition(TrackPlayPositionEvent trackPlayPositionEvent) {
        CombiBAPServiceTerminalMode combiBAPServiceTerminalMode = this.getBapService();
        if (combiBAPServiceTerminalMode == null) {
            return;
        }
        if (this.pendingCoverRetry && !this.inSkipBurst() && !this.waitingForFocus) {
            this.pendingCoverRetry = false;
            if (this.haveTrackInfo()) {
                CoverArt coverArt = CoverArt.getInstance();
                if (coverArt.hasCoverArt() && this.coverBelongsToCurrentTrack()) {
                    Log.i(TAG, "Retrying cover art send");
                    if (!this.pushCurrentWithCover(coverArt)) {
                        this.armCoverRetryAfterPush("retry not delivered");
                    }
                } else {
                    this.sendNowPlaying(this.currentTitle, this.currentArtist, this.currentAlbum, null, 0);
                    this.sentWithoutCover = true;
                }
            }
        }
        if (trackPlayPositionEvent.getTotalTimeOfTrack() == 0) {
            combiBAPServiceTerminalMode.updatePlayPosition(PlayPosition.builder().setTimePosition(TimeStamp.getInstanceFromSeconds(65535)).setTotalPlayTime(TimeStamp.getInstanceFromSeconds(65535)).build());
        } else {
            combiBAPServiceTerminalMode.updatePlayPosition(PlayPosition.builder().setTimePosition(TimeStamp.getInstanceFromSeconds(trackPlayPositionEvent.getPlayTime())).setTotalPlayTime(TimeStamp.getInstanceFromSeconds(trackPlayPositionEvent.getTotalTimeOfTrack())).build());
        }
    }

    private void sendNowPlaying(String string, String string2, String string3, String string4, int n) {
        CombiBAPServiceTerminalMode combiBAPServiceTerminalMode = this.getBapService();
        if (combiBAPServiceTerminalMode == null) {
            Log.w(TAG, "No BAP service - queuing retry");
            this.pendingCoverRetry = true;
            return;
        }
        int n2 = string.length() > 0 ? 6 : 0;
        int n3 = string2.length() > 0 ? 73 : 0;
        int n4 = string3.length() > 0 ? 74 : 0;
        CombiBAPCurrentStationInfo combiBAPCurrentStationInfo = new CombiBAPCurrentStationInfo();
        combiBAPCurrentStationInfo.setPrimaryInformation(string, n2, 0);
        combiBAPCurrentStationInfo.setSecondaryInformation(string2, n3);
        combiBAPCurrentStationInfo.setTertiaryInformation(string3, n4);
        combiBAPCurrentStationInfo.setListRef(0);
        combiBAPCurrentStationInfo.setListAbsolutePosition(0);
        combiBAPCurrentStationInfo.setPresetListRef(0);
        combiBAPCurrentStationInfo.setPresetListAbsolutePosition(0);
        combiBAPCurrentStationInfo.setCommonListRef(0);
        combiBAPCurrentStationInfo.setCommonListAbsolutePosition(0);
        if (string4 != null && string4.length() > 0 && n != 0) {
            combiBAPCurrentStationInfo.setPicture(n, string4);
            Log.i(TAG, "With cover: " + string4 + " id=" + n);
        }
        combiBAPServiceTerminalMode.updateCurrentStation(combiBAPCurrentStationInfo);
        combiBAPServiceTerminalMode.updateActiveInfoState(0);
        Log.i(TAG, "Sent now playing");
    }

    private LogChannel getLogger() {
        try {
            Field field = this.this$0.getClass().getDeclaredField("lc");
            field.setAccessible(true);
            return (LogChannel) field.get(this.this$0);
        } catch (Exception exception) {
            return null;
        }
    }

    private CombiBAPServiceTerminalMode getBapService() {
        try {
            Field field = this.this$0.getClass().getDeclaredField("bapService");
            field.setAccessible(true);
            return (CombiBAPServiceTerminalMode) field.get(this.this$0);
        } catch (Exception exception) {
            return null;
        }
    }
}
