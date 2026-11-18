package de.audi.app.combi.bap.app.kombipictures;
public interface IPictureManager {
    void registerPictureProvider(int i, IPictureProvider p);
    void setNotification(int i);
    Object responseCoverArt(long l, int i, Object r, boolean b);
}
