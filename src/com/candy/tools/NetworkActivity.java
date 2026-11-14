package com.candy.tools;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 网络连接：IPv4 / IPv6 切换，手动刷新，只显示已建立连接。 */
public class NetworkActivity extends Activity {

    private static final Pattern P_USER = Pattern.compile("users:\\(\\(\"([^\"]+)\",pid=(\\d+)");
    private static final Pattern P_LASTSND = Pattern.compile("lastsnd:(\\d+)");
    private static final Pattern P_TIMER = Pattern.compile("timer:\\(keepalive,(\\d+)(sec|min|hour)");

    private LinearLayout list;
    private TextView tabV4;
    private TextView tabV6;
    private boolean ipv6 = false;

    private static final class Conn {
        String program;
        int pid;
        String local;
        String peer;
        String proto;
        String age = "";
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

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        tabV4 = makeTab("IPv4", true);
        tabV6 = makeTab("IPv6", false);
        header.addView(tabV4, weightLp(0));
        header.addView(tabV6, weightLp(8));

        TextView refresh = new TextView(this);
        refresh.setText("刷新");
        refresh.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        refresh.setTypeface(Typeface.DEFAULT_BOLD);
        refresh.setTextColor(getResources().getColor(R.color.neon));
        refresh.setBackgroundResource(R.drawable.btn_ghost);
        refresh.setPadding(dp(14), dp(8), dp(14), dp(8));
        LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rLp.leftMargin = dp(10);
        header.addView(refresh, rLp);

        root.addView(header);

