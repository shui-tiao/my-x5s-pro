package com.myx5spro.control;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.myx5spro.control.proto.CommandCatalog;
import com.myx5spro.control.proto.CommandProbe;
import com.myx5spro.control.proto.EarbudCommands;
import com.myx5spro.control.proto.EarbudState;
import com.myx5spro.control.proto.MbbProtocol;
import com.myx5spro.control.proto.SppChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.IntConsumer;

/**
 * 独立耳机控制器主界面（消费级 UI）。
 *
 * <p>架构：经典蓝牙 SPP（UUID 00001101-...）→ MBB 帧（0x5A + len + TLV + CRC16）。
 *
 * <p>界面为单 Activity 三页切换（主页 / 设置与关于 / 开发者选项），主页只保留正常用户内容。
 */
public class MainActivity extends AppCompatActivity {

    private static final int REQ_BT_PERM = 1001;
    private static final String[] PERMS_12 = {
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
    };

    // ------------------------------------------------------------ 页面
    private static final int PAGE_MAIN = 0;
    private static final int PAGE_SETTINGS = 1;
    private static final int PAGE_DEV = 2;
    private int currentPage = PAGE_MAIN;

    private final EarbudState state = new EarbudState();
    private final SppChannel channel = new SppChannel();
    private final StringBuilder logBuf = new StringBuilder();

    private View pageMain;
    private View pageSettings;
    private View pageDev;
    private TextView tvPageTitle;
    private ImageView btnBack;

    /** 连接状态横幅。 */
    private View banner;
    private ImageView ivBannerIcon;
    private TextView tvBannerTitle;
    private TextView tvBannerSub;
    private TextView btnConnect;
    /** 一次性提示（权限缺失/断开原因/待选择设备），非空时覆盖默认未连接文案。 */
    private String bannerMsg;

    /** 电量三列。 */
    private TextView tvBatLeft;
    private ProgressBar barBatLeft;
    private TextView tvBatLeftState;
    private TextView tvBatRight;
    private ProgressBar barBatRight;
    private TextView tvBatRightState;
    private TextView tvBatCase;
    private ProgressBar barBatCase;
    private TextView tvBatCaseState;

    /** 降噪分段主控与等级行。 */
    private TextView tvAncHint;
    private TextView segAncOff;
    private TextView segAncOn;
    private TextView segAncTrans;
    private View rowAncLevel;
    private TextView chipLevelSmart;
    private TextView chipLevelLight;
    private TextView chipLevelMedium;
    private TextView chipLevelDeep;
    /** 降噪切换失败提示，非空时优先显示在卡片右上角。 */
    private String ancHintMsg;

    /** 音效：EQ 三选一 + LDAC/空间音频开关行。 */
    private TextView chipEqStd;
    private TextView chipEqBass;
    private TextView chipEqTreble;
    private View rowLdac;
    private TextView tvLdacState;
    private View row3d;
    private TextView tvAudio3dState;

    /** 快捷操作（捏压/捏住）芯片。 */
    private TextView chipTwiceL;
    private TextView chipTwiceR;
    private TextView chipThreeL;
    private TextView chipThreeR;
    private TextView chipHold;
    private TextView chipCycle;

    private TextView tvInfo;
    private TextView tvLog;
    private TextView tvProbe;
    private TextView tvSettings;
    private EditText etProbeFrom;
    private EditText etProbeTo;
    private EditText etProbeInterval;
    private EditText etRaw;
    private CommandProbe probe;
    /** 正在等待生效的降噪目标模式，-1 表示无。 */
    private int pendingAnc = -1;
    private ScrollView logScroll;
    private LinearLayout deviceList;

    private boolean connecting;
    /** 首次 onResume 跳过（连接由 onCreate 负责），之后回到前台自动重连。 */
    private boolean firstResume = true;
    /** 本次连接是否已发送过官方握手序列（01 27 / 01 3a / 01 3e / 01 07）。 */
    private boolean handshakeSent;
    private BluetoothDevice target;
    private BroadcastReceiver bondReceiver;

