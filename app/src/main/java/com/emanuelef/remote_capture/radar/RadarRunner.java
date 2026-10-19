package com.emanuelef.remote_capture.radar;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

public class RadarRunner {
    private static Process proc;
    private static boolean wanted = false;
    private static Context appCtx;
    private static int restarts = 0;
    private static long windowStart = 0;

    public static synchronized boolean isRunning() {
        if (proc == null) return false;
        try {
            proc.exitValue();
            return false;
        } catch (IllegalThreadStateException e) {
            return true;
        }
    }

    /** returns null on success, otherwise an error text */
    public static synchronized String start(Context ctx) {
        appCtx = ctx.getApplicationContext();
        wanted = true;
        restarts = 0;
        if (isRunning()) return null;
        File ev = new File(workDir(), "events.log");
        if (ev.length() > 100000) ev.delete();
        return spawn();
    }

    public static synchronized void stop() {
        wanted = false;
        if (proc != null) {
            proc.destroy();
            proc = null;
            event("радар остановлен");
        }
    }

    private static File workDir() {
        File w = new File(appCtx.getFilesDir(), "radar");
        w.mkdirs();
        return w;
    }

    private static void event(String msg) {
        try (FileOutputStream f = new FileOutputStream(new File(workDir(), "events.log"), true)) {
            String line = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date()) + " [APP] " + msg + "\n";
            f.write(line.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
        }
    }

    private static String spawn() {
        File libDir = new File(appCtx.getApplicationInfo().nativeLibraryDir);
        File bin = new File(libDir, "libradar.so");
        if (!bin.exists()) return "libradar.so не найден в " + libDir;
        File work = workDir();
        File log = new File(work, "radar.log");
        File prev = new File(work, "radar.prev.log");
        if (log.exists() && log.length() > 0) {
            prev.delete();
            log.renameTo(prev);
        }
        try {
            String cmd = "exec '" + bin.getAbsolutePath() + "' -ip 127.0.0.1 > radar.log 2>&1 < /dev/null";
            ProcessBuilder pb = new ProcessBuilder("/system/bin/sh", "-c", cmd);
            pb.directory(work);
            Map<String, String> env = pb.environment();
            env.put("LD_LIBRARY_PATH", libDir.getAbsolutePath());
            env.put("PCAPDROID", "1");
            env.put("HOME", work.getAbsolutePath());
            final Process p = pb.start();
            proc = p;
            event("радар запущен");
            Thread t = new Thread(() -> watch(p), "radar-watch");
            t.setDaemon(true);
            t.start();
            return null;
        } catch (IOException e) {
            return e.toString();
        }
    }

    private static void watch(Process p) {
        int code;
        try {
            code = p.waitFor();
        } catch (InterruptedException e) {
            return;
        }
        synchronized (RadarRunner.class) {
            if (proc != p) return;
            event("радар завершился, код " + code);
            if (!wanted) return;
            long now = System.currentTimeMillis();
            if (now - windowStart > 60000) {
                windowStart = now;
                restarts = 0;
            }
            if (++restarts > 5) {
                event("слишком много перезапусков подряд, остановлено");
                wanted = false;
                return;
            }
        }
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ignored) {
        }
        synchronized (RadarRunner.class) {
            if (wanted && proc == p) {
                event("перезапуск радара");
                String err = spawn();
                if (err != null) event("не удалось перезапустить: " + err);
            }
        }
    }

    private static String tail(File f, int max) {
        if (!f.exists() || f.length() == 0) return null;
        try (RandomAccessFile r = new RandomAccessFile(f, "r")) {
            long len = r.length();
            long start = Math.max(0, len - max);
            r.seek(start);
            byte[] buf = new byte[(int) (len - start)];
            r.readFully(buf);
            return new String(buf, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return e.toString();
        }
    }

    public static String readLog(Context ctx) {
        File dir = new File(ctx.getFilesDir(), "radar");
        StringBuilder sb = new StringBuilder();
        String ev = tail(new File(dir, "events.log"), 700);
        if (ev != null) sb.append(ev).append("\n———\n");
        File log = new File(dir, "radar.log");
        if (log.length() < 1500) {
            String pv = tail(new File(dir, "radar.prev.log"), 2200);
            if (pv != null) sb.append("[предыдущий запуск]\n").append(pv).append("\n———\n");
        }
        String cur = tail(log, 3200);
        sb.append(cur != null ? cur : "(лога нет)");
        return sb.toString();
    }
}
