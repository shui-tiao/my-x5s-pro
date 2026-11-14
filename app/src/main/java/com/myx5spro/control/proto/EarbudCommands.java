package com.myx5spro.control.proto;

import java.util.ArrayList;
import java.util.List;

/**
 * 耳机命令集合。
 *
 * <p>本文件的命令字节已通过两条独立途径交叉验证：
 * <ul>
 *   <li>反编译官方插件 {@code com.hihonor.mbbconnect.mbblayer.intelligentservice
 *       .IntelligentServiceMbbLayer} / {@code ...Second} 的字节构建方法；</li>
 *   <li>root 设备上 btsnoop HCI 抓包（荣耀智慧空间官方插件真实通信）。</li>
 * </ul>
 */
public final class EarbudCommands {

    /** 查询当前设备信息（电量/名称/固件等）。载荷 2B 39 01 00。 */
    public static final byte[] REQ_DEVICE_INFO = {0x2B, 0x39, 0x01, 0x00};
    /** 查询设备能力。载荷 2B 79 01 00。 */
    public static final byte[] REQ_DEVICE_ABILITY = {0x2B, 0x79, 0x01, 0x00};

    /** 降噪模式（2B 2A 应答第二字节 / 2B 04 上报第一字节）。 */
    public static final int ANC_OFF = 0;
    public static final int ANC_ON = 1;
    public static final int ANC_TRANSPARENCY = 2;

    /** 降噪等级编码（真机 + 反编译 setAncLevel 确认）。 */
    public static final int LEVEL_SMART = 0;
    public static final int LEVEL_LIGHT = 1;
    public static final int LEVEL_MEDIUM = 2;
    public static final int LEVEL_DEEP = 3;

    /**
     * EQ 音效取值，来自官方云端配置
     * {@code plugins_v3_data/com.hihonor.audioassistantplugin.eco/app_cloud_config_file/USFY/zh_CN/service_text.json}
     * （service=3 earphone, index=5 eq）。
     *
     * <p><b>没有 1</b>：0=标准、2=低音增强、100=高音增强。
     * 写入非枚举值（如 1）耳机也会接受并原样读回，但不属于任何音效档位。
     */
    public static final int EQ_STANDARD = 0;
    public static final int EQ_BASS = 2;
    public static final int EQ_TREBLE = 100;

    /** 子命令字（反编译 IntelligentServiceMbbLayer）。 */
    public static final int CMD_SET_PROPERTY = 0x39;   // DeviceManageMbbLayer.setPropertyValue（属性写入块）
    public static final int CMD_GET_PROPERTY = 0x3A;   // DeviceManageMbbLayer.getPropertyValue（属性读取）
    public static final int CMD_ANC_STATE = 0x04;      // setAncState / setPickUpState + 耳机状态上报
    public static final int CMD_ANC_SWITCH = 0x05;     // getAncState / getPickUpState
    public static final int CMD_ANC_MODE_LEVEL = 0x07; // getAncModeAndLevel
    public static final int CMD_ANC_LEVEL_SET = 0x08;  // setAncLevel
    public static final int CMD_ANC_MODE_INFO = 0x06;  // setAncMode（多场景智能降噪）
    public static final int CMD_LONGPRESS_SET = 0x16;  // setLongPressFunction（长按，tag1=左 tag2=右）
    public static final int CMD_LONGPRESS_QUERY = 0x17;
    public static final int CMD_NOISECTL_SET = 0x18;   // setNoiseControlFunction（捏住循环降噪，tag1=左 tag2=右）
    public static final int CMD_NOISECTL_QUERY = 0x19;
    public static final int CMD_LDAC_QUERY = 0x1A;     // LDAC（Second 层）
    public static final int CMD_LDAC_SET = 0x1B;
    public static final int CMD_SLIDE_SET = 0x1E;      // setSlideFunction（滑动，tag1=左 tag2=右）
    public static final int CMD_SLIDE_QUERY = 0x1F;
    public static final int CMD_PINCH_SET = 0x20;      // setPinchFunction（捏住，tag1=左 tag2=右）
    public static final int CMD_PINCH_QUERY = 0x21;
    public static final int CMD_DBL_QUERY = 0x2E;      // getDoubleClickState（双击开关）
    public static final int CMD_DBL_SET = 0x2F;        // getDoubleClickSetting(side)
    public static final int CMD_ANC_QUERY = 0x2A;      // queryNoiseReductionMode
    public static final int CMD_LDAC2_QUERY = 0x3A;    // LDAC 状态查询（Second 层另一路）
    public static final int CMD_LDAC2_SET = 0x3B;
    public static final int CMD_SHARED_QUERY = 0x6B;   // 双设备连接开关
    public static final int CMD_SHARED_SET = 0x6C;
    public static final int CMD_EQ_QUERY = 0x6D;       // getEqualizerSoundEffect
    public static final int CMD_EQ_SET = 0x6E;         // setEqualizerSoundEffect
    public static final int CMD_QUIT_SET = 0x64;       // setQuitTime
    public static final int CMD_3D_QUERY = 0x75;       // get3DAudio（空间音频，Second 层）
    public static final int CMD_3D_SET = 0x76;
    public static final int CMD_GME_QUERY = 0x70;      // 游戏模式
    public static final int CMD_GME_SET = 0x71;
    public static final int CMD_WEAR_SET = 0x69;       // 智能佩戴检测开关

