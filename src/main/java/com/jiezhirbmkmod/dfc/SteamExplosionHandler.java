package com.jiezhirbmkmod.dfc;

import com.jiezhirbmkmod.RbmkMonitorMod;
import com.jiezhirbmkmod.entity.EntityFlyingBlock;
import com.jiezhirbmkmod.network.ModNetwork;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Meltdown special effects: a steam cloud, an optional flying reactor cap and
 * an optional delayed 20.0F nuclear explosion.
 *
 * <p>Nothing here blocks the server thread: the delayed explosion is kept in a
 * small queue that is advanced once per server tick.</p>
 */
@Mod.EventBusSubscriber(modid = RbmkMonitorMod.MODID)
public final class SteamExplosionHandler {

    private static final int STEAM_PARTICLES = 240;
    private static final List<PendingExplosion> PENDING = new ArrayList<PendingExplosion>();
    private static final int MAX_PENDING = 256;

    private SteamExplosionHandler() {
    }

    public static void trigger(World world, BlockPos pos) {
        if (world == null || pos == null || world.isRemote) {
            return;
        }
        final BlockPos origin = pos.toImmutable();

        // 1) steam cloud around the source
        spawnSteam(world, origin);

        // 2) blow the cap off three blocks above the screen/console
        if (RbmkConfig.spawnFlyingCap) {
            launchCap(world, origin.up(3));
        }

        // 3) schedule the delayed nuclear explosion (never on the main thread)
        if (RbmkConfig.enableSteamExplosion) {
            int ticks = Math.max(1, RbmkConfig.steamExplosionDelaySec) * 20;
            synchronized (PENDING) {
                if (PENDING.size() < MAX_PENDING) {
                    PENDING.add(new PendingExplosion(world, origin, ticks));
                }
            }
        }
    }

    private static void spawnSteam(World world, BlockPos pos) {
        double cx = (double) pos.getX() + 0.5D;
        double cy = (double) pos.getY() + 0.5D;
        double cz = (double) pos.getZ() + 0.5D;
        for (int i = 0; i < STEAM_PARTICLES; ++i) {
            double ox = (world.rand.nextDouble() - 0.5D) * 6.0D;
            double oy = world.rand.nextDouble() * 4.0D;
            double oz = (world.rand.nextDouble() - 0.5D) * 6.0D;
            double vx = (world.rand.nextDouble() - 0.5D) * 0.12D;
            double vy = 0.05D + world.rand.nextDouble() * 0.15D;
            double vz = (world.rand.nextDouble() - 0.5D) * 0.12D;
            if (world instanceof WorldServer) {
                ((WorldServer) world).spawnParticle(EnumParticleTypes.CLOUD,
                        cx + ox, cy + oy, cz + oz, vx, vy, vz);
            } else {
                world.spawnParticle(EnumParticleTypes.CLOUD,
                        cx + ox, cy + oy, cz + oz, vx, vy, vz);
            }
        }
    }

    private static void launchCap(World world, BlockPos capPos) {
        if (capPos.getY() < 0 || capPos.getY() >= 256) {
            return;
        }
        if (!world.isBlockLoaded(capPos)) {
            return;
        }
        IBlockState state = world.getBlockState(capPos);
        if (state.getBlock() == Blocks.AIR) {
            return;
        }
        world.setBlockToAir(capPos);
        EntityFlyingBlock cap = new EntityFlyingBlock(world,
                (double) capPos.getX() + 0.5D, (double) capPos.getY(), (double) capPos.getZ() + 0.5D, state);
        cap.motionY = 1.5D;
        cap.motionX = (world.rand.nextDouble() - 0.5D) * 0.3D;
        cap.motionZ = (world.rand.nextDouble() - 0.5D) * 0.3D;
        world.spawnEntity(cap);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        List<PendingExplosion> ready = null;
        synchronized (PENDING) {
            Iterator<PendingExplosion> it = PENDING.iterator();
            while (it.hasNext()) {
                PendingExplosion p = it.next();
                if (--p.ticksLeft > 0) {
                    continue;
                }
                it.remove();
                if (ready == null) {
                    ready = new ArrayList<PendingExplosion>();
                }
                ready.add(p);
            }
        }
        if (ready == null) {
            return;
        }
        for (PendingExplosion p : ready) {
            detonate(p);
        }
    }

