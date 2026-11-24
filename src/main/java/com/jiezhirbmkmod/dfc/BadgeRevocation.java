package com.jiezhirbmkmod.dfc;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

import net.minecraftforge.fml.common.FMLCommonHandler;

/**
 * v17.7 工牌吊销注册表：{@code /rbmkrevoke} 的数据层。
 *
 * <p>工牌是实体凭证、刻意不与玩家绑定（谁捡到谁能用，与现实工牌同语义），
 * 但签发时<b>知道发给了谁</b> —— 签发器按勾选的玩家发卡，卡上留
 * {@code Holder}（持有人名）与 {@code Epoch}（签发时的吊销纪元）。
 * 吊销不追卡：把纪元 +1，所有纪元旧的卡当场失效，无论卡在哪。</p>
 *
 * <ul>
 *   <li><b>按人吊销</b>：{@code /rbmkrevoke <玩家>} —— 该玩家名下纪元 +1，
 *       发给他的旧卡全灭；卡本身仍是实体，可以销毁或留作纪念；</li>
 *   <li><b>全服吊销</b>：{@code /rbmkrevoke all} —— 全局纪元 +1，
 *       所有已签发卡（含无持有人记录的旧卡/创造栏卡）全灭；</li>
 *   <li><b>持久化</b>：存档目录 {@code rbmk_revoke.dat}（Properties 格式，
 *       global=<纪元>、每行 <玩家>=<纪元>），崩溃不丢纪元 —— 纪元回退
 *       等于把吊销过的卡集体复活，绝不允许。</li>
 * </ul>
 *
 * <p>判定只有一处：{@link com.jiezhirbmkmod.item.ItemRbmkOpCard#issuedCard}
 * 的工牌有效性检查。纪元比较是纯内存读，无 IO 热路径。</p>
 */
public final class BadgeRevocation {

    /** 全局吊销纪元 */
    private static int globalEpoch = 0;
    /** 每玩家吊销纪元（键为玩家名小写） */
    private static final java.util.Map<String, Integer> EPOCHS =
            new java.util.HashMap<String, Integer>();
    /** 是否已尝试从盘加载（失败不重试，避免每次判定都打盘） */
    private static boolean loaded = false;
    private static final Object LOCK = new Object();

    private BadgeRevocation() {
    }

    /** 当前全局纪元 */
    public static int globalEpoch() {
        BadgeRevocation.ensureLoaded();
        return BadgeRevocation.globalEpoch;
    }

    /** 某玩家名下当前纪元（无记录 = 0） */
    public static int epochOf(String holder) {
        if (holder == null || holder.isEmpty()) {
            return 0;
        }
        BadgeRevocation.ensureLoaded();
        Integer v = BadgeRevocation.EPOCHS.get(holder.toLowerCase(java.util.Locale.ROOT));
        return v == null ? 0 : v.intValue();
    }

    /** 全服吊销：全局纪元 +1 并落盘，返回新纪元 */
    public static int revokeAll() {
        synchronized (BadgeRevocation.LOCK) {
            BadgeRevocation.ensureLoaded();
            ++BadgeRevocation.globalEpoch;
            BadgeRevocation.save();
            return BadgeRevocation.globalEpoch;
        }
    }

    /** 按人吊销：该玩家名下纪元 +1 并落盘，返回新纪元 */
    public static int revokePlayer(String holder) {
        if (holder == null || holder.isEmpty()) {
            return 0;
        }
        String key = holder.toLowerCase(java.util.Locale.ROOT);
        synchronized (BadgeRevocation.LOCK) {
            BadgeRevocation.ensureLoaded();
            Integer old = BadgeRevocation.EPOCHS.get(key);
            int next = (old == null ? 0 : old.intValue()) + 1;
            BadgeRevocation.EPOCHS.put(key, Integer.valueOf(next));
            BadgeRevocation.save();
            return next;
        }
    }

    /**
     * 卡是否已被吊销：卡纪元落后于全局纪元，或（有持有人记录时）落后于
     * 持有人纪元。没有 Epoch 键的旧卡按纪元 0 处理 —— 任何一次吊销都会
     * 波及它们，这是有意的：老卡重签一次就进新体系。
     */
    public static boolean isRevoked(net.minecraft.item.ItemStack stack) {
        if (stack == null || !stack.hasTagCompound()) {
            return false;
        }
        net.minecraft.nbt.NBTTagCompound tag = stack.getTagCompound();
        if (!tag.getBoolean("Issued")) {
            return false; // 空白卡无所谓吊销
        }
        int cardEpoch = tag.hasKey("Epoch") ? tag.getInteger("Epoch") : 0;
        if (cardEpoch < BadgeRevocation.globalEpoch()) {
            return true;
        }
        String holder = tag.getString("Holder");
        return !holder.isEmpty() && cardEpoch < BadgeRevocation.epochOf(holder);
    }

    // ---- 持久化 ----

    private static File file() {
        return FMLCommonHandler.instance().getMinecraftServerInstance() == null
                ? null
                : new File(FMLCommonHandler.instance().getMinecraftServerInstance()
                        .getDataDirectory(), "rbmk_revoke.dat");
    }

    private static void ensureLoaded() {
        synchronized (BadgeRevocation.LOCK) {
            if (BadgeRevocation.loaded) {
                return;
            }
            BadgeRevocation.loaded = true;
            File f = BadgeRevocation.file();
            if (f == null || !f.isFile()) {
                return; // 客户端/尚无记录：纪元保持 0
            }
            try {
                Properties p = new Properties();
                FileInputStream in = new FileInputStream(f);
                try {
                    p.load(in);
                } finally {
                    in.close();
                }
                BadgeRevocation.globalEpoch = Integer.parseInt(
                        p.getProperty("global", "0").trim());
                for (java.util.Enumeration<?> e = p.propertyNames(); e.hasMoreElements();) {
                    String k = String.valueOf(e.nextElement());
                    if ("global".equals(k)) {
                        continue;
                    }
                    try {
                        BadgeRevocation.EPOCHS.put(k.toLowerCase(java.util.Locale.ROOT),
                                Integer.valueOf(p.getProperty(k).trim()));
                    } catch (NumberFormatException ignore) {
                        // 坏行跳过，不拖垮整个注册表
                    }
                }
            } catch (Exception ex) {
                com.jiezhirbmkmod.RbmkMonitorMod.warn(
                        "[rbmkrevoke] rbmk_revoke.dat 读取失败，纪元按 0 处理: " + ex);
            }
        }
    }

    private static void save() {
        File f = BadgeRevocation.file();
        if (f == null) {
            return; // 无服务端上下文（如客户端预览）：只保留内存值
        }
        try {
            Properties p = new Properties();
            p.setProperty("global", String.valueOf(BadgeRevocation.globalEpoch));
            for (java.util.Map.Entry<String, Integer> e : BadgeRevocation.EPOCHS.entrySet()) {
                p.setProperty(e.getKey(), String.valueOf(e.getValue().intValue()));
            }
            FileOutputStream out = new FileOutputStream(f);
            try {
                p.store(out, "RBMK badge revocation epochs (rbmkrevoke)");
            } finally {
                out.close();
            }
        } catch (Exception ex) {
            com.jiezhirbmkmod.RbmkMonitorMod.warn(
                    "[rbmkrevoke] rbmk_revoke.dat 写入失败: " + ex);
        }
    }
}
