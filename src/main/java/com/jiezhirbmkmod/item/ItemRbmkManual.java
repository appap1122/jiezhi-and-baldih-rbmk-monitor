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
 * v17.6 《RBMK 核电站中控系统操作手册》：成书形态的只读指南。
 *
 * <p>和原版成书（written book）的关系与差别：</p>
 *
 * <ul>
 *   <li><b>继承 {@link ItemWrittenBook}</b>：页面渲染、翻页、成书 NBT 结构
 *       （title / author / pages / generation）全部复用原版实现，观感就是一本原版书。</li>
 *   <li><b>页面硬编码在本类里</b>（{@link #PAGES}）：不走 .lang 文件是为了换行符
 *       与格式码 100% 可控；不走 NBT 持久化是为了让页面在任何存档、任何服务器、
 *       任何客户端上都一模一样 —— 这是安全文件，不是个人笔记。</li>
 *   <li><b>强制只读</b>：{@link #onItemRightClick} 打开的是
 *       {@code GuiScreenBook(..., isUnsigned=false)}，即原版"已签名的成书"阅读模式
 *       —— 没有编辑页、没有签名页、没有"完成"以外的写入路径。原版成书本身也是
 *       签名后不可编辑，本类把这个性质从"内容约定"升级成了"结构保证"。</li>
 *   <li><b>NBT 自愈</b>：客户端打开前发现 pages 缺失（/give 空书、数据包损坏、
 *       指令清 NBT）就当场重建 —— 玩家拿到的永远是一本内容完整的书，
 *       而且重建出来的内容和出厂一模一样，等于改了个寂寞。</li>
 *   <li><b>显示名锁死</b>：{@link #getItemStackDisplayName} 无视 NBT display 标签，
 *       铁砧改名在视觉上无效（省下的经验值就当学费）。内容 + 名字 + 页面三样
 *       都动不了，这本书才是"文件"而不是"物品"。</li>
 * </ul>
 *
 * <p>页面文本硬编码（v17.7 起双语：{@link #PAGES} 中文 / {@link #PAGES_EN}
 * 英文，逐页一一对应）。模组本体是中文模组（上游 jiezhimod 代码注释即中文），
 * 默认中文页；英文玩家打开时由客户端自愈重印成英文页。不走 .lang 文件
 * 是为了换行符与格式码 100% 可控；硬编码两套而非动态拼接，是为了让任何
 * 存档、任何服务器、任何客户端上打开同一卷都一字不差 —— 安全文件最怕
 * 版本漂移，两套页面在同一次编译里同时定稿。</p>
 */
public class ItemRbmkManual
extends ItemWrittenBook {

    /** 手册标题（成书页眉用，压在原版 32 字符签名上限内） */
    private static final String TITLE = "RBMK 中控操作手册";
    /** v17.7 英文书名/落款（非 zh 语言的自愈重印用） */
    private static final String TITLE_EN = "RBMK Central Control Manual";
    /** 落款，显示在成书"Signed by"位置 */
    private static final String AUTHOR = "中控设备科";
    private static final String AUTHOR_EN = "Control Equipment Div.";
    /** 物品栏悬浮提示 */
    private static final String TOOLTIP_KEY = "tooltip.jiezhirbmkmod.rbmk_manual";

    /**
     * 全部页面。每页控制在约 170 个字符内 —— 原版书页面约 13 行 x 13 个全角字，
     * 超出部分会被截到下一页显示，排版就散了。
     */
    private static final String[] PAGES = new String[] {
        "《RBMK 核电站\n中控系统操作手册》\n\n固件 18.1 · 持证必读\n\n本手册为只读文档，\n烧录于模组本体，\n不可编辑、不可批注。\n\n——中控设备科",
        "第一章 上电规范\n\n上电五查：\n1.区块常载\n2.安装面无遮挡\n3.灯位无嵌墙\n4.能源接口在网\n5.客户端环境确认\n\n【警告】上电初始化期间\n禁止连续拆装方块，\n区块快照会进入脏状态。",
        "第二章 矩阵拼接\n\n4x3 矩阵=12 单元，\n左下角为锚点。\n\n拼接三禁：\n·朝向不一致\n·不同垂直平面\n·半成品接信号\n\n【警告】拆锚点=整体解体。\n矩阵背面留 1 格检修道。",
        "物理校准\n\n拼成后正面 3~5 格目视：\n1.四边框线连续\n2.自检画面无缝延展\n3.斜视无 z 轴闪烁\n\n校准未过的矩阵\n无告警显示资格，\n联动目标登记会被\n服务端拒绝。",
        "第三章 数据绑定\n\nHBM 快照流：\n0.5 秒/周期，\n订阅票据 UUIDv4。\n\n绑定四步：\n1.终端 BIND 按键\n2.等回执≤3 秒\n3.低功率跟随校验\n4.登记中控屏\n\n【警告】绑错对象不报错。",
        "跨维度同步\n\n跨维：≤2 tick 中继，\n序列号连续性校验。\n\n链断三种态：\nLINK_LOST 票据作废\nSTALE 灰显过期数据\n心跳静默 链路死亡\n\n【警告】灰色数据\n禁止用于操作决策。",
        "第四章 终端指令\n\n语法：动词+宾语+参数\n大小写敏感。\n\nstatus 数据链状态\nrebind 重绑视线目标\nwhoami 本机注册名\n\n命令成功不回显，\n失败回显错误码。",
        "media 指令\n\nmedia list 列出曲库\nmedia play <文件>\nmedia pause/resume\nmedia stop / volume\n\n·只认 rbmk_media 目录\n·播放中告警有抢占权\n·告警高发时段禁播\n\n【警告】主控室不是网吧。",
        "第五章 告警等级\n\nL1 注意 黄\nL2 警告 橙 60秒确认\nL3 严重 红 15秒到场\nL4 灾难 闪白 即撤\n\n报警器面板：颜色 / 转速 /\n联动 / 触发阈值 / 渲染路径。\n\n主控区必须开联动。",
        "红石联锁\n\n比较器输出：\n0=无告警 1~4=等级。\n\nSCRAM 双通道冗余：\nA=HBM 原生保护\nB=终端越限判定\n\n任一触发即停堆。\n禁止表决，禁止旁路。\n\n【警告】多数表决\n用在停堆逻辑上是谋杀。",
        "渲染路径四档\n\n自动：按环境选优\n未开光影→FBO\n已开光影→贴图\n编挂→几何\n\nFBO 屏幕空间·柔和\n几何 实体锥·零依赖\n贴图 光晕光束·光影下唯一可见\n\n所有自动灯同步重选。",
        "第六章 多媒体\n\n支持 MP4/MKV/AVI\nH.264/H.265/AAC\nyuv420p 推荐。\n\n1080p@30fps 建议上限。\n单矩阵一路通道。\n漂移>80ms 以音频为主钟。\n\n【警告】别删内置告警音。",
        "第八章 应急 SOP\n\nL4 响应四步：\n1.确认矩阵全白闪\n2.确认控制棒全插\n3.撤至剂量壁垒外\n4.禁止回场自拍\n\n数据链丢失：\n先 status 分辨，\n后 rebind，禁拆方块。\n\n复堆按 1%→10%→25%。",
        "第九章 排查总则\n\n先看日志再动手；\n一次只改一个变量；\n客户端服务端同版本；\n不可复现记录后重启。\n\n【警告】每一个被手动\n跳过的安全步骤，\n都会在事故报告里\n变成一行加粗的时间戳。",
        "附录A 配方·屏\n\n总览屏（成形）：\n铁玻铁/玻红玻/铁玻铁\n铁=铁锭 玻=玻璃板\n红=红石粉\n\n监视器（无序）：\n玻璃x3 铁 x2 红石x3\n自定义（无序）：\n玻璃x2 铁 x2 书 纸x2\n曲线屏（无序）：\n玻璃x2 铁 x2 指南针 红石",
        "附录B 配方·终端\n\n终端屏（无序）：\n总览屏+中继器+铁锭\n落地终端（成形）：\n铁火铁/玻红玻/铁块铁\n火=红石火把\n块=铁块\n遥控器（无序）：\n铁+红石+末影珍珠\n便携终端（成形）：\n铁玻铁/玻红玻/铁灯铁\n灯=红石灯",
        "附录C 配方·告警\n\n警报器（成形）：\n铁红铁/红音红/铁红铁\n音=音符盒\n报警灯（无序）：\n红石灯 铁2 红石3 玻璃\n媒体屏（无序）：\n玻璃2 铁2 音符盒 纸\nPPT 屏（无序）：\n玻璃3 铁2 书 红石\n路由器（成形）：\n铁黑铁/珍红珍/铁红铁\n黑=黑曜石 珍=珍珠",
        "卷终\n\n先看日志再动手，\n一次只改一个变量。\n\n配方只是开始，\n规程才是终点。\n\n——中控设备科 制"
    };

    /**
     * v17.7 英文页：与 {@link #PAGES} 逐页对应（页数必须一致，自愈按页数
     * 校验）。行宽按原版成书 ~21 个半角字符控制。仍是硬编码安全文件，
     * 不走 .lang —— 中文版的理由（换行符、排版、防版本漂移）对英文同样成立。
     */
    private static final String[] PAGES_EN = new String[] {
        "RBMK NPP Central\nControl Operations\nManual\n\nFirmware 18.1\nRequired reading.\n\nRead-only: burned\ninto the mod, cannot\nbe edited or signed.\n\n— Control Equip. Div.",
        "Ch.1 Power-On Rules\n\nFive checks:\n1. chunk force-loaded\n2. mount face clear\n3. lamp not inset\n4. FE port online\n5. client env verified\n\n[WARN] No repeated\ntear-downs during boot;\nchunk snapshot goes\ndirty.",
        "Ch.2 Matrix Layout\n\n4x3 = 12 cells,\nbottom-left is the\nanchor.\n\nThree bans:\n- mismatched facing\n- different planes\n- wiring half-built\n\n[WARN] Breaking the\nanchor dissolves the\nmatrix. Keep 1 block\nservice aisle behind.",
        "Physical Alignment\n\nView 3-5 blocks in\nfront after build:\n1. frame lines join\n2. boot screen seamless\n3. no z-fighting\nat an angle\n\nA misaligned matrix\nhas no alert display\nrights; linking is\nrejected server-side.",
        "Ch.3 Data Binding\n\nHBM snapshot stream:\n0.5s per cycle,\nticket UUIDv4.\n\nFour steps:\n1. terminal BIND\n2. receipt <=3s\n3. low-power check\n4. register screens\n\n[WARN] A wrong bind\ngives no error.",
        "Cross-Dimension\n\nCross-dim: <=2 tick\nrelay, serial number\ncontinuity check.\n\nLINK_LOST ticket void\nSTALE greyed data\nSilent heartbeat =\nlink dead\n\n[WARN] Grey data is\nforbidden for\noperating decisions.",
        "Ch.4 Terminal\n\nSyntax: verb + object\n+ argument. Case\nsensitive.\n\nstatus  link state\nrebind  rebind target\nwhoami  terminal name\n\nSuccess prints nothing;\nfailure prints a\ncode.",
        "media Command\n\nmedia list\nmedia play <file>\nmedia pause / resume\nmedia stop / volume\n\n- rbmk_media dir only\n- alerts preempt audio\n- no playback during\nalert-heavy periods\n\n[WARN] The control\nroom is not a cafe.",
        "Ch.5 Alert Levels\n\nL1 notice   yellow\nL2 warning  orange\n  confirm in 60s\nL3 critical red\n  on scene in 15s\nL4 disaster strobe\n  evacuate now\n\nSiren panel: color /\nrpm / link / trigger\n/ render path.\n\nLinking is mandatory\nin the control area.",
        "Redstone Interlock\n\nComparator:\n0 = no alert\n1-4 = alert level\n\nSCRAM dual channel:\nA native HBM guard\nB threshold judgment\n\nEither trips = stop.\nNo voting, no bypass.\n\n[WARN] Voting on\nscram logic is murder.",
        "Render Paths\n\nAuto: pick by env.\nNo shader -> FBO\nShader -> Glow\nCompile fail -> Cone\n\nFBO screen-space, soft\nCone real geometry\nGlow survives shaders\n\nAll auto lamps re-pick\nin sync.",
        "Ch.6 Multimedia\n\nMP4/MKV/AVI\nH.264/H.265/AAC\nyuv420p preferred.\n\n1080p@30fps cap.\nOne channel per matrix.\nDrift >80ms: audio is\nmaster clock.\n\n[WARN] Do not delete\nthe built-in alarms.",
        "Ch.8 Emergency SOP\n\nL4 response:\n1. matrix all-white strobe\n2. rods fully inserted\n3. retreat past barrier\n4. no selfies on site\n\nLink lost: status\nfirst, rebind second,\nnever break blocks.\n\nRestart: 1->10->25%.",
        "Ch.9 Troubleshooting\n\nRead logs before\nacting; change one\nvariable at a time;\nsame version on both\nsides; log what does\nnot reproduce, then\nrestart.\n\n[WARN] Every safety\nstep skipped by hand\nbecomes a bold\ntimestamp in the\nincident report.",
        "Appendix A Recipes\n\nOverview (shaped):\nI G I / G R G / I G I\nI=ingot G=glass R=red\n\nMonitor (shapeless):\nglass3 iron2 redstone3\nCustom (shapeless):\nglass2 iron2 book\npaper2\nGraph (shapeless):\nglass2 iron2 compass\nredstone",
        "Appendix B Recipes\n\nTerm screen (shapeless):\noverview + repeater\n+ iron ingot\nTerminal (shaped):\nI T I / G R G / I B I\nT=torch B=iron block\nRemote (shapeless):\niron redstone pearl\nLaptop (shaped):\nI G I / G R G / I L I\nL=redstone lamp",
        "Appendix C Recipes\n\nSiren (shaped):\nI R I / R N R / I R I\nN=note block\nAlarm light (shapeless):\nredstone lamp iron2\nredstone3 glass\nMedia screen (shapeless):\nglass2 iron2 note\nbook\nPPT screen (shapeless):\nglass3 iron2 book\nredstone\nRouter (shaped):\nI O I / P R P / I R I\nO=obsidian P=pearl",
        "Closing\n\nRead logs first;\none variable at a\ntime.\n\nRecipes are the\nstart. Procedure is\nthe end.\n\n— Control Equip. Div."
    };

    public ItemRbmkManual() {
        this.setRegistryName("jiezhirbmkmod", "rbmk_manual");
        this.setTranslationKey("rbmk_manual");
        // 成书本来就不可堆叠；显式写一遍，语义自文档
        this.setMaxStackSize(1);
    }

    /**
     * 出厂状态的一本书：内容、标题、落款全部由本方法固化。
     * 物品栏、/give、终端商店任何地方需要一本手册，都从这里拿。
     */
    public static ItemStack makeManual() {
        ItemStack stack = new ItemStack(ModItems.RBMK_MANUAL);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("title", ItemRbmkManual.TITLE);
        tag.setString("author", ItemRbmkManual.AUTHOR);
        NBTTagList pages = new NBTTagList();
        for (int i = 0; i < ItemRbmkManual.PAGES.length; ++i) {
            pages.appendTag(new NBTTagString(ItemRbmkManual.PAGES[i]));
        }
        tag.setTag("pages", pages);
        // generation 0 = 原版，1 = 复制本，2 = 复制本的复制本。手册只允许 0
        tag.setInteger("generation", 0);
        stack.setTagCompound(tag);
        return stack;
    }

    /**
     * v17.7 双语辅助三件套（卷二卷三同款，刻意不抽公共基类）：
     * <ul>
     *   <li>{@link #pagesFor}/{@code titleFor}/{@code authorFor}：按语言码选内容；</li>
     *   <li>{@link #currentLang}：客户端语言码（只允许在客户端路径调用，
     *       服务端调用会加载 client 类 —— 所有调用点都已在 isRemote 之后）；</li>
     *   <li>{@link #langMatches}：书的 NBT 语言戳是否与目标语言一致。</li>
     * </ul>
     */
    private static String[] pagesFor(String lang) {
        return lang != null && lang.startsWith("zh") ? ItemRbmkManual.PAGES : ItemRbmkManual.PAGES_EN;
    }

    private static String titleFor(String lang) {
        return lang != null && lang.startsWith("zh") ? ItemRbmkManual.TITLE : ItemRbmkManual.TITLE_EN;
    }

    private static String authorFor(String lang) {
        return lang != null && lang.startsWith("zh") ? ItemRbmkManual.AUTHOR : ItemRbmkManual.AUTHOR_EN;
    }

    /** 客户端当前语言码（zh_cn / en_us / …）。仅客户端路径可调用。 */
    private static String currentLang() {
        return net.minecraft.client.Minecraft.getMinecraft().getLanguageManager()
                .getCurrentLanguage().getLanguageCode();
    }

    /** 书的 NBT 语言戳（v17.7 起随 build 写入；旧书无戳 → 视为不符，触发重印） */
    private static boolean langMatches(ItemStack stack, String lang) {
        NBTTagCompound tag = stack.getTagCompound();
        return tag != null && tag.hasKey("Lang") && lang != null && lang.equals(tag.getString("Lang"));
    }

    /** 按语言把出厂 NBT 写进一本书（自愈与制造共用同一份内容源） */
    private static ItemStack build(ItemStack stack, String lang) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("title", ItemRbmkManual.titleFor(lang));
        tag.setString("author", ItemRbmkManual.authorFor(lang));
        NBTTagList pages = new NBTTagList();
        String[] src = ItemRbmkManual.pagesFor(lang);
        for (int i = 0; i < src.length; ++i) {
            pages.appendTag(new NBTTagString(src[i]));
        }
        tag.setTag("pages", pages);
        tag.setInteger("generation", 0);
        tag.setString("Lang", lang == null ? "zh_cn" : lang);
        stack.setTagCompound(tag);
        return stack;
    }

    /** 手册内容是否有缺失（缺 tag / 缺 pages / 页数对不上都算） */
    private static boolean isIntact(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey("pages")) {
            return false;
        }
        return tag.getTagList("pages", 8).tagCount() == ItemRbmkManual.PAGES.length;
    }

    /**
     * 右键打开。服务端直接放行（打开书是纯客户端行为，不需要包）；
     * 客户端先做 NBT 自愈再进 {@code GuiScreenBook} 的<b>只读</b>模式。
     */
    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (!world.isRemote) {
            return new ActionResult<ItemStack>(EnumActionResult.SUCCESS, stack);
        }
        if (!ItemRbmkManual.isIntact(stack)
                || !ItemRbmkManual.langMatches(stack, ItemRbmkManual.currentLang())) {
            // 页面缺失或语言不符就当场重建：/give 出来的空书、被指令清过的 NBT、
            // 换了语言的旧手册，都还原成对应语言的出厂内容。重建只发生在客户端
            // 内存里，服务端存档里的 NBT 不动 —— 但玩家看到的永远完整且是自己
            // 的语言（v17.7 双语：zh 系语言中文页，其余英文页）。
            ItemRbmkManual.build(stack, ItemRbmkManual.currentLang());
        }
        // isUnsigned=false = 原版"已签名成书"阅读模式：只有翻页和"完成"按钮，
        // 没有编辑页、没有签名页。这就是"无法修改"的全部实现。
        net.minecraft.client.gui.GuiScreenBook gui =
                new net.minecraft.client.gui.GuiScreenBook(player, stack, false);
        net.minecraft.client.Minecraft.getMinecraft().displayGuiScreen(gui);
        return new ActionResult<ItemStack>(EnumActionResult.SUCCESS, stack);
    }

    /** 显示名锁死：无视 NBT display 标签，铁砧改名在视觉上无效 */
    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        return I18n.translateToLocalFormatted("item.rbmk_manual.name");
    }

    /** 悬浮提示：标明这是只读文件，省得玩家以为少了什么功能 */
    @Override
    public void addInformation(ItemStack stack, World worldIn, List<String> tooltip,
            net.minecraft.client.util.ITooltipFlag flagIn) {
        tooltip.add(I18n.translateToLocalFormatted(ItemRbmkManual.TOOLTIP_KEY));
    }

    /**
     * 创造模式物品栏只给出厂状态的那一本 —— 不给空书，不给"半本"。
     * {@link #makeManual()} 在服务端也安全（页面硬编码，不碰任何 client 类）。
     */
    @Override
    public void getSubItems(CreativeTabs tab, NonNullList<ItemStack> items) {
        if (this.isInCreativeTab(tab)) {
            items.add(ItemRbmkManual.makeManual());
        }
    }

    /** 喝书没有意义，但右键读书的手部动作沿用原版读书动画 */
    @Override
    public EnumAction getItemUseAction(ItemStack stack) {
        return EnumAction.NONE;
    }
}
