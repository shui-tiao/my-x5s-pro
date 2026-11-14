package com.myx5spro.control.proto;

/** 耳机状态数据模型（字段来自逆向的 IntelligentService 实体）。 */
public final class EarbudState {

    /** 左耳电量，-1 表示未知。 */
    public int batteryLeft = -1;
    public int batteryRight = -1;
    public int batteryCase = -1;
    /** 充电状态：0 未充电，1 充电中。 */
    public boolean chargingLeft;
    public boolean chargingRight;
    public boolean chargingCase;

    /** 降噪模式：0=关闭, 1=降噪, 2=透传。 */
    public int ancMode = -1;
    /** 降噪等级：0=智能, 1=轻度, 2=中度, 3=深度。-1 表示未知。 */
    public int ancLevel = -1;
    /** EQ 音效：0=标准, 2=低音增强, 100=高音增强（官方配置枚举，无 1）。-1 未知。 */
    public int eqLevel = -1;
    /** LDAC：1=开, 0=关。-1 未知（真机对照官方 APP 确认）。 */
    public int ldacOn = -1;
    /** 空间环绕声：1=开, 0=关。-1 未知。 */
    public int audio3d = -1;
    /** 轻捏（service=4）非通话双击：左/右。0=下一首 1=唤醒语音助手 2=无。 */
    public int pinchTwiceL = -1, pinchTwiceR = -1;
    /** 轻捏（service=4）非通话三击：左/右。0=上一首 1=游戏模式 2=无。 */
    public int pinchThreeL = -1, pinchThreeR = -1;
    /** 捏住 2 秒（service=5）非来电：0=噪声控制 1=无。 */
    public int holdNormal = -1;

    /**
     * 捏住 = 噪声控制时的轮换模式（2B 19）。左右耳分开。
     * wire 值是固件枚举而非参与轮换的模式个数：
     * 1=降噪+关闭 2=三个模式（降噪/关闭/透传，默认） 3=降噪+透传 4=关闭+透传。
     */
    public int holdCycleL = -1, holdCycleR = -1;
    /** 佩戴状态。 */
    public boolean wornLeft;
    public boolean wornRight;

    /** 设备名。 */
    public String deviceName;
    /** 固件版本。 */
    public String firmwareVersion;
    /** 型号（2B 0A tag3）。 */
    public String model;
    /** 配色（2B 0A tag4）。 */
    public String color;
    /** 产品 ID（2B 0A tag5），X5s Pro 为 USFY。 */
    public String prodId;
    /** 原始 TLV，调试用。 */
    public String rawTlvs;

    public boolean isConnected() {
        return deviceName != null || batteryLeft >= 0 || ancMode >= 0;
    }

    public String batteryText() {
        if (batteryLeft < 0 && batteryRight < 0 && batteryCase < 0) {
            return "--";
        }
        String s = fmt(batteryLeft) + " / " + fmt(batteryRight);
        if (batteryCase >= 0) {
            s += "  盒 " + batteryCase + "%";
        }
        return s;
    }

    private static String fmt(int v) {
        return v < 0 ? "--%" : v + "%";
    }

    public String ancText() {
        switch (ancMode) {
            case 0:
                return "关闭";
            case 1:
                return ancLevel >= 0 ? "降噪 · " + levelText() : "降噪";
            case 2:
                return "透传";
            default:
                return "未知";
        }
    }

    /** 降噪等级文案：0 智能 / 1 轻度 / 2 中度 / 3 深度。 */
    public String levelText() {
        switch (ancLevel) {
            case 0:
                return "智能";
            case 1:
                return "轻度";
            case 2:
                return "中度";
            case 3:
                return "深度";
            default:
                return "未知";
        }
    }

    /** EQ 文案（0=标准 / 2=低音增强 / 100=高音增强）。 */
    public String eqText() {
        switch (eqLevel) {
            case 0:
                return "标准";
            case 2:
                return "低音增强";
            case 100:
                return "高音增强";
            default:
                return "未知(" + eqLevel + ")";
        }
    }

    /** 轻捏双击文案（0=下一首 1=唤醒语音助手 2=无）。 */
    public static String pinchTwiceText(int v) {
        switch (v) {
            case 0:
                return "下一首";
            case 1:
                return "唤醒语音助手";
            case 2:
                return "无";
            default:
                return "?";
        }
    }

    /** 轻捏三击文案（0=上一首 1=游戏模式 2=无）。 */
    public static String pinchThreeText(int v) {
        switch (v) {
            case 0:
                return "上一首";
            case 1:
                return "游戏模式/普通模式";
            case 2:
                return "无";
            default:
                return "?";
        }
    }

    /** 捏住 2 秒（非来电）文案。 */
    public static String holdText(int v) {
        switch (v) {
            case 0:
                return "噪声控制";
            case 1:
                return "无";
            default:
                return "?";
        }
    }

    /**
     * 捏住轮换模式文案（固件枚举，全枚举真机测绘：官方页面渲染锚定）。
     * 1=降噪+关闭 2=三个模式（默认） 3=降噪+透传 4=关闭+透传。
     */
    public static String holdCycleText(int v) {
        switch (v) {
            case 1:
                return "两个模式（降噪+关闭）";
            case 2:
                return "三个模式（降噪/关闭/透传）";
            case 3:
                return "两个模式（降噪+透传）";
            case 4:
                return "两个模式（关闭+透传）";
            default:
                return "未知(" + v + ")";
        }
    }

    /** 所有已读设置的汇总（调试 + 展示）。 */
    public String settingsText() {
        StringBuilder sb = new StringBuilder();
        if (eqLevel >= 0) {
            sb.append("EQ: ").append(eqText()).append('\n');
        }
        if (ldacOn >= 0) {
            sb.append("LDAC: ").append(ldacOn == 0 ? "开" : "关").append('\n');
        }
        if (audio3d >= 0) {
            sb.append("空间环绕: ").append(audio3d == 0 ? "开" : "关").append('\n');
        }
        if (pinchTwiceL >= 0 || pinchTwiceR >= 0) {
            sb.append("轻捏两下 左=").append(pinchTwiceText(pinchTwiceL))
                    .append(" 右=").append(pinchTwiceText(pinchTwiceR)).append('\n');
        }
        if (pinchThreeL >= 0 || pinchThreeR >= 0) {
            sb.append("轻捏三下 左=").append(pinchThreeText(pinchThreeL))
                    .append(" 右=").append(pinchThreeText(pinchThreeR)).append('\n');
        }
        if (holdNormal >= 0) {
            sb.append("捏住2秒: ").append(holdText(holdNormal)).append('\n');
        }
        if (holdCycleL >= 0 || holdCycleR >= 0) {
            sb.append("捏住轮换 左=").append(holdCycleText(holdCycleL))
                    .append(" 右=").append(holdCycleText(holdCycleR)).append('\n');
        }
        return sb.length() == 0 ? "" : sb.toString();
    }
}
