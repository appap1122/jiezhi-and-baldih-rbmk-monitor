package com.jiezhirbmkmod.dfc;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/**
 * v18.2 自定义页布局（布局编辑器的数据模型）。
 *
 * <p>自定义页从「固定排布」升级为可编辑布局：仪表块（watch 槽位读数 +
 * 比例条）、曲线块（历史通道迷你曲线）、文本块三类小部件，自由摆放、
 * 缩放，坐标基于 256×192 虚拟屏（与 canvas 逻辑分辨率一致）。
 * 布局随屏存档走（NBT），编辑器支持导出/导入（Base64 文本串，
 * 可粘贴给朋友）。widgets 为空 = 未编辑过，走旧版固定排布，兼容旧存档。</p>
 */
public final class CustomWidgetLayout {

    public static final int TYPE_GAUGE = 0;
    public static final int TYPE_GRAPH = 1;
    public static final int TYPE_TEXT = 2;
    public static final int TYPE_COUNT = 3;
    /** 数量上限 */
    public static final int MAX_WIDGETS = 12;
    /** 虚拟屏逻辑分辨率（与 canvas 一致） */
    public static final int CANVAS_W = 256;
    public static final int CANVAS_H = 192;
    /** 最小尺寸（拖缩不小于此） */
    public static final int MIN_W = 24;
    public static final int MIN_H = 14;

    public static final class Widget {
        public int type = TYPE_GAUGE;
        /** 仪表/文本块的数据槽位（0~7）；曲线块不用 */
        public int slot = 0;
        /** 曲线块的通道（0=柱温 1=通量 2=水量 3=蒸汽 4=富集 5=氙） */
        public int channel = 0;
        public int x = 8;
        public int y = 8;
        public int w = 116;
        public int h = 30;

        public Widget copy() {
            Widget c = new Widget();
            c.type = this.type;
            c.slot = this.slot;
            c.channel = this.channel;
            c.x = this.x;
            c.y = this.y;
            c.w = this.w;
            c.h = this.h;
            return c;
        }

        public void clamp() {
            this.type = Math.max(0, Math.min(TYPE_COUNT - 1, this.type));
            this.slot = Math.max(0, Math.min(7, this.slot));
            this.channel = Math.max(0, Math.min(5, this.channel));
            this.w = Math.max(MIN_W, Math.min(CANVAS_W, this.w));
            this.h = Math.max(MIN_H, Math.min(CANVAS_H, this.h));
            this.x = Math.max(0, Math.min(CANVAS_W - this.w, this.x));
            this.y = Math.max(0, Math.min(CANVAS_H - this.h, this.y));
        }
    }

    private final List<Widget> widgets = new ArrayList<Widget>();

    public List<Widget> widgets() {
        return this.widgets;
    }

    public boolean isEmpty() {
        return this.widgets.isEmpty();
    }

    public CustomWidgetLayout copy() {
        CustomWidgetLayout c = new CustomWidgetLayout();
        for (Widget w : this.widgets) {
            c.widgets.add(w.copy());
        }
        return c;
    }

    public void clear() {
        this.widgets.clear();
    }

    public boolean add(Widget w) {
        if (w == null || this.widgets.size() >= MAX_WIDGETS) {
            return false;
        }
        w.clamp();
        this.widgets.add(w);
        return true;
    }

    public Widget widgetAt(int vx, int vy) {
        // 后添加的在上层：从前往后找第一个命中的
        for (Widget w : this.widgets) {
            if (vx >= w.x && vx < w.x + w.w && vy >= w.y && vy < w.y + w.h) {
                return w;
            }
        }
        return null;
    }

    // ---- NBT ----

    private static final String KEY_ROOT = "CustomLayout";
    private static final String KEY_COUNT = "CWCount";

    public void readNBT(NBTTagCompound c) {
        this.widgets.clear();
        if (c == null || !c.hasKey(KEY_ROOT)) {
            return;
        }
        NBTTagCompound root = c.getCompoundTag(KEY_ROOT);
        int n = root.hasKey(KEY_COUNT) ? Math.min(MAX_WIDGETS, root.getInteger(KEY_COUNT)) : 0;
        for (int i = 0; i < n; ++i) {
            NBTTagCompound wc = root.getCompoundTag("W" + i);
            Widget w = new Widget();
            w.type = wc.getInteger("Type");
            w.slot = wc.getInteger("Slot");
            w.channel = wc.getInteger("Chan");
            w.x = wc.getInteger("X");
            w.y = wc.getInteger("Y");
            w.w = wc.getInteger("W");
            w.h = wc.getInteger("H");
            w.clamp();
            this.widgets.add(w);
        }
    }

    public void writeNBT(NBTTagCompound c) {
        if (this.widgets.isEmpty()) {
            return; // 空布局不写键：存档保持旧格式
        }
        NBTTagCompound root = new NBTTagCompound();
        root.setInteger(KEY_COUNT, this.widgets.size());
        for (int i = 0; i < this.widgets.size(); ++i) {
            Widget w = this.widgets.get(i);
            NBTTagCompound wc = new NBTTagCompound();
            wc.setInteger("Type", w.type);
            wc.setInteger("Slot", w.slot);
            wc.setInteger("Chan", w.channel);
            wc.setInteger("X", w.x);
            wc.setInteger("Y", w.y);
            wc.setInteger("W", w.w);
            wc.setInteger("H", w.h);
            root.setTag("W" + i, wc);
        }
        c.setTag(KEY_ROOT, root);
    }

    // ---- 导出 / 导入（Base64 文本串，可粘贴）----

    /** 导出为可粘贴文本（Base64(NBT)），失败返回 null */
    public String exportString() {
        try {
            NBTTagCompound c = new NBTTagCompound();
            this.writeNBT(c);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            net.minecraft.nbt.CompressedStreamTools.writeCompressed(c, out);
            return java.util.Base64.getEncoder().encodeToString(out.toByteArray());
        }
        catch (Throwable t) {
            return null;
        }
    }

    /** 从粘贴文本导入；失败返回 false（当前布局不变） */
    public boolean importString(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        try {
            byte[] data = java.util.Base64.getDecoder().decode(s.trim());
            NBTTagCompound c = net.minecraft.nbt.CompressedStreamTools.readCompressed(
                    new java.io.ByteArrayInputStream(data));
            CustomWidgetLayout tmp = new CustomWidgetLayout();
            tmp.readNBT(c);
            if (tmp.widgets.isEmpty()) {
                return false;
            }
            this.widgets.clear();
            for (Widget w : tmp.widgets) {
                this.widgets.add(w.copy());
            }
            return true;
        }
        catch (Throwable t) {
            return false;
        }
    }
}
