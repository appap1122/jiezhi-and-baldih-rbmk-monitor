package com.jiezhirbmkmod.block;

import com.jiezhirbmkmod.RbmkMonitorMod;
import com.jiezhirbmkmod.alarm.AlarmConfig;
import com.jiezhirbmkmod.proxy.CommonProxy;
import javax.annotation.Nullable;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.item.ItemDye;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import java.util.List;

/**
 * 旋转警示灯（v17.7：整段换回原版 jiezhimod 5.6 的实现）。
 *
 * <h3>为什么方块本身几乎什么都不画</h3>
 * 真实外形（圆罩 / 旋转的灯芯 / 反光板）全部由 {@code RenderAlarmLight} 现画 ——
 * <b>贴图画不出"完美的圆"</b>，16×16 的象素材质只会得到一个锯齿圈。
 * 所以渲染类型是 {@link EnumBlockRenderType#INVISIBLE}，方块只留碰撞箱与方块光。
 *
 * <p>★ v17.10：{@code models/block/rbmk_alarm_light.json} 换回原版那份
 * {@code parent: block/cube_all} + 一张 16×16 的灯面贴图。物品栏、手持、掉落物
 * 看到的就是原版那个"整块贴图方块"。v17.7 曾经给它做过一只三段式立体小灯
 * （底座 + 两段玻璃罩），但方块本体在世界里是隐形的，那只模型只在物品栏里能看到，
 * 反而和上游对不上；既然本项目其余部分都以上游为准，图标也照上游来。</p>
 *
 * <h3>朝向</h3>
 * {@link #FACING} 有<b>六个方向</b>（不是只有水平四面）：贴天花板朝下、贴地面朝上、
 * 贴墙朝外，都按"点击那个面的外法线"来 —— 灯口永远朝外，底座永远贴着被点的那一面。
 *
 * <p>meta 用 4 bit：低 3 位放朝向序号，最高位放开关（正好用满）。</p>
 *
 * <h3>v17.7 保留的三处项目侧行为（与原版不同，但都是玩家已经在用的）</h3>
 * <ol>
 *   <li><b>meta 映射</b>：原版用 {@code EnumFacing.ordinal()}（DOWN,UP,NORTH,...），
 *       本项目用 N=0 E=1 S=2 W=3 UP=4 DOWN=5 —— 换回 ordinal 会让所有 17.x 存档里的灯
 *       集体转向，所以这里保留兼容表；</li>
 *   <li><b>底座支撑检测</b>：底座那面被拆掉时灯跟着掉，原版没有；</li>
 *   <li><b>红石 / 右键开关 / 潜行开面板</b>：原版只有染料 + 控制台，
 *       本模组没有报警控制台，不给玩家手动开关的话单盏灯放下去就再也不受控。</li>
 * </ol>
 *
 * <h3>谁能开关它（v17.9）</h3>
 * 面板里可以选四种触发方式，见 {@link TileAlarmLight#TRIGGER_CLICK} 等常量：
 * 右键 / 潜行右键 / 红石 / 任意。这个选择<b>只决定"哪个手势算开关"</b>，
 * 每种模式下"另一个手势"自动让给面板 —— 否则潜行右键既当开关又当面板，
 * 面板就再也打不开了。联动中的灯一律不看触发方式（告警等级说了算）。
 */
public class BlockAlarmLight extends Block {

    /** 灯口朝向（= 底座的反面） */
    public static final PropertyEnum<EnumFacing> FACING =
            PropertyEnum.create("facing", EnumFacing.class);
    /** 是否亮着（同时决定原版方块光） */
    public static final PropertyBool ON = PropertyBool.create("on");

    // 底座那 0.35 厚的板子当碰撞箱；灯罩是圆的，不该让玩家按方块边界去撞
    private static final AxisAlignedBB BOX_UP = new AxisAlignedBB(0.1D, 0.0D, 0.1D, 0.9D, 0.35D, 0.9D);
    private static final AxisAlignedBB BOX_DOWN = new AxisAlignedBB(0.1D, 0.65D, 0.1D, 0.9D, 1.0D, 0.9D);
    private static final AxisAlignedBB BOX_NORTH = new AxisAlignedBB(0.1D, 0.1D, 0.65D, 0.9D, 0.9D, 1.0D);
    private static final AxisAlignedBB BOX_SOUTH = new AxisAlignedBB(0.1D, 0.1D, 0.0D, 0.9D, 0.9D, 0.35D);
    private static final AxisAlignedBB BOX_WEST = new AxisAlignedBB(0.65D, 0.1D, 0.1D, 1.0D, 0.9D, 0.9D);
    private static final AxisAlignedBB BOX_EAST = new AxisAlignedBB(0.0D, 0.1D, 0.1D, 0.35D, 0.9D, 0.9D);

