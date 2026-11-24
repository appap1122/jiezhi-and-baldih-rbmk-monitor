package com.jiezhirbmkmod.block;

import com.jiezhirbmkmod.RbmkMonitorMod;
import com.jiezhirbmkmod.alarm.AlarmConfig;
import com.jiezhirbmkmod.alarm.AlertRules;
import com.jiezhirbmkmod.alarm.AlertSignal;
import com.jiezhirbmkmod.client.render.LightType;
import com.jiezhirbmkmod.dfc.RbmkConfig;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import javax.annotation.Nullable;

/**
 * 旋转警示灯的方块实体：只存"长什么样"（颜色 / 转速 / 开关），不存相位。
 *
 * <h3>相位为什么按坐标算，而不是记一个"放置时刻"</h3>
 * HBM 的旋转灯存的是 {@code timeAdded}（放下那一刻的世界刻），渲染时用
 * {@code now - timeAdded} 当转角。那样每盏灯都多一个必须<b>精确同步到客户端</b>的字段，
 * 一旦网络包晚到 / 存档重载，相位就会跳一下。
 *
 * <p>这里改用 {@code hash(x, y, z)} 当固定相位偏移：不占存档、不用同步、
 * 重进世界也完全一致，而且天然让相邻的灯错开（不会整排同步闪成一条线）。</p>
 *
 * <h3>v17.7：实现换回原版 jiezhimod 5.6</h3>
 * 配置类、联动轮询、同步方式全部沿用原版；差异只有三处，都在方法注释里标了：
 * <ul>
 *   <li>数据源从原版的 {@code TileDfcScreen}（DFC 熔点 / 激光）换成
 *       {@link TileRbmkMonitorScreen} + {@link AlertRules} 的 RBMK 四级判定；</li>
 *   <li>多一个 {@code powered}（红石）与手动开关，因为本模组没有报警控制台；</li>
 *   <li>NBT 兼容 17.6 及更早的 {@code Color/Speed/ManualEnabled} 旧键。</li>
 * </ul>
 */
public class TileAlarmLight extends TileEntity implements ITickable {

    private final AlarmConfig cfg = new AlarmConfig();

    /**
     * 玩家的手动配置快照（颜色 / 转速 / 开关），v17.9 随「自动染色」开关引入。
     *
     * <p>★ 为什么必须单独存一份：自动染色期间 {@code poll()} 会把 {@link #cfg}
     * 整个改写成告警值（颜色 / 转速 / 开关一起）并落盘 —— 不存快照的话，
     * 玩家手动染的颜色就被冲掉了，「关闭 = 玩家自己染色」无从谈起。
     * 开启自动染色那一刻存下，关闭时恢复。</p>
     *
     * <p>只进存档 NBT（{@code MColor/MRpm/MOn}），<b>不</b>进 {@code getUpdateTag}：
     * 客户端渲染只需要实时值 {@code cfg}，恢复由服务端做完再整体广播。
     * 17.9 早期绑定的存档没有快照键 → 读作 {@code null}，解除时保持当前颜色
     * （旧行为），玩家重新染一次即可。</p>
     */
    @Nullable
    private AlarmConfig manual;

    /** 坐标哈希出来的相位偏移（度），渲染时直接加在转角上 */
    private float phaseOffset;

    // ==================== 告警联动 ====================

    /**
     * 绑定的**数据源**（一块 RBMK 显示屏的坐标）。{@code null} = 不联动，
     * 只受红石 / 染料 / 手动开关摆布。
     *
     * <p>★ 联动期间**控制台的推送会被忽略**（见 {@link #applyConsoleConfig}）——
     * 否则控制台每次红石沿都会把颜色刷回统一色，告警就白做了。</p>
     */
    private BlockPos source;

    /** 上一次判出来的等级（-1 = 还没判过）；只在变化时下发，避免每 10 刻刷包 */
    private int alertLevel = -1;

    /** 轮询节流：读屏幕要走一遍快照，别每刻都来 */
    private static final int SCAN_TICKS = 10;
    private int scanTimer;

    /**
     * 红石通电电平（只在未联动、且触发方式用得上红石时才有意义）。
     *
     * <p>★ 它<b>不</b>并进 {@code cfg.on}：并进去之后红石一断灯也灭不掉
     * （cfg.on 已经是 true），"红石控制"就变成了"红石只能开不能关"。</p>
     */
    private boolean powered;

    /** onLoad 里发现"方块状态与 cfg 不一致"，等首 tick 再去改世界（onLoad 里改不安全） */
    private boolean syncPending;

    // ==================== 光锥实现（v17.8） ====================

    /** 跟随全局配置 {@code alarm.sssLighting}（默认） */
    public static final int SPOT_AUTO = 0;
    /** 强制几何锥体：逐条 rayTraceBlocks，边缘硬、开销低，不占 SSS 槽位 */
    public static final int SPOT_GEOMETRY = 1;
    /** 强制屏幕空间聚光灯：FBO 深度捕获 + 全屏 GLSL，边缘柔和、被真实深度遮挡 */
    public static final int SPOT_SSS = 2;
    /**
     * 强制贴图灯光：用 {@code rbmk_alarm_light_glow.png} 那张径向渐变贴图，
     * 画一圈朝向镜头的光晕、沿轴向排一串逐渐变大的光柱、再在射线落点贴光斑。
     *
     * <p>三种贴图几何全是 {@code POSITION_TEX_COLOR}，<b>有 UV</b>。这是它和另外两条路
     * 最大的区别：几何锥体是 {@code POSITION_COLOR}、SSS 是自写 program，两者在光影包下
     * 都会被 gbuffers 的 sampler0 采成黑雾；贴图这条走的是最普通的带 UV 的世界几何，
     * 光影包会把它当常规方块几何处理，所以它是唯一能在光影下正常显示的路径。</p>
     */
    public static final int SPOT_TEXTURE = 3;

