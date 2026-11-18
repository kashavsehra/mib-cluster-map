/*
 * Decompiled with CFR 0.152.
 */
package com.mhi2.carplay.framework;

import com.mhi2.carplay.framework.Log;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Vector;

public class CarplayBus {
    private static final String TAG = "CarplayBus";
    public static final String HOST = "127.0.0.1";
    public static final int PORT = 19810;
    public static final int MAGIC = 1129334850;
    public static final int HEADER_SIZE = 16;
    public static final int MAX_PAYLOAD = 131072;
    public static final int FLAG_STICKY = 1;
    public static final int FLAG_BINARY = 2;
    public static final int FLAG_REPLAY = 4;
    public static final int EVT_HELLO = 1;
    public static final int EVT_SYNC_BEGIN = 2;
    public static final int EVT_SYNC_END = 3;
    public static final int EVT_PONG = 4;
    public static final int EVT_CLIENT_ROLE = 5;
    public static final int EVT_COVERART = 16;
    public static final int EVT_COVERART_NONE = 17;
    public static final int EVT_RGD_UPDATE = 32;
    public static final int EVT_DEVICE_STATE = 48;
    public static final int EVT_TEL_LISTS = 64;
    public static final int EVT_TEL_COMM = 65;
    public static final int CMD_SYNC_REQ = 256;
    public static final int CMD_PING = 257;
    public static final int CMD_TEL = 279;
    private static final CarplayBus INSTANCE = new CarplayBus();
    private final Object lock = new Object();
    private volatile boolean running;
    private Thread ioThread;
    private ServerSocket serverSocket;
    private final Vector clients = new Vector();
    private Socket cmdSocket;
    private final Object writeLock = new Object();
    private int txSeq;
    private static final int MAX_TYPES = 1024;
    private final Listener[] listeners = new Listener[1024];

    public static Data parseText(byte[] byArray, int n) {
        String string;
        Data data = new Data();
        if (byArray == null || n <= 0) {
            return data;
        }
        try {
            string = new String(byArray, 0, n, "UTF-8");
        }
        catch (Exception exception) {
            string = new String(byArray, 0, n);
        }
        int n2 = 0;
        while (n2 < string.length()) {
            int n3;
            int n4 = string.indexOf(10, n2);
            if (n4 < 0) {
                n4 = string.length();
            }
            String string2 = string.substring(n2, n4);
            n2 = n4 + 1;
            if (string2.length() == 0 || string2.charAt(0) == '@' || (n3 = string2.indexOf(58)) < 0) continue;
            int n5 = string2.indexOf(58, n3 + 1);
            String string3 = string2.substring(0, n3);
            String string4 = n5 >= 0 && n5 + 1 <= string2.length() ? string2.substring(n5 + 1) : "";
            data.put(string3, string4);
        }
        return data;
    }

    public static CarplayBus getInstance() {
        return INSTANCE;
    }