    /** 断开后自动重连：切换 LDAC 会让耳机重启蓝牙栈、SPP 掉线，需等耳机恢复再重试。 */
    private final android.os.Handler mainHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());
    private static final int RECONNECT_MAX_ATTEMPTS = 10;
    private static final long RECONNECT_DELAY_MS = 3000;
    /** 记住上次成功连接的耳机地址，下次打开/重连直接连它，不再弹设备选择。 */
    private static final String PREFS_NAME = "earbud_prefs";
    private static final String KEY_LAST_ADDRESS = "last_device_address";
    private static final String KEY_AUTO_RESUME = "auto_resume_playback";
    /** 切换 LDAC 前是否在播放音乐；重连成功后按设置决定是否发送播放键。 */
    private boolean resumePlaybackPending;
    private boolean autoResumePlayback;
    private View rowAutoResume;
    private TextView tvAutoResumeState;
    private int reconnectAttempts;
    private final Runnable reconnectTask = new Runnable() {
        @Override
        public void run() {
            if (!channel.isConnected() && !connecting && target != null) {
                startConnect(target);
            }
        }
    };

    /** 降噪切换超时兜底：2.5s 内没等到目标模式就结束「切换中」并提示。 */
    private final Runnable pendingAncTimeout = new Runnable() {
        @Override
        public void run() {
            int aim = pendingAnc;
            if (aim < 0) {
                return;
            }
            pendingAnc = -1;
            if (state.ancMode != aim) {
                ancHintMsg = "切换未生效：请确认耳机已取出并佩戴";
                appendLog("降噪切换未生效（耳机回显 " + state.ancMode + "）");
            }
            updateUi();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // 让根容器先持有焦点，避免 EditText 自动弹出键盘挡住日志
        View root = findViewById(android.R.id.content);
        root.setFocusableInTouchMode(true);
        root.requestFocus();

        // ---- 页面切换 ----
        pageMain = findViewById(R.id.page_main);
        pageSettings = findViewById(R.id.page_settings);
        pageDev = findViewById(R.id.page_dev);
        tvPageTitle = findViewById(R.id.tv_page_title);
        btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> showPage(PAGE_MAIN));
        findViewById(R.id.btn_settings).setOnClickListener(v -> showPage(PAGE_SETTINGS));
        findViewById(R.id.btn_dev).setOnClickListener(v -> showPage(PAGE_DEV));
        findViewById(R.id.btn_refresh).setOnClickListener(v -> refresh());

        // ---- 状态横幅 ----
        banner = findViewById(R.id.banner);
        ivBannerIcon = findViewById(R.id.iv_banner_icon);
        tvBannerTitle = findViewById(R.id.tv_banner_title);
        tvBannerSub = findViewById(R.id.tv_banner_sub);
        btnConnect = findViewById(R.id.btn_connect);
        btnConnect.setOnClickListener(v -> pickDevice());

        // ---- 电量 ----
        tvBatLeft = findViewById(R.id.tv_bat_left);
        barBatLeft = findViewById(R.id.bar_bat_left);
        tvBatLeftState = findViewById(R.id.tv_bat_left_state);
        tvBatRight = findViewById(R.id.tv_bat_right);
        barBatRight = findViewById(R.id.bar_bat_right);
        tvBatRightState = findViewById(R.id.tv_bat_right_state);
        tvBatCase = findViewById(R.id.tv_bat_case);
        barBatCase = findViewById(R.id.bar_bat_case);
        tvBatCaseState = findViewById(R.id.tv_bat_case_state);

        // ---- 降噪分段主控 ----
        tvAncHint = findViewById(R.id.tv_anc_hint);
        segAncOff = findViewById(R.id.seg_anc_off);
        segAncOn = findViewById(R.id.seg_anc_on);
        segAncTrans = findViewById(R.id.seg_anc_trans);
        segAncOff.setOnClickListener(v -> setAnc(EarbudCommands.ANC_OFF));
        segAncOn.setOnClickListener(v -> setAnc(EarbudCommands.ANC_ON));
        segAncTrans.setOnClickListener(v -> setAnc(EarbudCommands.ANC_TRANSPARENCY));
        rowAncLevel = findViewById(R.id.row_anc_level);
        chipLevelSmart = findViewById(R.id.chip_level_smart);
        chipLevelLight = findViewById(R.id.chip_level_light);
        chipLevelMedium = findViewById(R.id.chip_level_medium);
        chipLevelDeep = findViewById(R.id.chip_level_deep);
        chipLevelSmart.setOnClickListener(v -> setAncLevel(EarbudCommands.LEVEL_SMART));
        chipLevelLight.setOnClickListener(v -> setAncLevel(EarbudCommands.LEVEL_LIGHT));
        chipLevelMedium.setOnClickListener(v -> setAncLevel(EarbudCommands.LEVEL_MEDIUM));
        chipLevelDeep.setOnClickListener(v -> setAncLevel(EarbudCommands.LEVEL_DEEP));

        // ---- 音效 ----
        chipEqStd = findViewById(R.id.chip_eq_std);
        chipEqBass = findViewById(R.id.chip_eq_bass);
        chipEqTreble = findViewById(R.id.chip_eq_treble);
        chipEqStd.setOnClickListener(v -> setEq(EarbudCommands.EQ_STANDARD));
        chipEqBass.setOnClickListener(v -> setEq(EarbudCommands.EQ_BASS));
        chipEqTreble.setOnClickListener(v -> setEq(EarbudCommands.EQ_TREBLE));
        rowLdac = findViewById(R.id.row_ldac);
        tvLdacState = findViewById(R.id.tv_ldac_state);
        // 协议值 1=开、0=关、-1=未知；点击时按「当前是否开启」取反，切换前二次确认
        rowLdac.setOnClickListener(v -> confirmLdac(state.ldacOn != EarbudCommands.SWITCH_ON));
        row3d = findViewById(R.id.row_3d);
        tvAudio3dState = findViewById(R.id.tv_3d_state);
        row3d.setOnClickListener(v -> {
            if (state.ldacOn == EarbudCommands.SWITCH_ON) {
                appendLog("LDAC 开启时空间音频不可用，需先关闭 LDAC");
                return;
            }
            setAudio3d(state.audio3d != EarbudCommands.SWITCH_ON);
        });

        // ---- 快捷操作 ----
        chipTwiceL = findViewById(R.id.chip_twice_l);
        chipTwiceR = findViewById(R.id.chip_twice_r);
        chipThreeL = findViewById(R.id.chip_three_l);
        chipThreeR = findViewById(R.id.chip_three_r);
        chipHold = findViewById(R.id.chip_hold);
        chipCycle = findViewById(R.id.chip_cycle_l);
        chipTwiceL.setOnClickListener(v -> pickPinchTwice(true));
        chipTwiceR.setOnClickListener(v -> pickPinchTwice(false));
        chipThreeL.setOnClickListener(v -> pickPinchThree(true));
        chipThreeR.setOnClickListener(v -> pickPinchThree(false));
        chipHold.setOnClickListener(v -> pickHold());
        chipCycle.setOnClickListener(v -> pickHoldCycle());

        // ---- 二级页与调试功能（全部收进开发者选项页）----
        tvInfo = findViewById(R.id.tv_info);
        tvLog = findViewById(R.id.tv_log);
        tvProbe = findViewById(R.id.tv_probe);
        tvSettings = findViewById(R.id.tv_settings);
        etProbeFrom = findViewById(R.id.et_probe_from);
        etProbeTo = findViewById(R.id.et_probe_to);
        etProbeInterval = findViewById(R.id.et_probe_interval);
        etRaw = findViewById(R.id.et_raw);
        logScroll = findViewById(R.id.log_scroll);
        deviceList = findViewById(R.id.device_list);

        // ---- 设置页：重连后自动恢复播放（默认关闭）----
        autoResumePlayback = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getBoolean(KEY_AUTO_RESUME, false);
        rowAutoResume = findViewById(R.id.row_auto_resume);
        tvAutoResumeState = findViewById(R.id.tv_auto_resume_state);
        rowAutoResume.setOnClickListener(v -> {
            autoResumePlayback = !autoResumePlayback;
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                    .putBoolean(KEY_AUTO_RESUME, autoResumePlayback).apply();
            renderAutoResume();
            appendLog("重连后自动恢复播放: " + (autoResumePlayback ? "开" : "关"));
        });
        renderAutoResume();

        findViewById(R.id.btn_read_settings).setOnClickListener(v -> readAllSettings());
        findViewById(R.id.btn_probe).setOnClickListener(v -> startProbe());
        findViewById(R.id.btn_anc_level_scan).setOnClickListener(v -> dumpSettings());
        findViewById(R.id.btn_raw_send).setOnClickListener(v -> sendRawPayload());
        findViewById(R.id.btn_identity).setOnClickListener(v -> channel.sendRaw(EarbudCommands.identityFrame()));
        findViewById(R.id.btn_clear_log).setOnClickListener(v -> {
            logBuf.setLength(0);
            tvLog.setText("");
        });

        channel.setListener(new SppChannel.Listener() {
            @Override
            public void onConnected(String deviceName) {
                connecting = false;
                bannerMsg = null;
                reconnectAttempts = 0;
                mainHandler.removeCallbacks(reconnectTask);
                rememberDevice();
                state.deviceName = deviceName;
                updateBanner();
                refresh();
                // 切换 LDAC 后重连成功：等音频链路恢复再发播放键
                if (resumePlaybackPending) {
                    resumePlaybackPending = false;
                    mainHandler.postDelayed(MainActivity.this::resumePlayback, 1500);
                }
            }

            @Override
            public void onDisconnected(String reason) {
                connecting = false;
                // 断开后旧状态已失效，清空以免界面停留在「已开启/已关闭」的假象
                clearConnectedState();
                ancHintMsg = null;
                // 切换 LDAC 会让耳机重启蓝牙栈、SPP 掉线，自动重连直到耳机恢复
                scheduleAutoReconnect(reason);
            }

            @Override
            public void onFrame(byte[] payload) {
                handleFrame(payload);
            }

            @Override
            public void onLog(String msg) {
                appendLog(msg);
            }
        });

        showPage(PAGE_MAIN);
        updateBanner();

        if (!hasBtPermission()) {
            requestBtPermission();
        } else {
            autoConnect();
        }
    }

    // ------------------------------------------------------------ 页面切换

    private void showPage(int page) {
        currentPage = page;
        pageMain.setVisibility(page == PAGE_MAIN ? View.VISIBLE : View.GONE);
        pageSettings.setVisibility(page == PAGE_SETTINGS ? View.VISIBLE : View.GONE);
        pageDev.setVisibility(page == PAGE_DEV ? View.VISIBLE : View.GONE);
        btnBack.setVisibility(page == PAGE_MAIN ? View.GONE : View.VISIBLE);
        tvPageTitle.setText(page == PAGE_SETTINGS ? "设置与关于"
                : page == PAGE_DEV ? "开发者选项" : "我的X5s Pro");
    }

    @Override
    public void onBackPressed() {
        if (currentPage != PAGE_MAIN) {
            showPage(PAGE_MAIN);
        } else {
            super.onBackPressed();
        }
    }

    // ------------------------------------------------------------ 状态横幅

    /** 断开连接后把耳机相关状态复位为未知，避免界面显示过期值。 */
    private void clearConnectedState() {
        state.deviceName = null;
        state.batteryLeft = -1;
        state.batteryRight = -1;
        state.batteryCase = -1;
        state.chargingLeft = false;
        state.chargingRight = false;
        state.chargingCase = false;
        state.ancMode = -1;
        state.ancLevel = -1;
        state.eqLevel = -1;
        state.ldacOn = -1;
        state.audio3d = -1;
        state.pinchTwiceL = -1;
        state.pinchTwiceR = -1;
        state.pinchThreeL = -1;
        state.pinchThreeR = -1;
        state.holdNormal = -1;
        state.holdCycleL = -1;
        state.holdCycleR = -1;
        pendingAnc = -1;
        mainHandler.removeCallbacks(pendingAncTimeout);
        handshakeSent = false;
    }

    /**
     * 刷新横幅：已连接（绿）/ 连接中（黄）/ 提示（黄）/ 未连接（灰）。
     */
    private void updateBanner() {
        if (channel.isConnected()) {
            banner.setBackgroundResource(R.drawable.bg_banner_ok);
            ivBannerIcon.setImageResource(R.drawable.ic_bluetooth);
            ivBannerIcon.setColorFilter(ContextCompat.getColor(this, R.color.accent));
            tvBannerTitle.setText("已连接");
            tvBannerSub.setText(state.deviceName != null ? state.deviceName : "耳机");
            btnConnect.setVisibility(View.GONE);
            return;
        }
        if (connecting) {
            banner.setBackgroundResource(R.drawable.bg_banner_warn);
            ivBannerIcon.setImageResource(R.drawable.ic_bluetooth);
            ivBannerIcon.setColorFilter(ContextCompat.getColor(this, R.color.warn));
            tvBannerTitle.setText("正在连接…");
            tvBannerSub.setText(target != null ? target.getAddress() : "请稍候");
            btnConnect.setVisibility(View.GONE);
            return;
        }
        btnConnect.setVisibility(View.VISIBLE);
        if (bannerMsg != null) {
            banner.setBackgroundResource(R.drawable.bg_banner_warn);
            ivBannerIcon.setImageResource(R.drawable.ic_info);
            ivBannerIcon.setColorFilter(ContextCompat.getColor(this, R.color.warn));
            tvBannerTitle.setText(bannerMsg);
            tvBannerSub.setText("点击「连接」重试");
            return;
        }
        banner.setBackgroundResource(R.drawable.bg_banner_off);
        ivBannerIcon.setImageResource(R.drawable.ic_bluetooth);
        ivBannerIcon.setColorFilter(ContextCompat.getColor(this, R.color.text_secondary));
        tvBannerTitle.setText("未连接耳机");
        tvBannerSub.setText("点击「连接」选择已配对的耳机");
    }

    // ------------------------------------------------------------ 权限

    private boolean hasBtPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return true;
        }
        for (String p : PERMS_12) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void requestBtPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(this, PERMS_12, REQ_BT_PERM);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_BT_PERM) {
            if (hasBtPermission()) {
                autoConnect();
            } else {
                bannerMsg = "缺少蓝牙权限";
                updateBanner();
            }
        }
    }

    // ------------------------------------------------------------ 设备选择

    @SuppressLint("MissingPermission")
    private List<BluetoothDevice> bondedDevices() {
        List<BluetoothDevice> result = new ArrayList<>();
        BluetoothManager bm = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        if (bm == null || bm.getAdapter() == null) {
            return result;
        }
        Set<BluetoothDevice> bonded = bm.getAdapter().getBondedDevices();
        if (bonded == null) {
            return result;
        }
        for (BluetoothDevice d : bonded) {
            int type = d.getType();
            int major = d.getBluetoothClass() == null ? -1 : d.getBluetoothClass().getMajorDeviceClass();
            boolean audio = major == 0x0400 /* AUDIO_VIDEO */ || type == BluetoothDevice.DEVICE_TYPE_LE
                    || type == BluetoothDevice.DEVICE_TYPE_DUAL;
            if (audio) {
                result.add(d);
            }
        }
        if (result.isEmpty()) {
            result.addAll(bonded);
        }
        return result;
    }

    @SuppressLint("MissingPermission")
    private void autoConnect() {
        // 优先连上次记住的耳机：只有首次安装（无记录）时才需要手动选择
        BluetoothDevice saved = rememberedDevice();
        if (saved != null) {
            startConnect(saved);
            return;
        }
        List<BluetoothDevice> devices = bondedDevices();
        if (devices.isEmpty()) {
            bannerMsg = "没有已配对的蓝牙设备";
            updateBanner();
            return;
        }
        if (devices.size() == 1) {
            startConnect(devices.get(0));
            return;
        }
        renderDeviceList(devices);
    }

    /** 上次成功连接的耳机；已被解配时返回 null。 */
    @SuppressLint("MissingPermission")
    private BluetoothDevice rememberedDevice() {
        String addr = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getString(KEY_LAST_ADDRESS, null);
        if (addr == null) {
            return null;
        }
        for (BluetoothDevice d : bondedDevices()) {
            if (addr.equals(d.getAddress())) {
                return d;
            }
        }
        return null;
    }

    /** 连接成功后记住耳机地址，后续自动连接/重连直接用它。 */
    @SuppressLint("MissingPermission")
    private void rememberDevice() {
        if (target == null) {
            return;
        }
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putString(KEY_LAST_ADDRESS, target.getAddress()).apply();
    }

    @SuppressLint("MissingPermission")
    private void renderDeviceList(List<BluetoothDevice> devices) {
        deviceList.removeAllViews();
        deviceList.setVisibility(View.VISIBLE);
        bannerMsg = "请选择耳机的蓝牙配对条目";
        updateBanner();
        float density = getResources().getDisplayMetrics().density;
        for (BluetoothDevice d : devices) {
            Button b = new Button(this);
            b.setBackgroundResource(R.drawable.bg_chip);
            b.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            b.setAllCaps(false);
            b.setTextSize(14);
            String name = d.getName();
            b.setText((name == null ? "未知设备" : name) + "\n" + d.getAddress());
            b.setOnClickListener(v -> {
                deviceList.setVisibility(View.GONE);
                startConnect(d);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins((int) (8 * density), (int) (8 * density), (int) (8 * density), 0);
            deviceList.addView(b, lp);
        }
    }

    private void pickDevice() {
        if (!hasBtPermission()) {
            requestBtPermission();
            return;
        }
        // 已知是哪副耳机就直接重试，只有从未选过设备时才让用户选择
        BluetoothDevice d = target != null ? target : rememberedDevice();
        if (d != null) {
            startConnect(d);
            return;
        }
        autoConnect();
    }

    /**
     * 断开后自动重连调度：按 3s 间隔重试最多 10 次，期间横幅显示进度；
     * 全部失败才转手动连接（耳机可能已关机/离线）。
     */
    private void scheduleAutoReconnect(String reason) {
        mainHandler.removeCallbacks(reconnectTask);
        if (target == null) {
            bannerMsg = "未连接（" + reason + "）";
            updateUi();
            return;
        }
        if (reconnectAttempts >= RECONNECT_MAX_ATTEMPTS) {
            reconnectAttempts = 0;
            bannerMsg = "自动重连失败（" + reason + "），请确认耳机开机并在附近后点「连接」";
            updateUi();
            appendLog("自动重连已达上限 " + RECONNECT_MAX_ATTEMPTS + " 次，停止重试");
            return;
        }
        reconnectAttempts++;
        bannerMsg = "耳机已断开（" + reason + "），自动重连中 "
                + reconnectAttempts + "/" + RECONNECT_MAX_ATTEMPTS;
        updateUi();
        appendLog("耳机断开（" + reason + "），" + (RECONNECT_DELAY_MS / 1000) + "s 后重连（第 "
                + reconnectAttempts + " 次）");
        mainHandler.postDelayed(reconnectTask, RECONNECT_DELAY_MS);
    }

    private void startConnect(BluetoothDevice device) {
        mainHandler.removeCallbacks(reconnectTask);
        target = device;
        connecting = true;
        bannerMsg = null;
        deviceList.setVisibility(View.GONE);
        updateBanner();
        registerBondReceiver(device);
        channel.connect(device);
    }

    @SuppressLint("MissingPermission")
    private void registerBondReceiver(BluetoothDevice device) {
        unregisterBondReceiver();
        bondReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                BluetoothDevice d = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (d == null || !device.getAddress().equals(d.getAddress())) {
                    return;
                }
                int bond = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1);
                appendLog("配对状态变化: " + bond);
                if (bond == BluetoothDevice.BOND_BONDED) {
                    startConnect(device);
                }
            }
        };
        IntentFilter f = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(bondReceiver, f, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(bondReceiver, f);
        }
    }

    private void unregisterBondReceiver() {
        if (bondReceiver != null) {
            try {
                unregisterReceiver(bondReceiver);
            } catch (IllegalArgumentException ignored) {
            }
            bondReceiver = null;
        }
    }

    // ------------------------------------------------------------ 通信

    private void handleFrame(byte[] payload) {
        if (probe != null && payload.length > 1 && (payload[0] & 0xFF) == 0x2B) {
            probe.onFrame(payload, payload[1] & 0xFF);
        }
        StringBuilder diag = new StringBuilder();
        EarbudCommands.parseAnc(payload, state, diag);
        // 只要耳机回报的模式等于目标就结束「切换中」。
        // 旧逻辑额外要求「本次帧改变了模式」，但耳机常先主动推送 2B 04 把状态更新掉，
        // 随后 2B 2A 应答里模式已等于目标 → 条件不成立，提示就一直卡在「切换中…」。
        if (pendingAnc >= 0 && state.ancMode == pendingAnc) {
            pendingAnc = -1;
            ancHintMsg = null;
            mainHandler.removeCallbacks(pendingAncTimeout);
            appendLog("降噪模式已切换");
        }
        // 耳机主动推送降噪状态（2B 04 01 02 <mode> <level>）时，按官方行为回 ACK
        if (EarbudCommands.parseSettings(payload, state, diag)) {
            appendLog("收到 ANC 上报，回 ACK");
            channel.sendRaw(EarbudCommands.ackFrame(EarbudCommands.CMD_ANC_STATE));
        }
        EarbudCommands.parseDeviceInfo(payload, state, diag);
        CommandCatalog.parseIdentity(payload, state, diag);
        // 属性块：EQ / LDAC / 空间环绕声都走这里（2B 6D/6E、2B 1A/1B、2B 75/76 在本固件上无应答）
        EarbudCommands.Property prop = EarbudCommands.parseProperty(payload);
        if (prop != null) {
            if (prop.serviceIndex == EarbudCommands.EQ_SERVICE_INDEX
                    && prop.propertyIndex == EarbudCommands.EQ_PROPERTY_INDEX) {
                state.eqLevel = prop.value;
                diag.append("EQ=").append(prop.value).append(' ').append(state.eqText()).append('\n');
            } else if (prop.serviceIndex == EarbudCommands.AUDIO_SERVICE_INDEX
                    && prop.propertyIndex == EarbudCommands.LDAC_PROPERTY_INDEX) {
                state.ldacOn = prop.value;
                diag.append("LDAC=").append(prop.value == EarbudCommands.SWITCH_ON ? "开" : "关").append('\n');
            } else if (prop.serviceIndex == EarbudCommands.AUDIO_SERVICE_INDEX
                    && prop.propertyIndex == EarbudCommands.AUDIO3D_PROPERTY_INDEX) {
                state.audio3d = prop.value;
                diag.append("空间环绕=").append(prop.value == EarbudCommands.SWITCH_ON ? "开" : "关").append('\n');
            } else if (prop.serviceIndex == EarbudCommands.PINCH_SERVICE_INDEX) {
                applyPinch(prop.propertyIndex, prop.value);
                diag.append("轻捏 prop=").append(prop.propertyIndex)
                        .append(" value=").append(prop.value).append('\n');
            } else if (prop.serviceIndex == EarbudCommands.HOLD_SERVICE_INDEX) {
                if (prop.propertyIndex == EarbudCommands.HOLD_PROP_NORMAL) {
                    state.holdNormal = prop.value;
                }
                diag.append("捏住 prop=").append(prop.propertyIndex)
                        .append(" value=").append(prop.value).append('\n');
            } else {
                diag.append("属性 service=").append(prop.serviceIndex)
                        .append(" prop=").append(prop.propertyIndex)
                        .append(" value=").append(prop.value).append('\n');
            }
        } else if (EarbudCommands.isPropertyWriteError(payload)) {
            diag.append("属性写入回 7F（官方同样如此，设置实际生效）\n");
        }
        if (diag.length() > 0) {
            appendLog("[RX 解析] " + diag.toString().replace("\n", " | "));
        }
        updateUi();
    }

    private void refresh() {
        if (!channel.isConnected()) {
            BluetoothDevice d = target != null ? target : rememberedDevice();
            if (!connecting && d != null) {
                startConnect(d);
            }
            return;
        }
        appendLog("查询设备信息");
        if (!handshakeSent) {
            handshakeSent = true;
            appendLog("发送官方握手序列（01 27 / 01 3a / 01 3e / 01 07）");
            android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
            byte[][] hs = EarbudCommands.handshakeFrames();
            for (int i = 0; i < hs.length; i++) {
                final byte[] f = hs[i];
                h.postDelayed(() -> channel.sendRaw(f), i * 250L);
            }
            h.postDelayed(() -> {
                channel.sendRaw(EarbudCommands.deviceInfoFrame());
                channel.sendRaw(EarbudCommands.ancQueryFrame());
                channel.sendRaw(EarbudCommands.holdCycleQueryFrame());
                readAllSettings(0);
            }, hs.length * 250L + 300L);
            updateUi();
            return;
        }
        channel.sendRaw(EarbudCommands.deviceInfoFrame());
        channel.sendRaw(EarbudCommands.ancQueryFrame());
        channel.sendRaw(EarbudCommands.holdCycleQueryFrame());
        readAllSettings(0);
        if (target != null) {
            int b = com.myx5spro.control.proto.BatteryReader.queryDeviceBattery(target);
            if (b >= 0) {
                state.batteryLeft = b;
                state.batteryRight = b;
            }
        }
        updateUi();
    }

    /** 逐个候选子命令做「写 01 → 回读」对比，找出哪些是可写的单值开关。 */
    private void dumpSettings() {
        if (!channel.isConnected()) {
            appendLog("未连接，无法读取设置");
            return;
        }
        int[] cmds = {0x09, 0x0B, 0x0C, 0x0D, 0x10, 0x11, 0x18, 0x19, 0x25,
                0x2A, 0x39, 0x3B, 0x73, 0x74, 0x76, 0x77, 0x79};
        android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
        for (int i = 0; i < cmds.length; i++) {
            final int c = cmds[i];
            h.postDelayed(() -> {
                appendLog("== 查询 2B " + String.format("%02X", c) + " ==");
                channel.sendRaw(EarbudCommands.subCmdQueryFrame(c));
            }, i * 600L);
        }
    }

    /**
     * 设置降噪等级（智能/轻度/中度/深度），并自动切到「降噪」模式。
     *
     * <p>官方专用命令 {@code 2B 08 01 01 <level>}（反编译 setAncLevel）；
     * 备选老路径 {@code 2B 04 01 02 01 <level>}。
     */
    private void setAncLevel(int level) {
        if (!channel.isConnected()) {
            appendLog("未连接，无法设置降噪等级");
            return;
        }
        appendLog("设置降噪等级: " + level + "（2B 08）");
        pendingAnc = EarbudCommands.ANC_ON;
        mainHandler.removeCallbacks(pendingAncTimeout);
        mainHandler.postDelayed(pendingAncTimeout, 2500);
        android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
        channel.sendRaw(EarbudCommands.ancLevelSetFrame(level));
        h.postDelayed(() -> channel.sendRaw(EarbudCommands.setAncFrame(EarbudCommands.ANC_ON, level)), 400);
        h.postDelayed(() -> channel.sendRaw(EarbudCommands.ancQueryFrame()), 900);
    }

    // ------------------------------------------------------------ EQ / LDAC / 空间音频 / 快捷操作

    private void setEq(int v) {
        if (!channel.isConnected()) {
            appendLog("未连接，无法设置 EQ");
            return;
        }
        appendLog("设置 EQ: " + v + "（01 39 属性块 service=3 prop=5）");
        channel.sendRaw(EarbudCommands.eqSetFrame(v));
        // 本固件写完属性后不再应答 01 3A 回读，先按写入值同步；若固件补发回读会覆盖
        state.eqLevel = v;
        updateUi();
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                () -> channel.sendRaw(EarbudCommands.eqQueryFrame()), 500);
    }

    /** LDAC 切换二次确认：切换会让耳机重启蓝牙，并自动关闭空间音频（与官方一致）。 */
    private void confirmLdac(boolean on) {
        if (!channel.isConnected()) {
            appendLog("未连接，无法设置 LDAC");
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(on ? "开启 LDAC？" : "关闭 LDAC？")
                .setMessage("切换 LDAC 会让耳机短暂重启，是否继续？"
                        + "注:空间音频和双设备连接无法和LDAC同时生效。")
                .setPositiveButton("继续", (d, w) -> setLdac(on))
                .setNegativeButton("取消", null)
                .show();
    }

    private void setLdac(boolean on) {
        if (!channel.isConnected()) {
            appendLog("未连接，无法设置 LDAC");
            return;
        }
        appendLog("设置 LDAC: " + (on ? "开" : "关"));
        // 切换会让耳机重启并打断播放：此刻记录是否在播放，重连成功后按设置恢复
        if (autoResumePlayback && isMusicPlaying()) {
            resumePlaybackPending = true;
            appendLog("检测到正在播放，重连后将自动恢复播放");
        }
        channel.sendRaw(EarbudCommands.ldacSetFrame(on));
        state.ldacOn = on ? EarbudCommands.SWITCH_ON : EarbudCommands.SWITCH_OFF;
        if (on) {
            // 耳机侧开 LDAC 会自动关掉空间音频，界面同步，避免显示过期状态
            state.audio3d = EarbudCommands.SWITCH_OFF;
        }
        updateUi();
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                () -> channel.sendRaw(EarbudCommands.ldacQueryFrame()), 500);
    }

    private void setAudio3d(boolean on) {
        if (!channel.isConnected()) {
            appendLog("未连接，无法设置空间环绕声");
            return;
        }
        appendLog("设置空间环绕声: " + (on ? "开" : "关"));
        channel.sendRaw(EarbudCommands.audio3dSetFrame(on));
        state.audio3d = on ? EarbudCommands.SWITCH_ON : EarbudCommands.SWITCH_OFF;
        updateUi();
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                () -> channel.sendRaw(EarbudCommands.audio3dQueryFrame()), 500);
    }

    /** 当前是否有音乐在播放（用于判断切换 LDAC 前是否处于播放状态）。 */
    private boolean isMusicPlaying() {
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        return am != null && am.isMusicActive();
    }

    /**
     * 重连完成后恢复播放：向当前活跃的媒体会话发送「播放」媒体键。
     *
     * <p>{@code KEYCODE_MEDIA_PLAY} 是纯播放键（非切换），已在播放时发送无副作用；
     * 若播放器在耳机重启期间自行释放了媒体会话，则可能无响应（平台限制）。
     */
    private void resumePlayback() {
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (am == null) {
            return;
        }
        try {
            am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY));
            am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY));
            appendLog("重连完成，已发送播放键");
        } catch (RuntimeException e) {
            appendLog("发送播放键失败: " + e.getMessage());
        }
    }

    /** 设置页「重连后自动恢复播放」开关文案。 */
    private void renderAutoResume() {
        if (tvAutoResumeState == null) {
            return;
        }
        tvAutoResumeState.setText(autoResumePlayback ? "已开启" : "已关闭");
        tvAutoResumeState.setSelected(autoResumePlayback);
    }

    /** 把 service=4 的应答写入状态。 */
    private void applyPinch(int propertyIndex, int value) {
        switch (propertyIndex) {
            case EarbudCommands.PINCH_PROP_TWICE_LEFT:
                state.pinchTwiceL = value;
                break;
            case EarbudCommands.PINCH_PROP_TWICE_RIGHT:
                state.pinchTwiceR = value;
                break;
            case EarbudCommands.PINCH_PROP_THREE_LEFT:
                state.pinchThreeL = value;
                break;
            case EarbudCommands.PINCH_PROP_THREE_RIGHT:
                state.pinchThreeR = value;
                break;
            default:
                break;
        }
    }

    /** 写一条属性，600ms 后回读同一属性确认。 */
    private void setProperty(String what, byte[] setFrame, byte[] queryFrame) {
        if (!channel.isConnected()) {
            appendLog("未连接，无法设置" + what);
            return;
        }
        appendLog("设置" + what);
        channel.sendRaw(setFrame);
        new android.os.Handler(android.os.Looper.getMainLooper())
                .postDelayed(() -> channel.sendRaw(queryFrame), 600);
    }

    private void setPinchTwice(boolean left, int value) {
        int prop = left ? EarbudCommands.PINCH_PROP_TWICE_LEFT : EarbudCommands.PINCH_PROP_TWICE_RIGHT;
        if (left) {
            state.pinchTwiceL = value;
        } else {
            state.pinchTwiceR = value;
        }
        updateUi();
        setProperty("轻捏两下（" + (left ? "左" : "右") + "）=" + EarbudState.pinchTwiceText(value),
                EarbudCommands.pinchSetFrame(prop, value), EarbudCommands.pinchQueryFrame(prop));
    }

    private void setPinchThree(boolean left, int value) {
        int prop = left ? EarbudCommands.PINCH_PROP_THREE_LEFT : EarbudCommands.PINCH_PROP_THREE_RIGHT;
        if (left) {
            state.pinchThreeL = value;
        } else {
            state.pinchThreeR = value;
        }
        updateUi();
        setProperty("轻捏三下（" + (left ? "左" : "右") + "）=" + EarbudState.pinchThreeText(value),
                EarbudCommands.pinchSetFrame(prop, value), EarbudCommands.pinchQueryFrame(prop));
    }

    private void setHoldNormal(int value) {
        state.holdNormal = value;
        updateUi();
        setProperty("捏住2秒=" + EarbudState.holdText(value),
                EarbudCommands.holdSetFrame(EarbudCommands.HOLD_PROP_NORMAL, value),
                EarbudCommands.holdQueryFrame(EarbudCommands.HOLD_PROP_NORMAL));
    }

    /**
     * 设置「捏住 = 噪声控制」时的轮换模式。
     *
     * <p>真机实测：只有左耳的写入生效，且写入后左右耳会同步成同一模式（与官方 APP 一致），
     * 因此界面只保留一个按钮，写入时左右两个字段都发同一个值。
     *
     * <p>只发写帧即可。耳机回 {@code 2B 18 03 01 00} 是 ACK（tag3=状态 0=成功）；
     * 手机不能发这条帧，否则写入被打回默认值（回读恒 2、行为不变）。
     * 官方写后约 0.7s 回读即得新值，这里 800ms 后自动回读。
     */
    private void setHoldCycle(int value) {
        if (!channel.isConnected()) {
            appendLog("未连接，无法设置捏住轮换模式");
            return;
        }
        appendLog("设置捏住轮换=" + EarbudState.holdCycleText(value));
        channel.sendRaw(EarbudCommands.holdCycleSetFrame(value, value));
        state.holdCycleL = value;
        state.holdCycleR = value;
        updateUi();
        new android.os.Handler(android.os.Looper.getMainLooper())
                .postDelayed(() -> channel.sendRaw(EarbudCommands.holdCycleQueryFrame()), 800);
    }

    /** 读取官方全部设置项（EQ/LDAC/空间音频/轻捏/捏住）。delayMs 用于连接后排队，避免与握手撞车。 */
    private void readAllSettings(long delayMs) {
        if (!channel.isConnected()) {
            appendLog("未连接，无法读取设置");
            return;
        }
        appendLog("== 读取全部设置 ==");
        byte[][] queries = {
                EarbudCommands.eqQueryFrame(),
                EarbudCommands.ldacQueryFrame(),
                EarbudCommands.audio3dQueryFrame(),
                EarbudCommands.pinchQueryFrame(EarbudCommands.PINCH_PROP_TWICE_LEFT),
                EarbudCommands.pinchQueryFrame(EarbudCommands.PINCH_PROP_TWICE_RIGHT),
                EarbudCommands.pinchQueryFrame(EarbudCommands.PINCH_PROP_THREE_LEFT),
                EarbudCommands.pinchQueryFrame(EarbudCommands.PINCH_PROP_THREE_RIGHT),
                EarbudCommands.holdQueryFrame(EarbudCommands.HOLD_PROP_NORMAL),
                EarbudCommands.holdCycleQueryFrame(),
        };
        android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
        for (int i = 0; i < queries.length; i++) {
            final byte[] f = queries[i];
            h.postDelayed(() -> channel.sendRaw(f), delayMs + i * 350L);
        }
    }

    private void readAllSettings() {
        readAllSettings(0);
    }

    private void setAnc(int mode) {
        if (!channel.isConnected()) {
            appendLog("未连接，无法设置降噪");
            return;
        }
        appendLog("设置降噪模式: " + mode);
        pendingAnc = mode;
        ancHintMsg = null;
        mainHandler.removeCallbacks(pendingAncTimeout);
        mainHandler.postDelayed(pendingAncTimeout, 2500);
        // 与官方一致：写 2B 04 → 回读 2B 2A
        channel.sendRaw(EarbudCommands.setAncFrame(mode));
        mainHandler.postDelayed(() -> channel.sendRaw(EarbudCommands.ancQueryFrame()), 500);
        updateUi();
    }

    private void sendRawPayload() {
        if (!channel.isConnected()) {
            appendLog("未连接，无法发送");
            return;
        }
        String text = etRaw.getText().toString().replaceAll("[^0-9A-Fa-f]", "");
        if (text.length() < 2 || text.length() % 2 != 0) {
            appendLog("十六进制格式无效: " + text);
            return;
        }
        byte[] payload = new byte[text.length() / 2];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) Integer.parseInt(text.substring(i * 2, i * 2 + 2), 16);
        }
        etRaw.clearFocus();
        channel.sendRaw(MbbProtocol.frame(payload));
    }

    private void startProbe() {
        if (!channel.isConnected()) {
            appendLog("未连接，无法探测");
            return;
        }
        int from;
        int to;
        long interval;
        try {
            from = Integer.parseInt(etProbeFrom.getText().toString().trim(), 16);
            to = Integer.parseInt(etProbeTo.getText().toString().trim(), 16);
            interval = Long.parseLong(etProbeInterval.getText().toString().trim());
        } catch (NumberFormatException e) {
            appendLog("探测参数无效");
            return;
        }
        etProbeFrom.clearFocus();
        etProbeTo.clearFocus();
        etProbeInterval.clearFocus();
        if (probe != null) {
            probe.stop();
        }
        probe = new CommandProbe(channel);
        tvProbe.setText("");
        appendLog("开始子命令探测 1.." + Integer.toHexString(to));
        probe.scan(from, to, interval, new CommandProbe.Callback() {
            @Override
            public void onProgress(int sent, int total, int subCmd, boolean answered) {
                tvProbe.setText("进度 " + sent + "/" + total
                        + "  当前 2B " + String.format("%02X", subCmd)
                        + (answered ? "  有应答" : ""));
            }

            @Override
            public void onDone(String report) {
                tvProbe.setText(report);
                appendLog(report);
            }
        });
    }

    private void startProbe(int from, int to) {
        etProbeFrom.setText(String.format("%02X", from));
        etProbeTo.setText(String.format("%02X", to));
        startProbe();
    }

    // ------------------------------------------------------------ 选项弹窗

    /** 通用单选弹窗：names/values 等长，选中后回调 wire 值。 */
    private void showChoice(String title, CharSequence[] names, int[] values, int current,
                            IntConsumer onPick) {
        int checked = -1;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == current) {
                checked = i;
                break;
            }
        }
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setSingleChoiceItems(names, checked, (dialog, which) -> {
                    dialog.dismiss();
                    onPick.accept(values[which]);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void pickPinchTwice(final boolean left) {
        showChoice("轻捏两下 · " + (left ? "左耳" : "右耳"),
                new CharSequence[]{"下一首", "唤醒语音助手", "无操作"},
                new int[]{EarbudCommands.PINCH_TWICE_NEXT,
                        EarbudCommands.PINCH_TWICE_VOICE,
                        EarbudCommands.PINCH_NONE},
                left ? state.pinchTwiceL : state.pinchTwiceR,
                v -> setPinchTwice(left, v));
    }

    private void pickPinchThree(final boolean left) {
        showChoice("轻捏三下 · " + (left ? "左耳" : "右耳"),
                new CharSequence[]{"上一首", "游戏模式", "无操作"},
                new int[]{EarbudCommands.PINCH_THREE_PREV,
                        EarbudCommands.PINCH_THREE_GAME,
                        EarbudCommands.PINCH_NONE},
                left ? state.pinchThreeL : state.pinchThreeR,
                v -> setPinchThree(left, v));
    }

    private void pickHold() {
        showChoice("捏住 2 秒（非来电时）",
                new CharSequence[]{"噪声控制", "无操作"},
                new int[]{EarbudCommands.HOLD_NOISE_CTL, EarbudCommands.HOLD_NONE},
                state.holdNormal,
                this::setHoldNormal);
    }

    private void pickHoldCycle() {
        int current = state.holdCycleL >= 0 ? state.holdCycleL : state.holdCycleR;
        showChoice("捏住轮换",
                new CharSequence[]{"两个模式（降噪+关闭）",
                        "三个模式（降噪/关闭/透传）",
                        "两个模式（降噪+透传）",
                        "两个模式（关闭+透传）"},
                new int[]{EarbudCommands.HOLD_CYCLE_ANC_OFF,
                        EarbudCommands.HOLD_CYCLE_ALL_THREE,
                        EarbudCommands.HOLD_CYCLE_ANC_TRANS,
                        EarbudCommands.HOLD_CYCLE_OFF_TRANS},
                current,
                this::setHoldCycle);
    }

    // ------------------------------------------------------------ UI 渲染

    private void updateUi() {
        updateBanner();

        // 电量三列
        renderBattery(tvBatLeft, barBatLeft, tvBatLeftState, state.batteryLeft, state.chargingLeft);
        renderBattery(tvBatRight, barBatRight, tvBatRightState, state.batteryRight, state.chargingRight);
        renderBattery(tvBatCase, barBatCase, tvBatCaseState, state.batteryCase, state.chargingCase);

        // 降噪分段主控
        segAncOff.setSelected(state.ancMode == EarbudCommands.ANC_OFF);
        segAncOn.setSelected(state.ancMode == EarbudCommands.ANC_ON);
        segAncTrans.setSelected(state.ancMode == EarbudCommands.ANC_TRANSPARENCY);
        rowAncLevel.setVisibility(
                state.ancMode == EarbudCommands.ANC_ON ? View.VISIBLE : View.GONE);
        chipLevelSmart.setSelected(state.ancLevel == EarbudCommands.LEVEL_SMART);
        chipLevelLight.setSelected(state.ancLevel == EarbudCommands.LEVEL_LIGHT);
        chipLevelMedium.setSelected(state.ancLevel == EarbudCommands.LEVEL_MEDIUM);
        chipLevelDeep.setSelected(state.ancLevel == EarbudCommands.LEVEL_DEEP);
        if (ancHintMsg != null) {
            tvAncHint.setText(ancHintMsg);
        } else if (pendingAnc >= 0) {
            tvAncHint.setText("切换中…");
        } else if (!channel.isConnected()) {
            tvAncHint.setText("连接耳机后可用");
        } else {
            tvAncHint.setText("");
        }

        // 音效
        chipEqStd.setSelected(state.eqLevel == EarbudCommands.EQ_STANDARD);
        chipEqBass.setSelected(state.eqLevel == EarbudCommands.EQ_BASS);
        chipEqTreble.setSelected(state.eqLevel == EarbudCommands.EQ_TREBLE);
        setToggle(tvLdacState, state.ldacOn);
        if (state.ldacOn == EarbudCommands.SWITCH_ON) {
            // 与官方一致：LDAC 与空间音频互斥，开 LDAC 时空间音频不可用
            tvAudio3dState.setText("LDAC 下不可用");
            tvAudio3dState.setSelected(false);
        } else {
            setToggle(tvAudio3dState, state.audio3d);
        }

        // 快捷操作（芯片文本 = 当前设置）
        chipTwiceL.setText(state.pinchTwiceL < 0 ? "未读取"
                : EarbudState.pinchTwiceText(state.pinchTwiceL));
        chipTwiceR.setText(state.pinchTwiceR < 0 ? "未读取"
                : EarbudState.pinchTwiceText(state.pinchTwiceR));
        chipThreeL.setText(state.pinchThreeL < 0 ? "未读取"
                : EarbudState.pinchThreeText(state.pinchThreeL));
        chipThreeR.setText(state.pinchThreeR < 0 ? "未读取"
                : EarbudState.pinchThreeText(state.pinchThreeR));
        chipHold.setText(state.holdNormal < 0 ? "未读取"
                : EarbudState.holdText(state.holdNormal));
        chipCycle.setText(holdCycleShort(state.holdCycleL >= 0
                ? state.holdCycleL : state.holdCycleR));

        // 设置页：设备信息
        StringBuilder info = new StringBuilder();
        if (state.deviceName != null) {
            info.append("设备: ").append(state.deviceName).append('\n');
        }
        if (state.firmwareVersion != null) {
            info.append("固件: ").append(state.firmwareVersion).append('\n');
        }
        if (state.model != null) {
            info.append("型号: ").append(state.model)
                    .append("  配色: ").append(state.color)
                    .append("  prodId: ").append(state.prodId).append('\n');
        }
        if (state.rawTlvs != null) {
            info.append("原始 TLV:\n").append(state.rawTlvs);
        }
        tvInfo.setText(info.length() == 0 ? "暂无数据" : info.toString());
        String s = state.settingsText();
        if (tvSettings != null && s.length() > 0) {
            tvSettings.setText(s);
        }
    }

    /** 渲染一列电量：未知显示 --，充电中在下方标注。 */
    private void renderBattery(TextView tv, ProgressBar bar, TextView stateTv,
                               int value, boolean charging) {
        if (value < 0) {
            tv.setText("--");
            bar.setProgress(0);
            stateTv.setText("");
            return;
        }
        tv.setText(value + "%");
        bar.setProgress(value);
        stateTv.setText(charging ? "充电中" : "");
    }

    /** 开关行状态文案：协议里 1=开、0=关、-1=未读取。 */
    private void setToggle(TextView tv, int v) {
        if (v < 0) {
            tv.setText("未读取");
            tv.setSelected(false);
        } else {
            boolean on = v == EarbudCommands.SWITCH_ON;
            tv.setText(on ? "已开启" : "已关闭");
            tv.setSelected(on);
        }
    }

    /** 捏住轮换的短文案（芯片空间有限，完整文案见弹窗与 EarbudState.holdCycleText）。 */
    private static String holdCycleShort(int v) {
        switch (v) {
            case 1:
                return "降噪/关闭";
            case 2:
                return "三模式";
            case 3:
                return "降噪/透传";
            case 4:
                return "关闭/透传";
            default:
                return v < 0 ? "未读取" : "未知";
        }
    }

    private void appendLog(String msg) {
        android.util.Log.i("EarbudCtl", msg);
        logBuf.append(msg).append('\n');
        if (logBuf.length() > 20000) {
            logBuf.delete(0, 10000);
        }
        tvLog.setText(logBuf.toString());
        logScroll.post(() -> logScroll.fullScroll(View.FOCUS_DOWN));
    }

    // ------------------------------------------------------------ 生命周期

    @Override
    protected void onResume() {
        super.onResume();
        // 首次进入由 onCreate 负责连接；之后每次从后台回到前台都自动重连/刷新，
        // 省掉手动点「刷新状态」（官方 APP 常会抢占 SPP 导致回来时连接已断）。
        if (firstResume) {
            firstResume = false;
            return;
        }
        if (!hasBtPermission()) {
            return;
        }
        if (!channel.isConnected()) {
            appendLog("回到前台，自动重新连接耳机");
        }
        refresh();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mainHandler.removeCallbacks(reconnectTask);
        mainHandler.removeCallbacks(pendingAncTimeout);
        channel.disconnect();
        unregisterBondReceiver();
    }
}