    /** {@link #renderPath()} 的返回值：走屏幕空间 SSS */
    public static final int PATH_SSS = 0;
    /** {@link #renderPath()} 的返回值：走几何锥体 */
    public static final int PATH_GEOMETRY = 1;
    /** {@link #renderPath()} 的返回值：走贴图灯光 */
    public static final int PATH_TEXTURE = 2;

    /** 联动时自动搜显示屏的半径（格） */
    public static final int LINK_RADIUS = 16;

    /**
     * 这盏灯的光锥实现。<b>是每盏灯独立的</b>，不是全局开关 ——
     * 一排灯里可以混着放，靠近镜头的主灯用 SSS、远处凑数的那几盏用几何锥体省开销。
     *
     * <p>存进 NBT 并随 {@code getUpdateTag} 同步：客户端渲染要按它分派，
     * 服务端算不了画面。</p>
     */
    private int spotMode = SPOT_AUTO;

    // ==================== 触发方式（v17.9） ====================

    /** 普通右键开关（潜行右键开面板） */
    public static final int TRIGGER_CLICK = 0;
    /** 只有潜行右键才开关（普通右键开面板 —— 两个手势正好对调） */
    public static final int TRIGGER_SNEAK = 1;
    /** 只有红石能开关：电平即灯态，右键不碰它 */
    public static final int TRIGGER_REDSTONE = 2;
    /** 任一来源为开即亮（右键 或 红石），也就是 17.8 的旧行为 */
    public static final int TRIGGER_ANY = 3;
    /**
     * v17.9「无线 / 常亮」：灯始终点亮，红石与手动开关都不参与。
     *
     * <p>两个手势（右键 / 潜行右键）都只负责开面板（见 {@link #clickOpensPanel}）——
     * 常亮模式下"开关灯"这个动作不存在，右键不能变成"关灯"，否则玩家一点
     * 常亮就灭，等于没这个档。</p>
     *
     * <p>包与 NBT 里触发方式从 2 位扩成 3 位（值 0..4）：旧客户端只发 0..3，
     * 新客户端发给旧服务器时 4 会被旧端 &3 截成 0（右键）—— 不崩线，
     * 只是那一档在旧端读作右键，跨版本混连仅此一处行为差异。</p>
     */
    public static final int TRIGGER_ALWAYS = 4;
    /**
     * v17.9「关闭：常灭」：灯始终熄灭，红石与手动开关都不参与（与
     * {@link #TRIGGER_ALWAYS} 正好互为镜像）。两个手势都只负责开面板 ——
     * 常灭模式下"开灯"这个动作不存在，右键不能变成"开灯"，否则玩家一点
     * 常灭就亮，等于没这个档。
     *
     * <p>值 5 仍在包/触发方式的 3 位掩码（0..7）内，网络格式不变。</p>
     */
    public static final int TRIGGER_OFF = 5;

    /**
     * 这盏灯的开关由谁触发。<b>只影响"谁能让它亮/灭"，不影响颜色转速</b>。
     *
     * <p>默认 {@link #TRIGGER_ANY} 而不是 CLICK：17.8 及更早的存档里根本没有这个键，
     * 那时候红石是和手动开关"或"在一起的。默认 CLICK 会让所有接了红石的灯
     * 在升级后突然不听红石 —— 这种"升级把线剪了"的回归比多一个选项难查得多。</p>
     *
     * <p>联动中的灯不看这个字段（告警等级说了算，见 {@link #isEnabled()}）。</p>
     */
    private int triggerMode = TRIGGER_ANY;

    /** 红石轮询节流：只在触发方式用到红石时才跑 */
    private int redstoneTimer;

    /**
     * 解析后的控制器坐标（多方块屏幕的部件坐标要归一到这里才有数据）。
     * 不入 NBT，重进世界解析一次；{@link #resolveTried} 保证"拆掉了"不会每 10 刻扫 567 格。
     */
    @Nullable
    private BlockPos resolved;
    private boolean resolveTried;

    public AlarmConfig config() {
        return this.cfg;
    }

    /** 绑定的数据源坐标（可能已被拆掉）；{@code null} = 未联动 */
    public BlockPos sourcePos() {
        return this.source;
    }

    public boolean isAuto() {
        return this.source != null;
    }

    /** 原始设置值（{@link #SPOT_AUTO} 表示"跟随全局"） */
    public int spotMode() {
        return this.spotMode;
    }

    public void setSpotMode(int mode) {
        int m = mode < SPOT_AUTO ? SPOT_AUTO : (mode > SPOT_TEXTURE ? SPOT_TEXTURE : mode);
        if (m == this.spotMode) {
            return;
        }
        this.spotMode = m;
        this.markDirty();
        if (this.world != null && !this.world.isRemote) {
            // 客户端要立刻换渲染路径，别等区块重发
            this.broadcastState();
        }
    }

    // ---------- 触发方式 ----------

    public int triggerMode() {
        return this.triggerMode;
    }

    /**
     * 换触发方式，并<b>立刻</b>把新语义算出来。
     *
     * <p>不能只存字段了事：从「右键」切到「红石」时，红石可能本来就是通电的，
     * 而 {@link #powered} 上一次更新还是不相关时的旧值（{@link #setPowered}
     * 在旧模式下会跳过重算）。这里必须重新读一次电平再对账，否则玩家切过去
     * 看到的是一盏"红石通着电却不亮"的灯，得等下一次邻居更新才恢复。</p>
     */
    public void setTriggerMode(int mode) {
        int m = clampTrigger(mode);
        if (m == this.triggerMode) {
            return;
        }
        // 先记下"现在看着是什么样"。换触发方式不该让眼前的灯闪一下 ——
        // 从红石（通着电、亮着）切到右键，如果只按 cfg.on 重算就会当场灭掉，
        // 玩家得再点一次才亮，看起来像切换把灯弄坏了。
        boolean wasLit = this.isEnabled();
        this.triggerMode = m;
        this.markDirty();
        if (this.world != null && !this.world.isRemote) {
            this.refreshRedstone();
            if (m != TRIGGER_REDSTONE && !this.isAuto() && this.isEnabled() != wasLit) {
                // 把手感接回来：新模式算出来跟刚才不一样，就把刚才的灯态写进手动开关。
                // 红石模式**不写** —— 那边的灯态是电平的函数，写了也只会被下一次
                // 邻居更新盖掉，还会顺手弄脏玩家原来设的手动状态。
                this.cfg.on = wasLit;
            }
            this.applyOnState();
            this.broadcastState();
        }
    }

