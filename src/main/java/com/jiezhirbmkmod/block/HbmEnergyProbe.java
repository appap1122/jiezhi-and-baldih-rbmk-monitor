package com.jiezhirbmkmod.block;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * v18.0 显示屏「厂用电」反射访问层：从相邻的 HBM 能量方块抽 HE 填进
 * 屏的独立 HE 缓冲。与 {@code ItemRbmkProbe} 同一条铁律 ——
 * <b>本文件与编译期都不出现任何 HBM 类名</b>，全部靠
 * {@code Class.forName}/{@code Method.invoke}，没装 HBM 时
 * {@link #available()} 恒 false，整条路径是零开销的死代码。
 *
 * <h3>为什么是"相邻抽取"而不是接 HBM 电网</h3>
 * HBM 的电力网络（电缆 + PowerNet）没有公开 API 坐标，反射接入
 * 电网成员注册既脆又侵入。改用拟真等价的物理模型：真实核电站主控室
 * 就是"厂用变压器就在旁边"，屏从<b>贴着它的</b> HBM 能量设备拉电 ——
 * 玩家把 HBM 的电容/发电视贴在屏旁边即完成供电，行为可预测，
 * 也不会被 HBM 电网重构炸掉。
 *
 * <h3>反射策略（宽容匹配）</h3>
 * HBM 各分叉（原版 NTM / Bob / AlCoke 等）的能量接口方法名不完全一致，
 * 所以不做精确签名绑定：
 * <ul>
 *   <li>接口探测：依次找 {@code com.hbm.tileentity.IEnergyConnector} 与
 *       {@code com.hbm.interfaces.IConsumer}，任一存在即认为 HBM 电力
 *       系统在场；</li>
 *   <li>抽电模式：读改写 —— 对相邻 tile 调无参 {@code getPower()}（long/
 *       int/short 都接受）读存量，{@code setPower(long)} 写回扣减后的值；
 *       这两个方法在 HBM 的能量 tile 上普遍存在；</li>
 *   <li>只抽不灌：{@code getPower} 缺失的相邻 tile 直接跳过（宁可抽不到，
 *       也不把 HBM 机器当垃圾桶写坏它的存量）。</li>
 * </ul>
 * Method 按相邻 tile 的实际类缓存，命中后零查找开销。
 *
 * <p>只在服务端 tick 被调用；所有异常一律吞掉降级为"这面墙今天没电"，
 * 绝不让反射问题炸掉屏的供电 tick。</p>
 */
public final class HbmEnergyProbe {

    /** 三态：null=还没探测过，TRUE/FALSE=探测结果（进程内只探一次） */
    private static volatile Boolean present = null;

    /** 探测到的 HBM 能量接口类（available() 成功时非 null） */
    private static volatile Class<?> connectorClass;

    /**
     * Method 缓存：tile 实际类 → { getPower, setPower }，查不到的类缓存
     * 空数组占位，避免每 tick 反复在同一个类上碰壁。
     */
    private static final Map<Class<?>, Method[]> METHODS = new HashMap<Class<?>, Method[]>();

    private HbmEnergyProbe() {
    }

    /**
     * HBM 电力系统是否在场（探测一次并缓存）。不抛异常 —— 类加载器
     * 抽风时按"不在场"处理。
     */
    public static boolean available() {
        Boolean p = present;
        if (p != null) {
            return p.booleanValue();
        }
        synchronized (HbmEnergyProbe.class) {
            if (present != null) {
                return present.booleanValue();
            }
            boolean ok = false;
            String[] candidates = {
                    "com.hbm.tileentity.IEnergyConnector",
                    "com.hbm.interfaces.IConsumer"};
            for (String name : candidates) {
                try {
                    connectorClass = Class.forName(name);
                    ok = true;
                    break;
                }
                catch (Throwable ignored) {
                    // 该候选不存在，试下一个
                }
            }
            present = Boolean.valueOf(ok);
            return ok;
        }
    }

    /**
     * 从 {@code pos} 六面相邻的 HBM 能量 tile 里抽取至多 {@code maxRequest}
     * HE，返回实际抽到的量。HBM 不在场 / 没有可抽的邻居时返回 0。
     *
     * <p>只对"存量够给"的邻居下手（{@code available >= 1}）：HBM 的
     * getPower 是整机存量语义，把一台机器抽到 0 与真实厂用电的行为
     * 也一致（厂用电优先级就是最高的）。</p>
     */
    public static long drawFromNeighbors(World world, BlockPos pos, long maxRequest) {
        if (world == null || pos == null || maxRequest <= 0L || !available()) {
            return 0L;
        }
        long total = 0L;
        for (EnumFacing side : EnumFacing.VALUES) {
            if (total >= maxRequest) {
                break;
            }
            BlockPos p = pos.offset(side);
            if (!world.isBlockLoaded(p)) {
                continue; // 查询绝不加载区块
            }
            TileEntity te = world.getTileEntity(p);
            if (te == null || te.isInvalid()
                    || !(connectorClass != null && connectorClass.isInstance(te))) {
                continue;
            }
            long got = drawFromTile(te, maxRequest - total);
            if (got > 0L) {
                total += got;
            }
        }
        return total;
    }

    /** 从单个 HBM tile 读改写抽电；反射任何一环失败都按 0 处理。 */
    private static long drawFromTile(TileEntity te, long maxRequest) {
        try {
            Method[] m = methodsFor(te.getClass());
            Method get = m[0];
            Method set = m[1];
            if (get == null || set == null) {
                return 0L;
            }
            long stock = readLong(get.invoke(te));
            if (stock <= 0L) {
                return 0L;
            }
            long take = Math.min(stock, maxRequest);
            set.invoke(te, Long.valueOf(stock - take));
            return take;
        }
        catch (Throwable t) {
            return 0L;
        }
    }

    /** 按类查找并缓存 { getPower, setPower }；找不到时缓存占位。 */
    private static synchronized Method[] methodsFor(Class<?> cls) {
        Method[] cached = METHODS.get(cls);
        if (cached != null) {
            return cached;
        }
        Method get = null;
        Method set = null;
        try {
            for (Method m : cls.getMethods()) {
                if (get == null && "getPower".equals(m.getName())
                        && m.getParameterTypes().length == 0
                        && isNumeric(m.getReturnType())) {
                    get = m;
                }
                else if (set == null && "setPower".equals(m.getName())
                        && m.getParameterTypes().length == 1
                        && isNumeric(m.getParameterTypes()[0])) {
                    set = m;
                }
                if (get != null && set != null) {
                    break;
                }
            }
        }
        catch (Throwable t) {
            get = null;
            set = null;
        }
        Method[] pair = new Method[]{get, set};
        METHODS.put(cls, pair);
        return pair;
    }

    private static boolean isNumeric(Class<?> t) {
        return t == long.class || t == int.class || t == short.class;
    }

    /** 把 getPower 的返回值（可能是 Integer/Long/Short）统一成 long。 */
    private static long readLong(Object o) {
        if (o instanceof Number) {
            return ((Number) o).longValue();
        }
        return 0L;
    }
}
