package com.jiezhirbmkmod.client;

import com.jiezhirbmkmod.client.ClientJetState;
import com.jiezhirbmkmod.client.util.GuiDraw;
import com.jiezhirbmkmod.item.JetpackEffect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(value=Side.CLIENT)
public final class JetHeatBarRenderer {
    private static final int BAR_W = 60;
    private static final int BAR_H = 5;
    private static final int CROSSHAIR_GAP = 22;

    private JetHeatBarRenderer() {
    }

    public static void draw(Minecraft mc) {
        if (mc == null || mc.player == null || mc.gameSettings == null) {
            return;
        }
        if (mc.gameSettings.hideGUI) {
            return;
        }
        EntityPlayerSP player = mc.player;
        if (!JetpackEffect.hasJetpack((EntityPlayer)player) || !JetpackEffect.hasSwitch((EntityPlayer)player)) {
            return;
        }
        float ratio = ClientJetState.heatRatio((EntityPlayer)player);
        if (ratio <= 0.0f) {
            return;
        }
        boolean overheated = ratio >= 0.999f;
        ScaledResolution sr = new ScaledResolution(mc);
        int x = sr.getScaledWidth() / 2 - 30;
        int y = sr.getScaledHeight() / 2 + 22;
        float[] rgb = JetHeatBarRenderer.colorFor(ratio, overheated);
        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate((int)770, (int)771, (int)1, (int)0);
        GuiDraw.drawRect(x - 1, y - 1, x + 60 + 1, y + 5 + 1, -1342177280);
        int fill = Math.round(60.0f * Math.max(0.0f, Math.min(1.0f, ratio)));
        if (fill > 0) {
            int a = overheated ? JetHeatBarRenderer.blinkAlpha(mc) : 235;
            GuiDraw.drawRect(x, y, x + fill, y + 5, a << 24 | (int)(rgb[0] * 255.0f) << 16 | (int)(rgb[1] * 255.0f) << 8 | (int)(rgb[2] * 255.0f));
        }
        JetHeatBarRenderer.drawOutline(x, y, 60, 5, -16777216);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    private static float[] colorFor(float ratio, boolean overheated) {
        float g;
        if (overheated) {
            return new float[]{1.0f, 0.25f, 0.2f};
        }
        float r = ratio;
        float b = 0.15f;
        if (ratio < 0.5f) {
            g = ratio * 2.0f;
        } else {
            g = 1.0f - (ratio - 0.5f) * 1.2f;
            if (g < 0.35f) {
                g = 0.35f;
            }
        }
        return new float[]{r, g, b};
    }

    private static int blinkAlpha(Minecraft mc) {
        long ms = System.currentTimeMillis() % 1000L;
        return ms < 500L ? 115 : 255;
    }

    private static void drawOutline(int x, int y, int w, int h, int color) {
        GuiDraw.drawRect(x, y, x + w, y + 1, color);
        GuiDraw.drawRect(x, y + h - 1, x + w, y + h, color);
        GuiDraw.drawRect(x, y, x + 1, y + h, color);
        GuiDraw.drawRect(x + w - 1, y, x + w, y + h, color);
    }
}

