package de.audi.app.combi.bap.app.audio;

import de.audi.app.bap.fw.functiontypes.BAPFunctionArrayFSG;
import de.audi.app.combi.bap.app.audio.list.SourceListHandler;
import de.audi.app.combi.bap.fw.AbstractCombiModule;
import de.audi.app.combi.bap.app.audio.AbstractAppConnectorAudio;
import de.audi.atip.base.AppNameConst;
import de.audi.app.combi.bap.app.audio.CombiBAPAudioListStates;
import de.audi.app.combi.bap.app.audio.CombiModuleAudio;

import de.audi.atip.interapp.combi.bap.audio.CombiBAPServiceTerminalMode;
import de.audi.atip.interapp.combi.bap.audio.data.CombiBAPAudioSource;
import de.audi.atip.interapp.combi.bap.audio.data.CombiBAPCurrentStationInfo;
import de.audi.atip.interapp.combi.bap.audio.data.PlayPosition;
import de.vw.mib.bap.generated.audiosd.serializer.PlayPosition_Status;
import de.audi.app.combi.bap.app.kombipictures.IPictureProvider;
import org.dsi.ifc.global.ResourceLocator;
import java.util.Map;

public class AppConnectorTerminalMode extends AbstractAppConnectorAudio implements CombiBAPServiceTerminalMode {
    private static final int SOURCE_TYPE_VC = 21;
    private final AppConnectorTerminalMode.CoverArtProvider pictureProvider = new AppConnectorTerminalMode.CoverArtProvider(this);
    private static volatile boolean pushDelivered = false;
    private static volatile boolean pushSkippedForFocus = false;
    private static volatile boolean clearRequested = false;
    private static volatile FocusListener focusListener = null;
    private static volatile boolean lastFocusSeen = false;

    protected AppConnectorTerminalMode(CombiModuleAudio combimoduleaudio) {
        super(combimoduleaudio);
        boolean bl = CoverArtProviderMux.install(combimoduleaudio.getPictureManager(), this.pictureProvider, this);
        if (!bl) {
            this.logChannel.log(100000, "[AppConnectorTerminalMode] cover-art mux unavailable; direct push only");
        }
        combimoduleaudio.getPictureManager().setNotification(0);
    }

    AbstractCombiModule getCombiModule() {
        return (AbstractCombiModule) this.moduleFsg;
    }

    protected int getAudioApplication() {
        return 3;
    }

    protected String getAudioApplicationName() {
        return AppNameConst.APP_NAME_TERMINAL_MODE;
    }

    protected boolean isAudioApplicationInFocus() {
        return ((CombiModuleAudio) this.moduleFsg).getAudioApplicationFocusHandler().isTerminalModeInFocus();
    }

    public void updateActiveSource(int i, int j, int k, boolean flag, boolean flag1, int l) {
        CombiBAPAudioSource combibapaudiosource = new CombiBAPAudioSource();
        combibapaudiosource.setSourceType(i);
        combibapaudiosource.setSlotNumber(j);
        combibapaudiosource.setPartitionNumber(k);
        CombiBAPAudioListStates combibapaudioliststates = new CombiBAPAudioListStates();
        combibapaudioliststates.setMediaBrowserListAvailable(flag);
        combibapaudioliststates.setPresetListAvailable(flag1);
        combibapaudioliststates.setListState(l);
        this.updateActiveSource(combibapaudiosource, combibapaudioliststates);
    }

    public void updateCurrentStation(CombiBAPCurrentStationInfo stationInfo) {
        this.noteFocus();
        this.pushCoverArt(stationInfo);
        super.updateCurrentStation(stationInfo);
    }

    public void updateActiveInfoState(int i) {
    }

    private void noteFocus() {
        boolean bl;
        try {
            bl = this.isAudioApplicationInFocus();
        } catch (Throwable throwable) {
            return;
        }
        if (bl == lastFocusSeen) {
            return;
        }
        lastFocusSeen = bl;
    }

    public static void requestCoverClear() {
        clearRequested = true;
    }

