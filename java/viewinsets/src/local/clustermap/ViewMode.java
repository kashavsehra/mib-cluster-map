package local.clustermap;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class ViewMode {
    private static final int MODEL_ID = 402521;
    static String statePath = "/tmp/clustermap-view-mode";
    private static String lastMode;

    private ViewMode() {
    }

    public static void sync(Object controller, Object event) {
        try {
            if (event != null) {
                Method getModelId = event.getClass().getMethod("getModelId", new Class[0]);
                getModelId.setAccessible(true);
                Object eventModelId = getModelId.invoke(event, new Object[0]);
                if (!(eventModelId instanceof Integer) ||
                    ((Integer) eventModelId).intValue() != MODEL_ID) return;
            }

            Field serviceField = findField(controller.getClass(), "hmiService");
            serviceField.setAccessible(true);
            Object service = serviceField.get(null);
            if (service == null) throw new IllegalStateException("hmiService is null");

            Method getModel = service.getClass().getMethod(
                "getModel", new Class[] { Integer.TYPE });
            getModel.setAccessible(true);
            Object model = getModel.invoke(service, new Object[] { new Integer(MODEL_ID) });
            if (model == null) throw new IllegalStateException("View-size model is null");

            Method getValue = model.getClass().getMethod("getValue", new Class[0]);
            getValue.setAccessible(true);
            Object choice = getValue.invoke(model, new Object[0]);
            if (!(choice instanceof Integer)) {
                throw new IllegalStateException("View-size choice is not an integer");
            }

            int value = ((Integer) choice).intValue();
            String mode;
            if (value == 1) mode = "classic";
            else if (value == 0) mode = "large";
            else {
                StringBuffer message = new StringBuffer("Unknown view-size choice ");
                message.append(value);
                throw new IllegalStateException(message.toString());
            }
            writeMode(mode);
        } catch (NoSuchFieldException e) {
            report("hmiService field not found", e);
        } catch (NoSuchMethodException e) {
            report("HMI view model method not found", e);
        } catch (IllegalAccessException e) {
            report("HMI view model is not accessible", e);
        } catch (InvocationTargetException e) {
            report("HMI view model call failed", e);
        } catch (IOException e) {
            report("Could not publish view mode", e);
        } catch (SecurityException e) {
            report("HMI view model access was denied", e);
        } catch (IllegalStateException e) {
            report("HMI view mode is unavailable", e);
        }
    }

    private static Field findField(Class type, String name) throws NoSuchFieldException {
        Class current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static void writeMode(String mode) throws IOException {
        if (mode.equals(lastMode)) return;

        File target = new File(statePath);
        StringBuffer temporaryPath = new StringBuffer(statePath);
        temporaryPath.append(".new");
        File temporary = new File(temporaryPath.toString());
        FileOutputStream output = new FileOutputStream(temporary, false);
        try {
            StringBuffer data = new StringBuffer(mode);
            data.append('\n');
            output.write(data.toString().getBytes("US-ASCII"));
        } finally {
            output.close();
        }
        if (!temporary.renameTo(target)) {
            temporary.delete();
            StringBuffer message = new StringBuffer("Could not atomically replace ");
            message.append(statePath);
            throw new IOException(message.toString());
        }
        lastMode = mode;
        StringBuffer message = new StringBuffer("[ClusterMapViewInsets] mode=");
        message.append(mode);
        System.err.println(message.toString());
    }

    private static void report(String message, Exception error) {
        StringBuffer output = new StringBuffer("[ClusterMapViewInsets] ");
        output.append(message);
        output.append(": ");
        output.append(error.toString());
        System.err.println(output.toString());
    }
}
