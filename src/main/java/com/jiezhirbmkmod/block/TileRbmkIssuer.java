package com.jiezhirbmkmod.block;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ITickable;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;

/**
 * v17.6 签发器（rbmk_issuer）：模组管理员的"发卡柜台"。
 *
 * <p>单格库存只收<b>空白</b>操作员工牌（{@code isItemValidForSlot} 拒收
 * 已签发的卡 —— 签发器是单行道，回炉的事不归它管）。槽里的卡是签发的
 * 弹药：授权一次消耗一张。</p>
 *
 * <p>两项签发配置存在方块实体里（{@code getField/setField} 走容器字段
 * 同步到客户端 GUI）：</p>
 * <ul>
 *   <li><b>次数</b> {@code usesPerCard}：0 = 无限制（默认），正数 = 每张
 *       签发出的卡可用的模组管理员操作次数；</li>
 *   <li><b>档位掩码</b> {@code tierMask}：bit0=低 bit1=中 bit2=高
 *       bit3=超高（默认 0xF 全选），持卡人调节屏刷新率时只能在这些档里轮换，
 *       出厂默认档始终可用。</li>
 * </ul>
 *
 * <p>授权动作本身走网络包（{@code MessageIssuerAuthorize}），服务端二次
 * 校验操作者权限后逐个发卡 —— GUI 只是前台，权限判断永远在服务端。</p>
 */
