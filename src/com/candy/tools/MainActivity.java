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
    private static final String KEY_SUPER_FAST_CHARGING = "super_fast_charging";
    private static final String KEY_FAST_CHARGING = "adaptive_fast_charging";

    private static final String SECRET_ACTION = "com.samsung.android.action.SECRET_CODE";
    private static final String SECRET_SUPER = "android_secret_code://27663368378";

    private static final String RIL_PKG = "com.sec.android.RilServiceModeApp";
    private static final String RIL_RECEIVER = RIL_PKG + ".SecKeyStringBroadcastReceiver";

    private static final String TEL_PKG = "com.samsung.android.app.telephonyui";
    private static final String TEL_ACTIVITY = TEL_PKG + ".hiddennetworksetting.MainActivity";

    private static final String PREFS = "candy_tools";
    private static final String PREF_NODE_OFF_MS = "node_off_ms";
    private static final String PREF_NODE_ON_MS = "node_on_ms";

    /** 模拟拔线时长与重新插入后的等待时长，可调。 */
    private long NODE_OFF_MS = 2500;
    private long NODE_ON_MS = 3000;

    /** 进度条匀速走完的预估总时长。 */
    private static final long SWITCH_EST_MS = 3200;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final StringBuilder logBuf = new StringBuilder();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.US);
    private final Map<String, String> dump = new LinkedHashMap<String, String>();

    private Handler ui;
    private SharedPreferences prefs;

    private Button btnTempLock, btnSuperLock;
    private ImageView btnPower;
    private BatteryBarView batteryBar;
    private TextView btnHelp, btnLog, btnAbout, tvHealth, tvCycles, tvDate,
            tvTemp, tvVoltage, tvCurrent, tvCapacity, tvChargeState, tvSignalNet, tvQuality,
            tvUpdated, btnRefreshSignal;
    private LinearLayout tileHealth, tileCycles;

    private LinearLayout switchBox;
    private TextView switchText;
    private Runnable progressTicker;

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
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        NODE_OFF_MS = prefs.getLong(PREF_NODE_OFF_MS, 2500);
        NODE_ON_MS = prefs.getLong(PREF_NODE_ON_MS, 3000);

        btnPower = (ImageView) findViewById(R.id.btn_power);
        batteryBar = (BatteryBarView) findViewById(R.id.battery_bar);
        switchBox = (LinearLayout) findViewById(R.id.switch_status_box);
        switchText = (TextView) findViewById(R.id.tv_switch_status);
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
        tvChargeState.setText(text);
        tvChargeState.setTextColor(color);
    }

    // ------------------------------------------------------------ 设置读写

    private boolean canWriteSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.System.canWrite(this);
        }
        return true;
    }

    private int readSystemSetting(String key) {
        try {
            return Settings.System.getInt(getContentResolver(), key, -1);
        } catch (Throwable t) {
            return -2;
        }
    }

    private static final class WriteResult {
        boolean ok;
        String channel = "";
        String detail = "";
    }

    private WriteResult writeSystemSetting(String key, int value) {
        WriteResult res = new WriteResult();

        if (canWriteSettings()) {
            try {
                boolean ret = Settings.System.putInt(getContentResolver(), key, value);
                int now = readSystemSetting(key);
                if (now == value) {
                    res.ok = true;
                    res.channel = "应用权限";
                    return res;
                }
                res.detail = "putInt=" + ret + "，回读=" + now;
            } catch (Throwable t) {
                res.detail = t.getClass().getSimpleName() + "：" + t.getMessage();
            }
        } else {
            res.detail = "没有「修改系统设置」权限";
        }

        if (isRootAvailable()) {
            Shell.Result w = Shell.su("settings put system " + key + " " + value);
            Shell.Result g = Shell.su("settings get system " + key);
            String now = g.out.trim();
            if (String.valueOf(value).equals(now)) {
                res.ok = true;
                res.channel = "root";
                res.detail = "";
            } else {
                res.detail = (res.detail.isEmpty() ? "" : res.detail + "；")
                        + "su exit=" + w.code + "，回读=" + (now.isEmpty() ? "null" : now);
            }
        }
        return res;
    }

    // ------------------------------------------------------------ 旁路

    private void refreshBypass() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                final int apiValue = readSystemSetting(KEY_PASS_THROUGH);
                final int rootValue;
                if (isRootAvailable()) {
                    Shell.Result r = Shell.su("settings get system " + KEY_PASS_THROUGH);
                    String v = r.out.trim();
                    rootValue = "1".equals(v) ? 1 : ("0".equals(v) ? 0 : -1);
                } else {
                    rootValue = -1;
                }
                final int value = apiValue >= 0 ? apiValue : rootValue;
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        renderBypass(value);
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

    private void onToggleBypass() {
        final int target = cachedBypass == 1 ? 0 : 1;
        setBusy(true);
        log(target == 1 ? "打开旁路…" : "关闭旁路…");
        startSwitchProgress(target);

        executor.execute(new Runnable() {
            @Override
            public void run() {
                final WriteResult rr = writeSystemSetting(KEY_PASS_THROUGH, target);
                String extra = null;
                if (rr.ok) {
                    setSwitchStage("调整快充开关");
                    extra = target == 1 ? disableFastCharging() : enableFastCharging();
                    extra = extra + " · " + settleCharging(target);
                }
                final String extraMsg = extra;

                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        setBusy(false);
                        stopSwitchProgress();
                        if (rr.ok) {
                            Toast.makeText(MainActivity.this,
                                    target == 1 ? "旁路充电已启用" : "已恢复普通充电",
                                    Toast.LENGTH_SHORT).show();
                            log((target == 1 ? "旁路已打开" : "旁路已关闭") + "（" + rr.channel + "）"
                                    + (extraMsg == null ? "" : " · " + extraMsg));
                        } else {
                            Toast.makeText(MainActivity.this, "失败，点右上「日志」查看原因", Toast.LENGTH_LONG).show();
                            log("失败：" + rr.detail);
                            if (!canWriteSettings() && !isRootAvailable()) {
                                promptPermission();
                            }
                        }
                        refreshBypass();
                        loadBattery();
                    }
                });
            }
        });
    }

    /** 用充电节点的开关复位充电链路，断开时长贴近真实拔插。 */
    private String settleCharging(int target) {
        String[] nodes = {
                "/sys/class/power_supply/battery/input_suspend",
                "/sys/class/power_supply/battery/batt_slate_mode",
                "/sys/class/power_supply/battery/charge_disable",
        };
        boolean done = false;
        for (String node : nodes) {
            Shell.Result probe = Shell.su("[ -w " + node + " ] && echo OK");
            if (!probe.out.contains("OK")) {
                continue;
            }
            Shell.su("echo 1 > " + node);
            setSwitchStage("断开充电输入");
            sleepQuiet(NODE_OFF_MS);
            Shell.su("echo 0 > " + node);
            setSwitchStage("重新协商充电");
            done = true;
            break;
        }
        sleepQuiet(NODE_ON_MS);
        if (done) {
            return target == 1 ? "已重新握手" : "已恢复充电";
        }
        Shell.su("am broadcast -a android.intent.action.BATTERY_CHANGED"
                + " --ei status 1 --ei plugged 0 --ez present true 2>/dev/null");
        sleepQuiet(NODE_ON_MS);
        return "已发送充电重置";
    }

    private volatile String switchStageText = "";

    private void setSwitchStage(String text) {
        switchStageText = text;
    }

    /** 标题与电池条之间的终端风格进度：按总耗时匀速推进，结束后短暂快进到 100%。 */
    private void startSwitchProgress(final int target) {
        switchStageText = "写入设置";
        final long est = SWITCH_EST_MS;
        final long start = System.currentTimeMillis();
        final String goal = target == 1 ? "切换到旁路" : "恢复普通充电";
        switchBox.setVisibility(View.VISIBLE);
        progressTicker = new Runnable() {
            @Override
            public void run() {
                long spent = System.currentTimeMillis() - start;
                float ratio = spent / (float) est;
                int pct;
                if (ratio <= 1f) {
                    pct = (int) (ratio * 100);
                } else {
                    // 超出预估：最后 2% 在 4 秒内走完
                    pct = 98 + (int) Math.min(2, (spent - est) * 2 / 4000);
                }
                pct = Math.min(99, pct);
                StringBuilder bar = new StringBuilder();
                int filled = pct / 5;
                for (int i = 0; i < 20; i++) {
                    bar.append(i < filled ? '■' : '□');
                }
                switchText.setText(String.format(Locale.US, "> %s · %s [%3d%%] %s",
                        goal, switchStageText, pct, bar));
                if (switchBox.getVisibility() == View.VISIBLE) {
                    ui.postDelayed(this, 100);
                }
            }
        };
        ui.post(progressTicker);
    }

    private void stopSwitchProgress() {
        if (progressTicker != null) {
            ui.removeCallbacks(progressTicker);
            progressTicker = null;
        }
        switchText.setText("> 已完成 [100%] ■■■■■■■■■■■■■■■■■■■■");
        ui.postDelayed(new Runnable() {
            @Override
            public void run() {
                switchBox.setVisibility(View.GONE);
            }
        }, 700);
    }

    private void sleepQuiet(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }

    private String disableFastCharging() {
        WriteResult superFast = writeSystemSetting(KEY_SUPER_FAST_CHARGING, 0);
        WriteResult fast = writeSystemSetting(KEY_FAST_CHARGING, 0);
        StringBuilder sb = new StringBuilder();
        sb.append(superFast.ok ? "超级快充已关" : "超级快充设置失败");
        sb.append(" · ");
        sb.append(fast.ok ? "快速充电已关" : "快速充电设置失败");
        return sb.toString();
    }

    private String enableFastCharging() {
        WriteResult adaptive = writeSystemSetting(KEY_FAST_CHARGING, 1);
        return adaptive.ok ? "自适应快充已开" : "自适应快充设置失败";
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

    private void promptPermission() {
        new AlertDialog.Builder(this)
                .setTitle("需要权限")
                .setMessage("写入旁路开关需要「修改系统设置」权限，或设备已 root。")
                .setPositiveButton("去授权", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
                            intent.setData(Uri.parse("package:" + getPackageName()));
                            startActivity(intent);
                        } catch (Throwable t) {
                            try {
                                startActivity(new Intent(Settings.ACTION_SETTINGS));
                            } catch (Throwable ignored) {
                            }
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
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