    /** 越界的触发方式一律当"任意"，别让坏存档把开关分派到一个不存在的分支 */
    private static int clampTrigger(int mode) {
        return mode < TRIGGER_CLICK || mode > TRIGGER_OFF ? TRIGGER_ANY : mode;
    }

    /** 这个触发方式看不看红石（也只有这时候才值得每几刻轮询一次电平） */
    public boolean usesRedstone() {
        return this.triggerMode == TRIGGER_REDSTONE || this.triggerMode == TRIGGER_ANY;
    }

    /**
     * 这一下右键该不该开关灯。
     *
     * <p>{@link TileAlarmLight#TRIGGER_REDSTONE} 返回 {@code false}：
     * 红石模式下右键不是"无效操作"，而是<b>不属于它的手势</b> ——
     * 调用方要能区分这两种情况（前者要吃掉点击，后者要放行给原版，
     * 否则贴着灯放方块会永远放不下去）。</p>
     */
    public boolean clickToggles(boolean sneaking) {
        switch (this.triggerMode) {
            case TRIGGER_SNEAK:
                return sneaking;
            case TRIGGER_REDSTONE:
            case TRIGGER_ALWAYS:
            case TRIGGER_OFF:
                // 常亮/常灭模式：开关灯这个动作不存在，右键不是"无效操作"而是
                // "不属于它的手势"，调用方据此放行或开面板
                return false;
            case TRIGGER_CLICK:
            case TRIGGER_ANY:
            default:
                // ★ 任意 = 右键 或 红石（任一为开即亮），**不含潜行右键**：
                //   潜行右键必须留着开面板，否则这个模式下面板再也没有入口。
                return !sneaking;
        }
    }

    /**
     * 这个手势是不是"开面板"。
     *
     * <p>★ 两种手势不能同时承担两个职责：{@link #TRIGGER_SNEAK} 把开关挪到潜行右键之后，
     * 面板就必须让给普通右键，否则面板再也打不开（原来的潜行手势已经被开关占了）。</p>
     *
     * <p>★ {@link #TRIGGER_ALWAYS}（常亮）下两个手势都开面板：开关职责不存在了，
     * 两个手势都闲着，别让玩家对着"哪个手势开面板"猜谜。</p>
     */
    public boolean clickOpensPanel(boolean sneaking) {
        if (this.triggerMode == TRIGGER_ALWAYS || this.triggerMode == TRIGGER_OFF) {
            return true;
        }
        return this.triggerMode == TRIGGER_SNEAK ? !sneaking : sneaking;
    }

    /** 重新读一次红石电平（不触发早期返回，切模式时用） */
    private void refreshRedstone() {
        if (this.world == null || this.world.isRemote || this.pos == null) {
            return;
        }
        this.powered = this.world.isBlockPowered(this.pos);
        this.redstoneTimer = 5;
    }

    /**
     * 这盏灯本帧实际走哪条渲染路径，返回 {@link #PATH_SSS} /
     * {@link #PATH_GEOMETRY} / {@link #PATH_TEXTURE} 三者之一。
     *
     * <p>这是渲染分派的<b>唯一</b>入口：{@code AlarmSpotlights} 只收走 SSS 的灯，
     * {@code AlarmLightVolume} 按几何 / 贴图分派。<b>三条路径互斥，永远不会有两份
     * 自发光叠在同一盏灯上</b> —— 叠起来就是过曝的白色光斑，而且形状对不上。</p>
     *
     * <p>★ 光影包下只有 {@link #PATH_TEXTURE} 留在场上：几何锥体是 {@code POSITION_COLOR}
     * 零贴图几何、SSS 是自写 program，两者都会被 gbuffers 的 sampler0 采成黑雾，
     * 所以外层渲染器会把它们跳过；贴图这条走的是最普通的带 UV 世界几何，
     * 光影包按常规方块几何处理它，能正常采样。这里只负责说出"该走哪条"，
     * 让不让路由渲染器自己判断（它才知道光影开没开）。</p>
     */
    public int renderPath() {
        if (this.spotMode == SPOT_TEXTURE) {
            return PATH_TEXTURE;
        }
        if (this.spotMode == SPOT_GEOMETRY) {
            return PATH_GEOMETRY;
        }
        if (this.spotMode == SPOT_SSS) {
            // 强制 SSS：只有管线本局真编挂了才退几何 —— 绝不留在"两边都不画"的状态
            return RbmkConfig.sssAvailable ? PATH_SSS : PATH_GEOMETRY;
        }
        return TileAlarmLight.autoPath();
    }