    public BlockAlarmLight() {
        super(Material.IRON, MapColor.IRON);
        this.setRegistryName("jiezhirbmkmod", "rbmk_alarm_light");
        this.setTranslationKey("rbmk_alarm_light");
        this.setHardness(1.2F);
        this.setResistance(6.0F);
        this.setSoundType(SoundType.METAL);
        this.setHarvestLevel("pickaxe", 0);
        this.setCreativeTab(ModBlocks.TAB_SCREEN);
        this.setLightLevel(0.0F);
        this.setDefaultState(this.blockState.getBaseState()
                .withProperty(FACING, EnumFacing.UP)
                .withProperty(ON, Boolean.FALSE));
    }

    // ==================== 方块状态 ====================

    @Override
    protected BlockStateContainer createBlockState() {
        // ★ 属性名排序后是 facing < on，blockstates 的 variant key 必须照这个顺序写
        return new BlockStateContainer(this, new IProperty[] {FACING, ON});
    }

    /**
     * meta 布局，和后 17.6 之前的存档保持一致：0..3 是四个水平朝向的旧顺序
     * （北、东、南、西），4/5 是两个竖直朝向，bit3 存开关。
     *
     * <p>这里<b>不能</b>用 {@code EnumFacing.ordinal()}：MC 的枚举顺序是
     * DOWN, UP, NORTH, SOUTH, WEST, EAST，用 ordinal 之后 17.5 存档里的北向灯
     * 会读成吊顶灯，所有老存档的灯集体转 90°。</p>
     */
    private static final EnumFacing[] META_FACING = {
        EnumFacing.NORTH, EnumFacing.EAST, EnumFacing.SOUTH, EnumFacing.WEST,
        EnumFacing.UP, EnumFacing.DOWN
    };

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return this.getDefaultState()
                .withProperty(FACING, META_FACING[(meta & 7) % META_FACING.length])
                .withProperty(ON, Boolean.valueOf((meta & 8) != 0));
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        EnumFacing f = state.getValue(FACING);
        int m = 0;
        for (int i = 0; i < META_FACING.length; i++) {
            if (META_FACING[i] == f) {
                m = i;
                break;
            }
        }
        return state.getValue(ON) ? (m | 8) : m;
    }

    // ==================== 形状 / 渲染 ====================

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return boxFor(state.getValue(FACING));
    }

    /**
     * 该朝向的碰撞箱（方块局部 0..1 坐标），按 facing 直接查表。
     *
     * <p>几何锥体降级方案 {@code AlarmLightVolume} 要用它把射线起点推出灯体：
     * 射线从灯罩中心出发时起点落在自己的盒子里，不推出去就会 100% 命中自身、
     * 光锥长度为 0，表现成"灯亮着但一点光都没有"。</p>
     */
    public static AxisAlignedBB boxFor(EnumFacing facing) {
        switch (facing == null ? EnumFacing.UP : facing) {
            case DOWN:
                return BOX_DOWN;
            case NORTH:
                return BOX_NORTH;
            case SOUTH:
                return BOX_SOUTH;
            case WEST:
                return BOX_WEST;
            case EAST:
                return BOX_EAST;
            case UP:
            default:
                return BOX_UP;
        }
    }

    @Nullable
    @Override
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return this.getBoundingBox(state, world, pos);
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    /**
     * 让相邻面别被这盏灯"压暗"：方块本身不渲染，但它仍然是个不透明方块位置，
     * 默认 AO 会在灯贴着墙/天花板时给周围一圈暗角 —— 看起来像灯座漏光。
     */
    @Override
    public float getAmbientOcclusionLightValue(IBlockState state) {
        return 1.0F;
    }

    /**
     * 世界里**完全不画方块本体**，外形全部交给 TESR（{@code RenderAlarmLight}）。
     *
     * <p>为什么不像 HBM 那样留一个 cube 占位：那个 cube 会把灯罩连同光锥一起罩住，
     * 玩家看到的是"一个贴图方块"而不是"一盏灯"。</p>
     */
    @Override
    public EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.INVISIBLE;
    }

    @Override
    public int getLightValue(IBlockState state) {
        return state.getValue(ON).booleanValue() ? 15 : 0;
    }

    // ==================== 放置 ====================

    /**
     * 灯口朝"被点的那一面的外法线"：点天花板下表面 → 灯朝下；点墙 → 灯朝外。
     * 这样"朝任意方向摆放"不用另外加按键。
     *
     * <p>放下即点亮（原版放下是灭的，只能等控制台推配置）。本模组没有报警控制台，
     * 灭了就只能靠右键，等于"放下去以为坏了"，所以这里直接给 ON。</p>
     */
    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing,
                                            float hitX, float hitY, float hitZ, int meta,
                                            EntityLivingBase placer, EnumHand hand) {
        EnumFacing f = facing == null ? EnumFacing.UP : facing;
        return this.getDefaultState()
                .withProperty(FACING, f)
                .withProperty(ON, Boolean.TRUE);
    }

    /**
     * 故意<b>不</b>在这里测底座：放置时这个坐标还是空气，朝向也还没写进方块状态，
     * 真正的支撑检测放在 {@link #onBlockAdded}。
     */
    @Override
    public boolean canPlaceBlockAt(World world, BlockPos pos) {
        return super.canPlaceBlockAt(world, pos);
    }

    /** 底座在 FACING 的反面，所以那一面必须是实心面 */
    private boolean hasSolidSupport(World world, BlockPos pos) {
        IBlockState st = world.getBlockState(pos);
        EnumFacing facing = st.getBlock() == this ? st.getValue(FACING) : EnumFacing.UP;
        BlockPos behind = pos.offset(facing.getOpposite());
        IBlockState support = world.getBlockState(behind);
        return support.getBlock().isSideSolid(support, world, behind, facing);
    }

    @Override
    public void onBlockAdded(World world, BlockPos pos, IBlockState state) {
        super.onBlockAdded(world, pos, state);
        if (!world.isRemote && !this.hasSolidSupport(world, pos)) {
            this.dropBlockAsItem(world, pos, state, 0);
            world.setBlockToAir(pos);
            return;
        }
        world.notifyBlockUpdate(pos, state, state, 3);
        this.updateRedstone(world, pos);
    }

    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block blockIn, BlockPos fromPos) {
        if (!world.isRemote && !this.hasSolidSupport(world, pos)) {
            this.dropBlockAsItem(world, pos, state, 0);
            world.setBlockToAir(pos);
            return;
        }
        super.neighborChanged(state, world, pos, blockIn, fromPos);
        this.updateRedstone(world, pos);
    }

    /**
     * 邻居变了就重读一次电平（拉杆、按钮、红石线变档都会走到这里）。
     *
     * <p>联动中的灯不拉线：它的开关由告警等级决定，红石沿再插一脚就会出现
     * "告警亮着，红石一断灯灭了"。触发方式那一层的过滤（右键 / 潜行右键不认红石）
     * 在 {@link TileAlarmLight#setPowered} 里，这里不必重复判断。</p>
     */
    private void updateRedstone(World world, BlockPos pos) {
        if (world.isRemote) {
            return;
        }
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileAlarmLight) {
            TileAlarmLight light = (TileAlarmLight) te;
            if (!light.isAuto()) {
                light.setPowered(world.isBlockPowered(pos));
            }
        }
    }

    // ==================== 交互 ====================

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                    EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileAlarmLight)) {
            return false;
        }
        TileAlarmLight light = (TileAlarmLight) te;
        ItemStack held = player.getHeldItem(hand);

        // 0) 手持遥控器潜行右键：把最近的显示屏当成数据源
        //    （本模组没有报警控制台，联动只能从屏幕这一侧接进来）
        if (player.isSneaking() && !held.isEmpty()
                && held.getItem() instanceof com.jiezhirbmkmod.item.ItemRbmkRemote) {
            if (world.isRemote) {
                return true;
            }
            if (light.isAuto()) {
                light.clearSource();
                player.sendStatusMessage(
                        new TextComponentTranslation("msg.jiezhirbmkmod.alarm_light.unbind"), false);
            } else {
                // ★ 不能用遥控器 NBT 里的 target：那记的是 HBM 控制台/部件（数据源），
                //   不是显示屏。按它绑过去，灯会盯着一个控制台位置，永远判 OK。
                BlockPos target = TileAlarmLight.findScreenNear(world, pos,
                        TileAlarmLight.LINK_RADIUS);
                if (target == null) {
                    player.sendStatusMessage(new TextComponentTranslation(
                            "msg.jiezhirbmkmod.alarm_light.no_screen_near"), true);
                } else {
                    light.bindSource(target);
                    player.sendStatusMessage(new TextComponentTranslation(
                            "msg.jiezhirbmkmod.alarm_light.bind",
                            target.getX() + ", " + target.getY() + ", " + target.getZ()), false);
                }
            }
            return true;
        }

        // 1) 染料上色（原版行为：不用开界面，手里有染料拍上去就变色）
        if (!player.isSneaking() && !held.isEmpty() && held.getItem() instanceof ItemDye) {
            if (world.isRemote) {
                return true;
            }
            // 联动中的灯颜色由告警等级决定：这里直接改会被下一次等级变化覆盖，
            // 与其让玩家以为"染料坏了"，不如明说原因（且不消耗染料）。
            if (light.isAuto()) {
                // ★ 服务端也会走这条分支：只能用 TextComponentTranslation，
                //   不能碰 I18n（那是客户端类，专用服务器上是 NoClassDefFoundError）
                player.sendStatusMessage(
                        new TextComponentTranslation("msg.jiezhirbmkmod.alarm_light.auto"), true);
                return true;
            }
            EnumDyeColor dye = EnumDyeColor.byDyeDamage(held.getMetadata());
            light.applyConfig(new AlarmConfig(dye.getColorValue(),
                    light.config().rpm, true));
            if (!player.capabilities.isCreativeMode) {
                held.shrink(1);
            }
            player.sendStatusMessage(new TextComponentTranslation(
                    "msg.jiezhirbmkmod.alarm_light.color",
                    String.format(java.util.Locale.ROOT, "#%06X", dye.getColorValue() & 0xFFFFFF),
                    dye.getName()), false);
            return true;
        }

        // 2) 开面板：哪个手势负责它由"触发方式"决定（潜行模式下面板让给普通右键）
        if (light.clickOpensPanel(player.isSneaking())) {
            // ★ 手持方块时**不**开面板：那个手势同时是"贴着灯放方块"的常规操作，
            //   返回 true 会把它吃掉 —— 玩家会发现灯边上永远放不下东西，还以为卡了。
            if (!held.isEmpty() && held.getItem() instanceof net.minecraft.item.ItemBlock) {
                return false;
            }
            if (!world.isRemote) {
                player.openGui(RbmkMonitorMod.instance, CommonProxy.GUI_ALARM_LIGHT, world,
                        pos.getX(), pos.getY(), pos.getZ());
            }
            return true;
        }

        // 3) 开关：只在当前触发方式认可这个手势时才算（联动中的灯拒绝，理由同上）
        if (light.clickToggles(player.isSneaking())) {
            if (!world.isRemote) {
                if (light.isAuto()) {
                    player.sendStatusMessage(
                            new TextComponentTranslation("msg.jiezhirbmkmod.alarm_light.auto"), true);
                    return true;
                }
                light.setManualEnabled(!light.config().on);
                world.playSound(null, pos, SoundEvents.BLOCK_LEVER_CLICK, SoundCategory.BLOCKS, 0.4F,
                        light.isEnabled() ? 1.2F : 0.8F);
            }
            return true;
        }

        // 4) 红石触发时空手右键：这一下**故意不管**这盏灯，但也不能闷着 ——
        //    玩家会以为"灯坏了"。放行（return false）让原版照常处理手上的东西，
        //    只在空手时提示一句，避免贴着灯放方块被刷屏。
        if (!world.isRemote && held.isEmpty() && light.triggerMode() == TileAlarmLight.TRIGGER_REDSTONE) {
            player.sendStatusMessage(new TextComponentTranslation(
                    "msg.jiezhirbmkmod.alarm_light.redstone_only"), true);
        }
        return false;
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        super.breakBlock(world, pos, state);
        if (!world.isRemote) {
            world.notifyNeighborsOfStateChange(pos, this, false);
        }
    }

    // ==================== 方块实体 ====================

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    /**
     * ★ 1.12.2 实际调的是这个。只有 {@code BlockContainer} 的默认实现里才会走
     * {@code createNewTileEntity}，这里直接继承 {@link Block}，两个方法只有一个存在，
     * 写错就永远不生效、并且零报错。
     */
    @Nullable
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileAlarmLight();
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World worldIn,
                               List<String> tooltip, ITooltipFlag flag) {
        // ★ 写死四条字面量 key，不要用 "…" + i 拼接：
        //   自测哨兵是扫字符串字面量的，拼出来的 key 它看不见，
        //   下次改文案就会出现"语言文件里有、代码里没引用"的假死键。
        tooltip.add(TextFormatting.GRAY + I18n.format("tooltip.jiezhirbmkmod.rbmk_alarm_light.0"));
        tooltip.add(TextFormatting.GRAY + I18n.format("tooltip.jiezhirbmkmod.rbmk_alarm_light.1"));
        tooltip.add(TextFormatting.GRAY + I18n.format("tooltip.jiezhirbmkmod.rbmk_alarm_light.2"));
        tooltip.add(TextFormatting.AQUA + I18n.format("tooltip.jiezhirbmkmod.rbmk_alarm_light.3"));
        tooltip.add(TextFormatting.LIGHT_PURPLE
                + I18n.format("tooltip.jiezhirbmkmod.rbmk_alarm_light.4"));
        tooltip.add(TextFormatting.YELLOW + I18n.format("tooltip.jiezhirbmkmod.rbmk_alarm_light.5"));
    }
}
