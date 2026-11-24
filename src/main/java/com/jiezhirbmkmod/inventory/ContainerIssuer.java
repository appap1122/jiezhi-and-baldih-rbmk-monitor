package com.jiezhirbmkmod.inventory;

import com.jiezhirbmkmod.block.TileRbmkIssuer;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/**
 * v17.6 签发器容器：单槽（空白卡）+ 两个跟踪字段（次数 / 档位掩码）。
 *
 * <p>槽位由服务端 {@code Container} 标准机制同步，空白卡放进取出都是
 * 原版点击语义；次数与掩码走 {@code IInventory} 字段（窗口属性）在
 * {@link #detectAndSendChanges} 里比对增量下发，客户端 GUI 点按钮改的是
 * 字段，最终随"授权"一起进包在服务端落定。</p>
 */
public class ContainerIssuer
extends Container {

    /** 窗口属性 id：0 = 每卡次数，1 = 档位掩码 */
    public static final int FIELD_USES = 0;
    public static final int FIELD_MASK = 1;

    private final TileRbmkIssuer tile;
    private final int[] cached = new int[]{-1, -1};

    public ContainerIssuer(TileRbmkIssuer tile) {
        this.tile = tile;
        this.addSlotToContainer(new Slot(tile, 0, 80, 30) {
            @Override
            public boolean isItemValid(ItemStack stack) {
                // 双保险：容器层再拦一次已签发的卡
                return tile.isItemValidForSlot(0, stack);
            }

            @Override
            public int getSlotStackLimit() {
                return 16;
            }
        });
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return this.tile.isUsableByPlayer(player);
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        int[] fields = new int[]{this.tile.getUsesPerCard(), this.tile.getTierMask()};
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
        this.tile.setField(id, data);
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        // 单槽 GUI 不需要 shift-click 路由；返回空表示无事发生
        return ItemStack.EMPTY;
    }

    /** GUI 关闭时若玩家袖子里还捏着半张卡（拖拽中），归还进槽，避免卡被吞 */
    @Override
    public void onContainerClosed(EntityPlayer player) {
        super.onContainerClosed(player);
        ItemStack cursor = player.inventory.getItemStack();
        if (!cursor.isEmpty() && this.tile.isItemValidForSlot(0, cursor)
                && this.tile.getStackInSlot(0).isEmpty()) {
            this.tile.setInventorySlotContents(0, cursor.copy());
            player.inventory.setItemStack(ItemStack.EMPTY);
        }
    }
}
