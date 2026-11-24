package com.jiezhirbmkmod.item;

import com.jiezhirbmkmod.block.TileCustomScreen;
import com.jiezhirbmkmod.block.TileRbmkMediaScreen;
import com.jiezhirbmkmod.block.TileRbmkMonitorScreen;
import com.jiezhirbmkmod.block.TileRbmkPptScreen;
import com.jiezhirbmkmod.block.TileRbmkTermScreen;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

/**
 * v17.4 refresh-rate tuner (admin edition).
 *
 * <p>Sneak + right-click a display: the screen's refresh tier cycles through
 * Default -> Low -> Mid -> High -> Ultra and the result is printed to chat as
 * {@code （x: X  y: Y  z: Z）<screen> 调节成 <tier>}. Every screen stores its own
 * tier in NBT, so a wall of displays can run at different rates.</p>
 *
 * <p>v17.6: identity check. Only permission level 2 (op / single-player host)
 * may operate this edition at all — or a normal player showing an issued
 * operator badge with uses left in either hand (each tune costs 1 use). A
 * badge whose uses are exhausted is treated as no badge: red refusal, the
 * screen is left untouched, and the message tells the bearer to ask for a
 * reissue rather than pointing them at the Basic edition. Normal players
 * without any badge are pointed at
 * {@link ItemRefreshTunerBasic}, which walks the 4-tier civilian cycle and has
 * no high / ultra step. Ops may of course also use the basic edition — both
 * items simply route through the same {@link #useOnScreen} handler.</p>
 *
 * <p>Standing right-click is left untouched, so the normal screen GUI still
 * opens when the tuner happens to be in hand.</p>
 */