    /**
     * 「自动」档按当前环境选<b>最优</b>渲染路径。
     *
     * <p>规则就两条：</p>
     *
     * <ol>
     *   <li><b>有光影包 → 贴图</b>（{@link RbmkConfig#shaderPackActive}）：四档里唯一
     *       带真实 UV、能被光影 program 正常采样的路径，别的档在光影下要么黑雾
     *       （几何）、要么被整屏接管（FBO）；</li>
     *   <li><b>没光影 → FBO</b>，除非本局 SSS 真的编挂（{@link RbmkConfig#sssAvailable}
     *       为 false，JVM 参数掐掉 / 显卡不支持 GL3.0 / 链接失败）才退几何。
     *       屏幕空间聚光灯按真实深度算、光斑柔和会被墙挡，是没光影时观感最好的。</li>
     * </ol>
     *
     * <p>★ <b>所有「自动」灯在任何时刻都是同一个答案</b>：只读两个全局布尔，
     * 没有任何 per-lamp 状态。第一盏灯放置时按当时的环境选最优；游戏途中
     * 开关光影包，探测缓存约 1.6 秒内刷新，之后<b>所有</b>自动灯（包括第一盏）
     * 一起重新按新环境选 —— 不存在"这盏是当年的选择、那盏是新规则"的错位。
     * 渲染分派（{@link #renderPath}）和面板按钮文案都调这一个方法。</p>
     */
    public static int autoPath() {
        if (RbmkConfig.shaderPackActive) {
            return PATH_TEXTURE;
        }
        return RbmkConfig.sssAvailable ? PATH_SSS : PATH_GEOMETRY;
    }

    /**
     * 这盏灯本帧该不该走屏幕空间管线。
     *
     * <p>等价于 {@code renderPath() == PATH_SSS}，留着是因为 {@code AlarmSpotlights}
     * 收集光源时只关心"收不收"，布尔比整数比较读起来直接。</p>
     */
    public boolean usesSss() {
        return this.renderPath() == PATH_SSS;
    }

    /** 贴图灯光：面板上第四档，光影包下唯一还画得出来的路径 */
    public boolean usesTexture() {
        return this.renderPath() == PATH_TEXTURE;
    }

    /** 几何锥体：逐条 rayTraceBlocks 的那条 */
    public boolean usesGeometry() {
        return this.renderPath() == PATH_GEOMETRY;
    }

    /**
     * 告警等级联动的开关（GUI 上那个按钮 / 遥控器潜行右键都走这里）。
     *
     * @param on {@code true} = 绑定 16 格内最近的显示屏；{@code false} = 解除联动
     * @return {@code false} 只有一种情况：要开联动但附近没有显示屏
     */
    public boolean setAutoEnabled(boolean on) {
        if (!on) {
            this.clearSource();
            return true;
        }
        if (this.isAuto()) {
            return true;
        }
        if (this.world == null || this.world.isRemote || this.pos == null) {
            return false;
        }
        BlockPos target = findScreenNear(this.world, this.pos, LINK_RADIUS);
        if (target == null) {
            return false;
        }
        this.bindSource(target);
        return true;
    }

    /** 最近一次判定的等级（0~3），{@code -1} = 还没判过 */
    public int alertLevel() {
        return this.alertLevel;
    }

    /**
     * 绑定一块显示屏当数据源，并**立刻**判一次（不用等 10 刻，绑定瞬间就该看到颜色）。
     */
    public void bindSource(BlockPos src) {
        this.source = src == null ? null : src.toImmutable();
        this.resolved = null;
        this.resolveTried = false;
        this.alertLevel = -1;
        this.scanTimer = 0;
        // v17.9 自动染色：先快照玩家的手动配置再让 poll 改写 cfg。
        // 必须在 bind 的同一时刻存 —— 这是 cfg 还是"玩家染色"的最后时刻。
        this.manual = this.cfg.copy();
        this.markDirty();
        if (this.world != null && !this.world.isRemote) {
            this.poll();
            this.broadcastState();
        }
    }

    /** 解除自动染色：恢复玩家手动染的颜色 / 转速 / 开关（快照在开启时存下） */
    public void clearSource() {
        this.source = null;
        this.resolved = null;
        this.resolveTried = false;
        this.alertLevel = -1;
        // 恢复手动配置。applyConfig 内部会按需 applyOnState + broadcast，
        // 但它只在"确实变了"时才标脏 —— 这里无条件再补一次，保证快照落盘。
        if (this.manual != null) {
            this.applyConfig(this.manual.copy());
        }
        this.markDirty();
        if (this.world != null && !this.world.isRemote) {
            this.broadcastState();
        }
    }

    /**
     * 写入一份新配置（告警 / 染料 / GUI / 红石都走这里）。
     *
     * <p>顺带把 {@code ON} 写进方块状态：{@code getLightValue} 是按状态返回的，
     * 光改方块实体，原版光照不会跟着变（而且客户端也拿不到）。</p>
     */
    public void applyConfig(AlarmConfig next) {
        if (next == null) {
            return;
        }
        boolean changed = !next.sameAs(this.cfg);
        this.cfg.color = next.color & 0xFFFFFF;
        this.cfg.rpm = AlarmConfig.clampRpm(next.rpm);
        this.cfg.on = next.on;
        if (this.world != null && !this.world.isRemote) {
            this.applyOnState();
            if (changed) {
                this.markDirty();
                this.broadcastState();
            }
        }
    }

    /**
     * 控制台推送专用入口：**联动中的灯不理会**。
     *
     * <p>控制台每次红石沿都会把统一配置推给成员，如果它也能改联动灯，
     * 那告警颜色刚亮起来就被刷回红色常亮 —— 联动等于没做。</p>
     */
    public void applyConsoleConfig(AlarmConfig next) {
        if (this.isAuto()) {
            return;
        }
        this.applyConfig(next);
    }

    /** 关灯（控制台下降沿 / 拆除时的兜底） */
    public void turnOff() {
        this.applyConfig(this.cfg.withOn(false));
    }

    /**
     * 找 {@code radius} 格内最近的一块显示屏（多方块取任一部件都行，poll 时按坐标取 TE）。
     *
     * <p>只在玩家手动绑定那一下调用，不进 tick。</p>
     *
     * <p>★ v17.10 修复：原来用 {@code world.getChunk(cx, cz)}，它会<b>同步加载</b>
     * 未加载的区块 —— 玩家在基地边缘拿遥控器点一下灯，最坏情况当场强载 9 个区块，
     * 服务端卡一下，还可能把本来不该加载的地形拉进内存。绑屏是"查"不是"去"。</p>
     *
     * <p>v18.0：实现统一收编进 {@link ScreenScan} —— 与巡屏中控/聚合中控/
     * /rbmktrace 同一条"只扫已加载区块、距离升序"的扫描策略，本方法只剩
     * 一行委托。屏所在的区块没加载，说明那片本来就不在工作，绑过去 poll
     * 也只会一直读"离线"。</p>
     *
     * @return 显示屏方块的坐标；找不到返回 {@code null}
     */
    @Nullable
    public static BlockPos findScreenNear(World world, BlockPos center, int radius) {
        return ScreenScan.nearest(world, center, radius);
    }