    private EarbudCommands() {
    }

    public static byte[] deviceInfoFrame() {
        return MbbProtocol.frame(REQ_DEVICE_INFO);
    }

    public static byte[] deviceAbilityFrame() {
        return MbbProtocol.frame(REQ_DEVICE_ABILITY);
    }

    /** 查询设备身份（2B 0A 01 00）：型号/配色/prodId/固件。 */
    public static byte[] identityFrame() {
        return MbbProtocol.frame(new byte[]{0x2B, 0x0A, 0x01, 0x00});
    }

    /**
     * 官方握手序列（btsnoop 抓包还原）。
     *
     * <p>官方插件在每次进入耳机页/每次操作前都会依次发送：
     * <pre>
     * 01 27 01 01 40 02 03 xx xx 40 03 03 64 64 00  能力/版本声明
     * 01 3a 81 14 82 12 …                            版本协商
     * 01 3e 01 04 00 00 00 82                        devType 会话建立
     * 01 07 …                                        能力清单
     * </pre>
     * 缺了 {@code 01 3e}（devType）时，耳机会静默忽略大量 {@code 2B xx} 设置命令
     * （实测 {@code 2B 6D} EQ 查询无任何应答）。
     */
    public static byte[][] handshakeFrames() {
        return new byte[][]{
                MbbProtocol.frame(new byte[]{0x01, 0x27, 0x01, 0x01, 0x40, 0x02, 0x03, 0x64, 0x64,
                        0x40, 0x03, 0x03, 0x64, 0x64, 0x00}),
                MbbProtocol.frame(new byte[]{0x01, 0x3a, (byte) 0x81, 0x14, (byte) 0x82, 0x12,
                        0x03, 0x04, 0x00, 0x00, 0x00, 0x03, 0x04, 0x04, 0x00, 0x00, 0x00,
                        0x05, 0x05, 0x04, 0x00, 0x00, 0x00, 0x00}),
                devTypeFrame(),
                MbbProtocol.frame(new byte[]{0x01, 0x07, 0x01, 0x08, 0x35, 0x2e, 0x33, 0x2e,
                        0x32, 0x2e, 0x32, 0x37, 0x03, 0x0b, 0x35, 0x30, 0x31, 0x31, 0x35,
                        0x5f, 0x33, 0x30, 0x2f, 0x34, 0x30, 0x05, 0x06, 0x31, 0x4c, (byte) 0xe4,
                        (byte) 0x9e, 0x00, 0x00, 0x07, 0x08, 0x31, 0x2e, 0x31, 0x2e, 0x36, 0x34,
                        0x2e, 0x32, 0x09, 0x10, 0x35, 0x35, 0x30, 0x34, 0x41, 0x41, 0x4b, 0x43,
                        0x32, 0x34, 0x34, 0x51, 0x30, 0x34, 0x33, 0x37, 0x0a, 0x0f, 0x42, 0x54,
                        0x56, 0x2d, 0x4d, 0x45, 0x31, 0x30, 0x2d, 0x30, 0x30, 0x55, 0x53, 0x46,
                        0x59, 0x0c, 0x00, 0x11, 0x08, 0x42, 0x54, 0x56, 0x2d, 0x4d, 0x45, 0x31,
                        0x30, 0x19, 0x01, 0x00}),
        };
    }

    /** devType 会话建立帧：01 3e 01 04 00 00 00 82（抓包还原）。 */
    public static byte[] devTypeFrame() {
        return MbbProtocol.frame(new byte[]{0x01, 0x3e, 0x01, 0x04, 0x00, 0x00, 0x00, (byte) 0x82});
    }

    /** 任意子命令的「查询」帧：2B xx 01 00。 */
    public static byte[] subCmdQueryFrame(int subCmd) {
        byte[] payload = {0x2B, (byte) subCmd, 0x01, 0x00};
        return MbbProtocol.frame(payload);
    }

    /** 任意子命令的「设置」帧：2B xx 01 len value...。 */
    public static byte[] subCmdSetFrame(int subCmd, byte[] value) {
        byte[] payload = new byte[4 + value.length];
        payload[0] = 0x2B;
        payload[1] = (byte) subCmd;
        payload[2] = 0x01;
        payload[3] = (byte) value.length;
        System.arraycopy(value, 0, payload, 4, value.length);
        return MbbProtocol.frame(payload);
    }

    /** 对耳机主动上报（如 2B 04 01 02 …）的确认帧：2B xx 02 01 00（反编译 + 抓包验证）。 */
    public static byte[] ackFrame(int subCmd) {
        byte[] payload = {0x2B, (byte) subCmd, 0x02, 0x01, 0x00};
        return MbbProtocol.frame(payload);
    }

