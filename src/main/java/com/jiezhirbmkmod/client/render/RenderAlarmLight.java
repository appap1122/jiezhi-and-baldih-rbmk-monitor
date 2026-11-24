package com.jiezhirbmkmod.client.render;

import com.jiezhirbmkmod.block.BlockAlarmLight;
import com.jiezhirbmkmod.block.TileAlarmLight;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

/**
 * 旋转警示灯的渲染器（v17.7：整段换回原版 jiezhimod 5.6）——
 * 底座 + 旋转灯芯 + 圆灯罩，<b>全部现画几何，零贴图</b>。
 *
 * <h3>为什么不给方块贴图</h3>
 * 16×16 的像素材质画圆只能得到锯齿圈，而需求是「完美的圆」。所以这里<b>全部现画几何</b>：
 * 圆柱按 {@link #SIDES} 边形细分，{@link #SIDES}=48 时一格宽度上是 48 段，
 * 肉眼已经完全看不出折线。
 *
 * <h3>光效去哪儿了</h3>
 * 1.12.2 的原版方块光只有亮度、没有颜色，所以「一束光扫过墙面、把墙照出颜色」
 * 不可能靠 {@code getLightValue} 做出来。真正的光照由屏幕空间聚光灯
 * （{@code DepthDebugPass} + {@code AlarmSpotlights}）负责，
 * 方向唯一来源是 {@link AlarmBeam#beamWorldDir}。本类只负责画出灯的外壳、反光板与灯罩 ——
 * 光柱、光斑贴片全部不再画（和 SSS 叠在一起会出现「柔和光照 + 一圈生硬红圈」）。
 *
 * <h3>朝向怎么处理</h3>
 * 不依赖 {@code GlStateManager.rotate} 的欧拉角顺序，而是先算一组正交基
 * （灯轴 {@code axis} + 两个径向 {@code u / v}），所有局部坐标一律
 * {@code world = axis*y + u*x + v*z} 变换。反光板和光锥共用同一组基，
 * 不会出现「锥体朝东、影子朝西」这种对不上的情况，六个安装方向也不需要六段矩阵代码。
 */
@SideOnly(Side.CLIENT)
public class RenderAlarmLight extends TileEntitySpecialRenderer<TileAlarmLight> {

    /** 圆周细分段数。32 已经看不出多边形，48 更稳（一格 48 段） */
    private static final int SIDES = 48;

    private static final int BASE_RGB = 0x2E3338;
    private static final int RIM_RGB = 0x4A525A;

    @Override
    public void render(TileAlarmLight te, double x, double y, double z, float partialTicks,
                       int destroyStage, float alpha) {
        if (te == null || te.getWorld() == null) {
            return;
        }
        // 破坏进度期间方块本体是 INVISIBLE，再画外壳会看到"半透明灯罩悬在空中"
        if (destroyStage >= 0) {
            return;
        }
        IBlockState state = te.getWorld().getBlockState(te.getPos());
        if (!(state.getBlock() instanceof BlockAlarmLight)) {
            return;
        }
        EnumFacing facing = state.getValue(BlockAlarmLight.FACING);
        float angle = te.angleDeg(partialTicks);
        boolean on = state.getValue(BlockAlarmLight.ON).booleanValue();
        int rgb = te.getColor() & 0xFFFFFF;

        // 正交基：axis = 灯口朝外；u / v 是垂直于轴的两个径向
        Vec3i n = facing.getDirectionVec();
        Vec3d axis = new Vec3d(n.getX(), n.getY(), n.getZ());
        Vec3d u = AlarmBeam.perpendicular(axis);
        Vec3d v = axis.crossProduct(u);

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();

        boolean cullWasOn = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        // 光照开关也要原样还回去：TESR 通常跑在 disableLighting 状态，
        // 无条件 enableLighting 会让后面渲染的东西突然变暗（或变亮）
        boolean lightingWasOn = GL11.glIsEnabled(GL11.GL_LIGHTING);
        // ★ v17.8：混合与贴图状态同样要还。原来无条件 disableBlend()，
        //   如果这一帧先渲染了粒子/光晕（blend 开着）再轮到灯，出来之后 blend 被关掉，
        //   后面的东西就整片变成不透明 —— 表现是"走过一盏灯，粒子突然不透明了"。
        boolean blendWasOn = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean textureWasOn = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);