    // ==================== 适配层（GUI / 网络包 / 渲染 / 体积光） ====================
    /** 当前颜色 0xRRGGBB（渲染器、SSS、GUI 都读这个） */
    public int getColor() {
        return this.cfg.color & 0xFFFFFF;
    }

    /** 只改颜色（GUI 网络包调用），开关与转速保持不动 */
    public void setColor(int color) {
        this.applyConfig(this.cfg.withColor(color));
    }

    /**
     * 灯此刻是否应该亮 —— <b>整个模组只有这一个判据</b>（方块光、TESR、SSS、几何光锥
     * 全都经由 {@link #applyOnState()} 或方块状态 ON 走它）。
     *
     * <p>优先级：</p>
     * <ol>
     *   <li><b>联动中</b>：只看 {@code cfg.on}，也就是告警等级。触发方式、红石、
     *       手动点击全部让路 —— 否则"告警亮着，红石一断灯灭了"；</li>
     *   <li><b>红石触发</b>：只看电平。这是玩家接红石时唯一的预期：
     *       通就亮、断就灭，而不是"断了我还得再右键一下"；</li>
     *   <li><b>任意</b>：手动开关或红石，任一为开（17.8 的 OR 语义）；</li>
     *   <li><b>右键 / 潜行右键</b>：只看手动开关，红石完全不参与。</li>
     * </ol>
     */
    public boolean isEnabled() {
        if (this.isAuto()) {
            return this.cfg.on;
        }
        switch (this.triggerMode) {
            case TRIGGER_REDSTONE:
                return this.powered;
            case TRIGGER_ALWAYS:
                // 常亮：无条件亮。红石电平与手动开关都不参与（usesRedstone
                // 也不含它，连轮询都不跑）。
                return true;
            case TRIGGER_OFF:
                // 常灭：无条件灭。镜像于常亮档。
                return false;
            case TRIGGER_ANY:
                return this.cfg.on || this.powered;
            case TRIGGER_CLICK:
            case TRIGGER_SNEAK:
            default:
                return this.cfg.on;
        }
    }

    public void setManualEnabled(boolean on) {
        this.applyConfig(this.cfg.withOn(on));
    }

    /** 只改转速（GUI 滑条 / 网络包），颜色与开关不动 */
    public void setRpm(int rpm) {
        this.applyConfig(this.cfg.withRpm(rpm));
    }

    public boolean isPowered() {
        return this.powered;
    }

    /**
     * 红石变化入口；只改电平，不写 cfg（否则红石会把玩家的手动开关吃掉）。
     *
     * <p>★ 触发方式用不到红石时（右键 / 潜行右键）只记电平就返回：那两种模式下
     * 灯态和电平无关，还去 {@code setBlockState} + 给 96 格内每个玩家发包，
     * 等于让一盏不听话的灯替全场刷数据。</p>
     */
    public void setPowered(boolean value) {
        if (this.powered == value) {
            return;
        }
        this.powered = value;
        if (this.world == null || this.world.isRemote) {
            return;
        }
        if (!this.usesRedstone() || this.isAuto()) {
            // 这个模式下灯态和电平无关：只记下来（切模式时 refreshRedstone 还会重读一次），
            // 不标脏、不 setBlockState、不给 96 格内每个玩家发包
            return;
        }
        this.markDirty();
        this.applyOnState();
        this.broadcastState();
    }

    /**
     * 光锥射程（格）—— 给几何锥体降级方案 {@code AlarmLightVolume} 用。
     * 数值直接取 {@link LightType}，和屏幕空间着色器保持同一份定义。
     */
    public float getRadius() {
        return this.lightType().range();
    }

    /** 光锥外张角（度），同样来自 {@link LightType} */
    public float getConeAngle() {
        float c = this.lightType().cosOuter();
        return (float) Math.toDegrees(Math.acos(MathHelper.clamp(c, -1.0F, 1.0F)));
    }

    private LightType lightType() {
        if (this.world == null || this.pos == null) {
            return LightType.FLOOR;
        }
        IBlockState st = this.world.getBlockState(this.pos);
        if (!(st.getBlock() instanceof BlockAlarmLight)) {
            return LightType.FLOOR;
        }
        Vec3i n = st.getValue(BlockAlarmLight.FACING).getDirectionVec();
        return LightType.classify(new Vec3d(n.getX(), n.getY(), n.getZ()));
    }

    // ==================== 告警轮询 ====================

    @Override
    public void update() {
        // 首 tick 补一次"方块状态 ↔ cfg"的对账。放在这里而不是 onLoad 里：
        // onLoad 发生在区块加载过程中，那时候 setBlockState 会插进世界正在遍历的
        // 方块实体表，轻则光照没跟上，重则 ConcurrentModificationException。
        if (this.syncPending) {
            this.syncPending = false;
            if (this.world != null && !this.world.isRemote) {
                this.applyOnState();
            }
        }
        if (this.world == null || this.world.isRemote) {
            return;
        }
        // 红石兜底轮询：neighborChanged 已经能覆盖绝大多数情况（拉杆、按钮、红石线
        // 变档都会触发邻居更新），但活塞推方块、/setblock、别的模组直接改电平都
        // 可能不通知到这盏灯。接了红石的灯每 5 刻对一次账，代价是 6 次 getWeakPower。
        if (!this.isAuto() && this.usesRedstone() && --this.redstoneTimer <= 0) {
            this.redstoneTimer = 5;
            this.setPowered(this.world.isBlockPowered(this.pos));
        }
        if (this.source == null) {
            return;
        }
        if (--this.scanTimer > 0) {
            return;
        }
        this.scanTimer = SCAN_TICKS;
        this.poll();
    }

