package com.refoler.app.process.actions.impl.socket;

public class ByteTypeUtils {
    public static byte[] toBytes(long value) {
        byte[] bytes = new byte[8];
        for (int i = 7; i >= 0; i--) {
            bytes[i] = (byte) (value & 0xFF);
            value >>= 8;
        }
        return bytes;
    }

    public static byte[] toBytes(int value) {
        byte[] bytes = new byte[4];
        for (int i = 3; i >= 0; i--) {
            bytes[i] = (byte) (value & 0xFF);
            value >>= 8;
        }
        return bytes;
    }

    public static long toLong(byte[] bytes) {
        long ch1 = bytes[0];
        long ch2 = bytes[1];
        long ch3 = bytes[2];
        long ch4 = bytes[3];
        long ch5 = bytes[4];
        long ch6 = bytes[5];
        long ch7 = bytes[6];
        long ch8 = bytes[7];
        return ((ch1 << 56) + (ch2 << 48) + (ch3 << 40) + (ch4 << 32) + (ch5 << 24) + (ch6 << 16) + (ch7 << 8) + (ch8));
    }

    public static int toInt(byte[] bytes) {
        int ch1 = bytes[0];
        int ch2 = bytes[1];
        int ch3 = bytes[2];
        int ch4 = bytes[3];
        return ((ch1 << 24) + (ch2 << 16) + (ch3 << 8) + (ch4));
    }

}
