package de.audi.app.combi.bap.fw;
import de.audi.app.combi.bap.app.kombipictures.IPictureManager;
import de.audi.app.bap.fw.functiontypes.BAPFunctionArrayFSG;
public class AbstractCombiModule {
    public IPictureManager getPictureManager() { return null; }
    public BAPFunctionArrayFSG getBAPFunctionArrayFSG(int i) { return null; }
    public BAPFunctionPropertyFSG getBAPFunctionPropertyFSG(int i) { return null; }
    public static class BAPFunctionPropertyFSG { public void sendStatusIfChanged(Object o) {} }
}
