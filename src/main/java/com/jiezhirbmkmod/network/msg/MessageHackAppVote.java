package com.jiezhirbmkmod.network.msg;


import com.jiezhirbmkmod.network.NetGuard;
import com.jiezhirbmkmod.dfc.HackAppManager;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * v17.6 审批投票包（客户端 → 服务端，id 19）。
 *
 * <p>服主/管理员在审批弹窗点「同意」/「不同意」后发出：申请编号 + 票。
 * 服务端校验投票人确在受理名单内（GUI 是包触发的，伪造包进不了名单），
 * 开启申请一票否决/全票通过，关闭申请一名同意即生效。</p>
 */
public class MessageHackAppVote
implements IMessage {

    private int appId;
    private boolean agree;

    public MessageHackAppVote() {
    }

    public MessageHackAppVote(int appId, boolean agree) {
        this.appId = appId;
        this.agree = agree;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.appId = buf.readInt();
        this.agree = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.appId);
        buf.writeBoolean(this.agree);
    }

    public static class Handler
    implements IMessageHandler<MessageHackAppVote, IMessage> {

        @Override
        public IMessage onMessage(final MessageHackAppVote msg, MessageContext ctx) {
            try {
            final EntityPlayerMP voter = ctx.getServerHandler().player;
            voter.getServer().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    HackAppManager.handleVote(voter, msg.appId, msg.agree);
                }
            });
            return null;
        
        } catch (Throwable t) {
            NetGuard.reportHandler("Handler", t);
            return null;
        }
        }
    }
}