        tabV4.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ipv6 = false;
                styleTabs();
                load();
            }
        });
        tabV6.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ipv6 = true;
                styleTabs();
                load();
            }
        });
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                load();
            }
        });

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(list);
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        sLp.topMargin = dp(10);
        root.addView(scroll, sLp);

        setContentView(root);
        load();
    }

    private TextView makeTab(String text, boolean active) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setGravity(Gravity.CENTER);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(8), 0, dp(8));
        t.setTextColor(getResources().getColor(active ? R.color.neon : R.color.text_secondary));
        t.setBackgroundResource(active ? R.drawable.chip_bg : R.drawable.card_bg);
        return t;
    }

    private void styleTabs() {
        tabV4.setTextColor(getResources().getColor(ipv6 ? R.color.text_secondary : R.color.neon));
        tabV6.setTextColor(getResources().getColor(ipv6 ? R.color.neon : R.color.text_secondary));
        tabV4.setBackgroundResource(ipv6 ? R.drawable.card_bg : R.drawable.chip_bg);
        tabV6.setBackgroundResource(ipv6 ? R.drawable.chip_bg : R.drawable.card_bg);
    }

    private LinearLayout.LayoutParams weightLp(int leftMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = dp(leftMargin);
        return lp;
    }

    private void load() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                List<Conn> conns = query("ss -tnpoi");
                if (conns.isEmpty()) {
                    conns = query("netstat -tnp");
                }
                final List<Conn> filtered = new ArrayList<Conn>();
                for (Conn c : conns) {
                    boolean v6 = isIpv6(c.local) || isIpv6(c.peer);
                    if (v6 == ipv6) {
                        filtered.add(c);
                    }
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        render(filtered);
                    }
                });
            }
        }, "net-load").start();
    }

    private boolean isIpv6(String addr) {
        if (addr == null) {
            return false;
        }
        return addr.indexOf(':') != addr.lastIndexOf(':');
    }

    private List<Conn> query(String cmd) {
        Shell.Result r = Shell.su(cmd);
        return parse(r.out, cmd.startsWith("ss"));
    }

    private List<Conn> parse(String out, boolean ssFormat) {
        List<Conn> list = new ArrayList<Conn>();
        for (String line : out.split("\n")) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("Netid") || t.startsWith("Proto")
                    || t.startsWith("Active") || t.startsWith("State")) {
                continue;
            }
            String[] parts = t.split("\\s+");
            Conn c = new Conn();
            String state;
            if (ssFormat) {
                if (parts.length < 6) {
                    continue;
                }
                c.proto = parts[0];
                state = parts[1];
                c.local = parts[4];
                c.peer = parts[5];
            } else {
                if (parts.length < 7) {
                    continue;
                }
                c.proto = parts[0];
                c.local = parts[3];
                c.peer = parts[4];
                state = parts[5];
                String pidProg = parts[6];
                int slash = pidProg.indexOf('/');
                if (slash > 0) {
                    c.pid = parseInt(pidProg.substring(0, slash));
                    c.program = pidProg.substring(slash + 1);
                }
            }
            if (!"ESTABLISHED".equalsIgnoreCase(state) && !"ESTAB".equalsIgnoreCase(state)) {
                continue;
            }
            Matcher m = P_USER.matcher(t);
            if (m.find()) {
                c.program = m.group(1);
                c.pid = parseInt(m.group(2));
            }
            if (c.program == null) {
                c.program = "(未知)";
            }
            Matcher ls = P_LASTSND.matcher(t);
            Matcher tm = P_TIMER.matcher(t);
            if (ls.find()) {
                c.age = "空闲 " + formatDuration(parseLong(ls.group(1)));
            } else if (tm.find()) {
                c.age = "保活 " + tm.group(1) + tm.group(2);
            }
            list.add(c);
            if (list.size() >= 150) {
                break;
            }
        }
        return list;
    }

    private void render(List<Conn> conns) {
        list.removeAllViews();
        if (conns.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(ipv6 ? "没有已建立的 IPv6 连接" : "没有已建立的 IPv4 连接");
            empty.setTextColor(getResources().getColor(R.color.text_secondary));
            empty.setPadding(0, dp(20), 0, 0);
            list.addView(empty);
            return;
        }
        for (Conn c : conns) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), dp(10), dp(14), dp(10));

            LinearLayout left = new LinearLayout(this);
            left.setOrientation(LinearLayout.VERTICAL);

            TextView name = new TextView(this);
            name.setText(c.program);
            name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            name.setTextColor(getResources().getColor(R.color.text_primary));
            name.setTypeface(Typeface.DEFAULT_BOLD);
            name.setMaxLines(1);
            left.addView(name);

            TextView meta = new TextView(this);
            meta.setText((c.pid > 0 ? "PID " + c.pid : "PID —") + " · " + c.proto
                    + (c.age.isEmpty() ? "" : " · " + c.age));
            meta.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            meta.setTextColor(getResources().getColor(R.color.text_tertiary));
            left.addView(meta);

            row.addView(left, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            LinearLayout right = new LinearLayout(this);
            right.setOrientation(LinearLayout.VERTICAL);
            right.setGravity(Gravity.END);

            TextView peer = new TextView(this);
            peer.setText(c.peer);
            peer.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            peer.setTextColor(getResources().getColor(R.color.text_primary));
            peer.setGravity(Gravity.END);
            peer.setMaxLines(1);
            right.addView(peer);

            TextView local = new TextView(this);
            local.setText(c.local);
            local.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            local.setTextColor(getResources().getColor(R.color.text_tertiary));
            local.setGravity(Gravity.END);
            local.setMaxLines(1);
            right.addView(local);

            LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rightLp.leftMargin = dp(10);
            row.addView(right, rightLp);

            list.addView(row);

            View divider = new View(this);
            divider.setBackgroundColor(getResources().getColor(R.color.divider));
            list.addView(divider, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        }
    }

    private String formatDuration(long ms) {
        long sec = ms / 1000L;
        if (sec < 60) {
            return sec + "s";
        }
        long min = sec / 60;
        if (min < 60) {
            return min + "m" + (sec % 60) + "s";
        }
        long hour = min / 60;
        return hour + "h" + (min % 60) + "m";
    }

    private long parseLong(String s) {
        try {
            return Long.parseLong(s.trim());
        } catch (Throwable t) {
            return 0;
        }
    }

    private int parseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Throwable t) {
            return -1;
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
