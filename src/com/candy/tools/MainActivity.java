package com.candy.tools;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final String KEY_PASS_THROUGH = "pass_through";

    private static final String SECRET_ACTION = "com.samsung.android.action.SECRET_CODE";
    private static final String SECRET_SUPER = "android_secret_code://27663368378";

    private static final String RIL_PKG = "com.sec.android.RilServiceModeApp";
    private static final String RIL_RECEIVER = RIL_PKG + ".SecKeyStringBroadcastReceiver";

    private static final String TEL_PKG = "com.samsung.android.app.telephonyui";
    private static final String TEL_ACTIVITY = TEL_PKG + ".hiddennetworksetting.MainActivity";


    /** 模拟拔线时长与重新插入后的等待时长，可调。 */

    /** 进度条匀速走完的预估总时长。 */

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final StringBuilder logBuf = new StringBuilder();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.US);
    private final Map<String, String> dump = new LinkedHashMap<String, String>();

    private Handler ui;

    private Button btnTempLock, btnSuperLock;
    private ImageView btnPower;
    private BatteryBarView batteryBar;
    private TextView btnHelp, btnLog, btnAbout, tvHealth, tvCycles, tvDate,
            tvTemp, tvVoltage, tvCurrent, tvCapacity, tvChargeState, tvSignalNet, tvQuality,
            tvUpdated, btnRefreshSignal;
    private LinearLayout tileHealth, tileCycles;


    private volatile boolean rootChecked = false;
    private volatile boolean rootAvailable = false;
    private int cachedBypass = -1;
    private boolean chargingNow = false;

    private volatile boolean batteryPolling = false;
    private Thread batteryThread;
    private volatile boolean refreshing = false;
    private boolean refreshedOnce = false;
    private SignalMonitor.Info lastInfo;

    private String lastBatteryLine = "";
    private String lastNetText = "";

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(ThemeHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ui = new Handler(Looper.getMainLooper());

        btnPower = (ImageView) findViewById(R.id.btn_power);
        batteryBar = (BatteryBarView) findViewById(R.id.battery_bar);
        btnTempLock = (Button) findViewById(R.id.btn_temp_lock);
        btnSuperLock = (Button) findViewById(R.id.btn_super_lock);
        btnHelp = (TextView) findViewById(R.id.btn_help);
        btnLog = (TextView) findViewById(R.id.btn_log);
        btnAbout = (TextView) findViewById(R.id.btn_about);
        tvHealth = (TextView) findViewById(R.id.tv_health);
        tvCycles = (TextView) findViewById(R.id.tv_cycles);
        tvDate = (TextView) findViewById(R.id.tv_date);
        tvTemp = (TextView) findViewById(R.id.tv_temp);
        tvVoltage = (TextView) findViewById(R.id.tv_voltage);
        tvCurrent = (TextView) findViewById(R.id.tv_current);
        tvCapacity = (TextView) findViewById(R.id.tv_capacity);
        tvChargeState = (TextView) findViewById(R.id.tv_charge_state);
        tvSignalNet = (TextView) findViewById(R.id.tv_signal_net);
        tvQuality = (TextView) findViewById(R.id.tv_quality);
        tvUpdated = (TextView) findViewById(R.id.tv_updated);
        btnRefreshSignal = (TextView) findViewById(R.id.btn_refresh_signal);
        tileHealth = (LinearLayout) findViewById(R.id.tile_health);
        tileCycles = (LinearLayout) findViewById(R.id.tile_cycles);

        btnPower.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onToggleBypass();
            }
        });
        findViewById(R.id.tv_title_tools).setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                showEasterEgg();
                return true;
            }
        });
        btnTempLock.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tempLock();
            }
        });
        btnSuperLock.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                log("超级锁频 → 27663368378");
                openSecret(SECRET_SUPER);
            }
        });
        btnHelp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, HelpActivity.class));
            }
        });
        btnLog.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showLogDialog();
            }
        });
        btnAbout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, AboutActivity.class));
            }
        });
        btnRefreshSignal.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                refreshSignal();
            }
        });
        tvQuality.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (lastInfo == null) {
                    return;
                }
                showTextDialog(getString(R.string.quality_dialog_title), lastInfo.detail());
            }
        });
        tileHealth.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDumpDialog();
            }
        });
        tileCycles.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDumpDialog();
            }
        });

        log("就绪 · " + Build.MANUFACTURER + " " + Build.MODEL);
        detectRoot();
        refreshBypass();
        loadBattery();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshBypass();
        loadBattery();
        startBatteryPolling();
        registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (!refreshedOnce) {
            refreshSignal();
        }
    }

    @Override
    protected void onPause() {
        stopBatteryPolling();
        try {
            unregisterReceiver(batteryReceiver);
        } catch (Throwable ignored) {
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        stopBatteryPolling();
        executor.shutdownNow();
        super.onDestroy();
    }

    // ------------------------------------------------------------ 电量与充电状态

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
            chargingNow = plugged != 0;
            float pct = scale > 0 && level >= 0 ? level / (float) scale : 0f;
            batteryBar.setLevel(pct);
            batteryBar.setState(chargingNow, cachedBypass == 1);
            renderChargeState();
        }
    };

    /** 电池下方居中的充电状态文字。 */
    private void renderChargeState() {
        if (tvChargeState == null) {
            return;
        }
        String text;
        int color;
        if (!chargingNow) {
            text = getString(R.string.charge_state_off);
            color = getResources().getColor(R.color.text_secondary);
        } else if (cachedBypass == 1) {
            text = getString(R.string.charge_state_bypass);
            color = 0xFF22C55E;
        } else {
            text = getString(R.string.charge_state_normal);
            color = getResources().getColor(R.color.neon);
        }
        String cap = tvCapacity == null ? null : tvCapacity.getText().toString();
        if (cap != null && !cap.isEmpty() && !"-".equals(cap) && !"\u2014".equals(cap)) {
            text = text + "  \u00b7  " + cap;
        }
        tvChargeState.setText(text);
        tvChargeState.setTextColor(color);
    }

    // ------------------------------------------------------------ 旁路

    private void refreshBypass() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                int value = -1;
                if (isRootAvailable()) {
                    Shell.Result r = Shell.su("settings get system " + KEY_PASS_THROUGH);
                    String v = r.out.trim();
                    value = "1".equals(v) ? 1 : ("0".equals(v) ? 0 : -1);
                }
                final int result = value;
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        renderBypass(result);
                    }
                });
            }
        });
    }

    private void renderBypass(int value) {
        cachedBypass = value;
        boolean on = value == 1;
        boolean known = value == 0 || value == 1;

        int tint = !known
                ? getResources().getColor(R.color.text_secondary)
                : (on ? android.graphics.Color.parseColor("#22C55E")
                      : getResources().getColor(R.color.danger));
        btnPower.setColorFilter(tint);
        batteryBar.setState(chargingNow, on);
        renderChargeState();
    }

    /**
     * 切换旁路（真空隔离法）。
     *
     * 开旁路：先切旁路，再在旁路里关快充降功率，避免 45W 旁路反复断流。
     * 切普通：旁路中先开快充开关（系统暂不协商）→ 假断电冻结协议 →
     *         断电状态下退出旁路 → 复位供电，一次性完成 45W PD 握手。
     * 这样把「改开关」与「触发协商」彻底分开，绕开 sm5443 的 IBUSUCP 死锁。
     */
    /**
     * 切换旁路（真空隔离法）。
     *
     * 整串命令必须在同一条 su -c 内串行执行：shell 层的 sleep 会让 root shell 挂起，
     * 期间不会有任何进程干扰 PMIC；分成多次 Shell.su() 会因进程创建销毁产生时序抖动，
     * 实测无法复现手动命令的修复效果。
     *
     * 开旁路：先进旁路，再在旁路态下降功率（此时系统不协商，安全）。
     * 切普通：旁路态预置快充 → 假断电冻结协议 → 断电态退出旁路 → 复位一次性握手 45W。
     */
    private void onToggleBypass() {
        final int target = cachedBypass == 1 ? 0 : 1;
        setBusy(true);
        log(target == 1 ? "打开旁路…" : "关闭旁路…");
        setStage(target == 1 ? "正在开启旁路" : "正在恢复普通充电");

        executor.execute(new Runnable() {
            @Override
            public void run() {
                final Shell.Result r;
                if (target == 1) {
                    r = Shell.su("settings put system " + KEY_PASS_THROUGH + " 1; "
                            + "sleep 1.5; "
                            + "settings put system adaptive_fast_charging 0; "
                            + "settings put system super_fast_charging 0");
                } else {
                    // 顺序关键：旁路态下预置快充（系统不协商）→ 退出旁路（协议卡在低功率，不断流）
                    // → 最后才断电复位，让 PMIC 第一次协商就按 45W 进行
                    r = Shell.su("settings put system adaptive_fast_charging 1; "
                            + "settings put system super_fast_charging 1; "
                            + "sleep 1; "
                            + "settings put system " + KEY_PASS_THROUGH + " 0; "
                            + "sleep 1.5; "
                            + "dumpsys battery unplug; "
                            + "sleep 4; "
                            + "dumpsys battery reset");
                }
                final boolean ok = r.code == 0;

                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        setBusy(false);
                        clearStage();
                        if (ok) {
                            Toast.makeText(MainActivity.this,
                                    target == 1 ? "旁路充电已启用" : "已恢复普通充电",
                                    Toast.LENGTH_SHORT).show();
                            log(target == 1 ? "旁路已打开" : "旁路已关闭");
                        } else {
                            Toast.makeText(MainActivity.this, "执行异常，点右上「日志」查看",
                                    Toast.LENGTH_LONG).show();
                            log("命令返回 " + r.code + (r.out.isEmpty() ? "" : " · " + r.out));
                        }
                        refreshBypass();
                        loadBattery();
                    }
                });
            }
        });
    }

    /** 切换阶段提示，显示在电池条上方。 */
    private void setStage(final String text) {
        log("· " + text);
        ui.post(new Runnable() {
            @Override
            public void run() {
                if (tvChargeState != null) {
                    tvChargeState.setText(text + "…");
                    tvChargeState.setTextColor(getResources().getColor(R.color.neon));
                }
            }
        });
    }

    private void clearStage() {
        ui.post(new Runnable() {
            @Override
            public void run() {
                renderChargeState();
            }
        });
    }

    // ------------------------------------------------------------ 电池实时（一次性读，1 秒间隔）

    private void startBatteryPolling() {
        stopBatteryPolling();
        final android.os.BatteryManager bm = (android.os.BatteryManager)
                getSystemService(BATTERY_SERVICE);
        if (bm == null) {
            tvChargeState.setText(R.string.charge_state_off);
            return;
        }
        batteryPolling = true;
        batteryThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (batteryPolling) {
                    try {
                        long cur = bm.getLongProperty(
                                android.os.BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
                        long cap = bm.getLongProperty(
                                android.os.BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
                        Intent sticky = registerReceiver(null,
                                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                        int mv = 0;
                        int temp = 0;
                        if (sticky != null) {
                            mv = sticky.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
                            temp = sticky.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
                        }
                        if (batteryPolling) {
                            handleBatteryLine(cur + "|" + mv + "|" + temp + "|" + cap);
                        }
                    } catch (Throwable ignored) {
                    }
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }
        }, "battery-poll");
        batteryThread.start();
    }

    private void stopBatteryPolling() {
        batteryPolling = false;
        if (batteryThread != null) {
            batteryThread.interrupt();
            batteryThread = null;
        }
    }

    private void handleBatteryLine(String line) {
        if (line.equals(lastBatteryLine)) {
            return;
        }
        lastBatteryLine = line;
        String[] p = line.split("\\|", -1);
        if (p.length < 4) {
            return;
        }
        final String cur = fmtCurrent(p[0]);
        final String vol = fmtVoltage(p[1]);
        final String tmp = fmtTemp(p[2]);
        final String cap = fmtCapacity(p[3]);
        final boolean nearZero = isNearZero(p[0]);

        ui.post(new Runnable() {
            @Override
            public void run() {
                tvCapacity.setText(cap);
                renderChargeState();
                tvVoltage.setText(vol);
                tvTemp.setText(tmp);
                tvCurrent.setText(cur);
                tvCurrent.setTextColor(getResources().getColor(
                        nearZero && cachedBypass == 1 ? R.color.neon : R.color.neon_dim));
            }
        });
    }

    private boolean isNearZero(String raw) {
        try {
            long v = Long.parseLong(raw.trim());
            double ma = Math.abs(v) > 20000 ? v / 1000.0 : v;
            return Math.abs(ma) < 150;
        } catch (Throwable t) {
            return false;
        }
    }

    private String fmtCurrent(String raw) {
        try {
            long v = Long.parseLong(raw.trim());
            double ma = Math.abs(v) > 20000 ? v / 1000.0 : v;
            return String.format(Locale.US, "%.1f mA", ma);
        } catch (Throwable t) {
            return "—";
        }
    }

    private String fmtVoltage(String raw) {
        try {
            long v = Long.parseLong(raw.trim());
            double volt = v > 100000 ? v / 1000000.0 : v / 1000.0;
            return String.format(Locale.US, "%.3f V", volt);
        } catch (Throwable t) {
            return "—";
        }
    }

    private String fmtTemp(String raw) {
        try {
            long v = Long.parseLong(raw.trim());
            double c = v > 200 ? v / 10.0 : v;
            return String.format(Locale.US, "%.1f °C", c);
        } catch (Throwable t) {
            return "—";
        }
    }

    private String fmtCapacity(String raw) {
        try {
            long v = Long.parseLong(raw.trim());
            long mah = v > 100000 ? v / 1000 : v;
            return mah + " mAh";
        } catch (Throwable t) {
            return "—";
        }
    }

    // ------------------------------------------------------------ 网络质量（一次性）

    private void refreshSignal() {
        if (refreshing) {
            return;
        }
        refreshing = true;
        refreshedOnce = true;
        btnRefreshSignal.setText("读取中…");
        SignalMonitor.refresh(new SignalMonitor.Callback() {
            @Override
            public void onDone(final SignalMonitor.Info info, final long timeMillis) {
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        refreshing = false;
                        btnRefreshSignal.setText(R.string.btn_refresh);
                        lastInfo = info;
                        updateSignalUi(info, timeMillis);
                    }
                });
            }
        });
    }

    private void updateSignalUi(SignalMonitor.Info info, long timeMillis) {
        String net = info.netLine();
        if (net.isEmpty()) {
            net = "—";
        }
        if (!net.equals(lastNetText)) {
            lastNetText = net;
            tvSignalNet.setText(net);
        }

        int textRes;
        int colorRes;
        if (info.grade == 2) {
            textRes = R.string.quality_text_good;
            colorRes = R.color.neon;
        } else if (info.grade == 1) {
            textRes = R.string.quality_text_mid;
            colorRes = R.color.warn;
        } else if (info.grade == 0) {
            textRes = R.string.quality_text_bad;
            colorRes = R.color.danger;
        } else {
            textRes = R.string.quality_text_none;
            colorRes = R.color.text_secondary;
        }
        tvQuality.setText(textRes);
        tvQuality.setTextColor(getResources().getColor(colorRes));
        tvUpdated.setText(getString(R.string.updated_prefix) + " "
                + timeFormat.format(new Date(timeMillis)));
    }

    // ------------------------------------------------------------ 电池档案（一次性）

    private void loadBattery() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                final boolean root = isRootAvailable();
                final Map<String, String> data;
                if (root) {
                    Shell.Result r = Shell.su("dumpsys battery");
                    data = parseDump(r.code == 0 ? r.out : "");
                } else {
                    data = new LinkedHashMap<String, String>();
                }
                final String health = formatPercent(find(data, "msavedbatteryasoc", "msavedbatterybsoh"));
                final String cycles = formatCycles(find(data, "msavedbatteryusage"));
                final String date = formatDate(find(data, "firstusedate"));

                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        dump.clear();
                        dump.putAll(data);
                        tvHealth.setText(health == null ? "—" : health);
                        tvCycles.setText(cycles == null ? "—" : cycles);
                        tvDate.setText(date == null ? "—" : date);
                    }
                });
            }
        });
    }

    private Map<String, String> parseDump(String text) {
        Map<String, String> map = new LinkedHashMap<String, String>();
        if (TextUtils.isEmpty(text)) {
            return map;
        }
        for (String line : text.split("\n")) {
            String t = line.trim();
            int idx = t.indexOf(':');
            if (idx <= 0) {
                continue;
            }
            String key = t.substring(0, idx).trim();
            String value = t.substring(idx + 1).trim();
            if (value.length() > 1 && value.startsWith("[") && value.endsWith("]")) {
                value = value.substring(1, value.length() - 1).trim();
            }
            if (key.toLowerCase(Locale.US).startsWith("battery ")) {
                key = key.substring(8).trim();
            }
            map.put(key, value);
        }
        return map;
    }

    private String find(Map<String, String> map, String... needles) {
        for (String n : needles) {
            for (Map.Entry<String, String> e : map.entrySet()) {
                if (e.getKey().toLowerCase(Locale.US).replace(" ", "").contains(n)
                        && !TextUtils.isEmpty(e.getValue())) {
                    return e.getValue();
                }
            }
        }
        return null;
    }

    private String formatPercent(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            float f = Float.parseFloat(raw.trim());
            if (f <= 0 || f > 200) {
                return null;
            }
            if (Math.abs(f - Math.rint(f)) < 0.001f) {
                return ((int) Math.rint(f)) + " %";
            }
            return String.format(Locale.US, "%.1f %%", f);
        } catch (Throwable t) {
            return null;
        }
    }

    private String formatCycles(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            float usage = Float.parseFloat(raw.trim());
            if (usage <= 0) {
                return null;
            }
            long cycles = Math.round(usage / 100f);
            if (cycles <= 0) {
                cycles = 1;
            }
            return cycles + " 次";
        } catch (Throwable t) {
            return null;
        }
    }

    private String formatDate(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        if (s.matches("\\d{8,14}")) {
            String y = s.substring(0, 4);
            String m = s.substring(4, 6);
            String d = s.substring(6, 8);
            if ("0000".equals(y)) {
                return null;
            }
            return y + "-" + m + "-" + d;
        }
        if (s.matches("\\d{4}-\\d{2}-\\d{2}.*")) {
            return s.substring(0, 10);
        }
        return null;
    }

    private void showDumpDialog() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : dump.entrySet()) {
            sb.append(e.getKey()).append(": ").append(e.getValue()).append('\n');
        }
        showTextDialog("电池原始数据", sb.length() == 0 ? "暂无可显示的数据" : sb.toString());
    }

    // ------------------------------------------------------------ 锁频

    private void tempLock() {
        log("临时锁频 → hiddennetworksetting");
        setBusy(true);
        executor.execute(new Runnable() {
            @Override
            public void run() {
                boolean started = false;
                try {
                    Intent intent = new Intent();
                    intent.setComponent(new ComponentName(TEL_PKG, TEL_ACTIVITY));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    started = true;
                    log("已打开临时锁频页");
                } catch (Throwable t) {
                    log("直接启动失败：" + t.getClass().getSimpleName());
                }
                if (!started && isRootAvailable()) {
                    Shell.Result r = Shell.su("am start -n " + TEL_PKG + "/" + TEL_ACTIVITY);
                    log("am start exit=" + r.code + (r.out.isEmpty() ? "" : " · " + firstLine(r.out)));
                } else if (!started) {
                    log("无 root 且页面未导出，无法打开");
                }
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        setBusy(false);
                    }
                });
            }
        });
    }

    private void openSecret(final String uri) {
        setBusy(true);
        executor.execute(new Runnable() {
            @Override
            public void run() {
                if (isRootAvailable()) {
                    String cmd = "am broadcast -a " + SECRET_ACTION + " -d " + uri
                            + " -n " + RIL_PKG + "/.SecKeyStringBroadcastReceiver";
                    Shell.Result r = Shell.su(cmd);
                    log("root 广播 exit=" + r.code + (r.out.isEmpty() ? "" : " · " + firstLine(r.out)));
                    if (r.code != 0) {
                        sendSecretFromApp(uri);
                    }
                } else {
                    sendSecretFromApp(uri);
                }
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        setBusy(false);
                    }
                });
            }
        });
    }

    private void sendSecretFromApp(String uri) {
        try {
            Intent intent = new Intent(SECRET_ACTION);
            intent.setData(Uri.parse(uri));
            intent.setComponent(new ComponentName(RIL_PKG, RIL_RECEIVER));
            sendBroadcast(intent);
            log("应用广播已发出（无 root 可能被拦截）");
        } catch (Throwable t) {
            log("广播失败：" + t.getClass().getSimpleName());
        }
    }

    // ------------------------------------------------------------ root / 权限

    private boolean isRootAvailable() {
        if (!rootChecked) {
            rootChecked = true;
            Shell.Result r = Shell.exec(new String[]{"su", "-c", "id"});
            rootAvailable = r.code == 0 && r.out.contains("uid=0");
            log(rootAvailable ? "root：可用" : "root：不可用");
        }
        return rootAvailable;
    }

    private void detectRoot() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                isRootAvailable();
            }
        });
    }

    // ------------------------------------------------------------ 日志 / UI

    private void log(String message) {
        final String line = timeFormat.format(new Date()) + "  " + message + "\n";
        ui.post(new Runnable() {
            @Override
            public void run() {
                logBuf.append(line);
                if (logBuf.length() > 8000) {
                    logBuf.delete(0, logBuf.length() - 8000);
                }
            }
        });
    }

    private String firstLine(String text) {
        int i = text.indexOf('\n');
        return i < 0 ? text : text.substring(0, i);
    }

    private void showLogDialog() {
        showTextDialog("运行日志", logBuf.length() == 0 ? "暂无日志" : logBuf.toString());
    }

    private void showTextDialog(String title, String body) {
        TextView content = new TextView(this);
        content.setText(body);
        content.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        content.setTextColor(getResources().getColor(R.color.text_primary));
        content.setTypeface(Typeface.MONOSPACE);
        content.setPadding(48, 40, 48, 40);
        content.setTextIsSelectable(true);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(getResources().getColor(R.color.tile));
        scroll.addView(content, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setPadding(20, 10, 20, 0);
        wrapper.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 800));

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(wrapper)
                .setPositiveButton("关闭", null)
                .show();
    }

    private void setBusy(final boolean busy) {
        ui.post(new Runnable() {
            @Override
            public void run() {
                btnPower.setEnabled(!busy);
                btnSuperLock.setEnabled(!busy);
                btnTempLock.setEnabled(!busy);
            }
        });
    }

    /** 彩蛋：长按标题里的 Tools 触发。 */
    private void showEasterEgg() {
        startActivity(new Intent(MainActivity.this, ToolsActivity.class));
    }
}