    // ------------------------------------------------------------ 降噪

    /**
     * 设置降噪模式 + 等级（官方 setAncState(byte,byte)）。
     *
     * <p>写入 {@code 2B 04 01 02 <mode> <level>} —— mode 在前、level 在后
     * （与耳机主动上报 2B 04 的字节序一致；注意与 2B 2A 应答的 [level][mode] 相反）。
     * 官方日志佐证：{@code setNoiseStateChange mode = 1 state = 3}。
     */
    public static byte[] setAncFrame(int mode, int level) {
        byte[] payload = {0x2B, CMD_ANC_STATE, 0x01, 0x02,
                (byte) (mode & 0xFF), (byte) (level & 0xFF)};
        return MbbProtocol.frame(payload);
    }

    /** 只切模式（保持当前等级由耳机侧决定；level 参数缺省用深度）。 */
    public static byte[] setAncFrame(int mode) {
        return setAncFrame(mode, LEVEL_DEEP);
    }

    /** 官方专用「设降噪等级」命令（setAncLevel）：2B 08 01 01 <level>。 */
    public static byte[] ancLevelSetFrame(int level) {
        byte[] payload = {0x2B, CMD_ANC_LEVEL_SET, 0x01, 0x01, (byte) (level & 0xFF)};
        return MbbProtocol.frame(payload);
    }

    /** 查询降噪模式+等级（getAncModeAndLevel）：2B 07 01 00。 */
    public static byte[] ancModeLevelQueryFrame() {
        return MbbProtocol.frame(new byte[]{0x2B, CMD_ANC_MODE_LEVEL, 0x01, 0x00});
    }

    /** 查询降噪开关/透传状态（getAncState）：2B 05 01 00。 */
    public static byte[] ancStateQueryFrame() {
        return MbbProtocol.frame(new byte[]{0x2B, CMD_ANC_SWITCH, 0x01, 0x00});
    }

    /** 查询降噪模式（queryNoiseReductionMode）：2B 2A 01 00；应答 2B 2A 01 02 <level> <mode>。 */
    public static byte[] ancQueryFrame() {
        return MbbProtocol.frame(new byte[]{0x2B, CMD_ANC_QUERY, 0x01, 0x00});
    }

    // ------------------------------------ 属性读写（01 39 / 01 3A）
    //
    // 真机实测（USFY 固件 1.1.64.2）：EQ / LDAC / 空间环绕声都不走 2B xx，
    // 而是走 DeviceManageMbbLayer 的「属性块」：
    //   写 = 01 39 + TLV(1,service) + TLV(2,prop) + TLV(3,value)，载荷 20 字节
    //   读 = 01 3A + TLV(1, TLV(2, TLV(3,service)+TLV(4,prop)+[flag,00]))，两层嵌套
    // 2B 6D/6E（EQ）、2B 1A/1B（LDAC）、2B 75/76（空间）在本固件上均无任何应答。

    /** EQ：service=3, prop=5；取值见 {@link #EQ_STANDARD} / {@link #EQ_BASS} / {@link #EQ_TREBLE}。 */
    public static final int EQ_SERVICE_INDEX = 3;
    public static final int EQ_PROPERTY_INDEX = 5;

    /**
     * LDAC 与空间环绕声共用 service=6（settings）：prop 0=LDACSwitch、prop 1=SpaceAudioSwitch。
     *
     * <p>真机对照官方 APP（2026-11-11）：读回 value=1 时官方显示「已开启」、value=0 显示「已关闭」
     * （真机帧 {@code 01 3A ... service=6 prop=0 value=1} 与官方页面同刻比对）。
     * 云端 service_text.json 的枚举表是文案索引而非协议值，此前按 0=开 写导致显示相反。
     * 两者互斥：开 LDAC 会关闭空间环绕声。
     */
    public static final int AUDIO_SERVICE_INDEX = 6;
    public static final int LDAC_PROPERTY_INDEX = 0;
    public static final int AUDIO3D_PROPERTY_INDEX = 1;
    /** 开关型属性的取值：1=开、0=关（真机对照官方 APP 确认）。 */
    public static final int SWITCH_ON = 1;
    public static final int SWITCH_OFF = 0;

    /** 写属性的应答是 7F 错误码，但设置实际生效——官方插件同样按成功处理。 */
    public static final int PROP_ERR_NOT_SUPPORT = 0x7F;

    public static byte[] eqQueryFrame() {
        return getPropertyFrame(EQ_SERVICE_INDEX, EQ_PROPERTY_INDEX);
    }

    public static byte[] eqSetFrame(int v) {
        return setPropertyFrame(EQ_SERVICE_INDEX, EQ_PROPERTY_INDEX, v);
    }

    public static byte[] ldacQueryFrame() {
        return getPropertyFrame(AUDIO_SERVICE_INDEX, LDAC_PROPERTY_INDEX);
    }

