package com.jiezhirbmkmod.inventory;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;

/**
 * v17.7 巡屏 GUI 的无槽容器。绑定清单走 {@code MessagePatrolSync} 专用包
 * （坐标塞不进 16 位窗口属性），实时数据由客户端 GUI 直接读各屏自己的
 * Tile —— 这个容器存在的唯一理由是让 Forge 的 openGui 流程成立。
 */
public class ContainerPatrol
extends Container {

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return true;
    }
}
