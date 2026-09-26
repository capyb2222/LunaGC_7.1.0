package emu.grasscutter.utils;

import java.nio.charset.StandardCharsets;

public final class ProtoSniff {

    private ProtoSniff() {}

    public static String describe(byte[] data) {
        if (data == null || data.length == 0) return "(empty)";
        var sb = new StringBuilder();
        int i = 0;
        int printed = 0;
        while (i < data.length && printed < 64) {
            long key;
            try {
                long[] kv = varint(data, i);
                key = kv[0];
                i = (int) kv[1];
            } catch (Exception e) {
                sb.append(" <undecodable at ").append(i).append(">");
                break;
            }
            int field = (int) (key >>> 3);
            int wire = (int) (key & 7);
            if (field == 0) {
                sb.append(" <field 0 at ").append(i).append(">");
                break;
            }
            if (sb.length() > 0) sb.append(' ');
            sb.append('#').append(field).append('=');
            try {
                switch (wire) {
                    case 0 -> {
                        long[] v = varint(data, i);
                        i = (int) v[1];
                        sb.append("varint(").append(v[0]).append(')');
                    }
                    case 1 -> {
                        sb.append("fixed64");
                        i += 8;
                    }
                    case 2 -> {
                        long[] len = varint(data, i);
                        i = (int) len[1];
                        int n = (int) len[0];
                        if (n < 0 || i + n > data.length) {
                            sb.append("<bad len ").append(n).append('>');
                            i = data.length;
                            break;
                        }
                        sb.append(renderBytes(data, i, n));
                        i += n;
                    }
                    case 5 -> {
                        sb.append("fixed32");
                        i += 4;
                    }
                    default -> {
                        sb.append("<wire ").append(wire).append('>');
                        i = data.length;
                    }
                }
            } catch (Exception e) {
                sb.append("<truncated>");
                break;
            }
            printed++;
        }
        if (i < data.length) sb.append(" ...+").append(data.length - i).append('B');
        return sb.toString();
    }

    private static String renderBytes(byte[] data, int off, int n) {
        var s = new String(data, off, n, StandardCharsets.UTF_8);
        boolean printable = true;
        for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
            if (c < 0x20 || c > 0x7e) {
                printable = false;
                break;
            }
        }
        if (printable && n > 0) {
            if (n > 48) return "\"" + s.substring(0, 24) + "…\"(" + n + " chars)";
            return "\"" + s + "\"";
        }
        return "<" + n + " bytes>";
    }

    private static long[] varint(byte[] data, int pos) {
        long value = 0;
        int shift = 0;
        while (pos < data.length) {
            byte b = data[pos++];
            value |= (long) (b & 0x7f) << shift;
            if ((b & 0x80) == 0) return new long[] {value, pos};
            shift += 7;
            if (shift > 63) throw new IllegalStateException("varint too long");
        }
        throw new IllegalStateException("varint ran off the end");
    }
}
