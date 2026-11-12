package com.candy.tools;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 卸载 / 冰冻 列表页。mode=uninstall 或 freeze。 */
public class AppOpsListActivity extends Activity {

    public static final String EXTRA_MODE = "mode";
    public static final String MODE_FREEZE = "freeze";
    public static final String MODE_UNINSTALL = "uninstall";

    private static final int S_ALL = 0;
    private static final int S_USER = 1;
    private static final int S_SYSTEM = 2;

    private static final int F_DEFAULT = 0;
    private static final int F_YES = 1;
    private static final int F_NO = 2;
    private static final int F_ASLEEP = 3;

    /** 冰冻选项卡的状态：与 enabled 值对应。 */
    private static final int ST_NORMAL = 0;
    private static final int ST_FROZEN = 3;
    private static final int ST_ASLEEP = 4;

    private static final Pattern P_PKG = Pattern.compile("package:(\\S+)");

    private boolean freezeMode = true;

    private final Set<String> installed = new HashSet<String>();
    private final Set<String> frozenPkgs = new HashSet<String>();
    private final Set<String> uninstalled = new HashSet<String>();
    private final Map<String, String> labels = new HashMap<String, String>();
    private final Map<String, Boolean> systemFlags = new HashMap<String, Boolean>();
    private final Set<String> checked = new HashSet<String>();
    private final Map<String, Integer> enabledState = new HashMap<String, Integer>();
    private final Set<String> livePkgs = new HashSet<String>();
    private final Set<String> sleepingScan = new HashSet<String>();
    private final List<TextView> scopeChips = new ArrayList<TextView>();

    private LinearLayout list;
    private EditText search;
    private TextView filterChip;
    private TextView actionChip;
    private TextView summary;
    private int scope = S_ALL;
    private int filter = F_DEFAULT;
    private String query = "";

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(ThemeHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        freezeMode = MODE_FREEZE.equals(getIntent().getStringExtra(EXTRA_MODE));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getResources().getColor(R.color.bg));
        int pad = dp(16);
        root.setPadding(pad, pad, pad, 0);

        search = new EditText(this);
        search.setHint("搜索应用");
        search.setSingleLine(true);
        search.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        search.setTextColor(getResources().getColor(R.color.text_primary));
        search.setHintTextColor(getResources().getColor(R.color.text_tertiary));
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                query = s.toString().trim().toLowerCase(Locale.US);
                render();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        root.addView(search, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout scopeRow = new LinearLayout(this);
        scopeRow.setOrientation(LinearLayout.HORIZONTAL);
        String[] scopes = freezeMode
                ? new String[]{"全部程序", "用户程序", "系统程序"}
                : new String[]{"系统应用"};
        for (int i = 0; i < scopes.length; i++) {
            final int idx = i;
            TextView chip = chip(scopes[i]);
            chip.setGravity(Gravity.CENTER);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    scope = idx;
                    checked.clear();
                    styleChips();
                    refreshActionChip();
                    render();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) {
                lp.leftMargin = dp(6);
            }
            scopeRow.addView(chip, lp);
            scopeChips.add(chip);
        }
        LinearLayout.LayoutParams scopeLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        scopeLp.topMargin = dp(8);
        root.addView(scopeRow, scopeLp);

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.addView(bulk("全选", "#6750A4", new Runnable() {
            @Override
            public void run() {
                selectAll();
            }
        }));

        filterChip = bulk("默认 ▾", "#7C3AED", new Runnable() {
            @Override
            public void run() {
                showFilterMenu();
            }
        });
        actionRow.addView(filterChip);

        actionChip = bulk("默认", "#7C3AED", new Runnable() {
            @Override
            public void run() {
                onAction();
            }
        });
        actionRow.addView(actionChip);

        HorizontalScrollView actionScroll = new HorizontalScrollView(this);
        actionScroll.setHorizontalScrollBarEnabled(false);
        actionScroll.addView(actionRow);
        LinearLayout.LayoutParams aLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        aLp.topMargin = dp(8);
        root.addView(actionScroll, aLp);

