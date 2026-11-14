package com.myx5spro.control.proto;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 子命令枚举：逐个发送 {@code 2B xx 01 00} 查询帧，记录哪些子命令有应答。
 *
 * <p>用于在没有完整协议表的情况下，从真机反推可用的子命令集合。
 */
public class CommandProbe {

    private static final String TAG = "CommandProbe";

    public interface Callback {
        void onProgress(int sent, int total, int subCmd, boolean answered);

        void onDone(String report);
    }

    private final SppChannel channel;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<Integer, String> answered = new LinkedHashMap<>();
    private final List<Integer> noAnswer = new ArrayList<>();
    private volatile boolean stopped;

    public CommandProbe(SppChannel channel) {
        this.channel = channel;
    }

    /** 记录一帧应答（由主界面在 onFrame 中转发）。返回是否与本次探测相关。 */
    public boolean onFrame(byte[] payload, int expectSubCmd) {
        if (payload.length < 2 || (payload[0] & 0xFF) != 0x2B) {
            return false;
        }
        int sub = payload[1] & 0xFF;
        if (sub != expectSubCmd) {
            return false;
        }
        answered.put(sub, MbbProtocol.hex(payload));
        return true;
    }

    public void stop() {
        stopped = true;
    }

    /** 从 from 到 to（含）逐个子命令探测，每条间隔 intervalMs 毫秒。 */
    public void scan(int from, int to, long intervalMs, Callback cb) {
        stopped = false;
        new Thread(() -> {
            int total = to - from + 1;
            int sent = 0;
            for (int sub = from; sub <= to && !stopped; sub++) {
                answered.remove(sub);
                byte[] payload = {0x2B, (byte) sub, 0x01, 0x00};
                channel.sendRaw(MbbProtocol.frame(payload));
                sent++;
                int cur = sub;
                try {
                    Thread.sleep(intervalMs);
                } catch (InterruptedException e) {
                    break;
                }
                boolean ok = answered.containsKey(cur);
                if (!ok) {
                    noAnswer.add(cur);
                }
                int s = sent;
                main.post(() -> cb.onProgress(s, total, cur, ok));
            }
            StringBuilder sb = new StringBuilder();
            sb.append("子命令探测结束：共发 ").append(sent).append(" 条\n");
            sb.append("有应答：\n");
            for (Map.Entry<Integer, String> e : answered.entrySet()) {
                sb.append(String.format("  2B %02X  ->  %s%n", e.getKey(), e.getValue()));
            }
            if (answered.isEmpty()) {
                sb.append("  （无）\n");
            }
            Log.d(TAG, sb.toString());
            main.post(() -> cb.onDone(sb.toString()));
        }, "cmd-probe").start();
    }
}
