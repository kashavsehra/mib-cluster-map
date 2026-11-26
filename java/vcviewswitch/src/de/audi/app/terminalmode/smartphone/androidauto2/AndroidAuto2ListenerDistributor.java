package de.audi.app.terminalmode.smartphone.androidauto2;

import de.audi.app.terminalmode.IContext;
import com.mhi2.vcview.VCViewWatcher;
import java.lang.reflect.Method;
import java.util.Vector;
import org.dsi.ifc.androidauto2.DSIAndroidAuto2Listener;
import org.dsi.ifc.androidauto2.TrackData;
import org.dsi.ifc.androidauto2.PlaybackInfo;
import org.dsi.ifc.androidauto2.CallState;
import org.dsi.ifc.androidauto2.TelephonyState;
import org.dsi.ifc.global.ResourceLocator;

public class AndroidAuto2ListenerDistributor implements DSIAndroidAuto2Listener {
    private Vector listeners;
    private IContext context;
    private static Object subscribedDsi;

    public AndroidAuto2ListenerDistributor(IContext context) {
        this.context = context;
        this.listeners = new Vector();
        VCViewWatcher.setAppClass(this.getClass());
        invokeBridge("setAppClass", new Class[]{Class.class}, new Object[]{this.getClass()});
        try {
            Method getBC = context.getClass().getMethod("getBundleContext", new Class[0]);
            Object bc = getBC.invoke(context, new Object[0]);
            if (bc != null) {
                VCViewWatcher.setBundleContext(bc);
                invokeBridge("setBundleContext", new Class[]{Object.class}, new Object[]{bc});
            }
        } catch (Throwable t) {}
        VCViewWatcher.start();
        discoverReferences(context);
    }

    public void addListener(DSIAndroidAuto2Listener[] listeners) {
        if (listeners != null) {
            for (int i = 0; i < listeners.length; i++) {
                if (listeners[i] != null) {
                    discoverReferences(listeners[i]);
                    VCViewWatcher.setAppClass(listeners[i].getClass());
                    invokeBridge("setAppClass", new Class[]{Class.class}, new Object[]{listeners[i].getClass()});
                    this.listeners.add(listeners[i]);
                }
            }
        }
    }

    private void discoverReferences(Object start) {
        if (start == null) return;
        Vector current = new Vector();
        Vector next = new Vector();
        Vector visited = new Vector();
        current.addElement(start);
        int depth = 0;
        while (!current.isEmpty() && depth < 6) {
            for (int i = 0; i < current.size(); i++) {
                Object obj = current.elementAt(i);
                if (obj == null) continue;
                String name = obj.getClass().getName();
                if (name.startsWith("java.lang.") || obj.getClass().isPrimitive()) continue;
                boolean seen = false;
                for (int j = 0; j < visited.size(); j++) {
                    if (visited.elementAt(j) == obj) { seen = true; break; }
                }
                if (seen) continue;
                visited.addElement(obj);
                checkAndStore(obj);
                walkFields(obj, next);
            }
            current = next;
            next = new Vector();
            depth++;
        }
    }

    private void checkAndStore(Object obj) {
        if (obj == null) return;
        String clsName = obj.getClass().getName();
        try {
            Class dsiCls = Class.forName("org.dsi.ifc.androidauto2.DSIAndroidAuto2");
            if (dsiCls.isInstance(obj)) {
                if (subscribedDsi != obj) {
                    subscribedDsi = obj;
                    Class dsiListenerCls = Class.forName("org.dsi.ifc.base.DSIListener");
                    Method setNotif = dsiCls.getMethod("setNotification", new Class[]{int[].class, dsiListenerCls});
                    int[] attrs = new int[]{
                        dsiCls.getField("ATTR_NOWPLAYINGDATA").getInt(null),
                        dsiCls.getField("ATTR_PLAYBACKSTATE").getInt(null),
                        dsiCls.getField("ATTR_COVERARTURL").getInt(null),
                        dsiCls.getField("ATTR_PLAYPOSITION").getInt(null)
                    };
                    setNotif.invoke(obj, new Object[]{attrs, this});
                }
                invokeBridge("setDSI", new Class[]{Object.class}, new Object[]{obj});
            }
        } catch (Throwable t) {}

        if ("de.audi.app.combi.bap.app.audio.CombiModuleAudio".equals(clsName)) {
            invokeBridge("setCombiModule", new Class[]{Object.class}, new Object[]{obj});
            invokeBridge("setMediaConnector", new Class[]{Object.class}, new Object[]{obj});
        }
        if (implementsInterface(obj, "org.osgi.framework.BundleContext")) {
            VCViewWatcher.setBundleContext(obj);
            invokeBridge("setBundleContext", new Class[]{Object.class}, new Object[]{obj});
        }

        if ("de.audi.tghu.navi.app.cluster.ClusterService".equals(clsName) ||
            clsName.indexOf("CombiModule") >= 0) {
            VCViewWatcher.setServiceObject(obj); // (if you add a setter in VCViewWatcher)
        }
       
    }

