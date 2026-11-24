package com.jiezhirbmkmod.client;

import com.jiezhirbmkmod.client.gui.GuiPptGoto;
import com.jiezhirbmkmod.item.ItemRbmkPptRemote;
import com.jiezhirbmkmod.network.ModNetwork;
import com.jiezhirbmkmod.network.msg.MessageRbmkPptControl;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * v17.1: while the player holds the PPT remote (either hand) the H / F / M keys
 * drive the bound screen instead of their vanilla action.
 */
@SideOnly(Side.CLIENT)
public class KeyInputHandler {

    private boolean lastNext;
    private boolean lastPrev;
    private boolean lastGoto;

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.player == null || mc.currentScreen != null) {
            return;
        }
        boolean hasRemote = KeyInputHandler.holdsRemote(mc.player);
        // edge detection: the key events repeat while held
        boolean next = hasRemote && KeyBindings.PPT_NEXT.isPressed();
        boolean prev = hasRemote && KeyBindings.PPT_PREV.isPressed();
        boolean open = hasRemote && KeyBindings.PPT_GOTO.isPressed();
        boolean nextEdge = next && !this.lastNext;
        boolean prevEdge = prev && !this.lastPrev;
        boolean openEdge = open && !this.lastGoto;
        this.lastNext = next;
        this.lastPrev = prev;
        this.lastGoto = open;
        if (!hasRemote) {
            return;
        }
        if (nextEdge) {
            ModNetwork.CHANNEL.sendToServer(new MessageRbmkPptControl(
                    MessageRbmkPptControl.NEXT, 0, BlockPos.ORIGIN));
        } else if (prevEdge) {
            ModNetwork.CHANNEL.sendToServer(new MessageRbmkPptControl(
                    MessageRbmkPptControl.PREV, 0, BlockPos.ORIGIN));
        } else if (openEdge) {
            mc.displayGuiScreen(new GuiPptGoto());
        }
    }

    public static boolean holdsRemote(EntityPlayer player) {
        return KeyInputHandler.isRemote(player.getHeldItemMainhand())
                || KeyInputHandler.isRemote(player.getHeldItemOffhand());
    }

    private static boolean isRemote(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof ItemRbmkPptRemote;
    }
}
