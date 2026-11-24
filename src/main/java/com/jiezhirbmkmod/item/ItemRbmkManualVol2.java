package com.jiezhirbmkmod.item;

import java.util.List;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemWrittenBook;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.text.translation.I18n;
import net.minecraft.world.World;

/**
 * v17.6 《RBMK 应急规程》操作手册 · 卷二：只读成书，与卷一配套。
 *
 * <p>结构、只读语义、NBT 自愈、显示名锁死全部与
 * {@link ItemRbmkManual}（卷一）同款 —— 两个类刻意保持同构，不抽公共
 * 基类：两本书的页面是两份独立的安全文件，共享代码意味着改一处动两本，
 * 安全文件最怕的就是这种联动。差异只有注册名、标题、落款和页面内容。</p>
 *
 * <p>卷一管日常（上电、拼接、绑定、指令），本卷只讲异常时该做什么：
 * 事故分级、L1/L2/L3 响应、SCRAM 双通道、熔毁临灾四步、疏散清点、
 * 断链处置、断电处置、卡棒处置、复堆规程、事后报告。页面文本同样
 * 中文/英文双套硬编码（理由见卷一类注释：安全文件不做动态多语言，
 * 两套页面在同一次编译里同时定稿，防版本漂移；v17.7 起按客户端语言重印）。</p>
 */
