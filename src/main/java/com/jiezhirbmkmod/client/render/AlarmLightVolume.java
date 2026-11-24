package com.jiezhirbmkmod.client.render;

import com.jiezhirbmkmod.block.BlockAlarmLight;
import com.jiezhirbmkmod.block.TileAlarmLight;
import com.jiezhirbmkmod.dfc.RbmkConfig;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

/**
 * Volumetric light cones for the alarm lights.
 *
 * Drawn in RenderWorldLastEvent (world origin), not in the TESR, so the matrix
 * origin is the camera and every cone is translated by -cameraPos.
 *
 * Occlusion uses world.rayTraceBlocks() only - never isOpaqueCube() - so glass,
 * slabs, custom models and micro blocks all block the light correctly.
 *
 * v17.2 fixes:
 *  - the cull test was the view cone on the block position: a 16 block beam
 *    could be on screen while the lamp itself was off to the side, and the
 *    whole light vanished. Now only lights fully behind the camera are dropped.
 *  - cone vertices are rotated into scratch arrays; the old code allocated
 *    576 Vec3d per light per frame (GC hitching with a row of lamps).
 *  - no lightmap poke: POSITION_COLOR carries no lightmap attribute, and
 *    leaving the unit at 240/240 leaked into the next render pass.
 *
 * <h3>光影包（OptiFine shader）下的行为</h3>
 * 只有<b>贴图灯光</b>那一档留在场上（见 {@link #draws}）。几何锥体是零贴图的
 * {@code POSITION_COLOR} 几何，光影 program 里 sampler0（gtexture）照采，顶点格式
 * 又没有 UV，等于恒采图集的 (0,0) 像素 —— 画出来是半透明黑雾。贴图档用
 * {@code POSITION_TEX_COLOR} 加一张真实贴图，光影包按常规方块几何处理它，能正常采样。
 *
 * <p>这是一处取舍，不是"修好了光影兼容"：要修几何那条，得往别人的着色器管线里
 * 塞自建白贴图，那属于改别人的管线，不做。所以默认（自动 / 几何）档在光影下
 * 依然什么都不画，玩家想在有光影时看到光，得手动把那一档点成贴图 ——
 * 也就是"光影下不自动降级"。</p>
 */
@SideOnly(Side.CLIENT)
public final class AlarmLightVolume {

    private static final int AZIMUTH = 24;
    private static final int ELEVATION = 12;
    private static final int REBUILD_PER_FRAME = 1;
    private static final int CACHE_TICKS = 3;
    private static final double MAX_DISTANCE = 96.0D;
    private static final float BASE_ALPHA = 0.17F;
    /**
     * 贴图灯光沿一条束摆几块告示板 —— 光柱的"分段数"。
     * <p>6 块是观感和开销的折中：再少能看出一颗颗光球，再多就是十几块半透明面片
     * 叠着走，加法混合下容易在灯口糊成一团白。这个数是观感参数，进游戏看着不对直接改。</p>
     */
    private static final int TEXTURE_PUFFS = 6;
    /**
     * 灯罩中心相对方块中心的偏移，沿灯口法线方向（负值 = 往方块里退一点）。
     * 原值是写死的 {@code y + 6/16}，只对"立杆"成立；换成沿法线以后，立杆算出来
     * 仍是 6/16（0.5 - 0.125），而吊顶 / 挂壁自动落到灯罩该在的位置。
     */
    private static final double LENS_OFFSET = -0.125D;
    private static final float GLOW_STRENGTH = 0.30F;
    private static final ResourceLocation GLOW = new ResourceLocation(
            "jiezhirbmkmod", "textures/blocks/rbmk_alarm_light_glow.png");

    /** rotated cone points, reused every frame (client render thread only) */
    private static final float[] SCRATCH_X = new float[AZIMUTH * ELEVATION];
    private static final float[] SCRATCH_Y = new float[AZIMUTH * ELEVATION];
    private static final float[] SCRATCH_Z = new float[AZIMUTH * ELEVATION];
    private static final float[] SCRATCH_A = new float[AZIMUTH * ELEVATION];

    private static final Map<TileAlarmLight, Cache> CACHES =
            new WeakHashMap<TileAlarmLight, Cache>();
    private static List<TileAlarmLight> LIGHT_CACHE = new ArrayList<TileAlarmLight>();
    private static long LIGHT_CACHE_TICK = Long.MIN_VALUE;
    private static int LIGHT_CACHE_DIM = Integer.MIN_VALUE;

    private AlarmLightVolume() {
    }

    private static final class Cache {
        long tick = Long.MIN_VALUE;
        Vec3d[] localA;
        double[] distA;
        /** 第二条光束；立杆以外的姿态没有，保持 null。 */
        Vec3d[] localB;
        double[] distB;
        float radius;
        float cone;
        BlockPos pos;
        /** 灯口法线，渲染时绕它旋转。 */
        Vec3d axis;
        LightType type;
        int beams;

        boolean isFresh(long now, TileAlarmLight light, Vec3d wantAxis) {
            return this.localA != null && this.axis != null
                    && now >= this.tick && now - this.tick < CACHE_TICKS
                    && this.radius == Math.max(1.0F, light.getRadius())
                    && this.cone == light.getConeAngle()
                    && this.pos != null && this.pos.equals(light.getPos())
                    // 换朝向（拆了重放、或将来加了扳手旋转）必须重建，否则光锥还挂在旧方向
                    && this.axis.squareDistanceTo(wantAxis) < 1.0E-9D;
        }
    }

