package com.jiezhirbmkmod.network.msg;

import com.jiezhirbmkmod.dfc.HackAppManager;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * v17.6 黑客攻击玩法申请提交包（客户端 → 服务端，id 17）。
 *
 * <p>申请人双击操作员工牌打开选择 GUI，点「发送申请」发出本包。
 * {@code close} = true 表示申请<b>关闭</b>开关（仅管理员/房主可发起），
 * false 表示申请<b>开启</b>开关（默认关，全票通过才开）。服务端在
 * {@link HackAppManager} 里做全部校验与状态机推进。</p>
 */
public class MessageHackAppSend
implements IMessage {

    /** true = 申请关闭开关；false = 申请开启开关 */
    private boolean close;

    public MessageHackAppSend() {
    }

    public MessageHackAppSend(boolean close) {
        this.close = close;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.close = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(this.close);
    }

    public static class Handler
    implements IMessageHandler<MessageHackAppSend, IMessage> {

        @Override
        public IMessage onMessage(final MessageHackAppSend msg, MessageContext ctx) {
            final EntityPlayerMP sender = ctx.getServerHandler().player;
            sender.getServer().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    HackAppManager.handleSend(sender, msg.close);
                }
            });
            return null;
        }
    }
}
