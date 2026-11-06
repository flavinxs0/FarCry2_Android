package com.deivid22srk.fc2;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.util.Date;
import java.util.List;
import org.libsdl.app.SDLActivity;

public class Fc2Activity extends SDLActivity {
    @Override
    protected String[] getLibraries() {
        return new String[] { "SDL2", "dxvk_d3d9", "main" };
    }

    /* argv[1] = game folder, argv[2] = user's libvulkan.so folder ("" for the
     * system driver), argv[3] = nativeLibraryDir for libadrenotools, argv[4] =
     * selected back-buffer height, argv[5] = Mali shader/clip-plane test mode. */
    @Override
    protected String[] getArguments() {
        String game = getIntent().getStringExtra("game");
        if (game == null) return new String[0];
        String driver = getIntent().getStringExtra("driver");
        int renderHeight = getIntent().getIntExtra("renderHeight", 0);
        int maliShaderTest = getIntent().getIntExtra("maliShaderTest", 0);
        return new String[] {
                game,
                driver == null ? "" : driver,
                getApplicationInfo().nativeLibraryDir,
                Integer.toString(renderHeight),
                Integer.toString(maliShaderTest) };
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        saveLastExit(this, getIntent().getStringExtra("game"));
        super.onCreate(savedInstanceState);
    }

    /* Capture the previous app exit when either the game activity or the
     * launcher starts. Android 11+ may also provide a native tombstone stream. */
    static void saveLastExit(Context context, String game) {
        if (Build.VERSION.SDK_INT < 30 || game == null) return;
        File dir = new File(game, "port");
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            List<ApplicationExitInfo> exits = am.getHistoricalProcessExitReasons(null, 0, 3);
            if (exits == null || exits.isEmpty()) return;
            if (!dir.exists() && !dir.mkdirs()) return;
            File traceFile = new File(dir, "native_trace.bin");
            boolean wroteTrace = false;
            try (FileWriter w = new FileWriter(new File(dir, "last_exit.txt"))) {
                for (ApplicationExitInfo e : exits) {
                    w.write(new Date(e.getTimestamp()) + ": reason " + reasonName(e.getReason())
                            + " status " + e.getStatus() + " importance " + e.getImportance()
                            + " pss " + e.getPss() / 1024 + "MB rss " + e.getRss() / 1024 + "MB"
                            + " desc " + e.getDescription() + "\n");
                    InputStream trace = wroteTrace ? null : e.getTraceInputStream();
                    if (trace != null) {
                        try (InputStream in = trace; FileOutputStream out = new FileOutputStream(traceFile)) {
                            byte[] buf = new byte[8192];
                            int total = 0, n;
                            while ((n = in.read(buf)) != -1 && total < 2 * 1024 * 1024) {
                                int take = Math.min(n, 2 * 1024 * 1024 - total);
                                out.write(buf, 0, take);
                                total += take;
                            }
                            wroteTrace = total > 0;
                        } catch (Exception ignored) {
                            // Exit metadata is still useful if Android withholds the tombstone.
                        }
                    }
                }
                ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                am.getMemoryInfo(mi);
                w.write("device RAM " + mi.totalMem / (1 << 20) + "MB, available now " + mi.availMem / (1 << 20)
                        + "MB, low-memory threshold " + mi.threshold / (1 << 20) + "MB\n");
            }
            if (!wroteTrace && traceFile.exists()) traceFile.delete();
        } catch (Exception ignored) {
            // Diagnostics are best-effort and must not prevent launching the game.
        }
    }

    private static String reasonName(int r) {
        switch (r) {
        case ApplicationExitInfo.REASON_LOW_MEMORY: return "LOW_MEMORY";
        case ApplicationExitInfo.REASON_CRASH: return "CRASH_JAVA";
        case ApplicationExitInfo.REASON_CRASH_NATIVE: return "CRASH_NATIVE";
        case ApplicationExitInfo.REASON_SIGNALED: return "SIGNALED";
        case ApplicationExitInfo.REASON_ANR: return "ANR";
        case ApplicationExitInfo.REASON_EXIT_SELF: return "EXIT_SELF";
        case ApplicationExitInfo.REASON_USER_REQUESTED: return "USER_REQUESTED";
        case ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE: return "EXCESSIVE_RESOURCE_USAGE";
        case ApplicationExitInfo.REASON_OTHER: return "OTHER";
        default: return "code " + r;
        }
    }
}
