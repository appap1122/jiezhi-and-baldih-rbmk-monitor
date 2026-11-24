package com.jiezhirbmkmod.client.dfc;

import com.jiezhirbmkmod.block.BlockRbmkCustomScreen;
import com.jiezhirbmkmod.block.BlockRbmkGraph;
import com.jiezhirbmkmod.block.BlockRbmkMonitor;
import com.jiezhirbmkmod.block.BlockRbmkScreen;
import com.jiezhirbmkmod.block.RealPower;
import com.jiezhirbmkmod.block.TileRbmkMonitorScreen;
import com.jiezhirbmkmod.client.dfc.RbmkScreenCanvas;
import com.jiezhirbmkmod.dfc.ScreenStructure;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL13;

@SideOnly(value=Side.CLIENT)
public class RenderRbmkMonitorScreen
extends TileEntitySpecialRenderer<TileRbmkMonitorScreen> {
    private static final int C_BACK = -12893880;
    private static final int C_FRAME = -11116953;
    private static final int C_FRAME_DARK = -14012619;
    private static final int C_ARM = -13551811;
    private static final int C_MOUNT = -12235693;
    private static final int C_HINGE = -10787988;
    private static final float HW = 2.0f;
    private static final float HH = 1.5f;
    private static final float T = 0.075f;
    private static final float BEZEL = 0.1f;
    private static final float GAP = 0.012f;

    public void render(TileRbmkMonitorScreen te, double x, double y, double z, float partialTicks, int destroyStage, float alpha) {
        if (te == null || te.getWorld() == null) {
            return;
        }
        IBlockState state = te.getWorld().getBlockState(te.getPos());
        if (!(state.getBlock() instanceof BlockRbmkScreen)) {
            return;
        }
        RbmkScreenCanvas canvas = te.canvas();
        // v18.0 修订：真实屏断电不再"什么都不画"。旧版直接 return，玩家
        // 只看到一块空面板，分不清"还没通电"和"刚才断电了"。现在黑屏 +
        // 提示文字（与待机画面的"待机 POWER"一眼可分），外壳/面板照常
        // 画 —— 物理机身还在，只是屏幕黑了。
        boolean realOff = te.isRealScreen() && te.getRealPowerState() != RealPower.ON;
        if (realOff) {
            boolean curve = state.getBlock() instanceof BlockRbmkGraph
                    || te instanceof com.jiezhirbmkmod.block.TileRbmkCycleScreen;
            canvas.renderRealPowerOff(curve);
            canvas.uploadIfDirty(System.currentTimeMillis());
        } else {
            boolean powered = te.isPowered();
            Block ctrl = state.getBlock();
            int page = 1;
            // v18.0 轮播屏：页号由 Tile 的 currentPage 决定（1=总览/2=热图/4=曲线）
            if (te instanceof com.jiezhirbmkmod.block.TileRbmkCycleScreen) {
                page = ((com.jiezhirbmkmod.block.TileRbmkCycleScreen)te).pageForRender();
            } else if (ctrl instanceof BlockRbmkCustomScreen) {
                page = 3;
            } else if (ctrl instanceof BlockRbmkGraph) {
                page = 4;
            } else if (ctrl instanceof BlockRbmkMonitor) {
                page = 2;
            } else if (ctrl instanceof com.jiezhirbmkmod.block.BlockRbmkTermScreen) {
                page = 5;
            }
            if (te instanceof com.jiezhirbmkmod.block.TileRbmkTermScreen) {
                canvas.setTermLines(((com.jiezhirbmkmod.block.TileRbmkTermScreen)te).termSnapshot());
            }
            canvas.setMainStepMs(te.getRefreshMs());
            canvas.setAlert(te.getAlertLevel(), te.getAlertReason());
            canvas.render(powered ? te.clientBootElapsedMs() : -1L, powered, te.getData(), te.getEvents(), page, powered ? te.clientFaultElapsedMs() : -1L);
            canvas.uploadIfDirty(System.currentTimeMillis());
        }
        EnumFacing facing = (EnumFacing)state.getValue(BlockRbmkScreen.FACING);
        float tilt = ScreenStructure.tiltDegrees(te.getTilt());
        double[] anchor = ScreenStructure.anchor(facing);
        GlStateManager.pushMatrix();
        GlStateManager.pushAttrib();
        GlStateManager.translate((double)(x + anchor[0]), (double)(y + anchor[1]), (double)(z + anchor[2]));
        GlStateManager.rotate((float)ScreenStructure.yawOf(facing), (float)0.0f, (float)1.0f, (float)0.0f);
        float push = 1.5f * (float)Math.sin(Math.toRadians(tilt));
        GlStateManager.translate((float)0.0f, (float)0.0f, (float)(-push));
        this.drawHanger();
        GlStateManager.translate((float)0.0f, (float)1.5f, (float)0.0f);
        GlStateManager.rotate((float)(-tilt), (float)1.0f, (float)0.0f, (float)0.0f);
        GlStateManager.translate((float)0.0f, (float)-1.5f, (float)0.0f);
        this.drawShell();
        this.drawPanel(canvas);
        GlStateManager.popAttrib();
        GlStateManager.popMatrix();
    }

    private void drawHanger() {
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableBlend();
        buf.begin(7, DefaultVertexFormats.POSITION_COLOR);
        RenderRbmkMonitorScreen.box(buf, -1.35f, 1.42f, -0.22f, 1.35f, 1.6f, 0.3f, -10787988);
        RenderRbmkMonitorScreen.box(buf, -1.48f, 1.44f, -0.16f, -1.28f, 1.7f, 0.22f, -12235693);
        RenderRbmkMonitorScreen.box(buf, 1.28f, 1.44f, -0.16f, 1.48f, 1.7f, 0.22f, -12235693);
        RenderRbmkMonitorScreen.box(buf, -1.0f, 1.58f, -0.09f, -0.88f, 1.96f, 0.09f, -13551811);
        RenderRbmkMonitorScreen.box(buf, 0.88f, 1.58f, -0.09f, 1.0f, 1.96f, 0.09f, -13551811);
        RenderRbmkMonitorScreen.box(buf, -1.5f, 1.86f, -0.28f, 1.5f, 1.96f, 0.28f, -12235693);
        RenderRbmkMonitorScreen.box(buf, -1.3f, 1.78f, -0.09f, -1.18f, 1.86f, 0.09f, -10787988);
        RenderRbmkMonitorScreen.box(buf, 1.18f, 1.78f, -0.09f, 1.3f, 1.86f, 0.09f, -10787988);
        tess.draw();
    }

    private void drawShell() {
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableBlend();
        buf.begin(7, DefaultVertexFormats.POSITION_COLOR);
        float e = 0.012f;
        float x0 = -2.0f + e;
        float x1 = 2.0f - e;
        float y0 = -1.5f + e;
        float y1 = 1.5f - e;
        RenderRbmkMonitorScreen.box(buf, x0 + 0.02f, y0 + 0.02f, 0.075f, x1 - 0.02f, y1 - 0.02f, 0.125f, -12893880);
        float zf = -0.085f;
        float zb = 0.075f;
        RenderRbmkMonitorScreen.box(buf, x0, y0, zf, x1, y0 + 0.1f, zb, -11116953);
        RenderRbmkMonitorScreen.box(buf, x0, y1 - 0.1f, zf, x1, y1, zb, -11116953);
        RenderRbmkMonitorScreen.box(buf, x0, y0 + 0.1f, zf, x0 + 0.1f, y1 - 0.1f, zb, -11116953);
        RenderRbmkMonitorScreen.box(buf, x1 - 0.1f, y0 + 0.1f, zf, x1, y1 - 0.1f, zb, -11116953);
        RenderRbmkMonitorScreen.box(buf, x0 + 0.1f - 0.014f, y0 + 0.1f - 0.014f, -0.071f, x1 - 0.1f + 0.014f, y1 - 0.1f + 0.014f, -0.064f, -14012619);
        tess.draw();
    }

    private void drawPanel(RbmkScreenCanvas canvas) {
        if (canvas.texture() == null) {
            return;
        }
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        float x0 = -1.888f;
        float x1 = 1.888f;
        float y0 = -1.388f;
        float y1 = 1.388f;
        float zf = -0.078f;
        GlStateManager.enableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate((int)770, (int)771, (int)1, (int)0);
        GlStateManager.color((float)1.0f, (float)1.0f, (float)1.0f, (float)1.0f);
        GL13.glActiveTexture((int)33984);
        GlStateManager.bindTexture((int)canvas.texture().getGlTextureId());
        buf.begin(7, DefaultVertexFormats.POSITION_TEX_COLOR);
        float[] uv = ScreenStructure.panelUv();
        float ux0 = uv[0];
        float ux1 = uv[1];
        float vy0 = uv[2];
        float vy1 = uv[3];
        buf.pos((double)x1, (double)y0, (double)zf).tex((double)ux1, (double)vy0).color(1.0f, 1.0f, 1.0f, 1.0f).endVertex();
        buf.pos((double)x0, (double)y0, (double)zf).tex((double)ux0, (double)vy0).color(1.0f, 1.0f, 1.0f, 1.0f).endVertex();
        buf.pos((double)x0, (double)y1, (double)zf).tex((double)ux0, (double)vy1).color(1.0f, 1.0f, 1.0f, 1.0f).endVertex();
        buf.pos((double)x1, (double)y1, (double)zf).tex((double)ux1, (double)vy1).color(1.0f, 1.0f, 1.0f, 1.0f).endVertex();
        tess.draw();
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
    }

    private static void box(BufferBuilder buf, float x0, float y0, float z0, float x1, float y1, float z1, int argb) {
        float a = (float)(argb >>> 24 & 0xFF) / 255.0f;
        float r = (float)(argb >> 16 & 0xFF) / 255.0f;
        float g = (float)(argb >> 8 & 0xFF) / 255.0f;
        float b = (float)(argb & 0xFF) / 255.0f;
        RenderRbmkMonitorScreen.v(buf, x1, y0, z0, r, g, b, a);
        RenderRbmkMonitorScreen.v(buf, x0, y0, z0, r, g, b, a);
        RenderRbmkMonitorScreen.v(buf, x0, y1, z0, r, g, b, a);
        RenderRbmkMonitorScreen.v(buf, x1, y1, z0, r, g, b, a);
        RenderRbmkMonitorScreen.v(buf, x0, y0, z1, r, g, b, a);
        RenderRbmkMonitorScreen.v(buf, x1, y0, z1, r, g, b, a);
        RenderRbmkMonitorScreen.v(buf, x1, y1, z1, r, g, b, a);
        RenderRbmkMonitorScreen.v(buf, x0, y1, z1, r, g, b, a);
        RenderRbmkMonitorScreen.v(buf, x0, y0, z0, r, g, b, a);
        RenderRbmkMonitorScreen.v(buf, x0, y0, z1, r, g, b, a);
        RenderRbmkMonitorScreen.v(buf, x0, y1, z1, r, g, b, a);
        RenderRbmkMonitorScreen.v(buf, x0, y1, z0, r, g, b, a);
        RenderRbmkMonitorScreen.v(buf, x1, y0, z1, r, g, b, a);
        RenderRbmkMonitorScreen.v(buf, x1, y0, z0, r, g, b, a);
        RenderRbmkMonitorScreen.v(buf, x1, y1, z0, r, g, b, a);
        RenderRbmkMonitorScreen.v(buf, x1, y1, z1, r, g, b, a);
        RenderRbmkMonitorScreen.v(buf, x0, y0, z1, r, g, b, a);
        RenderRbmkMonitorScreen.v(buf, x1, y0, z1, r, g, b, a);
        RenderRbmkMonitorScreen.v(buf, x1, y0, z0, r, g, b, a);
        RenderRbmkMonitorScreen.v(buf, x0, y0, z0, r, g, b, a);
        RenderRbmkMonitorScreen.v(buf, x0, y1, z0, r, g, b, a);
        RenderRbmkMonitorScreen.v(buf, x1, y1, z0, r, g, b, a);
        RenderRbmkMonitorScreen.v(buf, x1, y1, z1, r, g, b, a);
        RenderRbmkMonitorScreen.v(buf, x0, y1, z1, r, g, b, a);
    }

    private static void v(BufferBuilder buf, float x, float y, float z, float r, float g, float b, float a) {
        buf.pos((double)x, (double)y, (double)z).color(r, g, b, a).endVertex();
    }
}

