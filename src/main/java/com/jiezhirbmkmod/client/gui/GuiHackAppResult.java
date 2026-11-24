package com.jiezhirbmkmod.client.gui;

import java.io.IOException;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.TextFormatting;

/**
 * v17.6 审批结果回信弹窗（申请人收到 MessageHackAppResult 后弹出）。
 *
 * <p>正文「您的开启/关闭黑客攻击玩法申请已被同意/拒绝」，下方「知道了」
 * 按钮关窗。通过时服务端已写回开关并落盘、全服公告，无需申请人再操作。</p>
 */
public class GuiHackAppResult
extends GuiScreen {

    private static final int BTN_OK = 0;

    private static final int PANEL_W = 220;
    private static final int PANEL_H = 84;

    private final boolean close;
    private final boolean accepted;

    public GuiHackAppResult(boolean close, boolean accepted) {
        this.close = close;
        this.accepted = accepted;
    }

    @Override
    public void initGui() {
        super.initGui();
        int x = (this.width - PANEL_W) / 2;
        int y = (this.height - PANEL_H) / 2;
        this.buttonList.add(new GuiButton(BTN_OK, x + 10, y + PANEL_H - 34,
                PANEL_W - 20, 20, I18n.format("gui.jiezhirbmkmod.hackapp.ok")));
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == BTN_OK) {
            this.mc.displayGuiScreen(null);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        int x = (this.width - PANEL_W) / 2;
        int y = (this.height - PANEL_H) / 2;
        drawRect(x, y, x + PANEL_W, y + PANEL_H, 0xFF20242B);
        drawRect(x + 2, y + 2, x + PANEL_W - 2, y + 3,
                this.accepted ? 0xFF66C266 : 0xFFB03A34);

        String body = I18n.format(
                this.accepted ? "gui.jiezhirbmkmod.hackapp.result_ok"
                              : "gui.jiezhirbmkmod.hackapp.result_deny",
                I18n.format(this.close ? "gui.jiezhirbmkmod.hackapp.word_close"
                                       : "gui.jiezhirbmkmod.hackapp.word_open"));
        this.fontRenderer.drawString(body, x + 12, y + 22,
                this.accepted ? 0xFFB8F0B0 : 0xFFE8A0A0);

        if (this.accepted) {
            this.fontRenderer.drawString(I18n.format("gui.jiezhirbmkmod.hackapp.result_note"),
                    x + 12, y + 38, 0xFF9FB4CC);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
