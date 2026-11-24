package com.jiezhirbmkmod.block;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;

/**
 * v17.4 chat / action-bar lines for the real screens.
 *
 * <p>Everything here goes through {@link TextComponentTranslation} rather than
 * {@code I18n}, because the click is handled on the server: {@code I18n} is
 * client-only and would throw {@code NoClassDefFoundError} on a dedicated
 * server.</p>
 *
 * <p>Lines follow the same shape the refresh tuner already uses, so the two
 * items read consistently in chat:</p>
 *
 * <pre>（x: 12  y: 64  z: -30）显示屏 电源：开机</pre>
 */
public final class RealPowerStatus {

    private RealPowerStatus() {
    }

    /** "（x: .. y: .. z: ..）" prefix shared with the refresh tuner. */
    public static ITextComponent coords(IRealPowerTile tile) {
        // a tile that was never placed (or is being removed) has no pos yet;
        // calling getX() on it would crash the click handler with an NPE
        net.minecraft.util.math.BlockPos p = tile == null ? null : tile.getPos();
        if (p == null) {
            return new TextComponentString("（x: ?  y: ?  z: ?）");
        }
        return new TextComponentString(
                "（x: " + p.getX()
                        + "  y: " + p.getY()
                        + "  z: " + p.getZ() + "）");
    }

    /** The grey "(redstone 12 · HE 800k · FE 18400 · MAINS)" tail. */
    public static ITextComponent supply(IRealPowerTile tile) {
        int strength = 0;
        int energy = 0;
        long he = 0L;
        int source = RealPower.SOURCE_NONE;
        if (tile != null) {
            energy = tile.getRealEnergy();
            he = tile.getHeEnergy();
            source = tile.getPowerSource();
            try {
                if (tile.getWorld() != null && tile.getPos() != null) {
                    strength = tile.getWorld().getRedstonePowerFromNeighbors(tile.getPos());
                }
            }
            catch (Throwable t) {
                // an unloaded chunk must not abort the status line
                strength = 0;
            }
        }
        // v18.0 电源牌：MAINS=厂用 HE（HBM）/ EXT=外部 FE / NONE=无电，
        // 与真实主控室的电源指示牌同义 —— 看一眼就知道这块屏现在吃哪路电
        String sourceLabel = source == RealPower.SOURCE_HE ? "MAINS"
                : (source == RealPower.SOURCE_FE ? "EXT" : "NONE");
        return new TextComponentString(TextFormatting.DARK_GRAY + "（"
                + TextFormatting.DARK_GRAY + "redstone " + strength + "/" + RealPower.REDSTONE_MIN
                + " · HE " + he + "/" + RealPower.HE_CAPACITY
                + " · FE " + energy + "/" + RealPower.ENERGY_CAPACITY
                + " · " + sourceLabel + "）");
    }

    /**
     * One line describing the state the tile is in right now. Used both for the
     * click feedback and for the "cannot open, no mains" refusal.
     */
    public static ITextComponent line(IRealPowerTile tile) {
        if (tile == null) {
            return new TextComponentString("");
        }
        int state = tile.getRealPowerState();
        ITextComponent head = coords(tile);
        head.appendSibling(new TextComponentTranslation(tile.getRealNameKey()));
        head.appendSibling(new TextComponentString(" "));
        head.appendSibling(new TextComponentTranslation("msg.jiezhirbmkmod.real.power"));
        head.appendSibling(new TextComponentTranslation(RealPower.stateKey(state))
                .setStyle(new net.minecraft.util.text.Style()
                        .setColor(RealPower.stateColor(state))));
        head.appendSibling(new TextComponentString(" "));
        head.appendSibling(supply(tile));
        return head;
    }

    /** Why the click did nothing: redstone short, FE empty, or both. */
    public static ITextComponent refused(IRealPowerTile tile) {
        if (tile == null) {
            return new TextComponentString("");
        }
        ITextComponent head = coords(tile);
        head.appendSibling(new TextComponentTranslation(tile.getRealNameKey()));
        head.appendSibling(new TextComponentString(" "));
        // TextComponentTranslation, never I18n: this runs on the server
        head.appendSibling(new TextComponentTranslation("msg.jiezhirbmkmod.real.nopower")
                .setStyle(new net.minecraft.util.text.Style().setColor(TextFormatting.RED)));

        boolean redstoneOk = RealPower.hasRedstone(tile.getWorld(), tile.getPos());
        // v18.0 双源显示：HE（厂用）与 FE（外部）各给一个红绿灯，
        // 两路任一有电即算能量侧通过
        boolean heOk = tile.getHeEnergy() > 0L;
        boolean feOk = tile.getRealEnergy() > 0;
        head.appendSibling(new TextComponentString(" "));
        head.appendSibling(new TextComponentString(TextFormatting.DARK_GRAY + "（"
                + (redstoneOk ? TextFormatting.GREEN : TextFormatting.RED)
                + "redstone " + (redstoneOk ? "OK" : "LOW")
                + TextFormatting.DARK_GRAY + " · "
                + (heOk ? TextFormatting.GREEN : TextFormatting.RED)
                + "HE " + (heOk ? "OK" : "EMPTY")
                + TextFormatting.DARK_GRAY + " · "
                + (feOk ? TextFormatting.GREEN : TextFormatting.RED)
                + "FE " + (feOk ? "OK" : "EMPTY")
                + TextFormatting.DARK_GRAY + "）"));
        return head;
    }

    /** Send to the action bar, like the tuner does. */
    public static void send(EntityPlayer player, ITextComponent msg) {
        if (player != null) {
            player.sendStatusMessage(msg, true);
        }
    }
}