    public static byte[] ldacSetFrame(boolean on) {
        return setPropertyFrame(AUDIO_SERVICE_INDEX, LDAC_PROPERTY_INDEX, on ? SWITCH_ON : SWITCH_OFF);
    }

    public static byte[] audio3dQueryFrame() {
        return getPropertyFrame(AUDIO_SERVICE_INDEX, AUDIO3D_PROPERTY_INDEX);
    }

    public static byte[] audio3dSetFrame(boolean on) {
        return setPropertyFrame(AUDIO_SERVICE_INDEX, AUDIO3D_PROPERTY_INDEX, on ? SWITCH_ON : SWITCH_OFF);
    }

    // ------------------------------------------------------------ 快捷操作（service=4/5）

    /**
     * 轻捏 / 捏住，同样走属性通道，索引来自官方云端配置 service_text.json。
     *
     * <p>USFY 没有「滑动」功能：官方 UI 的快捷操作只有 Pinch（轻捏）与 Pinchandhold（捏住）；
     * 老的 {@code 2B 1E/1F/17/21/16/20} 在本固件无任何应答。
     */
    public static final int PINCH_SERVICE_INDEX = 4;
    public static final int HOLD_SERVICE_INDEX = 5;

    /** 轻捏：非通话单击（播放/暂停）。 */
    public static final int PINCH_PROP_ONCE = 7;
    /** 轻捏：通话中单击（接听/挂断）。 */
    public static final int PINCH_PROP_CALL_ONCE = 0;
    /** 轻捏：非通话双击左/右（0=下一首 1=唤醒语音助手 2=无）。 */
    public static final int PINCH_PROP_TWICE_LEFT = 2;
    public static final int PINCH_PROP_TWICE_RIGHT = 3;
    /** 轻捏：非通话三击左/右（0=上一首 1=游戏模式 2=无）。 */
    public static final int PINCH_PROP_THREE_LEFT = 5;
    public static final int PINCH_PROP_THREE_RIGHT = 6;

    public static final int PINCH_TWICE_NEXT = 0;
    public static final int PINCH_TWICE_VOICE = 1;
    public static final int PINCH_THREE_PREV = 0;
    public static final int PINCH_THREE_GAME = 1;
    public static final int PINCH_NONE = 2;

    /** 捏住：来电时捏住 2 秒（0=拒接来电）。 */
    public static final int HOLD_PROP_CALL = 0;
    /** 捏住：非来电时捏住 2 秒（0=噪声控制 1=无）。 */
    public static final int HOLD_PROP_NORMAL = 1;
    public static final int HOLD_NOISE_CTL = 0;
    public static final int HOLD_NONE = 1;

    public static byte[] pinchQueryFrame(int propertyIndex) {
        return getPropertyFrame(PINCH_SERVICE_INDEX, propertyIndex);
    }

    public static byte[] pinchSetFrame(int propertyIndex, int value) {
        return setPropertyFrame(PINCH_SERVICE_INDEX, propertyIndex, value);
    }

    public static byte[] holdQueryFrame(int propertyIndex) {
        return getPropertyFrame(HOLD_SERVICE_INDEX, propertyIndex);
    }

    public static byte[] holdSetFrame(int propertyIndex, int value) {
        return setPropertyFrame(HOLD_SERVICE_INDEX, propertyIndex, value);
    }

    /**
     * 捏住 2 秒 = 噪声控制「轮换模式组合」配置（官方抓包确认，走 2B 18，不是属性通道）。
     *
     * <p>官方「捏住」页给出降噪/关闭/透传三个复选框，**至少选两项**（只能取消一项）。
     * wire 值是<b>固件枚举，不是参与轮换的模式个数</b>（2026-11-11 全枚举真机测绘实锤，
     * 初版按「个数」解读是反的）。四种组合全部采集到官方写入样本 + 官方 UI 渲染对应：
     * <pre>
     * 1 = 两个模式（降噪+关闭，透传退出轮换）  官方取消透传时实测 2B 18 01 01 01 02 01 01（9963s）
     * 2 = 三个模式（降噪/关闭/透传，默认）    官方勾回任一项时实测 2B 18 01 01 02 02 01 02
     * 3 = 两个模式（降噪+透传，关闭退出轮换）  官方页面读到 3 时渲染「降噪✓ 关闭✗ 透传✓」
     * 4 = 两个模式（关闭+透传，降噪退出轮换）  官方取消降噪时实测 2B 18 01 01 04 02 01 04（9737s）
     * </pre>
     * 判定依据（互相印证）：
     * <ol>
     *   <li>官方页面全新启动读到 2B 19=2,2 时渲染<b>三项全勾</b>——官方 UI 的映射即真相；</li>
     *   <li>我们写 3,3 后官方页面渲染「降噪✓ 关闭✗ 透传✓」——直接锚定 3=降噪+透传；</li>
     *   <li>官方页面上切换勾选框，btsnoop 捕获写入 1/2/3/4 与每次 UI 状态一一对应；</li>
     *   <li>用户物理确认回读 2,2 时耳机实际在「降噪→关闭→透传」三模式轮换；</li>
     *   <li>写无效值 7 被钳回 2（默认=三模式）。</li>
     * </ol>
     *
     * <p>载荷 `2B 18 01 01 <左> 02 01 <右>`（写入 tag1=左耳、tag2=右耳，反编译
     * setNoiseControlFunction 确认——注意与读回方向相反：2B 19 应答是 tag1=右耳、tag2=左耳）。
     * **写完就结束，不要发任何后续帧**。`2B 18 03 01 00` 只是耳机方向的 ACK
     * （tag3=状态码，0=成功）；如果手机也发这条帧，耳机会把值<b>重置回默认 2</b>——
     * 实测我们写 3 + 提交帧后回读恒 2,2（6792s），去掉提交帧后写 1/2/3/4 回读立即跟随。
     * 回读 `2B 19 01 00 02 00` → `2B 19 01 01 <R> 02 01 <L>`，官方写后约 0.7s 回读即可拿到新值。
     */
    public static final int HOLD_CYCLE_ANC_OFF = 1;
    public static final int HOLD_CYCLE_ALL_THREE = 2;
    public static final int HOLD_CYCLE_ANC_TRANS = 3;
    public static final int HOLD_CYCLE_OFF_TRANS = 4;

