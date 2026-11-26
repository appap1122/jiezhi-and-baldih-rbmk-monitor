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
 * v17.8 《RBMK 管理员手册》· 卷三：权限体系的收尾读物。
 *
 * <p>结构、只读语义、NBT 自愈、显示名锁死全部与
 * {@link ItemRbmkManual}（卷一）/ {@link ItemRbmkManualVol2}（卷二）同款
 * —— 三卷刻意保持同构，不抽公共基类：三本书的页面是三份独立的安全
 * 文件，共享代码意味着改一处动三本，安全文件最怕的就是这种联动。</p>
 *
 * <p>卷一管日常，卷二管异常，本卷管<b>人和权限</b>：工牌签发与次数/
 * 档位、审计日志、巡屏中控、曲线回放、黑客攻击审批流、模板卡分工、
 * 防熊清单。页面文本同样中文硬编码（理由见卷一类注释：安全文件不做
 * 多语言，防止版本漂移）。</p>
 */
public class ItemRbmkManualVol3
extends ItemWrittenBook {

    /** 手册标题（成书页眉用，压在原版 32 字符签名上限内） */
    private static final String TITLE = "RBMK 管理员手册 卷三";
    /** v17.7 英文书名/落款（非 zh 语言的自愈重印用） */
    private static final String TITLE_EN = "RBMK Admin Handbook Vol. III";
    /** 落款，显示在成书"Signed by"位置 */
    private static final String AUTHOR = "中控设备科";
    private static final String AUTHOR_EN = "Control Equipment Div.";
    /** 物品栏悬浮提示 */
    private static final String TOOLTIP_KEY = "tooltip.jiezhirbmkmod.rbmk_manual_vol3";

    /** 全部页面。行宽与卷一同规矩：每行 ≤14 个全角字，每页 ≤170 字符。 */
    private static final String[] PAGES = new String[] {
        "《RBMK 管理员手册》\n卷三 · 人与权限\n\n固件 18.2 · 管理员\n上岗必读\n\n只读文档。卷一管\n日常，卷二管异常，\n本卷只讲一件事：\n管好人，别管坏事。\n\n——中控设备科",
        "第一章 权限体系\n\n管理员三件套：\n1.签发器（发卡柜台）\n2.操作员工牌\n3.审计日志\n\n权限只认服务端。\nGUI 只是前台，\n校验永远在服务端\n二次执行。",
        "第二章 签发规程\n\n签发器四步：\n1.放入空白工牌\n2.定次数与档位\n3.勾选在线玩家\n4.点授权\n\n次数 0 = 无限；\n正数 = 每张卡可\n用的管理员操作\n次数，用完即废。\n\n已签发的卡拒收回炉。",
        "第三章 工牌语义\n\n持卡 ≠ 管理员：\n卡只开放刷新率\n调节与门禁，\n不开放指令。\n\n卡被没收/作废，\n权限即刻失效。\n\n给谁发卡，谁\n就有一次「越权\n查看」的机会 ——\n发卡即授权。",
        "第四章 审计日志\n\n/rbmkaudit [关键词]\n[条数]（权限 2）\n\n吊销工牌：\n/rbmkrevoke <玩家|all>\n\n落盘：存档目录\nrbmk_audit.log，4MB\n自动轮转。\n\n签发、吊销、密码、\n攻击、粘贴、签发器\n被拆——全部留痕。\n\n日志不删，只轮转。",
        "第五章 巡屏中控\n\n潜行+右键中控：\n自动扫描绑定\n周边监视屏。\n\nGUI 点行选中，\n自动轮播每 5 秒\n换一块；谁告警\n看谁，立刻跳转。\n\n详情面板读的是\n客户端自采历史，\n零额外网络包。",
        "第六章 曲线回放\n\n盯着通电曲线屏\n滚滚轮：\n上滚 = 倒带\n每格 1 秒\n下滚 = 前进\n滚回尽头 = 实时\n\n回放时画面黄字\n横幅，曲线与末值\n都停在回放点。\n\n滚轮不会误切\n热键栏。",
        "第七章 攻击审批\n\n黑客攻击流程：\n申请 → 管理员\n审批 → 投票 →\n结果回信。\n\n管理员可否决，\n否决留审计痕。\n\n拿不准就否决：\n审批通过是\n例外，否决\n才是常态。",
        "第八章 模板分工\n\n配置拷贝卡：\n15 个调屏键，\n精细搬家。\n\n配方模板卡：\n整张配方黑名单\n快照，含监视槽\n清单与媒体文件。\n\n调参数用前者，\n复制一整套屏用\n后者。粘贴留痕。",
        "第九章 防熊清单\n\n签发器三重门：\n1.GUI 只对管理员\n2.非归属/管理员/\n创造 拆不动\n3.爆炸免疫\n\n被拆必留审计痕。\n\n卡是凭证：\n凭证只能落在\n签发流程里，\n不能落在地上。",
        "第十章 排障\n\n门禁红字：先查\n自己权限，再查\n卡是否用尽。\n\n审计查不到：\n先看轮转 .old。\n\n巡屏离线槽：\n屏被拆或区块\n未加载，服务端\n绑定不受影响。",
        "附录A 配方·权限\n\n签发器（无序）：\n铁2 红石2 玻璃 石按钮\n空白工牌（无序）：\n纸2 铁 金粒 红石\n配置拷贝卡（无序）：\n纸2 铁 红石 中继器\n配方模板卡（无序）：\n拷贝卡 纸2 金锭\n\n【警告】合成出的只是\n空白卡：放签发器\n签发后才有效。",
        "附录B 配方·巡检\n\n手持检测仪（无序）：\n铁2 红石 玻璃 红石火把\n告警扩音器（无序）：\n铁2 音符盒 红石2 皮革\n巡屏中控（无序）：\n铁3 红石2 玻璃 监视器\n调节器·完整（成形）：\n铁红铁/红玻红/铁时铁\n时=时钟",
        "附录C 配方·手册\n\n调节器·普通（成形）：\n铁红铁/红玻红/铁钮铁\n钮=石按钮\n本卷（无序）：\n书 黑染料 纸2 铁锭\n卷二（无序）：\n书 黑染料 纸2\n\n合成给出的只是卡和\n纸：签发与登记\n永远在柜台完成。",
        "卷终\n\n卷一管日常，\n卷二管异常，\n本卷管人。\n\n三卷齐备，\n夜班才敢\n交给你。\n\n——中控设备科 制"
    };

    /**
     * v17.7 英文页：与 {@link #PAGES} 逐页对应（页数必须一致）。行宽按
     * 原版成书 ~21 个半角字符控制，仍是硬编码安全文件（理由同卷一）。
     */
    private static final String[] PAGES_EN = new String[] {
        "RBMK Administrator\nHandbook Vol. III\n\nFirmware 18.2\nFor admins.\n\nRead-only. Vol. I\nruns the day, Vol. II\nthe emergency; this\none is about people\nand permissions.\n\n— Control Equip. Div.",
        "Ch.1 Permission\nModel\n\nThe admin trio:\n1. issuer (card counter)\n2. operator badge\n3. audit log\n\nThe server decides.\nGUIs are front desks;\nchecks always run\nagain server-side.",
        "Ch.2 Issuing\n\nIssuer, four steps:\n1. insert blank badges\n2. set uses and tiers\n3. tick online players\n4. press Authorize\n\nUses 0 = unlimited;\npositive = actions\nper card, then dead.\n\nIssued cards refuse\nto be re-melted.",
        "Ch.3 Badge Rules\n\nHolding a badge is\nnot being an admin:\nit opens tier tuning\nand gates only, no\ncommands.\n\nConfiscate or revoke\nthe card - rights die\nwith it instantly.\n\nIssuing a card grants\none peek beyond tier.\nIssuing = trust.",
        "Ch.4 Audit & Revoke\n\n/rbmkaudit [words][n]\n(perm level 2)\n\nRevoke badges:\n/rbmkrevoke <player|all>\n\nOn disk: rbmk_audit.log\nin the save dir,\n4MB rotation.\n\nIssuing, revoke,\npasswords, attacks,\npaste, issuer broken\n- all traced.\n\nLogs rotate, never\ndisappear.",
        "Ch.5 Patrol Hub\n\nSneak+right-click the\nhub: it scans and\nbinds nearby screens.\n\nClick a row to select;\nauto-rotate every 5s;\nwhoever alarms gets\nwatched, instantly.\n\nThe detail panel reads\nclient-sampled history:\nzero extra packets.",
        "Ch.6 Graph Replay\n\nScroll on a powered\ngraph screen:\nup = rewind (1s per\nnotch); down = forward;\nto the end = live.\n\nA yellow banner shows\nwhile scrubbing;\ncurves and end values\nstop at the replay\npoint.\n\nThe wheel never pages\nyour hotbar.",
        "Ch.7 Attack\nApproval\n\nHack flow: request ->\nadmin review -> votes\n-> result letter.\n\nAdmins may veto;\nveto is audited.\n\nUnsure? Veto.\nPassing is the\nexception; veto is\nthe norm.",
        "Ch.8 Card vs\nTemplate\n\nConfig card: 15\ntuning keys, fine-\ngrained moves.\n\nRecipe card: whole-\nrecipe snapshot incl.\nwatch slots and media\nfiles.\n\nTune with the former,\nclone screens with\nthe latter. Paste is\naudited.",
        "Ch.9 Grief\nChecklist\n\nIssuer triple gate:\n1. GUI admin-only\n2. only owner/admin/\ncreative may break\n3. explosion proof\n\nAny break attempt is\naudited.\n\nCards are credentials:\nthey leave only\nthrough the issuing\nflow, never the floor.",
        "Ch.10 Troubleshooting\n\nRed gate text: check\nyour rights, then\nwhether the card ran\nout or was revoked.\n\nAudit empty: check\nthe .old rotation.\n\nPatrol offline slots:\nscreen removed or\nchunk unloaded; the\nserver binds are\nunaffected.",
        "App.A Recipes\n\nIssuer (shapeless):\niron2 redstone2 glass\nstone button\nBlank badge (shapeless):\npaper2 iron gold\nnugget redstone\nConfig card (shapeless):\npaper2 iron redstone\nrepeater\nRecipe card (shapeless):\nconfig card paper2\ngold ingot",
        "App.B Recipes\n\nProbe (shapeless):\niron2 redstone glass\nredstone torch\nSiren horn (shapeless):\niron2 note block\nredstone2 leather\nPatrol hub (shapeless):\niron3 redstone2 glass\nmonitor\nTuner full (shaped):\nI R I / R G R / I C I\nC=clock",
        "App.C Recipes\n\nTuner basic (shaped):\nI R I / R G R / I B I\nB=stone button\nThis volume (shapeless):\nbook black dye paper2\niron ingot\nVol. II (shapeless):\nbook black dye paper2\n\nCrafting gives blanks:\nissuing happens at\nthe counter only.",
        "Closing\n\nVol. I runs the day,\nVol. II the\nemergency, this one\nthe people.\n\nWith all three on\nthe shelf, the night\nshift can finally be\nhanded over.\n\n— Control Equip. Div."
    };

    public ItemRbmkManualVol3() {
        this.setRegistryName("jiezhirbmkmod", "rbmk_manual_vol3");
        this.setTranslationKey("rbmk_manual_vol3");
        this.setMaxStackSize(1);
    }

    /**
     * 出厂状态的一本书。拿书的地方（物品栏、/give、配方结果）都从这里拿。
     */
    public static ItemStack makeManual() {
        return ItemRbmkManualVol3.build(
                new ItemStack(ModItems.RBMK_MANUAL_VOL3));
    }

    /** 把出厂 NBT 写进一本书（自愈与制造共用同一份内容源） */
    private static ItemStack build(ItemStack stack) {
        return ItemRbmkManualVol3.build(stack, "zh_cn");
    }

    /** v17.7 按语言写出厂 NBT（Lang 戳 + 对应语言的标题/落款/页面） */
    private static ItemStack build(ItemStack stack, String lang) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("title", lang != null && lang.startsWith("zh")
                ? ItemRbmkManualVol3.TITLE : ItemRbmkManualVol3.TITLE_EN);
        tag.setString("author", lang != null && lang.startsWith("zh")
                ? ItemRbmkManualVol3.AUTHOR : ItemRbmkManualVol3.AUTHOR_EN);
        NBTTagList pages = new NBTTagList();
        String[] src = lang != null && lang.startsWith("zh")
                ? ItemRbmkManualVol3.PAGES : ItemRbmkManualVol3.PAGES_EN;
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
        return tag.getTagList("pages", 8).tagCount() == ItemRbmkManualVol3.PAGES.length;
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

    /**
     * 右键打开。与卷一/卷二同一套实现：服务端放行，客户端先做 NBT 自愈
     * 再进 {@code GuiScreenBook} 的只读模式。
     */
    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (!world.isRemote) {
            return new ActionResult<ItemStack>(EnumActionResult.SUCCESS, stack);
        }
        if (!ItemRbmkManualVol3.isIntact(stack)
                || !ItemRbmkManualVol3.langMatches(stack, ItemRbmkManualVol3.currentLang())) {
            // 页面缺失或语言不符就当场重建（仅客户端内存，v17.7 双语）
            ItemRbmkManualVol3.build(stack, ItemRbmkManualVol3.currentLang());
        }
        net.minecraft.client.gui.GuiScreenBook gui =
                new net.minecraft.client.gui.GuiScreenBook(player, stack, false);
        net.minecraft.client.Minecraft.getMinecraft().displayGuiScreen(gui);
        return new ActionResult<ItemStack>(EnumActionResult.SUCCESS, stack);
    }

    /** 显示名锁死：无视 NBT display 标签，铁砧改名在视觉上无效 */
    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        return I18n.translateToLocalFormatted("item.rbmk_manual_vol3.name");
    }

    /** 悬浮提示：标明这是只读文件，且与卷一卷二配套 */
    @Override
    public void addInformation(ItemStack stack, World worldIn, List<String> tooltip,
            net.minecraft.client.util.ITooltipFlag flagIn) {
        tooltip.add(I18n.translateToLocalFormatted(ItemRbmkManualVol3.TOOLTIP_KEY));
    }

    /** 创造模式物品栏只给出厂状态的那一本 */
    @Override
    public void getSubItems(CreativeTabs tab, NonNullList<ItemStack> items) {
        if (this.isInCreativeTab(tab)) {
            items.add(ItemRbmkManualVol3.makeManual());
        }
    }

    /** 右键读书的手部动作沿用原版读书动画 */
    @Override
    public EnumAction getItemUseAction(ItemStack stack) {
        return EnumAction.NONE;
    }
}
