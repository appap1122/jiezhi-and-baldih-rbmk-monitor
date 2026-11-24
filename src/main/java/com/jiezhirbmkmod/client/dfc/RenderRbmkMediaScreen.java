package com.jiezhirbmkmod.client.dfc;

import com.jiezhirbmkmod.block.BlockRbmkScreen;
import com.jiezhirbmkmod.block.TileRbmkMediaScreen;
import com.jiezhirbmkmod.block.RealPower;
import com.jiezhirbmkmod.dfc.ScreenStructure;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

/**
 * 4x3 video panel. The whole frame is split into 12 tiles (4x3) with their own
 * UVs so the picture stays seamless across the multiblock, regardless of the
 * screen facing / tilt.
 */
@SideOnly(Side.CLIENT)
public class RenderRbmkMediaScreen
extends TileEntitySpecialRenderer<TileRbmkMediaScreen> {

    private static final float X0 = -1.888f;
    private static final float X1 = 1.888f;
    private static final float Y0 = -1.388f;
    private static final float Y1 = 1.388f;
    private static final float Z = -0.077f;
    private static final int COLS = 4;
    private static final int ROWS = 3;

    @Override
    public void render(TileRbmkMediaScreen te, double x, double y, double z,
            float partialTicks, int destroyStage, float alpha) {
        // null first: the mains check below dereferences te
        if (te == null || te.getWorld() == null) {
            return;
        }
        // v17.4 real editions: no mains, no picture
        if (te.isRealScreen() && te.getRealPowerState() != RealPower.ON) {
            return;
        }

        IBlockState state = te.getWorld().getBlockState(te.getPos());
        if (!(state.getBlock() instanceof BlockRbmkScreen)) {
            return;
        }
        EnumFacing facing = state.getValue(BlockRbmkScreen.FACING);
        if (te instanceof com.jiezhirbmkmod.block.TileRbmkPptScreen) {
            com.jiezhirbmkmod.block.TileRbmkPptScreen ppt =
                    (com.jiezhirbmkmod.block.TileRbmkPptScreen) te;
            if (ppt.isPictureMode()) {
                RenderRbmkMediaScreen.renderPicture(ppt, x, y, z, facing);
            } else {
                RenderRbmkMediaScreen.renderPpt(ppt, x, y, z, facing);
            }
            return;
        }
        BufferedImage image = te.pollMediaFrame();
        int tex = te.getMediaTexture();
        if (image != null) {
            if (tex <= 0) {
                tex = GL11.glGenTextures();
                te.setMediaTexture(tex);
            }
            RenderRbmkMediaScreen.upload(tex, image);
        }

        GlStateManager.pushMatrix();
        GlStateManager.pushAttrib();
        float tilt = ScreenStructure.tiltDegrees(te.getTilt());
        double[] anchor = ScreenStructure.anchor(facing);
        GlStateManager.translate((double) (x + anchor[0]), (double) (y + anchor[1]), (double) (z + anchor[2]));
        GlStateManager.rotate((float) ScreenStructure.yawOf(facing), 0.0f, 1.0f, 0.0f);
        float push = 1.5f * (float) Math.sin(Math.toRadians(tilt));
        GlStateManager.translate(0.0f, 0.0f, -push);
        GlStateManager.translate(0.0f, 1.5f, 0.0f);
        GlStateManager.rotate(-tilt, 1.0f, 0.0f, 0.0f);
        GlStateManager.translate(0.0f, -1.5f, 0.0f);
        RenderRbmkMediaScreen.drawBackdrop();
        if (tex > 0) {
            RenderRbmkMediaScreen.drawFrame(tex);
        }
        GlStateManager.popAttrib();
        GlStateManager.popMatrix();
    }

    /**
     * v17.1 PPT frame: identical 4x3 cell split / UV mapping as the video path
     * (12 cells x own UV), but the texture is the current slide PNG loaded from
     * .minecraft/rbmk_media/ppt/slide_X.png and cached until the page changes.
     */
    private static void renderPpt(com.jiezhirbmkmod.block.TileRbmkPptScreen te,
            double x, double y, double z, EnumFacing facing) {
        int tex = RenderRbmkMediaScreen.ensurePptTexture(te);
        if (tex <= 0) {
            tex = RenderRbmkMediaScreen.noSlideTexture();
        }
        GlStateManager.pushMatrix();
        GlStateManager.pushAttrib();
        float tilt = ScreenStructure.tiltDegrees(te.getTilt());
        double[] anchor = ScreenStructure.anchor(facing);
        GlStateManager.translate((double) (x + anchor[0]), (double) (y + anchor[1]), (double) (z + anchor[2]));
        GlStateManager.rotate((float) ScreenStructure.yawOf(facing), 0.0f, 1.0f, 0.0f);
        float push = 1.5f * (float) Math.sin(Math.toRadians(tilt));
        GlStateManager.translate(0.0f, 0.0f, -push);
        GlStateManager.translate(0.0f, 1.5f, 0.0f);
        GlStateManager.rotate(-tilt, 1.0f, 0.0f, 0.0f);
        GlStateManager.translate(0.0f, -1.5f, 0.0f);
        RenderRbmkMediaScreen.drawBackdrop();
        if (tex > 0) {
            RenderRbmkMediaScreen.drawFrame(tex);
        }
        GlStateManager.popAttrib();
        GlStateManager.popMatrix();
    }

    /** slide texture cache: rebuilt only when currentSlide changes */
    private static int ensurePptTexture(com.jiezhirbmkmod.block.TileRbmkPptScreen te) {
        int slide = te.getCurrentSlide();
        if (te.getPptTextureSlide() == slide) {
            return te.getPptTextureId();
        }
        java.io.File dir = new java.io.File(
                net.minecraft.client.Minecraft.getMinecraft().gameDir,
                "rbmk_media" + java.io.File.separator + "ppt");
        java.io.File file = new java.io.File(dir, "slide_" + slide + ".png");
        java.awt.image.BufferedImage img = null;
        if (file.isFile()) {
            try {
                img = javax.imageio.ImageIO.read(file);
            }
            catch (Throwable ignored) {
                img = null;
            }
        }
        if (img == null) {
            if (te.getPptTextureId() > 0) {
                org.lwjgl.opengl.GL11.glDeleteTextures(te.getPptTextureId());
            }
            te.setPptTexture(0, slide, null);
            return 0;
        }
        if (te.getPptTextureId() > 0) {
            org.lwjgl.opengl.GL11.glDeleteTextures(te.getPptTextureId());
        }
        net.minecraft.client.renderer.texture.DynamicTexture dyn =
                new net.minecraft.client.renderer.texture.DynamicTexture(img);
        int id = dyn.getGlTextureId();
        te.setPptTexture(id, slide, dyn);
        return id;
    }

    private static int noSlideTex = -1;
    private static Object noSlideDynamic;

    /**
     * Built-in "[NO SLIDE]" placeholder so a missing slide_X.png can never
     * throw or leave the panel blank.
     */
    private static int noSlideTexture() {
        if (RenderRbmkMediaScreen.noSlideTex >= 0) {
            return RenderRbmkMediaScreen.noSlideTex;
        }
        try {
            int w = 512;
            int h = 384;
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(
                    w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = img.createGraphics();
            g.setColor(new java.awt.Color(24, 0, 0));
            g.fillRect(0, 0, w, h);
            g.setColor(new java.awt.Color(200, 30, 30));
            g.setStroke(new java.awt.BasicStroke(10.0f));
            g.drawRect(8, 8, w - 16, h - 16);
            g.setColor(new java.awt.Color(235, 45, 45));
            g.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, 64));
            java.awt.FontMetrics fm = g.getFontMetrics();
            String label = "[NO SLIDE]";
            g.drawString(label, (w - fm.stringWidth(label)) / 2, h / 2 + fm.getAscent() / 3);
            g.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, 22));
            java.awt.FontMetrics fm2 = g.getFontMetrics();
            String hint = "put slide_X.png into rbmk_media/ppt/";
            g.drawString(hint, (w - fm2.stringWidth(hint)) / 2, h / 2 + 70);
            g.dispose();
            net.minecraft.client.renderer.texture.DynamicTexture dyn =
                    new net.minecraft.client.renderer.texture.DynamicTexture(img);
            RenderRbmkMediaScreen.noSlideDynamic = dyn;
            RenderRbmkMediaScreen.noSlideTex = dyn.getGlTextureId();
        }
        catch (Throwable t) {
            RenderRbmkMediaScreen.noSlideTex = 0;
        }
        return RenderRbmkMediaScreen.noSlideTex;
    }

    /** generic picture viewer: one image, aspect preserved with black bars */
    private static void renderPicture(com.jiezhirbmkmod.block.TileRbmkPptScreen te,
            double x, double y, double z, EnumFacing facing) {
        int tex = RenderRbmkMediaScreen.ensurePictureTexture(te);
        GlStateManager.pushMatrix();
        GlStateManager.pushAttrib();
        float tilt = ScreenStructure.tiltDegrees(te.getTilt());
        double[] anchor = ScreenStructure.anchor(facing);
        GlStateManager.translate((double) (x + anchor[0]), (double) (y + anchor[1]), (double) (z + anchor[2]));
        GlStateManager.rotate((float) ScreenStructure.yawOf(facing), 0.0f, 1.0f, 0.0f);
        float push = 1.5f * (float) Math.sin(Math.toRadians(tilt));
        GlStateManager.translate(0.0f, 0.0f, -push);
        GlStateManager.translate(0.0f, 1.5f, 0.0f);
        GlStateManager.rotate(-tilt, 1.0f, 0.0f, 0.0f);
        GlStateManager.translate(0.0f, -1.5f, 0.0f);
        RenderRbmkMediaScreen.drawBackdrop();
        if (tex > 0) {
            RenderRbmkMediaScreen.drawPictureQuad(tex, te.getPictureAspect());
        } else if (!te.getPictureFile().isEmpty()) {
            // file vanished between sync and render: never blank/throw
            int fallback = RenderRbmkMediaScreen.noSlideTexture();
            if (fallback > 0) {
                RenderRbmkMediaScreen.drawFrame(fallback);
            }
        }
        GlStateManager.popAttrib();
        GlStateManager.popMatrix();
    }

    /** letterboxed quad inside the 4x3 frame (aspect preserved, black bars) */
    private static void drawPictureQuad(int tex, float aspect) {
        float panelW = X1 - X0;
        float panelH = Y1 - Y0;
        float a = aspect <= 0.0f ? 1.0f : aspect;
        float w = panelW;
        float h = panelW / a;
        if (h > panelH) {
            h = panelH;
            w = panelH * a;
        }
        float cx = (X0 + X1) * 0.5f;
        float cy = (Y0 + Y1) * 0.5f;
        float x0 = cx - w * 0.5f;
        float x1 = cx + w * 0.5f;
        float y0 = cy - h * 0.5f;
        float y1 = cy + h * 0.5f;
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        GlStateManager.enableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        GlStateManager.bindTexture(tex);
        buf.begin(7, DefaultVertexFormats.POSITION_TEX_COLOR);
        RenderRbmkMediaScreen.uv(buf, x1, y0, 1.0f, 1.0f);
        RenderRbmkMediaScreen.uv(buf, x0, y0, 0.0f, 1.0f);
        RenderRbmkMediaScreen.uv(buf, x0, y1, 0.0f, 0.0f);
        RenderRbmkMediaScreen.uv(buf, x1, y1, 1.0f, 0.0f);
        tess.draw();
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
    }

    /** picture texture cache, keyed by file name; 0 = known missing */
    private static int ensurePictureTexture(com.jiezhirbmkmod.block.TileRbmkPptScreen te) {
        String name = te.getPictureFile();
        if (name.isEmpty()) {
            return 0;
        }
        if (name.equals(te.getPictureTextureName()) && te.getPictureTextureId() >= 0) {
            return te.getPictureTextureId();
        }
        java.io.File file = new java.io.File(
                com.jiezhirbmkmod.block.TileRbmkPptScreen.picturesDir(), name);
        java.awt.image.BufferedImage img = null;
        if (file.isFile()) {
            try {
                img = javax.imageio.ImageIO.read(file);
            }
            catch (Throwable ignored) {
                img = null;
            }
        }
        if (img == null) {
            if (te.getPictureTextureId() > 0) {
                org.lwjgl.opengl.GL11.glDeleteTextures(te.getPictureTextureId());
            }
            te.setPictureTexture(0, name, null, 1.0f);
            return 0;
        }
        if (te.getPictureTextureId() > 0) {
            org.lwjgl.opengl.GL11.glDeleteTextures(te.getPictureTextureId());
        }
        float aspect = img.getHeight() <= 0 ? 1.0f : (float) img.getWidth() / (float) img.getHeight();
        net.minecraft.client.renderer.texture.DynamicTexture dyn =
                new net.minecraft.client.renderer.texture.DynamicTexture(img);
        int id = dyn.getGlTextureId();
        te.setPictureTexture(id, name, dyn, aspect);
        return id;
    }

    private static void drawBackdrop() {        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableBlend();
        buf.begin(7, DefaultVertexFormats.POSITION_COLOR);
        RenderRbmkMediaScreen.vertex(buf, X1, Y0, Z + 0.002f, 0.02f, 0.03f, 0.02f, 1.0f);
        RenderRbmkMediaScreen.vertex(buf, X0, Y0, Z + 0.002f, 0.02f, 0.03f, 0.02f, 1.0f);
        RenderRbmkMediaScreen.vertex(buf, X0, Y1, Z + 0.002f, 0.02f, 0.03f, 0.02f, 1.0f);
        RenderRbmkMediaScreen.vertex(buf, X1, Y1, Z + 0.002f, 0.02f, 0.03f, 0.02f, 1.0f);
        tess.draw();
    }

    /** one textured quad per multiblock cell, UVs computed from the full frame */
    private static void drawFrame(int tex) {
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        GlStateManager.enableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        GlStateManager.bindTexture(tex);
        buf.begin(7, DefaultVertexFormats.POSITION_TEX_COLOR);
        for (int row = 0; row < ROWS; ++row) {
            float v0 = (float) row / (float) ROWS;
            float v1 = (float) (row + 1) / (float) ROWS;
            float yTop = Y1 - (Y1 - Y0) * (float) row / (float) ROWS;
            float yBottom = Y1 - (Y1 - Y0) * (float) (row + 1) / (float) ROWS;
            for (int col = 0; col < COLS; ++col) {
                float u0 = (float) col / (float) COLS;
                float u1 = (float) (col + 1) / (float) COLS;
                float xLeft = X0 + (X1 - X0) * (float) col / (float) COLS;
                float xRight = X0 + (X1 - X0) * (float) (col + 1) / (float) COLS;
                RenderRbmkMediaScreen.uv(buf, xRight, yBottom, u1, v1);
                RenderRbmkMediaScreen.uv(buf, xLeft, yBottom, u0, v1);
                RenderRbmkMediaScreen.uv(buf, xLeft, yTop, u0, v0);
                RenderRbmkMediaScreen.uv(buf, xRight, yTop, u1, v0);
            }
        }
        tess.draw();
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
    }

    private static void uv(BufferBuilder buf, float x, float y, float u, float v) {
        buf.pos((double) x, (double) y, (double) Z).tex((double) u, (double) v)
                .color(1.0f, 1.0f, 1.0f, 1.0f).endVertex();
    }

    private static void vertex(BufferBuilder buf, float x, float y, float z,
            float r, float g, float b, float a) {
        buf.pos((double) x, (double) y, (double) z).color(r, g, b, a).endVertex();
    }

    private static void upload(int tex, BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        int[] pixels = image.getRGB(0, 0, w, h, null, 0, w);
        ByteBuffer buffer = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder());
        for (int p : pixels) {
            buffer.put((byte) (p >> 16));
            buffer.put((byte) (p >> 8));
            buffer.put((byte) p);
            buffer.put((byte) (p >>> 24));
        }
        buffer.flip();
        GlStateManager.bindTexture(tex);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, w, h, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
    }
}