    public static byte[] holdCycleSetFrame(int left, int right) {
        return MbbProtocol.frame(new byte[]{
                0x2B, 0x18, 0x01, 0x01, (byte) left, 0x02, 0x01, (byte) right});
    }

    public static byte[] holdCycleQueryFrame() {
        return MbbProtocol.frame(new byte[]{0x2B, 0x19, 0x01, 0x00, 0x02, 0x00});
    }

    private static byte[] tlvInt32(int tag, int v) {
        return new byte[]{(byte) tag, 0x04,
                (byte) ((v >>> 24) & 0xFF), (byte) ((v >>> 16) & 0xFF),
                (byte) ((v >>> 8) & 0xFF), (byte) (v & 0xFF)};
    }

    /**
     * 写属性（{@code DeviceManageMbbLayer.setPropertyValue(int,int,int)}，commandId=0x39）。
     *
     * <pre>
     * 01 39 01 04 &lt;serviceIndex int32&gt; 02 04 &lt;propertyIndex int32&gt; 03 04 &lt;value int32&gt;
     * 载荷 20 字节；官方日志 length=21（= 载荷 + 1）
     * </pre>
     */
    public static byte[] setPropertyFrame(int serviceIndex, int propertyIndex, int value) {
        byte[] t1 = tlvInt32(1, serviceIndex);
        byte[] t2 = tlvInt32(2, propertyIndex);
        byte[] t3 = tlvInt32(3, value);
        byte[] payload = new byte[2 + t1.length + t2.length + t3.length];
        int p = 0;
        payload[p++] = 0x01;
        payload[p++] = CMD_SET_PROPERTY;
        System.arraycopy(t1, 0, payload, p, t1.length);
        p += t1.length;
        System.arraycopy(t2, 0, payload, p, t2.length);
        p += t2.length;
        System.arraycopy(t3, 0, payload, p, t3.length);
        return MbbProtocol.frame(payload);
    }

    /**
     * 读属性（{@code DeviceManageMbbLayer.getPropertyValue(List,List,byte)}，commandId=0x3A）。
     *
     * <p>注意结构是**两层嵌套** TLV，不是平铺：
     * <pre>
     * 01 3A 01 &lt;len&gt; 02 &lt;len&gt;
     *              03 04 &lt;serviceIndex int32&gt;
     *              04 04 &lt;propertyIndex int32&gt;
     *              &lt;flag&gt; 00
     * </pre>
     * 官方用例：{@code getProperty([3], [5], 5, cb)} → flag=0x05。
     */
    public static byte[] getPropertyFrame(int serviceIndex, int propertyIndex) {
        return getPropertyFrame(serviceIndex, propertyIndex, 0x05);
    }

    public static byte[] getPropertyFrame(int serviceIndex, int propertyIndex, int flag) {
        byte[] t3 = tlvInt32(3, serviceIndex);
        byte[] t4 = tlvInt32(4, propertyIndex);
        byte[] inner = new byte[t3.length + t4.length + 2];
        int q = 0;
        System.arraycopy(t3, 0, inner, q, t3.length);
        q += t3.length;
        System.arraycopy(t4, 0, inner, q, t4.length);
        q += t4.length;
        inner[q++] = (byte) (flag & 0xFF);
        inner[q] = 0x00;

        byte[] tlv2 = tlvBytes(2, inner);
        byte[] tlv1 = tlvBytes(1, tlv2);
        byte[] payload = new byte[2 + tlv1.length];
        payload[0] = 0x01;
        payload[1] = CMD_GET_PROPERTY;
        System.arraycopy(tlv1, 0, payload, 2, tlv1.length);
        return MbbProtocol.frame(payload);
    }

