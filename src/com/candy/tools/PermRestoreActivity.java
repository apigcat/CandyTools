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

/** 权限恢复：按备份把剪贴板 / 唤醒锁设为禁用。 */
public class PermRestoreActivity extends Activity {

    private static final int REQ_OPEN = 5001;

    private static final Pattern P_PKG = Pattern.compile("<pkg>(.*?)</pkg>");
    private static final Pattern P_CLIP = Pattern.compile(
            "<clipboard>(.*?)</clipboard>", Pattern.DOTALL);
    private static final Pattern P_WAKE = Pattern.compile(
            "<wakelock>(.*?)</wakelock>", Pattern.DOTALL);
    private static final Pattern P_NET = Pattern.compile(
            "<networkwake>(.*?)</networkwake>", Pattern.DOTALL);

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
        hint.setText("选择权限备份文件（.xml），把其中的应用恢复为禁用状态。");
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
        final List<String> clipList = parseBlock(P_CLIP, xml);
        final List<String> wakeList = parseBlock(P_WAKE, xml);
        final List<String> netList = parseBlock(P_NET, xml);
        if (clipList.isEmpty() && wakeList.isEmpty() && netList.isEmpty()) {
            Toast.makeText(this, "文件里没有权限记录", Toast.LENGTH_SHORT).show();
            return;
        }

        final Set<String> clipSel = new HashSet<String>(clipList);
        final Set<String> wakeSel = new HashSet<String>(wakeList);
        final Set<String> netSel = new HashSet<String>(netList);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(8), dp(16), dp(8));

        final java.util.Set<String> clipExisting = queryIgnore("READ_CLIPBOARD");
        final java.util.Set<String> wakeExisting = queryIgnore("WAKE_LOCK");
        final java.util.Set<String> netExisting = queryIgnore("RUN_ANY_IN_BACKGROUND");
        addSection(content, "剪贴板禁用列表", clipList, clipSel, clipExisting, "READ_CLIPBOARD");
        addSection(content, "唤醒锁禁用列表", wakeList, wakeSel, wakeExisting, "WAKE_LOCK");
        addSection(content, "网络唤醒禁用列表", netList, netSel, netExisting,
                "RUN_ANY_IN_BACKGROUND");

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

    private java.util.Set<String> queryIgnore(String op) {
        java.util.Set<String> set = new java.util.HashSet<String>();
        Shell.Result res = Shell.su("appops query-op " + op + " ignore");
        for (String line : res.out.split("\n")) {
            String s = line.trim();
            if (s.contains(".") && !s.contains(" ")) {
                set.add(s);
            }
        }
        return set;
    }

    private List<String> parseBlock(Pattern block, String xml) {
        List<String> list = new ArrayList<String>();
        Matcher b = block.matcher(xml);
        if (b.find()) {
            Matcher m = P_PKG.matcher(b.group(1));
            while (m.find()) {
                list.add(m.group(1).trim());
            }
        }
        return list;
    }

    private void addSection(LinearLayout parent, String title, List<String> pkgs,
                            final Set<String> selected, final Set<String> existing,
                            final String op) {
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

        header.addView(action("禁用", selected, existing, op));
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

            TextView tv = new TextView(this);
            tv.setText(pkg);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            tv.setTextColor(android.graphics.Color.parseColor(
                    existing.contains(pkg) ? "#EF4444" : "#22C55E"));
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

    private TextView action(String text, final Set<String> selected,
                            final Set<String> existing, final String op) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setTextColor(android.graphics.Color.parseColor("#EF4444"));
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
                    Toast.makeText(PermRestoreActivity.this, "请先勾选条目",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                int exist = 0;
                for (String p : targets) {
                    if (existing.contains(p)) {
                        exist++;
                    }
                }
                int added = targets.size() - exist;
                String msg;
                if (added == 0) {
                    msg = "选中 " + targets.size() + " 项，均已禁用，未发生变化";
                } else {
                    msg = "选中 " + targets.size() + " 项，其中已禁用 " + exist
                            + " 项，新增 " + added + " 项";
                }
                new AlertDialog.Builder(PermRestoreActivity.this)
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
                    if (Shell.su("cmd appops set " + pkg + " " + op + " ignore").code == 0) {
                        ok++;
                    }
                }
                final int success = ok;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(PermRestoreActivity.this,
                                "禁用完成：" + success + "/" + targets.size(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }, "perm-restore").start();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
