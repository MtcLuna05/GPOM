package com.l.gpom.optimization;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraft.util.BlockRenderLayer;

import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ModelLogSpamSuppressor {
    private static final Logger LOGGER = LogManager.getLogger("General Purpose Optimization Mod");
    private static final Set<String> VINTAGEFIX_UCW_NAMESPACES = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private static final Set<String> VINTAGEFIX_DYNAMIC_MODEL_NAMESPACES = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private static final Set<String> VINTAGEFIX_EARLY_MODEL_NAMESPACES = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private static final Set<String> VINTAGEFIX_INVALID_EARLY_MODEL_PATHS = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private static final Set<String> VINTAGEFIX_MISSING_TEXTURE_NAMESPACES = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private static final Set<String> CTM_UNKNOWN_RENDER_LAYERS = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private static final Set<String> CTM_TEXTURE_METADATA_ERRORS = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

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

    public static void suppressVintageFixEarlyModelLoadError(Object model, Throwable throwable) {
        logNamespaceOnce(
                VINTAGEFIX_EARLY_MODEL_NAMESPACES,
                "VintageFix early model-load error",
                model,
                "",
                throwable
        );
    }

    public static void suppressVintageFixInvalidEarlyModelPath(Object path) {
        String pathName = String.valueOf(path);
        if (VINTAGEFIX_INVALID_EARLY_MODEL_PATHS.add(pathName)) {
            LOGGER.info("[GPOM LogSpam] Suppressing VintageFix invalid early model path {}", pathName);
        }
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

    public static boolean isVintageFixSkippableEarlyModelPath(String path) {
        if (path == null) {
            return false;
        }
        String normalized = path.replace('\\', '/').toLowerCase(Locale.ROOT);
        if (normalized.startsWith("assets/unlimitedchiselworks/ucwdefs/")
                || normalized.contains("/assets/unlimitedchiselworks/ucwdefs/")
                || normalized.startsWith("unlimitedchiselworks/ucwdefs/")
                || normalized.contains("/unlimitedchiselworks/ucwdefs/")) {
            return true;
        }
        int slash = normalized.lastIndexOf('/');
        String fileName = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        boolean rootLevel = slash < 0;
        return rootLevel
                && fileName.endsWith(".json")
                && (fileName.startsWith("mixin.")
                || fileName.startsWith("mixins.")
                || fileName.contains(".mixin.")
                || fileName.contains(".mixins."));
    }

    public static boolean isVintageFixUcwDefinitionPath(String path) {
        return isVintageFixSkippableEarlyModelPath(path);
    }

    public static void suppressCtmUnknownRenderLayer(Object layer, Throwable throwable) {
        String layerName = String.valueOf(layer);
        if (CTM_UNKNOWN_RENDER_LAYERS.add(layerName)) {
            LOGGER.info(
                    "[GPOM CTM] CTM does not know render layer {}; leaving layer unset for matching texture metadata ({})",
                    layerName,
                    concise(throwable)
            );
        }
    }

    public static BlockRenderLayer ctmBlockRenderLayerValueOf(String layerName) {
        try {
            return BlockRenderLayer.valueOf(layerName);
        } catch (IllegalArgumentException exception) {
            suppressCtmUnknownRenderLayer(layerName, exception);
            return null;
        }
    }

    public static void suppressCtmTextureMetadataError(Throwable throwable) {
        String key = concise(throwable);
        if (CTM_TEXTURE_METADATA_ERRORS.add(key)) {
            LOGGER.info(
                    "[GPOM CTM] Suppressing repeated CTM texture metadata stack traces ({})",
                    key
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
