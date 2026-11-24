package com.jiezhirbmkmod.network.msg;

import com.jiezhirbmkmod.client.gui.GuiHackAppResult;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * v17.6 审批结果回信包（服务端 → 客户端，id 20）。
 *
 * <p>发给发起申请的玩家：「您的开启/关闭黑客攻击玩法申请已被同意/拒绝」，
 * 下方「知道了」按钮关闭弹窗。同意时开关已由服务端写回并落盘、全服公告。</p>
 */
public class MessageHackAppResult
implements IMessage {

    /** true = 关闭开关的申请；false = 开启开关的申请 */
    private boolean close;
    /** true = 通过；false = 被拒（一票否决 / 超时 / 受理人全下线） */
    private boolean accepted;

    public MessageHackAppResult() {
    }

    public MessageHackAppResult(boolean close, boolean accepted) {
        this.close = close;
        this.accepted = accepted;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.close = buf.readBoolean();
        this.accepted = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(this.close);
        buf.writeBoolean(this.accepted);
    }

    public static class Handler
    implements IMessageHandler<MessageHackAppResult, IMessage> {

        @Override
        public IMessage onMessage(final MessageHackAppResult msg, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    Minecraft.getMinecraft().displayGuiScreen(
                            new GuiHackAppResult(msg.close, msg.accepted));
                }
            });
            return null;
        }
    }
}
