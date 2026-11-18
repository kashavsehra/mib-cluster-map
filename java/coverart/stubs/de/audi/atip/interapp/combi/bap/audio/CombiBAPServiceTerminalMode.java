package de.audi.atip.interapp.combi.bap.audio;
import de.audi.atip.interapp.combi.bap.audio.data.CombiBAPCurrentStationInfo;
import de.audi.atip.interapp.combi.bap.audio.data.PlayPosition;
import de.audi.atip.interapp.combi.bap.audio.data.CombiBAPAudioSource;
public interface CombiBAPServiceTerminalMode {
    void updateActiveSource(Object a, Object b);
    void updateCurrentStation(CombiBAPCurrentStationInfo c);
    void updatePlayPosition(PlayPosition p);
    void updateActiveInfoState(int i);
    void updateSourceListTerminalMode(int[] a, CombiBAPAudioSource[][] b);
}
