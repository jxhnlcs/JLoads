package com.jloads.util;

import java.util.Locale;

/** Formatação legível de velocidade e tempo restante. */
public final class HumanFormat {

    private static final String[] UNITS = {"B", "KB", "MB", "GB", "TB"};

    private HumanFormat() {
    }

    /** Ex.: 4404019.2 → "4.2 MB/s". */
    public static String speed(Double bytesPerSecond) {
        if (bytesPerSecond == null || bytesPerSecond.isNaN() || bytesPerSecond < 0) {
            return null;
        }
        return bytes(bytesPerSecond) + "/s";
    }

    public static String bytes(double value) {
        int unit = 0;
        double scaled = value;
        while (scaled >= 1024 && unit < UNITS.length - 1) {
            scaled /= 1024;
            unit++;
        }
        return unit == 0
                ? String.format(Locale.ROOT, "%.0f %s", scaled, UNITS[unit])
                : String.format(Locale.ROOT, "%.1f %s", scaled, UNITS[unit]);
    }

    /** Ex.: 8 → "00:08"; 3725 → "1:02:05". */
    public static String eta(Long seconds) {
        if (seconds == null || seconds < 0) {
            return null;
        }
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        return h > 0
                ? String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
                : String.format(Locale.ROOT, "%02d:%02d", m, s);
    }
}
