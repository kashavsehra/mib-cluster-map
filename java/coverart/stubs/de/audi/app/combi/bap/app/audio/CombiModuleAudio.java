package de.audi.app.combi.bap.app.audio;
import de.audi.app.combi.bap.app.kombipictures.IPictureManager;
import de.audi.app.bap.fw.functiontypes.BAPFunctionArrayFSG;
public class CombiModuleAudio extends de.audi.app.combi.bap.fw.AbstractCombiModule {
    public IPictureManager getPictureManager() { return null; }
    public AudioApplicationFocusHandler getAudioApplicationFocusHandler() { return new AudioApplicationFocusHandler(); }
    public BAPFunctionArrayFSG getBAPFunctionArrayFSG(int i) { return null; }
    public de.audi.app.combi.bap.fw.AbstractCombiModule.BAPFunctionPropertyFSG getBAPFunctionPropertyFSG(int i) { return null; }
    public static class AudioApplicationFocusHandler { public boolean isTerminalModeInFocus() { return true; } }
}
