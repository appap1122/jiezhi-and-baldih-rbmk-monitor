package com.jiezhirbmkmod.item;

import com.jiezhirbmkmod.block.ScreenMultiblock;
import com.jiezhirbmkmod.block.TileCustomScreen;
import com.jiezhirbmkmod.block.TileRbmkMonitorScreen;
import com.jiezhirbmkmod.dfc.RbmkBridge;
import com.jiezhirbmkmod.item.ModItems;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.block.state.IBlockState;
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
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

public class ItemRbmkRemote
extends Item {
    private static final String K_X = "RbmkSrcX";
    private static final String K_Y = "RbmkSrcY";
    private static final String K_Z = "RbmkSrcZ";
    private static final String K_DIM = "RbmkSrcDim";
    private static final String K_NAME = "RbmkSrcName";
    private static final String K_DEV_COUNT = "RbmkDevCount";
    private static final String K_DEV = "RbmkDev";
    public static final int MAX_DEVICES = 8;
    public static final int DEVICE_SCAN_RADIUS = 3;

    public ItemRbmkRemote() {
        this.setRegistryName("jiezhirbmkmod", "rbmk_remote");
        this.setTranslationKey("rbmk_remote");
        this.setMaxStackSize(1);
        this.setCreativeTab(ModItems.TAB);
    }

    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (world.isRemote) {
            return EnumActionResult.SUCCESS;
        }
        TileEntity te = RbmkBridge.resolveDataSource(world, pos);
        ItemStack stack = player.getHeldItem(hand);
        if (RbmkBridge.isDataSource(te)) {
            boolean console = RbmkBridge.isConsole(te);
            BlockPos dpos = te.getPos();
            ItemRbmkRemote.writeTarget(stack, dpos, world.provider.getDimension(), console ? "CONSOLE" : "PART");
            // v17.4.1: bilingual, explicit feedback for step one of the bind.
            // "The remote does nothing" reports were impossible to diagnose
            // while every message here was an unformatted English action bar.
            ItemRbmkRemote.reply(player, console
                    ? "msg.jiezhirbmkmod.remote.recorded_console"
                    : "msg.jiezhirbmkmod.remote.recorded_part", ItemRbmkRemote.pos(dpos));
            return EnumActionResult.SUCCESS;
        }
        if (player.isSneaking()) {
            TileEntity dev = RbmkBridge.findDeviceAt(world, pos, 3);
            if (dev != null) {
                String name = RbmkBridge.deviceName(dev);
                BlockPos dpos = dev.getPos();
                boolean added = ItemRbmkRemote.addDevice(stack, dpos, world.provider.getDimension());
                int n = ItemRbmkRemote.deviceCount(stack);
                player.sendStatusMessage((ITextComponent)new TextComponentString(TextFormatting.AQUA + "[jiezhirbmkmod] " + TextFormatting.RESET + (added ? TextFormatting.GREEN + "Recorded device " + name + " @ " + ItemRbmkRemote.pos(dpos) : TextFormatting.YELLOW + "Already recorded: " + name) + TextFormatting.GRAY + "  (" + n + "/" + 8 + ")" + TextFormatting.DARK_GRAY + "  sneak + right-click the display to hook up"), false);
                return EnumActionResult.SUCCESS;
            }
            String why = te == null ? "no readable device within 3 blocks" : "unsupported block: " + te.getClass().getSimpleName() + " (no readable tank / energy / spin data)";
            player.sendStatusMessage((ITextComponent)new TextComponentString(TextFormatting.AQUA + "[jiezhirbmkmod] " + TextFormatting.RESET + TextFormatting.YELLOW + "Nothing to record - " + why), true);
            return EnumActionResult.SUCCESS;
        }
        if (!world.isRemote) {
            ItemRbmkRemote.reply(player, "msg.jiezhirbmkmod.remote.not_source");
        }
        return EnumActionResult.SUCCESS;
    }

    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (!player.isSneaking()) {
            return new ActionResult(EnumActionResult.PASS, (Object)stack);
        }
        if (!world.isRemote) {
            ItemRbmkRemote.clearTarget(stack);
            ItemRbmkRemote.clearDevices(stack);
            player.sendStatusMessage((ITextComponent)new TextComponentString(TextFormatting.AQUA + "[jiezhirbmkmod] " + TextFormatting.RESET + TextFormatting.YELLOW + "Remote cleared (source + devices)."), true);
        }
        return new ActionResult(EnumActionResult.SUCCESS, (Object)stack);
    }

    public boolean useOnScreen(ItemStack stack, World world, BlockPos pos, EntityPlayer player) {
        if (world.isRemote) {
            return true;
        }
        BlockPos controllerPos = ScreenMultiblock.findController(world, pos);
        if (controllerPos == null) {
            // v17.4.1: never fail silently. "The remote does nothing" was
            // indistinguishable from a binding bug, so the reason is printed.
            ItemRbmkRemote.reply(player, "msg.jiezhirbmkmod.remote.no_screen");
            return false;
        }
        TileEntity controller = world.getTileEntity(controllerPos);
        if (!(controller instanceof TileRbmkMonitorScreen)) {
            // a display whose tile entity has not been (re)created yet: force
            // a block update so Forge rebuilds it, then tell the player the
            // truth instead of doing nothing
            IBlockState st = world.getBlockState(controllerPos);
            world.notifyBlockUpdate(controllerPos, st, st, 3);
            world.markBlockRangeForRenderUpdate(controllerPos, controllerPos);
            ItemRbmkRemote.reply(player, "msg.jiezhirbmkmod.remote.no_tile");
            return true;
        }
        if (controller instanceof TileCustomScreen) {
            return this.connectDevices(stack, (TileCustomScreen)controller, world, player);
        }
        if (!ItemRbmkRemote.hasTarget(stack)) {
            ItemRbmkRemote.reply(player, "msg.jiezhirbmkmod.remote.no_target");
            return true;
        }
        int dim = stack.getTagCompound().getInteger(K_DIM);
        if (dim != world.provider.getDimension()) {
            ItemRbmkRemote.reply(player, "msg.jiezhirbmkmod.remote.dim_mismatch");
            return true;
        }
        BlockPos src = ItemRbmkRemote.readTarget(stack);
        if (src == null) {
            ItemRbmkRemote.reply(player, "msg.jiezhirbmkmod.remote.no_target");
            return true;
        }
        // bindSource reports its own success / offline result, including the
        // "bound but the console is not readable" case
        ((TileRbmkMonitorScreen)controller).bindSource(src, player);
        return true;
    }

    /** Server-side chat line; TextComponentTranslation works on both sides. */
    static void reply(EntityPlayer player, String key, Object... args) {
        if (player == null) {
            return;
        }
        player.sendStatusMessage(new TextComponentTranslation(key, args), false);
    }

    private boolean connectDevices(ItemStack stack, TileCustomScreen screen, World world, EntityPlayer player) {
        if (ItemRbmkRemote.deviceCount(stack) <= 0) {
            player.sendStatusMessage((ITextComponent)new TextComponentString(TextFormatting.AQUA + "[jiezhirbmkmod] " + TextFormatting.RESET + TextFormatting.YELLOW + "No device recorded. Sneak + right-click a tank / battery first."), false);
            return true;
        }
        int[] r = screen.importDevices(stack);
        screen.refresh();
        player.sendStatusMessage((ITextComponent)new TextComponentString(TextFormatting.AQUA + "[jiezhirbmkmod] " + TextFormatting.RESET + ItemRbmkRemote.summarize(r, screen.getSlots().size())), false);
        return true;
    }

    public static String summarize(int[] r, int used) {
        StringBuilder sb = new StringBuilder();
        sb.append(TextFormatting.GREEN).append("Hooked up ").append(r[0]).append(" reading(s)");
        if (r[1] > 0) {
            sb.append(TextFormatting.YELLOW).append("; ").append(r[1]).append(" skipped (panel full or already listed)");
        }
        if (r[2] > 0) {
            sb.append(TextFormatting.RED).append("; ").append(r[2]).append(" not found");
        }
        sb.append(TextFormatting.GRAY).append("  (").append(used).append("/").append(8).append(" slots used)");
        return sb.toString();
    }

    public static void writeTarget(ItemStack stack, BlockPos pos, int dim, String kind) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        tag.setInteger(K_X, pos.getX());
        tag.setInteger(K_Y, pos.getY());
        tag.setInteger(K_Z, pos.getZ());
        tag.setInteger(K_DIM, dim);
        tag.setString(K_NAME, kind);
    }

    public static void clearTarget(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag != null) {
            tag.removeTag(K_X);
            tag.removeTag(K_Y);
            tag.removeTag(K_Z);
            tag.removeTag(K_DIM);
            tag.removeTag(K_NAME);
        }
    }

    public static boolean hasTarget(ItemStack stack) {
        return stack.hasTagCompound() && stack.getTagCompound().hasKey(K_X);
    }

    @Nullable
    public static BlockPos readTarget(ItemStack stack) {
        if (!ItemRbmkRemote.hasTarget(stack)) {
            return null;
        }
        NBTTagCompound t = stack.getTagCompound();
        return new BlockPos(t.getInteger(K_X), t.getInteger(K_Y), t.getInteger(K_Z));
    }

    public static String readKind(ItemStack stack) {
        if (!stack.hasTagCompound()) {
            return "";
        }
        return stack.getTagCompound().getString(K_NAME);
    }

    public static boolean addDevice(ItemStack stack, BlockPos pos, int dim) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        int n = tag.getInteger(K_DEV_COUNT);
        for (int i = 0; i < n; ++i) {
            String p = K_DEV + i + "_";
            if (tag.getInteger(p + "X") != pos.getX() || tag.getInteger(p + "Y") != pos.getY() || tag.getInteger(p + "Z") != pos.getZ() || tag.getInteger(p + "Dim") != dim) continue;
            return false;
        }
        if (n >= 8) {
            return false;
        }
        String p = K_DEV + n + "_";
        tag.setInteger(p + "X", pos.getX());
        tag.setInteger(p + "Y", pos.getY());
        tag.setInteger(p + "Z", pos.getZ());
        tag.setInteger(p + "Dim", dim);
        tag.setInteger(K_DEV_COUNT, n + 1);
        return true;
    }

    public static int deviceCount(ItemStack stack) {
        if (!stack.hasTagCompound()) {
            return 0;
        }
        return Math.min(8, Math.max(0, stack.getTagCompound().getInteger(K_DEV_COUNT)));
    }

    @Nullable
    public static BlockPos deviceAt(ItemStack stack, int index) {
        String p;
        if (!stack.hasTagCompound() || index < 0 || index >= ItemRbmkRemote.deviceCount(stack)) {
            return null;
        }
        NBTTagCompound t = stack.getTagCompound();
        if (!t.hasKey((p = K_DEV + index + "_") + "X")) {
            return null;
        }
        return new BlockPos(t.getInteger(p + "X"), t.getInteger(p + "Y"), t.getInteger(p + "Z"));
    }

    public static int deviceDim(ItemStack stack, int index) {
        if (!stack.hasTagCompound() || index < 0 || index >= ItemRbmkRemote.deviceCount(stack)) {
            return Integer.MIN_VALUE;
        }
        return stack.getTagCompound().getInteger(K_DEV + index + "_Dim");
    }

    public static void clearDevices(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            return;
        }
        int n = tag.getInteger(K_DEV_COUNT);
        for (int i = 0; i < n; ++i) {
            String p = K_DEV + i + "_";
            tag.removeTag(p + "X");
            tag.removeTag(p + "Y");
            tag.removeTag(p + "Z");
            tag.removeTag(p + "Dim");
        }
        tag.removeTag(K_DEV_COUNT);
    }

    static String pos(BlockPos p) {
        return p.getX() + " " + p.getY() + " " + p.getZ();
    }

    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        if (!ItemRbmkRemote.hasTarget(stack)) {
            tooltip.add(TextFormatting.GRAY + I18n.format((String)"tooltip.jiezhirbmkmod.rbmk_remote.0", (Object[])new Object[0]));
            tooltip.add(TextFormatting.DARK_GRAY + I18n.format((String)"tooltip.jiezhirbmkmod.rbmk_remote.1", (Object[])new Object[0]));
        } else {
            BlockPos p = ItemRbmkRemote.readTarget(stack);
            tooltip.add(TextFormatting.GREEN + I18n.format((String)"tooltip.jiezhirbmkmod.rbmk_remote.2", (Object[])new Object[]{ItemRbmkRemote.readKind(stack), p == null ? "?" : ItemRbmkRemote.pos(p)}));
        }
        tooltip.add(TextFormatting.AQUA + I18n.format((String)"tooltip.jiezhirbmkmod.rbmk_remote.3", (Object[])new Object[]{ItemRbmkRemote.deviceCount(stack), 8}));
        tooltip.add(TextFormatting.DARK_GRAY + I18n.format((String)"tooltip.jiezhirbmkmod.rbmk_remote.4", (Object[])new Object[0]));
        tooltip.add(TextFormatting.AQUA + I18n.format((String)"tooltip.jiezhirbmkmod.rbmk_remote.5", (Object[])new Object[0]));
        tooltip.add(TextFormatting.AQUA + I18n.format((String)"tooltip.jiezhirbmkmod.rbmk_remote.6", (Object[])new Object[0]));
        tooltip.add(TextFormatting.DARK_GRAY + I18n.format((String)"tooltip.jiezhirbmkmod.rbmk_remote.7", (Object[])new Object[0]));
    }
}