    /** 通用 TLV：len &lt; 0x80 用 1 字节，否则 0x80|高字节 + 低字节。 */
    private static byte[] tlvBytes(int tag, byte[] value) {
        byte[] out;
        if (value.length < 0x80) {
            out = new byte[2 + value.length];
            out[0] = (byte) tag;
            out[1] = (byte) value.length;
            System.arraycopy(value, 0, out, 2, value.length);
        } else {
            out = new byte[3 + value.length];
            out[0] = (byte) tag;
            out[1] = (byte) (0x80 | (value.length >> 8));
            out[2] = (byte) (value.length & 0xFF);
            System.arraycopy(value, 0, out, 3, value.length);
        }
        return out;
    }

    /**
     * 解析到的属性三元组（{@code 01 39} 上报 / {@code 01 3A} 应答）。
     */
    public static final class Property {
        public final int serviceIndex;
        public final int propertyIndex;
        public final int value;

        Property(int serviceIndex, int propertyIndex, int value) {
            this.serviceIndex = serviceIndex;
            this.propertyIndex = propertyIndex;
            this.value = value;
        }
    }

    /**
     * 解析属性上报（{@code 01 39} 主动推送 / {@code 01 3A} 应答）。
     *
     * <p>{@code 01 39} 是三个平铺的 int32 TLV：tag1=service、tag2=prop、tag3=value；
     * {@code 01 3A} 是两层嵌套：外层 tag1 包住内层，内层 tag3=service、tag4=prop、tag5=value。
     * 这里两种都兼容，任意层级扫到 service/prop/value 三个 int32 即可。
     *
     * @return 属性三元组；不是属性帧时返回 null
     */
    public static Property parseProperty(byte[] payload) {
        if (payload == null || payload.length < 10 || payload[0] != 0x01) {
            return null;
        }
        int cmd = payload[1] & 0xFF;
        if (cmd != CMD_SET_PROPERTY && cmd != CMD_GET_PROPERTY) {
            return null;
        }
        int service = Integer.MIN_VALUE;
        int prop = Integer.MIN_VALUE;
        int value = Integer.MIN_VALUE;
        int p = 2;
        while (p + 2 <= payload.length) {
            int tag = payload[p] & 0xFF;
            int len = payload[p + 1] & 0xFF;
            int vp = p + 2;
            if (len == 0x04 && vp + 4 <= payload.length) {
                int v = MbbProtocol.readInt32(payload, vp);
                if (tag == 3 && service == Integer.MIN_VALUE) {
                    service = v;
                } else if (tag == 4 && prop == Integer.MIN_VALUE) {
                    prop = v;
                } else if (tag == 5 && value == Integer.MIN_VALUE) {
                    value = v;
                }
            } else if (len >= 0x80) {
                len = ((len & 0x7F) << 8) | (payload[vp] & 0xFF);
                vp++;
            }
            p = vp + len;
        }
        // 01 3A 的嵌套应答：81 <len> 82 <len> (03 04 svc)(04 04 prop)(05 04 value)
        // 顶层只有 tag1/tag2 两个容器，真正的三元组埋在里层，需递归展开后再取。
        if (service == Integer.MIN_VALUE || prop == Integer.MIN_VALUE || value == Integer.MIN_VALUE) {
            int s3 = Integer.MIN_VALUE;
            int p3 = Integer.MIN_VALUE;
            int v3 = Integer.MIN_VALUE;
            int q = 2;
            while (q + 2 <= payload.length) {
                int tag = payload[q] & 0xFF;
                int len = payload[q + 1] & 0xFF;
                int vp = q + 2;
                if (len >= 0x80) {
                    len = ((len & 0x7F) << 8) | (payload[vp] & 0xFF);
                    vp++;
                }
                // 容器（tag1/tag2）时进入里层，按同样的 TLV 结构继续扫
                int end = Math.min(vp + len, payload.length);
                int r = (tag == 1 || tag == 2) ? vp : q;
                for (int i = r; i + 2 <= end; i++) {
                    int t = payload[i] & 0xFF;
                    int l = payload[i + 1] & 0xFF;
                    int ip = i + 2;
                    if (l == 0x04 && ip + 4 <= payload.length) {
                        int v = MbbProtocol.readInt32(payload, ip);
                        if (t == 3) {
                            s3 = v;
                        } else if (t == 4) {
                            p3 = v;
                        } else if (t == 5) {
                            v3 = v;
                        }
                        i = ip + 3;
                    }
                }
                q = end;
            }
            if (s3 != Integer.MIN_VALUE && p3 != Integer.MIN_VALUE) {
                return new Property(s3, p3, v3 == Integer.MIN_VALUE ? -1 : v3);
            }
            return null;
        }
        return new Property(service, prop, value);
    }