public class TileRbmkIssuer
extends net.minecraft.tileentity.TileEntity
implements IInventory, ITickable {

    /** 槽位上允许的物品：空白操作员工牌 */
    public static final String CARD_ITEM = "jiezhirbmkmod:rbmk_op_card";

    /** 每张卡的可用次数：0 = 无限制 */
    private int usesPerCard = 0;
    /** 允许档位掩码：bit0=低 .. bit3=超高，0xF = 全部 */
    private int tierMask = 0xF;

    private ItemStack card = ItemStack.EMPTY;
    private String customName;

    // v17.8 防熊：放置者归属。归属者/管理员/创造 才能拆掉柜台
    private java.util.UUID ownerUuid = null;
    private String ownerName = "";

    /** 放置时登记归属（onBlockPlacedBy 调用；null 表示清除） */
    public void setOwner(EntityPlayer player) {
        if (player == null) {
            this.ownerUuid = null;
            this.ownerName = "";
        } else {
            this.ownerUuid = player.getUniqueID();
            this.ownerName = player.getName();
        }
        this.markDirty();
    }

    /** 是否已登记归属 */
    public boolean hasOwner() {
        return this.ownerUuid != null;
    }

    /** 归属者名字（未登记返回空串） */
    public String getOwnerName() {
        return this.ownerName == null ? "" : this.ownerName;
    }

    /**
     * 拆除许可：创造模式、服务端管理员（权限等级 2 / 单人房主）、或
     * 归属者本人 —— 其他人拆不动，这是 v17.8 的防熊红线。
     */
    public boolean mayBreak(EntityPlayer player) {
        if (player == null) {
            return true; // 没有破坏者上下文（如指令清除）不拦
        }
        if (player.capabilities != null && player.capabilities.isCreativeMode) {
            return true;
        }
        if (com.jiezhirbmkmod.item.ItemRbmkOpCard.isTrueOp(player)) {
            return true;
        }
        return this.ownerUuid != null && this.ownerUuid.equals(player.getUniqueID());
    }

    // ---- IInventory：单格 ----

    @Override
    public int getSizeInventory() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return this.card.isEmpty();
    }

    @Override
    public ItemStack getStackInSlot(int index) {
        return index == 0 ? this.card : ItemStack.EMPTY;
    }

    @Override
    public ItemStack decrStackSize(int index, int count) {
        if (index != 0 || this.card.isEmpty() || count <= 0) {
            return ItemStack.EMPTY;
        }
        this.markDirty(); // 取卡也要落盘：崩服回档时空白卡不能"复活"
        if (this.card.getCount() <= count) {
            ItemStack all = this.card;
            this.card = ItemStack.EMPTY;
            return all;
        }
        ItemStack part = this.card.splitStack(count);
        if (this.card.getCount() <= 0) {
            this.card = ItemStack.EMPTY;
        }
        return part;
    }

    @Override
    public ItemStack removeStackFromSlot(int index) {
        if (index != 0) {
            return ItemStack.EMPTY;
        }
        this.markDirty();
        ItemStack all = this.card;
        this.card = ItemStack.EMPTY;
        return all;
    }

    @Override
    public void setInventorySlotContents(int index, ItemStack stack) {
        if (index != 0) {
            return;
        }
        if (!stack.isEmpty() && !this.isItemValidForSlot(0, stack)) {
            return; // 已签发的卡拒收回炉
        }
        this.card = stack;
        this.markDirty();
    }

    /** 只收空白卡：任何签发过的卡（带 Issued 标签）都拒收 */
    @Override
    public boolean isItemValidForSlot(int index, ItemStack stack) {
        if (stack == null || stack.isEmpty()
                || !(stack.getItem() instanceof com.jiezhirbmkmod.item.ItemRbmkOpCard)) {
            return false;
        }
        return !(stack.hasTagCompound() && stack.getTagCompound().hasKey("Issued"));
    }

    @Override
    public int getInventoryStackLimit() {
        return 16;
    }

    @Override
    public void markDirty() {
        // 必须标脏：槽里的卡与签发配置要随方块实体落盘（存档保存）
        super.markDirty();
    }

    @Override
    public boolean isUsableByPlayer(EntityPlayer player) {
        return true;
    }

    @Override
    public void openInventory(EntityPlayer player) {
    }

    @Override
    public void closeInventory(EntityPlayer player) {
    }

    @Override
    public int getField(int id) {
        switch (id) {
            case 0:
                return this.usesPerCard;
            case 1:
                return this.tierMask;
            default:
                return 0;
        }
    }

    @Override
    public void setField(int id, int value) {
        switch (id) {
            case 0:
                this.usesPerCard = Math.max(0, Math.min(9999, value));
                break;
            case 1:
                this.tierMask = value & 0xF;
                break;
            default:
                break;
        }
    }

    @Override
    public int getFieldCount() {
        return 2;
    }

    @Override
    public void clear() {
        this.card = ItemStack.EMPTY;
    }

    @Override
    public String getName() {
        return this.hasCustomName() ? this.customName : "container.jiezhirbmkmod.rbmk_issuer";
    }

    @Override
    public boolean hasCustomName() {
        return this.customName != null && !this.customName.isEmpty();
    }

    @Override
    public ITextComponent getDisplayName() {
        return this.hasCustomName()
                ? new net.minecraft.util.text.TextComponentString(this.customName)
                : new TextComponentTranslation(this.getName());
    }

    // ---- NBT ----

    @Override
    public void readFromNBT(NBTTagCompound c) {
        super.readFromNBT(c);
        this.card = c.hasKey("Card") ? new ItemStack(c.getCompoundTag("Card"))
                : ItemStack.EMPTY;
        // 拒绝从损坏存档里读进已签发的卡：签发器只吃空白
        if (!this.card.isEmpty() && !this.isItemValidForSlot(0, this.card)) {
            this.card = ItemStack.EMPTY;
        }
        this.usesPerCard = c.hasKey("UsesPerCard") ? c.getInteger("UsesPerCard") : 0;
        this.tierMask = c.hasKey("TierMask") ? (c.getInteger("TierMask") & 0xF) : 0xF;
        // v17.8 防熊：归属随存档恢复
        if (c.hasKey("OwnerUUID")) {
            try {
                this.ownerUuid = java.util.UUID.fromString(c.getString("OwnerUUID"));
            } catch (IllegalArgumentException ex) {
                this.ownerUuid = null;
            }
        } else {
            this.ownerUuid = null;
        }
        this.ownerName = c.hasKey("OwnerName") ? c.getString("OwnerName") : "";
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound c) {
        super.writeToNBT(c);
        if (!this.card.isEmpty()) {
            c.setTag("Card", this.card.writeToNBT(new NBTTagCompound()));
        }
        c.setInteger("UsesPerCard", this.usesPerCard);
        c.setInteger("TierMask", this.tierMask);
        if (this.ownerUuid != null) {
            c.setString("OwnerUUID", this.ownerUuid.toString());
            c.setString("OwnerName", this.ownerName);
        }
        return c;
    }

    // ---- 签发配置访问器（授权包在服务端调用）----

    public int getUsesPerCard() {
        return this.usesPerCard;
    }

    public void setUsesPerCard(int uses) {
        this.usesPerCard = Math.max(0, Math.min(9999, uses));
    }

    public int getTierMask() {
        return this.tierMask;
    }

    public void setTierMask(int mask) {
        this.tierMask = mask & 0xF;
    }

    /** 取出一张空白卡（授权消耗），没有则返回 null */
    public ItemStack takeCard() {
        ItemStack one = this.decrStackSize(0, 1);
        if (one.isEmpty()) {
            return null;
        }
        this.markDirty();
        return one;
    }

    /** 槽内空白卡余量 */
    public int cardCount() {
        return this.card.isEmpty() ? 0 : this.card.getCount();
    }

    @Override
    public void update() {
        // 签发器不需要每刻逻辑；ITickable 只为与本项目其他 Tile 风格一致
    }
}
