package com.myx5spro.control.proto;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 荣耀耳机 MBB 协议编解码（逆向自 com.hihonor.mbbconnect）。
 *
 * <p>帧格式：
 * <pre>
 *   [0x5A][len_hi][len_lo][0x00][TLV payload ...][crc_hi][crc_lo]
 *   len = payload.length + 1
 *   crc = CRC-16/XMODEM(0x5A, len_hi, len_lo, 0x00, payload...)
 * </pre>
 *
 * <p>TLV 格式：
 * <pre>
 *   [tag:1B][len:1B 或 2B][value]
 *   len &lt; 0x80 : 单字节
 *   否则        : 0x80 | (len &gt;&gt; 8), len &amp; 0xFF
 * </pre>
 */
public final class MbbProtocol {

    public static final int FRAME_HEAD = 0x5A;
    public static final int FRAME_HEADER_LEN = 4;
    public static final int FRAME_CRC_LEN = 2;

    private MbbProtocol() {
    }

    /** 单个 TLV 项。 */
    public static final class Tlv {
        public final int tag;
        public final byte[] value;

        public Tlv(int tag, byte[] value) {
            this.tag = tag & 0xFF;
            this.value = value == null ? new byte[0] : value;
        }

        public Tlv(int tag, int... v) {
            this.tag = tag & 0xFF;
            this.value = new byte[v.length];
            for (int i = 0; i < v.length; i++) {
                this.value[i] = (byte) v[i];
            }
        }

        public int asInt() {
            int r = 0;
            for (byte b : value) {
                r = (r << 8) | (b & 0xFF);
            }
            return r;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("Tlv(").append(tag).append(")=");
            for (byte b : value) {
                sb.append(String.format("%02X", b));
            }
            return sb.toString();
        }
    }

    // ---------------------------------------------------------------- 编码

    /** 编码单个 TLV。 */
    public static void writeTlv(ByteArrayOutputStream out, int tag, byte[] value) {
        out.write(tag & 0xFF);
        int len = value.length;
        if (len < 0x80) {
            out.write(len);
        } else {
            out.write(0x80 | ((len >> 8) & 0x7F));
            out.write(len & 0xFF);
        }
        out.write(value, 0, value.length);
    }

    /** 把 TLV 列表组装成 payload。 */
    public static byte[] buildPayload(List<Tlv> tlvs) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (Tlv t : tlvs) {
            writeTlv(out, t.tag, t.value);
        }
        return out.toByteArray();
    }

    /** 对 payload 加帧头与 CRC。 */
    public static byte[] frame(byte[] payload) {
        int len = payload.length + 1;
        ByteArrayOutputStream out = new ByteArrayOutputStream(payload.length + 6);
        out.write(FRAME_HEAD);
        out.write((len >> 8) & 0xFF);
        out.write(len & 0xFF);
        out.write(0x00);
        out.write(payload, 0, payload.length);
        byte[] body = out.toByteArray();
        int crc = Crc16.calc(body, 0, body.length);
        out.write((crc >> 8) & 0xFF);
        out.write(crc & 0xFF);
        return out.toByteArray();
    }

    /** 一步到位：TLV 列表 → 完整帧。 */
    public static byte[] buildFrame(List<Tlv> tlvs) {
        return frame(buildPayload(tlvs));
    }

    // ---------------------------------------------------------------- 解码

    /**
     * 解析收到的字节流。流式解析：返回 [已经完整消费的帧, 剩余待续字节]。
     */
    public static List<byte[]> splitFrames(byte[] buf, ByteArrayOutputStream pending) {
        pending.write(buf, 0, buf.length);
        byte[] data = pending.toByteArray();
        List<byte[]> frames = new ArrayList<>();
        int pos = 0;
        while (true) {
            // 找帧头
            while (pos < data.length && (data[pos] & 0xFF) != FRAME_HEAD) {
                pos++;
            }
            if (data.length - pos < FRAME_HEADER_LEN) {
                break;
            }
            int len = ((data[pos + 1] & 0xFF) << 8) | (data[pos + 2] & 0xFF);
            int total = FRAME_HEADER_LEN + (len - 1) + FRAME_CRC_LEN;
            if (total <= FRAME_HEADER_LEN + FRAME_CRC_LEN) {
                pos += 1;
                continue;
            }
            if (data.length - pos < total) {
                break;
            }
            byte[] frame = new byte[total];
            System.arraycopy(data, pos, frame, 0, total);
            frames.add(frame);
            pos += total;
        }
        pending.reset();
        if (pos < data.length) {
            pending.write(data, pos, data.length - pos);
        }
        return frames;
    }

    /** 校验并去除帧头/CRC，返回 TLV 载荷。校验失败返回 null。 */
    public static byte[] unwrap(byte[] frame) {
        if (frame.length < FRAME_HEADER_LEN + FRAME_CRC_LEN) {
            return null;
        }
        int bodyLen = frame.length - FRAME_CRC_LEN;
        int crcCalc = Crc16.calc(frame, 0, bodyLen);
        int crcFrame = ((frame[bodyLen] & 0xFF) << 8) | (frame[bodyLen + 1] & 0xFF);
        if (crcCalc != crcFrame) {
            return null;
        }
        byte[] payload = new byte[bodyLen - FRAME_HEADER_LEN];
        System.arraycopy(frame, FRAME_HEADER_LEN, payload, 0, payload.length);
        return payload;
    }

    /** 解析 TLV 载荷为 TLV 列表。 */
    public static List<Tlv> parseTlvs(byte[] payload, int offset) {
        List<Tlv> result = new ArrayList<>();
        int p = offset;
        while (p + 2 <= payload.length) {
            int tag = payload[p++] & 0xFF;
            int len = payload[p++] & 0xFF;
            if ((len & 0x80) != 0) {
                if (p >= payload.length) {
                    break;
                }
                len = ((len & 0x7F) << 8) | (payload[p++] & 0xFF);
            }
            if (p + len > payload.length) {
                break;
            }
            byte[] v = new byte[len];
            System.arraycopy(payload, p, v, 0, len);
            p += len;
            result.add(new Tlv(tag, v));
        }
        return result;
    }

    public static Tlv findTlv(List<Tlv> tlvs, int tag) {
        for (Tlv t : tlvs) {
            if (t.tag == tag) {
                return t;
            }
        }
        return null;
    }

    /** 读 32 位大端整数（属性块 {@code 01 39/3A} 的 int32 字段）。 */
    public static int readInt32(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 24) | ((data[offset + 1] & 0xFF) << 16)
                | ((data[offset + 2] & 0xFF) << 8) | (data[offset + 3] & 0xFF);
    }

    public static String hex(byte[] data) {
        if (data == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder(data.length * 3);
        for (byte b : data) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }
}
