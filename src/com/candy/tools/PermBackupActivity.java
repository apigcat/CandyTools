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
import java.util.List;
import java.util.Locale;

/** 权限备份：导出剪贴板 / 唤醒锁的禁用列表。 */
public class PermBackupActivity extends Activity {

    private static final int REQ_SAVE = 4001;

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
                final List<String> clip = queryDenied("READ_CLIPBOARD");
                final List<String> wake = queryDenied("WAKE_LOCK");
                final List<String> net = queryDenied("RUN_ANY_IN_BACKGROUND");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        counts.setText("剪贴板 " + clip.size() + " · 唤醒锁 " + wake.size()
                                + " · 网络唤醒 " + net.size());
                        pendingXml = buildXml(clip, wake, net);
                    }
                });
            }
        }, "perm-load").start();
    }

    private List<String> queryDenied(String op) {
        List<String> list = new ArrayList<String>();
        Shell.Result r = Shell.su("appops query-op " + op + " ignore");
        for (String line : r.out.split("\n")) {
            String s = line.trim();
            if (s.contains(".") && !s.contains(" ")) {
                list.add(s);
            }
        }
        return list;
    }

    private String buildXml(List<String> clip, List<String> wake, List<String> net) {
        StringBuilder sb = new StringBuilder();
        String date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        sb.append("<CandyToolsPermissionBackup date=\"").append(date).append("\">\n");
        sb.append("  <clipboard>\n");
        for (String p : clip) {
            sb.append("    <pkg>").append(p).append("</pkg>\n");
        }
        sb.append("  </clipboard>\n");
        sb.append("  <wakelock>\n");
        for (String p : wake) {
            sb.append("    <pkg>").append(p).append("</pkg>\n");
        }
        sb.append("  </wakelock>\n");
        sb.append("  <networkwake>\n");
        for (String p : net) {
            sb.append("    <pkg>").append(p).append("</pkg>\n");
        }
        sb.append("  </networkwake>\n");
        sb.append("</CandyToolsPermissionBackup>\n");
        return sb.toString();
    }

    private void export() {
        if (pendingXml.isEmpty()) {
            Toast.makeText(this, "还没有读取完成", Toast.LENGTH_SHORT).show();
            return;
        }
        String name = "CandyTools_perm_"
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