    /**
     * 每 {@link #SCAN_TICKS} 刻读一次数据源，等级变了才改颜色。
     *
     * <p>为什么是"变了才改"：{@link #applyConfig} 会 {@code setBlockState} +
     * {@link #broadcastState()}（给 96 格内每个玩家发 TE 包）。每刻无脑写就是
     * 卡顿的来源，而告警状态几分钟也不一定变一次。</p>
     */
    private void poll() {
        // 区块没加载 ≠ 数据源没了：这时候什么都不做，否则走远一点灯就会自己灭
        if (this.world == null || this.source == null || !this.world.isBlockLoaded(this.source)) {
            return;
        }
        BlockPos at = this.resolved != null ? this.resolved : this.source;
        TileEntity te = this.world.getTileEntity(at);
        if (te == null && !this.resolveTried) {
            // 绑的是多方块屏幕的"部件"坐标：部件没有方块实体，必须归一化到控制器。
            // 只解析一次 —— findController 会扫 9×7×9 格，不能每 10 刻来一遍。
            this.resolveTried = true;
            BlockPos controller = ScreenMultiblock.findController(this.world, this.source);
            if (controller != null) {
                this.resolved = controller.toImmutable();
                at = this.resolved;
                te = this.world.getTileEntity(at);
            }
        }
        int warnC = RbmkConfig.defaultWarnC;
        int critC = RbmkConfig.defaultCritC;
        int rateD = RbmkConfig.defaultRateWarnD;
        int fluxF = RbmkConfig.defaultFluxWarnF;
        AlertSignal sig;
        if (te instanceof TileRbmkMonitorScreen) {
            TileRbmkMonitorScreen screen = (TileRbmkMonitorScreen) te;
            // 阈值跟屏幕上那份策略走：屏幕变黄的那一刻灯也变黄
            try {
                com.jiezhirbmkmod.dfc.AlertPolicy p = screen.getPolicy();
                if (p != null) {
                    warnC = p.warnC;
                    critC = p.critC;
                    rateD = p.rateWarnD;
                    fluxF = p.fluxWarn;
                }
            } catch (Throwable ignored) {
                // 策略还没初始化（刚放下 / 正在读 NBT）→ 用默认阈值，别让灯崩在这
            }
            // 屏幕关着（没通电）= 没在监视，别拿旧数据一直报警
            sig = screen.isPowered()
                    ? AlertRules.evaluate(screen.getData(), warnC, critC, rateD, fluxF)
                    : new AlertSignal(AlertRules.OK, "off", "");
        } else if (te == null) {
            // 已经加载了却找不到方块实体 ⇒ 屏被拆了。按"正常"处理并留着绑定，
            // 重建之后再接上就行（玩家拆屏不一定想重新绑一次）
            sig = new AlertSignal(AlertRules.OK, "nosrc", "");
        } else {
            sig = new AlertSignal(AlertRules.OK, "nosrc", "");
        }
        if (sig.level == this.alertLevel) {
            return;
        }
        this.alertLevel = sig.level;
        this.applyConfig(this.cfg.copy()
                .withColor(AlertRules.colorFor(sig.level))
                .withRpm(AlertRules.rpmFor(sig.level))
                .withOn(AlertRules.litFor(sig.level)));
        dbgAlert(sig);
    }

    /** 只在等级变化时打一行（前 60 条），进游戏验联动时靠它判断"判没判到" */
    private static int dbgAlertCount;

    private void dbgAlert(AlertSignal sig) {
        if (dbgAlertCount++ < 60) {
            RbmkMonitorMod.warn("[Alarm] " + this.pos + " level=" + sig.level
                    + " " + sig.key + (sig.detail.isEmpty() ? "" : " (" + sig.detail + ")"));
        }
    }

    /**
     * 把颜色 / 转速推给周围玩家。
     *
     * <p>★★ 不能只靠 {@code notifyBlockUpdate}：它只发 {@code SPacketBlockChange}（方块状态），
     * <b>不带方块实体 NBT</b>；而 {@code SPacketUpdateTileEntity} 只在区块加载时随
     * {@code SPacketChunkData} 发一次。不自己发的话，服务端颜色改了、客户端画面一点不变。</p>
     */
    public void broadcastState() {
        if (this.world == null || this.world.isRemote || this.pos == null) {
            return;
        }
        SPacketUpdateTileEntity pkt = this.getUpdatePacket();
        if (pkt == null) {
            return;
        }
        double range = 96.0D * 96.0D;
        for (Object o : this.world.playerEntities) {
            if (o instanceof net.minecraft.entity.player.EntityPlayerMP) {
                net.minecraft.entity.player.EntityPlayerMP p =
                        (net.minecraft.entity.player.EntityPlayerMP) o;
                if (p.getDistanceSq(this.pos.getX() + 0.5D, this.pos.getY() + 0.5D,
                        this.pos.getZ() + 0.5D) <= range) {
                    p.connection.sendPacket(pkt);
                }
            }
        }
    }

    // ==================== 渲染用 ====================

    /**
     * 此刻灯芯转到第几度（0~360）。
     *
     * @param partialTicks 帧插值，避免 20Hz 的世界刻看起来一顿一顿
     */
    public float angleDeg(float partialTicks) {
        double degPerTick = this.cfg.degreesPerSecond() / 20.0D;
        if (degPerTick <= 0.0D) {
            return this.phaseOffset;
        }
        long t = this.world == null ? 0L : this.world.getTotalWorldTime();
        double deg = (t + partialTicks) * degPerTick + this.phaseOffset;
        return (float) MathHelper.positiveModulo(deg, 360.0D);
    }

