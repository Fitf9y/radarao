package com.emanuelef.remote_capture.radar;

import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class RadarRunner {
    private static Process proc;

    public static synchronized boolean isRunning() {
        if (proc == null) return false;
        try {
            proc.exitValue();
            proc = null;
            return false;
        } catch (IllegalThreadStateException e) {
            return true;
        }
    }

    /** returns null on success, otherwise an error text */
    public static synchronized String start(Context ctx) {
        if (isRunning()) return null;
        File libDir = new File(ctx.getApplicationInfo().nativeLibraryDir);
        File bin = new File(libDir, "libradar.so");
        if (!bin.exists()) return "libradar.so не найден в " + libDir;
        File work = new File(ctx.getFilesDir(), "radar");
        work.mkdirs();
        try {
            String cmd = "exec '" + bin.getAbsolutePath() + "' -ip 127.0.0.1 > radar.log 2>&1 < /dev/null";
            ProcessBuilder pb = new ProcessBuilder("/system/bin/sh", "-c", cmd);
            pb.directory(work);
            Map<String, String> env = pb.environment();
            env.put("LD_LIBRARY_PATH", libDir.getAbsolutePath());
            env.put("PCAPDROID", "1");
            env.put("HOME", work.getAbsolutePath());
            proc = pb.start();
            return null;
        } catch (IOException e) {
            return e.toString();
        }
    }

    public static synchronized void stop() {
        if (proc != null) {
            proc.destroy();
            proc = null;
        }
    }

    public static String readLog(Context ctx) {
        File f = new File(new File(ctx.getFilesDir(), "radar"), "radar.log");
        if (!f.exists()) return "(лога нет)";
        try (RandomAccessFile r = new RandomAccessFile(f, "r")) {
            long len = r.length();
            long start = Math.max(0, len - 4000);
            r.seek(start);
            byte[] buf = new byte[(int) (len - start)];
            r.readFully(buf);
            return new String(buf, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return e.toString();
        }
    }
}
