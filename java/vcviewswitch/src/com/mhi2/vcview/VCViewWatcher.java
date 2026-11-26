package com.mhi2.vcview;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Vector;

public class VCViewWatcher implements Runnable {
    private static boolean started;
    private static Object bundleContext;
    private static Class appClass;
    private static Object clusterService;
    private static Object naviCombiModule;
    private static boolean dumpedLayout;
    private static String lastSentView = "";

    public static void setAppClass(Class cls) {
        if (cls != null) appClass = cls;
    }

    public static void setBundleContext(Object bc) {
        if (bc != null) bundleContext = bc;
        start();
    }

    public static synchronized void start() {
        if (started) return;
        started = true;
        debugLog("VCViewWatcher.start() spawning background poll thread");
        Thread t = new Thread(new VCViewWatcher(), "VCViewWatcher");
        t.setDaemon(true);
        t.start();
    }

    public void run() {
        debugLog("VCViewWatcher thread running");
        while (true) {
            try {
                pollOnce();
            } catch (Throwable t) {
                StringBuffer sb = new StringBuffer("pollOnce threw: ");
                sb.append(t);
                debugLog(sb.toString());
            }
            try {
                Thread.sleep(250L);
            } catch (InterruptedException ie) {
                break;
            }
        }
    }

    private void pollOnce() {
        if (clusterService == null || naviCombiModule == null) {
            discoverServices();
        }
        if (!dumpedLayout && (clusterService != null || naviCombiModule != null)) {
            dumpedLayout = true;
            dumpServiceLayout("clusterService", clusterService);
            dumpServiceLayout("naviCombiModule", naviCombiModule);
            if (naviCombiModule != null) {
                try {
                    Method getProp = naviCombiModule.getClass().getMethod(
                        "getBAPFunctionPropertyFSG", new Class[]{int.class});
                    Object func54 = getProp.invoke(naviCombiModule, new Object[]{new Integer(54)});
                    if (func54 != null) {
                        Object status = func54.getClass().getMethod("getLastStatus", new Class[0])
                            .invoke(func54, new Object[0]);
                        dumpServiceLayout("naviFct54Status", status);
                    }
                } catch (Throwable t) {}
            }
        }

        String detected = detectViewFromNaviCombi();
        if (detected == null) {
            detected = detectViewFromClusterService();
        }
        if (detected != null && !detected.equals(lastSentView)) {
            lastSentView = detected;
            writeGalView(detected);
        }
    }

    private String detectViewFromNaviCombi() {
        if (naviCombiModule == null) return null;
        try {
            Method getProp = naviCombiModule.getClass().getMethod(
                "getBAPFunctionPropertyFSG", new Class[]{int.class});
            // BAP FctID 54 = MapPresentation on Audi Virtual Cockpit
            Object func54 = getProp.invoke(naviCombiModule, new Object[]{new Integer(54)});
            if (func54 != null) {
                Object status = func54.getClass().getMethod("getLastStatus", new Class[0])
                    .invoke(func54, new Object[0]);
                if (status != null) {
                    String mapped = mapStatusObjectToView(status);
                    if (mapped != null) return mapped;
                }
            }
        } catch (Throwable t) {}
        return null;
    }

    private String detectViewFromClusterService() {
        if (clusterService == null) return null;
        return mapStatusObjectToView(clusterService);
    }

