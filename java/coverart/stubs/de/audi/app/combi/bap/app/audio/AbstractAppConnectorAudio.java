package de.audi.app.combi.bap.app.audio;
import de.audi.app.combi.bap.fw.AbstractCombiModule;
import de.audi.atip.log.LogChannel;
public class AbstractAppConnectorAudio {
    protected CombiModuleAudio moduleFsg;
    protected LogChannel logChannel;
    public AbstractAppConnectorAudio(CombiModuleAudio m) { this.moduleFsg = m; }
    public void updateActiveSource(Object a, Object b) {}
    public void updateCurrentStation(Object o) {}
}
