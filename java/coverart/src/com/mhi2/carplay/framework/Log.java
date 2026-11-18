package com.mhi2.carplay.framework;

public final class Log {
    public static final int LEVEL_DEBUG = 0;
    public static final int LEVEL_INFO = 1;
    public static final int LEVEL_WARN = 2;
    public static final int LEVEL_ERROR = 3;
    public static final int LEVEL_NONE = 4;
    private static final String RAM_PATH = "/tmp/carplay_hook.log";
    private static long persistRetryAtMs;
    private static String logPath = RAM_PATH;
    private static int minLevel = 0;
    private static boolean enabled = true;
    private static boolean includeTimestamp = true;
    private static final long MAX_LOG_BYTES = 524288L;

    private Log() {
    }

    public static void setPath(String string) {
        logPath = string;
    }

    public static void setLevel(int n) {
        minLevel = n;
    }

    public static void setEnabled(boolean bl) {
        enabled = bl;
    }

    public static void setIncludeTimestamp(boolean bl) {
        includeTimestamp = bl;
    }

    public static void d(String string, String string2) {
        Log.write(0, string, string2);
    }

    public static void i(String string, String string2) {
        Log.write(1, string, string2);
    }

    public static void w(String string, String string2) {
        Log.write(2, string, string2);
    }

    public static void e(String string, String string2) {
        Log.write(3, string, string2);
    }

    public static void e(String string, String string2, Throwable throwable) {
        Log.write(3, string, string2 + ": " + throwable.getClass().getName() + ": " + throwable.getMessage());
    }

    public static synchronized void write(int n, String string, String string2) {
        if (!enabled || n < minLevel) {
            return;
        }
        StringBuffer stringBuffer = new StringBuffer();
        if (includeTimestamp) {
            stringBuffer.append(System.currentTimeMillis());
            stringBuffer.append(" ");
        }
        stringBuffer.append("[");
        stringBuffer.append(Log.levelStr(n));
        stringBuffer.append("] ");
        if (string != null) {
            stringBuffer.append("[");
            stringBuffer.append(string);
            stringBuffer.append("] ");
        }
        stringBuffer.append(string2);
        String string3 = stringBuffer.toString();
        if (logPath != null) {
            try {
                java.io.FileWriter writer = new java.io.FileWriter(logPath, true);
                writer.write(string3);
                writer.write("\n");
                writer.close();
            } catch (Exception exception) {
                /* never crash the hook over a log write */
            }
        }
    }

    public static void hexdump(String string, String string2, byte[] byArray, int n, int n2, int n3) {
        if (!enabled || 0 < minLevel) {
            return;
        }
        if (byArray == null || n2 <= 0) {
            return;
        }
        int n4 = n3 > 0 && n2 > n3 ? n3 : n2;
        StringBuffer stringBuffer = new StringBuffer();
        if (string2 != null) {
            stringBuffer.append(string2);
            stringBuffer.append(" ");
        }
        stringBuffer.append("len=");
        stringBuffer.append(n2);
        stringBuffer.append(" bytes=");
        for (int i = 0; i < n4; ++i) {
            int n5 = byArray[n + i] & 0xFF;
            stringBuffer.append(Log.hexChar(n5 >> 4));
            stringBuffer.append(Log.hexChar(n5 & 0xF));
            stringBuffer.append(" ");
        }
        if (n4 < n2) {
            stringBuffer.append("...");
        }
        Log.write(0, string, stringBuffer.toString());
    }

    private static String levelStr(int n) {
        switch (n) {
            case 0: {
                return "DBG";
            }
            case 1: {
                return "INF";
            }
            case 2: {
                return "WRN";
            }
            case 3: {
                return "ERR";
            }
        }
        return "???";
    }

    private static char hexChar(int n) {
        return n < 10 ? (char)(48 + n) : (char)(65 + n - 10);
    }
}