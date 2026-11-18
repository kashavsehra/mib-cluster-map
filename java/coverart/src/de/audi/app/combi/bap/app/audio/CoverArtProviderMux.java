package de.audi.app.combi.bap.app.audio;

import de.audi.app.combi.bap.app.kombipictures.IPictureManager;
import de.audi.app.combi.bap.app.kombipictures.IPictureProvider;
import java.lang.reflect.Field;
import java.util.Map;

public final class CoverArtProviderMux implements IPictureProvider {
    private static final int PICTURE_TYPE_COVER_ART = 0;
    private final IPictureProvider stockMedia;
    private final AppConnectorTerminalMode.CoverArtProvider carPlay;
    private final AppConnectorTerminalMode owner;

    private CoverArtProviderMux(IPictureProvider iPictureProvider, AppConnectorTerminalMode.CoverArtProvider coverArtProvider, AppConnectorTerminalMode appConnectorTerminalMode) {
        this.stockMedia = iPictureProvider;
        this.carPlay = coverArtProvider;
        this.owner = appConnectorTerminalMode;
    }

    public static boolean install(IPictureManager iPictureManager, AppConnectorTerminalMode.CoverArtProvider coverArtProvider) {
        if (iPictureManager == null || coverArtProvider == null) {
            return false;
        }
        IPictureProvider iPictureProvider = CoverArtProviderMux.findProvider(iPictureManager, 0);
        if (iPictureProvider == null) {
            return false;
        }
        if (iPictureProvider instanceof CoverArtProviderMux) {
            return true;
        }
        iPictureManager.registerPictureProvider(0, new CoverArtProviderMux(iPictureProvider, coverArtProvider, null));
        return true;
    }

    public static boolean install(IPictureManager iPictureManager, AppConnectorTerminalMode.CoverArtProvider coverArtProvider, AppConnectorTerminalMode appConnectorTerminalMode) {
        if (iPictureManager == null || coverArtProvider == null) {
            return false;
        }
        IPictureProvider iPictureProvider = CoverArtProviderMux.findProvider(iPictureManager, 0);
        if (iPictureProvider == null) {
            return false;
        }
        if (iPictureProvider instanceof CoverArtProviderMux) {
            return true;
        }
        iPictureManager.registerPictureProvider(0, new CoverArtProviderMux(iPictureProvider, coverArtProvider, appConnectorTerminalMode));
        return true;
    }

    private static IPictureProvider findProvider(IPictureManager iPictureManager, int n) {
        for (Class<?> clazz = iPictureManager.getClass(); clazz != null; clazz = clazz.getSuperclass()) {
            try {
                Field field = clazz.getDeclaredField("pictureProviders");
                field.setAccessible(true);
                Object object = field.get(iPictureManager);
                if (!(object instanceof Map)) {
                    return null;
                }
                Object v = ((Map) object).get(new Integer(n));
                return v instanceof IPictureProvider ? (IPictureProvider) v : null;
            } catch (NoSuchFieldException noSuchFieldException) {
                continue;
            } catch (Throwable throwable) {
                return null;
            }
        }
        return null;
    }

    private boolean isOurs(long l) {
        if (!this.carPlay.hasPicture(l)) {
            return false;
        }
        try {
            if (this.owner != null) {
                return this.owner.isAudioApplicationInFocus();
            }
            return true;
        } catch (Throwable throwable) {
            return true;
        }
    }

    public void requestPicture(long l) {
        if (this.isOurs(l)) {
            this.carPlay.requestPicture(l);
        } else {
            this.stockMedia.requestPicture(l);
        }
    }

    public void requestPicture(long l, int n) {
        if (this.isOurs(l)) {
            this.carPlay.requestPicture(l, n);
        } else {
            this.stockMedia.requestPicture(l, n);
        }
    }
}
