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
    /** 连接超时看门狗：到点强制关闭仍在阻塞的 socket，让 connect() 抛错返回。 */
    private final java.util.concurrent.ScheduledExecutorService connectWatchdog =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
    /** 单个 UUID 的连接尝试超时（毫秒）。 */
    public static final int CONNECT_TIMEOUT_MS = 8000;
    private final Object writeLock = new Object();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ByteArrayOutputStream pending = new ByteArrayOutputStream();

    /** 连接代数：每次 connect()/disconnect() 递增，旧连接的回调一律作废。 */
    private final java.util.concurrent.atomic.AtomicInteger generation =
            new java.util.concurrent.atomic.AtomicInteger();

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
        // 递增代数：使旧连接的回调全部作废，避免过期 onDisconnected 干扰新一轮连接
        generation.incrementAndGet();
        disconnectInternal();
        executor.execute(() -> runConnect(device, generation.get()));
    }

    @SuppressLint("MissingPermission")
    private void runConnect(BluetoothDevice device, int gen) {
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
            if (gen != generation.get()) {
                return; // 已被新一轮连接取代，立即退出
            }
            BluetoothSocket s = null;
            try {
                log("尝试 UUID " + uuid);
                if (adapter != null) {
                    adapter.cancelDiscovery();
                }
                s = device.createInsecureRfcommSocketToServiceRecord(uuid);
                socket = s;
                final BluetoothSocket attemptSocket = s;
                // 看门狗：connect() 无自带超时，耳机在系统层已连接但串口无响应时可能长时间阻塞，
                // 到点强制关闭 socket 让 connect() 抛错返回
                java.util.concurrent.ScheduledFuture<?> timeout =
                        connectWatchdog.schedule(() -> {
                            log("UUID " + uuid + " 连接超时（" + CONNECT_TIMEOUT_MS + "ms），强制中断");
                            try {
                                attemptSocket.close();
                            } catch (IOException ignored) {
                            }
                        }, CONNECT_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
                boolean connected;
                try {
                    attemptSocket.connect();
                    connected = true;
                } catch (IOException e) {
                    lastError = e;
                    log("UUID " + uuid + " 连接失败: " + e.getMessage());
                    connected = false;
                } finally {
                    timeout.cancel(false);
                }
                if (!connected) {
                    closeSocketOnly(s);
                    continue;
                }
                if (gen != generation.get()) {
                    closeSocketOnly(s);
                    return; // 连接成功瞬间已被新一轮取代，丢弃
                }
                input = s.getInputStream();
                output = s.getOutputStream();
                running = true;
                pending.reset();
                log("连接成功，使用 UUID " + uuid);
                String name = device.getName();
                final String displayName = name == null ? device.getAddress() : name;
                main.post(() -> {
                    if (gen == generation.get() && listener != null) {
                        listener.onConnected(displayName);
                    }
                });
                readLoop(s, gen);
                return;
            } catch (IOException e) {
                lastError = e;
                log("UUID " + uuid + " 连接失败: " + e.getMessage());
                closeSocketOnly(s);
            }
        }

        String reason = lastError == null ? "无可用 UUID" : lastError.getMessage();
        log("全部 UUID 均连接失败: " + reason);
        main.post(() -> {
            if (gen == generation.get() && listener != null) {
                listener.onDisconnected(reason);
            }
        });
    }

    private void readLoop(BluetoothSocket s, int gen) {
        byte[] buf = new byte[512];
        try {
            InputStream in = s.getInputStream();
            while (running && gen == generation.get()) {
                int n = in.read(buf);
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
                        final byte[] p = payload;
                        main.post(() -> {
                            if (gen == generation.get() && listener != null) {
                                listener.onFrame(p);
                            }
                        });
                    }
                }
            }
        } catch (IOException e) {
            if (running && gen == generation.get()) {
                log("读取异常: " + e.getMessage());
            }
        } finally {
            closeSocketOnly(s);
            // 只有当前代数的连接断开才通知上层；被新连接取代的旧连接保持沉默
            if (gen == generation.get()) {
                running = false;
                main.post(() -> {
                    if (gen == generation.get() && listener != null) {
                        listener.onDisconnected("连接已断开");
                    }
                });
            }
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
        // 递增代数：让仍在运行的读循环与连接尝试的回调全部作废
        generation.incrementAndGet();
        disconnectInternal();
    }

    private void disconnectInternal() {
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

    /** 只关闭指定的 socket；共享字段若已指向别的连接则不动，避免误伤新一轮连接。 */
    private void closeSocketOnly(BluetoothSocket s) {
        if (s == null) {
            return;
        }
        try {
            s.close();
        } catch (IOException ignored) {
        }
        if (socket == s) {
            socket = null;
            input = null;
            output = null;
        }
    }

    private void log(String msg) {
        Log.d(TAG, msg);
        if (listener != null) {
            main.post(() -> listener.onLog(msg));
        }
    }
}