public class ItemRefreshTuner
extends Item {

    public static final String SCREEN_MONITOR = "screen.jiezhirbmkmod.monitor";
    public static final String SCREEN_TERM = "screen.jiezhirbmkmod.term";
    public static final String SCREEN_CUSTOM = "screen.jiezhirbmkmod.custom";
    public static final String SCREEN_MEDIA = "screen.jiezhirbmkmod.media";
    public static final String SCREEN_PPT = "screen.jiezhirbmkmod.ppt";

    public ItemRefreshTuner() {
        this.setRegistryName("jiezhirbmkmod", "rbmk_refresh_tuner");
        this.setTranslationKey("rbmk_refresh_tuner");
        this.setMaxStackSize(1);
        this.setCreativeTab(ModItems.TAB);
    }

    /** True for the admin edition, false for {@link ItemRefreshTunerBasic}. */
    protected boolean isAdminEdition() {
        return true;
    }

    /**
     * Tier cycle this handler walks. Both editions share it; the admin edition
     * adds high and ultra at the end.
     *
     * <p>v17.6: a restricted operator badge narrows the cycle. The issuer
     * writes a tier mask into the card (which of low / mid / high / ultra the
     * bearer may use); the default tier always stays in the cycle so a
     * restricted bearer can always put a screen back to factory. True ops are
     * unaffected.</p>
     */
    protected int[] tierOrder(EntityPlayer player) {
        return ItemRbmkOpCard.filterTiers(TileRbmkMonitorScreen.TIER_ORDER_FULL,
                ItemRbmkOpCard.tierMaskInHand(player));
    }

    /**
     * Identity gate. The admin edition is op-only, the civilian edition is open
     * to everyone (including ops, who are free to use either item).
     */
    protected boolean mayUse(EntityPlayer player) {
        return !this.isAdminEdition() || isOp(player);
    }

    /**
     * Permission level 2 = gamemaster, the level vanilla uses for /gamemode etc.
     *
     * <p>Single player is special-cased because {@code canUseCommand} answers
     * false when the host never enabled cheats, which would lock the world owner
     * out of their own tuner. The check is against {@code getServerOwner()}, not
     * against {@code isSinglePlayer()} alone: an integrated server also reports
     * {@code isSinglePlayer() == true} after "Open to LAN", so a plain
     * {@code isSinglePlayer()} test would hand every LAN guest the admin tuner.</p>
     *
     * <p>v17.6: an issued operator badge held in either hand also passes.
     * The badge is the middle tier between "no rights" and "full vanilla op":
     * issue one to a trusted player without adding them to the op list.
     * Judgment stays in one place - {@link ItemRbmkOpCard#isModOp}.</p>
     */
    protected static boolean isOp(EntityPlayer player) {
        if (player == null || player.world == null || player.world.isRemote) {
            return false;
        }
        MinecraftServer server = player.world.getMinecraftServer();
        if (server != null && server.isSinglePlayer()) {
            String owner = server.getServerOwner();
            if (owner == null || owner.isEmpty() || owner.equalsIgnoreCase(player.getName())) {
                return true;
            }
        }
        if (ItemRbmkOpCard.isBadgeInHand(player)) {
            return true;
        }
        return player.canUseCommand(2, "rbmk.refresh");
    }

    /**
     * Called by {@code ScreenMultiblock.handleUse} on both sides. The client only
     * consumes the click, the server owns the actual change.
     */
    public boolean useOnScreen(ItemStack stack, World world, BlockPos pos, EntityPlayer player) {
        if (world.isRemote) {
            return true;
        }
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileRbmkMonitorScreen)) {
            player.sendStatusMessage(new TextComponentString(
                    TextFormatting.RED + "[jiezhirbmkmod] 这个方块不是 RBMK 显示屏"), true);
            return true;
        }
        if (!this.mayUse(player)) {
            // op-only item in a normal player's hand: refuse, touch nothing.
            // TextComponentTranslation (not I18n) so this also runs on a
            // dedicated server, where the client-only I18n class is absent.
            // v17.6: a normal player CAN pass this gate by showing an issued
            // badge with uses left (both hands). The tip line says so; an
            // exhausted badge gets its own message so the bearer knows to ask
            // for a reissue instead of switching to the Basic edition.
            boolean badgeDead = ItemRbmkOpCard.exhaustedBadgeInHand(player);
            // v17.7: 被吊销的卡单独报——持有人该知道是管理员收了权限，而不是卡用完了
            boolean badgeRevoked = !badgeDead && ItemRbmkOpCard.revokedBadgeInHand(player);
            String denyKey = badgeDead
                    ? "msg.jiezhirbmkmod.refresh.usedup"
                    : (badgeRevoked
                            ? "msg.jiezhirbmkmod.refresh.revoked"
                            : "msg.jiezhirbmkmod.refresh.denied");
            ITextComponent deny = new TextComponentString(
                    "（x: " + pos.getX() + "  y: " + pos.getY() + "  z: " + pos.getZ() + "）")
                    .appendSibling(new TextComponentTranslation(screenKey(te)))
                    .appendSibling(new TextComponentString(" "))
                    .appendSibling(new TextComponentTranslation(denyKey)
                            .setStyle(new Style().setColor(TextFormatting.RED)));
            if (!badgeDead) {
                deny.appendSibling(new TextComponentString(" "))
                        .appendSibling(new TextComponentTranslation("msg.jiezhirbmkmod.refresh.deniedtip")
                                .setStyle(new Style().setColor(TextFormatting.DARK_GRAY)));
            }
            player.sendStatusMessage(deny, false);
            return true;
        }
        TileRbmkMonitorScreen screen = (TileRbmkMonitorScreen) te;
        int beforeMs = screen.getRefreshMs();
        int tier = screen.cycleRefreshTier(this.tierOrder(player));
        int ms = screen.getRefreshMs();
        // v17.7: 审计 —— 调节事件落盘（谁动了哪块屏的刷新档）
        com.jiezhirbmkmod.dfc.AuditLog.log(player.getName(), "TUNER",
                "screen @ " + pos.getX() + "," + pos.getY() + "," + pos.getZ()
                        + " tier=" + tier + " cardHolder=" + !ItemRbmkOpCard.isTrueOp(player));
        // v17.6: 持工牌（非真 OP）的每一次成功调节都扣 1 次使用次数；
        // 扣到 0 时当场失效，下一次调节走拒绝分支。扣尽的那一刻给一条
        // 金色提醒，让持卡人知道这张卡已经没有权限了。
        if (!ItemRbmkOpCard.isTrueOp(player) && !ItemRbmkOpCard.consumeUse(player)) {
            player.sendStatusMessage(new TextComponentTranslation(
                    "msg.jiezhirbmkmod.refresh.usedup")
                    .setStyle(new Style().setColor(TextFormatting.GOLD)), false);
        }

        ITextComponent msg = new TextComponentString(
                "（x: " + pos.getX() + "  y: " + pos.getY() + "  z: " + pos.getZ() + "）")
                .appendSibling(new TextComponentTranslation(screenKey(te)))
                .appendSibling(new TextComponentString(" "))
                .appendSibling(new TextComponentTranslation("msg.jiezhirbmkmod.refresh.action"))
                .appendSibling(new TextComponentString(" "))
                .appendSibling(new TextComponentTranslation(TileRbmkMonitorScreen.refreshTierKey(tier)));
        msg.appendSibling(new TextComponentString(TextFormatting.DARK_GRAY + "  (" + ms + " ms)"));
        if (ms == beforeMs) {
            // only reachable when the server owner set defaultRefreshMs to one of
            // the fixed tiers; say so instead of letting the click look broken
            msg.appendSibling(new TextComponentString(TextFormatting.DARK_GRAY + " "));
            msg.appendSibling(new TextComponentTranslation("msg.jiezhirbmkmod.refresh.same"));
        }
        player.sendStatusMessage(msg, false);
        return true;
    }

    private static String screenKey(TileEntity te) {
        if (te instanceof TileRbmkPptScreen) {
            return SCREEN_PPT;
        }
        if (te instanceof TileRbmkMediaScreen) {
            return SCREEN_MEDIA;
        }
        if (te instanceof TileRbmkTermScreen) {
            return SCREEN_TERM;
        }
        if (te instanceof TileCustomScreen) {
            return SCREEN_CUSTOM;
        }
        return SCREEN_MONITOR;
    }

    /**
     * v17.6 公开版屏名解析。配置拷贝卡与告警扩音器也要在聊天栏里报
     * "这是哪块屏"，判断逻辑与 {@link #screenKey} 完全一致 —— 判断只有一处，
     * 以后新增屏幕类型不会出现两种叫法。
     */
    public static String screenKeyOf(TileEntity te) {
        return ItemRefreshTuner.screenKey(te);
    }

    /** Language-file prefix for the four tooltip lines. */
    protected String tooltipBase() {
        return "tooltip.jiezhirbmkmod.rbmk_refresh_tuner";
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        String key = this.tooltipBase();
        tooltip.add(TextFormatting.GRAY + I18n.format(key + ".0"));
        tooltip.add(TextFormatting.DARK_GRAY + I18n.format(key + ".1"));
        tooltip.add(TextFormatting.DARK_GRAY + I18n.format(key + ".2"));
        tooltip.add((this.isAdminEdition() ? TextFormatting.DARK_RED : TextFormatting.DARK_GRAY)
                + I18n.format(key + ".3"));
    }
}
