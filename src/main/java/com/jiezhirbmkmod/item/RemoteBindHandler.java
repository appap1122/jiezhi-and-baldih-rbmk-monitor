package com.jiezhirbmkmod.item;

import com.jiezhirbmkmod.dfc.RbmkBridge;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * v17.4.1 fix for "the remote cannot bind the console".
 *
 * <p>Recording a data source used to rely on {@code Item#onItemUse}, which
 * Forge only calls when the clicked block returns {@code false} from its own
 * {@code onBlockActivated}. HBM's RBMK console is a dummyable multiblock and
 * returns {@code true} (it opens its own GUI), so the remote silently never
 * recorded anything - the player right-clicks the console, gets no message at
 * all, then right-clicks the display and sees "Nothing recorded".</p>
 *
 * <p>This handler runs <em>before</em> the block logic, records the source on
 * the server and consumes the click so the console GUI does not fight with the
 * remote. It is deliberately narrow:</p>
 * <ul>
 *   <li>only the RBMK remote in hand,</li>
 *   <li>only while not sneaking (sneak is the "record a tank/battery" gesture),</li>
 *   <li>only when the clicked block really resolves to an RBMK data source.</li>
 * </ul>
 * <p>Everything else falls through to vanilla/HBM untouched.</p>
 */
public final class RemoteBindHandler {

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        World world = event.getWorld();
        // server only: the client must not write to its own copy of the stack,
        // it would be overwritten by the next sync anyway
        if (world == null || world.isRemote) {
            return;
        }
        EntityPlayer player = event.getEntityPlayer();
        if (player == null || player.isSneaking()) {
            return;
        }
        ItemStack held = event.getItemStack();
        if (held == null || held.isEmpty() || !(held.getItem() instanceof ItemRbmkRemote)) {
            return;
        }
        BlockPos pos = event.getPos();
        TileEntity te;
        try {
            te = RbmkBridge.resolveDataSource(world, pos);
        }
        catch (Throwable t) {
            return;
        }
        if (!RbmkBridge.isDataSource(te)) {
            // not an RBMK block: never touch the click
            return;
        }
        boolean console = RbmkBridge.isConsole(te);
        BlockPos target = te.getPos();
        ItemRbmkRemote.writeTarget(held, target, world.provider.getDimension(),
                console ? "CONSOLE" : "PART");
        ItemRbmkRemote.reply(player, console
                ? "msg.jiezhirbmkmod.remote.recorded_console"
                : "msg.jiezhirbmkmod.remote.recorded_part", ItemRbmkRemote.pos(target));
        // consume the click: the console GUI would otherwise open on top of
        // the bind and make the remote look broken
        event.setUseBlock(Event.Result.DENY);
        event.setUseItem(Event.Result.DENY);
        event.setCanceled(true);
    }
}
