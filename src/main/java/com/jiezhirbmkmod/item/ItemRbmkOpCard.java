package com.jiezhirbmkmod.item;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.world.World;

/**
 * v17.6 操作员工牌：模组管理员凭证，把"权限等级 2"变成一张能发出去的卡。
 *
 * <p>此前模组有两处门禁都压在权限等级 2 上：完整版刷新率调节器
 * （{@link ItemRefreshTuner#isOp}）和网络攻击玩法开关
 * （{@code TileRbmkMonitorScreen} 的 hack 门禁）。权限等级是服务器级的
 * 非黑即白 —— 房主要么把人拉进 OP 列表（附赠全部原版管理权），要么
 * 什么都给不了。工牌补的就是中间那档：发一张卡，持卡人能调模组的
 * 屏刷新档、能开黑客玩法，其余原版管理权一概没有。</p>
 *
 * <p>生命周期：</p>
 * <ul>
 *   <li><b>空白卡</b>：合成获得（纸+铁+金粒+红石），本身没有任何权限；</li>
 *   <li><b>签发</b>：只走签发器柜台（rbmk_issuer）—— 手工"潜行+右键空气
 *       签发"已在 v17.6 关闭，卡上必须留下签发器坐标、次数与档位配置；</li>
 *   <li><b>生效</b>：已签发的卡<b>拿在手上</b>（主手或副手）即视作模组
 *       管理员 —— 必须出示，塞在背包里不算；</li>
 *   <li><b>作废</b>：管理员潜行 + 右键，卡清回空白。收走权限就是
 *       把卡收回来抹掉，不留黑名单；</li>
 *   <li><b>申请</b>：连点两下右键打开黑客攻击玩法申请选择界面 —— 开启
 *       需全服主/管理员全票通过，关闭一名管理员/房主同意即生效。</li>
 * </ul>
 *
 * <p>有意不做的事：不做卡与玩家绑定（卡本来就是实体凭证，谁捡到谁能用，
 * 和现实工牌一个语义 —— 所以丢了要赶紧作废）；不做签发有效期（过期
 * 逻辑放在哪里都会有人问"为什么今天不行了"，要收权限请直接作废）。
 * 审计广播（hack 事件的 [AUDIT] 提醒）仍只发给真正的原版 OP ——
 * 工牌持有人是"被授权的操作员"，不是"被汇报的管理员"。</p>
 */
