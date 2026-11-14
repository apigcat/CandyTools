package com.candy.tools;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Hosts 屏蔽：编辑 hosts 并用 bind 挂载，支持备份与恢复。 */
public class HostsActivity extends Activity {

    private static final int REQ_BACKUP = 7001;
    private static final int REQ_RESTORE = 7002;

    private static final String TARGET = "/system/etc/hosts";

    private EditText editor;
    private TextView status;

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

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("Hosts屏蔽");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(getResources().getColor(R.color.text_primary));
        top.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        top.addView(button("备份", "#3B82F6", new Runnable() {
            @Override
            public void run() {
                backup();
            }
        }));
        top.addView(button("恢复", "#22C55E", new Runnable() {
            @Override
            public void run() {
                restore();
            }
        }));
        top.addView(button("写入模块", "#7C3AED", new Runnable() {
            @Override
            public void run() {
                installModule();
            }
        }));
        top.addView(button("移除模块", "#EF4444", new Runnable() {
            @Override
            public void run() {
                removeModule();
            }
        }));
        root.addView(top);

        status = new TextView(this);
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        status.setTextColor(getResources().getColor(R.color.text_tertiary));
        LinearLayout.LayoutParams stLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        stLp.topMargin = dp(6);
        root.addView(status, stLp);

        editor = new EditText(this);
        editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        editor.setTextColor(getResources().getColor(R.color.text_primary));
        editor.setBackgroundResource(R.drawable.card_bg);
        editor.setPadding(dp(12), dp(12), dp(12), dp(12));
        editor.setMinLines(12);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(editor);
        LinearLayout.LayoutParams eLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        eLp.topMargin = dp(10);
        root.addView(scroll, eLp);

        setContentView(root);
        loadHosts();
    }

    private TextView button(String text, String color, final Runnable action) {
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

    private void loadHosts() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Shell.Result r = Shell.su("cat " + TARGET + " 2>/dev/null");
                final String content = r.out;
                Shell.Result m = Shell.su("grep " + TARGET + " /proc/mounts 2>/dev/null");
                final boolean mounted = !m.out.trim().isEmpty();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        editor.setText(content);
                        status.setText(mounted
                                ? "当前状态：已挂载自定义 hosts"
                                : "当前状态：使用系统原版 hosts");
                        status.setTextColor(android.graphics.Color.parseColor(
                                mounted ? "#22C55E" : "#6B7280"));
                    }
                });
            }
        }, "hosts-load").start();
    }

    private void backup() {
        String name = "hosts_" + new java.text.SimpleDateFormat("yyyyMMdd_HHmmss",
                java.util.Locale.US).format(new java.util.Date()) + ".txt";
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, name);
        pending = editor.getText().toString();
        try {
            startActivityForResult(intent, REQ_BACKUP);
        } catch (Throwable t) {
            Toast.makeText(this, "无法打开保存对话框", Toast.LENGTH_SHORT).show();
        }
    }

    private void restore() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        try {
            startActivityForResult(intent, REQ_RESTORE);
        } catch (Throwable t) {
            Toast.makeText(this, "无法打开文件选择器", Toast.LENGTH_SHORT).show();
        }
    }

    private String pending = "";

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != RESULT_OK || data == null) {
            super.onActivityResult(requestCode, resultCode, data);
            return;
        }
        android.net.Uri uri = data.getData();
        if (uri == null) {
            return;
        }
        if (requestCode == REQ_BACKUP) {
            try {
                java.io.OutputStream os = getContentResolver().openOutputStream(uri);
                os.write(pending.getBytes("UTF-8"));
                os.flush();
                os.close();
                Toast.makeText(this, "备份已保存", Toast.LENGTH_SHORT).show();
            } catch (Throwable t) {
                Toast.makeText(this, "保存失败：" + t.getMessage(), Toast.LENGTH_LONG).show();
            }
            return;
        }
        if (requestCode == REQ_RESTORE) {
            try {
                java.io.InputStream is = getContentResolver().openInputStream(uri);
                java.io.BufferedReader br = new java.io.BufferedReader(
                        new java.io.InputStreamReader(is, "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line).append('\n');
                }
                br.close();
                editor.setText(sb.toString());
                Toast.makeText(this, "已载入，点「应用」生效", Toast.LENGTH_SHORT).show();
            } catch (Throwable t) {
                Toast.makeText(this, "读取失败：" + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    private void installModule() {
        final String text = editor.getText().toString();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String local = getFilesDir().getAbsolutePath() + "/hosts";
                    java.io.FileOutputStream fos = new java.io.FileOutputStream(local);
                    fos.write(text.getBytes("UTF-8"));
                    fos.close();
                    String dir = "/data/adb/modules/candytools_hosts";
                    String prop = "id=candytools_hosts\n"
                            + "name=CandyTools Hosts\n"
                            + "version=1.0\n"
                            + "versionCode=1\n"
                            + "author=CandyTools\n"
                            + "description=Hosts block by CandyTools\n";
                    Shell.Result r = Shell.su(
                            "mkdir -p " + dir + "/system/etc && "
                                    + "cp " + local + " " + dir + "/system/etc/hosts && "
                                    + "chmod 644 " + dir + "/system/etc/hosts && "
                                    + "printf '" + prop.replace("'", "") + "' > "
                                    + dir + "/module.prop && echo OK");
                    final boolean ok = r.out.contains("OK");
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (ok) {
                                Toast.makeText(HostsActivity.this,
                                        "已写入模块，重启后生效", Toast.LENGTH_LONG).show();
                            } else {
                                Toast.makeText(HostsActivity.this,
                                        "写入模块失败，确认 KernelSU/Magisk 可用",
                                        Toast.LENGTH_LONG).show();
                            }
                        }
                    });
                } catch (Throwable t) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(HostsActivity.this,
                                    "失败：" + t.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        }, "hosts-module").start();
    }

    private void removeModule() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Shell.su("rm -rf /data/adb/modules/candytools_hosts");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(HostsActivity.this,
                                "已移除模块，重启后恢复原版 hosts", Toast.LENGTH_LONG).show();
                    }
                });
            }
        }, "hosts-module-rm").start();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