    /**
     * 坐标哈希 → 0~360 的固定相位（重进世界不变、相邻灯自然错开）。
     *
     * <p>★ v17.8：三步混合。原来的 {@code x*A ^ y*B ^ z*C} 有两个看得见的毛病：</p>
     * <ul>
     *   <li><b>一排灯</b>（y、z 相同，x 递增）：相位步长恒为 {@code A % 360 = 293}°，
     *       也就是每格 -67°，虽然错开了但太"规律"，四盏之后开始像等差数列；</li>
     *   <li><b>一列灯</b>（x、z 相同，y 递增）：{@code B % 360 = 23}°，
     *       一竖排看起来几乎同步转，这正是玩家抱怨"整排一起闪"的另一面。</li>
     * </ul>
     * <p>加一轮 xorshift + 乘法混合之后，三个轴的贡献都会被摊平到整个 0~359 区间，
     * 无论沿哪个轴排都看不出等差。</p>
     */
    private float phaseFromPos() {
        if (this.pos == null) {
            return 0.0F;
        }
        int h = this.pos.getX() * 73856093 ^ this.pos.getY() * 19349663 ^ this.pos.getZ() * 83492791;
        h ^= h >>> 13;
        h *= 0x5BD1E995;
        h ^= h >>> 15;
        // h % 360 ∈ (-360, 360)，取绝对值后落在 0..359；Integer.MIN_VALUE 到不了（% 之后不会溢出）
        return Math.abs(h % 360);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        this.phaseOffset = this.phaseFromPos();
        if (this.world == null || this.world.isRemote || this.pos == null) {
            return;
        }
        // 新放的灯：方块状态已经是 ON（放置时就点亮），但 cfg.on 还是构造时的 false，
        // 不补这一步会出现"灯罩亮着、方块光却不亮，右键一下才正常"。
        IBlockState st = this.world.getBlockState(this.pos);
        if (st.getBlock() instanceof BlockAlarmLight) {
            boolean stateOn = st.getValue(BlockAlarmLight.ON).booleanValue();
            if (stateOn && !this.cfg.on) {
                this.cfg.on = true;
                this.markDirty();
            }
        }
        if (!this.isAuto()) {
            this.powered = this.world.isBlockPowered(this.pos);
        }
        // 真正的 setBlockState 放到首 tick（见 update()），onLoad 里改世界不安全
        this.syncPending = true;
    }

    @Override
    public void setPos(BlockPos posIn) {
        super.setPos(posIn);
        this.phaseOffset = this.phaseFromPos();
    }

    /**
     * 把灯芯的开关写进方块状态（{@code ON} 是方块光 / 渲染 / 屏幕空间光照的唯一依据）。
     *
     * <p>★ 只在服务端写：客户端自己写会和服务器下发的包打架，还会顺手把区块局部重打光。</p>
     */
    private void applyOnState() {
        if (this.world == null || this.world.isRemote || this.pos == null) {
            return;
        }
        IBlockState st = this.world.getBlockState(this.pos);
        if (!(st.getBlock() instanceof BlockAlarmLight)) {
            return;
        }
        boolean want = this.isEnabled();
        if (st.getValue(BlockAlarmLight.ON).booleanValue() == want) {
            return;
        }
        this.world.setBlockState(this.pos, st.withProperty(BlockAlarmLight.ON, Boolean.valueOf(want)), 3);
        // setBlockState 不会因为"只有自己这个方块的光变了"而主动重算光照，
        // 所以在这一刻显式补一次（只发生在开关跳变时，不是每刻）
        this.world.checkLight(this.pos);
    }

