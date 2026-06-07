package com.l.gpom.profiling;

import com.l.gpom.GPOM;
import com.l.gpom.config.GpomEarlyConfig;

import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class AsyncProbeLogger {
    private static final int INFO = 0;
    private static final int WARN = 1;
    private static final int ERROR = 2;
    private static final boolean ENABLED = GpomEarlyConfig.asyncProbeLogsEnabled();
    private static final int QUEUE_SIZE = Math.max(128, GpomEarlyConfig.asyncProbeLogQueueSize());
    private static final BlockingQueue<Event> QUEUE = new ArrayBlockingQueue<>(QUEUE_SIZE);
    private static final AtomicBoolean STARTED = new AtomicBoolean();
    private static final AtomicLong DROPPED = new AtomicLong();

    private AsyncProbeLogger() {
    }

    public static void info(String message, Object... args) {
        log(INFO, message, args);
    }

    public static void warn(String message, Object... args) {
        log(WARN, message, args);
    }

    public static void error(String message, Object... args) {
        log(ERROR, message, args);
    }

    private static void log(int level, String message, Object... args) {
        if (!ENABLED) {
            if (level == ERROR) {
                GPOM.LOGGER.error(message, args);
            } else if (level == WARN) {
                GPOM.LOGGER.warn(message, args);
            } else {
                GPOM.LOGGER.info(message, args);
            }
            return;
        }
        startWorker();
        Object[] copiedArgs = args == null || args.length == 0 ? null : Arrays.copyOf(args, args.length);
        if (!QUEUE.offer(new Event(level, message, copiedArgs))) {
            DROPPED.incrementAndGet();
        }
    }

    private static void startWorker() {
        if (!STARTED.compareAndSet(false, true)) {
            return;
        }
        Thread worker = new Thread(AsyncProbeLogger::runWorker, "GPOM-AsyncProbeLogger");
        worker.setDaemon(true);
        worker.setContextClassLoader(null);
        worker.start();

        Thread shutdown = new Thread(AsyncProbeLogger::drainQueue, "GPOM-AsyncProbeLogger-Shutdown");
        shutdown.setContextClassLoader(null);
        Runtime.getRuntime().addShutdownHook(shutdown);
    }

    private static void runWorker() {
        while (true) {
            try {
                write(QUEUE.take());
                drainQueue();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                return;
            } catch (Throwable throwable) {
                try {
                    GPOM.LOGGER.warn("[GPOM AsyncProbeLogger] Worker failure", throwable);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static void drainQueue() {
        Event event;
        while ((event = QUEUE.poll()) != null) {
            write(event);
        }
        long dropped = DROPPED.getAndSet(0L);
        if (dropped > 0L) {
            GPOM.LOGGER.warn("[GPOM AsyncProbeLogger] Dropped {} probe log lines because the async queue was full", dropped);
        }
    }

    private static void write(Event event) {
        if (event.args == null || event.args.length == 0) {
            if (event.level == ERROR) {
                GPOM.LOGGER.error(event.message);
            } else if (event.level == WARN) {
                GPOM.LOGGER.warn(event.message);
            } else {
                GPOM.LOGGER.info(event.message);
            }
        } else if (event.level == ERROR) {
            GPOM.LOGGER.error(event.message, event.args);
        } else if (event.level == WARN) {
            GPOM.LOGGER.warn(event.message, event.args);
        } else {
            GPOM.LOGGER.info(event.message, event.args);
        }
    }

    private static final class Event {
        private final int level;
        private final String message;
        private final Object[] args;

        private Event(int level, String message, Object[] args) {
            this.level = level;
            this.message = message;
            this.args = args;
        }
    }
}
