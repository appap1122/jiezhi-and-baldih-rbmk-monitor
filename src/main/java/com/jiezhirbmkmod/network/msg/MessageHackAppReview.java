package com.jiezhirbmkmod.network.msg;

import com.jiezhirbmkmod.client.gui.GuiHackAppReview;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * v17.6 审批弹窗包（服务端 → 客户端，id 18）。
 *
 * <p>发给每一名在线服主/管理员：弹出「玩家xxx试图开启/关闭黑客攻击玩法」
 * 界面，下方同意/不同意两个按钮。投出的票走 {@link MessageHackAppVote}
 * 回服务端，开启申请一票否决、全票通过；关闭申请一名同意即生效。</p>
 */
public class MessageHackAppReview
implements IMessage {

    /** 发起申请的玩家名 */
    private String applicant = "";
    /** true = 关闭开关的申请；false = 开启开关的申请 */
    private boolean close;
    /** 申请编号：投票时带回，防止旧弹窗投到新申请上 */
    private int appId;
    /** v18.2 申请类别：0 = 黑客玩法，1 = 阈值防护链 */
    private int kind;

    public MessageHackAppReview() {
    }

    /** v17.6 兼容：黑客玩法申请 */
    public MessageHackAppReview(String applicant, boolean close, int appId) {
        this(applicant, close, appId, 0);
    }

    public MessageHackAppReview(String applicant, boolean close, int appId, int kind) {
        this.applicant = applicant;
        this.close = close;
        this.appId = appId;
        this.kind = kind;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.applicant = ByteBufUtils.readUTF8String(buf);
        this.close = buf.readBoolean();
        this.appId = buf.readInt();
        this.kind = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, this.applicant);
        buf.writeBoolean(this.close);
        buf.writeInt(this.appId);
        buf.writeInt(this.kind);
    }

    public static class Handler
    implements IMessageHandler<MessageHackAppReview, IMessage> {

        @Override
        public IMessage onMessage(final MessageHackAppReview msg, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    Minecraft.getMinecraft().displayGuiScreen(
                            new GuiHackAppReview(msg.applicant, msg.close, msg.appId, msg.kind));
                }
            });
            return null;
        }
    }
}
