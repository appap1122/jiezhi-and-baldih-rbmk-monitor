package com.jiezhirbmkmod.client.gui;


import com.jiezhirbmkmod.network.NetGuard;
import com.jiezhirbmkmod.RbmkMonitorMod;
import com.jiezhirbmkmod.block.ContainerCustomScreen;
import com.jiezhirbmkmod.block.TileCustomScreen;
import com.jiezhirbmkmod.client.util.GuiDraw;
import com.jiezhirbmkmod.dfc.DfcWatch;
import com.jiezhirbmkmod.item.ItemRbmkRemote;
import com.jiezhirbmkmod.network.ModNetwork;
import com.jiezhirbmkmod.network.msg.MessageRbmkCustomScreenAction;
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
    /** v18.2 布局编辑器入口 */
    private static final int ID_LAYOUT = 4;
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
        this.pos = tile.getPos();
        this.xSize = 312;
        this.ySize = 240;
    }

    public boolean doesGuiPauseGame() {
        return false;
    }

    public void initGui() {
        super.initGui();
        // If the virtual screen is smaller than the panel, centering produces a
        // negative guiLeft/guiTop and clips the left control column (remote slot
        // + device count). Anchor at the top-left so the left pane stays usable.
        this.guiLeft = Math.max(0, this.guiLeft);
        this.guiTop = Math.max(0, this.guiTop);
        int px = this.guiLeft;
        int py = this.guiTop;
        this.powerButton = new GuiButton(0, px + 6, py + 14, 78, 16, "");
        this.buttonList.add(this.powerButton);
        this.addButton = new GuiButton(3, px + 6, py + 67, 78, 16, I18n.format((String)"gui.jiezhirbmkmod.custom.add", (Object[])new Object[0]));
        this.buttonList.add(this.addButton);
        int delX = px + 312 - 6 - 34;
        for (int i = 0; i < 8; ++i) {
            GuiButton b;
            this.delButtons[i] = b = new GuiButton(100 + i, delX, py + 14 + i * 12, 34, 11, I18n.format((String)"gui.jiezhirbmkmod.custom.delete", (Object[])new Object[0]));
            this.buttonList.add(b);
        }
        this.clearButton = new GuiButton(1, px + 6, py + 134, 72, 16, I18n.format((String)"gui.jiezhirbmkmod.custom.clear", (Object[])new Object[0]));
        this.buttonList.add(this.clearButton);
        this.buttonList.add(new GuiButton(2, px + 120, py + 134, 72, 16, I18n.format((String)"gui.jiezhirbmkmod.custom.close", (Object[])new Object[0])));
        // v18.2 布局编辑器入口（第三行按钮：清空 / 关闭 / 布局编辑器）
        this.buttonList.add(new GuiButton(ID_LAYOUT, px + 234, py + 134, 72, 16,
                I18n.format("gui.jiezhirbmkmod.custom.layout")));
        if (!loggedInit) {
            loggedInit = true;
            RbmkMonitorMod.warn("[GuiCustomScreen] init: screen={}x{} panel={}x{} gui=({},{}) overflowY={} overflowX={}", this.width, this.height, 312, 240, px, py, py < 0 || py + 240 > this.height, px < 0 || px + 312 > this.width);
        }
        this.refreshButtons();
    }

    private void refreshButtons() {
        if (this.powerButton != null) {
            this.powerButton.displayString = I18n.format((String)(this.tile.isPowered() ? "gui.jiezhirbmkmod.custom.power.off" : "gui.jiezhirbmkmod.custom.power.on"), (Object[])new Object[0]);
        }
        int n = this.slots().size();
        for (int i = 0; i < this.delButtons.length; ++i) {
            if (this.delButtons[i] == null) continue;
            this.delButtons[i].enabled = i < n;
        }
        if (this.clearButton != null) {
            boolean bl = this.clearButton.enabled = n > 0;
        }
        if (this.addButton != null) {
            this.addButton.enabled = this.remoteInSlot() != null;
        }
    }

    private List<DfcWatch> slots() {
        List<DfcWatch> list = this.tile.displaySlots();
        if (list == null) {
            return new java.util.ArrayList<DfcWatch>();
        }
        return list;
    }

    private ItemStack remoteInSlot() {
        if (this.inventorySlots == null || this.inventorySlots.getSlot(0) == null) {
            return null;
        }
        ItemStack s = this.inventorySlots.getSlot(0).getStack();
        if (s == null || s.isEmpty() || !(s.getItem() instanceof ItemRbmkRemote)) {
            return null;
        }
        return s;
    }

    public void updateScreen() {
        super.updateScreen();
        this.refreshButtons();
    }

    protected void actionPerformed(GuiButton button) throws IOException {
        int id = button.id;
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
        if (id == ID_LAYOUT) {
            // v18.2 布局编辑器：工作副本进编辑器，保存才落服务端
            this.mc.displayGuiScreen(new GuiCustomLayoutEditor(this.pos, this.tile.getLayout()));
            return;
        }
        if (id >= 100 && id < 108) {
            this.send(1, id - 100);
        }
    }

    private void send(int action, int index) {
        NetGuard.sendToServer((IMessage)new MessageRbmkCustomScreenAction(this.pos, action, index));
    }

    private void closeGui() {
        if (this.mc.player != null) {
            this.mc.player.closeScreen();
        }
        this.mc.displayGuiScreen(null);
    }

    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        int px = this.guiLeft;
        int py = this.guiTop;
        if (!loggedBg) {
            loggedBg = true;
            RbmkMonitorMod.warn("[GuiCustomScreen] bg layer: gui=({},{}) screen={}x{} panel={}x{} overflow={}", px, py, this.width, this.height, 312, 240, py < 0 || py + 240 > this.height || px < 0 || px + 312 > this.width);
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

    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
    }

    private void drawTitle(int px, int py) {
        String title = I18n.format((String)"gui.jiezhirbmkmod.custom.title", (Object[])new Object[0]);
        this.fontRenderer.drawString(title, px + (312 - this.fontRenderer.getStringWidth(title)) / 2, py + 4, -1);
    }

    private void drawPowerRow(int px, int py) {
        this.fontRenderer.drawString(I18n.format((String)(this.tile.isPowered() ? "gui.jiezhirbmkmod.custom.state.on" : "gui.jiezhirbmkmod.custom.state.off"), (Object[])new Object[0]), px + 6, py + 33, this.tile.isPowered() ? -11345782 : -7829368);
    }

    private void drawRemoteRow(int px, int py) {
        int color;
        String text;
        int sx = px + 6;
        int sy = py + 45;
        this.drawSlotBox(sx, sy);
        String label = I18n.format((String)"gui.jiezhirbmkmod.custom.remote", (Object[])new Object[0]);
        int lx = px + 28;
        int ly = py + 50;
        ItemStack remote = this.remoteInSlot();
        if (remote == null) {
            text = I18n.format((String)"gui.jiezhirbmkmod.custom.remote.empty", (Object[])new Object[0]);
            color = -9800576;
        } else {
            text = I18n.format((String)"gui.jiezhirbmkmod.custom.remote.count", (Object[])new Object[]{ItemRbmkRemote.deviceCount(remote), 8});
            color = ItemRbmkRemote.deviceCount(remote) > 0 ? -8395872 : -2575782;
        }
        // Keep the count right-aligned inside the left pane; trim the label
        // first if the localized text is too long, never the count itself.
        int paneRight = px + 128;
        int countMax = Math.max(12, paneRight - 4 - lx);
        String count = this.fontRenderer.trimStringToWidth(text, countMax);
        int countW = this.fontRenderer.getStringWidth(count);
        int countX = Math.max(lx, paneRight - 4 - countW);
        int labelMax = Math.max(8, countX - 6 - lx);
        this.fontRenderer.drawString(this.fontRenderer.trimStringToWidth(label, labelMax), lx, ly, -6641740);
        this.fontRenderer.drawString(count, countX, ly, color);
    }

    private void drawAddHint(int px, int py) {
        int hx = px + 6;
        int maxW = Math.max(12, px + 132 - 4 - hx);
        this.fontRenderer.drawString(this.fontRenderer.trimStringToWidth(I18n.format((String)"gui.jiezhirbmkmod.custom.add.hint", (Object[])new Object[0]), maxW), hx, py + 85, -9800576);
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
            this.fontRenderer.drawString(i + 1 + ".", textX, rowY + 2, -10066330);
            if (i >= list.size()) {
                this.fontRenderer.drawString(I18n.format((String)"gui.jiezhirbmkmod.custom.empty", (Object[])new Object[0]), textX + 12, rowY + 2, -12303292);
                continue;
            }
            DfcWatch w = list.get(i);
            if (w == null) {
                continue;
            }
            String value = w.alive ? w.value : I18n.format((String)"gui.jiezhirbmkmod.custom.offline", (Object[])new Object[0]);
            if (value == null || value.isEmpty()) {
                value = "--";
            }
            int rowTextX = textX + 12;
            // Reserve at least half the row for the name, so a long reading can
            // never push the value over the delete button / panel edge.
            int valueMaxW = Math.max(24, (valueRight - rowTextX) / 2);
            String valueText = this.fontRenderer.trimStringToWidth(value, valueMaxW);
            int valueW = this.fontRenderer.getStringWidth(valueText);
            int valueX = Math.max(rowTextX + 8, valueRight - valueW);
            int nameW = Math.max(8, valueX - 6 - rowTextX);
            String device = w.device == null ? "" : w.device;
            String name = w.name == null || w.name.isEmpty() ? "--" : w.name;
            String left = device + " \u00b7 " + name;
            this.fontRenderer.drawString(this.fontRenderer.trimStringToWidth(left, nameW), rowTextX, rowY + 2, w.alive ? -3355444 : -6728363);
            this.fontRenderer.drawString(valueText, valueX, rowY + 2, w.alive ? -12003585 : -6728363);
        }
    }

    private void drawHint(int px, int py) {
        List hint = this.fontRenderer.listFormattedStringToWidth(I18n.format((String)"gui.jiezhirbmkmod.custom.hint", (Object[])new Object[0]), 300);
        for (int i = 0; i < hint.size() && i < 2; ++i) {
            this.fontRenderer.drawString((String)hint.get(i), px + 6, py + 113 + i * 9, -8947849);
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

