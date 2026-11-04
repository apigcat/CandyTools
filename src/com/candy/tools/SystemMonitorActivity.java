package com.candy.tools;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 系统监控：CPU / 内存 / 网络连接。 */
public class SystemMonitorActivity extends Activity {

    private static final Pattern P_CPU = Pattern.compile(
            "^\\s*([0-9.]+)%\\s+(\\d+)/([^:\\s]+):");

    private static final int GREEN = 0xFF22C55E;
    private static final int PURPLE = 0xFF7C3AED;

    private RingView cpuUserRing;
    private RingView cpuSysRing;
    private RingView memUserRing;
    private RingView memSysRing;
    private RingView netV4Ring;
    private RingView netV6Ring;
    private TextView updated;

    private static final class Item {
        String pkg;
        int pid;
        int uid;
        float percent;
        long rssKb;
    }

    private static final class Conn {
        String proc;
        int pid;
        String local;
        String peer;
        boolean ipv6;
        boolean inbound;
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

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("系统监控");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(getResources().getColor(R.color.text_primary));
        top.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView refresh = new TextView(this);
        refresh.setText("刷新");
        refresh.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        refresh.setTypeface(Typeface.DEFAULT_BOLD);
        refresh.setTextColor(getResources().getColor(R.color.neon));
        refresh.setBackgroundResource(R.drawable.btn_ghost);
        refresh.setPadding(dp(14), dp(8), dp(14), dp(8));
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (updated != null) {
                    updated.setText("刷新中…");
                }
                refreshSummary();
            }
        });
        top.addView(refresh);
        root.addView(top);

        updated = new TextView(this);
        updated.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        updated.setTextColor(getResources().getColor(R.color.text_tertiary));
        LinearLayout.LayoutParams uLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        uLp.topMargin = dp(6);
        root.addView(updated, uLp);

        LinearLayout cpuCard = ringCard("CPU");
        cpuUserRing = (RingView) cpuCard.findViewWithTag("ringA");
        cpuSysRing = (RingView) cpuCard.findViewWithTag("ringB");
        addViewButton(cpuCard, "cpu");
        root.addView(cpuCard);

        LinearLayout memCard = ringCard("内存");
        memUserRing = (RingView) memCard.findViewWithTag("ringA");
        memSysRing = (RingView) memCard.findViewWithTag("ringB");
        addViewButton(memCard, "mem");
        root.addView(memCard);

        LinearLayout netCard = ringCard("网络连接");
        netV4Ring = (RingView) netCard.findViewWithTag("ringA");
        netV6Ring = (RingView) netCard.findViewWithTag("ringB");
        addViewButton(netCard, "net");
        root.addView(netCard);

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshSummary();
    }

    // ------------------------------------------------------------ 卡片

    private LinearLayout ringCard(String name) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.card_bg);
        int p = dp(12);
        card.setPadding(p, p, p, p);

        TextView t = new TextView(this);
        t.setText(name);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        t.setTextColor(getResources().getColor(R.color.text_secondary));
        card.addView(t);

        LinearLayout rings = new LinearLayout(this);
        rings.setOrientation(LinearLayout.HORIZONTAL);
        rings.setGravity(Gravity.CENTER);
        boolean isNet = name.equals("网络连接");
        rings.addView(ringColumn(isNet ? "IPv4" : "用户", "ringA", GREEN), ringLp(0));
        rings.addView(ringColumn(isNet ? "IPv6" : "系统", "ringB", PURPLE), ringLp(1));
        card.addView(rings);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        card.setLayoutParams(lp);
        return card;
    }

    private LinearLayout ringColumn(String label, String tag, int color) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);

        RingView ring = new RingView(this);
        ring.setTag(tag);
        ring.setColor(color);
        col.addView(ring, new LinearLayout.LayoutParams(dp(60), dp(60)));

        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);
        t.setTextColor(getResources().getColor(R.color.text_secondary));
        t.setGravity(Gravity.CENTER);
        col.addView(t);
        return col;
    }

    private LinearLayout.LayoutParams ringLp(int index) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        if (index > 0) {
            lp.leftMargin = dp(6);
        }
        return lp;
    }

    private void addViewButton(final LinearLayout card, final String kind) {
        TextView view = new TextView(this);
        view.setText("查看");
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setTextColor(getResources().getColor(R.color.neon));
        view.setBackgroundResource(R.drawable.btn_ghost);
        view.setPadding(dp(10), dp(4), dp(10), dp(4));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(6);
        card.addView(view, lp);
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if ("cpu".equals(kind)) {
                    showListDialog("CPU 占用", true);
                } else if ("mem".equals(kind)) {
                    showListDialog("内存占用", false);
                } else {
                    showConns();
                }
            }
        });
    }

    // ------------------------------------------------------------ 汇总

    private void refreshSummary() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<Item> cpuUser = listCpu(false);
                final List<Item> cpuSys = listCpu(true);
                final List<Item> memUser = listMem(false);
                final List<Item> memSys = listMem(true);
                final List<Conn> conns = listConns();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (cpuUserRing != null) {
                            cpuUserRing.setValue(Math.min(100, sumPct(cpuUser)));
                            cpuSysRing.setValue(Math.min(100, sumPct(cpuSys)));
                        }
                        if (memUserRing != null) {
                            memUserRing.setValue((float) Math.min(100, sumMem(memUser)));
                            memSysRing.setValue((float) Math.min(100, sumMem(memSys)));
                        }
                        int v4 = 0;
                        int v6 = 0;
                        for (Conn c : conns) {
                            if (c.ipv6) {
                                v6++;
                            } else {
                                v4++;
                            }
                        }
                        int total = v4 + v6;
                        if (netV4Ring != null) {
                            netV4Ring.setValue(total == 0 ? 0 : v4 * 100f / total);
                            netV6Ring.setValue(total == 0 ? 0 : v6 * 100f / total);
                            netV4Ring.setCenterText(v4 + "个");
                            netV6Ring.setCenterText(v6 + "个");
                        }
                        updated.setText(String.format(Locale.US,
                                "IPv4 %d · IPv6 %d · 更新 %s", v4, v6,
                                new java.text.SimpleDateFormat("HH:mm:ss", Locale.US)
                                        .format(new java.util.Date())));
                    }
                });
            }
        }, "sysmon-sum").start();
    }

    private float sumPct(List<Item> list) {
        float s = 0;
        for (Item it : list) {
            s += it.percent;
        }
        return s;
    }

    private double sumMem(List<Item> list) {
        long s = 0;
        for (Item it : list) {
            s += it.rssKb;
        }
        if (s <= 0) {
            return 0;
        }
        long total = memTotalKb();
        return total > 0 ? s * 100.0 / total : 0;
    }

    private long memTotalKb() {
        try {
            java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.FileReader("/proc/meminfo"));
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("MemTotal:")) {
                    return kb(line);
                }
            }
            br.close();
        } catch (Throwable ignored) {
        }
        return 0;
    }

    private long kb(String line) {
        try {
            String[] p = line.trim().split("\\s+");
            return Long.parseLong(p[1]);
        } catch (Throwable t) {
            return 0;
        }
    }

    // ------------------------------------------------------------ 列表

    private List<Item> listCpu(boolean system) {
        List<Item> list = new ArrayList<Item>();
        Set<String> userPkgs = new HashSet<String>();
        Shell.Result up = Shell.su("pm list packages -3 2>/dev/null");
        for (String line : up.out.split("\n")) {
            String t = line.trim();
            if (t.startsWith("package:")) {
                userPkgs.add(t.substring("package:".length()).trim());
            }
        }
        String cmd = system
                ? "dumpsys cpuinfo | grep -E '^[ ]*[0-9.]+%' | sort -t'%' -k1 -rn | head -40"
                : "pm list packages -3 | sed 's/^package://' >/data/local/tmp/up.txt; "
                + "dumpsys cpuinfo | grep -F -f /data/local/tmp/up.txt "
                + "| grep -E '^[ ]*[0-9.]+%' | sort -t'%' -k1 -rn | head -20";
        Shell.Result r = Shell.su(cmd);
        for (String line : r.out.split("\n")) {
            Matcher m = P_CPU.matcher(line);
            if (!m.find()) {
                continue;
            }
            Item it = new Item();
            try {
                it.percent = Float.parseFloat(m.group(1));
                it.pid = Integer.parseInt(m.group(2));
            } catch (Throwable t) {
                continue;
            }
            it.pkg = m.group(3).trim();
            if (!it.pkg.contains(".") || it.pkg.startsWith("[")) {
                continue;
            }
            boolean isUser = userPkgs.contains(it.pkg);
            if (system == isUser) {
                continue;
            }
            it.uid = isUser ? 10000 : 1000;
            list.add(it);
        }
        return list;
    }

    private List<Item> listMem(boolean system) {
        List<Item> list = new ArrayList<Item>();
        Shell.Result r = Shell.su("ps -A -o PID,UID,RSS,NAME 2>/dev/null");
        for (String line : r.out.split("\n")) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("PID")) {
                continue;
            }
            String[] p = t.split("\\s+");
            if (p.length < 4) {
                continue;
            }
            Item it = new Item();
            try {
                it.pid = Integer.parseInt(p[0]);
                it.uid = Integer.parseInt(p[1]);
                it.rssKb = Long.parseLong(p[2]);
            } catch (Throwable e) {
                continue;
            }
            StringBuilder name = new StringBuilder();
            for (int i = 3; i < p.length; i++) {
                if (name.length() > 0) {
                    name.append(' ');
                }
                name.append(p[i]);
            }
            it.pkg = name.toString().trim();
            if (it.rssKb <= 0 || it.pkg.isEmpty() || it.pkg.startsWith("[")) {
                continue;
            }
            boolean isUser = it.uid >= 10000;
            if (system == isUser) {
                continue;
            }
            list.add(it);
        }
        Collections.sort(list, new Comparator<Item>() {
            @Override
            public int compare(Item a, Item b) {
                return Long.compare(b.rssKb, a.rssKb);
            }
        });
        while (list.size() > 40) {
            list.remove(list.size() - 1);
        }
        return list;
    }

    private void showListDialog(final String title, final boolean cpu) {
        final LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(8), dp(14), dp(8));

        final TextView chipUser = segChip("用户", true);
        final TextView chipSys = segChip("系统", false);
        LinearLayout seg = new LinearLayout(this);
        seg.setOrientation(LinearLayout.HORIZONTAL);
        seg.addView(chipUser, segLp(0));
        seg.addView(chipSys, segLp(1));
        content.addView(seg);

        final LinearLayout listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        content.addView(listBox);
        listBox.addView(hint("读取中…"));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (int) (getResources().getDisplayMetrics().heightPixels * 0.62)));

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(wrapper)
                .setNegativeButton("关闭", null)
                .show();

        chipUser.setTag(Boolean.TRUE);
        chipSys.setTag(Boolean.FALSE);
        final List<Item>[] data = new List[]{new ArrayList<Item>(), new ArrayList<Item>()};
        final Runnable[] render = new Runnable[1];
        render[0] = new Runnable() {
            @Override
            public void run() {
                boolean isUser = (Boolean) chipUser.getTag();
                styleSeg(chipUser, isUser);
                styleSeg(chipSys, !isUser);
                listBox.removeAllViews();
                List<Item> list = data[isUser ? 0 : 1];
                if (list.isEmpty()) {
                    listBox.addView(hint("没有数据"));
                }
                for (Item it : list) {
                    listBox.addView(itemRow(it, cpu));
                }
            }
        };
        chipUser.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chipUser.setTag(Boolean.TRUE);
                chipSys.setTag(Boolean.FALSE);
                render[0].run();
            }
        });
        chipSys.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chipUser.setTag(Boolean.FALSE);
                chipSys.setTag(Boolean.TRUE);
                render[0].run();
            }
        });

        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<Item> user = cpu ? listCpu(false) : listMem(false);
                final List<Item> sys = cpu ? listCpu(true) : listMem(true);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        data[0] = user;
                        data[1] = sys;
                        render[0].run();
                    }
                });
            }
        }, "sysmon-list").start();
    }

    private View itemRow(final Item it, final boolean cpu) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.card_bg);
        int p = dp(9);
        row.setPadding(p, p, p, p);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);

        TextView name = new TextView(this);
        name.setText(it.pkg);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setTextColor(it.uid >= 10000
                ? getResources().getColor(R.color.text_primary) : 0xFF9CA3AF);
        name.setMaxLines(1);
        texts.addView(name);

        TextView sub = new TextView(this);
        sub.setText(cpu
                ? String.format(Locale.US, "CPU %.1f%% · PID %d", it.percent, it.pid)
                : String.format(Locale.US, "%s · PID %d", mb(it.rssKb * 1024), it.pid));
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);
        sub.setTextColor(getResources().getColor(R.color.text_tertiary));
        texts.addView(sub);

        row.addView(texts, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (it.uid >= 10000) {
            TextView kill = new TextView(this);
            kill.setText("终止");
            kill.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            kill.setTypeface(Typeface.DEFAULT_BOLD);
            kill.setTextColor(0xFFEF4444);
            kill.setBackgroundResource(R.drawable.btn_ghost);
            kill.setPadding(dp(9), dp(4), dp(9), dp(4));
            kill.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    killDialog(it.pkg, it.pid);
                }
            });
            row.addView(kill);
        }

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(6);
        row.setLayoutParams(lp);
        return row;
    }

    // ------------------------------------------------------------ 网络连接

    private List<Conn> listConns() {
        List<Conn> list = new ArrayList<Conn>();
        Set<String> listenPorts = new HashSet<String>();
        Shell.Result lr = Shell.su("ss -tlnp 2>/dev/null");
        for (String line : lr.out.split("\n")) {
            String[] p = line.trim().split("\\s+");
            if (p.length >= 4) {
                String local = p[3];
                int idx = local.lastIndexOf(':');
                if (idx > 0) {
                    listenPorts.add(local.substring(idx + 1));
                }
            }
        }
        Shell.Result r = Shell.su("ss -tunp 2>/dev/null");
        for (String line : r.out.split("\n")) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("Netid")) {
                continue;
            }
            String[] p = t.split("\\s+");
            if (p.length < 6 || !p[1].contains("ESTAB")) {
                continue;
            }
            Conn c = new Conn();
            c.local = p[4];
            c.peer = p[5];
            c.ipv6 = colons(c.local) > 1 || colons(c.peer) > 1;
            int lp = c.local.lastIndexOf(':');
            c.inbound = lp > 0 && listenPorts.contains(c.local.substring(lp + 1));
            if (p.length > 6) {
                String proc = p[6];
                int idx = proc.indexOf("pid=");
                if (idx >= 0) {
                    String rest = proc.substring(idx + 4);
                    int end = 0;
                    while (end < rest.length() && Character.isDigit(rest.charAt(end))) {
                        end++;
                    }
                    try {
                        c.pid = Integer.parseInt(rest.substring(0, end));
                    } catch (Throwable ignored) {
                    }
                }
                int q = proc.indexOf('"');
                int q2 = proc.indexOf('"', q + 1);
                if (q >= 0 && q2 > q) {
                    c.proc = proc.substring(q + 1, q2);
                }
            }
            list.add(c);
            if (list.size() >= 120) {
                break;
            }
        }
        return list;
    }

    private int colons(String s) {
        if (s == null) {
            return 0;
        }
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == ':') {
                n++;
            }
        }
        return n;
    }

    private void showConns() {
        final LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(8), dp(14), dp(8));

        final TextView chipV4 = segChip("IPv4", true);
        final TextView chipV6 = segChip("IPv6", false);
        LinearLayout seg = new LinearLayout(this);
        seg.setOrientation(LinearLayout.HORIZONTAL);
        seg.addView(chipV4, segLp(0));
        seg.addView(chipV6, segLp(1));
        content.addView(seg);

        final LinearLayout listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        content.addView(listBox);
        listBox.addView(hint("读取中…"));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (int) (getResources().getDisplayMetrics().heightPixels * 0.64)));

        new AlertDialog.Builder(this)
                .setTitle("网络连接")
                .setView(wrapper)
                .setNegativeButton("关闭", null)
                .show();

        chipV4.setTag(Boolean.TRUE);
        chipV6.setTag(Boolean.FALSE);
        final List<List<Conn>> data = new ArrayList<List<Conn>>();
        data.add(new ArrayList<Conn>());
        data.add(new ArrayList<Conn>());
        final Runnable[] render = new Runnable[1];
        render[0] = new Runnable() {
            @Override
            public void run() {
                boolean isV4 = (Boolean) chipV4.getTag();
                styleSeg(chipV4, isV4);
                styleSeg(chipV6, !isV4);
                listBox.removeAllViews();
                renderConnBlocks(listBox, data.get(isV4 ? 0 : 1));
            }
        };
        chipV4.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chipV4.setTag(Boolean.TRUE);
                chipV6.setTag(Boolean.FALSE);
                render[0].run();
            }
        });
        chipV6.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chipV4.setTag(Boolean.FALSE);
                chipV6.setTag(Boolean.TRUE);
                render[0].run();
            }
        });

        new Thread(new Runnable() {
            @Override
            public void run() {
                List<Conn> all = listConns();
                final List<Conn> v4 = new ArrayList<Conn>();
                final List<Conn> v6 = new ArrayList<Conn>();
                for (Conn c : all) {
                    if (c.ipv6) {
                        v6.add(c);
                    } else {
                        v4.add(c);
                    }
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        data.set(0, v4);
                        data.set(1, v6);
                        render[0].run();
                    }
                });
            }
        }, "sysmon-conn").start();
    }

    private void renderConnBlocks(LinearLayout parent, List<Conn> list) {
        if (list.isEmpty()) {
            parent.addView(hint("没有连接"));
            return;
        }
        Map<String, List<Conn>> groups = new LinkedHashMap<String, List<Conn>>();
        for (Conn c : list) {
            String key = c.proc == null ? "未知进程" : c.proc;
            List<Conn> g = groups.get(key);
            if (g == null) {
                g = new ArrayList<Conn>();
                groups.put(key, g);
            }
            g.add(c);
        }
        for (Map.Entry<String, List<Conn>> e : groups.entrySet()) {
            LinearLayout block = new LinearLayout(this);
            block.setOrientation(LinearLayout.VERTICAL);
            block.setBackgroundResource(R.drawable.card_bg);
            block.setPadding(dp(9), dp(8), dp(9), dp(8));

            LinearLayout head = new LinearLayout(this);
            head.setOrientation(LinearLayout.HORIZONTAL);
            head.setGravity(Gravity.CENTER_VERTICAL);

            TextView name = new TextView(this);
            name.setText(e.getKey());
            name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            name.setTypeface(Typeface.DEFAULT_BOLD);
            name.setTextColor(getResources().getColor(R.color.text_primary));
            name.setMaxLines(1);
            head.addView(name, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            final int pid = e.getValue().get(0).pid;
            if (pid > 0) {
                TextView kill = new TextView(this);
                kill.setText("终止");
                kill.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
                kill.setTypeface(Typeface.DEFAULT_BOLD);
                kill.setTextColor(0xFFEF4444);
                kill.setBackgroundResource(R.drawable.btn_ghost);
                kill.setPadding(dp(8), dp(3), dp(8), dp(3));
                kill.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        killDialog(e.getKey(), pid);
                    }
                });
                head.addView(kill);
            }
            block.addView(head);

            for (Conn c : e.getValue()) {
                TextView line = new TextView(this);
                line.setText((c.inbound ? "入站  " : "出站  ") + c.local + " → " + c.peer);
                line.setTextSize(TypedValue.COMPLEX_UNIT_SP, 8);
                line.setTextColor(c.inbound ? 0xFFF59E0B : 0xFF22C55E);
                line.setMaxLines(1);
                block.addView(line);
            }

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(6);
            parent.addView(block, lp);
        }
    }

    // ------------------------------------------------------------ 通用

    private void killDialog(String label, final int pid) {
        new AlertDialog.Builder(this)
                .setTitle("终止 " + (label == null ? "pid " + pid : label) + "？")
                .setMessage("PID " + pid)
                .setNegativeButton("取消", null)
                .setPositiveButton("kill -9", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                final Shell.Result r = Shell.su("kill -9 " + pid
                                        + " 2>&1; echo RC=$?");
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(SystemMonitorActivity.this,
                                                r.out.contains("RC=0") ? "已终止" : "失败",
                                                Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }
                        }, "sysmon-kill").start();
                    }
                })
                .show();
    }

    private TextView segChip(String text, boolean active) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(8), dp(6), dp(8), dp(6));
        styleSeg(v, active);
        return v;
    }

    private void styleSeg(TextView v, boolean active) {
        v.setTextColor(getResources().getColor(active ? R.color.neon : R.color.text_secondary));
        v.setBackgroundResource(active ? R.drawable.chip_bg : R.drawable.card_bg);
    }

    private LinearLayout.LayoutParams segLp(int index) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        if (index > 0) {
            lp.leftMargin = dp(6);
        }
        return lp;
    }

    private TextView hint(String text) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        v.setTextColor(getResources().getColor(R.color.text_secondary));
        v.setPadding(0, dp(10), 0, dp(10));
        return v;
    }

    private String mb(long bytes) {
        double m = bytes / 1024.0 / 1024.0;
        if (m >= 1024) {
            return String.format(Locale.US, "%.1f GB", m / 1024.0);
        }
        return String.format(Locale.US, "%.0f MB", m);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    /** 圆环进度。 */
    static class RingView extends View {
        private String centerText = null;
        private float target = 0;
        private float shown = 0;
        private int color = GREEN;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final android.os.Handler h = new android.os.Handler(
                android.os.Looper.getMainLooper());

        RingView(Context c) {
            super(c);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(8f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            text.setTextAlign(Paint.Align.CENTER);
            text.setTypeface(Typeface.DEFAULT_BOLD);
        }

        void setColor(int c) {
            color = c;
            invalidate();
        }

        void setCenterText(String t) {
            centerText = t;
            invalidate();
        }

        void setValue(float v) {
            target = Math.max(0, Math.min(100, v));
            h.removeCallbacks(step);
            h.post(step);
        }

        private final Runnable step = new Runnable() {
            @Override
            public void run() {
                float diff = target - shown;
                if (Math.abs(diff) < 0.4f) {
                    shown = target;
                    invalidate();
                    return;
                }
                shown += diff * 0.25f;
                invalidate();
                h.postDelayed(this, 16);
            }
        };

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float radius = Math.min(getWidth(), getHeight()) / 2f - 8f;

            Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
            track.setStyle(Paint.Style.STROKE);
            track.setStrokeWidth(8f);
            track.setColor(0xFF2A2A30);
            canvas.drawCircle(cx, cy, radius, track);

            paint.setColor(color);
            android.graphics.RectF oval = new android.graphics.RectF(
                    cx - radius, cy - radius, cx + radius, cy + radius);
            canvas.drawArc(oval, -90f, 360f * shown / 100f, false, paint);

            text.setTextSize(radius * 0.45f);
            text.setColor(color);
            String label = centerText != null ? centerText
                    : String.format(Locale.US, "%.0f%%", shown);
            canvas.drawText(label, cx, cy + radius * 0.18f, text);
        }
    }
}
