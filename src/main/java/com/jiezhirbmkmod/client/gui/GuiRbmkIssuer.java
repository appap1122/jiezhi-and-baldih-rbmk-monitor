package com.jiezhirbmkmod.client.gui;


import com.jiezhirbmkmod.network.NetGuard;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.jiezhirbmkmod.block.TileRbmkIssuer;
import com.jiezhirbmkmod.inventory.ContainerIssuer;
import com.jiezhirbmkmod.network.ModNetwork;
import com.jiezhirbmkmod.network.msg.MessageIssuerAuthorize;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import net.minecraft.inventory.Slot;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.input.Mouse;

/**
 * v17.6 签发器 GUI —— 与警报器 / 告警面板同一套"安全工艺"画风：
 * 深灰面板 + 分区框线 + 按钮直排，无贴图依赖。
 *
 * <p>布局（256x210）：左上是空白卡槽与签发配置（次数 ±、档位勾选），
 * 右上是玩家区（搜索框 + 在线名单 + 单选/多选切换），底部"授权"。</p>
 *
 * <p>在线名单来自客户端连接里现成的 {@code getPlayerInfoMap}（与 TAB
 * 名单同一数据源），搜索过滤与选择全部在客户端完成，只有"授权"一按
 * 才发包 —— 服务端仍会重新校验权限与距离。</p>
 */