    private boolean implementsInterface(Object obj, String ifaceName) {
        Class cls = obj.getClass();
        while (cls != null) {
            Class[] ifaces = cls.getInterfaces();
            for (int i = 0; i < ifaces.length; i++) {
                if (ifaceName.equals(ifaces[i].getName())) return true;
            }
            cls = cls.getSuperclass();
        }
        return false;
    }

    private void walkFields(Object obj, Vector next) {
        try {
            Class cls = obj.getClass();
            while (cls != null) {
                java.lang.reflect.Field[] fields = cls.getDeclaredFields();
                for (int i = 0; i < fields.length; i++) {
                    try {
                        fields[i].setAccessible(true);
                        Object val = fields[i].get(obj);
                        if (val != null) next.addElement(val);
                    } catch (Throwable t) {}
                }
                cls = cls.getSuperclass();
            }
        } catch (Throwable t) {}
    }

    private static void invokeBridge(String method, Class[] types, Object[] args) {
        try {
            Class bridge = Class.forName("com.mhi2.androidadio.AAJavaBridge");
            Method m = bridge.getMethod(method, types);
            m.invoke(null, args);
        } catch (Throwable ignored) {}
    }

    public void updateNowPlayingData(TrackData a, int b) {
        invokeBridge("onNowPlayingData", new Class[]{TrackData.class, int.class}, new Object[]{a, new Integer(b)});
        for (int i = 0; i < listeners.size(); i++) {
            DSIAndroidAuto2Listener l = (DSIAndroidAuto2Listener) listeners.elementAt(i);
            if (l != null) try { l.updateNowPlayingData(a, b); } catch (Throwable t) {}
        }
    }
    public void updateCoverArtUrl(ResourceLocator a, int b) {
        invokeBridge("onCoverArtUrl", new Class[]{ResourceLocator.class, int.class}, new Object[]{a, new Integer(b)});
        for (int i = 0; i < listeners.size(); i++) {
            DSIAndroidAuto2Listener l = (DSIAndroidAuto2Listener) listeners.elementAt(i);
            if (l != null) try { l.updateCoverArtUrl(a, b); } catch (Throwable t) {}
        }
    }
    public void updateNavigationNextTurnEvent(String a, int b, int c, int d, int e, int f) {
        invokeBridge("onNextTurnEvent", new Class[]{String.class, int.class, int.class, int.class, int.class, int.class},
            new Object[]{a, new Integer(b), new Integer(c), new Integer(d), new Integer(e), new Integer(f)});
        for (int i = 0; i < listeners.size(); i++) {
            DSIAndroidAuto2Listener l = (DSIAndroidAuto2Listener) listeners.elementAt(i);
            if (l != null) try { l.updateNavigationNextTurnEvent(a, b, c, d, e, f); } catch (Throwable t) {}
        }
    }
    public void updateNavigationNextTurnDistance(int a, int b, int c) {
        invokeBridge("onNextTurnDistance", new Class[]{int.class, int.class, int.class},
            new Object[]{new Integer(a), new Integer(b), new Integer(c)});
        for (int i = 0; i < listeners.size(); i++) {
            DSIAndroidAuto2Listener l = (DSIAndroidAuto2Listener) listeners.elementAt(i);
            if (l != null) try { l.updateNavigationNextTurnDistance(a, b, c); } catch (Throwable t) {}
        }
    }
    public void updatePlayposition(int a, int b) {
        invokeBridge("onPlayPosition", new Class[]{int.class, int.class}, new Object[]{new Integer(a), new Integer(b)});
        for (int i = 0; i < listeners.size(); i++) {
            DSIAndroidAuto2Listener l = (DSIAndroidAuto2Listener) listeners.elementAt(i);
            if (l != null) try { l.updatePlayposition(a, b); } catch (Throwable t) {}
        }
    }
    public void videoFocusRequestNotification(int a, int b) {
        for (int i = 0; i < listeners.size(); i++) {
            DSIAndroidAuto2Listener l = (DSIAndroidAuto2Listener) listeners.elementAt(i);
            if (l != null) try { l.videoFocusRequestNotification(a, b); } catch (Throwable t) {}
        }
    }
    public void videoAvailable(boolean a, int b) {
        for (int i = 0; i < listeners.size(); i++) {
            DSIAndroidAuto2Listener l = (DSIAndroidAuto2Listener) listeners.elementAt(i);
            if (l != null) try { l.videoAvailable(a, b); } catch (Throwable t) {}
        }
    }
    public void audioFocusRequestNotification(int a, int b) {
        for (int i = 0; i < listeners.size(); i++) {
            DSIAndroidAuto2Listener l = (DSIAndroidAuto2Listener) listeners.elementAt(i);
            if (l != null) try { l.audioFocusRequestNotification(a, b); } catch (Throwable t) {}
        }
    }
    public void audioAvailable(int a, boolean b, int c) {
        for (int i = 0; i < listeners.size(); i++) {
            DSIAndroidAuto2Listener l = (DSIAndroidAuto2Listener) listeners.elementAt(i);
          if (l != null) try { l.audioAvailable(a, b, c); } catch (Throwable t) {}
}
}
public void voiceSessionNotification(int a, int b) {
for (int i = 0; i < listeners.size(); i++) {
DSIAndroidAuto2Listener l = (DSIAndroidAuto2Listener) listeners.elementAt(i);
if (l != null) try { l.voiceSessionNotification(a, b); } catch (Throwable t) {}
}
}
public void microphoneRequestNotification(int a, int b) {
for (int i = 0; i < listeners.size(); i++) {
DSIAndroidAuto2Listener l = (DSIAndroidAuto2Listener) listeners.elementAt(i);
if (l != null) try { l.microphoneRequestNotification(a, b); } catch (Throwable t) {}
}
}
public void navFocusRequestNotification(int a, int b) {
for (int i = 0; i < listeners.size(); i++) {
DSIAndroidAuto2Listener l = (DSIAndroidAuto2Listener) listeners.elementAt(i);
if (l != null) try { l.navFocusRequestNotification(a, b); } catch (Throwable t) {}
}
}
public void updateCallState(CallState[] a, int b) {
for (int i = 0; i < listeners.size(); i++) {
DSIAndroidAuto2Listener l = (DSIAndroidAuto2Listener) listeners.elementAt(i);
if (l != null) try { l.updateCallState(a, b); } catch (Throwable t) {}
}
}
public void updateTelephonyState(TelephonyState a, int b) {
for (int i = 0; i < listeners.size(); i++) {
DSIAndroidAuto2Listener l = (DSIAndroidAuto2Listener) listeners.elementAt(i);
if (l != null) try { l.updateTelephonyState(a, b); } catch (Throwable t) {}
}
}
public void updatePlaybackState(PlaybackInfo a, int b) {
for (int i = 0; i < listeners.size(); i++) {
DSIAndroidAuto2Listener l = (DSIAndroidAuto2Listener) listeners.elementAt(i);
if (l != null) try { l.updatePlaybackState(a, b); } catch (Throwable t) {}
}
}
public void setExternalDestination(double a, double b, String c, String d, int e) {
for (int i = 0; i < listeners.size(); i++) {
DSIAndroidAuto2Listener l = (DSIAndroidAuto2Listener) listeners.elementAt(i);
if (l != null) try { l.setExternalDestination(a, b, c, d, e); } catch (Throwable t) {}
}
}
public void bluetoothPairingRequest(String a, int b) {
for (int i = 0; i < listeners.size(); i++) {
DSIAndroidAuto2Listener l = (DSIAndroidAuto2Listener) listeners.elementAt(i);
if (l != null) try { l.bluetoothPairingRequest(a, b); } catch (Throwable t) {}
}
}
public void asyncException(int a, String b, int c) {
for (int i = 0; i < listeners.size(); i++) {
DSIAndroidAuto2Listener l = (DSIAndroidAuto2Listener) listeners.elementAt(i);
if (l != null) try { l.asyncException(a, b, c); } catch (Throwable t) {}
}
}
}