    private static void detonate(PendingExplosion p) {
        World world = p.world;
        if (world == null || world.provider == null || world.isRemote) {
            return;
        }
        if (!world.isBlockLoaded(p.pos)) {
            return;
        }
        try {
            world.newExplosion(null,
                    (double) p.pos.getX() + 0.5D,
                    (double) p.pos.getY() + 0.5D,
                    (double) p.pos.getZ() + 0.5D,
                    20.0F, true, true);
            spawnDebris(world, p.pos);
            playRumble(world, p.pos);
            shakeNearbyPlayers(world, p.pos);
        }
        catch (Throwable t) {
            RbmkMonitorMod.warn("Delayed steam explosion failed at {}: {}", p.pos, t.toString());
        }
    }

    private static final class PendingExplosion {
        final World world;
        final BlockPos pos;
        int ticksLeft;

        PendingExplosion(World world, BlockPos pos, int ticksLeft) {
            this.world = world;
            this.pos = pos;
            this.ticksLeft = ticksLeft;
        }
    }

    /** 30-50 debris blocks thrown out with exaggerated velocities. */
    private static void spawnDebris(World world, BlockPos pos) {
        int existing = 0;
        try {
            existing = world.getEntitiesWithinAABB(EntityFlyingBlock.class,
                    new AxisAlignedBB(pos).grow(24.0D)).size();
        }
        catch (Throwable ignored) {
            return;
        }
        // hard cap: never let repeated explosions flood the world with entities
        int budget = 120 - existing;
        if (budget <= 0) {
            return;
        }
        int count = Math.min(budget, 30 + world.rand.nextInt(21));
        IBlockState fallback = Blocks.NETHER_BRICK.getDefaultState();
        for (int i = 0; i < count; ++i) {
            BlockPos sample = pos.add(world.rand.nextInt(9) - 4, world.rand.nextInt(5) - 2,
                    world.rand.nextInt(9) - 4);
            IBlockState state = world.getBlockState(sample);
            if (state.getBlock() == Blocks.AIR) {
                state = fallback;
            }
            EntityFlyingBlock debris = new EntityFlyingBlock(world,
                    (double) pos.getX() + 0.5D + (world.rand.nextDouble() - 0.5D) * 3.0D,
                    (double) pos.getY() + 0.5D + world.rand.nextDouble() * 2.0D,
                    (double) pos.getZ() + 0.5D + (world.rand.nextDouble() - 0.5D) * 3.0D,
                    state);
            debris.motionX = (world.rand.nextDouble() - 0.5D) * 3.2D;
            debris.motionY = 1.2D + world.rand.nextDouble() * 3.0D;
            debris.motionZ = (world.rand.nextDouble() - 0.5D) * 3.2D;
            world.spawnEntity(debris);
        }
    }

    /** Extra low-frequency rumble on top of the normal explosion sound. */
    private static void playRumble(World world, BlockPos pos) {
        world.playSound(null, pos, SoundEvents.ENTITY_ENDERDRAGON_GROWL,
                SoundCategory.HOSTILE, 4.0F, 0.45F);
    }

    /** Send the camera-shake packet to every player within 50 blocks. */
    private static void shakeNearbyPlayers(World world, BlockPos pos) {
        AxisAlignedBB box = new AxisAlignedBB(pos).grow(50.0D);
        List<EntityPlayerMP> players;
        try {
            players = world.getEntitiesWithinAABB(EntityPlayerMP.class, box);
        }
        catch (Throwable t) {
            return;
        }
        for (EntityPlayerMP player : players) {
            if (player == null) {
                continue;
            }
            ModNetwork.CHANNEL.sendTo(new ShakeMessage(2.2F, 40), player);
        }
    }

    /** Server -> client camera shake packet. */
    public static class ShakeMessage
    implements IMessage {
        private float strength;
        private int duration;

        public ShakeMessage() {
        }

        public ShakeMessage(float strength, int duration) {
            this.strength = strength;
            this.duration = duration;
        }

        public void fromBytes(ByteBuf buf) {
            this.strength = buf.readFloat();
            this.duration = buf.readInt();
        }

        public void toBytes(ByteBuf buf) {
            buf.writeFloat(this.strength);
            buf.writeInt(this.duration);
        }

        public static class Handler
        implements IMessageHandler<ShakeMessage, IMessage> {
            public IMessage onMessage(ShakeMessage msg, MessageContext ctx) {
                try {
                    if (msg == null || msg.strength <= 0.0F || msg.duration <= 0) {
                        return null;
                    }
                    net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(
                            () -> com.jiezhirbmkmod.client.gui.ClientShake.shake(msg.strength, msg.duration));
                }
                catch (Throwable ignored) {
                    // never let a shake packet break the client network thread
                }
                return null;
            }
        }
    }
}
