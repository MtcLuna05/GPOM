package com.l.gpom.optimization;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ModelLogSpamSuppressor {
    private static final Logger LOGGER = LogManager.getLogger("General Purpose Optimization Mod");
    private static final Set<String> VINTAGEFIX_UCW_NAMESPACES = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private static final Set<String> VINTAGEFIX_DYNAMIC_MODEL_NAMESPACES = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private static final Set<String> VINTAGEFIX_MISSING_TEXTURE_NAMESPACES = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

    private ModelLogSpamSuppressor() {
    }

    public static void suppressVintageFixUcwModelError(Object model, Throwable throwable) {
        logNamespaceOnce(
                VINTAGEFIX_UCW_NAMESPACES,
                "VintageFix UCW model error",
                model,
                "",
                throwable
        );
    }

    public static void suppressVintageFixDynamicModelError(Object model, Throwable throwable) {
        logNamespaceOnce(
                VINTAGEFIX_DYNAMIC_MODEL_NAMESPACES,
                "VintageFix dynamic model error",
                model,
                "",
                throwable
        );
    }

    public static void suppressVintageFixDynamicModelItemError(Object model, Object itemModel, Throwable throwable) {
        logNamespaceOnce(
                VINTAGEFIX_DYNAMIC_MODEL_NAMESPACES,
                "VintageFix dynamic item-model error",
                model,
                " as item " + String.valueOf(itemModel),
                throwable
        );
    }

    public static void suppressVintageFixMissingTexture(Object texture) {
        String textureName = String.valueOf(texture);
        String namespace = namespace(textureName);
        if (VINTAGEFIX_MISSING_TEXTURE_NAMESPACES.add(namespace)) {
            LOGGER.info(
                    "[GPOM LogSpam] Suppressing repeated VintageFix missing-texture warnings for namespace '{}' starting at texture {}",
                    namespace,
                    textureName
            );
        }
    }

    private static void logNamespaceOnce(Set<String> namespaces, String source, Object model, String extra, Throwable throwable) {
        String modelName = String.valueOf(model);
        String namespace = namespace(modelName);
        if (namespaces.add(namespace)) {
            LOGGER.info(
                    "[GPOM LogSpam] Suppressing repeated {} stack traces for namespace '{}' starting at model {}{} ({})",
                    source,
                    namespace,
                    modelName,
                    extra,
                    concise(throwable)
            );
        }
    }

    private static String namespace(String modelName) {
        if (modelName == null || modelName.trim().isEmpty()) {
            return "unknown";
        }
        int separator = modelName.indexOf(':');
        if (separator <= 0) {
            return "unknown";
        }
        return modelName.substring(0, separator).toLowerCase(Locale.ROOT);
    }

    private static String concise(Throwable throwable) {
        if (throwable == null) {
            return "no throwable";
        }
        String message = throwable.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return throwable.getClass().getSimpleName();
        }
        return throwable.getClass().getSimpleName() + ": " + message;
    }
}
