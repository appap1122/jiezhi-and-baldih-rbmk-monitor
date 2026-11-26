package com.jiezhirbmkmod.inventory;

import com.jiezhirbmkmod.dfc.RbmkConfig;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;

/**
 * 敏感开关申请选择 GUI 的无槽容器。v18.2 泛化为两类开关：
 *
 * <p>没有格子，只有四个窗口属性随容器机制同步到客户端：黑客开关状态
 * （字段 0）、"该玩家能否发起黑客关闭申请"（字段 1）、防护链开关状态
 * （字段 2）、"该玩家能否发起防护链关闭申请"（字段 3）。客户端 GUI 据
 * 此决定按钮文案与置灰逻辑，判定本身仍以服务端为准。</p>
 */
public class ContainerHackApp
extends Container {

    /** 窗口属性 id：0 = 黑客开关状态，1 = 可关黑客，2 = 防护链开关状态，3 = 可关防护链 */
    public static final int FIELD_ALLOW = 0;
    public static final int FIELD_CAN_CLOSE = 1;
    public static final int FIELD_WATCH = 2;
    public static final int FIELD_WATCH_CAN_CLOSE = 3;

    /** 关闭申请都要管理员/房主身份 —— 两类开关共用同一个判定 */
    private final boolean canClose;
    private final int[] cached = new int[]{-1, -1, -1, -1};

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
                this.canClose ? 1 : 0,
                RbmkConfig.allowThresholdWatch ? 1 : 0,
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
