package com.jiezhirbmkmod.client.gui;


import com.jiezhirbmkmod.network.NetGuard;
import com.jiezhirbmkmod.network.ModNetwork;
import com.jiezhirbmkmod.network.msg.MessageRbmkPptControl;
import java.io.IOException;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** v17.1 tiny "go to slide" prompt opened by the M key of the PPT remote. */
@SideOnly(Side.CLIENT)
public class GuiPptGoto
extends GuiScreen {

    private GuiTextField pageField;
    private String error = "";

    @Override
    public void initGui() {
        this.buttonList.clear();
        this.pageField = new GuiTextField(0, this.fontRenderer,
                this.width / 2 - 40, this.height / 2 - 10, 80, 20);
        this.pageField.setMaxStringLength(3);
        this.pageField.setFocused(true);
        this.pageField.setText("1");
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == 1) {
            this.mc.displayGuiScreen(null);
            return;
        }
        if (keyCode == 28 || keyCode == 156) {
            this.submit();
            return;
        }
        if (Character.isDigit(typedChar) || keyCode == 14) {
            this.pageField.textboxKeyTyped(typedChar, keyCode);
            this.error = "";
        }
    }

    private void submit() {
        int page;
        try {
            page = Integer.parseInt(this.pageField.getText().trim());
        }
        catch (NumberFormatException e) {
            this.error = "请输入页码";
            return;
        }
        if (page < 1) {
            this.error = "页码必须 >= 1";
            return;
        }
        NetGuard.sendToServer(new MessageRbmkPptControl(
                MessageRbmkPptControl.GOTO, page, BlockPos.ORIGIN));
        this.mc.displayGuiScreen(null);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        this.drawCenteredString(this.fontRenderer, "[PPT] 跳转到第几页", this.width / 2, this.height / 2 - 40, 0xFFFFFF);
        this.pageField.drawTextBox();
        if (!this.error.isEmpty()) {
            this.drawCenteredString(this.fontRenderer, this.error, this.width / 2, this.height / 2 + 16, 0xE74856);
        } else {
            this.drawCenteredString(this.fontRenderer, "回车确认 / Esc 取消", this.width / 2, this.height / 2 + 16, 0x888888);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