    @SubscribeEvent
    public static void onRenderWorldLast(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.world == null || mc.player == null) {
            return;
        }
        // ★ 光影包下这里不再整段让路，但只有"贴图灯光"那一档留在场上。
        //   几何锥体是零贴图的 POSITION_COLOR 几何，光影的 program 里 sampler0（gtexture）
        //   照采不误，而顶点格式没有 UV，等于恒采纹理的 (0,0) 像素 —— 一整片乘上那个
        //   像素颜色的半透明黑雾，比不画更显眼。贴图那条走的是最普通的带 UV 世界几何，
        //   光影包按常规方块几何处理它，能正常采样，所以它是唯一画得出来的。
        //   要判定的具体档位在 draws() 里，这里只把"有没有光影"传下去。
        final boolean shaders = AlarmLightVolume.shadersActive();
        // ★ 把探测结果发布给 common 包：「自动」档（TileAlarmLight#autoPath）按它
        //   选最优路径，服务端安全（TileAlarmLight 不能反射 client 类）。每帧一次、
        //   先于任何灯的分派；游戏内开关光影包由探测缓存约 1.6 秒内带过来。
        RbmkConfig.shaderPackActive = shaders;
        World world = mc.world;
        float partial = event.getPartialTicks();
        Vec3d cam = AlarmLightVolume.cameraPos(mc, partial);
        long now = world.getTotalWorldTime();
        int rebuilds = REBUILD_PER_FRAME;

        List<TileAlarmLight> lights = new ArrayList<TileAlarmLight>(
                AlarmLightVolume.collect(world, mc));
        // ★ 纯 CPU 粗筛：这一帧到底有没有灯要画。没有就直接返回，一次 GL 调用都不做。
        //   下面那几个 glIsEnabled / glGetInteger 会打断 GPU 批处理，虽然有灯时每帧
        //   也只需要一轮（不是每盏灯一轮），但不该在"附近根本没灯"的帧上白付这笔钱。
        //   光影下这一筛格外值钱：一组走几何的灯在光影下全被 draws() 挡掉，
        //   筛完 any 还是 false 就整帧不碰 GL。
        boolean any = false;
        for (TileAlarmLight light : lights) {
            if (light != null && !light.isInvalid() && light.isEnabled()
                    && AlarmLightVolume.draws(light, shaders)
                    && light.getPos().distanceSq(cam.x, cam.y, cam.z)
                            <= MAX_DISTANCE * MAX_DISTANCE) {
                any = true;
                break;
            }
        }
        if (!any) {
            return;
        }

