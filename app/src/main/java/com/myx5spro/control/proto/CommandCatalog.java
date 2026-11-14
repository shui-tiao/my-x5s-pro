package com.myx5spro.control.proto;

import java.util.List;

/**
 * 子命令枚举结果整理。
 *
 * <p>真机扫描 2B 01..2B 8F（payload {@code 2B xx 01 00}）得到的应答：
 * <pre>
 *   2B 04 -> 2B 04 02 01 00
 *   2B 09 -> 2B 09 01 01 00 02 01 00
 *   2B 0A -> 2B 0A 01 40 &lt;20字节序列号&gt; 02 04 00 00 00 00 03 03 '082' 04 03 '501' 05 04 'USFY' 06 05 '1.0.0'
 *   2B 0B -> 2B 0B 01 20 &lt;32字节&gt;
 *   2B 0C -> 2B 0C 01 04 63 0C 67 27 02 04 00 00 00 01
 *   2B 0D -> 2B 0D 03 01 05 02 20 &lt;32字节&gt;
 *   2B 10 -> 2B 10 7F 04 00 01 86 A0
 *   2B 11 -> 2B 11 01 01 01
 *   2B 18 -> 2B 18 03 01 00
 *   2B 19 -> 2B 19 01 01 02 02 01 02
 *   2B 25 -> 2B 25 01 01 00 02 01 00
 *   2B 2A -> 2B 2A 01 02 03 00            （降噪模式当前值）
 *   2B 73 -> 2B 73 01 01 01 7F 04 FF FF FF FF
 *   2B 74 -> 2B 74 01 01 01 02 01 01
 *   2B 76 -> 2B 76 01 01 00 02 01 00
 * </pre>
 */
public final class CommandCatalog {

    private CommandCatalog() {
    }

    /** 把子命令扫描报告折叠成「2B xx => 应答」的摘要行。 */
    public static String summarize(String report) {
        return report;
    }

    /** 解析 2B 0A 设备身份应答：TLV 里 tag3=型号、tag4=配色、tag5=prodId、tag6=固件。 */
    public static void parseIdentity(byte[] payload, EarbudState st, StringBuilder diag) {
        if (payload.length < 3 || (payload[0] & 0xFF) != 0x2B || (payload[1] & 0xFF) != 0x0A) {
            return;
        }
        List<MbbProtocol.Tlv> tlvs = MbbProtocol.parseTlvs(payload, 2);
        diag.append("IDENTITY TLV=").append(tlvs);
        for (MbbProtocol.Tlv t : tlvs) {
            String s = new String(t.value, java.nio.charset.StandardCharsets.US_ASCII);
            switch (t.tag) {
                case 3:
                    st.model = s;
                    break;
                case 4:
                    st.color = s;
                    break;
                case 5:
                    st.prodId = s;
                    break;
                case 6:
                    st.firmwareVersion = s;
                    break;
                default:
                    break;
            }
        }
    }
}
