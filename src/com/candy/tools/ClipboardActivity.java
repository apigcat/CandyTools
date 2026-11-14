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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 通用 appops 权限页：剪贴板 / 唤醒锁。 */
public class ClipboardActivity extends Activity {

    private static final int F_DEFAULT = 0;
    private static final int F_DENIED = 1;
    private static final int F_ALLOWED = 2;

    private final List<AppEntry> userApps = new ArrayList<AppEntry>();
    private final List<AppEntry> sysApps = new ArrayList<AppEntry>();
    private final Set<String> allowSet = new HashSet<String>();
    private final Set<String> ignoreSet = new HashSet<String>();
    private final Set<String> checked = new HashSet<String>();

    private LinearLayout list;
    private TextView tabUser;
    private TextView tabSys;
    private TextView filterChip;
    private TextView actionChip;
    private TextView summary;
    private boolean showingUser = true;
    private String query = "";
    private int filter = F_DEFAULT;

    private static final class AppEntry {
        String label;
        String pkg;
    }

    protected String[] ops() {
        return new String[]{"READ_CLIPBOARD"};
    }


    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(ThemeHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getResources().getColor(R.color.bg));
        int pad = dp(16);
        root.setPadding(pad, pad, pad, 0);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabUser = makeTab("用户应用", true);
        tabSys = makeTab("系统应用", false);
        tabs.addView(tabUser, weightLp(0));
        tabs.addView(tabSys, weightLp(8));
        root.addView(tabs);

        EditText search = new EditText(this);
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
                renderList();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        LinearLayout.LayoutParams qLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        qLp.topMargin = dp(8);
        root.addView(search, qLp);

        LinearLayout bulkRow = new LinearLayout(this);
        bulkRow.setOrientation(LinearLayout.HORIZONTAL);
        bulkRow.addView(bulkChip("全选", "#6750A4", new Runnable() {
            @Override
            public void run() {
                selectAll();
            }
        }));

        filterChip = bulkChip("默认 ▾", "#7C3AED", new Runnable() {
            @Override
            public void run() {
                showFilterMenu();
            }
        });
        bulkRow.addView(filterChip);

        actionChip = bulkChip("默认", "#7C3AED", new Runnable() {
            @Override
            public void run() {
                onAction();
            }
        });
        bulkRow.addView(actionChip);

        HorizontalScrollView bulkScroll = new HorizontalScrollView(this);
        bulkScroll.setHorizontalScrollBarEnabled(false);
        bulkScroll.addView(bulkRow);
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bLp.topMargin = dp(8);
        root.addView(bulkScroll, bLp);

        summary = new TextView(this);
        summary.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        summary.setTextColor(getResources().getColor(R.color.text_tertiary));
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sLp.topMargin = dp(6);
        root.addView(summary, sLp);

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(list);
        LinearLayout.LayoutParams lLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        lLp.topMargin = dp(6);
        root.addView(scroll, lLp);