    /** 是否是对写入的 7F 错误应答（该固件对 01 39 写属性一律回 7F，但设置实际生效）。 */
    public static boolean isPropertyWriteError(byte[] payload) {
        return payload != null && payload.length >= 4
                && payload[0] == 0x01 && (payload[1] & 0xFF) == CMD_SET_PROPERTY
                && (payload[2] & 0xFF) == PROP_ERR_NOT_SUPPORT;
    }


    // ------------------------------------------------------------ 快捷操作（左耳 tag1 / 右耳 tag2）

    /**
     * 「左右两值」型查询帧（反编译固定格式）：2B xx 01 00 02 00。
     * 适用于 滑动(1F) / 捏住(21) / 降噪控制(19) / 长按(17)。
     */
    public static byte[] pairQueryFrame(int queryCmd) {
        byte[] payload = {0x2B, (byte) queryCmd, 0x01, 0x00, 0x02, 0x00};
        return MbbProtocol.frame(payload);
    }

    /** 「左右两值」型设置帧：2B xx [tag1 01 v][tag2 01 v]。 */
    public static byte[] pairSetFrame(int setCmd, int left, int right) {
        List<MbbProtocol.Tlv> tlvs = new ArrayList<>();
        tlvs.add(new MbbProtocol.Tlv(1, left & 0xFF));
        tlvs.add(new MbbProtocol.Tlv(2, right & 0xFF));
        byte[] body = MbbProtocol.buildPayload(tlvs);
        byte[] payload = new byte[2 + body.length];
        payload[0] = 0x2B;
        payload[1] = (byte) setCmd;
        System.arraycopy(body, 0, payload, 2, body.length);
        return MbbProtocol.frame(payload);
    }

    // ------------------------------------------------------------ 解析

    /**
     * 解析设备信息上报（电量），只认两种帧：
     * <ul>
     *   <li>周期包络 {@code 01 27 | 01 01 <盒电量> | 02 03 <左> <右> <盒> | 03 03 ...}
     *       （实测每 ~15s 推送一次，电量值随真实电量变化）；</li>
     *   <li>{@code 2B 39} 查询应答（本固件实测不应答，保留兼容）。</li>
     * </ul>
     *
     * <p>注意：<b>tag=3 不是充电状态</b>。实测未充电时 tag3 恒为 {@code 64 64 00}，
     * 与电量、充电均无关（不同电量采样一致）；旧版把「非 0」当充电导致左右耳恒显示充电中。
     * 充电位的真实编码待耳机入盒充电时抓帧确认，确认前一律按未充电显示。
     * 设备名不在这里猜：握手 01 07 应答的 tag1 是版本串（曾把 "5.3.2.27" 当设备名），
     * 设备名以 SPP 连接回调的蓝牙名为准（onConnected）。
     */
    public static void parseDeviceInfo(byte[] payload, EarbudState st, StringBuilder diag) {
        boolean envelope = payload.length > 3 && (payload[0] & 0xFF) == 0x01
                && (payload[1] & 0xFF) == 0x27;
        boolean direct = payload.length > 3 && (payload[0] & 0xFF) == 0x2B
                && (payload[1] & 0xFF) == 0x39;
        if (!envelope && !direct) {
            return;
        }
        // 包络帧首两字节 01 27 是包络（子命令 0x01 + 业务字 0x27），真正 TLV 从偏移 2 开始；
        // 不能用 parseTlvs(payload,0)，否则 tag=1/len=0x27 会吞掉整个载荷。
        List<MbbProtocol.Tlv> body = MbbProtocol.parseTlvs(payload, 2);
        diag.append("电量帧 tlv=").append(body);
        for (MbbProtocol.Tlv t : body) {
            if (t.tag == 1 && t.value.length == 1) {
                // 单字节：充电盒电量（实测 0x32 = 50%，与 tag2 第三字节一致）
                int c = t.value[0] & 0xFF;
                if (c <= 100) {
                    st.batteryCase = c;
                }
            } else if (t.tag == 2 && t.value.length == 3) {
                st.batteryLeft = t.value[0] & 0xFF;
                st.batteryRight = t.value[1] & 0xFF;
                int caseB = t.value[2] & 0xFF;
                st.batteryCase = (caseB == 0xFF || caseB > 100) ? -1 : caseB;
            } else if (t.tag == 3 && t.value.length == 3) {
                // 编码未知（未充电时恒 64 64 00），不映射任何状态，留日志待抓充电样本
                diag.append(" tag3(未知)=").append(MbbProtocol.hex(t.value));
            }
        }
        st.rawTlvs = listToString(body);
    }

