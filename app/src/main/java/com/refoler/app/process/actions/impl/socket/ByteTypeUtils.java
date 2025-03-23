package com.refoler.app.process.actions.impl.socket;

public class ByteTypeUtils {
    public static byte[] toBytes(boolean value) {
        byte[] bytes = new byte[1];
        bytes[0] = (byte) (value ? 0x01 : 0x00);
        return bytes;
    }

    public static byte[] toBytes(long value) {
        byte[] bytes = new byte[8];
        bytes[0] = (byte) ((int)(value >>> 56) & 0xFF);
        bytes[1] = (byte) ((int)(value >>> 48) & 0xFF);
        bytes[2] = (byte) ((int)(value >>> 40) & 0xFF);
        bytes[3] = (byte) ((int)(value >>> 32) & 0xFF);
        bytes[4] = (byte) ((int)(value >>> 24) & 0xFF);
        bytes[5] = (byte) ((int)(value >>> 16) & 0xFF);
        bytes[6] = (byte) ((int)(value >>>  8) & 0xFF);
        bytes[7] = (byte) ((int)(value) & 0xFF);
        return bytes;
    }

    public static byte[] toBytes(int value) {
        byte[] bytes = new byte[4];
        bytes[0] = (byte) ((value >>> 24) & 0xFF);
        bytes[1] = (byte) ((value >>> 16) & 0xFF);
        bytes[2] = (byte) ((value >>>  8) & 0xFF);
        bytes[3] = (byte) ((value) & 0xFF);
        return bytes;
    }

    public static boolean toBoolean(byte[] bytes) {
        return bytes[0] != 0x00;
    }

    public static long toLong(byte[] bytes) {
        return ((long) (bytes[0] & 0xFF) << 56) |
                ((long) (bytes[1] & 0xFF) << 48) |
                ((long) (bytes[2] & 0xFF) << 40) |
                ((long) (bytes[3] & 0xFF) << 32) |
                ((long) (bytes[4] & 0xFF) << 24) |
                ((long) (bytes[5] & 0xFF) << 16) |
                ((long) (bytes[6] & 0xFF) <<  8) |
                ((long) (bytes[7] & 0xFF) & 0xFF);
    }

    public static int toInt(byte[] bytes) {
        return ((bytes[0] & 0xFF) << 24) |
                ((bytes[1] & 0xFF) << 16) |
                ((bytes[2] & 0xFF) << 8 ) |
                ((bytes[3] & 0xFF));
    }
}
