package com.l.gpom.optimization;

import com.l.gpom.GPOM;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class FmlConstructionSafety {
    private static final Object CLASSLOADER_LOCK = new Object();
    private static final Object FORGE_LOCK = new Object();
    private static final Object NETWORK_LOCK = new Object();
    private static final Object PROXY_LOCK = new Object();
    private static final Object SUBSCRIBER_LOCK = new Object();
    private static final Object CONFIG_LOCK = new Object();
    private static final Object ANNOTATION_LOCK = new Object();
    private static final Set<String> LOGGED_GROUPS = ConcurrentHashMap.newKeySet();

    private FmlConstructionSafety() {
    }

    public static <E extends Exception> void classloaderMutation(String stage, ThrowingRunnable<E> action) throws E {
        run(CLASSLOADER_LOCK, "classloader mutations", stage, action);
    }

    public static <E extends Exception> void forgeSharedMutation(String stage, ThrowingRunnable<E> action) throws E {
        run(FORGE_LOCK, "Forge shared construction mutations", stage, action);
    }

    public static <E extends Exception> void networkRegistration(String stage, ThrowingRunnable<E> action) throws E {
        run(NETWORK_LOCK, "network registrations", stage, action);
    }

    public static <E extends Exception> void proxyInjection(String stage, ThrowingRunnable<E> action) throws E {
        run(PROXY_LOCK, "proxy injections", stage, action);
    }

    public static <E extends Exception> void subscriberInjection(String stage, ThrowingRunnable<E> action) throws E {
        run(SUBSCRIBER_LOCK, "automatic subscriber injections", stage, action);
    }

    public static <E extends Exception> void subscriberRegistration(String stage, ThrowingRunnable<E> action) throws E {
        run(SUBSCRIBER_LOCK, "automatic subscriber registrations", stage, action);
    }

    public static <E extends Exception> void configSync(String stage, ThrowingRunnable<E> action) throws E {
        run(CONFIG_LOCK, "config sync", stage, action);
    }

    public static <E extends Exception> void annotationProcessing(String stage, ThrowingRunnable<E> action) throws E {
        run(ANNOTATION_LOCK, "annotation field processing", stage, action);
    }

    private static <E extends Exception> void run(Object lock, String group, String stage, ThrowingRunnable<E> action) throws E {
        logGroup(group);
        long startedAt = System.nanoTime();
        synchronized (lock) {
            long waitedMillis = (System.nanoTime() - startedAt) / 1_000_000L;
            if (waitedMillis >= 250L) {
                GPOM.LOGGER.info(
                        "[FmlConstructionSafety] Waited {} ms for serialized {} at {}",
                        waitedMillis,
                        group,
                        stage
                );
            }
            action.run();
        }
    }

    private static void logGroup(String group) {
        if (LOGGED_GROUPS.add(group)) {
            GPOM.LOGGER.info("[FmlConstructionSafety] Serializing threaded construction {}", group);
        }
    }

    @FunctionalInterface
    public interface ThrowingRunnable<E extends Exception> {
        void run() throws E;
    }
}
