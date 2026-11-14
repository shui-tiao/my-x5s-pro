package com.myx5spro.control.proto;

/**
 * CRC-16/XMODEM (poly 0x1021, init 0x0000) — 与荣耀 mbbconnect 的 CommandConstant CRC 表一致。
 */
public final class Crc16 {

    private static final int[] TABLE = new int[256];

    static {
        for (int i = 0; i < 256; i++) {
            int crc = i << 8;
            for (int b = 0; b < 8; b++) {
                crc = ((crc & 0x8000) != 0) ? ((crc << 1) ^ 0x1021) : (crc << 1);
                crc &= 0xFFFF;
            }
            TABLE[i] = crc;
        }
    }

    private Crc16() {
    }

    public static int calc(byte[] data, int offset, int length) {
        int crc = 0x0000;
        for (int i = offset; i < offset + length; i++) {
            crc = ((crc << 8) & 0xFFFF) ^ TABLE[((crc >>> 8) ^ (data[i] & 0xFF)) & 0xFF];
        }
        return crc & 0xFFFF;
    }
}