        tabUser.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectTab(true);
            }
        });
        tabSys.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectTab(false);
            }
        });

        setContentView(root);
        updateActionChip();
        loadApps();
    }

    private TextView makeTab(String text, boolean active) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setGravity(Gravity.CENTER);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(10), 0, dp(10));
        t.setTextColor(getResources().getColor(active ? R.color.neon : R.color.text_secondary));
        t.setBackgroundResource(active ? R.drawable.chip_bg : R.drawable.card_bg);
        return t;
    }

    private LinearLayout.LayoutParams weightLp(int leftMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = dp(leftMargin);
        return lp;
    }

    private TextView bulkChip(String text, String color, final Runnable action) {
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
        String[] names = {"默认", "禁用", "允许"};
        new AlertDialog.Builder(this)
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        filter = which;
                        filterChip.setText(names[which] + " ▾");
                        checked.clear();
                        updateActionChip();
                        renderList();
                    }
                })
                .show();
    }

    private void updateActionChip() {
        if (filter == F_DENIED) {
            actionChip.setText("执行允许");
            actionChip.setTextColor(android.graphics.Color.parseColor("#22C55E"));
        } else if (filter == F_ALLOWED) {
            actionChip.setText("执行禁用");
            actionChip.setTextColor(android.graphics.Color.parseColor("#EF4444"));
        } else {
            actionChip.setText("默认");
            actionChip.setTextColor(android.graphics.Color.parseColor("#7C3AED"));
        }
    }

    private void onAction() {
        final List<String> targets = new ArrayList<String>(checked);
        if (targets.isEmpty()) {
            Toast.makeText(this, "请先勾选应用（可点全选）", Toast.LENGTH_SHORT).show();
            return;
        }
        if (filter == F_DEFAULT) {
            new AlertDialog.Builder(this)
                    .setItems(new String[]{"执行禁用", "执行允许"},
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    confirm(which == 0 ? "ignore" : "allow", targets);
                                }
                            })
                    .show();
            return;
        }
        confirm(filter == F_DENIED ? "allow" : "ignore", targets);
    }

    private void confirm(final String mode, final List<String> targets) {
        int same = 0;
        for (String pkg : targets) {
            boolean denied = ignoreSet.contains(pkg);
            if ("ignore".equals(mode) ? denied : !denied) {
                same++;
            }
        }
        int added = targets.size() - same;
        String action = "ignore".equals(mode) ? "禁用" : "允许";
        String msg = added == 0
                ? "选中 " + targets.size() + " 项，均已" + action + "，未发生变化"
                : "选中 " + targets.size() + " 项，已是" + action + " " + same
                + " 项，新增 " + added + " 项";
        new AlertDialog.Builder(this)
                .setMessage(msg)
                .setNegativeButton("取消", null)
                .setPositiveButton("确认", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        runBatch(mode, targets);
                    }
                })
                .show();
    }

    private void runBatch(final String mode, final List<String> targets) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                StringBuilder cmd = new StringBuilder();
                for (String pkg : targets) {
                    for (String o : ops()) {
                        cmd.append("cmd appops set ").append(pkg).append(' ')
                                .append(o).append(' ').append(mode).append("; ");
                    }
                }
                final Shell.Result r = Shell.su(cmd.toString());
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        checked.clear();
                        Toast.makeText(ClipboardActivity.this,
                                r.code == 0 ? "已更新 " + targets.size() + " 个应用"
                                        : "失败：" + r.out,
                                Toast.LENGTH_SHORT).show();
                        loadApps();
                    }
                });
            }
        }, "ops-batch").start();
    }

    private void selectTab(boolean user) {
        showingUser = user;
        tabUser.setTextColor(getResources().getColor(user ? R.color.neon : R.color.text_secondary));
        tabSys.setTextColor(getResources().getColor(user ? R.color.text_secondary : R.color.neon));
        tabUser.setBackgroundResource(user ? R.drawable.chip_bg : R.drawable.card_bg);
        tabSys.setBackgroundResource(user ? R.drawable.card_bg : R.drawable.chip_bg);
        checked.clear();
        renderList();
    }

    private void loadApps() {
        final PackageManager pm = getPackageManager();
        new Thread(new Runnable() {
            @Override
            public void run() {
                userApps.clear();
                sysApps.clear();
                allowSet.clear();
                ignoreSet.clear();

                List<ApplicationInfo> infos = pm.getInstalledApplications(0);
                for (ApplicationInfo info : infos) {
                    AppEntry e = new AppEntry();
                    e.pkg = info.packageName;
                    e.label = String.valueOf(info.loadLabel(pm));
                    boolean system = (info.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                    if (system) {
                        sysApps.add(e);
                    } else {
                        userApps.add(e);
                    }
                }
                Comparator<AppEntry> byLabel = new Comparator<AppEntry>() {
                    @Override
                    public int compare(AppEntry a, AppEntry b) {
                        return a.label.compareToIgnoreCase(b.label);
                    }
                };
                Collections.sort(userApps, byLabel);
                Collections.sort(sysApps, byLabel);

                for (String o : ops()) {
                    allowSet.addAll(queryOp(o, "allow"));
                    ignoreSet.addAll(queryOp(o, "ignore"));
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        renderList();
                    }
                });
            }
        }, "ops-load").start();
    }

    private List<String> queryOp(String op, String mode) {
        List<String> result = new ArrayList<String>();
        Shell.Result r = Shell.su("appops query-op " + op + " " + mode);
        for (String line : r.out.split("\n")) {
            String s = line.trim();
            if (s.contains(".") && !s.contains(" ")) {
                result.add(s);
            }
        }
        return result;
    }

    private void selectAll() {
        List<AppEntry> src = visibleEntries();
        if (checked.size() >= src.size()) {
            checked.clear();
        } else {
            checked.clear();
            for (AppEntry e : src) {
                checked.add(e.pkg);
            }
        }
        renderList();
    }

    private List<AppEntry> visibleEntries() {
        List<AppEntry> src = showingUser ? userApps : sysApps;
        List<AppEntry> out = new ArrayList<AppEntry>();
        for (AppEntry e : src) {
            boolean allow = allowSet.contains(e.pkg);
            boolean ignore = ignoreSet.contains(e.pkg);
            if (!showingUser && !allow && !ignore) {
                continue;
            }
            if (!query.isEmpty()) {
                String label = e.label == null ? "" : e.label.toLowerCase(Locale.US);
                if (!label.contains(query) && !e.pkg.toLowerCase(Locale.US).contains(query)) {
                    continue;
                }
            }
            if (filter == F_DENIED && !ignore) {
                continue;
            }
            if (filter == F_ALLOWED && !allow) {
                continue;
            }
            out.add(e);
        }
        return out;
    }

    private void renderList() {
        list.removeAllViews();
        List<AppEntry> shown = visibleEntries();
        summary.setText("共 " + shown.size() + " 项 · 已选 " + checked.size());
        if (shown.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(showingUser ? "没有可显示的用户应用" : "系统应用没有显式设置记录");
            empty.setTextColor(getResources().getColor(R.color.text_secondary));
            empty.setPadding(0, dp(20), 0, 0);
            list.addView(empty);
            return;
        }
        for (AppEntry e : shown) {
            addRow(e, allowSet.contains(e.pkg), ignoreSet.contains(e.pkg));
        }
    }

    private void addRow(final AppEntry entry, boolean allow, boolean ignore) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.card_bg);
        int p = dp(10);
        row.setPadding(p, p, p, p);

        CheckBox box = new CheckBox(this);
        box.setChecked(checked.contains(entry.pkg));
        box.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (((CheckBox) v).isChecked()) {
                    checked.add(entry.pkg);
                } else {
                    checked.remove(entry.pkg);
                }
                summary.setText("共 " + visibleEntries().size() + " 项 · 已选 " + checked.size());
            }
        });
        row.addView(box);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);

        TextView name = new TextView(this);
        name.setText(entry.label);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        name.setTextColor(getResources().getColor(R.color.text_primary));
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setMaxLines(1);
        texts.addView(name);

        String state = ignore ? "已禁用" : (allow ? "已允许" : "默认");
        TextView pkg = new TextView(this);
        pkg.setText(entry.pkg + " · " + state);
        pkg.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        pkg.setTextColor(android.graphics.Color.parseColor(
                ignore ? "#EF4444" : (allow ? "#22C55E" : "#6B7280")));
        texts.addView(pkg);

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

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
