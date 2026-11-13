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
        if (id == GUI_ALERT || id == GUI_SIREN || id == GUI_TERMINAL || id == GUI_LAPTOP) {
            // These screens are plain GuiScreens, but Forge's openGui() only
            // sends the OpenGui packet when the server side returns a
            // non-null container. Returning an empty container is the
            // standard trick for container-less GUIs.
            return new EmptyContainer();
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

