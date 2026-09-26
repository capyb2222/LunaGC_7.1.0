package emu.grasscutter.utils;

import java.nio.charset.StandardCharsets;

public final class ProtoRead {

    private ProtoRead() {}

    public static long varint(byte[] data, int field) {
        var found = find(data, field, 0);
        return found == null ? 0 : (long) found;
    }

    public static String string(byte[] data, int field) {
        var found = find(data, field, 2);
        return found == null ? "" : new String((byte[]) found, StandardCharsets.UTF_8);
    }

    private static Object find(byte[] data, int field, int wanted) {
        if (data == null) return null;
        int i = 0;
        while (i < data.length) {
            long[] kv;
            try {
                kv = varintAt(data, i);
            } catch (Exception e) {
                return null;
            }
            i = (int) kv[1];
            int number = (int) (kv[0] >>> 3);
            int wire = (int) (kv[0] & 7);
            if (number == 0) return null;
            try {
                switch (wire) {
                    case 0 -> {
                        long[] v = varintAt(data, i);
                        i = (int) v[1];
                        if (number == field && wanted == 0) return v[0];
                    }
                    case 1 -> i += 8;
                    case 2 -> {
                        long[] len = varintAt(data, i);
                        i = (int) len[1];
                        int n = (int) len[0];
                        if (n < 0 || i + n > data.length) return null;
                        if (number == field && wanted == 2) {
                            var out = new byte[n];
                            System.arraycopy(data, i, out, 0, n);
                            return out;
                        }
                        i += n;
                    }
                    case 5 -> i += 4;
                    default -> {
                        return null;
                    }
                }
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    private static long[] varintAt(byte[] data, int pos) {
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