    private String mapStatusObjectToView(Object target) {
        if (target == null) return null;
        Class cls = target.getClass();
        while (cls != null && !"java.lang.Object".equals(cls.getName())) {
            Field[] fields = cls.getDeclaredFields();
            for (int i = 0; i < fields.length; i++) {
                try {
                    fields[i].setAccessible(true);
                    String name = fields[i].getName().toLowerCase();
                    Object val = fields[i].get(target);
                    if (val instanceof Boolean) {
                        boolean b = ((Boolean) val).booleanValue();
                        if (name.indexOf("largemap") >= 0 || name.indexOf("fullmap") >= 0 ||
                            name.indexOf("expanded") >= 0 || name.indexOf("widescreen") >= 0) {
                            return b ? "large" : "classic";
                        }
                    } else if (val instanceof Integer) {
                        int iv = ((Integer) val).intValue();
                        if (name.indexOf("presentation") >= 0 || name.indexOf("viewsize") >= 0 ||
                            name.indexOf("mapview") >= 0) {
                            // 0 = normal/classic, 1 = large/expanded, 2 = sport (or vice versa logged in debug)
                            if (iv == 1) return "large";
                            if (iv == 2) return "sport";
                            if (iv == 0) return "classic";
                        }
                        if (name.indexOf("preset") >= 0) {
                            if (iv == 1) return "sport";
                            if (iv == 0) return "classic";
                        }
                    }
                } catch (Throwable t) {}
            }
            cls = cls.getSuperclass();
        }
        return null;
    }

    private void writeGalView(String view) {
        try {
            File tmp = new File("/tmp/gal_view.tmp");
            File dst = new File("/tmp/gal_view");
            FileOutputStream fos = new FileOutputStream(tmp);
            StringBuffer line = new StringBuffer(view);
            line.append('\n');
            fos.write(line.toString().getBytes("US-ASCII"));
            fos.close();
            if (!tmp.renameTo(dst)) {
                FileOutputStream fos2 = new FileOutputStream(dst);
                fos2.write(line.toString().getBytes("US-ASCII"));
                fos2.close();
                tmp.delete();
            }
            StringBuffer sb = new StringBuffer("VCViewWatcher: wrote view=");
            sb.append(view);
            sb.append(" to /tmp/gal_view");
            debugLog(sb.toString());
        } catch (Throwable t) {
            StringBuffer sb = new StringBuffer("VCViewWatcher: writeGalView failed: ");
            sb.append(t);
            debugLog(sb.toString());
        }
    }

    private void discoverServices() {
        Object bc = getOrFindBundleContext();
        if (bc == null) return;
        try {
            Method getSR = bc.getClass().getMethod(
                "getServiceReferences", new Class[]{String.class, String.class});
            Object[] refs = (Object[]) getSR.invoke(bc, new Object[]{null, null});
            if (refs == null) return;
            Method getService = findMethod1(bc, "getService");
            Method ungetService = findMethod1(bc, "ungetService");
            Vector visited = new Vector();
            for (int i = 0; i < refs.length; i++) {
                if (refs[i] == null) continue;
                Object svc;
                try {
                    svc = getService.invoke(bc, new Object[]{refs[i]});
                } catch (Throwable t) {
                    continue;
                }
                if (svc == null) continue;
                boolean kept = tryStoreService(svc);
                if (!kept) {
                    Vector queue = new Vector();
                    queue.addElement(svc);
                    for (int depth = 0; depth < 2 && !queue.isEmpty(); depth++) {
                        Vector next = new Vector();
                        for (int j = 0; j < queue.size(); j++) {
                            Object o = queue.elementAt(j);
                            if (o == null) continue;
                            boolean seen = false;
                            for (int k = 0; k < visited.size(); k++) {
                                if (visited.elementAt(k) == o) { seen = true; break; }
                            }
                            if (seen) continue;
                            visited.addElement(o);
                            if (tryStoreService(o)) { kept = true; continue; }
                            try {
                                Field[] fs = o.getClass().getDeclaredFields();
                                for (int f = 0; f < fs.length; f++) {
                                    try {
                                        fs[f].setAccessible(true);
                                        Object v = fs[f].get(o);
                                        if (v != null) next.addElement(v);
                                    } catch (Throwable t) {}
                                }
                            } catch (Throwable t) {}
                        }
                        queue = next;
                    }
                }
                if (!kept && ungetService != null) {
                    try { ungetService.invoke(bc, new Object[]{refs[i]}); } catch (Throwable t) {}
                }
            }
        } catch (Throwable t) {}
    }