    public static void armPushProbe() {
        pushDelivered = false;
        pushSkippedForFocus = false;
    }

    public static boolean pushWasDelivered() {
        return pushDelivered;
    }

    public static boolean pushSkippedForFocus() {
        return pushSkippedForFocus;
    }

    public static void setFocusListener(FocusListener focusListener) {
        AppConnectorTerminalMode.focusListener = focusListener;
    }

    private void pushCoverArt(CombiBAPCurrentStationInfo stationInfo) {
        if (!this.isAudioApplicationInFocus()) {
            pushSkippedForFocus = true;
            return;
        }
        String url = stationInfo.getPictureURL();
        boolean bl = url != null && url.length() > 0;
        boolean bl2 = clearRequested;
        clearRequested = false;
        if (!bl && !bl2) {
            return;
        }
        try {
            ResourceLocator resource = bl ? new ResourceLocator(stationInfo.getPictureID(), url) : null;
            this.pictureProvider.clearMapping();
            this.pictureProvider.addToMapping(stationInfo.getListRef(), resource);
            ((AbstractCombiModule) this.moduleFsg).getPictureManager()
                    .responseCoverArt(stationInfo.getListRef(), SOURCE_TYPE_VC, resource, true);
            pushDelivered = true;
        } catch (Exception e) {
            this.logChannel.log(1000000, "[AppConnectorTerminalMode#pushCoverArt] error: " + e.getMessage());
        }
    }

    public void updateSourceListTerminalMode(int[] aint, CombiBAPAudioSource[][] acombibapaudiosource) {
        this.logChannel.log(1000000, "[AppConnectorTerminalMode#updateSourceListTerminalMode] called (specified sourceTypes)");
        BAPFunctionArrayFSG bapfunctionarrayfsg = this.moduleFsg.getBAPFunctionArrayFSG(32);
        ((SourceListHandler) bapfunctionarrayfsg.getArrayHandler()).updateSources(3, aint, acombibapaudiosource);
    }

    public void updatePlayPosition(PlayPosition playposition) {
        this.logChannel.log(10000000, "[AppConnectorTerminalMode#updatePlayPosition] %1", playposition);
        if (this.isAudioApplicationInFocus()) {
            PlayPosition_Status playposition_status = new PlayPosition_Status();
            playposition_status.timePosition = playposition.getTimePosition().getTimeInSeconds();
            playposition_status.totalPlayTime = playposition.getTotalPlayTime().getTimeInSeconds();
            playposition_status.attributes.variableBitRateActive = playposition.isVariableBitRate();
            this.moduleFsg.getBAPFunctionPropertyFSG(52).sendStatusIfChanged(playposition_status);
        } else {
            this.logChannel.log(10000000, "[AppConnectorTerminalMode#updatePlayPosition] application not in focus; don't send playPosition");
        }
    }

    public static interface FocusListener {
        public void onAudioFocusGained();
    }

    static class CoverArtProvider implements IPictureProvider {
        private final AppConnectorTerminalMode owner;
        private final Map id2pictureURL = new java.util.HashMap();
        static int lastRequestedSourceType = -1;

        CoverArtProvider(AppConnectorTerminalMode owner) {
            this.owner = owner;
        }

        public void requestPicture(long l) {
            this.requestPicture(l, 255);
        }

        public void requestPicture(long l, int n) {
            lastRequestedSourceType = n;
            Long l2 = new Long(l);
            ResourceLocator resourceLocator = (ResourceLocator) this.id2pictureURL.get(l2);
            this.owner.getCombiModule().getPictureManager().responseCoverArt(l, n, resourceLocator, false);
        }

        boolean hasPicture(long l) {
            return this.id2pictureURL.containsKey(new Long(l));
        }

        public void addToMapping(long l, ResourceLocator resourceLocator) {
            this.id2pictureURL.put(new Long(l), resourceLocator);
        }

        public void clearMapping() {
            this.id2pictureURL.clear();
        }
    }
}
