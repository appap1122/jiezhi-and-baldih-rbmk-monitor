package com.jiezhirbmkmod.proxy;

import com.jiezhirbmkmod.block.ContainerCustomScreen;
import com.jiezhirbmkmod.block.TileCustomScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;

public class CommonProxy
implements IGuiHandler {
    public static final int GUI_CUSTOM_SCREEN = 1;
    /** alert thresholds + redstone linkage (sneak + empty hand right-click on a screen) */
    public static final int GUI_ALERT = 2;
    /** standalone siren tuner */
    public static final int GUI_SIREN = 3;
    /** handheld RBMK terminal (command prompt) */
    public static final int GUI_TERMINAL = 4;
    /** portable laptop terminal, held item based (v11.2) */
    public static final int GUI_LAPTOP = 5;
    /** rotating alarm light colour editor (v16.0) */
    public static final int GUI_ALARM_LIGHT = 7;
    /** v17.6 签发器：1x1 发卡柜台（单槽容器 GUI） */
    public static final int GUI_ISSUER = 8;
    /** v17.6 黑客攻击玩法申请选择界面（操作员工牌双击打开，无槽） */
    public static final int GUI_HACK_APP = 9;
    /** v17.7 巡屏中控 GUI（绑定清单走 MessagePatrolSync 专用包，无槽） */
    public static final int GUI_PATROL = 12;
    /** v17.9 多堆芯聚合中控 GUI（归属表走 MessageAggregateSync 专用包，无槽） */
    public static final int GUI_AGGREGATE = 13;

    public void preInit() {
    }

    public void init() {
    }

    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        if (id == GUI_CUSTOM_SCREEN) {
            TileEntity te = world.getTileEntity(new BlockPos(x, y, z));
            if (te instanceof TileCustomScreen) {
                return new ContainerCustomScreen(player.inventory, (TileCustomScreen)te);
            }
            return null;
        }
        // v17.6 签发器：真容器（单槽空白卡），槽内容由原版容器同步机制下发
        if (id == GUI_ISSUER) {
            TileEntity te = world.getTileEntity(new BlockPos(x, y, z));
            if (te instanceof com.jiezhirbmkmod.block.TileRbmkIssuer) {
                return new com.jiezhirbmkmod.inventory.ContainerIssuer(
                        (com.jiezhirbmkmod.block.TileRbmkIssuer) te);
            }
            return null;
        }
        if (id == GUI_ALERT || id == GUI_SIREN || id == GUI_TERMINAL || id == GUI_LAPTOP
                || id == GUI_ALARM_LIGHT) {
            // These screens are plain GuiScreens, but Forge's openGui() only
            // sends the OpenGui packet when the server side returns a
            // non-null container. Returning an empty container is the
            // standard trick for container-less GUIs.
            return new EmptyContainer();
        }
        // v17.6 黑客攻击玩法申请：无槽但带窗口属性（开关状态/能否发起关闭），
        // 所以不用 EmptyContainer，用专用的 ContainerHackApp
        if (id == GUI_HACK_APP) {
            return new com.jiezhirbmkmod.inventory.ContainerHackApp(
                    com.jiezhirbmkmod.item.ItemRbmkOpCard.isTrueOp(player));
        }
        // v17.7 巡屏中控：无槽容器；绑定清单由方块右键处理器在 openGui
        // 之前用 MessagePatrolSync 下发（坐标塞不进 16 位窗口属性）
        if (id == GUI_PATROL) {
            return new com.jiezhirbmkmod.inventory.ContainerPatrol();
        }
        // v17.9 多堆芯聚合中控：同为无槽容器；归属表由右键处理器经
        // MessageAggregateSync 在 openGui 前下发
        if (id == GUI_AGGREGATE) {
            return new com.jiezhirbmkmod.inventory.ContainerPatrol();
        }
        return null;
    }

    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        return null;
    }

    /**
     * Container-less screens still need a server-side Container in 1.12.2,
     * because Forge only sends the OpenGui packet when this is non-null.
     */
    private static final class EmptyContainer extends Container {
        @Override
        public boolean canInteractWith(EntityPlayer player) {
            return true;
        }
    }
}

