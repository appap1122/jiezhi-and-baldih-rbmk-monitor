package com.jiezhirbmkmod.client.util;

import net.minecraft.client.gui.Gui;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(value=Side.CLIENT)
public final class GuiDraw {
    private static boolean loggedOnce = false;

    private GuiDraw() {
    }

    public static void drawRect(int x1, int y1, int x2, int y2, int color) {
        if (x2 <= x1 || y2 <= y1) {
            return;
        }
        Gui.drawRect((int)x1, (int)y1, (int)x2, (int)y2, (int)color);
    }

    public static void drawRoundedRect(float x, float y, float w, float h, float radius, int color) {
        if (w <= 0.0f || h <= 0.0f) {
            return;
        }
        GuiDraw.drawRect(Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h), color);
    }

    public static int withAlpha(int color, float alpha) {
        if (alpha <= 0.0f) {
            return color & 0xFFFFFF;
        }
        int base = color >>> 24 & 0xFF;
        int a = Math.max(0, Math.min(255, Math.round((float)base * Math.min(1.0f, alpha))));
        return a << 24 | color & 0xFFFFFF;
    }
}

