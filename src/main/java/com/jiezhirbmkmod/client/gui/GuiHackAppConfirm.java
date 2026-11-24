package com.jiezhirbmkmod.client.gui;

import java.io.IOException;

import com.jiezhirbmkmod.dfc.RbmkConfig;
import com.jiezhirbmkmod.inventory.ContainerHackApp;
import com.jiezhirbmkmod.network.ModNetwork;
import com.jiezhirbmkmod.network.msg.MessageHackAppSend;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.resources.I18n;

/**
 * v17.6 黑客攻击玩法申请选择界面（申请人双击操作员工牌打开）。
 *
 * <p><b>必须</b>继承 {@link GuiContainer} 而不是 GuiScreen：开关状态走
 * 容器窗口属性（{@code SPacketWindowProperty}）下发，Forge 只把属性喂给
 * 客户端侧被挂到 {@code player.openContainer} 上的 GuiContainer 的容器 ——
 * 纯 GuiScreen 的路由收不到同步，按钮永远读不到当前是开还是关。</p>
 *
 * <p>状态读取时序（打开 → 读取 → 显示）：构造时先复位静态暂存再从本机
 * {@link RbmkConfig} 预填 —— 单人世界里客户端与内置服务端共享同一个 JVM
 * 静态字段，预填即精确；专用服务器上预填是近似值，服务端
 * {@code ContainerHackApp} 打开时立即下发窗口属性（容器 addListener 会
 * 先跑一遍 detectAndSendChanges），一 tick 内被真实值纠正。按钮文案随后
 * 定稿：开关关 → 「发送开启申请」；开关开且发起人是管理员/房主 →
 * 「发送关闭申请」；开关开但只是持卡人 → 同文案但置灰（服务端会再拦）。</p>
 */
public class GuiHackAppConfirm
extends GuiContainer {

    private static final int BTN_SEND = 0;
    private static final int BTN_CANCEL = 1;

    private static final int PANEL_W = 200;
    private static final int PANEL_H = 108;

    /** 容器窗口属性同步进来的状态（updateProgressBar 写入） */
    private static int allowState = -1;    // 1 开 / 0 关 / -1 未同步
    private static int canCloseState = -1; // 1 可发起关闭 / 0 否

    private GuiButton sendButton;

    public static void setField(int id, int data) {
        if (id == ContainerHackApp.FIELD_ALLOW) {
            allowState = data;
        } else if (id == ContainerHackApp.FIELD_CAN_CLOSE) {
            canCloseState = data;
        }
    }

    public GuiHackAppConfirm() {
        super(new ContainerHackApp(false));
        // 每次打开都先清掉上一次的残留值，再从本机配置预填开关状态
        allowState = RbmkConfig.allowNormalPlayerHack ? 1 : 0;
        canCloseState = -1;
        this.xSize = PANEL_W;
        this.ySize = PANEL_H;
    }

    @Override
    public void initGui() {
        super.initGui();
        int x = this.guiLeft;
        int y = this.guiTop;
        this.buttonList.add(this.sendButton = new GuiButton(BTN_SEND,
                x + 10, y + PANEL_H - 44, (PANEL_W - 30) / 2, 20,
                I18n.format("gui.jiezhirbmkmod.hackapp.send")));
        this.buttonList.add(new GuiButton(BTN_CANCEL,
                x + 20 + (PANEL_W - 30) / 2, y + PANEL_H - 44, (PANEL_W - 30) / 2, 20,
                I18n.format("gui.jiezhirbmkmod.hackapp.cancel")));
        this.refreshButtons();
    }

    /** 开关状态决定发送按钮的含义与可用性（未同步到真值前置灰防误发） */
    private void refreshButtons() {
        if (this.sendButton == null) {
            return;
        }
        if (allowState < 0) {
            // 理论上只在属性包到达前的一瞬出现
            this.sendButton.enabled = false;
            this.sendButton.displayString = I18n.format("gui.jiezhirbmkmod.hackapp.send");
        } else if (allowState == 0) {
            // 开关关着：所有人都可以申请开启
            this.sendButton.enabled = true;
            this.sendButton.displayString = I18n.format("gui.jiezhirbmkmod.hackapp.send_open");
        } else if (canCloseState == 1) {
            // 开关开着且发起人是管理员/房主：可以申请关闭
            this.sendButton.enabled = true;
            this.sendButton.displayString = I18n.format("gui.jiezhirbmkmod.hackapp.send_close");
        } else {
            // 开关开着但发起人只是持卡人：关闭申请要管理员发起
            this.sendButton.enabled = false;
            this.sendButton.displayString = I18n.format("gui.jiezhirbmkmod.hackapp.send_close");
        }
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        this.refreshButtons();
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == BTN_SEND && this.sendButton != null && this.sendButton.enabled) {
            ModNetwork.CHANNEL.sendToServer(new MessageHackAppSend(allowState == 1));
        }
        this.mc.displayGuiScreen(null);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        int x = this.guiLeft;
        int y = this.guiTop;
        drawRect(x, y, x + PANEL_W, y + PANEL_H, 0xFF20242B);
        drawRect(x + 2, y + 2, x + PANEL_W - 2, y + 3, 0xFFB03A34);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        String title = I18n.format("gui.jiezhirbmkmod.hackapp.title");
        this.fontRenderer.drawString(title, PANEL_W / 2
                - this.fontRenderer.getStringWidth(title) / 2, 10, 0xFFE0E0E0);

        String state;
        int color;
        if (allowState < 0) {
            state = I18n.format("gui.jiezhirbmkmod.hackapp.state_sync");
            color = 0xFF9FB4CC;
        } else if (allowState == 1) {
            state = I18n.format("gui.jiezhirbmkmod.hackapp.state_on");
            color = 0xFFB8F0B0;
        } else {
            state = I18n.format("gui.jiezhirbmkmod.hackapp.state_off");
            color = 0xFFE8A0A0;
        }
        this.fontRenderer.drawString(state, 12, 34, color);

        this.fontRenderer.drawString(I18n.format("gui.jiezhirbmkmod.hackapp.hint1"),
                12, 50, 0xFF9FB4CC);
        if (allowState == 1 && canCloseState != 1) {
            this.fontRenderer.drawString(I18n.format("gui.jiezhirbmkmod.hackapp.hint_locked"),
                    12, 62, 0xFFE8D48A);
        } else {
            this.fontRenderer.drawString(I18n.format("gui.jiezhirbmkmod.hackapp.hint2"),
                    12, 62, 0xFF8A94A0);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
        this.renderHoveredToolTip(mouseX, mouseY);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