    /** 解析 ANC 状态响应（2B 2A 01 02 <level> <mode>，等级在前）。 */
    public static void parseAnc(byte[] payload, EarbudState st, StringBuilder diag) {
        if (payload.length < 3 || (payload[0] & 0xFF) != 0x2B
                || (payload[1] & 0xFF) != CMD_ANC_QUERY) {
            return;
        }
        int off = 2;
        diag.append("anc=").append(MbbProtocol.hex(payload));
        List<MbbProtocol.Tlv> tlvs = MbbProtocol.parseTlvs(payload, off);
        diag.append("ANC TLV: ").append(tlvs).append('\n');
        for (MbbProtocol.Tlv t : tlvs) {
            if (t.tag != 1) {
                continue;
            }
            int v = -1;
            if (t.value.length == 1) {
                v = t.value[0] & 0xFF;
            } else if (t.value.length == 2) {
                // 实测 2B 2A 01 02 <level> <mode>：第一字节是降噪等级（0 智能/1 轻度/2 中度/3 深度），
                // 第二字节是模式（0 关闭/1 降噪/2 透传）。
                st.ancLevel = t.value[0] & 0xFF;
                v = t.value[1] & 0xFF;
            }
            if (v >= 0 && v <= ANC_TRANSPARENCY) {
                st.ancMode = v;
            }
        }
    }

    /**
     * 解析耳机主动上报与设置应答（EQ / LDAC / 空间音频 / 快捷操作 / 2B 04 状态上报）。
     *
     * @return true 表示这是耳机的 2B 04 状态上报，需要回 ACK（2B 04 02 01 00）
     */
    public static boolean parseSettings(byte[] payload, EarbudState st, StringBuilder diag) {
        if (payload.length < 3 || (payload[0] & 0xFF) != 0x2B) {
            return false;
        }
        int cmd = payload[1] & 0xFF;
        boolean needAck = false;

        // 2B 04：耳机主动推送降噪状态（官方格式 2B 04 01 02 <mode> <level>，mode 在前）
        if (cmd == CMD_ANC_STATE) {
            List<MbbProtocol.Tlv> tlvs = MbbProtocol.parseTlvs(payload, 2);
            for (MbbProtocol.Tlv t : tlvs) {
                if (t.tag == 1 && t.value.length == 2) {
                    st.ancMode = t.value[0] & 0xFF;
                    st.ancLevel = t.value[1] & 0xFF;
                    diag.append("ANC 上报 mode=").append(st.ancMode)
                            .append(" level=").append(st.ancLevel).append('\n');
                    needAck = true;
                }
            }
            return needAck;
        }

        List<MbbProtocol.Tlv> tlvs = MbbProtocol.parseTlvs(payload, 2);
        switch (cmd) {
            case CMD_EQ_QUERY:   // 2B 6D 应答 01 01 <v>
                for (MbbProtocol.Tlv t : tlvs) {
                    if (t.tag == 1 && t.value.length >= 1) {
                        st.eqLevel = t.value[0] & 0xFF;
                        diag.append("EQ=").append(st.eqLevel).append('\n');
                    }
                }
                break;
            case CMD_LDAC_QUERY: // 2B 1A 应答 01 01 <v>
                for (MbbProtocol.Tlv t : tlvs) {
                    if (t.tag == 1 && t.value.length >= 1) {
                        st.ldacOn = t.value[0] & 0xFF;
                        diag.append("LDAC=").append(st.ldacOn).append('\n');
                    }
                }
                break;
            case CMD_3D_QUERY:   // 2B 75 应答 01 01 <v>
                for (MbbProtocol.Tlv t : tlvs) {
                    if (t.tag == 1 && t.value.length >= 1) {
                        st.audio3d = t.value[0] & 0xFF;
                        diag.append("3D=").append(st.audio3d).append('\n');
                    }
                }
                break;
            case CMD_NOISECTL_QUERY: {
                // 反编译 IntelligentServiceMbbLayer$3：tag1→setRightAncControl、tag2→setLeftAncControl
                Integer l = null, r = null;
                boolean isSetAck = false;
                for (MbbProtocol.Tlv t : tlvs) {
                    if (t.tag == 3) {
                        isSetAck = true;
                    } else if (t.tag == 1 && t.value.length >= 1) {
                        r = t.value[0] & 0xFF;
                    } else if (t.tag == 2 && t.value.length >= 1) {
                        l = t.value[0] & 0xFF;
                    }
                }
                if (isSetAck) {
                    diag.append("捏住轮换写入成功\n");
                } else if (l != null || r != null) {
                    st.holdCycleL = l == null ? -1 : l;
                    st.holdCycleR = r == null ? -1 : r;
                    diag.append("捏住轮换 左=").append(st.holdCycleL)
                            .append(" 右=").append(st.holdCycleR).append('\n');
                }
                break;
            }
            case CMD_SLIDE_QUERY:
            case CMD_PINCH_QUERY:
                // 这些 2B 命令在 USFY 固件上无应答，快捷操作已改走属性通道 service=4/5，
                // 分支保留仅为兼容其它固件/机型的原始应答打印。
                break;
            default:
                break;
        }
        return false;
    }

    private static String listToString(List<MbbProtocol.Tlv> tlvs) {
        StringBuilder sb = new StringBuilder();
        for (MbbProtocol.Tlv t : tlvs) {
            sb.append(t).append('\n');
        }
        return sb.toString();
    }

}
