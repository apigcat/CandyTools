package com.candy.tools;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 备份：导出全部冰冻与卸载记录。 */
public class BackupActivity extends Activity {

    private static final int REQ_SAVE = 2001;
    private static final Pattern P_PKG = Pattern.compile("package:(\\S+)");

    private final Set<String> installed = new HashSet<String>();
    private final Set<String> frozen = new HashSet<String>();
    private final Set<String> asleep = new HashSet<String>();
    private final Set<String> uninstalled = new HashSet<String>();
    private final java.util.Map<String, String> paths = new java.util.HashMap<String, String>();
    private String pendingXml = "";
    private TextView counts;

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
        title.setText("备份");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(getResources().getColor(R.color.text_primary));
        root.addView(title);

        counts = new TextView(this);
        counts.setText("读取中…");
        counts.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        counts.setTextColor(getResources().getColor(R.color.text_secondary));
        LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cLp.topMargin = dp(10);
        root.addView(counts, cLp);

        TextView btn = new TextView(this);
        btn.setText("备份为 XML");
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
                export();
            }
        });

        setContentView(root);
        load();
    }

    private void load() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                installed.clear();
                frozen.clear();
                asleep.clear();
                uninstalled.clear();
                paths.clear();
                paths.putAll(queryPaths());
                Set<String> all = query("pm list packages -u --user 0");
                installed.addAll(query("pm list packages --user 0"));
                frozen.addAll(query("pm list packages -d --user 0"));
                for (String p : all) {
                    if (!installed.contains(p)) {
                        uninstalled.add(p);
                    }
                }
                asleep.addAll(queryStates("4"));
                frozen.removeAll(asleep);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        counts.setText("冰冻 " + frozen.size() + " 个 · 睡眠 "
                                + asleep.size() + " 个 · 卸载 "
                                + uninstalled.size() + " 个");
                    }
                });
            }
        }, "backup-load").start();
    }

    private java.util.Map<String, String> queryPaths() {
        java.util.Map<String, String> result = new java.util.HashMap<String, String>();
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
        return path.startsWith("/system/") || path.startsWith("/system_ext/")
                || path.startsWith("/product/") || path.startsWith("/vendor/")
                || path.startsWith("/odm/") || path.startsWith("/apex/")
                || path.startsWith("/prism/") || path.startsWith("/opt/");
    }

    private Set<String> query(String cmd) {
        Set<String> result = new HashSet<String>();
        Shell.Result r = Shell.su(cmd);
        Matcher m = P_PKG.matcher(r.out);
        while (m.find()) {
            result.add(m.group(1));
        }
        return result;
    }

    /** 扫描全部用户安装包，挑出 enabled 等于指定值的包（3 冰冻 / 4 睡眠）。 */
    private Set<String> queryStates(String wanted) {
        Set<String> result = new HashSet<String>();
        List<String> list = new ArrayList<String>(installed);
        StringBuilder cmd = new StringBuilder();
        for (int i = 0; i < list.size(); i += 20) {
            if (cmd.length() > 0) {
                cmd.append("; ");
            }
            cmd.append("for p in ");
            for (int j = i; j < Math.min(i + 20, list.size()); j++) {
                cmd.append(list.get(j)).append(' ');
            }
            cmd.append("; do printf '%s ' $p; ")
                    .append("dumpsys package $p | sed -n '/User 0:/,/User [1-9]/p' ")
                    .append("| sed -n 's/.*enabled=\\([0-9]\\).*/\\1/p' | head -n1; done");
        }
        if (cmd.length() == 0) {
            return result;
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
                if (wanted.equals(t.substring(sp + 1).trim())) {
                    result.add(cur);
                }
            }
        }
        return result;
    }

    private void export() {
        StringBuilder sb = new StringBuilder();
        String date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                .format(new Date());
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        sb.append("<CandyToolsBackup date=\"").append(date).append("\">\n");
        sb.append("  <frozen>\n");
        for (String p : frozen) {
            sb.append("    <pkg>").append(p).append("</pkg>\n");
        }
        sb.append("  </frozen>\n");
        sb.append("  <asleep>\n");
        for (String p : asleep) {
            sb.append("    <pkg>").append(p).append("</pkg>\n");
        }
        sb.append("  </asleep>\n");
        sb.append("  <uninstalled>\n");
        for (String p : uninstalled) {
            sb.append("    <pkg>").append(p).append("</pkg>\n");
        }
        sb.append("  </uninstalled>\n");
        sb.append("</CandyToolsBackup>\n");
        pendingXml = sb.toString();

        String name = "CandyTools_backup_"
                + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date())
                + ".xml";
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/xml");
        intent.putExtra(Intent.EXTRA_TITLE, name);
        try {
            startActivityForResult(intent, REQ_SAVE);
        } catch (Throwable t) {
            Toast.makeText(this, "无法打开保存对话框", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQ_SAVE || resultCode != RESULT_OK || data == null) {
            super.onActivityResult(requestCode, resultCode, data);
            return;
        }
        Uri uri = data.getData();
        if (uri == null) {
            return;
        }
        try {
            java.io.OutputStream os = getContentResolver().openOutputStream(uri);
            os.write(pendingXml.getBytes("UTF-8"));
            os.flush();
            os.close();
            Toast.makeText(this, "备份已保存", Toast.LENGTH_SHORT).show();
        } catch (Throwable t) {
            Toast.makeText(this, "保存失败：" + t.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
