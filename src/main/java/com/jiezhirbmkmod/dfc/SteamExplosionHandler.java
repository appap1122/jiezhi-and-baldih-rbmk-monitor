package com.jiezhirbmkmod.dfc;

import com.jiezhirbmkmod.RbmkMonitorMod;
import com.jiezhirbmkmod.Tags;
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
 * v18.2 熔毁蒸气爆炸景观（对齐 HBM 式名场面）：
 *
 * <ol>
 *   <li><b>蒸气云</b>：堆芯顶部喷出大量白色蒸汽；</li>
 *   <li><b>顶盖飞天</b>：控制台上方三格的方块被整块掀飞；</li>
 *   <li><b>延迟核爆</b>：主坑由 20.0F 原版爆炸炸出；</li>
 *   <li><b>弹坑阵</b>：主坑之外散布数十个小弹坑（1~2 格深浅不一）；</li>
 *   <li><b>地表燃烧</b>：爆炸范围内实体表面上随机起火；</li>
 *   <li><b>喷泉碎片雨</b>：核爆后数秒内持续喷出大量碎片方块，冲天而起
 *       再像雨一样砸回地面——落地一部分消失（砸散）、一部分留成方块；</li>
 *   <li><b>持续黑烟</b>：爆心冒出上百拍的黑烟柱，远看就是一朵蘑菇云。</li>
 * </ol>
 *
 * <p>主线程安全：延迟爆炸、碎片喷泉、黑烟柱全部挂在每服务器拍推进的
 * 队列上，{@link #trigger} 本身不做任何重活。实体总量有硬上限，
 * 反复熔毁不会把世界塞满掉落方块实体。</p>
 */
@Mod.EventBusSubscriber(modid = Tags.MODID)
public final class SteamExplosionHandler {

    private static final int STEAM_PARTICLES = 240;
    /** 碎片实体全局预算（周围 32 格内的 EntityFlyingBlock 上限） */
    private static final int DEBRIS_ENTITY_CAP = 260;
    /** 待爆队列上限 */
    private static final int MAX_PENDING = 256;

    private static final List<PendingExplosion> PENDING = new ArrayList<PendingExplosion>();
    /** v18.2 碎片喷泉队列：每拍喷一批，直到配额用完 */
    private static final List<DebrisWave> WAVES = new ArrayList<DebrisWave>();
    /** v18.2 黑烟柱队列：每拍冒一批黑烟，持续上百拍 */
    private static final List<SmokePlume> PLUMES = new ArrayList<SmokePlume>();

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
        // ---- 延迟核爆 ----
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
        if (ready != null) {
            for (PendingExplosion p : ready) {
                detonate(p);
            }
        }
        // ---- v18.2 碎片喷泉（世界引用弱持有：世界卸载就作废）----
        synchronized (WAVES) {
            Iterator<DebrisWave> it = WAVES.iterator();
            while (it.hasNext()) {
                DebrisWave w = it.next();
                if (w.world == null || w.world.isRemote || !w.world.isBlockLoaded(w.pos)) {
                    it.remove();
                    continue;
                }
                if (--w.ticksLeft <= 0 || w.remaining <= 0) {
                    it.remove();
                    continue;
                }
                w.emit();
            }
        }
        // ---- v18.2 黑烟柱 ----
        synchronized (PLUMES) {
            Iterator<SmokePlume> it = PLUMES.iterator();
            while (it.hasNext()) {
                SmokePlume s = it.next();
                if (s.world == null || s.world.isRemote || !s.world.isBlockLoaded(s.pos) || --s.ticksLeft <= 0) {
                    it.remove();
                    continue;
                }
                s.emit();
            }
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
            // 主坑：原版大爆炸
            world.newExplosion(null,
                    (double) p.pos.getX() + 0.5D,
                    (double) p.pos.getY() + 0.5D,
                    (double) p.pos.getZ() + 0.5D,
                    20.0F, true, true);
            // v18.2 景观四件套
            digCraterField(world, p.pos);
            lightSurfaceFires(world, p.pos);
            startDebrisFountain(world, p.pos);
            startSmokePlume(world, p.pos);
            playRumble(world, p.pos);
            shakeNearbyPlayers(world, p.pos);
        }
        catch (Throwable t) {
            RbmkMonitorMod.warn("Delayed steam explosion failed at {}: {}", p.pos, t.toString());
        }
    }

    // ==================== v18.2 弹坑阵 ====================

    /**
     * 主坑之外散布数十个小弹坑：每个坑 1~2 格半径、1~2 格深，坑沿偶尔
     * 留火。基岩绝不挖。尝试次数是坑数的 6 倍，选点太密（离主坑中心
     * 或别的坑太近）就跳过，保证稀疏散布的观感。
     */
    private static void digCraterField(World world, BlockPos center) {
        int count = RbmkConfig.explosionCraterCount;
        int radius = RbmkConfig.explosionCraterRadius;
        if (count <= 0 || radius < 2) {
            return;
        }
        List<BlockPos> dug = new ArrayList<BlockPos>();
        for (int attempt = 0; attempt < count * 6 && dug.size() < count; ++attempt) {
            double ang = world.rand.nextDouble() * Math.PI * 2.0D;
            double dist = 5.0D + world.rand.nextDouble() * (radius - 5.0D);
            int dx = (int) Math.round(Math.cos(ang) * dist);
            int dz = (int) Math.round(Math.sin(ang) * dist);
            BlockPos surface = world.getTopSolidOrLiquidBlock(center.add(dx, 0, dz));
            if (surface == null || surface.getY() <= 0 || surface.getY() >= 255) {
                continue;
            }
            boolean tooClose = false;
            for (BlockPos d : dug) {
                if (d.distanceSq(surface) < 16.0D) {
                    tooClose = true;
                    break;
                }
            }
            if (tooClose) {
                continue;
            }
            int cr = 1 + world.rand.nextInt(2);
            int depth = 1 + world.rand.nextInt(2);
            boolean any = false;
            for (int ox = -cr; ox <= cr; ++ox) {
                for (int oz = -cr; oz <= cr; ++oz) {
                    if (ox * ox + oz * oz > cr * cr + 1) {
                        continue;
                    }
                    for (int oy = 0; oy < depth; ++oy) {
                        BlockPos t = surface.down(oy).add(ox, 0, oz);
                        IBlockState st = world.getBlockState(t);
                        if (st.getBlock() == Blocks.BEDROCK || st.getBlock() == Blocks.AIR) {
                            continue;
                        }
                        world.setBlockToAir(t);
                        any = true;
                    }
                }
            }
            if (any) {
                dug.add(surface.toImmutable());
                // 坑沿余火
                if (world.rand.nextInt(4) == 0) {
                    BlockPos rim = surface.up();
                    if (world.isAirBlock(rim) && world.getBlockState(surface).getBlock().isFullCube(world.getBlockState(surface))) {
                        world.setBlockState(rim, Blocks.FIRE.getDefaultState());
                    }
                }
            }
        }
    }

    // ==================== v18.2 地表燃烧 ====================

    private static void lightSurfaceFires(World world, BlockPos center) {
        int count = RbmkConfig.explosionFireCount;
        int radius = Math.min(RbmkConfig.explosionCraterRadius + 4, 24);
        for (int attempt = 0; attempt < count * 5 && count > 0; ++attempt) {
            int dx = world.rand.nextInt(radius * 2 + 1) - radius;
            int dz = world.rand.nextInt(radius * 2 + 1) - radius;
            if (dx * dx + dz * dz > radius * radius) {
                continue;
            }
            BlockPos surface = world.getTopSolidOrLiquidBlock(center.add(dx, 0, dz));
            if (surface == null || surface.getY() <= 0 || surface.getY() >= 255) {
                continue;
            }
            BlockPos above = surface.up();
            if (!world.isAirBlock(above)) {
                continue;
            }
            IBlockState ground = world.getBlockState(surface);
            if (!ground.getBlock().isFullCube(ground) || ground.getBlock() == Blocks.BEDROCK) {
                continue;
            }
            world.setBlockState(above, Blocks.FIRE.getDefaultState());
            if (--count <= 0) {
                return;
            }
        }
    }

    // ==================== v18.2 喷泉碎片雨 ====================

    /**
     * 碎片不再一次全砸下来：排进队列，每拍从爆心喷 4~7 块，喷满
     * {@code explosionDebrisCount} 块或喷满秒数为止。冲天初速
     * 1.8~4.6，水平散布宽 —— 就是截图里那种喷泉式升空再落成碎片雨。
     */
    private static void startDebrisFountain(World world, BlockPos pos) {
        int total = RbmkConfig.explosionDebrisCount;
        if (total <= 0) {
            return;
        }
        int seconds = Math.max(1, RbmkConfig.explosionDebrisSeconds);
        DebrisWave wave = new DebrisWave(world, pos.toImmutable(), seconds * 20, total);
        synchronized (WAVES) {
            WAVES.add(wave);
        }
        // 首拍直接喷一波，保证爆炸瞬间就有碎片上天
        wave.emit();
    }

    private static void emitDebrisBatch(DebrisWave w) {
        World world = w.world;
        // 实体预算：周围 32 格已有的飞行方块 + 本波剩余，绝不超过上限
        int existing;
        try {
            existing = world.getEntitiesWithinAABB(EntityFlyingBlock.class,
                    new AxisAlignedBB(w.pos).grow(32.0D)).size();
        }
        catch (Throwable t) {
            return;
        }
        int budget = DEBRIS_ENTITY_CAP - existing;
        if (budget <= 0) {
            return;
        }
        int batch = Math.min(budget, Math.min(w.remaining, 4 + world.rand.nextInt(4)));
        double cx = (double) w.pos.getX() + 0.5D;
        double cy = (double) w.pos.getY() + 1.0D;
        double cz = (double) w.pos.getZ() + 0.5D;
        for (int i = 0; i < batch; ++i) {
            IBlockState state = sampleTerrainBlock(world, w.pos);
            EntityFlyingBlock debris = new EntityFlyingBlock(world,
                    cx + (world.rand.nextDouble() - 0.5D) * 4.0D,
                    cy + world.rand.nextDouble() * 2.5D,
                    cz + (world.rand.nextDouble() - 0.5D) * 4.0D,
                    state);
            // v18.2 一部分碎片落地即散（砸散了），一部分落成方块
            debris.setVanishOnLand(world.rand.nextInt(100) < RbmkConfig.explosionVanishChance);
            // 喷泉式冲天初速，比旧版高得多
            debris.motionY = 1.8D + world.rand.nextDouble() * 2.8D;
            debris.motionX = (world.rand.nextDouble() - 0.5D) * 4.4D;
            debris.motionZ = (world.rand.nextDouble() - 0.5D) * 4.4D;
            world.spawnEntity(debris);
        }
        w.remaining -= batch;
        // 喷口粒子：每次喷发伴随爆炸烟与火花
        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            for (int i = 0; i < 6; ++i) {
                ws.spawnParticle(EnumParticleTypes.EXPLOSION_NORMAL,
                        cx + (world.rand.nextDouble() - 0.5D) * 5.0D,
                        cy + world.rand.nextDouble() * 2.0D,
                        cz + (world.rand.nextDouble() - 0.5D) * 5.0D,
                        0.0D, 0.25D, 0.0D);
            }
            for (int i = 0; i < 8; ++i) {
                ws.spawnParticle(EnumParticleTypes.LAVA,
                        cx + (world.rand.nextDouble() - 0.5D) * 4.0D,
                        cy + world.rand.nextDouble() * 1.5D,
                        cz + (world.rand.nextDouble() - 0.5D) * 4.0D,
                        0.0D, 0.0D, 0.0D);
            }
        }
    }

    /** 从爆心附近地表抓一块真实地形方块当碎片（抓不到就退回砖块/圆石） */
    private static IBlockState sampleTerrainBlock(World world, BlockPos center) {
        for (int attempt = 0; attempt < 6; ++attempt) {
            BlockPos sample = center.add(world.rand.nextInt(21) - 10,
                    world.rand.nextInt(5) - 2, world.rand.nextInt(21) - 10);
            if (!world.isBlockLoaded(sample)) {
                continue;
            }
            IBlockState st = world.getBlockState(sample);
            if (st.getBlock() != Blocks.AIR && st.getBlock() != Blocks.BEDROCK) {
                return st;
            }
        }
        return world.rand.nextBoolean() ? Blocks.NETHER_BRICK.getDefaultState()
                : Blocks.COBBLESTONE.getDefaultState();
    }

    private static final class DebrisWave {
        final World world;
        final BlockPos pos;
        int ticksLeft;
        int remaining;

        DebrisWave(World world, BlockPos pos, int ticksLeft, int remaining) {
            this.world = world;
            this.pos = pos;
            this.ticksLeft = ticksLeft;
            this.remaining = remaining;
        }

        void emit() {
            SteamExplosionHandler.emitDebrisBatch(this);
        }
    }

    // ==================== v18.2 黑烟柱（蘑菇云） ====================

    private static void startSmokePlume(World world, BlockPos pos) {
        synchronized (PLUMES) {
            PLUMES.add(new SmokePlume(world, pos.toImmutable(), 120));
        }
    }

    private static final class SmokePlume {
        final World world;
        final BlockPos pos;
        int ticksLeft;

        SmokePlume(World world, BlockPos pos, int ticksLeft) {
            this.world = world;
            this.pos = pos;
            this.ticksLeft = ticksLeft;
        }

        void emit() {
            if (!(this.world instanceof WorldServer)) {
                return;
            }
            WorldServer ws = (WorldServer) this.world;
            double cx = (double) this.pos.getX() + 0.5D;
            double cz = (double) this.pos.getZ() + 0.5D;
            double baseY = (double) this.pos.getY() + 1.0D;
            // 柱身：粗黑烟一路往上卷
            for (int i = 0; i < 14; ++i) {
                double h = this.world.rand.nextDouble() * 18.0D;
                double drift = h * 0.10D;
                ws.spawnParticle(EnumParticleTypes.SMOKE_LARGE,
                        cx + (this.world.rand.nextDouble() - 0.5D) * (2.0D + drift),
                        baseY + h,
                        cz + (this.world.rand.nextDouble() - 0.5D) * (2.0D + drift),
                        (this.world.rand.nextDouble() - 0.5D) * 0.05D,
                        0.06D + this.world.rand.nextDouble() * 0.08D,
                        (this.world.rand.nextDouble() - 0.5D) * 0.05D);
            }
            // 云盖：顶端一团更大的黑烟往外翻卷
            if (this.ticksLeft > 40) {
                for (int i = 0; i < 10; ++i) {
                    double ang = this.world.rand.nextDouble() * Math.PI * 2.0D;
                    double r = 2.0D + this.world.rand.nextDouble() * 6.0D;
                    ws.spawnParticle(EnumParticleTypes.SMOKE_LARGE,
                            cx + Math.cos(ang) * r, baseY + 18.0D + this.world.rand.nextDouble() * 4.0D,
                            cz + Math.sin(ang) * r,
                            Math.cos(ang) * 0.12D, 0.02D, Math.sin(ang) * 0.12D);
                }
            }
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
