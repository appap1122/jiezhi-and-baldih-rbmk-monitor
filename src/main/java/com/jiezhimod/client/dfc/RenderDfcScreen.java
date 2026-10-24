package com.jiezhimod.client.dfc;

import com.jiezhimod.block.BlockRbmkCustomScreen;
import com.jiezhimod.block.BlockRbmkMonitor;
import com.jiezhimod.block.BlockRbmkScreen;
import com.jiezhimod.block.TileRbmkScreen;
import com.jiezhimod.client.dfc.ScreenCanvas;
import com.jiezhimod.dfc.ScreenStructure;
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
public class RenderDfcScreen
extends TileEntitySpecialRenderer<TileRbmkScreen> {
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

    public void render(TileRbmkScreen te, double x, double y, double z, float partialTicks, int destroyStage, float alpha) {
        if (te == null || te.func_145831_w() == null) {
            return;
        }
        IBlockState state = te.func_145831_w().func_180495_p(te.func_174877_v());
        if (!(state.func_177230_c() instanceof BlockRbmkScreen)) {
            return;
        }
        ScreenCanvas canvas = te.canvas();
        boolean powered = te.isPowered();
        Block ctrl = state.func_177230_c();
        int page = 1;
        if (ctrl instanceof BlockRbmkCustomScreen) {
            page = 3;
        } else if (ctrl instanceof BlockRbmkMonitor) {
            page = 2;
        }
        canvas.setMainStepMs(te.getRefreshMs());
        canvas.render(powered ? te.clientBootElapsedMs() : -1L, powered, te.getData(), te.getEvents(), page, powered ? te.clientFaultElapsedMs() : -1L);
        canvas.uploadIfDirty(System.currentTimeMillis());
        EnumFacing facing = (EnumFacing)state.func_177229_b(BlockRbmkScreen.FACING);
        float tilt = ScreenStructure.tiltDegrees(te.getTilt());
        double[] anchor = ScreenStructure.anchor(facing);
        GlStateManager.func_179094_E();
        GlStateManager.func_179123_a();
        GlStateManager.func_179137_b((double)(x + anchor[0]), (double)(y + anchor[1]), (double)(z + anchor[2]));
        GlStateManager.func_179114_b((float)ScreenStructure.yawOf(facing), (float)0.0f, (float)1.0f, (float)0.0f);
        float push = 1.5f * (float)Math.sin(Math.toRadians(tilt));
        GlStateManager.func_179109_b((float)0.0f, (float)0.0f, (float)(-push));
        this.drawHanger();
        GlStateManager.func_179109_b((float)0.0f, (float)1.5f, (float)0.0f);
        GlStateManager.func_179114_b((float)(-tilt), (float)1.0f, (float)0.0f, (float)0.0f);
        GlStateManager.func_179109_b((float)0.0f, (float)-1.5f, (float)0.0f);
        this.drawShell();
        this.drawPanel(canvas);
        GlStateManager.func_179099_b();
        GlStateManager.func_179121_F();
    }

    private void drawHanger() {
        Tessellator tess = Tessellator.func_178181_a();
        BufferBuilder buf = tess.func_178180_c();
        GlStateManager.func_179090_x();
        GlStateManager.func_179140_f();
        GlStateManager.func_179084_k();
        buf.func_181668_a(7, DefaultVertexFormats.field_181706_f);
        RenderDfcScreen.box(buf, -1.35f, 1.42f, -0.22f, 1.35f, 1.6f, 0.3f, -10787988);
        RenderDfcScreen.box(buf, -1.48f, 1.44f, -0.16f, -1.28f, 1.7f, 0.22f, -12235693);
        RenderDfcScreen.box(buf, 1.28f, 1.44f, -0.16f, 1.48f, 1.7f, 0.22f, -12235693);
        RenderDfcScreen.box(buf, -1.0f, 1.58f, -0.09f, -0.88f, 1.96f, 0.09f, -13551811);
        RenderDfcScreen.box(buf, 0.88f, 1.58f, -0.09f, 1.0f, 1.96f, 0.09f, -13551811);
        RenderDfcScreen.box(buf, -1.5f, 1.86f, -0.28f, 1.5f, 1.96f, 0.28f, -12235693);
        RenderDfcScreen.box(buf, -1.3f, 1.78f, -0.09f, -1.18f, 1.86f, 0.09f, -10787988);
        RenderDfcScreen.box(buf, 1.18f, 1.78f, -0.09f, 1.3f, 1.86f, 0.09f, -10787988);
        tess.func_78381_a();
    }

    private void drawShell() {
        Tessellator tess = Tessellator.func_178181_a();
        BufferBuilder buf = tess.func_178180_c();
        GlStateManager.func_179090_x();
        GlStateManager.func_179140_f();
        GlStateManager.func_179084_k();
        buf.func_181668_a(7, DefaultVertexFormats.field_181706_f);
        float e = 0.012f;
        float x0 = -2.0f + e;
        float x1 = 2.0f - e;
        float y0 = -1.5f + e;
        float y1 = 1.5f - e;
        RenderDfcScreen.box(buf, x0 + 0.02f, y0 + 0.02f, 0.075f, x1 - 0.02f, y1 - 0.02f, 0.125f, -12893880);
        float zf = -0.085f;
        float zb = 0.075f;
        RenderDfcScreen.box(buf, x0, y0, zf, x1, y0 + 0.1f, zb, -11116953);
        RenderDfcScreen.box(buf, x0, y1 - 0.1f, zf, x1, y1, zb, -11116953);
        RenderDfcScreen.box(buf, x0, y0 + 0.1f, zf, x0 + 0.1f, y1 - 0.1f, zb, -11116953);
        RenderDfcScreen.box(buf, x1 - 0.1f, y0 + 0.1f, zf, x1, y1 - 0.1f, zb, -11116953);
        RenderDfcScreen.box(buf, x0 + 0.1f - 0.014f, y0 + 0.1f - 0.014f, -0.071f, x1 - 0.1f + 0.014f, y1 - 0.1f + 0.014f, -0.064f, -14012619);
        tess.func_78381_a();
    }

    private void drawPanel(ScreenCanvas canvas) {
        if (canvas.texture() == null) {
            return;
        }
        Tessellator tess = Tessellator.func_178181_a();
        BufferBuilder buf = tess.func_178180_c();
        float x0 = -1.888f;
        float x1 = 1.888f;
        float y0 = -1.388f;
        float y1 = 1.388f;
        float zf = -0.078f;
        GlStateManager.func_179098_w();
        GlStateManager.func_179140_f();
        GlStateManager.func_179147_l();
        GlStateManager.func_179120_a((int)770, (int)771, (int)1, (int)0);
        GlStateManager.func_179131_c((float)1.0f, (float)1.0f, (float)1.0f, (float)1.0f);
        GL13.glActiveTexture((int)33984);
        GlStateManager.func_179144_i((int)canvas.texture().func_110552_b());
        buf.func_181668_a(7, DefaultVertexFormats.field_181709_i);
        float[] uv = ScreenStructure.panelUv();
        float ux0 = uv[0];
        float ux1 = uv[1];
        float vy0 = uv[2];
        float vy1 = uv[3];
        buf.func_181662_b((double)x1, (double)y0, (double)zf).func_187315_a((double)ux1, (double)vy0).func_181666_a(1.0f, 1.0f, 1.0f, 1.0f).func_181675_d();
        buf.func_181662_b((double)x0, (double)y0, (double)zf).func_187315_a((double)ux0, (double)vy0).func_181666_a(1.0f, 1.0f, 1.0f, 1.0f).func_181675_d();
        buf.func_181662_b((double)x0, (double)y1, (double)zf).func_187315_a((double)ux0, (double)vy1).func_181666_a(1.0f, 1.0f, 1.0f, 1.0f).func_181675_d();
        buf.func_181662_b((double)x1, (double)y1, (double)zf).func_187315_a((double)ux1, (double)vy1).func_181666_a(1.0f, 1.0f, 1.0f, 1.0f).func_181675_d();
        tess.func_78381_a();
        GlStateManager.func_179084_k();
        GlStateManager.func_179145_e();
    }

    private static void box(BufferBuilder buf, float x0, float y0, float z0, float x1, float y1, float z1, int argb) {
        float a = (float)(argb >>> 24 & 0xFF) / 255.0f;
        float r = (float)(argb >> 16 & 0xFF) / 255.0f;
        float g = (float)(argb >> 8 & 0xFF) / 255.0f;
        float b = (float)(argb & 0xFF) / 255.0f;
        RenderDfcScreen.v(buf, x1, y0, z0, r, g, b, a);
        RenderDfcScreen.v(buf, x0, y0, z0, r, g, b, a);
        RenderDfcScreen.v(buf, x0, y1, z0, r, g, b, a);
        RenderDfcScreen.v(buf, x1, y1, z0, r, g, b, a);
        RenderDfcScreen.v(buf, x0, y0, z1, r, g, b, a);
        RenderDfcScreen.v(buf, x1, y0, z1, r, g, b, a);
        RenderDfcScreen.v(buf, x1, y1, z1, r, g, b, a);
        RenderDfcScreen.v(buf, x0, y1, z1, r, g, b, a);
        RenderDfcScreen.v(buf, x0, y0, z0, r, g, b, a);
        RenderDfcScreen.v(buf, x0, y0, z1, r, g, b, a);
        RenderDfcScreen.v(buf, x0, y1, z1, r, g, b, a);
        RenderDfcScreen.v(buf, x0, y1, z0, r, g, b, a);
        RenderDfcScreen.v(buf, x1, y0, z1, r, g, b, a);
        RenderDfcScreen.v(buf, x1, y0, z0, r, g, b, a);
        RenderDfcScreen.v(buf, x1, y1, z0, r, g, b, a);
        RenderDfcScreen.v(buf, x1, y1, z1, r, g, b, a);
        RenderDfcScreen.v(buf, x0, y0, z1, r, g, b, a);
        RenderDfcScreen.v(buf, x1, y0, z1, r, g, b, a);
        RenderDfcScreen.v(buf, x1, y0, z0, r, g, b, a);
        RenderDfcScreen.v(buf, x0, y0, z0, r, g, b, a);
        RenderDfcScreen.v(buf, x0, y1, z0, r, g, b, a);
        RenderDfcScreen.v(buf, x1, y1, z0, r, g, b, a);
        RenderDfcScreen.v(buf, x1, y1, z1, r, g, b, a);
        RenderDfcScreen.v(buf, x0, y1, z1, r, g, b, a);
    }

    private static void v(BufferBuilder buf, float x, float y, float z, float r, float g, float b, float a) {
        buf.func_181662_b((double)x, (double)y, (double)z).func_181666_a(r, g, b, a).func_181675_d();
    }
}

