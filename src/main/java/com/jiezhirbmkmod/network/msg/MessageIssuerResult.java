package com.jiezhirbmkmod.network.msg;

import com.jiezhirbmkmod.client.gui.GuiRbmkIssuer;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * v17.6 签发结果回包（服务端 → 客户端）。
 *
 * <p>授权动作完成后由 {@link MessageIssuerAuthorize} 的服务端处理器发回
 * 操作者：成功张数、无卡/不在线跳过数，以及<b>实际按当前配置产出的卡的
 * 完整 NBT</b>。GUI 收到后不再干等 —— 签发过后的卡直接出现在签发器界面
 * 的结果区（图标 + 原版 tooltip，悬浮即见签发人 / 剩余次数 / 允许档位），
 * 让"发出去的卡长什么样"当场可见、可核对。</p>
 *
 * <p>卡的 NBT 与塞给目标玩家的完全同源（同一配置同一构建函数），所以
 * GUI 里展示的就是目标背包里那张卡的样子，不是客户端猜的。</p>
 */
public class MessageIssuerResult
implements IMessage {

    /** 本次实际签发成功张数 */
    private int ok;
    /** 因签发器无空白卡而跳过的人数 */
    private int noCard;
    /** 因目标不在线而跳过的人数 */
    private int offline;
    /** 首名成功签发的目标（可能为空串） */
    private String issuedTo = "";
    /** 实际产出的卡的 NBT（ok == 0 时为 null） */
    private NBTTagCompound cardTag;

    public MessageIssuerResult() {
    }

    public MessageIssuerResult(int ok, int noCard, int offline, String issuedTo, NBTTagCompound cardTag) {
        this.ok = ok;
        this.noCard = noCard;
        this.offline = offline;
        this.issuedTo = issuedTo == null ? "" : issuedTo;
        this.cardTag = cardTag;
    }

    public int getOk() {
        return this.ok;
    }

    public int getNoCard() {
        return this.noCard;
    }

    public int getOffline() {
        return this.offline;
    }

    public String getIssuedTo() {
        return this.issuedTo;
    }

    public NBTTagCompound getCardTag() {
        return this.cardTag;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.ok = buf.readByte();
        this.noCard = buf.readByte();
        this.offline = buf.readByte();
        this.issuedTo = ByteBufUtils.readUTF8String(buf);
        this.cardTag = buf.readBoolean() ? ByteBufUtils.readTag(buf) : null;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(this.ok);
        buf.writeByte(this.noCard);
        buf.writeByte(this.offline);
        ByteBufUtils.writeUTF8String(buf, this.issuedTo == null ? "" : this.issuedTo);
        buf.writeBoolean(this.cardTag != null);
        if (this.cardTag != null) {
            ByteBufUtils.writeTag(buf, this.cardTag);
        }
    }

    public static class Handler
    implements IMessageHandler<MessageIssuerResult, IMessage> {

        @Override
        public IMessage onMessage(final MessageIssuerResult msg, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    if (Minecraft.getMinecraft().currentScreen instanceof GuiRbmkIssuer) {
                        ((GuiRbmkIssuer) Minecraft.getMinecraft().currentScreen)
                                .receiveResult(msg);
                    }
                    // GUI 已被关掉（例如操作者提前退出）则丢弃结果，无副作用
                }
            });
            return null;
        }
    }
}
