package com.jiezhirbmkmod.item;

import java.util.List;
import java.util.Locale;

import javax.annotation.Nullable;

import com.jiezhirbmkmod.dfc.RbmkBridge;
import com.jiezhirbmkmod.dfc.RbmkConfig;
import com.jiezhirbmkmod.dfc.RbmkSnapshot;

import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

/**
 * v17.6 手持检测仪：巡场用的贴身仪表。
 *
 * <p>现在看堆况只有两条路 —— 站到屏墙前，或者跑回落地终端。巡检的人
 * 在锅炉层、汽机层是两样都没有的。检测仪补的就是这一段：手持右键
 * RBMK 控制台（或任意堆芯柱件），聊天栏直接报一组巡检读数 —— 平均柱温
 * 与熔毁阈值、最高柱温、温升速率、中子通量、蒸汽量、熔毁风险。</p>
 *
 * <p>数据链路全程<b>反射</b>，与便携终端/显示屏完全同源：{@link RbmkBridge}
 * 是本模组唯一的 HBM 访问层，靠 {@code Class.forName}/{@code Method.invoke}
 * 取快照，本类与编译期都不出现任何 HBM 类名 —— 没装 HBM 时
 * {@link RbmkBridge#isDataSource} 恒为 false，检测仪右键什么都让给原版
 * （PASS），不报错也不刷屏，和没装数据层的屏走同一套"提示未绑定"逻辑。</p>
 *
 * <p>读数按熔毁风险上色：≥50% 红、≥20% 黄、其余绿；已熔毁时整行红字加粗。
 * 只读不写 —— 检测仪不改任何状态，所以不需要 OP，也不进告警链。</p>
 */
public class ItemRbmkProbe
extends Item {

    /** 熔毁风险的告警色分界，与屏上风险盘同一套语言 */
    private static final double RISK_RED = 0.5D;
    private static final double RISK_YELLOW = 0.2D;

    public ItemRbmkProbe() {
        this.setRegistryName("jiezhirbmkmod", "rbmk_probe");
        this.setTranslationKey("rbmk_probe");
        this.setMaxStackSize(1);
        this.setCreativeTab(ModItems.TAB);
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand,
            EnumFacing facing, float hitX, float hitY, float hitZ) {
        TileEntity te = RbmkBridge.resolveDataSource(world, pos);
        if (!RbmkBridge.isDataSource(te)) {
            // 不是数据源就把点击让给原版（放方块、开门都照旧）
            return EnumActionResult.PASS;
        }
        if (world.isRemote) {
            return EnumActionResult.SUCCESS;
        }
        // 柱件向附近搜控制台，控制台直连 —— 与 ItemRbmkLaptop 的解析一致
        BlockPos console;
        if (RbmkBridge.isConsole(te)) {
            console = te.getPos();
        } else {
            console = RbmkBridge.findConsoleNear(world, te.getPos(), RbmkConfig.consoleSearchRadius);
        }
        TileEntity src = console != null ? world.getTileEntity(console) : te;
        RbmkSnapshot snap = new RbmkSnapshot();
        if (!RbmkBridge.read(src, snap)) {
            player.sendStatusMessage((ITextComponent) new TextComponentString(TextFormatting.AQUA
                    + "[jiezhirbmkmod] " + TextFormatting.RED
                    + net.minecraft.util.text.translation.I18n.translateToLocalFormatted(
                            "msg.jiezhirbmkmod.probe.noread")), false);
            return EnumActionResult.SUCCESS;
        }
        this.report(player, snap, console != null ? console : te.getPos(), console != null);
        return EnumActionResult.SUCCESS;
    }

    /** 读数输出。全部走 server-safe 的 I18n（静态方法），专用服务器上同样安全。 */
    private void report(EntityPlayer player, RbmkSnapshot d, BlockPos pos, boolean console) {
        player.sendStatusMessage((ITextComponent) new TextComponentString(TextFormatting.AQUA
                + "[jiezhirbmkmod] " + TextFormatting.WHITE
                + net.minecraft.util.text.translation.I18n.translateToLocalFormatted("msg.jiezhirbmkmod.probe.header",
                        pos.getX(), pos.getY(), pos.getZ(),
                        net.minecraft.util.text.translation.I18n.translateToLocalFormatted(console
                                ? "msg.jiezhirbmkmod.probe.src_console"
                                : "msg.jiezhirbmkmod.probe.src_part"))), false);
        player.sendStatusMessage((ITextComponent) new TextComponentString(TextFormatting.GRAY
                + net.minecraft.util.text.translation.I18n.translateToLocalFormatted("msg.jiezhirbmkmod.probe.heat",
                        fmt(d.avgHeat), fmt(d.maxHeatLimit), fmt(d.maxHeat))), false);
        player.sendStatusMessage((ITextComponent) new TextComponentString(TextFormatting.GRAY
                + net.minecraft.util.text.translation.I18n.translateToLocalFormatted("msg.jiezhirbmkmod.probe.rate",
                        fmtSigned(d.tempDelta))
                + net.minecraft.util.text.translation.I18n.translateToLocalFormatted("msg.jiezhirbmkmod.probe.fluxsteam",
                        Integer.valueOf(d.flux), fmt(d.steamTotal))), false);

        TextFormatting color = d.meltRisk >= ItemRbmkProbe.RISK_RED
                ? TextFormatting.RED
                : (d.meltRisk >= ItemRbmkProbe.RISK_YELLOW ? TextFormatting.YELLOW : TextFormatting.GREEN);
        if (d.meltdown) {
            player.sendStatusMessage((ITextComponent) new TextComponentString(TextFormatting.RED
                    + TextFormatting.BOLD.toString()
                    + net.minecraft.util.text.translation.I18n.translateToLocalFormatted(
                            "msg.jiezhirbmkmod.probe.meltdown")), false);
        } else {
            player.sendStatusMessage((ITextComponent) new TextComponentString("[jiezhirbmkmod] ")
                    .appendSibling(new TextComponentTranslation("msg.jiezhirbmkmod.probe.riskline")
                            .setStyle(new Style().setColor(TextFormatting.GRAY)))
                    .appendSibling(new TextComponentString(
                            net.minecraft.util.text.translation.I18n.translateToLocalFormatted(
                                    "msg.jiezhirbmkmod.probe.risk",
                                    Integer.valueOf((int) Math.round(d.meltRisk * 100.0D))))
                            .setStyle(new Style().setColor(color))), false);
        }
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.1f", Double.valueOf(v));
    }

    private static String fmtSigned(double v) {
        return String.format(Locale.ROOT, "%+.1f", Double.valueOf(v));
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        tooltip.add(TextFormatting.GRAY + I18n.format("tooltip.jiezhirbmkmod.rbmk_probe.0"));
        tooltip.add(TextFormatting.DARK_GRAY + I18n.format("tooltip.jiezhirbmkmod.rbmk_probe.1"));
        tooltip.add(TextFormatting.DARK_GRAY + I18n.format("tooltip.jiezhirbmkmod.rbmk_probe.2"));
    }
}
