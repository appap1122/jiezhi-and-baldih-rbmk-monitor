package com.jiezhirbmkmod.block;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

/**
 * v17.4 shared power state machine for the "real" screen family.
 *
 * <p>Three states, deliberately named after a piece of equipment rather than a
 * boolean, because "off" and "standby" mean different things on a control-room
 * display: off is a dead panel, standby keeps the tube warm but shows nothing,
 * on is normal operation.</p>
 *
 * <pre>
 *   OFF (0)  --click-->  STANDBY (1)  --click-->  ON (2)
 *   OFF      --double--> ON
 *   STANDBY  --double--> OFF
 *   ON       --double--> OFF
 * </pre>
 *
 * <p>The double-click target is always computed from the state the screen was
 * in <em>before</em> the first click, not from the current state: the first
 * click has already moved the screen by the time the second arrives, so reading
 * the live state would make a double-click on a dead screen land on standby
 * instead of on. That is why {@link #doubleTarget(int)} takes the snapshot
 * value.</p>
 */
public final class RealPower {

    /** Dead panel, nothing is drawn. */
    public static final int OFF = 0;
    /** Powered but dark: the tube is warm, no picture yet. */
    public static final int STANDBY = 1;
    /** Normal operation. */
    public static final int ON = 2;

    /** Redstone strength at or above this counts as "mains present". */
    public static final int REDSTONE_MIN = 10;

    /** FE buffer size of one screen. */
    public static final int ENERGY_CAPACITY = 100000;
    /** FE accepted per tick; a single basic generator is enough to keep up. */
    public static final int ENERGY_MAX_INPUT = 500;
    /** FE drained per tick while the screen shows a picture. */
    public static final int ENERGY_DRAW_ON = 10;
    /** FE drained per tick while the screen is only in standby. */
    public static final int ENERGY_DRAW_STANDBY = 2;

    // ---- v18.0 HE (HBM energy) side of the mains ----

    /**
     * v18.0 拟真厂用电：屏的独立 HE 缓冲（HBM 能量单位）。真实主控室吃
     * "厂用电"——堆发的电优先喂自己的仪控；这里屏从相邻 HBM 能量方块拉
     * HE，RF/FE 退居外部电源（UPS 语义）。HBM 的数值尺度大，容量与功耗
     * 都按 FE 的 10 倍量级定，占比仍可忽略（一台 HBM RBMK 输出几千到几万
     * HE/t，一块屏只喝几十）。
     */
    public static final long HE_CAPACITY = 1000000L;
    /** HE accepted per tick from neighbours. */
    public static final long HE_MAX_INPUT = 4000L;
    /**
     * HE 侧费率基准（ON/STANDBY 每刻），恰为 FE 费率 × {@link #HE_PER_FE}。
     * 实际扣费统一按 {@code drawFor(state) × HE_PER_FE} 换算 —— 只维护
     * 一套费率源，这两条仅作数值文档与换算自检。
     */
    public static final long HE_DRAW_ON = 100L;
    /** HE drained per tick while the screen is only in standby. */
    public static final long HE_DRAW_STANDBY = 20L;
    /** 换算：1 FE 当量 ≈ 10 HE（扣费先走 HE，不足的 FE 当量再扣 FE）。 */
    public static final int HE_PER_FE = 10;

    /** 电源来源指示（拟真主控室的电源牌）：无 / 厂用 HE / 外部 FE。 */
    public static final int SOURCE_NONE = 0;
    public static final int SOURCE_HE = 1;
    public static final int SOURCE_FE = 2;

    /** Click window in milliseconds; two clicks inside it count as a double. */
    public static final long DOUBLE_CLICK_MS = 1500L;
    /**
     * Same window expressed in ticks (20 tps). The click handlers use this one:
     * {@code System.currentTimeMillis()} can jump backwards on a wall-clock
     * correction, which would make {@code now - last} negative and silently
     * swallow every double click, while the world tick counter only ever grows.
     */
    public static final long DOUBLE_CLICK_TICKS = 30L;

    private RealPower() {
    }

    /**
     * A screen may run only when both supplies are present: redstone of at
     * least 10 next to the block <em>and</em> a non-empty FE buffer. Either one
     * alone keeps the panel dark.
     */
    public static boolean isSupplied(World world, BlockPos pos, int energyStored) {
        if (world == null || pos == null) {
            return false;
        }
        if (energyStored <= 0) {
            return false;
        }
        return world.getRedstonePowerFromNeighbors(pos) >= REDSTONE_MIN;
    }

    /**
     * v18.0 双源供电判定：红石许可照旧，能量侧改为「HE 或 FE 任一有电即可」
     * —— 厂用 HE（HBM）与外部 FE（RF 兼容）互为备份，模拟真实电厂的
     * 厂用电源 + 外部电源/UPS 双路进线。两路都空 + 红石满足也点不亮。
     */
    public static boolean isSupplied(World world, BlockPos pos, boolean heAvailable, boolean feAvailable) {
        if (world == null || pos == null) {
            return false;
        }
        if (!heAvailable && !feAvailable) {
            return false;
        }
        return world.getRedstonePowerFromNeighbors(pos) >= REDSTONE_MIN;
    }

    /** How much FE this state costs per tick. */
    public static int drawFor(int state) {
        return state == ON ? ENERGY_DRAW_ON : (state == STANDBY ? ENERGY_DRAW_STANDBY : 0);
    }

    /** Redstone side of {@link #isSupplied}, exposed for the status line. */
    public static boolean hasRedstone(World world, BlockPos pos) {
        return world != null && pos != null
                && world.getRedstonePowerFromNeighbors(pos) >= REDSTONE_MIN;
    }

    /** What a single click does. */
    public static int nextSingle(int state) {
        switch (state) {
            case OFF:
                return STANDBY;
            case STANDBY:
                return ON;
            case ON:
            default:
                return STANDBY;
        }
    }

    /**
     * What the second click of a double click jumps to.
     *
     * @param stateBeforeFirstClick the state before the first click landed
     */
    public static int doubleTarget(int stateBeforeFirstClick) {
        switch (stateBeforeFirstClick) {
            case OFF:
                return ON;
            case STANDBY:
            case ON:
            default:
                return OFF;
        }
    }

    public static String stateKey(int state) {
        switch (state) {
            case OFF:
                return "msg.jiezhirbmkmod.real.off";
            case STANDBY:
                return "msg.jiezhirbmkmod.real.standby";
            case ON:
                return "msg.jiezhirbmkmod.real.on";
            default:
                return "msg.jiezhirbmkmod.real.off";
        }
    }

    public static TextFormatting stateColor(int state) {
        switch (state) {
            case OFF:
                return TextFormatting.DARK_GRAY;
            case STANDBY:
                return TextFormatting.YELLOW;
            case ON:
            default:
                return TextFormatting.GREEN;
        }
    }

    /** Clamp anything read back from NBT or the network into 0..2. */
    public static int clamp(int state) {
        return state < OFF || state > ON ? OFF : state;
    }
}
