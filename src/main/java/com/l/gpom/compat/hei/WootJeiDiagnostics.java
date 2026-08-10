package com.l.gpom.compat.hei;

import net.minecraftforge.fml.common.Loader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class WootJeiDiagnostics {
    private static final Object LOCK = new Object();
    private static final SimpleDateFormat TIMESTAMP =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT);
    private static boolean initialized;

    private WootJeiDiagnostics() {
    }

    static void log(String message, Object... values) {
        write(format(message, values), null);
    }

    static void error(String message, Throwable throwable) {
        write(message, throwable);
    }

    private static void write(String message, Throwable throwable) {
        synchronized (LOCK) {
            File logFile = logFile();
            File parent = logFile.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                return;
            }
            try (PrintWriter writer = new PrintWriter(new FileOutputStream(logFile, initialized))) {
                if (!initialized) {
                    writer.println("GPOM Woot/HEI diagnostics - one file per launch");
                    initialized = true;
                }
                writer.print('[');
                writer.print(TIMESTAMP.format(new Date()));
                writer.print("] [");
                writer.print(Thread.currentThread().getName());
                writer.print("] ");
                writer.println(message);
                if (throwable != null) {
                    throwable.printStackTrace(writer);
                }
            } catch (IOException ignored) {
                // Diagnostics must never interfere with HEI registration.
            }
        }
    }

    private static File logFile() {
        try {
            File configDirectory = Loader.instance().getConfigDir();
            if (configDirectory != null && configDirectory.getParentFile() != null) {
                return new File(new File(configDirectory.getParentFile(), "logs"), "gpom-woot-jei.log");
            }
        } catch (Throwable ignored) {
            // The integration is client-only, but retain a safe fallback for very early loading.
        }
        return new File("logs", "gpom-woot-jei.log");
    }

    private static String format(String message, Object... values) {
        String result = message;
        if (values == null) {
            return result;
        }
        for (Object value : values) {
            int placeholder = result.indexOf("{}");
            if (placeholder < 0) {
                break;
            }
            result = result.substring(0, placeholder)
                    + String.valueOf(value)
                    + result.substring(placeholder + 2);
        }
        return result;
    }
}