        // Fallback: ClusterService static instance field (matching AAManeuverProvider)
        if (clusterService == null) {
            try {
                Class cls = forName("de.audi.tghu.navi.app.cluster.ClusterService");
                Class c = cls;
                while (c != null) {
                    try {
                        Field f = c.getDeclaredField("instance");
                        f.setAccessible(true);
                        Object inst = f.get(null);
                        if (inst != null) {
                            clusterService = inst;
                            debugLog("VCViewWatcher: discovered ClusterService via static instance");
                            break;
                        }
                    } catch (NoSuchFieldException e) {
                        c = c.getSuperclass();
                    }
                }
            } catch (Throwable t) {}
        }


    
    private boolean tryStoreService(Object obj) {
        if (obj == null) return false;
        String name = obj.getClass().getName();
        if ("de.audi.tghu.navi.app.cluster.ClusterService".equals(name)) {
            clusterService = obj;
            debugLog("VCViewWatcher: discovered ClusterService");
            return true;
        }
        if (name.indexOf("CombiModule") >= 0 &&
            (name.toLowerCase().indexOf("navi") >= 0 || name.toLowerCase().indexOf("kombi") >= 0)) {
            naviCombiModule = obj;
            StringBuffer sb = new StringBuffer("VCViewWatcher: discovered ");
            sb.append(name);
            debugLog(sb.toString());
            return true;
        }
        return false;
    }

    private Object getOrFindBundleContext() {
        if (bundleContext != null) return bundleContext;
        try {
            Class fuCls = Class.forName("org.osgi.framework.FrameworkUtil");
            Method getBundle = fuCls.getMethod("getBundle", new Class[]{Class.class});
Class[] candidates = new Class[]{
appClass,
forName("de.audi.tghu.navi.app.cluster.ClusterService"),
forName("de.audi.app.combi.bap.app.audio.CombiModuleAudio")
};
for (int i = 0; i < candidates.length; i++) {
if (candidates[i] == null) continue;
Object b = getBundle.invoke(null, new Object[]{candidates[i]});
if (b != null) {
Object bc = b.getClass().getMethod("getBundleContext", new Class[0])
.invoke(b, new Object[0]);
if (bc != null) {
bundleContext = bc;
return bc;
}
}
}
} catch (Throwable t) {}
return null;
}
private static Class forName(String name) {
try {
if (appClass != null && appClass.getClassLoader() != null) {
return Class.forName(name, true, appClass.getClassLoader());
}
} catch (Throwable t) {}
try {
return Class.forName(name);
} catch (Throwable t) {
return null;
}
}
private static Method findMethod1(Object target, String name) {
Method[] methods = target.getClass().getMethods();
for (int i = 0; i < methods.length; i++) {
if (name.equals(methods[i].getName()) && methods[i].getParameterTypes().length == 1) {
return methods[i];
}
}
return null;
}
private void dumpServiceLayout(String label, Object target) {
if (target == null || !new File("/tmp/aa_debug").exists()) return;
StringBuffer sb = new StringBuffer("LAYOUT ");
sb.append(label);
sb.append(" class=");
sb.append(target.getClass().getName());
sb.append(" fields=[");
Class cls = target.getClass();
while (cls != null && !"java.lang.Object".equals(cls.getName())) {
Field[] fs = cls.getDeclaredFields();
for (int i = 0; i < fs.length; i++) {
try {
fs[i].setAccessible(true);
sb.append(fs[i].getName());
sb.append('=');
sb.append(fs[i].get(target));
sb.append(',');
} catch (Throwable t) {}
}
cls = cls.getSuperclass();
}
sb.append(']');
debugLog(sb.toString());
}
public static void debugLog(String msg) {
if (!new File("/tmp/aa_debug").exists()) return;
try {
SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");
StringBuffer sb = new StringBuffer();
sb.append(sdf.format(new Date()));
sb.append(" VCViewWatcher: ");
sb.append(msg);
FileWriter fw = new FileWriter("/tmp/vcviewswitch.log", true);
PrintWriter pw = new PrintWriter(fw);
pw.println(sb.toString());
pw.close();
} catch (Throwable t) {}
}
}