    // ==================== NBT / 同步 ====================

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        if (compound.hasKey("AColor") || compound.hasKey("AOn")) {
            this.cfg.readFromNBT(compound);
        } else {
            this.migrateLegacy(compound);
        }
        this.phaseOffset = this.phaseFromPos();
        this.source = compound.hasKey("AlarmSrcX")
                ? new BlockPos(compound.getInteger("AlarmSrcX"),
                        compound.getInteger("AlarmSrcY"), compound.getInteger("AlarmSrcZ"))
                : null;
        this.spotMode = clampMode(compound.getInteger("SpotMode"));
        this.triggerMode = readTrigger(compound);
        this.resolved = null;
        this.resolveTried = false;
        this.powered = compound.getBoolean("Powered");
        // v17.9：手动配置快照。缺键（旧存档）读作 null —— 解除自动染色时
        // 保持当前颜色不动（旧行为），玩家重新染一次即可。
        this.manual = compound.hasKey("MColor")
                ? new AlarmConfig(compound.getInteger("MColor"),
                        compound.getInteger("MRpm"), compound.getBoolean("MOn"))
                : null;
        // 等级不存盘：重载后 10 刻内会重新判一次，存了反而可能和现场不符
        this.alertLevel = -1;
        this.scanTimer = 0;
    }

    /** 越界的模式值一律当"跟随全局"，别让坏存档把渲染分派到一个不存在的分支 */
    private static int clampMode(int mode) {
        return mode < SPOT_AUTO || mode > SPOT_TEXTURE ? SPOT_AUTO : mode;
    }

    /**
     * 读触发方式。
     *
     * <p>★ 缺键时给 {@link #TRIGGER_ANY}，<b>不是</b> {@code getInteger} 的默认 0：
     * 0 是「右键」，会让所有 17.8 存档里接了红石的灯升级后突然不听红石 ——
     * 玩家看到的是"线还在、灯不灭了"，几乎不可能联想到是版本升级改了默认值。</p>
     */
    private static int readTrigger(NBTTagCompound c) {
        return c.hasKey("Trigger") ? clampTrigger(c.getInteger("Trigger")) : TRIGGER_ANY;
    }

    /** 把联动源写进任何一个 NBT（存档与 {@code getUpdateTag} 共用，避免两处漏字段） */
    private void writeSource(NBTTagCompound c) {
        if (this.source != null) {
            c.setInteger("AlarmSrcX", this.source.getX());
            c.setInteger("AlarmSrcY", this.source.getY());
            c.setInteger("AlarmSrcZ", this.source.getZ());
        }
        c.setInteger("SpotMode", this.spotMode);
        c.setInteger("Trigger", this.triggerMode);
    }

    /**
     * 从同步包/存档里读回"联动源 + 光锥模式"。
     *
     * <p>★ 联动源<b>必须</b>进 {@code getUpdateTag}：客户端拿不到它就永远
     * {@code isAuto() == false}，GUI 会把"联动中"显示成"未联动"，
     * 玩家点一下按钮就把好好的联动关掉了。</p>
     */
    private void readSource(NBTTagCompound c) {
        this.source = c.hasKey("AlarmSrcX")
                ? new BlockPos(c.getInteger("AlarmSrcX"),
                        c.getInteger("AlarmSrcY"), c.getInteger("AlarmSrcZ"))
                : null;
        this.spotMode = clampMode(c.getInteger("SpotMode"));
        // ★ 触发方式同样要同步：客户端 onBlockActivated 要按它决定"这一下是开关还是开面板"，
        //   不同步就会出现"客户端以为在开面板、服务端以为在关灯"的错位。
        this.triggerMode = readTrigger(c);
        this.resolved = null;
        this.resolveTried = false;
    }

    /**
     * 17.6 及更早存档的字段迁移。
     *
     * <p>旧字段：{@code Color}（RGB）、{@code Speed}（旧渲染的转速系数）、
     * {@code ManualEnabled} / {@code Enabled}（手动开关）、{@code Powered}（红石）。
     * 旧 {@code Radius} / {@code ConeAngle} 直接丢弃 —— 新渲染的锥角按安装方式分档，
     * 再留着会让老存档的灯比别人的细一号。</p>
     *
     * <p>转速换算：旧式 {@code 每 tick 角度 = speed * 6} ⇒ {@code rpm = speed * 20}。
     * 旧默认 0.5 正好是 10 rpm，和新的"警告档"同速。</p>
     */
    private void migrateLegacy(NBTTagCompound c) {
        this.cfg.color = (c.hasKey("Color") ? c.getInteger("Color") : 0xFF3A3A) & 0xFFFFFF;
        boolean manual = c.hasKey("ManualEnabled") ? c.getBoolean("ManualEnabled")
                : (c.hasKey("Enabled") ? c.getBoolean("Enabled") : true);
        // ★ 红石状态不并进 cfg.on：并进去之后红石断开了灯也灭不掉（cfg.on 已经是 true）。
        //   红石单独由 readFromNBT 末尾的 powered 字段恢复。
        this.cfg.on = manual;
        float speed = c.hasKey("Speed") ? c.getFloat("Speed") : 0.5F;
        this.cfg.rpm = AlarmConfig.clampRpm(Math.round(speed * 20.0F));
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        this.cfg.writeToNBT(compound);
        this.writeSource(compound);
        compound.setBoolean("Powered", this.powered);
        // v17.9：手动配置快照只进存档 —— getUpdateTag 不带它，
        // 客户端不需要知道"玩家原本染的什么颜色"，恢复由服务端做完再广播
        if (this.manual != null) {
            compound.setInteger("MColor", this.manual.color & 0xFFFFFF);
            compound.setInteger("MRpm", this.manual.rpm);
            compound.setBoolean("MOn", this.manual.on);
        }
        return compound;
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        NBTTagCompound c = super.getUpdateTag();
        this.cfg.writeToNBT(c);
        // ★ 联动源与光锥模式必须一起下发：客户端没有任何别的途径知道这两件事
        this.writeSource(c);
        c.setBoolean("Powered", this.powered);
        return c;
    }

    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        if (this.pos == null) {
            return null;
        }
        return new SPacketUpdateTileEntity(this.pos, 1, this.getUpdateTag());
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        if (pkt == null || pkt.getNbtCompound() == null) {
            return;
        }
        NBTTagCompound c = pkt.getNbtCompound();
        if (!c.hasKey("AColor") && !c.hasKey("AOn")) {
            // 没有任何配置字段的包（旧格式 / 别的模组发的空包）：不能拿它去覆盖 cfg，
            // 否则 getInteger 读回 0 ⇒ 灯当场变黑
            return;
        }
        this.cfg.readFromNBT(c);
        this.readSource(c);
        this.powered = c.getBoolean("Powered");
        this.phaseOffset = this.phaseFromPos();
        if (this.world != null) {
            // 方块状态里的 ON 也要跟上，否则原版光照还是旧的那一档
            IBlockState state = this.world.getBlockState(this.pos);
            if (state.getBlock() instanceof BlockAlarmLight) {
                boolean want = this.isEnabled();
                if (state.getValue(BlockAlarmLight.ON).booleanValue() != want) {
                    this.world.setBlockState(this.pos,
                            state.withProperty(BlockAlarmLight.ON, Boolean.valueOf(want)), 2);
                }
            }
        }
    }

    @Override
    public void handleUpdateTag(NBTTagCompound tag) {
        if (tag == null || (!tag.hasKey("AColor") && !tag.hasKey("AOn"))) {
            return;
        }
        this.cfg.readFromNBT(tag);
        this.readSource(tag);
        this.powered = tag.getBoolean("Powered");
        this.phaseOffset = this.phaseFromPos();
    }

    /** 渲染包围盒要放大：灯罩 + 六向底座，按 1 格算在屏幕边缘会被裁掉 */
    @Override
    public double getMaxRenderDistanceSquared() {
        return 96.0D * 96.0D;
    }

    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        if (this.pos == null) {
            return new AxisAlignedBB(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D);
        }
        return new AxisAlignedBB(this.pos).grow(2.0D);
    }
}
