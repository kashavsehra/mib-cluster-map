package com.mhi2.carplay.framework;

/*
 * Obf - one helper the framework uses to keep log paths and feature flags out
 * of plain sight in the strings table.  It is a simple XOR cipher keyed by the
 * character position, so the constants stay short and the decompiler shows a
 * char[] loop instead of a path.  Nothing here depends on the exact strings;
 * only the cipher round-trips them.
 *
 * Copyright (c) 2026 LuKa (@LuKa_dev), ported into mib-cluster-map.
 */
public final class Obf {
    private Obf() {
    }

    public static String d(String string, int n) {
        char[] cArray = new char[string.length()];
        for (int i = 0; i < cArray.length; ++i) {
            cArray[i] = (char)((string.charAt(i) ^ 23100 + n * 131 + i * 29 + i * i * 7) & 0xFF);
        }
        return new String(cArray);
    }
}