public class ItemRbmkOpCard
extends Item {

    /** 签发标记 */
    private static final String K_ISSUED = "Issued";
    /** 签发人 */
    private static final String K_BY = "IssuedBy";
    /**
     * 剩余使用次数。键缺失 = 无限制（兼容潜行+右键空气手工签发的旧卡）；
     * 0 = 已用尽（卡失效）；正数 = 每次成功的模组管理员操作扣 1。
     */
    private static final String K_USES = "UsesLeft";
    /** 允许调整的刷新档位位掩码：bit0=低 bit1=中 bit2=高 bit3=超高。
     * 键缺失 = 全档位（兼容旧卡）。默认档（出厂值）不受掩码管。 */
    private static final String K_TIERS = "TierMask";
    /**
     * v17.7 吊销体系：签发时记录的<b>持有人名</b>与<b>吊销纪元</b>。
     * 卡仍是实体凭证（谁捡到谁用），Holder 只是签发留痕 —— 但
     * {@code /rbmkrevoke <玩家>} 依据它把发给某人的卡集体判死；
     * Epoch 落后于注册表纪元的卡在有效性检查里当场失效。
     */
    private static final String K_HOLDER = "Holder";
    private static final String K_EPOCH = "Epoch";

    /**
     * 签发器产出的卡：签发人 + 次数 + 档位掩码一次写入。
     * v17.7 增加持有人纪元戳：{@code holder} 非空时记录 Holder 与
     * 签发时刻的吊销纪元，供 {@code /rbmkrevoke} 按人追杀。
     */
    public static ItemStack makeIssued(String by, int usesLeft, int tierMask, String holder) {
        ItemStack stack = new ItemStack(ModItems.RBMK_OP_CARD);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setBoolean(ItemRbmkOpCard.K_ISSUED, true);
        tag.setString(ItemRbmkOpCard.K_BY, by);
        tag.setInteger(ItemRbmkOpCard.K_USES, usesLeft);
        tag.setInteger(ItemRbmkOpCard.K_TIERS, tierMask);
        if (holder != null && !holder.isEmpty()) {
            tag.setString(ItemRbmkOpCard.K_HOLDER, holder);
            // v17.7-fix: 戳必须取「全局纪元与玩家纪元的较大者」。只取玩家纪元的
            // 话，/rbmkrevoke all 之后新签的卡（玩家纪元 0 < 全局纪元 1）会在
            // 有效性检查里当场判死 —— 签发器直接变废铁。新卡必须把两条纪元
            // 线都跨过去，之后的任何一次吊销（按人或全服）才轮到它失效。
            tag.setInteger(ItemRbmkOpCard.K_EPOCH,
                    Math.max(com.jiezhirbmkmod.dfc.BadgeRevocation.globalEpoch(),
                            com.jiezhirbmkmod.dfc.BadgeRevocation.epochOf(holder)));
        }
        stack.setTagCompound(tag);
        return stack;
    }

    /** 旧签名：不带持有人记录（创造栏卡等），兼容保留 */
    public static ItemStack makeIssued(String by, int usesLeft, int tierMask) {
        return ItemRbmkOpCard.makeIssued(by, usesLeft, tierMask, null);
    }

    public ItemRbmkOpCard() {
        this.setRegistryName("jiezhirbmkmod", "rbmk_op_card");
        this.setTranslationKey("rbmk_op_card");
        // 空白卡允许堆叠（发牌的人一口气做一叠）；签发后 NBT 使其天然独占
        this.setMaxStackSize(16);
        this.setCreativeTab(ModItems.TAB);
    }

    /** 双击判定窗口（毫秒）：两次右键间隔小于此值算"连点两下" */
    private static final long DOUBLE_CLICK_MS = 600L;
    /** 每个玩家的上一次右键时刻（服务端专用，GUI 由服务端打开） */
    private static final java.util.Map<UUID, Long> LAST_CLICK =
            new java.util.HashMap<UUID, Long>();

    /**
     * 右键行为（v17.6 定稿）：
     * <ul>
     *   <li><b>潜行 + 右键</b>：管理员作废已签发的卡（收权限）。手工签发
     *       已关闭 —— 签发只能走签发器柜台，卡上要留下签发器坐标与配置；</li>
     *   <li><b>连点两下右键</b>（600ms 内）：打开"黑客攻击玩法申请"选择
     *       界面，可选发或不发申请（开启需全票通过，关闭一名管理员即可）；</li>
     *   <li><b>单击</b>：无动作，仅记录时刻用于双击判定。</li>
     * </ul>
     */
    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (world.isRemote) {
            return new ActionResult<ItemStack>(EnumActionResult.SUCCESS, stack);
        }
        NBTTagCompound tag = stack.getTagCompound();
        boolean issued = tag != null && tag.getBoolean(ItemRbmkOpCard.K_ISSUED);
        if (player.isSneaking()) {
            // 作废：清回空白，权限随卡片实体一起收回（手工签发已关闭，仅保留作废）
            if (issued) {
                if (ItemRbmkOpCard.isTrueOp(player)) {
                    // v17.7: 审计 —— 作废事件落盘（收走权限要留痕）
                    com.jiezhirbmkmod.dfc.AuditLog.log(player.getName(), "REVOKE",
                            "card issuedBy=" + (tag != null ? tag.getString(ItemRbmkOpCard.K_BY) : "?"));
                    stack.getTagCompound().removeTag(ItemRbmkOpCard.K_ISSUED);
                    stack.getTagCompound().removeTag(ItemRbmkOpCard.K_BY);
                    stack.getTagCompound().removeTag(ItemRbmkOpCard.K_USES);
                    stack.getTagCompound().removeTag(ItemRbmkOpCard.K_TIERS);
                    player.sendStatusMessage(new TextComponentString(
                            TextFormatting.GOLD + "[jiezhirbmkmod] "
                                    + net.minecraft.util.text.translation.I18n.translateToLocalFormatted(
                                            "msg.jiezhirbmkmod.opcard.revoked")), true);
                } else {
                    player.sendStatusMessage(new TextComponentString(
                            TextFormatting.RED + "[jiezhirbmkmod] "
                                    + net.minecraft.util.text.translation.I18n.translateToLocalFormatted(
                                            "msg.jiezhirbmkmod.opcard.denied")), true);
                }
            }
            return new ActionResult<ItemStack>(EnumActionResult.SUCCESS, stack);
        }
        // 非潜行：双击检测 —— 连点两下打开黑客攻击玩法申请选择界面
        long now = System.currentTimeMillis();
        Long prev = ItemRbmkOpCard.LAST_CLICK.get(player.getUniqueID());
        ItemRbmkOpCard.LAST_CLICK.put(player.getUniqueID(), Long.valueOf(now));
        if (prev != null && now - prev.longValue() <= ItemRbmkOpCard.DOUBLE_CLICK_MS) {
            ItemRbmkOpCard.LAST_CLICK.remove(player.getUniqueID());
            player.openGui(com.jiezhirbmkmod.RbmkMonitorMod.instance,
                    com.jiezhirbmkmod.proxy.CommonProxy.GUI_HACK_APP, world,
                    (int) player.posX, (int) player.posY, (int) player.posZ);
        }
        return new ActionResult<ItemStack>(EnumActionResult.SUCCESS, stack);
    }

    /**
     * 卡片是否生效：已签发，且<b>拿在手上</b>（主手或副手），且次数未用尽。
     * 塞在背包里不出示不算 —— 工牌要出示，这是设计而非疏漏。
     */
    public static boolean isBadgeInHand(EntityPlayer player) {
        if (player == null) {
            return false;
        }
        return ItemRbmkOpCard.issuedCard(player.getHeldItemMainhand())
                || ItemRbmkOpCard.issuedCard(player.getHeldItemOffhand());
    }

    /** 剩余次数：-1 无限制（键缺失同样视为无限制），0 已用尽 */
    public static int usesLeft(ItemStack stack) {
        if (stack == null || !stack.hasTagCompound()) {
            return -1;
        }
        NBTTagCompound tag = stack.getTagCompound();
        return tag.hasKey(ItemRbmkOpCard.K_USES) ? tag.getInteger(ItemRbmkOpCard.K_USES) : -1;
    }

    /**
     * 一次成功的模组管理员操作扣 1 次。只对持<b>有限次</b>卡的玩家生效；
     * 返回扣完后这张卡是否仍然有效（false = 用尽，权限当场失效）。
     */
    public static boolean consumeUse(EntityPlayer player) {
        if (player == null) {
            return true;
        }
        ItemStack[] hands = {player.getHeldItemMainhand(), player.getHeldItemOffhand()};
        for (int i = 0; i < hands.length; ++i) {
            ItemStack stack = hands[i];
            if (!ItemRbmkOpCard.issuedCard(stack)) {
                continue;
            }
            int uses = ItemRbmkOpCard.usesLeft(stack);
            if (uses == 0) {
                return false; // 已用尽的卡不该出现在 issuedCard 里，双保险
            }
            if (uses < 0) {
                return true; // 无限制卡不扣数
            }
            stack.getTagCompound().setInteger(ItemRbmkOpCard.K_USES, uses - 1);
            return uses - 1 > 0;
        }
        return true;
    }

    /**
     * 该玩家手上的卡允许调整的刷新档位掩码（bit0=低..bit3=超高）。
     * 没有持卡或卡未限制档位时返回 0xF（全档位）。
     */
    public static int tierMaskInHand(EntityPlayer player) {
        if (player == null) {
            return 0xF;
        }
        ItemStack[] hands = {player.getHeldItemMainhand(), player.getHeldItemOffhand()};
        for (int i = 0; i < hands.length; ++i) {
            ItemStack stack = hands[i];
            if (ItemRbmkOpCard.issuedCard(stack) && stack.getTagCompound().hasKey(ItemRbmkOpCard.K_TIERS)) {
                int mask = stack.getTagCompound().getInteger(ItemRbmkOpCard.K_TIERS);
                return mask <= 0 ? 0xF : (mask & 0xF);
            }
        }
        return 0xF;
    }

    /** 按掩码过滤档位轮换序列：默认档始终保留，其余按原顺序保留被授权的档 */
    public static int[] filterTiers(int[] order, int mask) {
        if (mask >= 0xF || order == null) {
            return order;
        }
        java.util.List<Integer> out = new java.util.ArrayList<Integer>();
        for (int i = 0; i < order.length; ++i) {
            int tier = order[i];
            if (tier == 0 || (mask & (1 << (tier - 1))) != 0) {
                out.add(Integer.valueOf(tier));
            }
        }
        int[] arr = new int[out.size()];
        for (int i = 0; i < arr.length; ++i) {
            arr[i] = ((Integer) out.get(i)).intValue();
        }
        return arr.length == 1 ? new int[]{0} : arr; // 只剩默认档时也要能轮换回去
    }

    /**
     * 手上是否握着一张<b>已签发但次数用尽</b>的卡。用于在拒绝提示里区分
     * "没带工牌"和"工牌次数用完了"两种情形 —— 后者该去找管理员重签，
     * 而不是换普通版调节器。
     */
    public static boolean exhaustedBadgeInHand(EntityPlayer player) {
        if (player == null) {
            return false;
        }
        ItemStack[] hands = {player.getHeldItemMainhand(), player.getHeldItemOffhand()};
        for (int i = 0; i < hands.length; ++i) {
            ItemStack stack = hands[i];
            if (stack != null && !stack.isEmpty()
                    && stack.getItem() instanceof ItemRbmkOpCard
                    && stack.hasTagCompound()
                    && stack.getTagCompound().getBoolean(ItemRbmkOpCard.K_ISSUED)
                    && ItemRbmkOpCard.usesLeft(stack) == 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean issuedCard(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && stack.getItem() instanceof ItemRbmkOpCard
                && stack.hasTagCompound()
                && stack.getTagCompound().getBoolean(ItemRbmkOpCard.K_ISSUED)
                && ItemRbmkOpCard.usesLeft(stack) != 0
                // v17.7: 吊销纪元检查 —— 被吊销的卡在这里当场失效，判定只有这一处
                && !com.jiezhirbmkmod.dfc.BadgeRevocation.isRevoked(stack);
    }

    /**
     * 手上是否握着一张<b>已被吊销</b>的卡（Issued 在、纪元落后）。
     * 与 {@link #exhaustedBadgeInHand} 同款：用于在拒绝提示里区分
     * "没带工牌" / "次数用尽" / "已被吊销" —— 第三种该去找管理员
     * 重签，而且说明管理员动过权限。
     */
    public static boolean revokedBadgeInHand(EntityPlayer player) {
        if (player == null) {
            return false;
        }
        ItemStack[] hands = {player.getHeldItemMainhand(), player.getHeldItemOffhand()};
        for (int i = 0; i < hands.length; ++i) {
            ItemStack stack = hands[i];
            if (stack != null && !stack.isEmpty()
                    && stack.getItem() instanceof ItemRbmkOpCard
                    && stack.hasTagCompound()
                    && stack.getTagCompound().getBoolean(ItemRbmkOpCard.K_ISSUED)
                    && com.jiezhirbmkmod.dfc.BadgeRevocation.isRevoked(stack)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 模组管理员判定：原版权限（OP / 单人房主）或手持已签发工牌。
     * 调节器与黑客玩法门禁都走这一个方法 —— 判定只有一处。
     */
    public static boolean isModOp(EntityPlayer player) {
        if (ItemRbmkOpCard.isBadgeInHand(player)) {
            return true;
        }
        return ItemRbmkOpCard.isTrueOp(player);
    }

    /** 原版权限等级 2，不含工牌（避免自引用）。判定逻辑与调节器一致。 */
    public static boolean isTrueOp(EntityPlayer player) {
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
        return player.canUseCommand(2, "rbmk.opcard");
    }

    /** 创造栏给空白 + 已签发两张，方便服主直接开卡发放 */
    @Override
    public void getSubItems(CreativeTabs tab, NonNullList<ItemStack> items) {
        if (!this.isInCreativeTab(tab)) {
            return;
        }
        items.add(new ItemStack(this));
        ItemStack issued = new ItemStack(this);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setBoolean(ItemRbmkOpCard.K_ISSUED, true);
        tag.setString(ItemRbmkOpCard.K_BY, "Server");
        issued.setTagCompound(tag);
        items.add(issued);
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        tooltip.add(TextFormatting.GRAY + I18n.format("tooltip.jiezhirbmkmod.rbmk_op_card.0"));
        tooltip.add(TextFormatting.DARK_GRAY + I18n.format("tooltip.jiezhirbmkmod.rbmk_op_card.1"));
        NBTTagCompound tag = stack.getTagCompound();
        if (tag != null && tag.getBoolean(ItemRbmkOpCard.K_ISSUED)) {
            tooltip.add(TextFormatting.GREEN + I18n.format(
                    "tooltip.jiezhirbmkmod.rbmk_op_card.2", tag.getString(ItemRbmkOpCard.K_BY)));
            // 签发器出的卡：显示次数与档位限制；手工签发的卡两键都缺，不显示
            int uses = ItemRbmkOpCard.usesLeft(stack);
            if (tag.hasKey(ItemRbmkOpCard.K_USES)) {
                String usesText = I18n.format(
                        "tooltip.jiezhirbmkmod.rbmk_op_card.4",
                        uses <= 0 ? I18n.format("tooltip.jiezhirbmkmod.rbmk_op_card.6")
                                  : String.valueOf(uses));
                tooltip.add((uses == 0 ? TextFormatting.RED : TextFormatting.GRAY) + usesText);
            }
            if (tag.hasKey(ItemRbmkOpCard.K_TIERS)) {
                tooltip.add(TextFormatting.GRAY + I18n.format(
                        "tooltip.jiezhirbmkmod.rbmk_op_card.5",
                        ItemRbmkOpCard.tierMaskLabel(tag.getInteger(ItemRbmkOpCard.K_TIERS))));
            }
        } else {
            tooltip.add(TextFormatting.DARK_GRAY
                    + I18n.format("tooltip.jiezhirbmkmod.rbmk_op_card.3"));
        }
    }

    /** 掩码 → 档位名串（复用屏类已有的档位语言键，0xF 全档位时直接报"全部"） */
    public static String tierMaskLabel(int mask) {
        if (mask <= 0 || mask >= 0xF) {
            return I18n.format("tooltip.jiezhirbmkmod.rbmk_op_card.7");
        }
        int[] tiers = {1, 2, 3, 4}; // 低 / 中 / 高 / 超高，与 TileRbmkMonitorScreen 常量对齐
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tiers.length; ++i) {
            if ((mask & (1 << (tiers[i] - 1))) != 0) {
                if (sb.length() > 0) {
                    sb.append('/');
                }
                sb.append(com.jiezhirbmkmod.block.TileRbmkMonitorScreen.refreshTierKey(tiers[i]));
            }
        }
        return sb.length() == 0 ? I18n.format("tooltip.jiezhirbmkmod.rbmk_op_card.7") : sb.toString();
    }
}
