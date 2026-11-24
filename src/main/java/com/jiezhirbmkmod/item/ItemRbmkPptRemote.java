package com.jiezhirbmkmod.item;

import com.jiezhirbmkmod.block.TileRbmkPptScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

/**
 * v17.1 PPT clicker. Right click binds the nearest {@link TileRbmkPptScreen}
 * (controller block) and stores its dimension + position in the stack NBT; the
 * key bindings then drive that screen.
 */
public class ItemRbmkPptRemote
extends Item {

    public static final String NBT_DIM = "PptDim";
    public static final String NBT_X = "PptX";
    public static final String NBT_Y = "PptY";
    public static final String NBT_Z = "PptZ";
    private static final int BIND_RADIUS = 32;

    public ItemRbmkPptRemote() {
        this.setRegistryName("jiezhirbmkmod", "rbmk_ppt_remote");
        this.setTranslationKey("rbmk_ppt_remote");
        this.setMaxStackSize(1);
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos,
            EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (!world.isRemote) {
            net.minecraft.tileentity.TileEntity te = world.getTileEntity(pos);
            if (te instanceof TileRbmkPptScreen) {
                ItemStack stack = player.getHeldItem(hand);
                NBTTagCompound tag = stack.getTagCompound();
                if (tag == null) {
                    tag = new NBTTagCompound();
                    stack.setTagCompound(tag);
                }
                tag.setInteger(NBT_DIM, world.provider.getDimension());
                tag.setInteger(NBT_X, pos.getX());
                tag.setInteger(NBT_Y, pos.getY());
                tag.setInteger(NBT_Z, pos.getZ());
                ((TileRbmkPptScreen) te).setTotalSlides(Math.max(1, ((TileRbmkPptScreen) te).getTotalSlides()));
                player.sendStatusMessage(new TextComponentString(
                        "[PPT] 已绑定屏幕 @ " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()
                                + "（幻灯片 1/" + ((TileRbmkPptScreen) te).getTotalSlides() + "）"), true);
                return EnumActionResult.SUCCESS;
            }
        }
        return EnumActionResult.PASS;
    }

    @Override
    public net.minecraft.util.ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack held = player.getHeldItem(hand);
        if (world.isRemote) {
            return new net.minecraft.util.ActionResult<ItemStack>(EnumActionResult.SUCCESS, held);
        }
        TileRbmkPptScreen screen = TileRbmkPptScreen.findNear(world, player.getPosition(), BIND_RADIUS);
        if (screen == null) {
            player.sendStatusMessage(new TextComponentString(
                    "[PPT] " + BIND_RADIUS + " 格内没有 PPT 汇报屏，请靠近屏幕右键。"), true);
            return new net.minecraft.util.ActionResult<ItemStack>(EnumActionResult.FAIL, held);
        }
        ItemStack stack = held;
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        BlockPos p = screen.getPos();
        tag.setInteger(NBT_DIM, world.provider.getDimension());
        tag.setInteger(NBT_X, p.getX());
        tag.setInteger(NBT_Y, p.getY());
        tag.setInteger(NBT_Z, p.getZ());
        player.sendStatusMessage(new TextComponentString(
                "[PPT] 已绑定屏幕 @ " + p.getX() + ", " + p.getY() + ", " + p.getZ()
                        + "（幻灯片 " + screen.getCurrentSlide() + "/" + screen.getTotalSlides() + "）"), true);
        return new net.minecraft.util.ActionResult<ItemStack>(EnumActionResult.SUCCESS, held);
    }

    /** bound controller position, or null when this remote was never bound */
    public static BlockPos getBoundPos(ItemStack stack) {
        if (stack == null || !stack.hasTagCompound()) {
            return null;
        }
        NBTTagCompound tag = stack.getTagCompound();
        if (!tag.hasKey(NBT_X)) {
            return null;
        }
        return new BlockPos(tag.getInteger(NBT_X), tag.getInteger(NBT_Y), tag.getInteger(NBT_Z));
    }

    public static int getBoundDim(ItemStack stack) {
        if (stack == null || !stack.hasTagCompound()) {
            return Integer.MIN_VALUE;
        }
        return stack.getTagCompound().getInteger(NBT_DIM);
    }
}
