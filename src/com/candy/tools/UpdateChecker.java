package com.candy.tools;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * 更新检查：只在用户点击时调用一次，不做后台轮询。
 * 数据源是仓库根目录的 version.json（jsDelivr 优先，raw.githubusercontent 兜底）。
 */
final class UpdateChecker {

    static final class Result {
        boolean ok;
        int versionCode;
        String versionName = "";
        String notes = "";
        String url = "";
    }

    interface Callback {
        void onResult(Result result);
    }

    private static final String[] SOURCES = {
            "https://cdn.jsdelivr.net/gh/apigcat/CandyTools@main/version.json",
            "https://raw.githubusercontent.com/apigcat/CandyTools/main/version.json"
    };

    private UpdateChecker() {
    }

    static void check(final Callback callback) {
        final Handler ui = new Handler(Looper.getMainLooper());
        new Thread(new Runnable() {
            @Override
            public void run() {
                Result result = null;
                for (String base : SOURCES) {
                    result = fetch(base);
                    if (result != null) {
                        break;
                    }
                }
                final Result finalResult = result != null ? result : failed();
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onResult(finalResult);
                    }
                });
            }
        }, "update-check").start();
    }

    private static Result failed() {
        Result r = new Result();
        r.ok = false;
        return r;
    }

    private static Result fetch(String base) {
        HttpURLConnection conn = null;
        try {
            String url = base + "?t=" + (System.currentTimeMillis() / 60000);
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(6000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("Accept", "application/json");
            if (conn.getResponseCode() != 200) {
                return null;
            }
            StringBuilder sb = new StringBuilder();
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();

            JSONObject json = new JSONObject(sb.toString());
            Result r = new Result();
            r.ok = true;
            r.versionCode = json.optInt("versionCode", 0);
            r.versionName = json.optString("versionName", "");
            r.notes = json.optString("notes", "");
            r.url = json.optString("url", "https://github.com/apigcat/CandyTools/releases/latest");
            return r;
        } catch (Throwable t) {
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}
