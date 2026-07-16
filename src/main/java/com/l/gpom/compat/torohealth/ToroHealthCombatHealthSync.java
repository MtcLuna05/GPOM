package com.l.gpom.compat.torohealth;

import com.l.gpom.GPOM;
import com.l.gpom.Reference;
import com.l.gpom.compat.minecraft.MinecraftMappingCompat;
import com.l.gpom.config.GpomEarlyConfig;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class ToroHealthCombatHealthSync {
    private static final SimpleNetworkWrapper NETWORK = NetworkRegistry.INSTANCE.newSimpleChannel(Reference.MOD_ID + "_thsync");
    private static final List<PendingSync> PENDING = new ArrayList<>();
    private static volatile boolean registered;
    private static volatile Method clientApplyMethod;

    private ToroHealthCombatHealthSync() {
    }

    public static void registerIfNeeded() {
        if (registered
                || !GpomEarlyConfig.toroHealthCombatHealthSyncEnabled()
                || !Loader.isModLoaded("torohealthmod")) {
            return;
        }
        registered = true;
        NETWORK.registerMessage(HealthHandlerClient.class, HealthMessage.class, 0, Side.CLIENT);
        MinecraftForge.EVENT_BUS.register(ToroHealthCombatHealthSync.class);
        if (GpomEarlyConfig.optimizationInfoLogsEnabled()) {
            GPOM.LOGGER.info("[GPOM ToroHealth] Registered combat health sync bridge");
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(LivingDamageEvent event) {
        if (!registered || !GpomEarlyConfig.toroHealthCombatHealthSyncEnabled() || event == null) {
            return;
        }
        EntityLivingBase target = event.getEntityLiving();
        World world = MinecraftMappingCompat.entityWorld(target);
        if (target == null || world == null || MinecraftMappingCompat.worldIsRemote(world)) {
            return;
        }
        EntityPlayerMP attacker = attackingPlayer(event.getSource());
        if (attacker == null) {
            return;
        }
        int entityId = MinecraftMappingCompat.entityId(target);
        if (entityId < 0) {
            return;
        }
        float predictedHealth = Math.max(0.0F, MinecraftMappingCompat.livingHealth(target) - Math.max(0.0F, event.getAmount()));
        float maxHealth = MinecraftMappingCompat.livingMaxHealth(target);
        synchronized (PENDING) {
            PENDING.add(new PendingSync(attacker, target, entityId, predictedHealth, maxHealth));
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (!registered || event.phase != TickEvent.Phase.END) {
            return;
        }
        synchronized (PENDING) {
            Iterator<PendingSync> iterator = PENDING.iterator();
            while (iterator.hasNext()) {
                PendingSync pending = iterator.next();
                if (--pending.delayTicks > 0) {
                    continue;
                }
                iterator.remove();
                send(pending);
            }
        }
    }

    private static EntityPlayerMP attackingPlayer(DamageSource source) {
        Entity trueSource = MinecraftMappingCompat.damageSourceTrueSource(source);
        return trueSource instanceof EntityPlayerMP ? (EntityPlayerMP) trueSource : null;
    }

    private static void send(PendingSync pending) {
        EntityPlayerMP player = pending.player;
        EntityLivingBase target = pending.target;
        if (player == null || target == null) {
            return;
        }
        float health = MinecraftMappingCompat.livingHealth(target);
        if (Float.isNaN(health)) {
            health = pending.predictedHealth;
        }
        float maxHealth = MinecraftMappingCompat.livingMaxHealth(target);
        if (maxHealth <= 0.0F || Float.isNaN(maxHealth)) {
            maxHealth = pending.maxHealth;
        }
        NETWORK.sendTo(new HealthMessage(pending.entityId, Math.max(0.0F, health), maxHealth), player);
    }

    private static void applyOnClient(HealthMessage message) {
        try {
            Method method = clientApplyMethod;
            if (method == null) {
                Class<?> type = Class.forName("com.l.gpom.compat.torohealth.ToroHealthCombatHealthClient", false,
                        ToroHealthCombatHealthSync.class.getClassLoader());
                method = type.getMethod("apply", int.class, float.class, float.class);
                method.setAccessible(true);
                clientApplyMethod = method;
            }
            method.invoke(null, message.entityId, message.health, message.maxHealth);
        } catch (Throwable ignored) {
        }
    }

    public static final class HealthMessage implements IMessage {
        private int entityId;
        private float health;
        private float maxHealth;

        public HealthMessage() {
        }

        private HealthMessage(int entityId, float health, float maxHealth) {
            this.entityId = entityId;
            this.health = health;
            this.maxHealth = maxHealth;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            this.entityId = buf.readInt();
            this.health = buf.readFloat();
            this.maxHealth = buf.readFloat();
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeInt(entityId);
            buf.writeFloat(health);
            buf.writeFloat(maxHealth);
        }
    }

    public static final class HealthHandlerClient implements IMessageHandler<HealthMessage, IMessage> {
        @Override
        public IMessage onMessage(HealthMessage message, MessageContext context) {
            if (message != null) {
                applyOnClient(message);
            }
            return null;
        }
    }

    private static final class PendingSync {
        private final EntityPlayerMP player;
        private final EntityLivingBase target;
        private final int entityId;
        private final float predictedHealth;
        private final float maxHealth;
        private int delayTicks = 1;

        private PendingSync(EntityPlayerMP player, EntityLivingBase target, int entityId, float predictedHealth, float maxHealth) {
            this.player = player;
            this.target = target;
            this.entityId = entityId;
            this.predictedHealth = predictedHealth;
            this.maxHealth = maxHealth;
        }
    }
}
