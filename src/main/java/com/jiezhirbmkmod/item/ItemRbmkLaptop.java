package com.jiezhirbmkmod.item;

import java.util.List;
import javax.annotation.Nullable;

import com.jiezhirbmkmod.dfc.RbmkBridge;
import com.jiezhirbmkmod.dfc.RbmkConfig;
import com.jiezhirbmkmod.network.ModNetwork;
import com.jiezhirbmkmod.network.msg.MessageTerminalCommand;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Portable terminal, v11.2: a handheld computer styled item.
 *
 * - Right-click any RBMK console / RBMK column while holding it to link
 *   to that reactor (same resolving as the RBMK remote: a part links to
 *   the console near it, a console links directly).
 * - Right-click in the air to open the command prompt GUI (same keyboard
 *   input, Tab completion and history as the floor terminal).
 * - Sneak + right-click in the air clears the link.
 *
 * The command round trip reuses MessageTerminalCommand: for the handheld
 * the "host" is a virtual position (the bound console itself), so the
 * server only trusts the item's stored console position and re-checks
 * the player distance to it, never the client.
 */
public class ItemRbmkLaptop extends Item {

    private static final String K_X = "LapConX";
    private static final String K_Y = "LapConY";
    private static final String K_Z = "LapConZ";
    private static final String K_DIM = "LapConDim";

    public ItemRbmkLaptop() {
        this.setRegistryName("jiezhirbmkmod", "rbmk_laptop");
        this.setTranslationKey("rbmk_laptop");
        this.setMaxStackSize(1);
        this.setCreativeTab(ModItems.TAB);
        this.setFull3D();
    }

    /** Link to a reactor: right-click a console (or any RBMK part). */
    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand,
            EnumFacing facing, float hitX, float hitY, float hitZ) {
        TileEntity te = RbmkBridge.resolveDataSource(world, pos);
        if (!RbmkBridge.isDataSource(te)) {
            // Right-clicking an ordinary block while holding the portable
            // terminal should still open it. onItemRightClick() is not
            // called for block hits, so open the GUI from here.
            if (!world.isRemote) {
                openTerminal(player, world);
            }
            return EnumActionResult.SUCCESS;
        }
        if (world.isRemote) {
            return EnumActionResult.SUCCESS;
        }
        BlockPos console;
        if (RbmkBridge.isConsole(te)) {
            console = te.getPos();
        } else {
            // a part was clicked: locate the console near it
            console = RbmkBridge.findConsoleNear(world, te.getPos(), RbmkConfig.consoleSearchRadius);
            if (console == null) {
                player.sendStatusMessage((ITextComponent) new TextComponentString(TextFormatting.AQUA
                        + "[jiezhirbmkmod] " + TextFormatting.RED
                        + "No RBMK console found near that part."), false);
                return EnumActionResult.SUCCESS;
            }
        }
        ItemStack stack = player.getHeldItem(hand);
        ItemRbmkLaptop.writeConsole(stack, console, world.provider.getDimension());
        player.sendStatusMessage((ITextComponent) new TextComponentString(TextFormatting.AQUA
                + "[jiezhirbmkmod] " + TextFormatting.GREEN + "Laptop connected to RBMK console @ "
                + console.getX() + " " + console.getY() + " " + console.getZ()
                + TextFormatting.GRAY + " (right-click in the air to open the prompt)"), false);
        return EnumActionResult.SUCCESS;
    }

    /** Open the terminal GUI; sneak + click clears the link. */
    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (player.isSneaking() && !world.isRemote) {
            ItemRbmkLaptop.clearConsole(stack);
            player.sendStatusMessage((ITextComponent) new TextComponentString(TextFormatting.AQUA
                    + "[jiezhirbmkmod] " + TextFormatting.YELLOW + "Laptop link cleared."), true);
            return new ActionResult<ItemStack>(EnumActionResult.SUCCESS, stack);
        }
        if (!world.isRemote) {
            openTerminal(player, world);
        }
        return new ActionResult<ItemStack>(EnumActionResult.SUCCESS, stack);
    }

    /**
     * GUI id 5, the "position" passed through the gui handler is the player
     * position; the client proxy reads the link from the held stack instead
     * of from a tile entity.
     */
    public static void openTerminal(EntityPlayer player, World world) {
        player.openGui(com.jiezhirbmkmod.RbmkMonitorMod.instance,
                com.jiezhirbmkmod.proxy.CommonProxy.GUI_LAPTOP, world,
                (int) player.posX, (int) player.posY, (int) player.posZ);
    }

    /** The portable terminal in either hand (main hand wins). */
    public static ItemStack findHeld(EntityPlayer player) {
        ItemStack main = player.getHeldItemMainhand();
        if (main.getItem() instanceof ItemRbmkLaptop) {
            return main;
        }
        ItemStack off = player.getHeldItemOffhand();
        if (off.getItem() instanceof ItemRbmkLaptop) {
            return off;
        }
        return ItemStack.EMPTY;
    }

    // ---- NBT helpers ------------------------------------------------------

    public static void writeConsole(ItemStack stack, BlockPos console, int dim) {
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

    public static void clearConsole(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag != null) {
            tag.removeTag(K_X);
            tag.removeTag(K_Y);
            tag.removeTag(K_Z);
            tag.removeTag(K_DIM);
        }
    }

    public static boolean hasConsole(ItemStack stack) {
        return stack.hasTagCompound() && stack.getTagCompound().hasKey(K_X);
    }

    @Nullable
    public static BlockPos readConsole(ItemStack stack) {
        if (!ItemRbmkLaptop.hasConsole(stack)) {
            return null;
        }
        NBTTagCompound t = stack.getTagCompound();
        return new BlockPos(t.getInteger(K_X), t.getInteger(K_Y), t.getInteger(K_Z));
    }

    public static int readDim(ItemStack stack) {
        if (!stack.hasTagCompound()) {
            return Integer.MIN_VALUE;
        }
        return stack.getTagCompound().getInteger(K_DIM);
    }

    @SideOnly(value = Side.CLIENT)
    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        BlockPos console = ItemRbmkLaptop.readConsole(stack);
        if (console == null) {
            tooltip.add(TextFormatting.GRAY + I18n.format("tooltip.jiezhirbmkmod.rbmk_laptop.0"));
        } else {
            tooltip.add(TextFormatting.GREEN + I18n.format("tooltip.jiezhirbmkmod.rbmk_laptop.1",
                    console.getX(), console.getY(), console.getZ()));
        }
        tooltip.add(TextFormatting.AQUA + I18n.format("tooltip.jiezhirbmkmod.rbmk_laptop.2"));
        tooltip.add(TextFormatting.AQUA + I18n.format("tooltip.jiezhirbmkmod.rbmk_laptop.3"));
        tooltip.add(TextFormatting.DARK_GRAY + I18n.format("tooltip.jiezhirbmkmod.rbmk_laptop.4"));
    }
}