        // ★ 进入前把要用到的状态全部读回来，退出时按原值还原。
        //   这里原来是「finally 里无条件设成我以为的默认值」：blend 关掉、cull 打开、
        //   纹理打开、光照打开、混合函数设回 SRC_ALPHA/ONE_MINUS_SRC_ALPHA、颜色拨白。
        //   原版世界画完刚好就是这个状态，所以看不出问题；但只要同一条事件总线上还有别的
        //   模组排在我们后面（粒子、高亮/线框类），它们设的混合方式、颜色就会被按回原版
        //   默认值 —— 表现是"走过报警灯，粒子突然变不透明、别的模组的线框变白"。
        //   注意这里只查"开关类"的状态：不切纹理单元、不绑任何纹理，纯粹读五个值。
        //   混合函数（blendFunc）不查：GL 的因子枚举值在源/目标两个语境里是复用的
        //   （770 既可能是 SRC_ALPHA 也可能是 DST_ALPHA），拿 int 反查枚举有歧义，
        //   而 GlStateManager 只提供枚举版本的 blendFunc。世界渲染阶段这一项本来就是
        //   SRC_ALPHA / ONE_MINUS_SRC_ALPHA，直接设回去即可。
        final boolean blendWasOn = GL11.glIsEnabled(GL11.GL_BLEND);
        final boolean cullWasOn = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        final boolean lightWasOn = GL11.glIsEnabled(GL11.GL_LIGHTING);
        final boolean texWasOn = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        final boolean depthMaskWas = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);

        GlStateManager.pushMatrix();
        GlStateManager.translate(-cam.x, -cam.y, -cam.z);
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE);
        GlStateManager.depthMask(false);
        GlStateManager.disableLighting();
        GlStateManager.disableTexture2D();
        GlStateManager.disableCull();
        try {
            final Vec3d camF = cam;
            Collections.sort(lights, new Comparator<TileAlarmLight>() {
                @Override
                public int compare(TileAlarmLight a, TileAlarmLight b) {
                    double da = a.getPos().distanceSq(camF.x, camF.y, camF.z);
                    double db = b.getPos().distanceSq(camF.x, camF.y, camF.z);
                    return Double.compare(da, db);
                }
            });
            for (TileAlarmLight light : lights) {
                if (light == null || light.isInvalid() || !light.isEnabled()) {
                    continue;
                }
                // ★ 每盏灯单独兜底：任何异常都不许沿事件总线冒上去 —— Forge 对
                //   渲染事件异常一律判 FATAL，客户端当场黑屏退到桌面/黑屏界面。
                //   光影包在场时尤其容易触发：几何状态被别人的 program 接管，
                //   Tessellator / rayTrace / 贴图加载都可能出幺蛾子（v4.11.0 的
                //   BufferUnderflowException 就是这么炸的，DepthDebugPass 因此
                //   早就包了兜底，这里一直漏着）。异常只记一次；catch 里先复位
                //   可能停在 begin/draw 之间的共享 BufferBuilder，否则下一帧
                //   任何 begin() 都会 IllegalStateException，一盏灯的错变成
                //   整帧的错。
                try {
                    // ★ 三条路径互斥，判定只有一处（TileAlarmLight#renderPath）：
                    //   SSS 由 DepthDebugPass 自己画、几何与贴图归这里，同一盏灯绝不会被
                    //   两条路径同时画；光影下非贴图的档位也在这里被挡掉。
                    if (!AlarmLightVolume.draws(light, shaders)) {
                        continue;
                    }
                    BlockPos pos = light.getPos();
                    double distSq = pos.distanceSq(cam.x, cam.y, cam.z);
                    if (distSq > MAX_DISTANCE * MAX_DISTANCE) {
                        continue;
                    }
                    if (!AlarmLightVolume.visible(mc, light, cam)) {
                        continue;
                    }
                    Vec3d axis = AlarmLightVolume.axisOf(light);
                    Cache cache = CACHES.get(light);
                    if (cache == null || !cache.isFresh(now, light, axis)) {
                        if (rebuilds > 0) {
                            cache = AlarmLightVolume.rebuild(world, light, now, axis);
                            --rebuilds;
                        }
                        else if (cache == null) {
                            continue;
                        }
                    }
                    // localB 只在立杆姿态下存在，不能再拿它当"缓存有效"的判据
                    if (cache == null || cache.localA == null) {
                        continue;
                    }
                    AlarmLightVolume.renderLight(mc, light, world, partial, cache);
                }
                catch (Throwable t) {
                    AlarmLightVolume.recoverAfterRenderError(t);
                }
            }
        }
        finally {
            // 逆序还原，一步都不省 —— try 里的 continue / early return 都绕不过这里。
            // 全部走 GlStateManager：它自己带状态缓存，用裸 glEnable 改会让缓存与实际相反，
            // 后面原版调 disableCull() 时会被缓存挡掉、GL 状态就永远回不来了。
            if (cullWasOn) {
                GlStateManager.enableCull();
            } else {
                GlStateManager.disableCull();
            }
            if (lightWasOn) {
                GlStateManager.enableLighting();
            } else {
                GlStateManager.disableLighting();
            }
            if (texWasOn) {
                GlStateManager.enableTexture2D();
            } else {
                GlStateManager.disableTexture2D();
            }
            GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            if (blendWasOn) {
                GlStateManager.enableBlend();
            } else {
                GlStateManager.disableBlend();
            }
            GlStateManager.depthMask(depthMaskWas);
            // 顶点色画完必须拨回白色：后面的方块/实体渲染会继承当前颜色
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.popMatrix();
        }
    }

    /** cached alarm-light list around the player (chunk scan every 10 ticks) */
    private static List<TileAlarmLight> collect(World world, Minecraft mc) {
        long tick = world.getTotalWorldTime();
        int dim = world.provider.getDimension();
        if (LIGHT_CACHE_DIM == dim && LIGHT_CACHE_TICK != Long.MIN_VALUE
                && tick - LIGHT_CACHE_TICK < 10L && tick >= LIGHT_CACHE_TICK) {
            return LIGHT_CACHE;
        }
        BlockPos center = mc.player.getPosition();
        int chunkRange = (int) Math.ceil(MAX_DISTANCE / 16.0D);
        int cx0 = (center.getX() >> 4) - chunkRange;
        int cx1 = (center.getX() >> 4) + chunkRange;
        int cz0 = (center.getZ() >> 4) - chunkRange;
        int cz1 = (center.getZ() >> 4) + chunkRange;
        List<TileAlarmLight> found = new ArrayList<TileAlarmLight>();
        for (int cx = cx0; cx <= cx1; ++cx) {
            for (int cz = cz0; cz <= cz1; ++cz) {
                Chunk chunk;
                try {
                    chunk = world.getChunkProvider().getLoadedChunk(cx, cz);
                }
                catch (Throwable ignored) {
                    continue;
                }
                if (chunk == null) {
                    continue;
                }
                for (TileEntity te : chunk.getTileEntityMap().values()) {
                    if (te instanceof TileAlarmLight && !te.isInvalid()) {
                        found.add((TileAlarmLight) te);
                    }
                }
            }
        }
        LIGHT_CACHE = found;
        LIGHT_CACHE_TICK = tick;
        LIGHT_CACHE_DIM = dim;
        return LIGHT_CACHE;
    }

    private static Vec3d cameraPos(Minecraft mc, float partial) {
        Entity view = mc.getRenderViewEntity();
        if (view == null) {
            return new Vec3d(0.0D, 0.0D, 0.0D);
        }
        double x = view.lastTickPosX + (view.posX - view.lastTickPosX) * partial;
        double y = view.lastTickPosY + (view.posY - view.lastTickPosY) * partial;
        double z = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * partial;
        return new Vec3d(x, y, z);
    }

    /**
     * Conservative cull: only drop a light when its entire cone volume sits
     * behind the camera. A view-cone test on the block position is wrong here -
     * a 16 block beam can sweep across the screen while the lamp itself is off
     * to the side.
     */
    private static boolean visible(Minecraft mc, TileAlarmLight light, Vec3d cam) {
        // 用渲染视点而不是玩家实体：第三人称（F5）和自由视角下两者方向可以差 180°，
        // 拿玩家的朝向剔除会把玩家身后的灯当"在身后"扔掉，可镜头正对着它
        Entity view = mc.getRenderViewEntity();
        Vec3d look = view != null ? view.getLook(1.0F) : mc.player.getLook(1.0F);
        double dx = light.getPos().getX() + 0.5D - cam.x;
        double dy = light.getPos().getY() + 0.5D - cam.y;
        double dz = light.getPos().getZ() + 0.5D - cam.z;
        double reach = Math.max(1.0F, light.getRadius());
        return dx * look.x + dy * look.y + dz * look.z > -reach;
    }

    /**
     * 这盏灯这一帧<b>本渲染器</b>要不要画。
     *
     * <p>两条判断，顺序不能换：</p>
     *
     * <ol>
     *   <li><b>走 SSS 的一律不归这里管</b> —— 那条路由 {@code DepthDebugPass} 的
     *       全屏 pass 画，这里再画一遍就是两份自发光叠在同一盏灯上（过曝白斑，
     *       而且形状对不上：SSS 是深度裁剪的柔和光斑，这边是多边形锥）。</li>
     *   <li><b>光影包下只有贴图档留下</b> —— 几何锥体是零贴图 {@code POSITION_COLOR}，
     *       光影的 program 里 sampler0 照采，采到的是图集 (0,0)，画出来是黑雾；
     *       贴图档是带 UV 的普通世界几何，光影包按常规方块几何处理，能正常采样。
     *       这里只做取舍，不碰任何 program、不往别人的管线里塞东西。</li>
     * </ol>
     *
     * <p>注意第二道判断对「自动」档是<b>透明的</b>：光影下 {@code autoPath()}
     * 自己会选贴图（PATH_TEXTURE 落在第二道判断的放行侧），所以自动灯在光影下
     * 照样有光；被这道判断挡住的只有手动选 FBO / 几何的灯 —— 那是玩家自己
     * 点出来的决定。</p>
     */
    private static boolean draws(TileAlarmLight light, boolean shaders) {
        int path = light.renderPath();
        if (path == TileAlarmLight.PATH_SSS) {
            return false;
        }
        return !shaders || path == TileAlarmLight.PATH_TEXTURE;
    }

    /** 渲染异常只报一次，否则每帧一刷，日志里全是它 */
    private static boolean renderErrorLogged;

    /**
     * 灯体渲染出错后的现场恢复。
     *
     * <p>异常可能停在 {@code buf.begin()} 和 {@code tess.draw()} 之间，共享的
     * {@code BufferBuilder} 还停在"绘制中"状态 —— 不复位的话，之后任何一次
     * {@code begin()}（包括原版和别的模组的）都会 {@code IllegalStateException}，
     * 一盏灯的错扩大成整帧的错。{@code BufferBuilder.reset()} 就是干这个的：
     * 把 isDrawing / 顶点计数 / 模式全部清零，等价于"这笔白画了"。</p>
     */
    private static void recoverAfterRenderError(Throwable t) {
        try {
            Tessellator.getInstance().getBuffer().reset();
        }
        catch (Throwable ignored) {
            // 连 reset 都救不了就到此为止：finally 里的 GL 状态还原仍然会跑
        }
        if (!renderErrorLogged) {
            renderErrorLogged = true;
            com.jiezhirbmkmod.RbmkMonitorMod.warn(
                    "[灯体] 报警灯渲染出错，本盏跳过（只报一次）：{}", t.toString());
        }
    }

    /**
     * 灯口法线，读方块的 {@code FACING}。取不到方块（区块卸载、TE 残留）时按"立杆"
     * 处理，这是升级前的唯一姿态，也是失败时最不刺眼的那个方向。
     */
    private static EnumFacing facingOf(TileAlarmLight light) {
        World w = light.getWorld();
        BlockPos p = light.getPos();
        if (w == null || p == null) {
            return EnumFacing.UP;
        }
        try {
            IBlockState st = w.getBlockState(p);
            return st.getBlock() instanceof BlockAlarmLight
                    ? st.getValue(BlockAlarmLight.FACING) : EnumFacing.UP;
        }
        catch (Throwable t) {
            return EnumFacing.UP;
        }
    }

    private static Vec3d axisOf(TileAlarmLight light) {
        net.minecraft.util.math.Vec3i d = AlarmLightVolume.facingOf(light).getDirectionVec();
        return new Vec3d(d.getX(), d.getY(), d.getZ());
    }

    /**
     * 射线起点沿 {@code dir} 离开本方块碰撞箱的距离（slab 法，无额外射线开销）。
     *
     * <p>灯罩中心在方块内部，而 {@code rayTraceBlocks} 会从起点所在方块开始判，
     * 不推出去每条射线都会撞在自己身上。推出去以后光锥从灯罩外表面开始，
     * 沿各方向的距离由 AABB 自动决定，不会出现"光从半空中冒出来"。</p>
     */
    private static double exitDistance(AxisAlignedBB box, Vec3d origin, Vec3d dir) {
        if (box == null) {
            return 0.0D;
        }
        // 起点在盒内时，出射点取三个"离开面"里<b>最早</b>的那个（即 min）。
        // 取 max 会把近乎平行于某轴的分量算进来，得到假的大距离，射线被推得过远。
        double t = Double.MAX_VALUE;
        t = Math.min(t, AlarmLightVolume.planeT(box.minX, box.maxX, origin.x, dir.x));
        t = Math.min(t, AlarmLightVolume.planeT(box.minY, box.maxY, origin.y, dir.y));
        t = Math.min(t, AlarmLightVolume.planeT(box.minZ, box.maxZ, origin.z, dir.z));
        if (t == Double.MAX_VALUE) {
            return 0.0D;
        }
        return Math.min(1.2D, Math.max(0.0D, t));
    }

    /** 射线穿过该轴某一面的曲线参数，平行于该轴时返回无穷（不构成出射面）。 */
    private static double planeT(double min, double max, double o, double d) {
        if (d > 1.0E-9D) {
            return (max - o) / d;
        }
        if (d < -1.0E-9D) {
            return (min - o) / d;
        }
        return Double.MAX_VALUE;
    }

    /**
     * 光锥顶点：方块中心沿灯口法线退 {@link #LENS_OFFSET}。
     *
     * <p>不能放在方块表面：那样灯罩内部是空的，光看起来从玻璃外面才开始。
     * 也不能固定用 {@code y + 6/16}：吊顶灯和挂壁灯会整根错位。</p>
     */
    private static Vec3d apex(TileAlarmLight light) {
        Vec3d n = AlarmLightVolume.axisOf(light);
        BlockPos p = light.getPos();
        return new Vec3d(p.getX() + 0.5D + n.x * LENS_OFFSET,
                p.getY() + 0.5D + n.y * LENS_OFFSET,
                p.getZ() + 0.5D + n.z * LENS_OFFSET);
    }

    /** 任意一个与 {@code v} 垂直的单位向量。 */
    private static Vec3d perpOf(Vec3d v) {
        Vec3d ref = Math.abs(v.y) < 0.9D ? new Vec3d(0.0D, 1.0D, 0.0D) : new Vec3d(1.0D, 0.0D, 0.0D);
        Vec3d c = v.crossProduct(ref);
        double len = c.length();
        return len < 1.0E-6D ? new Vec3d(1.0D, 0.0D, 0.0D) : c.scale(1.0D / len);
    }

    /**
     * 锥的主方向。
     *
     * <ul>
     *   <li><b>立杆</b>：灯口朝上，但反光镜把光甩向四周水平方向，所以锥轴取水平；</li>
     *   <li><b>吊顶 / 挂壁</b>：锥轴就是灯口法线，光从开口射出去。</li>
     * </ul>
     *
     * <p>与 {@link AlarmBeam#beamWorldDir} 的分支同源：立杆的径向平面是水平面，
     * 另两种沿法线。两处必须一起改，否则可见灯罩和光斑会分家。</p>
     */
    private static Vec3d coneAxis(Vec3d n, LightType type, boolean back) {
        if (type == LightType.FLOOR) {
            Vec3d u = AlarmLightVolume.perpOf(n);
            return back ? u.scale(-1.0D) : u;
        }
        return n;
    }

    /**
     * 绕单位轴 {@code axis} 旋转（Rodrigues）。渲染时每帧每点算一次，
     * 立杆灯绕竖直轴转 = 水平扫，吊顶灯绕自身法线转 = 不动，挂壁灯绕水平法线转
     * = 在墙面上画圆，三种姿态共用一个公式。
     */
    private static Vec3d rotateAround(Vec3d v, Vec3d axis, double cos, double sin) {
        double dot = axis.dotProduct(v);
        Vec3d cross = axis.crossProduct(v);
        return v.scale(cos).add(cross.scale(sin)).add(axis.scale(dot * (1.0D - cos)));
    }

    private static Cache rebuild(World world, TileAlarmLight light, long now, Vec3d axis) {
        Cache cache = CACHES.get(light);
        if (cache == null) {
            cache = new Cache();
            CACHES.put(light, cache);
        }
        Vec3d apex = AlarmLightVolume.apex(light);
        float radius = Math.max(1.0F, light.getRadius());
        double cone = Math.toRadians(Math.max(5.0F, Math.min(80.0F, light.getConeAngle())));
        LightType type = LightType.classify(axis);
        int beams = type.beamCount();
        int size = AZIMUTH * ELEVATION;

        if (cache.localA == null || cache.localA.length != size) {
            cache.localA = new Vec3d[size];
            cache.distA = new double[size];
        }
        // 立杆两束、吊顶和挂壁一束：束数由 LightType 给，别在这里写死 2
        AxisAlignedBB box = BlockAlarmLight.boxFor(AlarmLightVolume.facingOf(light));
        AlarmLightVolume.traceBeam(world, apex, axis,
                AlarmLightVolume.coneAxis(axis, type, false), cone, radius, box,
                cache.localA, cache.distA);
        if (beams > 1) {
            if (cache.localB == null || cache.localB.length != size) {
                cache.localB = new Vec3d[size];
                cache.distB = new double[size];
            }
            AlarmLightVolume.traceBeam(world, apex, axis,
                    AlarmLightVolume.coneAxis(axis, type, true), cone, radius, box,
                    cache.localB, cache.distB);
        }
        else {
            cache.localB = null;
            cache.distB = null;
        }

        cache.radius = radius;
        cache.cone = light.getConeAngle();
        cache.pos = light.getPos();
        cache.axis = axis;
        cache.type = type;
        cache.beams = beams;
        cache.tick = now;
        return cache;
    }

    /**
     * 沿一个锥主方向打 {@code AZIMUTH * ELEVATION} 条射线，把结果写进 {@code out}。
     * 射线打到什么就停在什么位置，这是"几何锥体 + rayTraceBlocks 遮挡"的全部内容：
     * 玻璃、半砖、别的模组的机器都会真的挡住光，因为走的是原版碰撞判定。
     */
    private static void traceBeam(World world, Vec3d apex, Vec3d axis, Vec3d base, double cone,
            float radius, AxisAlignedBB box, Vec3d[] out, double[] dist) {
        Vec3d p = AlarmLightVolume.perpOf(base);
        Vec3d q = base.crossProduct(p);
        // 灯罩中心在方块局部坐标里的位置（含沿法线的偏移），用来算"离开自身碰撞箱"的距离
        Vec3d local = new Vec3d(0.5D, 0.5D, 0.5D).add(axis.scale(LENS_OFFSET));
        int idx = 0;
        for (int i = 0; i < AZIMUTH; ++i) {
            double phi = Math.PI * 2.0D * i / AZIMUTH;
            Vec3d radial = p.scale(Math.cos(phi)).add(q.scale(Math.sin(phi)));
            for (int j = 0; j < ELEVATION; ++j) {
                double theta = cone * (j + 1) / ELEVATION;
                Vec3d dir = base.scale(Math.cos(theta)).add(radial.scale(Math.sin(theta)));
                // 推出的距离最多半个射程：小半径的灯不能被推到射程之外，
                // 否则 from 会落在 to 后面，射线方向就反了
                double skip = AlarmLightVolume.exitDistance(box, local, dir) + 0.002D;
                skip = Math.min(skip, radius * 0.5D);
                Vec3d from = apex.add(dir.scale(skip));
                Vec3d to = apex.add(dir.scale(radius));
                Vec3d end = to;
                if (radius - skip > 0.05D) {
                    RayTraceResult hit = world.rayTraceBlocks(from, to, false, true, false);
                    if (hit != null) {
                        end = hit.hitVec;
                    }
                }
                Vec3d rel = end.subtract(apex);
                out[idx] = rel;
                dist[idx] = rel.length();
                ++idx;
            }
        }
    }

    private static void renderLight(Minecraft mc, TileAlarmLight light, World world,
            float partialTicks, Cache cache) {
        Vec3d apex = AlarmLightVolume.apex(light);
        // 与 TESR 和屏幕空间聚光灯同一个角度源：包含 per-position 相位偏移。
        // 以前这里自己乘 totalWorldTime，一排灯会整齐划一地闪，而灯罩早就错开了。
        float angle = light.angleDeg(partialTicks);
        int color = light.getColor();
        float r = ((color >> 16) & 255) / 255.0F;
        float g = ((color >> 8) & 255) / 255.0F;
        float b = (color & 255) / 255.0F;
        float radius = Math.max(1.0F, light.getRadius());
        Vec3d axis = cache.axis != null ? cache.axis : new Vec3d(0.0D, 1.0D, 0.0D);
        // ★ 贴图档走另一套几何：光晕 + 光柱，全部是带 UV 的告示板。
        //   两套都用 cache 里同一批射线结果算长度，所以"光柱打到哪"和"几何锥打在哪"
        //   是同一个答案，切档的时候光不会忽然长一截或短一截。
        if (light.renderPath() == TileAlarmLight.PATH_TEXTURE) {
            AlarmLightVolume.renderTextureLight(mc, apex, axis, radius, r, g, b,
                    partialTicks, cache);
            return;
        }
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        // 第二束的锥轴在 traceBeam 里就反了 180°，两组传同一个 angle
        AlarmLightVolume.renderCone(buf, apex, axis, cache.localA, cache.distA, angle, radius, r, g, b);
        if (cache.localB != null) {
            AlarmLightVolume.renderCone(buf, apex, axis, cache.localB, cache.distB, angle, radius, r, g, b);
        }
        AlarmLightVolume.renderGlow(mc, apex, radius, light, partialTicks, r, g, b);
    }

    /**
     * One cone: apex fan + wall cells. No rim cap - the old triangle fan
     * flattened onto walls as a bright disc. Points are rotated into scratch
     * arrays instead of allocating a Vec3d each: 576 allocations per light per
     * frame was pure GC pressure.
     */
    private static void renderCone(BufferBuilder buf, Vec3d apex, Vec3d axis, Vec3d[] local,
            double[] dist, float angle, float radius, float r, float g, float b) {
        double rad = Math.toRadians(angle);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        for (int i = 0; i < AZIMUTH; ++i) {
            for (int j = 0; j < ELEVATION; ++j) {
                int k = i * ELEVATION + j;
                // 绕灯口法线旋转，不再写死绕 Y：吊顶灯绕自己的竖直法线转等于不动，
                // 挂壁灯绕水平法线转就是在墙面画圆，立杆灯绕竖直轴转就是水平扫。
                Vec3d l = AlarmLightVolume.rotateAround(local[k], axis, cos, sin);
                SCRATCH_X[k] = (float) (apex.x + l.x);
                SCRATCH_Y[k] = (float) (apex.y + l.y);
                SCRATCH_Z[k] = (float) (apex.z + l.z);
                SCRATCH_A[k] = AlarmLightVolume.alphaAt(dist[k], radius);
            }
        }
        float ax = (float) apex.x;
        float ay = (float) apex.y;
        float az = (float) apex.z;

        buf.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
        // apex fan: closes the mouth so the cone does not start with a hole
        for (int i = 0; i < AZIMUTH; ++i) {
            int nxt = (i + 1) % AZIMUTH;
            int k0 = i * ELEVATION;
            int k1 = nxt * ELEVATION;
            AlarmLightVolume.vertex(buf, ax, ay, az, 0.0F, r, g, b);
            AlarmLightVolume.vertex(buf, SCRATCH_X[k0], SCRATCH_Y[k0], SCRATCH_Z[k0],
                    SCRATCH_A[k0], r, g, b);
            AlarmLightVolume.vertex(buf, SCRATCH_X[k1], SCRATCH_Y[k1], SCRATCH_Z[k1],
                    SCRATCH_A[k1], r, g, b);
        }
        // wall cells: two triangles each, alpha continuous across the corners
        for (int i = 0; i < AZIMUTH; ++i) {
            int nxt = (i + 1) % AZIMUTH;
            for (int j = 0; j < ELEVATION - 1; ++j) {
                int k0 = i * ELEVATION + j;
                int k1 = nxt * ELEVATION + j;
                int k2 = nxt * ELEVATION + j + 1;
                int k3 = i * ELEVATION + j + 1;
                AlarmLightVolume.vertex(buf, SCRATCH_X[k0], SCRATCH_Y[k0], SCRATCH_Z[k0],
                        SCRATCH_A[k0], r, g, b);
                AlarmLightVolume.vertex(buf, SCRATCH_X[k1], SCRATCH_Y[k1], SCRATCH_Z[k1],
                        SCRATCH_A[k1], r, g, b);
                AlarmLightVolume.vertex(buf, SCRATCH_X[k2], SCRATCH_Y[k2], SCRATCH_Z[k2],
                        SCRATCH_A[k2], r, g, b);
                AlarmLightVolume.vertex(buf, SCRATCH_X[k0], SCRATCH_Y[k0], SCRATCH_Z[k0],
                        SCRATCH_A[k0], r, g, b);
                AlarmLightVolume.vertex(buf, SCRATCH_X[k2], SCRATCH_Y[k2], SCRATCH_Z[k2],
                        SCRATCH_A[k2], r, g, b);
                AlarmLightVolume.vertex(buf, SCRATCH_X[k3], SCRATCH_Y[k3], SCRATCH_Z[k3],
                        SCRATCH_A[k3], r, g, b);
            }
        }
        Tessellator.getInstance().draw();
    }

    /** continuous falloff - no step between neighbouring cells */
    private static float alphaAt(double dist, float radius) {
        double k = Math.max(0.0D, 1.0D - dist / radius);
        return (float) (Math.pow(k, 1.6D) * BASE_ALPHA);
    }

    private static void vertex(BufferBuilder buf, float x, float y, float z, float alpha,
            float r, float g, float b) {
        buf.pos(x, y, z).color(r, g, b, alpha).endVertex();
    }

    /** additive radial sprite so the lamp has a visible core */
    private static void renderGlow(Minecraft mc, Vec3d apex, float radius, TileAlarmLight light,
            float partialTicks, float r, float g, float b) {
        Entity view = mc.getRenderViewEntity();
        if (view == null) {
            return;
        }
        // fade the core out with distance, keep a floor so it never pops
        double dist = light.getPos().distanceSq(view.posX, view.posY, view.posZ);
        float strength = (float) Math.max(0.25D, 1.0D - dist / (MAX_DISTANCE * MAX_DISTANCE));
        strength *= GLOW_STRENGTH;
        Vec3d look = view.getLook(partialTicks);
        Vec3d right = look.crossProduct(new Vec3d(0.0D, 1.0D, 0.0D));
        if (right.lengthSquared() < 1.0E-6D) {
            return;
        }
        right = right.normalize();
        Vec3d up = right.crossProduct(look).normalize();
        float size = Math.max(0.18F, Math.min(0.5F, radius * 0.028F));
        mc.getTextureManager().bindTexture(GLOW);
        GlStateManager.enableTexture2D();
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
        AlarmLightVolume.glowVertex(buf, apex.add(right.scale(size)).add(up.scale(size)),
                1.0F, 0.0F, r, g, b, strength);
        AlarmLightVolume.glowVertex(buf, apex.add(right.scale(-size)).add(up.scale(size)),
                0.0F, 0.0F, r, g, b, strength);
        AlarmLightVolume.glowVertex(buf, apex.add(right.scale(-size)).add(up.scale(-size)),
                0.0F, 1.0F, r, g, b, strength);
        AlarmLightVolume.glowVertex(buf, apex.add(right.scale(size)).add(up.scale(-size)),
                1.0F, 1.0F, r, g, b, strength);
        tess.draw();
        GlStateManager.disableTexture2D();
    }

    private static void glowVertex(BufferBuilder buf, Vec3d p, float u, float v,
            float r, float g, float b, float alpha) {
        buf.pos(p.x, p.y, p.z).tex(u, v).color(r * alpha, g * alpha, b * alpha, alpha)
                .endVertex();
    }

    /**
     * 贴图灯光（面板第四档）：光晕 + 光柱，全部由同一张 64×64 径向渐变贴图拼出来。
     *
     * <p>两段几何：</p>
     * <ul>
     *   <li><b>A 光晕</b>：灯口一块朝向镜头的方形告示板，尺寸随半径走。负责
     *       "这里有一盏亮着的灯"，十几格外也能看见一个亮点。</li>
     *   <li><b>B 光柱</b>：沿光锥主方向排 {@link #TEXTURE_PUFFS} 块，一块比一块大、
     *       一块比一块淡，叠出体积感。每块的落点距离取自 {@code cache} 里已经打好的
     *       射线结果（最外圈命中距离的平均），所以光柱不会捅穿墙面。</li>
     * </ul>
     *
     * <p>为什么这条路径值得单独存在：它全程 {@code POSITION_TEX_COLOR}、有 UV，
     * 是三条路里<b>唯一在光影包下还能正常显示</b>的。几何锥体是零贴图
     * {@code POSITION_COLOR}，SSS 是自写 program，两者在光影下都得让路。</p>
     *
     * <p>遮挡是近似：光柱总长取最外圈命中距离的平均，不是逐块板单独打射线。
     * 精确遮挡要几十条额外射线，而这档的定位是"便宜且一定画得出来"，
     * 想要严格遮挡的人应该选几何那档。</p>
     */
    private static void renderTextureLight(Minecraft mc, Vec3d apex, Vec3d axis, float radius,
            float r, float g, float b, float partialTicks, Cache cache) {
        Entity view = mc.getRenderViewEntity();
        if (view == null) {
            return;
        }
        Vec3d look = view.getLook(partialTicks);
        Vec3d right = look.crossProduct(new Vec3d(0.0D, 1.0D, 0.0D));
        if (right.lengthSquared() < 1.0E-6D) {
            // 镜头垂直朝上/朝下时"右"退化成零向量，告示板会被压成一条线，索性不画
            return;
        }
        right = right.normalize();
        Vec3d up = right.crossProduct(look).normalize();

        mc.getTextureManager().bindTexture(GLOW);
        GlStateManager.enableTexture2D();
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);

        // A：灯口光晕
        float coreSize = Math.max(0.28F, Math.min(0.80F, radius * 0.42F));
        AlarmLightVolume.billboard(buf, apex, right, up, coreSize, r, g, b, 0.55F);

        // B：沿束排一串。立杆是两束对扫，吊顶 / 挂壁只有一束
        int beams = cache.beams > 1 ? 2 : 1;
        for (int side = 0; side < beams; ++side) {
            Vec3d dir = AlarmLightVolume.coneAxis(axis, cache.type, side == 1);
            double len = AlarmLightVolume.beamLength(side == 1 ? cache.distB : cache.distA);
            if (len < 0.35D) {
                continue;
            }
            for (int i = 0; i < TEXTURE_PUFFS; ++i) {
                double t = (i + 1.0D) / TEXTURE_PUFFS;
                Vec3d c = apex.add(dir.scale(len * t));
                float size = (float) Math.max(0.20D,
                        Math.min(1.40D, radius * (0.10D + 0.17D * t)));
                // 越远越淡：这块是叠加上去的，单块太亮会在灯口糊成一团白球
                float alpha = 0.60F * (1.0F - (float) t * 0.78F);
                AlarmLightVolume.billboard(buf, c, right, up, size, r, g, b, alpha);
            }
        }
        tess.draw();
        GlStateManager.disableTexture2D();
    }

    /**
     * 一块朝向镜头的方形告示板，四角 UV 铺满整张贴图。
     *
     * <p>顶点顺序和 UV 的对应关系必须和 {@link #glowVertex} 的既有用法一致
     * （右上 1,0 / 左上 0,0 / 左下 0,1 / 右下 1,1），顺序错了贴图会沿对角线镜像。</p>
     */
    private static void billboard(BufferBuilder buf, Vec3d c, Vec3d right, Vec3d up,
            float size, float r, float g, float b, float alpha) {
        Vec3d rx = right.scale(size);
        Vec3d uy = up.scale(size);
        AlarmLightVolume.glowVertex(buf, c.add(rx).add(uy), 1.0F, 0.0F, r, g, b, alpha);
        AlarmLightVolume.glowVertex(buf, c.add(rx.scale(-1.0D)).add(uy),
                0.0F, 0.0F, r, g, b, alpha);
        AlarmLightVolume.glowVertex(buf, c.add(rx.scale(-1.0D)).add(uy.scale(-1.0D)),
                0.0F, 1.0F, r, g, b, alpha);
        AlarmLightVolume.glowVertex(buf, c.add(rx).add(uy.scale(-1.0D)),
                1.0F, 1.0F, r, g, b, alpha);
    }

    /**
     * 一束光大概能打多远：取最外圈（仰角最大那圈）{@link #AZIMUTH} 个采样点命中距离的平均。
     *
     * <p>不逐块板做遮挡判断 —— 那要多打几十条射线，代价翻十倍；取平均已经能保证
     * "对着墙时不会捅穿、对着空地时也不会缩成一团"。</p>
     */
    private static double beamLength(double[] dist) {
        if (dist == null || dist.length < ELEVATION) {
            return 0.0D;
        }
        double sum = 0.0D;
        int n = 0;
        for (int i = 0; i < AZIMUTH; ++i) {
            int k = i * ELEVATION + ELEVATION - 1;
            if (k < dist.length) {
                sum += dist[k];
                ++n;
            }
        }
        return n == 0 ? 0.0D : sum / n;
    }

    /*
     * 这里原本还有一个 renderDecal()：光影下改画一张零贴图的叠加公告板，
     * 想法是"锥体不行就贴片凑"。删掉有两个原因：
     *
     *   1. 它同样是 POSITION_COLOR、同样没有 UV，在光影的 program 里照样恒采
     *      gtexture 的 (0,0) 像素，画出来是一块半透明黑斑 —— 比不画更显眼，
     *      根本没有"降级"到任何能看的东西；
     *   2. onRenderWorldLast 现在在光影下整段早退，它已经是不可达代码。
     *
     * 留着它只会让下一个人以为"光影下有兜底"，然后把早退放开，再踩一次同一个坑。
     */

    /**
     * OptiFine 光影是否生效。
     *
     * <p>这里原来自己写了一份反射，用的类名是 {@code "Config"} —— 少了
     * {@code net.optifine.} 前缀，每次都是 {@code ClassNotFoundException}，被下面的
     * {@code catch} 吞掉返回 {@code false}。也就是说这个判断<b>从写进来那天起就没
     * 返回过 true</b>，调用它的"有光影就降级"分支一次都没执行过。</p>
     *
     * <p>现在直接复用 {@link DepthDebugPass#shaderPackActive()}：那边已经把
     * {@code Shaders.shaderPackLoaded} 和 {@code Config.isShaders} 两条反射路径都试了，
     * 还带 100 次调用刷新一次的缓存。同一个判断只留一处实现，不会再出现
     * "SSS 认为有光影、几何光锥认为没有"这种自相矛盾。</p>
     */
    public static boolean shadersActive() {
        return DepthDebugPass.shaderPackActive();
    }
}
