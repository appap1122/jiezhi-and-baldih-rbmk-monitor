package com.jiezhirbmkmod.client.gui;

import java.io.IOException;

import com.jiezhirbmkmod.network.ModNetwork;
import com.jiezhirbmkmod.network.msg.MessageHackAppVote;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

/**
 * v17.6 审批弹窗（服务端/管理员收到 MessageHackAppReview 后强制弹出）。
 *
 * <p>正文「玩家xxx试图开启/关闭黑客攻击玩法」，下方同意/不同意两个按钮。
 * 点击即投票并关窗：开启申请全票通过、一票否决；关闭申请任意一名同意即
 * 生效。弹窗被覆盖或错过投票窗口（超时/已出结果）时，迟到的票会收到
 * "该申请已被处理或已过期" 的聊天提示，不会有副作用。</p>
 */
public class GuiHackAppReview
extends GuiScreen {

    private static final int BTN_AGREE = 0;
    private static final int BTN_DENY = 1;

    private static final int PANEL_W = 220;
    private static final int PANEL_H = 96;

    private final String applicant;
    private final boolean close;
    private final int appId;
    /** v18.2 申请类别：0 = 黑客玩法，1 = 阈值防护链（正文键前缀随之切换） */
    private final int kind;
    private boolean voted = false;

    /** v17.6 兼容：黑客玩法申请 */
    public GuiHackAppReview(String applicant, boolean close, int appId) {
        this(applicant, close, appId, 0);
    }

    public GuiHackAppReview(String applicant, boolean close, int appId, int kind) {
        this.applicant = applicant == null || applicant.isEmpty()
                ? "?" : applicant;
        this.close = close;
        this.appId = appId;
        this.kind = kind;
    }

    /** v18.2：防护链申请的正文键在 watchapp 前缀下，文案对应替换 */
    private String key(String tail) {
        return "gui.jiezhirbmkmod." + (this.kind == 1 ? "watchapp." : "hackapp.") + tail;
    }

    @Override
    public void initGui() {
        super.initGui();
        int x = (this.width - PANEL_W) / 2;
        int y = (this.height - PANEL_H) / 2;
        this.buttonList.add(new GuiButton(BTN_AGREE, x + 10, y + PANEL_H - 40,
                (PANEL_W - 30) / 2, 20, I18n.format("gui.jiezhirbmkmod.hackapp.agree")));
        this.buttonList.add(new GuiButton(BTN_DENY, x + 20 + (PANEL_W - 30) / 2, y + PANEL_H - 40,
                (PANEL_W - 30) / 2, 20, I18n.format("gui.jiezhirbmkmod.hackapp.deny")));
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (this.voted) {
            return;
        }
        this.voted = true;
        ModNetwork.CHANNEL.sendToServer(new MessageHackAppVote(
                this.appId, button.id == BTN_AGREE));
        this.mc.displayGuiScreen(null);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        int x = (this.width - PANEL_W) / 2;
        int y = (this.height - PANEL_H) / 2;
        drawRect(x, y, x + PANEL_W, y + PANEL_H, 0xFF20242B);
        drawRect(x + 2, y + 2, x + PANEL_W - 2, y + 3, 0xFFB03A34);

        String title = I18n.format("gui.jiezhirbmkmod.hackapp.review_title");
        this.fontRenderer.drawString(title, x + PANEL_W / 2
                - this.fontRenderer.getStringWidth(title) / 2, y + 10, 0xFFE8D48A);

        String body = I18n.format(this.close
                ? this.key("review_body_close")
                : this.key("review_body_open"), this.applicant);
        this.fontRenderer.drawString(body, x + 12, y + 34, 0xFFE0E0E0);

        String rule = I18n.format(this.close
                ? this.key("rule_close")
                : this.key("rule_open"));
        this.fontRenderer.drawString(rule, x + 12, y + 48, 0xFF9FB4CC);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