        summary = new TextView(this);
        summary.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        summary.setTextColor(getResources().getColor(R.color.text_tertiary));
        LinearLayout.LayoutParams sumLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sumLp.topMargin = dp(6);
        root.addView(summary, sumLp);

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(list);
        LinearLayout.LayoutParams lLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        lLp.topMargin = dp(6);
        root.addView(scroll, lLp);

        setContentView(root);
        styleChips();
        updateActionChip();
        load();
    }

    private TextView chip(String text) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setPadding(dp(6), dp(8), dp(6), dp(8));
        return v;
    }

    private TextView bulk(String text, String color, final Runnable action) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setTextColor(android.graphics.Color.parseColor(color));
        v.setBackgroundResource(R.drawable.btn_ghost);
        v.setPadding(dp(10), dp(6), dp(10), dp(6));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(6);
        v.setLayoutParams(lp);
        v.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                action.run();
            }
        });
        return v;
    }

    private void showFilterMenu() {
        String[] names = freezeMode
                ? (sleepAllowed()
                    ? new String[]{"默认", "已冻", "未冻", "睡眠"}
                    : new String[]{"默认", "已冻", "未冻"})
                : new String[]{"默认", "已卸", "未卸"};
        new AlertDialog.Builder(this)
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        filter = which;
                        filterChip.setText(names[which] + " ▾");
                        checked.clear();
                        updateActionChip();
                        render();
                    }
                })
                .show();
    }

    /** 睡眠/唤醒只在「用户程序」页签下提供。 */
    private boolean sleepAllowed() {
        return freezeMode && scope == S_USER;
    }

    private void refreshActionChip() {
        if (freezeMode && filter == F_ASLEEP && !sleepAllowed()) {
            filter = F_DEFAULT;
            filterChip.setText("默认 ▾");
        }
        if (freezeMode && filter == F_ASLEEP) {
            actionChip.setText("执行解冻");
            actionChip.setTextColor(android.graphics.Color.parseColor("#22C55E"));
            return;
        }
        updateActionChip();
    }

    private void updateActionChip() {
        if (filter == F_DEFAULT) {
            actionChip.setText("默认");
            actionChip.setTextColor(android.graphics.Color.parseColor("#7C3AED"));
        } else {
            actionChip.setText(actionLabel());
            String label = actionLabel();
            boolean green = label.contains("解冻") || label.contains("安装")
                    || label.contains("唤醒");
            actionChip.setTextColor(android.graphics.Color.parseColor(
                    green ? "#22C55E" : "#EF4444"));
        }
    }

    private String actionLabel() {
        if (freezeMode) {
            if (filter == F_ASLEEP) {
                return "执行解冻";
            }
            return filter == F_YES ? "执行解冻" : "执行冰冻";
        }
        return filter == F_YES ? "执行安装" : "执行卸载";
    }

    /** 默认筛选下的动作，与动作后缀一一对应；非用户程序页签不提供睡眠。 */
    private String[] defaultOps() {
        return sleepAllowed()
                ? new String[]{"解冻", "冰冻", "睡眠"}
                : new String[]{"解冻", "冰冻"};
    }

    private void onAction() {
        final List<String> targets = new ArrayList<String>(checked);
        if (targets.isEmpty()) {
            Toast.makeText(this, "请先勾选应用（可点全选）", Toast.LENGTH_SHORT).show();
            return;
        }
        if (filter == F_DEFAULT) {
            String[] ops = freezeMode
                    ? defaultOps()
                    : new String[]{"执行卸载", "执行安装"};
            new AlertDialog.Builder(this)
                    .setItems(ops, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (freezeMode) {
                                confirm(defaultOps()[which], targets);
                            } else {
                                confirm(which == 0 ? "卸载" : "安装", targets);
                            }
                        }
                    })
                    .show();
            return;
        }
        String op = actionLabel().replace("执行", "");
        confirm(op, targets);
    }

    private void confirm(final String op, final List<String> targets) {
        new AlertDialog.Builder(this)
                .setMessage("是否" + op + "所选 " + targets.size() + " 项？")
                .setNegativeButton("取消", null)
                .setPositiveButton("继续", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        runOp(op, targets);
                    }
                })
                .show();
    }

    private void runOp(final String op, final List<String> targets) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                int ok = 0;
                for (String pkg : targets) {
                    String cmd;
                    if ("解冻".equals(op)) {
                        cmd = "pm unsuspend --user 0 " + pkg + " >/dev/null 2>&1; "
                                + "am set-inactive " + pkg + " false >/dev/null 2>&1; "
                                + "pm enable " + pkg;
                    } else if ("冰冻".equals(op)) {
                        cmd = "pm disable-user --user 0 " + pkg;
                    } else if ("睡眠".equals(op)) {
                        cmd = "pm suspend --user 0 " + pkg;
                    } else if ("安装".equals(op)) {
                        cmd = "pm install-existing " + pkg;
                    } else {
                        cmd = "pm uninstall --user 0 " + pkg;
                    }
                    if (Shell.su(cmd).code == 0) {
                        ok++;
                    }
                }
                final int success = ok;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        checked.clear();
                        Toast.makeText(AppOpsListActivity.this,
                                op + "完成：" + success + "/" + targets.size(),
                                Toast.LENGTH_SHORT).show();
                        load();
                    }
                });
            }
        }, "ops-run").start();
    }

    private void styleChips() {
        for (int i = 0; i < scopeChips.size(); i++) {
            boolean active = i == scope;
            scopeChips.get(i).setTextColor(getResources().getColor(
                    active ? R.color.neon : R.color.text_secondary));
            scopeChips.get(i).setBackgroundResource(
                    active ? R.drawable.chip_bg : R.drawable.card_bg);
        }
    }

    private void load() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                installed.clear();
                frozenPkgs.clear();
                uninstalled.clear();
                labels.clear();
                systemFlags.clear();
                enabledState.clear();
                livePkgs.clear();
                sleepingScan.clear();

                Set<String> all = queryPackages("pm list packages -u --user 0");
                installed.addAll(queryPackages("pm list packages --user 0"));
                frozenPkgs.addAll(queryPackages("pm list packages -d --user 0"));
                for (String p : all) {
                    if (!installed.contains(p)) {
                        uninstalled.add(p);
                    }
                }

                if (freezeMode) {
                    sleepingScan.clear();
                    for (String pkg : installed) {
                        if (!Boolean.TRUE.equals(systemFlags.get(pkg))) {
                            sleepingScan.add(pkg);
                        }
                    }
                }

                PackageManager pm = getPackageManager();
                Map<String, String> paths = queryPaths();
                for (String pkg : installed) {
                    try {
                        ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
                        labels.put(pkg, String.valueOf(info.loadLabel(pm)));
                    } catch (Throwable ignored) {
                        labels.put(pkg, pkg);
                    }
                    String path = paths.get(pkg);
                    systemFlags.put(pkg, path == null ? isSystemLike(pkg) : isSystemPath(path));
                }
                for (String pkg : uninstalled) {
                    String path = paths.get(pkg);
                    labels.put(pkg, pkg);
                    systemFlags.put(pkg, path == null ? isSystemLike(pkg) : isSystemPath(path));
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        render();
                    }
                });

                if (freezeMode && !sleepingScan.isEmpty()) {
                    queryStatesOf(sleepingScan);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            render();
                        }
                    });
                }
            }
        }, "ops-load").start();
    }

    private Map<String, String> queryPaths() {
        Map<String, String> result = new HashMap<String, String>();
        Shell.Result r = Shell.su("pm list packages -f -u --user 0");
        for (String line : r.out.split("\n")) {
            String t = line.trim();
            if (!t.startsWith("package:")) {
                continue;
            }
            String body = t.substring("package:".length());
            int eq = body.lastIndexOf('=');
            if (eq <= 0) {
                continue;
            }
            result.put(body.substring(eq + 1).trim(), body.substring(0, eq).trim());
        }
        return result;
    }

    private boolean isSystemPath(String path) {
        if (path == null) {
            return false;
        }
        String p = path.startsWith("/data") ? null : path;
        if (p == null) {
            return false;
        }
        return path.startsWith("/system/") || path.startsWith("/system_ext/")
                || path.startsWith("/product/") || path.startsWith("/vendor/")
                || path.startsWith("/odm/") || path.startsWith("/apex/")
                || path.startsWith("/prism/") || path.startsWith("/opt/");
    }

    private boolean isSystemLike(String pkg) {
        return pkg.startsWith("com.android.") || pkg.startsWith("com.google.")
                || pkg.startsWith("com.samsung.") || pkg.startsWith("com.sec.");
    }

    /** 只扫描用户应用（系统应用无法睡眠），分批一次 su 完成。 */
    private void queryStatesOf(Set<String> pkgs) {
        enabledState.clear();
        if (pkgs.isEmpty()) {
            return;
        }
        List<String> list = new ArrayList<String>(pkgs);
        StringBuilder cmd = new StringBuilder();
        for (int i = 0; i < list.size(); i += 40) {
            if (cmd.length() > 0) {
                cmd.append("; ");
            }
            cmd.append("for p in ");
            for (int j = i; j < Math.min(i + 40, list.size()); j++) {
                cmd.append(list.get(j)).append(' ');
            }
            cmd.append("; do printf '%s ' $p; ")
                    .append("dumpsys package $p | sed -n '/User 0:/,/User [1-9]/p' ")
                    .append("| sed -n 's/.*enabled=\\([0-9]\\).*/\\1/p' | head -n1; done");
        }
        Shell.Result r = Shell.su(cmd.toString());
        String cur = null;
        for (String line : r.out.split("\n")) {
            String t = line.trim();
            if (t.isEmpty()) {
                continue;
            }
            int sp = t.indexOf(' ');
            if (sp > 0) {
                cur = t.substring(0, sp);
                enabledState.put(cur, toState(t.substring(sp + 1).trim()));
            } else if (cur != null) {
                enabledState.put(cur, toState(t));
                cur = null;
            }
        }
    }

    private int toState(String v) {
        if ("3".equals(v)) {
            return ST_FROZEN;
        }
        if ("4".equals(v)) {
            return ST_ASLEEP;
        }
        return ST_NORMAL;
    }

    private int stateOf(String pkg) {
        Integer v = enabledState.get(pkg);
        return v == null ? ST_NORMAL : v;
    }

    private Set<String> queryPackages(String cmd) {
        Set<String> result = new HashSet<String>();
        Shell.Result r = Shell.su(cmd);
        Matcher m = P_PKG.matcher(r.out);
        while (m.find()) {
            result.add(m.group(1));
        }
        return result;
    }

    private boolean inScope(String pkg) {
        boolean sys = Boolean.TRUE.equals(systemFlags.get(pkg));
        if (!freezeMode) {
            return sys;
        }
        if (scope == S_ALL) {
            return true;
        }
        return scope == S_SYSTEM ? sys : !sys;
    }

    private boolean matches(String pkg) {
        if (query.isEmpty()) {
            return true;
        }
        String label = labels.containsKey(pkg) ? labels.get(pkg) : pkg;
        return label.toLowerCase(Locale.US).contains(query)
                || pkg.toLowerCase(Locale.US).contains(query);
    }

    private List<String> visiblePackages() {
        List<String> shown = new ArrayList<String>();
        if (freezeMode) {
            Set<String> pool = new HashSet<String>(installed);
            pool.addAll(uninstalled);
            for (String p : pool) {
                boolean frozen = frozenPkgs.contains(p);
                boolean removed = uninstalled.contains(p);
                if (!inScope(p) || !matches(p)) {
                    continue;
                }
                int st = stateOf(p);
                boolean sys = Boolean.TRUE.equals(systemFlags.get(p));
                if (filter == F_YES && (!frozen || removed || st == ST_ASLEEP)) {
                    continue;
                }
                if (filter == F_NO && (frozen || removed || (!sys && st != ST_NORMAL))) {
                    continue;
                }
                if (filter == F_ASLEEP && st != ST_ASLEEP) {
                    continue;
                }
                shown.add(p);
            }
        } else {
            if (filter == F_YES || filter == F_DEFAULT) {
                for (String p : uninstalled) {
                    if (matches(p)) {
                        shown.add(p);
                    }
                }
            }
            if (filter == F_NO || filter == F_DEFAULT) {
                for (String p : installed) {
                    if (Boolean.TRUE.equals(systemFlags.get(p)) && matches(p)) {
                        shown.add(p);
                    }
                }
            }
        }
        Collections.sort(shown, new Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                String la = labels.containsKey(a) ? labels.get(a) : a;
                String lb = labels.containsKey(b) ? labels.get(b) : b;
                return la.compareToIgnoreCase(lb);
            }
        });
        return shown;
    }

    private void render() {
        list.removeAllViews();
        List<String> shown = visiblePackages();
        summary.setText("共 " + shown.size() + " 项 · 已选 " + checked.size());
        if (shown.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("没有匹配的应用");
            empty.setTextColor(getResources().getColor(R.color.text_secondary));
            empty.setPadding(0, dp(20), 0, 0);
            list.addView(empty);
            return;
        }
        int count = 0;
        for (String pkg : shown) {
            if (count++ >= 200) {
                break;
            }
            addRow(pkg);
        }
    }

    private void addRow(final String pkg) {
        boolean frozen = frozenPkgs.contains(pkg);
        boolean removed = uninstalled.contains(pkg);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.card_bg);
        int p = dp(10);
        row.setPadding(p, p, p, p);

        CheckBox box = new CheckBox(this);
        box.setChecked(checked.contains(pkg));
        box.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (((CheckBox) v).isChecked()) {
                    checked.add(pkg);
                } else {
                    checked.remove(pkg);
                }
                summary.setText("共 " + visiblePackages().size() + " 项 · 已选 " + checked.size());
            }
        });
        row.addView(box);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);

        TextView name = new TextView(this);
        String label = labels.containsKey(pkg) ? labels.get(pkg) : pkg;
        name.setText(label);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        name.setTextColor(getResources().getColor(R.color.text_primary));
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setMaxLines(1);
        texts.addView(name);

        TextView sub = new TextView(this);
        String state;
        int color;
        if (freezeMode) {
            int st = stateOf(pkg);
            if (removed || st == ST_FROZEN) {
                state = "已冰冻";
                color = 0xFFEF4444;
            } else if (st == ST_ASLEEP) {
                state = "已睡眠";
                color = 0xFF3B82F6;
            } else {
                state = "未管理";
                color = 0xFF22C55E;
            }
        } else {
            state = removed ? "已卸载" : "已安装";
            color = removed ? 0xFFEF4444 : 0xFF22C55E;
        }
        sub.setText(pkg + " · " + state);
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        sub.setTextColor(color);
        sub.setMaxLines(1);
        texts.addView(sub);

        row.addView(texts, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = dp(8);
        list.addView(row, rowLp);
        row.setAlpha(0f);
        row.setTranslationY(dp(10));
        row.animate().alpha(1f).translationY(0f)
                .setStartDelay(Math.min(list.getChildCount(), 12) * 25L)
                .setDuration(200).start();
    }

    private void selectAll() {
        List<String> shown = visiblePackages();
        if (checked.size() >= shown.size()) {
            checked.clear();
        } else {
            checked.clear();
            checked.addAll(shown);
        }
        render();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
