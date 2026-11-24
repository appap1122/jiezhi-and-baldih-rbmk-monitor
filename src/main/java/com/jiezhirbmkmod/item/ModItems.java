package com.jiezhirbmkmod.item;

import com.jiezhirbmkmod.item.ItemGravityCoil;
import com.jiezhirbmkmod.item.ItemHealingCoil;
import com.jiezhirbmkmod.item.ItemJetSwitch;
import com.jiezhirbmkmod.item.ItemJetpack;
import com.jiezhirbmkmod.item.ItemRbmkLaptop;
import com.jiezhirbmkmod.item.ItemRbmkRemote;
import com.jiezhirbmkmod.item.ItemSpeedCoil;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.registries.IForgeRegistryEntry;

public class ModItems {
    public static final CreativeTabs TAB = new CreativeTabs("jiezhirbmkmod"){

        public ItemStack createIcon() {
            return new ItemStack(SPEED_COIL);
        }
    };
    public static final Item SPEED_COIL = new ItemSpeedCoil();
    public static final Item GRAVITY_COIL = new ItemGravityCoil();
    public static final Item HEALING_COIL = new ItemHealingCoil();
    public static final Item JETPACK = new ItemJetpack();
    public static final Item JET_SWITCH = new ItemJetSwitch();
    public static final Item RBMK_REMOTE = new ItemRbmkRemote();
    public static final Item RBMK_LAPTOP = new ItemRbmkLaptop();
    public static final Item RBMK_PPT_REMOTE = new ItemRbmkPptRemote();
    public static final Item RBMK_REFRESH_TUNER = new ItemRefreshTuner();
    public static final Item RBMK_REFRESH_TUNER_BASIC = new ItemRefreshTunerBasic();
    /** v17.6 只读操作手册：成书形态，页面硬编码，不可编辑 */
    public static final Item RBMK_MANUAL = new ItemRbmkManual();
    /** v17.6 只读操作手册 · 卷二（应急规程）：与卷一同构的第二本安全文件 */
    public static final Item RBMK_MANUAL_VOL2 = new ItemRbmkManualVol2();
    /** v17.6 配置拷贝卡：读一块屏的阈值/红石/刷新/绑定，写到下一块屏 */
    public static final Item RBMK_CONFIG_CARD = new ItemRbmkConfigCard();
    /** v17.6 手持检测仪：右键控制台/柱件，反射读快照，聊天栏报巡检读数 */
    public static final Item RBMK_PROBE = new ItemRbmkProbe();
    /** v17.6 告警扩音器：绑定一块屏，告警时随身发声（复用警报器音源） */
    public static final Item RBMK_SIREN_HORN = new ItemRbmkSirenHorn();
    /** v17.6 操作员工牌：签发后手持即可使用模组管理员功能（调节器/黑客门禁） */
    public static final Item RBMK_OP_CARD = new ItemRbmkOpCard();
    /** v17.8 配方模板卡：整张屏配方的黑名单快照，含监视槽清单与媒体文件 */
    public static final Item RBMK_RECIPE_CARD = new ItemRbmkRecipeCard();
    /** v17.8 只读操作手册 · 卷三（管理员手册）：权限体系收尾读物 */
    public static final Item RBMK_MANUAL_VOL3 = new ItemRbmkManualVol3();

    @SubscribeEvent
    public void onRegisterItems(RegistryEvent.Register<Item> event) {
        event.getRegistry().register(SPEED_COIL);
        event.getRegistry().register(GRAVITY_COIL);
        event.getRegistry().register(HEALING_COIL);
        event.getRegistry().register(JETPACK);
        event.getRegistry().register(JET_SWITCH);
        event.getRegistry().register(RBMK_REMOTE);
        event.getRegistry().register(RBMK_LAPTOP);
        event.getRegistry().register(RBMK_PPT_REMOTE);
        event.getRegistry().register(RBMK_REFRESH_TUNER);
        event.getRegistry().register(RBMK_REFRESH_TUNER_BASIC);
        event.getRegistry().register(RBMK_MANUAL);
        event.getRegistry().register(RBMK_MANUAL_VOL2);
        event.getRegistry().register(RBMK_CONFIG_CARD);
        event.getRegistry().register(RBMK_PROBE);
        event.getRegistry().register(RBMK_SIREN_HORN);
        event.getRegistry().register(RBMK_OP_CARD);
        event.getRegistry().register(RBMK_RECIPE_CARD);
        event.getRegistry().register(RBMK_MANUAL_VOL3);
    }

    static {
        SPEED_COIL.setCreativeTab(TAB);
        GRAVITY_COIL.setCreativeTab(TAB);
        HEALING_COIL.setCreativeTab(TAB);
        JETPACK.setCreativeTab(TAB);
        JET_SWITCH.setCreativeTab(TAB);
        RBMK_REMOTE.setCreativeTab(TAB);
        RBMK_LAPTOP.setCreativeTab(TAB);
        RBMK_PPT_REMOTE.setCreativeTab(TAB);
        RBMK_REFRESH_TUNER.setCreativeTab(TAB);
        RBMK_REFRESH_TUNER_BASIC.setCreativeTab(TAB);
        RBMK_MANUAL.setCreativeTab(TAB);
        RBMK_MANUAL_VOL2.setCreativeTab(TAB);
        RBMK_CONFIG_CARD.setCreativeTab(TAB);
        RBMK_PROBE.setCreativeTab(TAB);
        RBMK_SIREN_HORN.setCreativeTab(TAB);
        RBMK_OP_CARD.setCreativeTab(TAB);
        RBMK_RECIPE_CARD.setCreativeTab(TAB);
        RBMK_MANUAL_VOL3.setCreativeTab(TAB);
    }
}

