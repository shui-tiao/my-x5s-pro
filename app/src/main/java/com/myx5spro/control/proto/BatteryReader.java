package com.myx5spro.control.proto;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;

/**
 * 从系统蓝牙协议栈读取耳机电池（HFP AT+IPHONEACCEV 上报）。
 * 逆向参考 com.hihonor.batterycard.batteryservice.hfp.HfpManager。
 */
public final class BatteryReader {

    public static final String ACTION_HFP_BATTERY = "android.bluetooth.headset.profile.action.BATTERY_LEVEL_CHANGED";

    public interface Callback {
        void onBattery(int left, int right, int caseBattery);
    }

    private BatteryReader() {
    }

    /**
     * 通过隐藏 API BluetoothDevice#getBatteryLevel() 反射读取，
     * 不可用时返回 -1。
     */
    public static int queryDeviceBattery(BluetoothDevice device) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Object v = BluetoothDevice.class.getMethod("getBatteryLevel").invoke(device);
                return v instanceof Integer ? (Integer) v : -1;
            }
        } catch (Throwable ignored) {
        }
        return -1;
    }

    public static BroadcastReceiver register(Context ctx, Callback cb) {
        BroadcastReceiver r = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int level = intent.getIntExtra("android.bluetooth.headset.profile.extra.BATTERY_LEVEL", -1);
                if (level >= 0) {
                    cb.onBattery(level, level, -1);
                }
            }
        };
        IntentFilter f = new IntentFilter(ACTION_HFP_BATTERY);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ctx.registerReceiver(r, f, Context.RECEIVER_EXPORTED);
        } else {
            ctx.registerReceiver(r, f);
        }
        return r;
    }
}