public class GuiRbmkIssuer
extends GuiContainer {

    /** 面板尺寸 */
    private static final int PANEL_W = 256;
    private static final int PANEL_H = 232;

    /** 结果区（签发后的卡展示条）纵向范围 */
    private static final int RESULT_Y = 186;

    // 按钮 id
    private static final int BTN_USES_10 = 0;
    private static final int BTN_USES_1 = 1;
    private static final int BTN_USES_P1 = 2;
    private static final int BTN_USES_P10 = 3;
    private static final int BTN_TIER_LOW = 4;
    private static final int BTN_TIER_MID = 5;
    private static final int BTN_TIER_HIGH = 6;
    private static final int BTN_TIER_ULTRA = 7;
    private static final int BTN_SELECT_MODE = 8;
    private static final int BTN_AUTHORIZE = 9;

    private final TileRbmkIssuer tile;
    private final BlockPos pos;

    private GuiTextField search;
    private boolean multiSelect = true;
    /** 选中的玩家名（多选可多个；单选只有一个） */
    private final List<String> selected = new ArrayList<String>();
    private int listScroll = 0;

    /** 本地可编辑副本：点按钮只改它，"授权"时才随包发出 */
    private int usesPerCard;
    private int tierMask;

    // ---- 授权结果状态（由 MessageIssuerResult 回包填充）----
    /** 已提交授权、等待服务端回包（期间授权按钮置灰防连点） */
    private boolean waiting = false;
    private int waitingTicks = 0;
    /** 等待超时：包没回来（权限/距离校验被拒也会表现为无回包） */
    private boolean timedOut = false;
    /** 最近一次签发结果 */
    private com.jiezhirbmkmod.network.msg.MessageIssuerResult result = null;
    /** 签发过后的卡（服务端实际产出的 NBT 还原，图标+tooltip 展示） */
    private net.minecraft.item.ItemStack resultCard = null;

    public GuiRbmkIssuer(TileRbmkIssuer tile) {
        super(new ContainerIssuer(tile));
        this.tile = tile;
        this.pos = tile.getPos();
        this.usesPerCard = tile.getUsesPerCard();
        this.tierMask = tile.getTierMask() <= 0 ? 0xF : tile.getTierMask();
        this.xSize = PANEL_W;
        this.ySize = PANEL_H;
    }

    @Override
    public void initGui() {
        super.initGui();
        int bx = this.guiLeft;
        int by = this.guiTop;
        this.buttonList.add(new GuiButton(BTN_USES_10, bx + 10, by + 46, 30, 20, "-10"));
        this.buttonList.add(new GuiButton(BTN_USES_1, bx + 42, by + 46, 22, 20, "-1"));
        this.buttonList.add(new GuiButton(BTN_USES_P1, bx + 122, by + 46, 22, 20, "+1"));
        this.buttonList.add(new GuiButton(BTN_USES_P10, bx + 146, by + 46, 30, 20, "+10"));
        this.buttonList.add(new GuiButton(BTN_TIER_LOW, bx + 10, by + 74, 40, 20, this.tierLabel(1)));
        this.buttonList.add(new GuiButton(BTN_TIER_MID, bx + 52, by + 74, 40, 20, this.tierLabel(2)));
        this.buttonList.add(new GuiButton(BTN_TIER_HIGH, bx + 94, by + 74, 40, 20, this.tierLabel(3)));
        this.buttonList.add(new GuiButton(BTN_TIER_ULTRA, bx + 136, by + 74, 40, 20, this.tierLabel(4)));
        this.buttonList.add(new GuiButton(BTN_SELECT_MODE, bx + 144, by + 100, 44, 18, this.modeLabel()));
        this.buttonList.add(new GuiButton(BTN_AUTHORIZE, bx + 10, by + PANEL_H - 26, PANEL_W - 20, 20,
                I18n.format("gui.jiezhirbmkmod.issuer.authorize")));

        this.search = new GuiTextField(0, this.fontRenderer, bx + 10, by + 102, 130, 16);
        this.search.setMaxStringLength(32);
        this.updateButtonStates();
    }

    private String tierLabel(int tier) {
        return I18n.format(com.jiezhirbmkmod.block.TileRbmkMonitorScreen.refreshTierKey(tier));
    }

    private String modeLabel() {
        // 模式 + 已选人数合并显示，省一行文字给结果区
        return I18n.format(this.multiSelect ? "gui.jiezhirbmkmod.issuer.multi"
                                            : "gui.jiezhirbmkmod.issuer.single")
                + "(" + this.selected.size() + ")";
    }

    /** 档位按钮显示选中态（§a 绿 = 勾选，§8 暗灰 = 关闭）；次数区文字在绘制层 */
    private void updateButtonStates() {
        for (Object o : this.buttonList) {
            GuiButton b = (GuiButton) o;
            switch (b.id) {
                case BTN_TIER_LOW:
                    b.displayString = this.tierLabel(1);
                    break;
                case BTN_TIER_MID:
                    b.displayString = this.tierLabel(2);
                    break;
                case BTN_TIER_HIGH:
                    b.displayString = this.tierLabel(3);
                    break;
                case BTN_TIER_ULTRA:
                    b.displayString = this.tierLabel(4);
                    break;
                case BTN_SELECT_MODE:
                    b.displayString = this.modeLabel();
                    break;
                case BTN_AUTHORIZE:
                    // 等待回包期间置灰，防连点造成重复发卡
                    b.enabled = !this.waiting;
                    break;
                default:
                    break;
            }
        }
    }

    /** 在线玩家 + 搜索过滤 */
    private List<String> onlinePlayers() {
        List<String> out = new ArrayList<String>();
        net.minecraft.client.network.NetHandlerPlayClient conn =
                net.minecraft.client.Minecraft.getMinecraft().getConnection();
        if (conn == null) {
            return out;
        }
        String q = this.search == null ? "" : this.search.getText().trim().toLowerCase();
        for (net.minecraft.client.network.NetworkPlayerInfo info : conn.getPlayerInfoMap()) {
            String name = info.getGameProfile() == null ? null : info.getGameProfile().getName();
            if (name == null) {
                continue;
            }
            if (q.isEmpty() || name.toLowerCase().contains(q)) {
                out.add(name);
            }
        }
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        switch (button.id) {
            case BTN_USES_10:
                this.usesPerCard = clampUses(this.usesPerCard - 10);
                break;
            case BTN_USES_1:
                this.usesPerCard = clampUses(this.usesPerCard - 1);
                break;
            case BTN_USES_P1:
                this.usesPerCard = clampUses(this.usesPerCard + 1);
                break;
            case BTN_USES_P10:
                this.usesPerCard = clampUses(this.usesPerCard + 10);
                break;
            case BTN_TIER_LOW:
                this.toggleTier(1);
                break;
            case BTN_TIER_MID:
                this.toggleTier(2);
                break;
            case BTN_TIER_HIGH:
                this.toggleTier(3);
                break;
            case BTN_TIER_ULTRA:
                this.toggleTier(4);
                break;
            case BTN_SELECT_MODE:
                this.multiSelect = !this.multiSelect;
                if (!this.multiSelect && this.selected.size() > 1) {
                    String first = this.selected.get(0);
                    this.selected.clear();
                    this.selected.add(first);
                }
                this.updateButtonStates();
                break;
            case BTN_AUTHORIZE:
                this.authorize();
                return;
            default:
                return;
        }
        this.updateButtonStates();
    }

    private static int clampUses(int v) {
        return Math.max(0, Math.min(9999, v));
    }

    private void toggleTier(int tier) {
        int bit = 1 << (tier - 1);
        this.tierMask = (this.tierMask ^ bit) & 0xF;
        // 掩码不允许全关：至少留一档，否则卡一发出来就是废的
        if (this.tierMask == 0) {
            this.tierMask = bit;
        }
    }

    /**
     * 授权：把次数/掩码/名单一包发出。GUI 不关屏 —— 服务端会把实际产出的
     * 卡（完整 NBT）随结果回包带回来，签发过后的卡随后出现在结果区；
     * 没有选人时红字提示，原地不动。
     */
    private void authorize() {
        if (this.selected.isEmpty()) {
            this.mc.player.sendMessage(new net.minecraft.util.text.TextComponentString(
                    net.minecraft.util.text.TextFormatting.RED + "[jiezhirbmkmod] "
                            + I18n.format("msg.jiezhirbmkmod.issuer.noselect")));
            return;
        }
        this.waiting = true;
        this.waitingTicks = 0;
        this.timedOut = false;
        this.result = null;
        this.resultCard = null;
        this.updateButtonStates();
        NetGuard.sendToServer(new MessageIssuerAuthorize(this.pos,
                this.usesPerCard, this.tierMask,
                this.selected.toArray(new String[this.selected.size()])));
    }

    /** 服务端回包入口：签发过后的卡在这里"最后出现" */
    public void receiveResult(com.jiezhirbmkmod.network.msg.MessageIssuerResult msg) {
        this.waiting = false;
        this.timedOut = false;
        this.result = msg;
        if (msg.getCardTag() != null) {
            this.resultCard = new net.minecraft.item.ItemStack(msg.getCardTag());
        } else {
            this.resultCard = null;
        }
        this.updateButtonStates();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (this.search != null) {
            this.search.mouseClicked(mouseX, mouseY, mouseButton);
        }
        // 玩家名单点击区
        List<String> players = this.onlinePlayers();
        int listX = this.guiLeft + 158;
        int listY = this.guiTop + 124;
        if (mouseX >= listX && mouseX <= listX + 88
                && mouseY >= listY && mouseY <= listY + 60 && !players.isEmpty()) {
            int row = (mouseY - listY) / 12 + this.listScroll;
            if (row >= 0 && row < players.size()) {
                String name = players.get(row);
                if (this.multiSelect) {
                    if (this.selected.contains(name)) {
                        this.selected.remove(name);
                    } else {
                        this.selected.add(name);
                    }
                } else {
                    this.selected.clear();
                    this.selected.add(name);
                }
                // 选中数变了：刷新单选/多选按钮上的计数标签
                this.updateButtonStates();
            }
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (this.search != null && this.search.isFocused()
                && this.search.textboxKeyTyped(typedChar, keyCode)) {
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0 && this.onlinePlayers().size() > 5) {
            this.listScroll = Math.max(0,
                    Math.min(this.onlinePlayers().size() - 5, this.listScroll + (wheel > 0 ? -1 : 1)));
        }
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (this.search != null) {
            this.search.updateCursorCounter();
        }
        // 滚动位置跟随列表长度收紧：搜索过滤后名单变短时，旧 scroll 可能
        // 越过末尾导致整屏空白 —— 每刻按当前名单大小钳一次
        int n = this.onlinePlayers().size();
        if (this.listScroll > Math.max(0, n - 5)) {
            this.listScroll = Math.max(0, n - 5);
        }
        // 等待回包计时：5 秒无响应按超时处理并解锁按钮（包丢失 / 校验被拒）
        if (this.waiting && ++this.waitingTicks > 100) {
            this.waiting = false;
            this.timedOut = true;
            this.updateButtonStates();
        }
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        // 面板底 + 分区框（安全工艺：深灰底、亮框、红色告警条点缀）
        this.drawDefaultBackground();
        int x = this.guiLeft;
        int y = this.guiTop;
        drawRect(x, y, x + PANEL_W, y + PANEL_H, 0xFF20242B);
        drawRect(x + 2, y + 2, x + PANEL_W - 2, y + 3, 0xFFB03A34); // 顶部红条
        drawRect(x + 6, y + 40, x + 148, y + 100, 0xFF2A2F37);      // 签发配置区
        drawRect(x + 154, y + 40, x + PANEL_W - 6, y + 184, 0xFF2A2F37); // 玩家区
        drawRect(x + 6, y + RESULT_Y, x + PANEL_W - 6, y + RESULT_Y + 18, 0xFF2A2F37); // 结果区
        // 槽位框：与容器槽 (80,30) 对齐 —— 18x18 从槽位左上角 (79,29) 起
        drawRect(x + 79, y + 29, x + 97, y + 47, 0xFF14161A);
        drawRect(x + 80, y + 30, x + 96, y + 46, 0xFF38404B);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        int cx = PANEL_W / 2;
        this.fontRenderer.drawString(I18n.format("gui.jiezhirbmkmod.issuer.title"),
                cx - this.fontRenderer.getStringWidth(I18n.format("gui.jiezhirbmkmod.issuer.title")) / 2, 8, 0xFFE0E0E0);

        // 签发配置区标题与数值
        this.fontRenderer.drawString(I18n.format("gui.jiezhirbmkmod.issuer.cfg"), 10, 28, 0xFF9FB4CC);
        String usesText = this.usesPerCard == 0
                ? I18n.format("gui.jiezhirbmkmod.issuer.unlimited")
                : I18n.format("gui.jiezhirbmkmod.issuer.uses", this.usesPerCard);
        this.fontRenderer.drawString(usesText, 68 - this.fontRenderer.getStringWidth(usesText) / 2 + 30, 52, 0xFFE8D48A);
        this.fontRenderer.drawString(I18n.format("gui.jiezhirbmkmod.issuer.tiers"), 10, 68, 0xFF9FB4CC);

        // 档位勾选态：在按钮文字上叠色点
        int[] tierBtnIds = {BTN_TIER_LOW, BTN_TIER_MID, BTN_TIER_HIGH, BTN_TIER_ULTRA};
        for (int i = 0; i < tierBtnIds.length; ++i) {
            boolean on = (this.tierMask & (1 << i)) != 0;
            GuiButton b = this.getButtonById(tierBtnIds[i]);
            if (b != null && on) {
                drawRect(b.x - this.guiLeft, b.y - this.guiTop,
                        b.x - this.guiLeft + 3, b.y - this.guiTop + 20, 0xFF66C266);
            }
        }

        // 卡槽状态
        String card = I18n.format("gui.jiezhirbmkmod.issuer.cards", this.tile.cardCount());
        this.fontRenderer.drawString(card, 98, 30, 0xFFC8CDD6);

        // 玩家区
        this.fontRenderer.drawString(I18n.format("gui.jiezhirbmkmod.issuer.players"), 158, 28, 0xFF9FB4CC);
        List<String> players = this.onlinePlayers();
        int rowY = 124;
        int shown = Math.min(5, players.size() - this.listScroll);
        for (int i = 0; i < shown; ++i) {
            String name = players.get(this.listScroll + i);
            boolean sel = this.selected.contains(name);
            if (sel) {
                drawRect(158, rowY + i * 12 - 1, 246, rowY + i * 12 + 10, 0xFF2E5A2E);
            }
            this.fontRenderer.drawString(name, 160, rowY + i * 12, sel ? 0xFFB8F0B0 : 0xFFC8CDD6);
        }
        if (players.isEmpty()) {
            this.fontRenderer.drawString(I18n.format("gui.jiezhirbmkmod.issuer.nobody"), 160, rowY, 0xFF6B7280);
        }
        // ---- 结果区：签发过后的卡"最后出现"在这里 ----
        int ry = RESULT_Y;
        if (this.waiting) {
            this.fontRenderer.drawString(I18n.format("gui.jiezhirbmkmod.issuer.waiting"),
                    10, ry + 5, 0xFFC8CDD6);
        } else if (this.timedOut) {
            this.fontRenderer.drawString(I18n.format("gui.jiezhirbmkmod.issuer.timeout"),
                    10, ry + 5, 0xFFE0A060);
        } else if (this.result != null) {
            if (this.resultCard != null && !this.resultCard.isEmpty()) {
                // 实际产出的卡（服务端 NBT 原样还原）：图标 + 原版 tooltip
                // （悬浮即见签发人 / 剩余次数 / 允许档位，与目标背包里那张同源）
                net.minecraft.client.renderer.RenderItem ri = this.mc.getRenderItem();
                ri.renderItemAndEffectIntoGUI(this.resultCard, 8, ry + 1);
            }
            String line1 = this.result.getOk() > 0
                    ? I18n.format("gui.jiezhirbmkmod.issuer.result", Integer.valueOf(this.result.getOk()),
                            Integer.valueOf(this.result.getNoCard()), Integer.valueOf(this.result.getOffline()))
                    : I18n.format("gui.jiezhirbmkmod.issuer.result_none");
            this.fontRenderer.drawString(line1, 30, ry + 1,
                    this.result.getOk() > 0 ? 0xFFB8F0B0 : 0xFFE0A060);
            if (this.result.getOk() > 0) {
                String line2 = I18n.format("gui.jiezhirbmkmod.issuer.result_card",
                        this.result.getIssuedTo());
                this.fontRenderer.drawString(line2, 30, ry + 10, 0xFFC8CDD6);
            }
        }
    }

    private GuiButton getButtonById(int id) {
        for (Object o : this.buttonList) {
            GuiButton b = (GuiButton) o;
            if (b.id == id) {
                return b;
            }
        }
        return null;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
        this.renderHoveredToolTip(mouseX, mouseY);
        if (this.search != null) {
            this.search.drawTextBox();
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
