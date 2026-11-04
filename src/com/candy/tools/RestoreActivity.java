package com.candy.tools;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 恢复：选择备份文件，按列表执行冰冻/解冻/卸载/安装。 */
public class RestoreActivity extends Activity {

    private static final int REQ_OPEN = 3001;

    private static final Pattern P_XML_PKG = Pattern.compile("<pkg>(.*?)</pkg>");
    private static final Pattern P_BLOCK = Pattern.compile(
            "<(frozen|uninstalled)>(.*?)</\\1>", Pattern.DOTALL);

    private TextView hint;

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
        int pad = dp(18);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("恢复");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(getResources().getColor(R.color.text_primary));
        root.addView(title);

        hint = new TextView(this);
        hint.setText("选择一个备份文件（.xml），按其中的冰冻与卸载列表恢复。");
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        hint.setTextColor(getResources().getColor(R.color.text_secondary));
        LinearLayout.LayoutParams hLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hLp.topMargin = dp(10);
        root.addView(hint, hLp);

        TextView btn = new TextView(this);
        btn.setText("选择文件");
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setTextColor(getResources().getColor(R.color.neon));
        btn.setBackgroundResource(R.drawable.btn_ghost);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(dp(20), dp(12), dp(20), dp(12));
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bLp.topMargin = dp(18);
        root.addView(btn, bLp);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pick();
            }
        });

        setContentView(root);
    }

    private void pick() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        try {
            startActivityForResult(intent, REQ_OPEN);
        } catch (Throwable t) {
            Toast.makeText(this, "无法打开文件选择器", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQ_OPEN || resultCode != RESULT_OK || data == null) {
            super.onActivityResult(requestCode, resultCode, data);
            return;
        }
        Uri uri = data.getData();
        if (uri == null) {
            return;
        }
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
            br.close();
            show(sb.toString());
        } catch (Throwable t) {
            Toast.makeText(this, "读取失败：" + t.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void show(String xml) {
        final List<String> frozenList = new ArrayList<String>();
        final List<String> removedList = new ArrayList<String>();

        Matcher block = P_BLOCK.matcher(xml);
        while (block.find()) {
            boolean isFrozen = "frozen".equals(block.group(1));
            Matcher m = P_XML_PKG.matcher(block.group(2));
            while (m.find()) {
                String pkg = m.group(1).trim();
                if (isFrozen) {
                    frozenList.add(pkg);
                } else {
                    removedList.add(pkg);
                }
            }
        }
        if (frozenList.isEmpty() && removedList.isEmpty()) {
            Toast.makeText(this, "文件里没有条目", Toast.LENGTH_SHORT).show();
            return;
        }

        final Set<String> frozenSel = new HashSet<String>(frozenList);
        final Set<String> removedSel = new HashSet<String>(removedList);
        final Set<String> frozenNow = queryPackages("pm list packages -d --user 0");
        final Set<String> allNow = queryPackages("pm list packages -u --user 0");
        final Set<String> installedNow = queryPackages("pm list packages --user 0");

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(8), dp(16), dp(8));

        addSection(content, "冰冻列表", frozenList, frozenSel, frozenNow, installedNow, true);
        addSection(content, "卸载列表", removedList, removedSel, frozenNow, installedNow, false);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        int height = (int) (getResources().getDisplayMetrics().heightPixels * 0.65);
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, height));

        new AlertDialog.Builder(this)
                .setView(wrapper)
                .setNegativeButton("关闭", null)
                .show();
    }

    private Set<String> queryPackages(String cmd) {
        Set<String> set = new HashSet<String>();
        Shell.Result r = Shell.su(cmd);
        for (String line : r.out.split("\n")) {
            String t = line.trim();
            if (t.startsWith("package:")) {
                set.add(t.substring("package:".length()).trim());
            }
        }
        return set;
    }

    private void addSection(LinearLayout parent, String title, List<String> pkgs,
                            final Set<String> selected, final Set<String> frozenNow,
                            final Set<String> installedNow, final boolean isFrozen) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, dp(10), 0, dp(4));

        final List<CheckBox> boxes = new ArrayList<CheckBox>();
        final CheckBox selectAll = new CheckBox(this);
        selectAll.setChecked(true);
        header.addView(selectAll);

        TextView name = new TextView(this);
        name.setText(title + " (" + pkgs.size() + ")");
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setTextColor(getResources().getColor(R.color.neon));
        header.addView(name, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (isFrozen) {
            header.addView(act("解冻", "#22C55E", selected, frozenNow, "解冻", true));
            header.addView(act("冰冻", "#EF4444", selected, frozenNow, "冰冻", false));
        } else {
            header.addView(act("安装", "#22C55E", selected, installedNow, "安装", true));
            header.addView(act("卸载", "#EF4444", selected, installedNow, "卸载", false));
        }
        parent.addView(header);

        for (final String pkg : pkgs) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);

            final CheckBox box = new CheckBox(this);
            box.setChecked(true);
            box.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (((CheckBox) v).isChecked()) {
                        selected.add(pkg);
                    } else {
                        selected.remove(pkg);
                    }
                }
            });
            row.addView(box);
            boxes.add(box);

            boolean already = isFrozen ? frozenNow.contains(pkg) : !installedNow.contains(pkg);
            TextView tv = new TextView(this);
            tv.setText(pkg);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            tv.setTextColor(android.graphics.Color.parseColor(
                    already ? "#EF4444" : "#22C55E"));
            tv.setPadding(0, dp(6), 0, dp(6));
            row.addView(tv, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            parent.addView(row);
        }

        selectAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean on = ((CheckBox) v).isChecked();
                for (int i = 0; i < boxes.size(); i++) {
                    boxes.get(i).setChecked(on);
                    if (on) {
                        selected.add(pkgs.get(i));
                    } else {
                        selected.remove(pkgs.get(i));
                    }
                }
            }
        });
    }

    private TextView act(String text, String color, final Set<String> selected,
                         final Set<String> currentSet, final String op,
                         final boolean invert) {
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
                final List<String> targets = new ArrayList<String>(selected);
                if (targets.isEmpty()) {
                    Toast.makeText(RestoreActivity.this, "请先勾选条目",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                int same = 0;
                for (String pkg : targets) {
                    boolean inSet = currentSet.contains(pkg);
                    if (invert ? !inSet : inSet) {
                        same++;
                    }
                }
                int added = targets.size() - same;
                String msg;
                if (added == 0) {
                    msg = "选中 " + targets.size() + " 项，" + op + "后状态未发生变化";
                } else {
                    msg = "选中 " + targets.size() + " 项，已处于目标状态 " + same
                            + " 项，新增 " + added + " 项";
                }
                new AlertDialog.Builder(RestoreActivity.this)
                        .setMessage(msg)
                        .setNegativeButton("取消", null)
                        .setPositiveButton("确认", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                run(op, targets);
                            }
                        })
                        .show();
            }
        });
        return v;
    }

    private void run(final String op, final List<String> targets) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                int ok = 0;
                for (String pkg : targets) {
                    String cmd;
                    if ("解冻".equals(op)) {
                        cmd = "pm enable " + pkg;
                    } else if ("冰冻".equals(op)) {
                        cmd = "pm disable-user --user 0 " + pkg;
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
                        Toast.makeText(RestoreActivity.this,
                                op + "完成：" + success + "/" + targets.size(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }, "restore-run").start();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
