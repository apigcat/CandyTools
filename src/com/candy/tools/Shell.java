package com.candy.tools;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/** 极简 shell 执行器：一次性执行 su 命令并回收输出，不保留常驻进程。 */
final class Shell {

    static final class Result {
        int code = -1;
        String out = "";
    }

    private Shell() {
    }

    static Result su(String command) {
        return exec(new String[]{"su", "-c", command});
    }

    static Result exec(String[] cmd) {
        final Result result = new Result();
        Process process = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            process = pb.start();

            final Process proc = process;
            final StringBuilder sb = new StringBuilder();
            Thread reader = new Thread(new Runnable() {
                @Override
                public void run() {
                    BufferedReader br = null;
                    try {
                        br = new BufferedReader(new InputStreamReader(proc.getInputStream(), "UTF-8"));
                        String line;
                        while ((line = br.readLine()) != null) {
                            sb.append(line).append('\n');
                        }
                    } catch (Exception ignored) {
                    } finally {
                        if (br != null) {
                            try {
                                br.close();
                            } catch (Exception ignored) {
                            }
                        }
                    }
                }
            });
            reader.setDaemon(true);
            reader.start();

            final boolean[] finished = new boolean[]{false};
            Thread watchdog = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(20000);
                    } catch (InterruptedException e) {
                        return;
                    }
                    synchronized (finished) {
                        if (!finished[0]) {
                            proc.destroy();
                        }
                    }
                }
            });
            watchdog.setDaemon(true);
            watchdog.start();

            int code = process.waitFor();
            synchronized (finished) {
                finished[0] = true;
            }
            watchdog.interrupt();
            reader.join(1500);

            result.code = code;
            result.out = sb.toString().trim();
        } catch (Exception e) {
            result.code = -1;
            result.out = "无法执行 " + cmd[0] + "：" + e.getMessage();
        } finally {
            if (process != null) {
                try {
                    process.destroy();
                } catch (Exception ignored) {
                }
            }
        }
        return result;
    }
}
