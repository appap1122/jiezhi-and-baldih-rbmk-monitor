package com.jiezhirbmkmod.client;

import com.jiezhirbmkmod.block.BlockRbmkGraph;
import com.jiezhirbmkmod.block.TileRbmkMonitorScreen;

import net.minecraft.client.Minecraft;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * v17.7 历史回放的滚轮输入。
 *
 * <p>盯着<b>曲线屏</b>（rbmk_graph）滚动滚轮即可倒带：上滚 = 往历史回退
 * （每格 1 秒），下滚 = 前进，滚回尽头自动恢复实时。事件被消费掉，
 * 热键栏不会被误滚。</p>
 *
 * <p>实现上没有新包也没有新 GUI：回放指针存在客户端每块屏自己的
 * {@code RbmkScreenCanvas} 里（历史本来就是客户端采样的），滚轮只是
 * 把窗口终点往回挪 —— 服务端全程无感知。</p>
 */
@SideOnly(Side.CLIENT)
public class GraphReplayWheel {

    @SubscribeEvent
    public void onMouse(MouseEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.player == null || mc.world == null || mc.currentScreen != null) {
            return;
        }
        if (event.getDwheel() == 0 || mc.objectMouseOver == null
                || mc.objectMouseOver.typeOfHit != RayTraceResult.Type.BLOCK) {
            return;
        }
        TileEntity te = mc.world.getTileEntity(mc.objectMouseOver.getBlockPos());
        if (!(te instanceof TileRbmkMonitorScreen)) {
            return;
        }
        // 只认曲线屏：监控/自定义/终端屏滚轮保持原版行为（滚热键栏）
        if (!(mc.world.getBlockState(mc.objectMouseOver.getBlockPos())
                .getBlock() instanceof BlockRbmkGraph)) {
            return;
        }
        // 屏没通电时没有画面，也不必响应
        if (!((TileRbmkMonitorScreen) te).isPowered()) {
            return;
        }
        ((TileRbmkMonitorScreen) te).canvas().replayScroll(event.getDwheel());
        event.setCanceled(true); // 别把回放当成翻热键栏
    }
}
