package com.jiezhimod.client.gui;

import com.jiezhimod.JiezhiMod;
import com.jiezhimod.block.ContainerCustomScreen;
import com.jiezhimod.block.TileCustomScreen;
import com.jiezhimod.client.util.GuiDraw;
import com.jiezhimod.dfc.DfcWatch;
import com.jiezhimod.item.ItemRbmkRemote;
import com.jiezhimod.network.ModNetwork;
import com.jiezhimod.network.msg.MessageCustomScreenAction;
import java.io.IOException;
import java.util.List;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.resources.I18n;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(value=Side.CLIENT)
public class GuiCustomScreen
extends GuiContainer {
    private static final int W = 312;
    private static final int H = 240;
    private static final int MARGIN = 6;
    private static final int SLOT_BOX = 18;
    private static final int VALUE_GAP = 6;
    private static final int ID_POWER = 0;
    private static final int ID_CLEAR = 1;
    private static final int ID_CLOSE = 2;
    private static final int ID_ADD = 3;
    private static final int ID_DEL_BASE = 100;
    private final TileCustomScreen tile;
    private final BlockPos pos;
    private GuiButton powerButton;
    private GuiButton clearButton;
    private GuiButton addButton;
    private GuiButton[] delButtons = new GuiButton[8];
    private static boolean loggedInit = false;
    private static boolean loggedBg = false;

    public GuiCustomScreen(TileCustomScreen tile, ContainerCustomScreen container) {
        super((Container)container);
        this.tile = tile;
        this.pos = tile.func_174877_v();
        this.field_146999_f = 312;
        this.field_147000_g = 240;
    }

    public boolean func_73868_f() {
        return false;
    }

    public void func_73866_w_() {
        super.func_73866_w_();
        int px = this.field_147003_i;
        int py = this.field_147009_r;
        this.powerButton = new GuiButton(0, px + 6, py + 14, 78, 16, "");
        this.field_146292_n.add(this.powerButton);
        this.addButton = new GuiButton(3, px + 6, py + 67, 78, 16, I18n.func_135052_a((String)"gui.jiezhimod.custom.add", (Object[])new Object[0]));
        this.field_146292_n.add(this.addButton);
        int delX = px + 312 - 6 - 34;
        for (int i = 0; i < 8; ++i) {
            GuiButton b;
            this.delButtons[i] = b = new GuiButton(100 + i, delX, py + 14 + i * 12, 34, 11, I18n.func_135052_a((String)"gui.jiezhimod.custom.delete", (Object[])new Object[0]));
            this.field_146292_n.add(b);
        }
        this.clearButton = new GuiButton(1, px + 6, py + 134, 72, 16, I18n.func_135052_a((String)"gui.jiezhimod.custom.clear", (Object[])new Object[0]));
        this.field_146292_n.add(this.clearButton);
        this.field_146292_n.add(new GuiButton(2, px + 120, py + 134, 72, 16, I18n.func_135052_a((String)"gui.jiezhimod.custom.close", (Object[])new Object[0])));
        if (!loggedInit) {
            loggedInit = true;
            JiezhiMod.warn("[GuiCustomScreen] init: screen={}x{} panel={}x{} gui=({},{}) overflowY={} overflowX={}", this.field_146294_l, this.field_146295_m, 312, 240, px, py, py < 0 || py + 240 > this.field_146295_m, px < 0 || px + 312 > this.field_146294_l);
        }
        this.refreshButtons();
    }

    private void refreshButtons() {
        if (this.powerButton != null) {
            this.powerButton.field_146126_j = I18n.func_135052_a((String)(this.tile.isPowered() ? "gui.jiezhimod.custom.power.off" : "gui.jiezhimod.custom.power.on"), (Object[])new Object[0]);
        }
        int n = this.slots().size();
        for (int i = 0; i < this.delButtons.length; ++i) {
            if (this.delButtons[i] == null) continue;
            this.delButtons[i].field_146124_l = i < n;
        }
        if (this.clearButton != null) {
            boolean bl = this.clearButton.field_146124_l = n > 0;
        }
        if (this.addButton != null) {
            this.addButton.field_146124_l = this.remoteInSlot() != null;
        }
    }

    private List<DfcWatch> slots() {
        return this.tile.displaySlots();
    }

    private ItemStack remoteInSlot() {
        if (this.field_147002_h == null || this.field_147002_h.func_75139_a(0) == null) {
            return null;
        }
        ItemStack s = this.field_147002_h.func_75139_a(0).func_75211_c();
        if (s == null || s.func_190926_b() || !(s.func_77973_b() instanceof ItemRbmkRemote)) {
            return null;
        }
        return s;
    }

    public void func_73876_c() {
        super.func_73876_c();
        this.refreshButtons();
    }

    protected void func_146284_a(GuiButton button) throws IOException {
        int id = button.field_146127_k;
        if (id == 2) {
            this.closeGui();
            return;
        }
        if (id == 0) {
            this.send(0, 0);
            return;
        }
        if (id == 1) {
            this.send(2, 0);
            return;
        }
        if (id == 3) {
            this.send(3, 0);
            return;
        }
        if (id >= 100 && id < 108) {
            this.send(1, id - 100);
        }
    }

    private void send(int action, int index) {
        ModNetwork.CHANNEL.sendToServer((IMessage)new MessageCustomScreenAction(this.pos, action, index));
    }

    private void closeGui() {
        if (this.field_146297_k.field_71439_g != null) {
            this.field_146297_k.field_71439_g.func_71053_j();
        }
        this.field_146297_k.func_147108_a(null);
    }

    protected void func_146976_a(float partialTicks, int mouseX, int mouseY) {
        int px = this.field_147003_i;
        int py = this.field_147009_r;
        if (!loggedBg) {
            loggedBg = true;
            JiezhiMod.warn("[GuiCustomScreen] bg layer: gui=({},{}) screen={}x{} panel={}x{} overflow={}", px, py, this.field_146294_l, this.field_146295_m, 312, 240, py < 0 || py + 240 > this.field_146295_m || px < 0 || px + 312 > this.field_146294_l);
        }
        GuiDraw.drawRoundedRect(px, py, 312.0f, 240.0f, 8.0f, -16053488);
        this.drawTitle(px, py);
        this.drawPowerRow(px, py);
        this.drawRemoteRow(px, py);
        this.drawAddHint(px, py);
        this.drawList(px, py);
        this.drawHint(px, py);
        this.drawPlayerInventory(px, py);
    }

    protected void func_146979_b(int mouseX, int mouseY) {
    }

    private void drawTitle(int px, int py) {
        String title = I18n.func_135052_a((String)"gui.jiezhimod.custom.title", (Object[])new Object[0]);
        this.field_146289_q.func_78276_b(title, px + (312 - this.field_146289_q.func_78256_a(title)) / 2, py + 4, -1);
    }

    private void drawPowerRow(int px, int py) {
        this.field_146289_q.func_78276_b(I18n.func_135052_a((String)(this.tile.isPowered() ? "gui.jiezhimod.custom.state.on" : "gui.jiezhimod.custom.state.off"), (Object[])new Object[0]), px + 6, py + 33, this.tile.isPowered() ? -11345782 : -7829368);
    }

    private void drawRemoteRow(int px, int py) {
        int color;
        String text;
        int sx = px + 6;
        int sy = py + 45;
        this.drawSlotBox(sx, sy);
        String label = I18n.func_135052_a((String)"gui.jiezhimod.custom.remote", (Object[])new Object[0]);
        int lx = px + 28;
        int ly = py + 50;
        this.field_146289_q.func_78276_b(label, lx, ly, -6641740);
        ItemStack remote = this.remoteInSlot();
        if (remote == null) {
            text = I18n.func_135052_a((String)"gui.jiezhimod.custom.remote.empty", (Object[])new Object[0]);
            color = -9800576;
        } else {
            text = I18n.func_135052_a((String)"gui.jiezhimod.custom.remote.count", (Object[])new Object[]{ItemRbmkRemote.deviceCount(remote), 8});
            color = ItemRbmkRemote.deviceCount(remote) > 0 ? -8395872 : -2575782;
        }
        int tx = lx + this.field_146289_q.func_78256_a(label) + 6;
        int maxW = Math.max(12, px + 132 - 4 - tx);
        this.field_146289_q.func_78276_b(this.field_146289_q.func_78269_a(text, maxW), tx, ly, color);
    }

    private void drawAddHint(int px, int py) {
        int hx = px + 6;
        int maxW = Math.max(12, px + 132 - 4 - hx);
        this.field_146289_q.func_78276_b(this.field_146289_q.func_78269_a(I18n.func_135052_a((String)"gui.jiezhimod.custom.add.hint", (Object[])new Object[0]), maxW), hx, py + 85, -9800576);
    }

    private void drawList(int px, int py) {
        List<DfcWatch> list = this.slots();
        int textX = px + 132;
        int delX = px + 312 - 6 - 34;
        int valueRight = delX - 6;
        for (int i = 0; i < 8; ++i) {
            int rowY = py + 14 + i * 12;
            if ((i & 1) == 0) {
                GuiDraw.drawRoundedRect(textX - 4, rowY - 1, px + 312 - 6 - (textX - 4), 12.0f, 2.0f, 0x22FFFFFF);
            }
            this.field_146289_q.func_78276_b(i + 1 + ".", textX, rowY + 2, -10066330);
            if (i >= list.size()) {
                this.field_146289_q.func_78276_b(I18n.func_135052_a((String)"gui.jiezhimod.custom.empty", (Object[])new Object[0]), textX + 12, rowY + 2, -12303292);
                continue;
            }
            DfcWatch w = list.get(i);
            String value = w.alive ? w.value : I18n.func_135052_a((String)"gui.jiezhimod.custom.offline", (Object[])new Object[0]);
            int valueW = this.field_146289_q.func_78256_a(value);
            int valueX = valueRight - valueW;
            int nameW = Math.max(8, valueX - 6 - (textX + 12));
            String left = w.device + " · " + (w.name == null || w.name.isEmpty() ? "--" : w.name);
            this.field_146289_q.func_78276_b(this.field_146289_q.func_78269_a(left, nameW), textX + 12, rowY + 2, w.alive ? -3355444 : -6728363);
            this.field_146289_q.func_78276_b(value, Math.max(textX + 12, valueX), rowY + 2, w.alive ? -12003585 : -6728363);
        }
    }

    private void drawHint(int px, int py) {
        List hint = this.field_146289_q.func_78271_c(I18n.func_135052_a((String)"gui.jiezhimod.custom.hint", (Object[])new Object[0]), 300);
        for (int i = 0; i < hint.size() && i < 2; ++i) {
            this.field_146289_q.func_78276_b((String)hint.get(i), px + 6, py + 113 + i * 9, -8947849);
        }
    }

    private void drawPlayerInventory(int px, int py) {
        int ix = px + 75;
        int iy = py + 154;
        GuiDraw.drawRoundedRect(ix - 3, iy - 3, 167.0f, 79.0f, 3.0f, 0x33000000);
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.slotFill(ix + col * 18, iy + row * 18);
            }
        }
        for (int col = 0; col < 9; ++col) {
            this.slotFill(ix + col * 18, py + 210);
        }
    }

    private void slotFill(int x, int y) {
        GuiDraw.drawRect(x, y, x + 18, y + 18, -15394785);
        GuiDraw.drawRect(x, y, x + 18, y + 1, -14011584);
        GuiDraw.drawRect(x, y, x + 1, y + 18, -14011584);
    }

    private void drawSlotBox(int x, int y) {
        this.slotFill(x, y);
        GuiDraw.drawRect(x, y + 18 - 1, x + 18, y + 18, -12821896);
        GuiDraw.drawRect(x + 18 - 1, y, x + 18, y + 18, -12821896);
    }
}

