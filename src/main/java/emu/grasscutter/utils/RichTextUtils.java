package emu.grasscutter.utils;

public final class RichTextUtils {
    private RichTextUtils() {}

    public static final int BYTES_PER_GRADIENT_CHAR = 24;

    public static int parseColor(String raw) {
        if (raw == null) return -1;
        var hex = raw.startsWith("#") ? raw.substring(1) : raw;

        if (hex.length() == 3) {
            var sb = new StringBuilder();
            for (char c : hex.toCharArray()) sb.append(c).append(c);
            hex = sb.toString();
        }

        if (hex.length() != 6) return -1;
        try {
            return Integer.parseInt(hex, 16);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    public static int lerpColor(int from, int to, float t) {
        int r = Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
        int g = Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
        int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return (r << 16) | (g << 8) | b;
    }

    public static String colorize(String text, int color) {
        return "<color=#" + String.format("%06X", color) + ">" + text + "</color>";
    }

    public static String gradient(String text, int start, int end) {
        var sb = new StringBuilder();
        int last = text.length() - 1;

        for (int i = 0; i <= last; i++) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) {
                sb.append(c);
                continue;
            }

            float t = last == 0 ? 0f : (float) i / last;
            sb.append(colorize(String.valueOf(c), lerpColor(start, end, t)));
        }
        return sb.toString();
    }
}
