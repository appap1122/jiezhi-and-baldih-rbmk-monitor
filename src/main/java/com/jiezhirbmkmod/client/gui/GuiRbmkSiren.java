package com.jiezhirbmkmod.client.gui;


import com.jiezhirbmkmod.network.NetGuard;
import com.jiezhirbmkmod.block.TileRbmkSiren;
import com.jiezhirbmkmod.client.util.GuiDraw;
import com.jiezhirbmkmod.dfc.SirenPresets;
import com.jiezhirbmkmod.item.ModSounds;
import com.jiezhirbmkmod.network.ModNetwork;
import com.jiezhirbmkmod.network.msg.MessageSirenTune;
import java.io.IOException;
import java.util.Locale;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class GuiRbmkSiren extends GuiScreen {

    private static final int W = 250;
    private static final int H = 270;
    private static final int M = 8;
    private static final int BH = 14;

    private static final int Y_STATUS = 18;
    private static final int Y_PALETTE = 34;
    private static final int Y_SOUND_L = 78, Y_SOUND = 86;
    private static final int Y_VOL_L = 106, Y_VOL = 114;
    private static final int Y_PITCH = 134;
    private static final int Y_GAP_L = 154, Y_GAP = 162;
    private static final int Y_MODE_L = 182, Y_MODE = 190;
    private static final int Y_CAT_L = 210, Y_CAT = 218;
    private static final int Y_NOTE = 238;
    private static final int Y_SAVE = 248;

    private static final int ID_SOUND = 200;      // +0..COUNT-1
    private static final int ID_VOLUME = 300;     // +0..3
    private static final int ID_GAP = 400;        // +0..3
    private static final int ID_MODE = 500;       // +0..2
    private static final int ID_CAT = 600;        // +0..2
    private static final int ID_PITCH_DOWN = 700;
    private static final int ID_PITCH_UP = 701;
    private static final int ID_FLASH = 702;
    private static final int ID_PREVIEW = 800;
    private static final int ID_SAVE = 801;
    private static final int ID_CLOSE = 802;

    private static final int PAL_X = M, PAL_Y = Y_PALETTE, PAL_W = 25, PAL_H = 18, PAL_GAP = 2, PAL_COLS = 8;

    private static final float[] VOLUMES = new float[]{0.5F, 1.0F, 2.0F, 4.0F};
    private static final String[] VOLUME_LABELS = new String[]{"很轻", "正常", "较响", "全基地"};
    private static final int[] GAPS = new int[]{0, 20, 60, 120};
    private static final String[] GAP_LABELS = new String[]{"连响", "1秒", "3秒", "6秒"};

    private final TileRbmkSiren tile;
    private final BlockPos pos;

    private int colorMeta;
    private int preset;
    private int volumeTier;
    private float pitch;
    private int gapTier;
    private int mode;
    private int category;
    private boolean flash;
    private boolean dirty;

    public GuiRbmkSiren(TileRbmkSiren tile) {
        this.tile = tile;
        this.pos = tile.getPos();
        this.colorMeta = tile.getColor().ordinal();
        this.preset = tile.getPreset();
        this.volumeTier = nearest(VOLUMES, tile.getVolume());
        this.pitch = SirenPresets.clampPitch(tile.getPitch());
        this.gapTier = nearestInt(GAPS, tile.getGapTicks());
        this.mode = SirenPresets.clampMode(tile.getMode());
        this.category = SirenPresets.clampCat(tile.getCategory());
        this.flash = tile.isFlashLight();
    }

    private static int nearest(float[] tiers, float v) {
        int best = 0;
        float bd = Float.MAX_VALUE;
        for (int i = 0; i < tiers.length; ++i) {
            float d = Math.abs(tiers[i] - v);
            if (d < bd) {
                bd = d;
                best = i;
            }
        }
        return best;
    }

    private static int nearestInt(int[] tiers, int v) {
        int best = 0;
        int bd = Integer.MAX_VALUE;
        for (int i = 0; i < tiers.length; ++i) {
            int d = Math.abs(tiers[i] - v);
            if (d < bd) {
                bd = d;
                best = i;
            }
        }
        return best;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void initGui() {
        super.initGui();
        int px = this.left();
        int py = this.top();
        for (int i = 0; i < SirenPresets.COUNT; ++i) {
            this.buttonList.add(new GuiButton(ID_SOUND + i, px + M + i * 44, py + Y_SOUND, 42, BH, SirenPresets.label(i)));
        }
        for (int i = 0; i < VOLUMES.length; ++i) {
            this.buttonList.add(new GuiButton(ID_VOLUME + i, px + M + i * 33, py + Y_VOL, 31, BH, VOLUME_LABELS[i]));
        }
        this.buttonList.add(new GuiButton(ID_PITCH_DOWN, px + M + 34, py + Y_PITCH, 16, BH, "-"));
        this.buttonList.add(new GuiButton(ID_PITCH_UP, px + M + 106, py + Y_PITCH, 16, BH, "+"));
        for (int i = 0; i < GAPS.length; ++i) {
            this.buttonList.add(new GuiButton(ID_GAP + i, px + M + i * 33, py + Y_GAP, 31, BH, GAP_LABELS[i]));
        }
        this.buttonList.add(new GuiButton(ID_FLASH, px + M + 142, py + Y_GAP, 88, BH, ""));
        for (int i = 0; i < SirenPresets.MODE_COUNT; ++i) {
            this.buttonList.add(new GuiButton(ID_MODE + i, px + M + i * 44, py + Y_MODE, 42, BH, SirenPresets.modeLabel(i)));
        }
        this.buttonList.add(new GuiButton(ID_PREVIEW, px + M + 140, py + Y_MODE, 62, BH, "试听"));
        for (int i = 0; i < SirenPresets.CAT_COUNT; ++i) {
            this.buttonList.add(new GuiButton(ID_CAT + i, px + M + i * 52, py + Y_CAT, 50, BH, SirenPresets.catLabel(i)));
        }
        this.buttonList.add(new GuiButton(ID_CLOSE, px + W - M - 50, py + Y_SAVE, 50, BH + 2, "关闭"));
        this.buttonList.add(new GuiButton(ID_SAVE, px + W - M - 106, py + Y_SAVE, 50, BH + 2, "保存"));
        this.syncLabels();
    }

    private int left() {
        return (this.width - W) / 2;
    }

    private int top() {
        return Math.max(4, (this.height - H) / 2);
    }

    private void syncLabels() {
        GuiButton f = this.byId(ID_FLASH);
        if (f != null) {
            f.displayString = "闪灯 " + (this.flash ? "开" : "关");
        }
    }

    private GuiButton byId(int id) {
        for (GuiButton b : this.buttonList) {
            if (b.id == id) {
                return b;
            }
        }
        return null;
    }

    @Override
    protected void mouseClicked(int mx, int my, int mouseButton) throws IOException {
        super.mouseClicked(mx, my, mouseButton);
        if (mouseButton != 0) {
            return;
        }
        int i = this.paletteAt(mx, my);
        if (i >= 0 && i != this.colorMeta) {
            this.colorMeta = i;
            this.dirty = true;
        }
    }

    private int paletteAt(int mx, int my) {
        int px = this.left() + PAL_X;
        int py = this.top() + PAL_Y;
        int cw = PAL_W + PAL_GAP, ch = PAL_H + PAL_GAP;
        if (mx < px || my < py || mx > px + cw * PAL_COLS || my > py + ch * 2) {
            return -1;
        }
        int col = (mx - px) / cw;
        int row = (my - py) / ch;
        int idx = row * PAL_COLS + col;
        EnumDyeColor[] all = EnumDyeColor.values();
        if (col < 0 || col >= PAL_COLS || row < 0 || row > 1 || idx < 0 || idx >= all.length) {
            return -1;
        }
        int x0 = px + col * cw, y0 = py + row * ch;
        return (mx <= x0 + PAL_W && my <= y0 + PAL_H) ? idx : -1;
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        int id = button.id;
        if (id >= ID_SOUND && id < ID_SOUND + SirenPresets.COUNT) {
            this.preset = id - ID_SOUND;
        } else if (id >= ID_VOLUME && id < ID_VOLUME + VOLUMES.length) {
            this.volumeTier = id - ID_VOLUME;
        } else if (id >= ID_GAP && id < ID_GAP + GAPS.length) {
            this.gapTier = id - ID_GAP;
        } else if (id >= ID_MODE && id < ID_MODE + SirenPresets.MODE_COUNT) {
            this.mode = id - ID_MODE;
        } else if (id >= ID_CAT && id < ID_CAT + SirenPresets.CAT_COUNT) {
            this.category = id - ID_CAT;
        } else if (id == ID_PITCH_DOWN || id == ID_PITCH_UP) {
            this.pitch = SirenPresets.clampPitch(this.pitch + (id == ID_PITCH_UP ? 0.05F : -0.05F));
        } else if (id == ID_FLASH) {
            this.flash = !this.flash;
        } else if (id == ID_PREVIEW) {
            this.preview();
            return;
        } else if (id == ID_SAVE) {
            this.save();
            return;
        } else if (id == ID_CLOSE) {
            this.mc.player.closeScreen();
            return;
        } else {
            return;
        }
        this.dirty = true;
        this.syncLabels();
    }

    private float volume() {
        return SirenPresets.clampVolume(VOLUMES[this.volumeTier]);
    }

    private int gap() {
        return SirenPresets.clampGap(GAPS[this.gapTier]);
    }

    private void preview() {
        this.mc.getSoundHandler().playSound(
                (net.minecraft.client.audio.ISound) new net.minecraft.client.audio.PositionedSoundRecord(
                        ModSounds.siren(this.preset), SoundCats.of(this.category),
                        this.volume(), this.pitch, this.pos));
    }

    private void save() {
        NetGuard.sendToServer((IMessage) new MessageSirenTune(this.pos, this.colorMeta, this.preset,
                this.volume(), this.pitch, this.gap(), this.mode, this.category, this.flash));
        this.tile.applyConfigLocal(this.preset, this.volume(), this.pitch, this.gap(),
                this.mode, this.category, this.flash);
        this.dirty = false;
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == 1 && this.dirty && this.mc.player != null) {
            this.mc.player.sendStatusMessage((ITextComponent) new TextComponentString(TextFormatting.YELLOW
                    + "[jiezhirbmkmod] " + TextFormatting.RESET + "警报器设置还没保存（点「保存」才生效）"), false);
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
        int px = this.left();
        int py = this.top();
        GuiDraw.drawRoundedRect(px, py, W, H, 8.0F, 0xF014171B);
        this.frame(px, py);
        this.drawCenteredString(this.fontRenderer, "RBMK 警报器", px + W / 2, py + 6, 0xFFE6EBF2);
        this.drawStatus(px, py);
        this.drawPalette(px, py);
        this.label(px + M, py + Y_SOUND_L, "音色");
        this.label(px + M + 30, py + Y_SOUND_L, SirenPresets.label(this.preset)
                + "  一次周期约 " + SirenPresets.repeatSeconds(this.preset, this.gap()) + " 秒");
        this.label(px + M, py + Y_VOL_L, "音量");
        this.label(px + M + 148, py + Y_VOL_L, SirenPresets.approxRadius(this.volume()));
        this.label(px + M, py + Y_PITCH + 4, "音调");
        this.label(px + M + 58, py + Y_PITCH + 4, fmt(this.pitch));
        this.label(px + M, py + Y_GAP_L, "停顿");
        this.label(px + M, py + Y_MODE_L, "模式：循环=不间歇重拉 / 响一次=只来一遍 / 慢拍=至少隔 2 秒");
        this.label(px + M, py + Y_CAT_L, "声道：方块声近、环境声最远、总音量不吃音效滑条");
        this.label(px + M, py + Y_NOTE, "拿染料右键方块=直接换色；信号来自任意一面");
        this.highlightSelections();
        this.drawPalettePick(px, py);
    }

    private void frame(int px, int py) {
        int line = 0xFF4A525C;
        GuiDraw.drawRect(px, py, px + W, py + 1, line);
        GuiDraw.drawRect(px, py + H - 1, px + W, py + H, line);
        GuiDraw.drawRect(px, py, px + 1, py + H, line);
        GuiDraw.drawRect(px + W - 1, py, px + W, py + H, line);
    }

    private void drawStatus(int px, int py) {
        boolean powered = this.mc.world != null && this.mc.world.isBlockPowered(this.pos);
        this.fontRenderer.drawString(this.tile.getColor().getName(), px + M, py + Y_STATUS, 0xFFC8D0DA);
        this.fontRenderer.drawString(powered ? "红石 有 → 正在响" : "红石 无 → 静默",
                px + M + 76, py + Y_STATUS, powered ? 0xFFFFB347 : 0xFF8A93A0);
        this.fontRenderer.drawString(this.dirty ? "未保存" : "已同步",
                px + W - M - 30, py + Y_STATUS, this.dirty ? 0xFFFF5A50 : 0xFF7FD7A0);
    }

    private void drawPalette(int px, int py) {
        EnumDyeColor[] all = EnumDyeColor.values();
        for (int i = 0; i < all.length && i < 16; ++i) {
            int x = px + PAL_X + (i % PAL_COLS) * (PAL_W + PAL_GAP);
            int y = py + PAL_Y + (i / PAL_COLS) * (PAL_H + PAL_GAP);
            GuiDraw.drawRect(x, y, x + PAL_W, y + PAL_H, 0xFF000000 | all[i].getColorValue());
            GuiDraw.drawRect(x, y, x + PAL_W, y + 1, 0x40FFFFFF);
            GuiDraw.drawRect(x, y + PAL_H - 1, x + PAL_W, y + PAL_H, 0x60000000);
        }
    }

    private void drawPalettePick(int px, int py) {
        int i = this.colorMeta;
        if (i < 0 || i >= 16) {
            return;
        }
        int x = px + PAL_X + (i % PAL_COLS) * (PAL_W + PAL_GAP);
        int y = py + PAL_Y + (i / PAL_COLS) * (PAL_H + PAL_GAP);
        int c = 0xFFFFFFFF;
        GuiDraw.drawRect(x - 1, y - 1, x + PAL_W + 1, y, c);
        GuiDraw.drawRect(x - 1, y + PAL_H, x + PAL_W + 1, y + PAL_H + 1, c);
        GuiDraw.drawRect(x - 1, y, x, y + PAL_H, c);
        GuiDraw.drawRect(x + PAL_W, y, x + PAL_W + 1, y + PAL_H, c);
    }

    private void highlightSelections() {
        this.tint(ID_SOUND + this.preset);
        this.tint(ID_VOLUME + this.volumeTier);
        this.tint(ID_GAP + this.gapTier);
        this.tint(ID_MODE + this.mode);
        this.tint(ID_CAT + this.category);
    }

    private void tint(int id) {
        GuiButton b = this.byId(id);
        if (b == null) {
            return;
        }
        int c = 0xFF7FD7A0;
        GuiDraw.drawRect(b.x - 1, b.y - 1, b.x + b.width + 1, b.y, c);
        GuiDraw.drawRect(b.x - 1, b.y + b.height, b.x + b.width + 1, b.y + b.height + 1, c);
        GuiDraw.drawRect(b.x - 1, b.y, b.x, b.y + b.height, c);
        GuiDraw.drawRect(b.x + b.width, b.y, b.x + b.width + 1, b.y + b.height, c);
    }

    private void label(int x, int y, String s) {
        this.fontRenderer.drawString(s, x, y, 0xFF9AA5B1);
    }

    private static String fmt(float v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    private static final class SoundCats {
        private SoundCats() {
        }

        static SoundCategory of(int cat) {
            switch (SirenPresets.clampCat(cat)) {
                case SirenPresets.CAT_AMBIENT:
                    return SoundCategory.AMBIENT;
                case SirenPresets.CAT_MASTER:
                    return SoundCategory.MASTER;
                default:
                    return SoundCategory.BLOCKS;
            }
        }
    }
}