        GlStateManager.pushMatrix();
        // ★ 整个绘制段包兜底，还原全部搬进 finally：原来还原代码是顺序写的，
        //   半路抛异常会同时漏掉两件事 —— GL 状态回不来，以及异常沿渲染管线
        //   冒上去把客户端带走（"一开光影立刻黑屏"最典型的形态：TESR 在光影的
        //   shadow pass 里也会被调，几何状态被别人的 program 接管后随时可能
        //   出幺蛾子）。catch 里复位可能停在 begin/draw 之间的共享 BufferBuilder，
        //   异常只记一次。
        try {

            GlStateManager.translate(x + 0.5D, y + 0.5D, z + 0.5D);
            GlStateManager.disableTexture2D();
            GlStateManager.disableLighting();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            GlStateManager.disableCull();

            // ---------- 1) 底座（不透明，写深度） ----------
            buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
            cylinder(buf, axis, u, v, -0.5D, -0.36D, 0.42D, 0.42D, BASE_RGB, 1.0F, true, false);
            cylinder(buf, axis, u, v, -0.38D, -0.34D, 0.45D, 0.45D, RIM_RGB, 1.0F, false, false);
            tess.draw();

            // ---------- 2a) 灯泡内胆 ----------
            // 颜色压暗（往 0x14181C 混 55%），看起来是实体灯泡而不是一层粉色滤镜；
            // 不写深度 —— 否则反光板转到背面时会被整片挡掉，扫起来像卡帧
            GlStateManager.depthMask(false);
            buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
            int core = on ? mix(rgb, 0x14181C, 0.55F) : 0x33383D;
            cylinder(buf, axis, u, v, -0.34D, 0.04D, 0.22D, 0.19D, core, 0.85F, true, true);
            tess.draw();

            // ---------- 2) 灯芯：两块反光板绕轴旋转 ----------
            if (on) {
                buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
                reflector(buf, axis, u, v, Math.toRadians(angle), rgb);
                reflector(buf, axis, u, v, Math.toRadians(angle + 180.0D), rgb);
                tess.draw();
            }

            // ---------- 3) 灯罩（半透明，不写深度，双面） ----------
            GlStateManager.depthMask(false);
            // 白混 0.28、alpha 0.16：之前"整个灯泡是一层粉光"就是这两个数太高，
            // 白混把红色冲成粉，alpha 太厚把内胆糊掉。
            float glassAlpha = on ? 0.16F : 0.10F;
            int glass = on ? mix(rgb, 0xFFFFFF, 0.28F) : 0xB8C2CC;
            buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
            cylinder(buf, axis, u, v, -0.36D, 0.10D, 0.36D, 0.34D, glass, glassAlpha, true, false);
            // 顶盖：一圈略亮的边，让"圆"的轮廓在亮处也看得见
            cylinder(buf, axis, u, v, 0.10D, 0.125D, 0.34D, 0.30D, mix(glass, 0xFFFFFF, 0.35F),
                    glassAlpha + 0.06F, true, false);
            tess.draw();

            // ---------- 4) 3D 体积光柱：已废弃 ----------
            // 真实警示灯在空气里看不到光束（没开光影体积雾时），光只出现在被打亮的表面上。

        }
        catch (Throwable t) {
            RenderAlarmLight.recoverAfterRenderError(t);
        }
        finally {
            GlStateManager.popMatrix();


            // 恢复
            GlStateManager.depthMask(depthMask);
            if (cullWasOn) {
                GlStateManager.enableCull();
            } else {
                GlStateManager.disableCull();
            }
            if (blendWasOn) {
                GlStateManager.enableBlend();
            } else {
                GlStateManager.disableBlend();
            }
            if (lightingWasOn) {
                GlStateManager.enableLighting();
            } else {
                GlStateManager.disableLighting();
            }
            if (textureWasOn) {
                GlStateManager.enableTexture2D();
            } else {
                GlStateManager.disableTexture2D();
            }
            // 顶点色画完必须把全局颜色拨回白色：TESR 之后紧跟着的是方块/实体渲染，
            // 留着最后一盏灯的颜色会让后面的东西整片染色（灯是红的，后面的机器也发红）
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        }

    }


    /** TESR 异常只报一次，否则每个灯每帧一条日志 */
    private static boolean renderErrorLogged;

    /**
     * 灯壳渲染出错后的现场恢复（同 {@code AlarmLightVolume#recoverAfterRenderError}）：
     * 复位可能停在 begin/draw 之间的共享 {@code BufferBuilder} —— 不复位的话，
     * 之后任何一次 begin() 都会 IllegalStateException，一个灯的错扩大成整帧的错。
     * 异常沿 TESR 冒出去同样会把客户端带走，所以这里兜，跟渲染事件的兜底是一个道理。
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
                    "[灯壳] 报警灯 TESR 渲染出错，本盏跳过（只报一次）：{}", t.toString());
        }
    }

    // ==================== 几何 ====================

    /** 局部 (x, y, z) → 世界偏移；y 沿灯轴，x/z 是径向 */
    private static Vec3d local(Vec3d axis, Vec3d u, Vec3d v, double lx, double ly, double lz) {
        return new Vec3d(
                axis.x * ly + u.x * lx + v.x * lz,
                axis.y * ly + u.y * lx + v.y * lz,
                axis.z * ly + u.z * lx + v.z * lz);
    }

    private static void vertex(BufferBuilder buf, Vec3d axis, Vec3d u, Vec3d v,
                               double lx, double ly, double lz, int rgb, float a) {
        Vec3d p = local(axis, u, v, lx, ly, lz);
        buf.pos(p.x, p.y, p.z)
                .color(((rgb >> 16) & 0xFF) / 255.0F, ((rgb >> 8) & 0xFF) / 255.0F,
                        (rgb & 0xFF) / 255.0F, a)
                .endVertex();
    }

    /** 圆台侧面（r0→r1），可选上下盖 */
    private static void cylinder(BufferBuilder buf, Vec3d axis, Vec3d u, Vec3d v,
                                 double y0, double y1, double r0, double r1,
                                 int rgb, float a, boolean capTop, boolean capBottom) {
        for (int i = 0; i < SIDES; i++) {
            double a0 = Math.PI * 2.0D * i / SIDES;
            double a1 = Math.PI * 2.0D * (i + 1) / SIDES;
            double x0 = Math.cos(a0);
            double z0 = Math.sin(a0);
            double x1 = Math.cos(a1);
            double z1 = Math.sin(a1);
            vertex(buf, axis, u, v, x0 * r0, y0, z0 * r0, rgb, a);
            vertex(buf, axis, u, v, x1 * r0, y0, z1 * r0, rgb, a);
            vertex(buf, axis, u, v, x1 * r1, y1, z1 * r1, rgb, a);
            vertex(buf, axis, u, v, x0 * r1, y1, z0 * r1, rgb, a);
        }
        if (capTop) {
            cap(buf, axis, u, v, y1, r1, rgb, a);
        }
        if (capBottom) {
            cap(buf, axis, u, v, y0, r0, rgb, a);
        }
    }

    /** 圆面（扇形拼） */
    private static void cap(BufferBuilder buf, Vec3d axis, Vec3d u, Vec3d v,
                            double y, double r, int rgb, float a) {
        for (int i = 0; i < SIDES; i++) {
            double a0 = Math.PI * 2.0D * i / SIDES;
            double a1 = Math.PI * 2.0D * (i + 1) / SIDES;
            vertex(buf, axis, u, v, 0, y, 0, rgb, a);
            vertex(buf, axis, u, v, Math.cos(a0) * r, y, Math.sin(a0) * r, rgb, a);
            vertex(buf, axis, u, v, Math.cos(a1) * r, y, Math.sin(a1) * r, rgb, a);
            vertex(buf, axis, u, v, 0, y, 0, rgb, a);
        }
    }

    /**
     * 一块反光板：从轴心斜着立起来的小平板。
     * 两块对置（角度差 180°）就形成了旋转警示灯里那个"双面反光碗"的样子 ——
     * 转到正面时亮、转到侧面时几乎看不见，从而产生"一闪一闪扫过去"的观感。
     */
    private static void reflector(BufferBuilder buf, Vec3d axis, Vec3d u, Vec3d v,
                                  double rad, int rgb) {
        double cs = Math.cos(rad);
        double sn = Math.sin(rad);
        // 板在"径向 cs/sn"方向上，从半径 0.03 到 0.30，沿轴向 -0.16..0.12 倾斜
        double rIn = 0.03D;
        double rOut = 0.30D;
        double yLow = -0.16D;
        double yHigh = 0.02D;
        double w = 0.14D;   // 板宽（垂直于径向）
        // 四个角：内下、外下、外上、内上（径向 × 轴向）
        double[] rs = {rIn, rOut, rOut, rIn};
        double[] ys = {yLow, yLow + 0.10D, yHigh + 0.10D, yHigh};
        for (int i = 0; i < 4; i++) {
            double rr = rs[i];
            double yy = ys[i];
            // 局部坐标：径向 = (cs, 0, sn)，法向 = 垂直于径向的水平方向 (-sn, 0, cs)
            double cx = cs * rr;
            double cz = sn * rr;
            double side = (i % 2 == 0) ? -w : w;
            double px = cx - sn * side;
            double pz = cz + cs * side;
            vertex(buf, axis, u, v, px, yy, pz, mix(rgb, 0xFFFFFF, 0.55F), 1.0F);
        }
    }

    /** 两个颜色按 t 混合（t=0 全 a，t=1 全 b） */
    private static int mix(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF;
        int ag = (a >> 8) & 0xFF;
        int ab = a & 0xFF;
        int br = (b >> 16) & 0xFF;
        int bg = (b >> 8) & 0xFF;
        int bb = b & 0xFF;
        int r = (int) (ar + (br - ar) * t);
        int g = (int) (ag + (bg - ag) * t);
        int bl = (int) (ab + (bb - ab) * t);
        return (r << 16) | (g << 8) | bl;
    }
}
