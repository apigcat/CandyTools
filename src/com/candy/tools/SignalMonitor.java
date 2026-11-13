package com.candy.tools;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 一次性读取 dumpsys telephony.registry，解析信号与驻网信息。
 * 不做任何轮询 / 常驻进程，调用方点一次按钮跑一次。
 */
final class SignalMonitor {

    static final class Info {
        int rsrp = Integer.MIN_VALUE;
        int rsrq = Integer.MIN_VALUE;
        int snr = Integer.MIN_VALUE;
        int level = -1;
        int band = -1;
        int earfcn = -1;
        String operator = "";
        String tech = "";
        int grade = -1; // 2 优 / 1 中 / 0 差 / -1 未知

        String netLine() {
            if (operator.isEmpty() && band <= 0 && tech.isEmpty()) {
                return "";
            }
            StringBuilder sb = new StringBuilder(operator.isEmpty() ? "已驻网" : operator);
            if (!tech.isEmpty()) {
                sb.append(" · ").append(tech);
            }
            if (band > 0) {
                sb.append(" · B").append(band);
            }
            if (earfcn > 0) {
                sb.append(" · EARFCN ").append(earfcn);
            }
            return sb.toString();
        }

        String detail() {
            StringBuilder sb = new StringBuilder();
            sb.append("运营商：").append(operator.isEmpty() ? "—" : operator).append('\n');
            sb.append("制式：").append(tech.isEmpty() ? "—" : tech).append('\n');
            sb.append("频段：").append(band > 0 ? "B" + band : "—").append('\n');
            sb.append("频点：").append(earfcn > 0 ? "EARFCN " + earfcn : "—").append('\n');
            sb.append("RSRP：").append(rsrp != Integer.MIN_VALUE ? rsrp + " dBm" : "—").append('\n');
            sb.append("RSRQ：").append(rsrq != Integer.MIN_VALUE ? rsrq + " dB" : "—").append('\n');
            sb.append("SNR：").append(snr != Integer.MIN_VALUE ? snr + " dB" : "—").append('\n');
            sb.append("信号等级：").append(level >= 0 ? level + "/4" : "—").append('\n');
            sb.append("判定：").append(grade == 2 ? "优" : grade == 1 ? "中" : grade == 0 ? "差" : "—");
            return sb.toString();
        }
    }

    interface Callback {
        void onDone(Info info, long timeMillis);
    }

    private static final Pattern P_RSRP = Pattern.compile("rsrp=(-?\\d+)");
    private static final Pattern P_RSRQ = Pattern.compile("rsrq=(-?\\d+)");
    private static final Pattern P_SNR = Pattern.compile("rssnr=(-?\\d+)");
    private static final Pattern P_LEVEL = Pattern.compile("level=(\\d+)");
    private static final Pattern P_OP = Pattern.compile("mOperatorAlphaLong=([^,]*)");
    private static final Pattern P_TECH = Pattern.compile("getRilDataRadioTechnology=(\\d+)(?:\\((\\w+)\\))?");
    private static final Pattern P_BAND = Pattern.compile("mBands=\\[(\\d+)\\]");
    private static final Pattern P_EARFCN = Pattern.compile("mEarfcn=(-?\\d+)");
    private static final Pattern P_REG = Pattern.compile("mDataRegState=(\\d+)\\(([A-Z_]+)\\)");

    private SignalMonitor() {
    }

    /** 跑一次命令，回调一次，进程随即退出。 */
    static void refresh(final Callback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Shell.Result r = Shell.su(
                        "dumpsys telephony.registry 2>/dev/null | grep -E 'mSignalStrength|mServiceState'");
                Info info = parse(r.code == 0 ? r.out : "");
                callback.onDone(info, System.currentTimeMillis());
            }
        }, "signal-refresh").start();
    }

    static Info parse(String dump) {
        Info info = new Info();
        boolean hasNet = false;
        boolean hasSignal = false;
        if (dump == null) {
            return info;
        }
        for (String line : dump.split("\n")) {
            if (!hasNet && line.contains("mServiceState=")) {
                Matcher reg = P_REG.matcher(line);
                if (!reg.find() || !"0".equals(reg.group(1))) {
                    continue;
                }
                String op = match(P_OP, line);
                info.operator = op == null ? "" : op.trim();
                Matcher tm = P_TECH.matcher(line);
                if (tm.find()) {
                    info.tech = tm.group(2) == null ? techName(parseInt(tm.group(1))) : tm.group(2);
                }
                String band = match(P_BAND, line);
                String earfcn = match(P_EARFCN, line);
                info.band = band == null ? -1 : parseInt(band);
                info.earfcn = earfcn == null ? -1 : parseInt(earfcn);
                hasNet = true;
            } else if (!hasSignal && line.contains("mLte=CellSignalStrengthLte")) {
                Matcher rsrp = P_RSRP.matcher(line);
                if (!rsrp.find()) {
                    continue;
                }
                info.rsrp = parseInt(rsrp.group(1));
                String rsrq = match(P_RSRQ, line);
                String snr = match(P_SNR, line);
                String level = match(P_LEVEL, line);
                info.rsrq = rsrq == null ? Integer.MIN_VALUE : parseInt(rsrq);
                info.snr = snr == null ? Integer.MIN_VALUE : parseInt(snr);
                info.level = level == null ? -1 : parseInt(level);
                hasSignal = true;
            }
        }
        info.grade = gradeOf(info.rsrp, info.snr);
        return info;
    }

    private static int gradeOf(int rsrp, int snr) {
        if (rsrp == Integer.MIN_VALUE) {
            return -1;
        }
        int grade = rsrp >= -90 ? 2 : (rsrp >= -105 ? 1 : 0);
        if (grade == 2 && snr != Integer.MIN_VALUE && snr < 8) {
            grade = 1;
        }
        return grade;
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Throwable t) {
            return Integer.MIN_VALUE;
        }
    }

    private static String match(Pattern p, String s) {
        Matcher m = p.matcher(s);
        return m.find() ? m.group(1) : null;
    }

    private static String techName(int code) {
        switch (code) {
            case 14:
            case 13:
                return "LTE";
            case 20:
                return "NR";
            case 3:
                return "UMTS";
            case 2:
                return "EDGE";
            default:
                return "RAT" + code;
        }
    }
}
