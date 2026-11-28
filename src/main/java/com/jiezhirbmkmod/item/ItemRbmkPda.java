package com.jiezhirbmkmod.item;

import com.jiezhirbmkmod.dfc.RbmkBridge;
import com.jiezhirbmkmod.dfc.RbmkConfig;
import javax.annotation.Nullable;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;

/**
 * v18.3 portable mobile control console (PDA).
 *
 * <p>A handheld terminal with a touch-sized GUI. The stack stores the linked
 * reactor console in plain NBT ({@code boundX/Y/Z/Dim}); every privileged act
 * (binding, snapshot read, command execution) is validated and performed on
 * the server, never from client state.</p>
 *
 * <p>The GUI itself is opened client-side only: the item never asks the
 * server for a container. Snapshot and command traffic travels through
 * {@link com.jiezhirbmkmod.network.msg.MessageRbmkPda} and the existing
 * {@link com.jiezhirbmkmod.network.msg.MessageTerminalCommand}.</p>
 */
public class ItemRbmkPda extends Item {

    public static final String K_X = "boundX";
    public static final String K_Y = "boundY";
    public static final String K_Z = "boundZ";
    public static final String K_DIM = "boundDim";

    /** how far the crosshair can reach when binding a console */
    public static final double BIND_REACH = 32.0D;

    public ItemRbmkPda() {
        this.setRegistryName("jiezhirbmkmod", "rbmk_pda");
        this.setTranslationKey("rbmk_pda");
        this.setMaxStackSize(1);
        this.setCreativeTab(ModItems.TAB);
    }

    /**
     * Plain right-click opens the PDA screen. The GUI is client-only; the
     * server side returns success without doing anything (the server learns
     * what it needs from the follow-up packets).
     */
    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (world.isRemote) {
            net.minecraft.client.Minecraft.getMinecraft().displayGuiScreen(
                    new com.jiezhirbmkmod.client.gui.GuiRbmkPda(stack));
        }
        return new ActionResult<ItemStack>(EnumActionResult.SUCCESS, stack);
    }

    /** The PDA in either hand (main hand wins). */
    public static ItemStack findHeld(EntityPlayer player) {
        if (player == null) {
            return ItemStack.EMPTY;
        }
        ItemStack main = player.getHeldItemMainhand();
        if (!main.isEmpty() && main.getItem() instanceof ItemRbmkPda) {
            return main;
        }
        ItemStack off = player.getHeldItemOffhand();
        if (!off.isEmpty() && off.getItem() instanceof ItemRbmkPda) {
            return off;
        }
        return ItemStack.EMPTY;
    }

    /**
     * Resolve what the player is aiming at, with the same rule the remote
     * tools use: a console under the crosshair wins, an RBMK part resolves to
     * its nearest console, and a miss falls back to the closest console within
     * the configured search radius (so the PDA stays usable in a control room).
     */
    @Nullable
    public static BlockPos findBindTarget(EntityPlayer player, World world) {
        if (player == null || world == null) {
            return null;
        }
        RayTraceResult hit = player.rayTrace(BIND_REACH, 1.0F);
        if (hit != null && hit.typeOfHit == RayTraceResult.Type.BLOCK) {
            BlockPos pos = hit.getBlockPos();
            TileEntity te = world.getTileEntity(pos);
            if (RbmkBridge.isConsole(te)) {
                return pos;
            }
            if (RbmkBridge.isRbmkPart(te)) {
                BlockPos console = RbmkBridge.findConsoleNear(world, pos, RbmkConfig.consoleSearchRadius);
                if (console != null) {
                    return console;
                }
            }
        }
        return RbmkBridge.findConsoleNear(world, player.getPosition(), RbmkConfig.consoleSearchRadius);
    }

    // ---- NBT helpers ------------------------------------------------------

    public static void writeBound(ItemStack stack, BlockPos console, int dim) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        tag.setInteger(K_X, console.getX());
        tag.setInteger(K_Y, console.getY());
        tag.setInteger(K_Z, console.getZ());
        tag.setInteger(K_DIM, dim);
    }

    public static void clearBound(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag != null) {
            tag.removeTag(K_X);
            tag.removeTag(K_Y);
            tag.removeTag(K_Z);
            tag.removeTag(K_DIM);
        }
    }

    public static boolean hasBound(ItemStack stack) {
        if (stack == null || !stack.hasTagCompound()) {
            return false;
        }
        NBTTagCompound tag = stack.getTagCompound();
        return tag.hasKey(K_X) && tag.hasKey(K_Y) && tag.hasKey(K_Z);
    }

    @Nullable
    public static BlockPos readBoundPos(ItemStack stack) {
        if (stack == null || !stack.hasTagCompound()) {
            return null;
        }
        NBTTagCompound tag = stack.getTagCompound();
        if (!tag.hasKey(K_X) || !tag.hasKey(K_Y) || !tag.hasKey(K_Z)) {
            return null;
        }
        return new BlockPos(tag.getInteger(K_X), tag.getInteger(K_Y), tag.getInteger(K_Z));
    }

    public static int readBoundDim(ItemStack stack) {
        if (stack == null || !stack.hasTagCompound()) {
            return Integer.MIN_VALUE;
        }
        NBTTagCompound tag = stack.getTagCompound();
        if (!tag.hasKey(K_DIM)) {
            return Integer.MIN_VALUE;
        }
        return tag.getInteger(K_DIM);
    }
}