    private CarplayBus() {
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void start() {
        Object object = this.lock;
        synchronized (object) {
            if (this.running) {
                return;
            }
            this.running = true;
            this.ioThread = new Thread(new Runnable(){

                public void run() {
                    CarplayBus.this.ioLoop();
                }
            }, "CarplayBus-IO");
            this.ioThread.setDaemon(true);
            this.ioThread.start();
        }
        Log.i(TAG, "CarplayBus started");
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void stop() {
        Object object = this.lock;
        synchronized (object) {
            if (!this.running) {
                return;
            }
            this.running = false;
            this.closeAllClientsLocked("stopping");
            if (this.serverSocket != null) {
                try {
                    this.serverSocket.close();
                }
                catch (Exception exception) {
                    // empty catch block
                }
            }
            this.lock.notifyAll();
        }
        if (this.ioThread != null) {
            try {
                this.ioThread.join(1000L);
            }
            catch (InterruptedException interruptedException) {
                // empty catch block
            }
            this.ioThread = null;
        }
        Log.i(TAG, "CarplayBus stopped");
    }

    public void flush(long l) {
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public boolean isConnected() {
        Object object = this.lock;
        synchronized (object) {
            return !this.clients.isEmpty();
        }
    }

    private void reapClosedClientsLocked() {
        int n = 0;
        for (int i = this.clients.size() - 1; i >= 0; --i) {
            Socket socket = (Socket)this.clients.elementAt(i);
            if (!socket.isClosed()) continue;
            this.clients.removeElementAt(i);
            ++n;
        }
        if (n > 0) {
            Log.i(TAG, "reaped " + n + " closed client(s), " + this.clients.size() + " left");
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public int clientCount() {
        Object object = this.lock;
        synchronized (object) {
            return this.clients.size();
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void on(int n, Listener listener) {
        if (n < 0 || n >= 1024) {
            Log.w(TAG, "on: type 0x" + Integer.toHexString(n) + " out of range");
            return;
        }
        Object object = this.lock;
        synchronized (object) {
            this.listeners[n] = listener;
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void off(int n) {
        if (n < 0 || n >= 1024) {
            return;
        }
        Object object = this.lock;
        synchronized (object) {
            this.listeners[n] = null;
        }
    }

    public void send(int n, int n2, byte[] byArray, int n3) {
        this.sendTo(n, n2, byArray, n3);
    }

    public boolean sendBinary(int n, byte[] byArray) {
        return this.sendTo(n, 2, byArray, byArray != null ? byArray.length : 0);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private boolean sendTo(int n, int n2, byte[] byArray, int n3) {
        Socket socket;
        Object object = this.lock;
        synchronized (object) {
            socket = this.cmdSocket;
        }
        if (socket == null) {
            Log.w(TAG, "send type=0x" + Integer.toHexString(n) + ": mm-ipod not connected");
            return false;
        }
        byte[] packet = new byte[16 + n3];
        Object object2 = this.writeLock;
        synchronized (object2) {
            CarplayBus.putBE32(packet, 0, 1129334850);
            CarplayBus.putBE32(packet, 4, ++this.txSeq);
            packet[8] = (byte)(n >> 8);
            packet[9] = (byte)n;
            packet[10] = (byte)n2;
            packet[11] = 0;
            CarplayBus.putBE32(packet, 12, n3);
            if (n3 > 0) {
                System.arraycopy(byArray, 0, packet, 16, n3);
            }
            try {
                OutputStream outputStream = socket.getOutputStream();
                outputStream.write(packet);
                outputStream.flush();
                return true;
            }
            catch (IOException iOException) {
                Log.w(TAG, "send type=0x" + Integer.toHexString(n) + " failed: " + iOException.getMessage());
                return false;
            }
        }
    }

    private static void putBE32(byte[] byArray, int n, int n2) {
        byArray[n] = (byte)(n2 >> 24);
        byArray[n + 1] = (byte)(n2 >> 16);
        byArray[n + 2] = (byte)(n2 >> 8);
        byArray[n + 3] = (byte)n2;
    }

    public void sendText(int n, int n2, String string) {
        this.send(n, n2 & 0xFFFFFFFD, null, 0);
    }

    public void sendBare(int n, int n2) {
        this.send(n, n2, null, 0);
    }

    private void ioLoop() {
        if (!this.openServerSocket()) {
            Log.e(TAG, "ServerSocket bind failed; bus disabled");
            return;
        }
        while (this.running) {
            if (this.acceptOne()) continue;
            CarplayBus.sleepMs(500);
        }
        try {
            if (this.serverSocket != null) {
                this.serverSocket.close();
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
        this.serverSocket = null;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private boolean openServerSocket() {
        try {
            ServerSocket serverSocket = new ServerSocket();
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress(HOST, 19810));
            Object object = this.lock;
            synchronized (object) {
                this.serverSocket = serverSocket;
            }
            Log.i(TAG, "listening on 127.0.0.1:19810");
            return true;
        }
        catch (IOException iOException) {
            Log.e(TAG, "ServerSocket bind 127.0.0.1:19810 failed: " + iOException.getMessage());
            return false;
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private boolean acceptOne() {
        ServerSocket serverSocket;
        Object object = this.lock;
        synchronized (object) {
            serverSocket = this.serverSocket;
            if (serverSocket == null) {
                return false;
            }
        }
        try {
            object = serverSocket.accept();
            ((Socket)object).setTcpNoDelay(true);
            ((Socket)object).setKeepAlive(true);
            InputStream inputStream = ((Socket)object).getInputStream();
            Object object2 = this.lock;
            synchronized (object2) {
                this.reapClosedClientsLocked();
                this.clients.addElement(object);
            }
            Log.i(TAG, "hook connected from " + ((Socket)object).getInetAddress() + ":" + ((Socket)object).getPort() + " (" + this.clientCount() + " attached)");
            final Socket sock2 = (Socket) object;
            final InputStream in2 = inputStream;
            object2 = new Thread(new Runnable() {
                public void run() {
                    try {
                        CarplayBus.this.readerLoop(sock2, in2);
                    } finally {
                        Object o = CarplayBus.this.lock;
                        synchronized (o) {
                            CarplayBus.this.clients.removeElement(sock2);
                            if (CarplayBus.this.cmdSocket == sock2) {
                                CarplayBus.this.cmdSocket = null;
                            }
                        }
                        try { sock2.close(); } catch (Exception exception) {}
                    }
                }
            }, "CarplayBus-Reader");
            ((Thread)object2).setDaemon(true);
            ((Thread)object2).start();
            return true;
        }
        catch (IOException iOException) {
            if (this.running) {
                Log.w(TAG, "accept() failed: " + iOException.getMessage());
            }
            return false;
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void readerLoop(Socket socket, InputStream inputStream) {
        if (socket == null || inputStream == null) {
            return;
        }
        DataInputStream dataInputStream = new DataInputStream(inputStream);
        byte[] byArray = new byte[16];
        while (this.running) {
            try {
                byte[] byArray2;
                dataInputStream.readFully(byArray);
                int n = CarplayBus.getBE32(byArray, 0);
                if (n != 1129334850) {
                    Log.w(TAG, "bad magic 0x" + Integer.toHexString(n));
                    break;
                }
                int n2 = CarplayBus.getBE32(byArray, 4);
                int n3 = CarplayBus.getBE16(byArray, 8);
                int n4 = byArray[10] & 0xFF;
                int n5 = CarplayBus.getBE32(byArray, 12);
                if (n5 < 0 || n5 > 131072) {
                    Log.w(TAG, "bad len " + n5);
                    break;
                }
                byte[] byArray3 = byArray2 = n5 > 0 ? new byte[n5] : new byte[]{};
                if (n5 > 0) {
                    dataInputStream.readFully(byArray2);
                }
                if (n3 == 5) {
                    String string = CarplayBus.parseText(byArray2, n5).str("role", "");
                    if (!"mm-ipod".equals(string)) continue;
                    Object object = this.lock;
                    synchronized (object) {
                        this.cmdSocket = socket;
                    }
                    Log.i(TAG, "command channel -> " + string + " pid " + CarplayBus.parseText(byArray2, n5).num("pid", -1));
                    continue;
                }
                this.dispatch(n3, n4, byArray2, n5, n2);
            }
            catch (IOException iOException) {
                if (!this.running) break;
                String string = iOException.getMessage();
                if (iOException instanceof EOFException) break;
                Log.i(TAG, "reader IO closed: " + iOException.getClass().getName() + (string == null ? "" : ": " + string));
                break;
            }
            catch (Throwable throwable) {
                Log.w(TAG, "reader error: " + throwable);
                break;
            }
        }
        Object object = this.lock;
        synchronized (object) {
            this.lock.notifyAll();
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void dispatch(int n, int n2, byte[] byArray, int n3, int n4) {
        Listener listener;
        if (n < 0 || n >= 1024) {
            Log.w(TAG, "dispatch: type 0x" + Integer.toHexString(n) + " out of range");
            return;
        }
        Object object = this.lock;
        synchronized (object) {
            listener = this.listeners[n];
        }
        if (listener == null) {
            if (n == 4 || n == 1 || n == 2 || n == 3) {
                return;
            }
            Log.d(TAG, "no listener for type 0x" + Integer.toHexString(n));
            return;
        }
        try {
            listener.onFrame(n, n2, byArray, n3);
        }
        catch (Throwable throwable) {
            Log.w(TAG, "listener for type 0x" + Integer.toHexString(n) + " threw: " + throwable);
        }
    }

    private void closeAllClientsLocked(String string) {
        while (!this.clients.isEmpty()) {
            Socket socket = (Socket)this.clients.elementAt(0);
            this.clients.removeElementAt(0);
            try {
                socket.close();
            }
            catch (Exception exception) {}
        }
    }

    private static int getBE16(byte[] byArray, int n) {
        return (byArray[n] & 0xFF) << 8 | byArray[n + 1] & 0xFF;
    }

    private static int getBE32(byte[] byArray, int n) {
        return (byArray[n] & 0xFF) << 24 | (byArray[n + 1] & 0xFF) << 16 | (byArray[n + 2] & 0xFF) << 8 | byArray[n + 3] & 0xFF;
    }

    private static void sleepMs(int n) {
        try {
            Thread.sleep(n);
        }
        catch (InterruptedException interruptedException) {
            // empty catch block
        }
    }

    public static void main(String[] stringArray) throws Exception {
        CarplayBus carplayBus = CarplayBus.getInstance();
        Listener listener = new Listener(){

            public void onFrame(int n, int n2, byte[] byArray, int n3) {
                System.out.println("RX type=0x" + Integer.toHexString(n) + " (" + CarplayBus.typeName(n) + ") flags=" + n2 + " len=" + n3);
                if (n3 > 0 && (n2 & 2) == 0) {
                    try {
                        String string = new String(byArray, 0, n3, "UTF-8");
                        if (string.length() > 200) {
                            string = string.substring(0, 200) + "...";
                        }
                        System.out.println("   text: " + string.replace('\n', '|'));
                    }
                    catch (Exception exception) {
                        // empty catch block
                    }
                }
            }
        };
        int[] nArray = new int[]{1, 2, 3, 4, 16, 17, 32, 48};
        for (int i = 0; i < nArray.length; ++i) {
            carplayBus.on(nArray[i], listener);
        }
        carplayBus.start();
        while (true) {
            Thread.sleep(2000L);
            System.out.println(carplayBus.isConnected() ? "(connected)" : "(not connected)");
        }
    }

    private static String typeName(int n) {
        switch (n) {
            case 1: {
                return "HELLO";
            }
            case 2: {
                return "SYNC_BEGIN";
            }
            case 3: {
                return "SYNC_END";
            }
            case 4: {
                return "PONG";
            }
            case 16: {
                return "COVERART";
            }
            case 17: {
                return "COVERART_NONE";
            }
            case 32: {
                return "RGD_UPDATE";
            }
            case 48: {
                return "DEVICE_STATE";
            }
            case 256: {
                return "SYNC_REQ";
            }
            case 257: {
                return "PING";
            }
        }
        return "?";
    }

    public static class Data {
        private static final int MAX = 512;
        private String[] keys = new String[512];
        private String[] vals = new String[512];
        private int count = 0;

        void put(String string, String string2) {
            if (this.count < 512) {
                this.keys[this.count] = string;
                this.vals[this.count] = string2;
                ++this.count;
            }
        }

        public String str(String string, String string2) {
            for (int i = 0; i < this.count; ++i) {
                if (!string.equals(this.keys[i])) continue;
                return this.vals[i];
            }
            return string2;
        }

        public String str(String string) {
            return this.str(string, null);
        }

        public int num(String string, int n) {
            String string2 = this.str(string, null);
            if (string2 == null) {
                return n;
            }
            try {
                return Integer.parseInt(string2.trim());
            }
            catch (Exception exception) {
                return n;
            }
        }

        public long num64(String string, long l) {
            String string2 = this.str(string, null);
            if (string2 == null) {
                return l;
            }
            try {
                return Long.parseLong(string2.trim());
            }
            catch (Exception exception) {
                return l;
            }
        }

        public boolean bool(String string, boolean bl) {
            String string2 = this.str(string, null);
            if (string2 == null) {
                return bl;
            }
            if ("true".equals(string2 = string2.trim()) || "1".equals(string2)) {
                return true;
            }
            if ("false".equals(string2) || "0".equals(string2)) {
                return false;
            }
            return bl;
        }

        public int[] intList(String string) {
            String string2 = this.str(string, null);
            if (string2 == null || string2.length() == 0) {
                return null;
            }
            int n = 0;
            for (int i = 0; i < string2.length(); ++i) {
                if (string2.charAt(i) != ',') continue;
                ++n;
            }
            int[] nArray = new int[n + 1];
            int n2 = 0;
            int n3 = 0;
            for (int i = 0; i <= string2.length(); ++i) {
                if (i != string2.length() && string2.charAt(i) != ',') continue;
                try {
                    nArray[n3++] = Integer.parseInt(string2.substring(n2, i).trim());
                }
                catch (Exception exception) {
                    nArray[n3++] = 0;
                }
                n2 = i + 1;
            }
            return nArray;
        }

        public String[] strList(String string, char c) {
            String string2 = this.str(string, null);
            if (string2 == null || string2.length() == 0) {
                return null;
            }
            int n = 1;
            for (int i = 0; i < string2.length(); ++i) {
                if (string2.charAt(i) != c) continue;
                ++n;
            }
            String[] stringArray = new String[n];
            int n2 = 0;
            int n3 = 0;
            for (int i = 0; i <= string2.length(); ++i) {
                if (i != string2.length() && string2.charAt(i) != c) continue;
                stringArray[n3++] = string2.substring(n2, i);
                n2 = i + 1;
            }
            return stringArray;
        }

        public boolean has(String string) {
            for (int i = 0; i < this.count; ++i) {
                if (!string.equals(this.keys[i])) continue;
                return true;
            }
            return false;
        }

        public int size() {
            return this.count;
        }
    }

    public static interface Listener {
        public void onFrame(int var1, int var2, byte[] var3, int var4);
    }
}
