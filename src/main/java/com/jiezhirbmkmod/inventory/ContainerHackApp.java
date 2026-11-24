package com.jiezhirbmkmod.inventory;

import com.jiezhirbmkmod.dfc.RbmkConfig;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;

/**
 * v17.6 黑客攻击玩法申请选择 GUI 的无槽容器。
 *
 * <p>没有格子，只有两个窗口属性随容器机制同步到客户端：当前开关状态
 * （字段 0）与"该玩家能否发起关闭申请"（字段 1，管理员/房主才为真）。
 * 客户端 GUI 据此决定按钮文案与置灰逻辑，判定本身仍以服务端为准。</p>
 */
public class ContainerHackApp
extends Container {

    /** 窗口属性 id：0 = 开关状态（1 开 / 0 关），1 = 发起人可关闭（1/0） */
    public static final int FIELD_ALLOW = 0;
    public static final int FIELD_CAN_CLOSE = 1;

    private final boolean canClose;
    private final int[] cached = new int[]{-1, -1};

    public ContainerHackApp(boolean canClose) {
        this.canClose = canClose;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return true;
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        int[] fields = new int[]{RbmkConfig.allowNormalPlayerHack ? 1 : 0,
                this.canClose ? 1 : 0};
        for (int i = 0; i < this.listeners.size(); ++i) {
            for (int f = 0; f < fields.length; ++f) {
                if (this.cached[f] != fields[f]) {
                    ((net.minecraft.inventory.IContainerListener) this.listeners.get(i))
                            .sendWindowProperty(this, f, fields[f]);
                }
            }
        }
        System.arraycopy(fields, 0, this.cached, 0, fields.length);
    }

    @Override
    public void updateProgressBar(int id, int data) {
        // 客户端容器收到属性后存进 GUI 可读的静态暂存区
        com.jiezhirbmkmod.client.gui.GuiHackAppConfirm.setField(id, data);
    }
}
