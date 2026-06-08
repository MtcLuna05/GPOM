package com.l.gpom.compat.multipart.ae2;

import appeng.api.AEApi;
import appeng.api.util.AECableType;
import appeng.block.networking.BlockCableBus;
import appeng.client.render.cablebus.CableBusRenderState;
import appeng.parts.CableBusContainer;
import codechicken.lib.render.CCRenderState;
import codechicken.lib.vec.Vector3;
import com.l.gpom.GPOM;
import com.l.gpom.config.GpomEarlyConfig;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockModelRenderer;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.property.IExtendedBlockState;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

final class Ae2CableBusMultipartRenderer {
    private static final long MODEL_SEED = 0L;
    private static final AtomicInteger FAIL_LOGS_REMAINING = new AtomicInteger(6);
    private static final Method MINECRAFT_INSTANCE = findMethod(Minecraft.class, new Class<?>[0], "getMinecraft", "func_71410_x");
    private static final Method MINECRAFT_BLOCK_RENDERER = findMethod(Minecraft.class, new Class<?>[0], "getBlockRendererDispatcher", "func_175602_ab");
    private static final Method DISPATCHER_MODEL_RENDERER = findMethod(BlockRendererDispatcher.class, new Class<?>[0], "getBlockModelRenderer", "func_175019_b");
    private static final Method DISPATCHER_MODEL_FOR_STATE = findMethod(BlockRendererDispatcher.class, new Class<?>[] {IBlockState.class}, "getModelForState", "func_184389_a");
    private static final Method BLOCK_DEFAULT_STATE = findMethod(Block.class, new Class<?>[0], "getDefaultState", "func_176223_P");
    private static final Method MODEL_RENDER = findMethod(
            BlockModelRenderer.class,
            new Class<?>[] {net.minecraft.world.IBlockAccess.class, IBakedModel.class, IBlockState.class, BlockPos.class, BufferBuilder.class, boolean.class, long.class},
            "renderModel",
            "func_187493_a"
    );

    private Ae2CableBusMultipartRenderer() {
    }

    static boolean render(Ae2CableBusMultipart host, CableBusContainer cableBus, World world, BlockPos pos, Vector3 position, CCRenderState renderState) {
        if (host == null || cableBus == null || world == null || pos == null || renderState == null || renderState.getBuffer() == null) {
            return false;
        }

        try {
            Block cableBlock = ae2CableBusBlock();
            if (cableBlock == null) {
                return false;
            }

            IBlockState state = defaultState(cableBlock);
            if (!(state instanceof IExtendedBlockState)) {
                return false;
            }

            CableBusRenderState cableState = cableBus.getRenderState();
            cableState.setWorld(world);
            cableState.setPos(pos);
            augmentHostedCableConnections(host, cableState);
            IBlockState extendedState = ((IExtendedBlockState) state).withProperty(BlockCableBus.RENDER_STATE_PROPERTY, cableState);

            Object minecraft = invoke(MINECRAFT_INSTANCE, null);
            Object dispatcher = invoke(MINECRAFT_BLOCK_RENDERER, minecraft);
            Object model = invoke(DISPATCHER_MODEL_FOR_STATE, dispatcher, state);
            Object modelRenderer = invoke(DISPATCHER_MODEL_RENDERER, dispatcher);
            if (!(model instanceof IBakedModel) || modelRenderer == null) {
                return false;
            }

            Object rendered = MODEL_RENDER.invoke(
                    modelRenderer,
                    world,
                    model,
                    extendedState,
                    pos,
                    renderState.getBuffer(),
                    false,
                    MODEL_SEED
            );
            return Boolean.TRUE.equals(rendered);
        } catch (Throwable throwable) {
            logFailure(throwable);
            return false;
        }
    }

    private static void augmentHostedCableConnections(Ae2CableBusMultipart host, CableBusRenderState cableState) {
        AECableType localType = validCableType(host.internalCableConnectionType(), cableState.getCableType());
        if (localType == null) {
            return;
        }

        for (EnumFacing side : EnumFacing.values()) {
            if (cableState.getConnectionTypes().containsKey(side) || !host.allowsExternalRenderConnection(side)) {
                continue;
            }

            EnumFacing opposite = opposite(side);
            Ae2CableBusMultipart adjacent = host.adjacentHostedCable(side);
            if (opposite == null || adjacent == null || !adjacent.allowsExternalRenderConnection(opposite)) {
                continue;
            }

            AECableType adjacentType = validCableType(adjacent.internalCableConnectionType(), localType);
            AECableType connectionType = AECableType.min(localType, adjacentType);
            if (connectionType != null && connectionType != AECableType.NONE) {
                cableState.getConnectionTypes().put(side, connectionType);
                cableState.getCableBusAdjacent().add(side);
            }
        }
    }

    private static AECableType validCableType(AECableType preferred, AECableType fallback) {
        if (preferred != null && preferred != AECableType.NONE) {
            return preferred;
        }
        return fallback == AECableType.NONE ? null : fallback;
    }

    private static EnumFacing opposite(EnumFacing side) {
        if (side == null) {
            return null;
        }
        switch (side) {
            case DOWN:
                return EnumFacing.UP;
            case UP:
                return EnumFacing.DOWN;
            case NORTH:
                return EnumFacing.SOUTH;
            case SOUTH:
                return EnumFacing.NORTH;
            case WEST:
                return EnumFacing.EAST;
            case EAST:
            default:
                return EnumFacing.WEST;
        }
    }

    private static Block ae2CableBusBlock() {
        Optional<Block> block = AEApi.instance().definitions().blocks().multiPart().maybeBlock();
        return block.orElse(null);
    }

    private static IBlockState defaultState(Block block) throws Exception {
        Object value = invoke(BLOCK_DEFAULT_STATE, block);
        return value instanceof IBlockState ? (IBlockState) value : null;
    }

    private static Object invoke(Method method, Object target, Object... args) throws Exception {
        if (method == null) {
            return null;
        }
        return method.invoke(target, args);
    }

    private static Method findMethod(Class<?> type, Class<?>[] parameterTypes, String... names) {
        for (String name : names) {
            try {
                Method method = type.getMethod(name, parameterTypes);
                method.setAccessible(true);
                return method;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static void logFailure(Throwable throwable) {
        if (GpomEarlyConfig.multipartCompatAe2DebugLogsEnabled() && FAIL_LOGS_REMAINING.getAndDecrement() > 0) {
            GPOM.LOGGER.warn("[GPOM Multipart] AE2 hosted cable render bridge failed", throwable);
        }
    }
}
