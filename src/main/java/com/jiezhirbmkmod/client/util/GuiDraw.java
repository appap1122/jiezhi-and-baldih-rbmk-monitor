package com.jiezhirbmkmod.client.util;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * v17.8 GUI 统一安全渲染中枢。
 *
 * <p>模组自家 GUI 的绘制以前各写各的：颜色有的带 0xFF alpha 有的不带，
 * 文案有的走 {@code I18n.format} 有的走 {@code translateToLocalFormatted}，
 * 居中、边线每个 GUI 自己再发明一遍。统一收口到这里，约定三条：</p>
 * <ol>
 *   <li><b>矩形类绘制必须带 alpha</b>：原版 {@code Gui.drawRect} 不给
 *       alpha 位会整块画成透明（不可见），{@code GuiDraw.rect} 对缺
 *       alpha 的颜色自动补 0xFF；刻意的半透明色（如 0x66000000 遮罩）
 *       原样放行。</li>
 *   <li><b>文本类绘制统一走 {@link #text}</b>：原版 drawString 对缺
 *       alpha 的颜色本来就会补不透明，但这里仍统一规整一遍 —— 同一条
 *       规则管两种画法，不靠记忆。</li>
 *   <li><b>界面文案统一走 {@link #safe}</b>：翻译键缺失时不把
 *       {@code gui.jiezhirbmkmod.xxx} 原样糊在玩家脸上，回退显示
 *       去掉命名空间前缀的短键 —— 玩家能看懂个大概，报错也有迹可循。</li>
 * </ol>
 *
 * <p>本类不持有状态，全部静态。</p>
 */
@SideOnly(Side.CLIENT)
public final class GuiDraw {

    private GuiDraw() {
    }

    /** 矩形：缺 alpha 的颜色自动补不透明位（刻意半透明的颜色原样放行） */
    public static void drawRect(int x1, int y1, int x2, int y2, int color) {
        if (x2 <= x1 || y2 <= y1) {
            return;
        }
        Gui.drawRect((int)x1, (int)y1, (int)x2, (int)y2, (int)GuiDraw.ensureAlpha(color));
    }

    /** 圆角矩形（实际是整数化后的普通矩形，保留旧入口语义） */
    public static void drawRoundedRect(float x, float y, float w, float h, float radius, int color) {
        if (w <= 0.0f || h <= 0.0f) {
            return;
        }
        GuiDraw.drawRect(Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h), color);
    }

    /** 水平线（矩形实现，颜色规则同 {@link #drawRect}） */
    public static void hline(int x1, int x2, int y, int color) {
        if (x2 < x1) {
            int t = x1;
            x1 = x2;
            x2 = t;
        }
        GuiDraw.drawRect(x1, y, x2 + 1, y + 1, color);
    }

    /** 垂直线（矩形实现，颜色规则同 {@link #drawRect}） */
    public static void vline(int x, int y1, int y2, int color) {
        if (y2 < y1) {
            int t = y1;
            y1 = y2;
            y2 = t;
        }
        GuiDraw.drawRect(x, y1, x + 1, y2 + 1, color);
    }

    /** 文本：统一规整 alpha 再画，shadow 由调用方在需要时用 drawStringWithShadow */
    public static void text(FontRenderer fr, String s, int x, int y, int color) {
        if (fr == null || s == null || s.isEmpty()) {
            return;
        }
        fr.drawString(s, x, y, GuiDraw.ensureAlpha(color));
    }

    /** 居中文本：cx 为面板中心 x 坐标 */
    public static void textCentered(FontRenderer fr, String s, int cx, int y, int color) {
        if (fr == null || s == null || s.isEmpty()) {
            return;
        }
        fr.drawString(s, cx - fr.getStringWidth(s) / 2, y, GuiDraw.ensureAlpha(color));
    }

    /**
     * 安全文案：翻译键缺失时回退显示去命名空间前缀的短键，
     * 不把完整键名原样糊在界面上。
     */
    public static String safe(String key) {
        return GuiDraw.safe(key, new Object[0]);
    }

    /** 带参数的 {@link #safe} */
    public static String safe(String key, Object... args) {
        String s = I18n.format(key, args);
        if (s == null || s.isEmpty() || s.equals(key)) {
            int dot = key.indexOf('.');
            return dot >= 0 && dot < key.length() - 1 ? key.substring(dot + 1) : key;
        }
        return s;
    }

    /** alpha 位为 0 的颜色补成不透明；带 alpha 的颜色原样返回 */
    public static int ensureAlpha(int color) {
        return (color & 0xFF000000) == 0 ? color | 0xFF000000 : color;
    }

    /** 旧入口：按比例调制 alpha（保留既有调用语义不变） */
    public static int withAlpha(int color, float alpha) {
        if (alpha <= 0.0f) {
            return color & 0xFFFFFF;
        }
        int base = color >>> 24 & 0xFF;
        int a = Math.max(0, Math.min(255, Math.round((float)base * Math.min(1.0f, alpha))));
        return a << 24 | color & 0xFFFFFF;
    }
}