public class ItemRbmkManualVol2
extends ItemWrittenBook {

    /** 手册标题（成书页眉用，压在原版 32 字符签名上限内） */
    private static final String TITLE = "RBMK 应急规程 卷二";
    /** v17.7 英文书名/落款（非 zh 语言的自愈重印用） */
    private static final String TITLE_EN = "RBMK Emergency Procedures II";
    /** 落款，显示在成书"Signed by"位置 */
    private static final String AUTHOR = "中控设备科";
    private static final String AUTHOR_EN = "Control Equipment Div.";
    /** 物品栏悬浮提示 */
    private static final String TOOLTIP_KEY = "tooltip.jiezhirbmkmod.rbmk_manual_vol2";

    /**
     * 全部页面。行宽与卷一同规矩：每行 ≤14 个全角字，每页控制在约
     * 170 个字符内，超出会被原版截到下一页、排版就散了。
     */
    private static final String[] PAGES = new String[] {
        "《RBMK 应急规程》\n操作手册 · 卷二\n\n固件 18.1 · 持证必读\n\n只读文档，与卷一\n配套使用。本卷只\n讲一件事：出事时\n该做什么。\n\n——中控设备科",
        "第一章 事故分级\n\nL1 注意 黄\nL2 警告 橙\nL3 严重 红\nL4 灾难 闪白\n\n颜色只认屏，\n不认记忆。看到\n什么颜色，\n按什么响应。",
        "第二章 预警响应\n\nL1/L2 三查：\n1.查事件日志\n2.查趋势走向\n3.查现场设备\n\n确认是误报也要\n留记录：误报\n本身就是数据。\n\n60 秒内回复确认。",
        "第三章 严重响应\n\nL3 红色：15 秒\n内到场，双人\n复核，一人操作\n一人读规程。\n\n单人不得处置\nL3 事件。凑不出\n第二个人，就先\n按规程 SCRAM\n再叫人。",
        "第四章 SCRAM\n\n双通道冗余：\nA 路 HBM 原生\nB 路 越限判定\n\n任一触发即停堆。\n禁止表决，\n禁止旁路，\n禁止延时。\n\n【警告】停堆\n没有商量。",
        "第五章 熔毁临灾\n\nL4 响应四步：\n1.确认矩阵全白闪\n2.确认棒已全插\n3.撤至壁垒外\n4.禁止回场\n\n前两步各 5 秒，\n超时直接执行\n第三步。",
        "第六章 疏散清点\n\n撤离路线只认\n当班计划那一套。\n\n壁垒外集合清点，\n缺人立即上报。\n\n禁止直播，\n禁止自拍，\n禁止看一眼就走。",
        "第七章 断链处置\n\n数据链丢失：\n1.status 辨别\n2.rebind 重连\n3.禁拆方块\n\n灰显数据禁止\n用于操作决策。\n\n链路反复断，\n先查负载再\n查距离。",
        "第八章 断电处置\n\n真实版屏黑屏\n多半是没电：\n查红石强度，\n查 FE 缓冲。\n\n恢复供电后按\n一次开关自检。\n\n黑屏不是故障\n信号，白闪才是。",
        "第九章 卡棒处置\n\n控制棒卡涩：\n1.降速率\n2.小步进\n3.禁急拉\n\n双棒同时卡死\n按 L3 处置。\n\n【警告】急拉\n卡棒等于给堆芯\n做深呼吸。",
        "第十章 复堆规程\n\n熔毁风险归零\n后才能复堆：\n\n1%→10%→25%\n每档观察不少\n于 5 分钟。\n\n氙毒化高峰期\n复堆，棒要\n提得更慢。",
        "第十一章 事后\n\n报告三要素：\n时间戳、\n变量清单、\n现场截图。\n\n一次只改一个\n变量的纪律，\n事故后同样适用。\n\n瞒报 = 第二起\n事故的第一行。",
        "卷终\n\n卷一管日常，\n本卷管异常。\n\n两卷都放在\n快捷栏里的人，\n才是合格\n操作员。\n\n——中控设备科 制"
    };

    /**
     * v17.7 英文页：与 {@link #PAGES} 逐页对应（页数必须一致）。行宽按
     * 原版成书 ~21 个半角字符控制，仍是硬编码安全文件（理由同卷一）。
     */
    private static final String[] PAGES_EN = new String[] {
        "RBMK Emergency\nProcedures Vol. II\n\nFirmware 18.1\nRequired reading.\n\nRead-only companion\nto Vol. I. This volume\ncovers one thing:\nwhat to do when it\ngoes wrong.\n\n— Control Equip. Div.",
        "Ch.1 Severity\n\nL1 notice   yellow\nL2 warning  orange\nL3 critical red\nL4 disaster strobe\n\nTrust the screen\ncolor, not memory.\nMatch the response\nto the color you\nsee.",
        "Ch.2 L1/L2\n\nThree checks:\n1. event log\n2. trend direction\n3. field equipment\n\nLog false alarms\ntoo: a false alarm\nis data.\n\nAcknowledge within\n60 seconds.",
        "Ch.3 L3 Response\n\nL3 red: on scene in\n15 seconds, two-\nperson rule - one\noperates, one reads\nthe procedure.\n\nNever handle L3\nalone. No second\nperson? SCRAM first,\ncall for help\nsecond.",
        "Ch.4 SCRAM\n\nDual channel:\nA native HBM guard\nB threshold judgment\n\nEither trips = stop.\nNo voting, no\nbypass, no delay.\n\n[WARN] Scram is not\nnegotiable.",
        "Ch.5 Pre-Melt\n\nL4 response:\n1. matrix all-white\n2. rods fully in\n3. retreat past barrier\n4. do not return\n\nFirst two steps get\n5 seconds each;\noverrun means do\nstep 3 now.",
        "Ch.6 Evacuation\n\nFollow the on-duty\nplan only.\n\nAssemble outside\nthe barrier, count\nheads, report any\nmissing name at\nonce.\n\nNo streaming,\nno selfies, no\npeek-and-walk-away.",
        "Ch.7 Link Lost\n\n1. status to diagnose\n2. rebind to reconnect\n3. never break blocks\n\nGrey data is\nforbidden for\noperating decisions.\n\nLink flapping? Check\nload first, distance\nsecond.",
        "Ch.8 Power Lost\n\nA black real-edition\nscreen usually means\nno power: check\nredstone strength,\ncheck FE buffer.\n\nAfter restore, cycle\nthe switch once to\nself-test.\n\nBlack is not a\nfault; white strobe\nis.",
        "Ch.9 Stuck Rods\n\n1. lower rate\n2. small steps\n3. no yanking\n\nTwo rods stuck at\nonce = handle as L3.\n\n[WARN] Yanking a\nstuck rod is giving\nthe core a deep\nbreath.",
        "Ch.10 Restart\n\nRestart only when\nmelt risk is zero:\n\n1 -> 10 -> 25%\nHold each step for\nat least 5 minutes.\n\nRestarting at xenon\npeak: pull rods even\nmore slowly.",
        "Ch.11 Aftermath\n\nReport three items:\ntimestamps, the\nvariable list, field\nscreenshots.\n\nOne-variable-at-a-\ntime still applies\nafter the accident.\n\nConcealment = the\nfirst line of the\nnext accident.",
        "Closing\n\nVol. I runs the\nday; this volume\nruns the emergency.\n\nThe operator with\nboth volumes in the\nhotbar is the one\nyou can trust.\n\n— Control Equip. Div."
    };

    public ItemRbmkManualVol2() {
        this.setRegistryName("jiezhirbmkmod", "rbmk_manual_vol2");
        this.setTranslationKey("rbmk_manual_vol2");
        // 成书本来就不可堆叠；显式写一遍，语义自文档
        this.setMaxStackSize(1);
    }

    /**
     * 出厂状态的一本书。卷二与卷一的 makeManual 结构相同但互相独立，
     * 拿书的地方（物品栏、/give、配方结果）都从这里拿。
     */
    public static ItemStack makeManual() {
        ItemStack stack = new ItemStack(ModItems.RBMK_MANUAL_VOL2);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("title", ItemRbmkManualVol2.TITLE);
        tag.setString("author", ItemRbmkManualVol2.AUTHOR);
        NBTTagList pages = new NBTTagList();
        for (int i = 0; i < ItemRbmkManualVol2.PAGES.length; ++i) {
            pages.appendTag(new NBTTagString(ItemRbmkManualVol2.PAGES[i]));
        }
        tag.setTag("pages", pages);
        // generation 0 = 原版，1 = 复制本，2 = 复制本的复制本。手册只允许 0
        tag.setInteger("generation", 0);
        stack.setTagCompound(tag);
        return stack;
    }

    /** 手册内容是否有缺失（缺 tag / 缺 pages / 页数对不上都算） */
    private static boolean isIntact(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey("pages")) {
            return false;
        }
        return tag.getTagList("pages", 8).tagCount() == ItemRbmkManualVol2.PAGES.length;
    }

    // ---- v17.7 双语辅助（与卷一同款三件套，见卷一类注释） ----

    private static String[] pagesFor(String lang) {
        return lang != null && lang.startsWith("zh") ? ItemRbmkManualVol2.PAGES : ItemRbmkManualVol2.PAGES_EN;
    }

    private static String titleFor(String lang) {
        return lang != null && lang.startsWith("zh") ? ItemRbmkManualVol2.TITLE : ItemRbmkManualVol2.TITLE_EN;
    }

    private static String authorFor(String lang) {
        return lang != null && lang.startsWith("zh") ? ItemRbmkManualVol2.AUTHOR : ItemRbmkManualVol2.AUTHOR_EN;
    }

    /** 客户端当前语言码。仅客户端路径可调用。 */
    private static String currentLang() {
        return net.minecraft.client.Minecraft.getMinecraft().getLanguageManager()
                .getCurrentLanguage().getLanguageCode();
    }

    private static boolean langMatches(ItemStack stack, String lang) {
        NBTTagCompound tag = stack.getTagCompound();
        return tag != null && tag.hasKey("Lang") && lang != null && lang.equals(tag.getString("Lang"));
    }

    /** 按语言把出厂 NBT 写进一本书（自愈与制造共用同一份内容源） */
    private static ItemStack build(ItemStack stack, String lang) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("title", ItemRbmkManualVol2.titleFor(lang));
        tag.setString("author", ItemRbmkManualVol2.authorFor(lang));
        NBTTagList pages = new NBTTagList();
        String[] src = ItemRbmkManualVol2.pagesFor(lang);
        for (int i = 0; i < src.length; ++i) {
            pages.appendTag(new NBTTagString(src[i]));
        }
        tag.setTag("pages", pages);
        tag.setInteger("generation", 0);
        tag.setString("Lang", lang == null ? "zh_cn" : lang);
        stack.setTagCompound(tag);
        return stack;
    }

    /**
     * 右键打开。与卷一同一套实现：服务端放行，客户端先做 NBT 自愈
     * 再进 {@code GuiScreenBook} 的只读模式（isUnsigned=false）。
     */
    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (!world.isRemote) {
            return new ActionResult<ItemStack>(EnumActionResult.SUCCESS, stack);
        }
        if (!ItemRbmkManualVol2.isIntact(stack)
                || !ItemRbmkManualVol2.langMatches(stack, ItemRbmkManualVol2.currentLang())) {
            // 页面缺失或语言不符就当场重建（仅客户端内存，v17.7 双语）
            ItemRbmkManualVol2.build(stack, ItemRbmkManualVol2.currentLang());
        }
        // isUnsigned=false = 原版"已签名成书"阅读模式：只有翻页和"完成"按钮
        net.minecraft.client.gui.GuiScreenBook gui =
                new net.minecraft.client.gui.GuiScreenBook(player, stack, false);
        net.minecraft.client.Minecraft.getMinecraft().displayGuiScreen(gui);
        return new ActionResult<ItemStack>(EnumActionResult.SUCCESS, stack);
    }

    /** 显示名锁死：无视 NBT display 标签，铁砧改名在视觉上无效 */
    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        return I18n.translateToLocalFormatted("item.rbmk_manual_vol2.name");
    }

    /** 悬浮提示：标明这是只读文件，且与卷一配套 */
    @Override
    public void addInformation(ItemStack stack, World worldIn, List<String> tooltip,
            net.minecraft.client.util.ITooltipFlag flagIn) {
        tooltip.add(I18n.translateToLocalFormatted(ItemRbmkManualVol2.TOOLTIP_KEY));
    }

    /** 创造模式物品栏只给出厂状态的那一本 */
    @Override
    public void getSubItems(CreativeTabs tab, NonNullList<ItemStack> items) {
        if (this.isInCreativeTab(tab)) {
            items.add(ItemRbmkManualVol2.makeManual());
        }
    }

    /** 右键读书的手部动作沿用原版读书动画 */
    @Override
    public EnumAction getItemUseAction(ItemStack stack) {
        return EnumAction.NONE;
    }
}
