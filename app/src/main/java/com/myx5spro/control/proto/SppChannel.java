package com.myx5spro.control.proto;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 通过经典蓝牙 SPP 通道与耳机通信。
 *
 * <p>逆向自 com.hihonor.capability.spp.BluetoothSppImpl：
 * UUID = 00001101-0000-1000-8000-00805F9B34FB（标准串口服务）。
 */
public class SppChannel {

    private static final String TAG = "SppChannel";

    /** 标准 SPP 串口 UUID（兜底）。 */
    public static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    /** 荣耀私有 SPP UUID（MbbConstants.UUID_BR_SPP_UUID_HONOR）。 */
    public static final UUID HONOR_SPP_UUID = UUID.fromString("82ff3820-8411-400c-b85a-55bdb32cf060");
    /** 荣耀旧版（B3）私有 SPP UUID（MbbConstants.UUID_BR_SPP_UUID_B3）。 */
    public static final UUID HONOR_SPP_UUID_B3 = UUID.fromString("c9770a18-4c3d-453a-8aaf-d7ec7bbd2785");

    /**
     * 按荣耀 BrMbbConnection$ConnectThread#chooseUUID 的策略选择 UUID：
     * 优先设备广播的荣耀 UUID，其次 B3，最后兜底标准 SPP。
     */
    @SuppressLint("MissingPermission")
    public static UUID chooseUuid(BluetoothDevice device) {
        try {
            android.os.ParcelUuid[] uuids = device.getUuids();
            if (uuids != null) {
                java.util.Set<String> s = new java.util.HashSet<>();
                for (android.os.ParcelUuid p : uuids) {
                    s.add(p.toString().toLowerCase());
                }
                if (s.contains(HONOR_SPP_UUID.toString().toLowerCase())) {
                    return HONOR_SPP_UUID;
                }
                if (s.contains(HONOR_SPP_UUID_B3.toString().toLowerCase())) {
                    return HONOR_SPP_UUID_B3;
                }
            }
        } catch (Throwable ignored) {
        }
        return SPP_UUID;
    }

    public interface Listener {
        void onConnected(String deviceName);

        void onDisconnected(String reason);

        void onFrame(byte[] payload);

        void onLog(String msg);
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    /** 独立的写线程：readLoop 长期占用唯一的读线程，写入不能共用。 */
    private final ExecutorService sendExecutor = Executors.newSingleThreadExecutor();
    private final Object writeLock = new Object();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ByteArrayOutputStream pending = new ByteArrayOutputStream();

    private BluetoothSocket socket;
    private InputStream input;
    private OutputStream output;
    private volatile boolean running;
    private Listener listener;

    public void setListener(Listener l) {
        this.listener = l;
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected() && running;
    }

    @SuppressLint("MissingPermission")
    public void connect(BluetoothDevice device) {
        disconnect();
        executor.execute(() -> {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            UUID preferred = chooseUuid(device);
            // 候选顺序：优先设备广播的 UUID，随后依次尝试荣耀 UUID 与标准 SPP
            List<UUID> candidates = new ArrayList<>();
            candidates.add(preferred);
            for (UUID u : new UUID[]{HONOR_SPP_UUID, HONOR_SPP_UUID_B3, SPP_UUID}) {
                if (!candidates.contains(u)) {
                    candidates.add(u);
                }
            }

            IOException lastError = null;
            for (UUID uuid : candidates) {
                try {
                    log("尝试 UUID " + uuid);
                    if (adapter != null) {
                        adapter.cancelDiscovery();
                    }
                    socket = device.createInsecureRfcommSocketToServiceRecord(uuid);
                    socket.connect();
                    input = socket.getInputStream();
                    output = socket.getOutputStream();
                    running = true;
                    pending.reset();
                    log("连接成功，使用 UUID " + uuid);
                    String name = device.getName();
                    main.post(() -> {
                        if (listener != null) {
                            listener.onConnected(name == null ? device.getAddress() : name);
                        }
                    });
                    readLoop();
                    return;
                } catch (IOException e) {
                    lastError = e;
                    log("UUID " + uuid + " 连接失败: " + e.getMessage());
                    closeQuietly();
                }
            }

            String reason = lastError == null ? "无可用 UUID" : lastError.getMessage();
            log("全部 UUID 均连接失败: " + reason);
            main.post(() -> {
                if (listener != null) {
                    listener.onDisconnected(reason);
                }
            });
            closeQuietly();
        });
    }

    private void readLoop() {
        byte[] buf = new byte[512];
        try {
            while (running) {
                int n = input.read(buf);
                if (n < 0) {
                    break;
                }
                if (n == 0) {
                    continue;
                }
                byte[] chunk = new byte[n];
                System.arraycopy(buf, 0, chunk, 0, n);
                log("<< " + MbbProtocol.hex(chunk));
                List<byte[]> frames = MbbProtocol.splitFrames(chunk, pending);
                for (byte[] frame : frames) {
                    byte[] payload = MbbProtocol.unwrap(frame);
                    if (payload == null) {
                        log("CRC 校验失败: " + MbbProtocol.hex(frame));
                        continue;
                    }
                    if (listener != null) {
                        // 必须在主线程回调，否则调用方更新 UI 会抛 CalledFromWrongThreadException
                        main.post(() -> {
                            if (listener != null) {
                                listener.onFrame(payload);
                            }
                        });
                    }
                }
            }
        } catch (IOException e) {
            if (running) {
                log("读取异常: " + e.getMessage());
            }
        } finally {
            running = false;
            closeQuietly();
            main.post(() -> {
                if (listener != null) {
                    listener.onDisconnected("连接已断开");
                }
            });
        }
    }

    /** 发送 TLV 列表（自动加帧头与 CRC）。 */
    public void send(List<MbbProtocol.Tlv> tlvs) {
        byte[] frame = MbbProtocol.buildFrame(tlvs);
        sendRaw(frame);
    }

    public void sendRaw(byte[] frame) {
        if (!isConnected()) {
            log("未连接，丢弃发送: " + MbbProtocol.hex(frame));
            return;
        }
        // 注意：readLoop 长期占用 executor 的唯一线程，写入必须走独立线程
        sendExecutor.execute(() -> {
            synchronized (writeLock) {
                try {
                    OutputStream out = output;
                    if (out == null) {
                        log("输出流已关闭，丢弃发送: " + MbbProtocol.hex(frame));
                        return;
                    }
                    out.write(frame);
                    out.flush();
                    log(">> " + MbbProtocol.hex(frame));
                } catch (IOException e) {
                    log("发送失败: " + e.getMessage());
                }
            }
        });
    }

    public void disconnect() {
        running = false;
        closeQuietly();
    }

    private void closeQuietly() {
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
        socket = null;
        input = null;
        output = null;
    }

    private void log(String msg) {
        Log.d(TAG, msg);
        if (listener != null) {
            main.post(() -> listener.onLog(msg));
        }
    }
}
