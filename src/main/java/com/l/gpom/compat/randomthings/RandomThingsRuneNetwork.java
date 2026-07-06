package com.l.gpom.compat.randomthings;

import com.l.gpom.Reference;
import com.l.gpom.compat.minecraft.MinecraftMappingCompat;
import com.l.gpom.config.GpomEarlyConfig;
import com.l.gpom.util.GpomRemoteEnvironment;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

import java.util.UUID;

public final class RandomThingsRuneNetwork {
    private static final SimpleNetworkWrapper NETWORK = NetworkRegistry.INSTANCE.newSimpleChannel(Reference.MOD_ID + "_runes");
    private static boolean registered;

    private RandomThingsRuneNetwork() {
    }

    public static void registerIfNeeded() {
        if (registered || !GpomEarlyConfig.randomThingsImprovedRunicDustEnabled() || !Loader.isModLoaded("randomthings")) {
            return;
        }
        registered = true;
        NETWORK.registerMessage(SettingsHandler.class, SettingsMessage.class, 0, Side.SERVER);
    }

    public static void sendClientSettingsToServer() {
        if (!registered || !GpomRemoteEnvironment.serverFeaturesAllowed()) {
            return;
        }
        RandomThingsRuneSettings.RuneSettings[] settings = RandomThingsRuneSettings.clientSnapshot();
        for (int rune = 0; rune < settings.length; rune++) {
            NETWORK.sendToServer(new SettingsMessage(rune, settings[rune]));
        }
    }

    public static void sendClientSettingToServer(int rune, RandomThingsRuneSettings.RuneSettings settings) {
        if (!registered || !GpomRemoteEnvironment.serverFeaturesAllowed()) {
            return;
        }
        NETWORK.sendToServer(new SettingsMessage(rune, settings));
    }

    public static final class SettingsMessage implements IMessage {
        private int rune;
        private boolean autoConnect;
        private int resolution;
        private int brush;
        private int visualScale;
        private int visualPadding;
        private boolean replaceOccupied;

        public SettingsMessage() {
        }

        SettingsMessage(int rune, RandomThingsRuneSettings.RuneSettings settings) {
            RandomThingsRuneSettings.RuneSettings normalized = RandomThingsRuneSettings.normalize(settings);
            this.rune = RandomThingsRuneSettings.clampRune(rune);
            this.autoConnect = normalized.autoConnect;
            this.resolution = normalized.resolution;
            this.brush = normalized.brush;
            this.visualScale = normalized.visualScale;
            this.visualPadding = normalized.visualPadding;
            this.replaceOccupied = normalized.replaceOccupied;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            this.rune = buf.readByte() & 255;
            this.autoConnect = buf.readBoolean();
            this.resolution = buf.readUnsignedShort();
            this.brush = buf.readByte() & 255;
            this.visualScale = buf.readByte() & 255;
            this.visualPadding = buf.readByte() & 255;
            this.replaceOccupied = buf.readBoolean();
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeByte(RandomThingsRuneSettings.clampRune(rune));
            buf.writeBoolean(autoConnect);
            buf.writeShort(RandomThingsRuneSettings.normalizeResolution(resolution));
            buf.writeByte(RandomThingsRuneSettings.clamp(brush, 1, 9));
            buf.writeByte(RandomThingsRuneSettings.clamp(visualScale, 10, 100));
            buf.writeByte(RandomThingsRuneSettings.clamp(visualPadding, 0, 45));
            buf.writeBoolean(replaceOccupied);
        }
    }

    public static final class SettingsHandler implements IMessageHandler<SettingsMessage, IMessage> {
        @Override
        public IMessage onMessage(SettingsMessage message, MessageContext context) {
            Object playerValue = context == null || context.getServerHandler() == null
                    ? null
                    : MinecraftMappingCompat.fieldValue(context.getServerHandler(), "netHandlerPlayServer.player", "field_147369_b", "player");
            EntityPlayerMP player = playerValue instanceof EntityPlayerMP ? (EntityPlayerMP) playerValue : null;
            if (player == null) {
                return null;
            }
            UUID playerId = MinecraftMappingCompat.playerUniqueId(player);
            Object world = MinecraftMappingCompat.invoke(player, "player.getServerWorld", MinecraftMappingCompat.NO_TYPES, MinecraftMappingCompat.NO_ARGS,
                    "func_71121_q", "getServerWorld");
            if (world == null) {
                world = MinecraftMappingCompat.playerWorld(player);
            }
            MinecraftMappingCompat.invoke(world, "serverWorld.addScheduledTask", new Class<?>[]{Runnable.class},
                    new Object[]{(Runnable) () -> RandomThingsRuneSettings.updateServer(playerId, message.rune,
                            new RandomThingsRuneSettings.RuneSettings(message.autoConnect, message.resolution, message.brush,
                                    message.visualScale, message.visualPadding, message.replaceOccupied))},
                    "func_152344_a", "addScheduledTask");
            return null;
        }
    }
}
