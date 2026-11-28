package com.jiezhirbmkmod.network.msg;


import com.jiezhirbmkmod.network.NetGuard;
import com.jiezhirbmkmod.block.TileRbmkIssuer;
import com.jiezhirbmkmod.item.ItemRbmkOpCard;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * v17.6 签发器授权包（客户端 → 服务端）。
 *
 * <p>内容：签发器坐标 + 每卡次数 + 档位掩码 + 目标玩家名列表。服务端
 * 收到后<b>重新校验每一件事</b>：操作者是权限等级 2 / 单人房主、距离
 * 签发器不远、方块实体在、名单里的玩家在线 —— GUI 是前台，权限判断
 * 永远在服务端，与终端命令同一套原则。</p>
 *
 * <p>发卡：每授权一名玩家消耗槽里一张空白卡；卡 NBT 写入签发人、次数
 * 与档位掩码（{@link ItemRbmkOpCard#makeIssued}），塞进目标背包，满了
 * 脚下落地。向操作者与被授权者各回一条聊天提示，逐人成功/失败都在
 * 汇总里说清。</p>
 */
public class MessageIssuerAuthorize
implements IMessage {

    private int x;
    private int y;
    private int z;
    private int usesPerCard;
    private int tierMask;
    private String[] names;

    public MessageIssuerAuthorize() {
    }

    public MessageIssuerAuthorize(BlockPos pos, int usesPerCard, int tierMask, String[] names) {
        this.x = pos.getX();
        this.y = pos.getY();
        this.z = pos.getZ();
        this.usesPerCard = usesPerCard;
        this.tierMask = tierMask;
        this.names = names == null ? new String[0] : names;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.x = buf.readInt();
        this.y = buf.readInt();
        this.z = buf.readInt();
        this.usesPerCard = buf.readInt();
        this.tierMask = buf.readByte();
        int n = Math.min(64, buf.readByte());
        this.names = new String[n];
        for (int i = 0; i < n; ++i) {
            this.names[i] = readString(buf);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.x);
        buf.writeInt(this.y);
        buf.writeInt(this.z);
        buf.writeInt(this.usesPerCard);
        buf.writeByte(this.tierMask);
        buf.writeByte(this.names.length);
        for (int i = 0; i < this.names.length; ++i) {
            writeString(buf, this.names[i] == null ? "" : this.names[i]);
        }
    }

    private static void writeString(ByteBuf buf, String s) {
        byte[] b = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        buf.writeShort(Math.min(b.length, 255));
        buf.writeBytes(b, 0, Math.min(b.length, 255));
    }

    private static String readString(ByteBuf buf) {
        int len = buf.readShort();
        byte[] b = new byte[len];
        buf.readBytes(b);
        return new String(b, java.nio.charset.StandardCharsets.UTF_8);
    }

    public static class Handler
    implements IMessageHandler<MessageIssuerAuthorize, IMessage> {

        @Override
        public IMessage onMessage(MessageIssuerAuthorize msg, MessageContext ctx) {
            try {
            final MessageIssuerAuthorize m = msg;
            final EntityPlayerMP operator = ctx.getServerHandler().player;
            final MinecraftServer server = operator.getServer();
            if (server == null) {
                return null;
            }
            server.addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    MessageIssuerAuthorize.Handler.handle(m, operator);
                }
            });
            return null;
        
        } catch (Throwable t) {
            NetGuard.reportHandler("Handler", t);
            return null;
        }
        }

        private static void handle(MessageIssuerAuthorize msg, EntityPlayerMP operator) {
            World world = operator.world;
            MinecraftServer server = operator.getServer();
            BlockPos pos = new BlockPos(msg.x, msg.y, msg.z);
            TileEntity te = world.getTileEntity(pos);
            if (!(te instanceof TileRbmkIssuer)) {
                return;
            }
            // 门禁二次校验：GUI 打得开不代表包改得动坐标来绕
            if (!ItemRbmkOpCard.isTrueOp(operator)) {
                operator.sendStatusMessage(new TextComponentString(
                        TextFormatting.RED + "[jiezhirbmkmod] "
                                + net.minecraft.util.text.translation.I18n.translateToLocalFormatted(
                                        "msg.jiezhirbmkmod.issuer.denied")), false);
                return;
            }
            // 距离限制：离签发器太远的授权请求直接扔掉
            if (operator.getDistanceSq((double) pos.getX() + 0.5, (double) pos.getY() + 0.5,
                    (double) pos.getZ() + 0.5) > 64.0D) {
                return;
            }
            TileRbmkIssuer issuer = (TileRbmkIssuer) te;
            issuer.setUsesPerCard(msg.usesPerCard);
            issuer.setTierMask(msg.tierMask);

            String issuerName = operator.getName();
            String byLine = issuerName + " @ " + pos.getX() + "," + pos.getY() + "," + pos.getZ();
            int ok = 0;
            int noCard = 0;
            int offline = 0;
            String firstIssued = "";
            StringBuilder detail = new StringBuilder();
            for (int i = 0; i < msg.names.length; ++i) {
                String name = msg.names[i];
                if (name == null || name.isEmpty()) {
                    continue;
                }
                EntityPlayerMP target = findPlayer(server, name);
                if (target == null) {
                    ++offline;
                    detail.append(TextFormatting.GRAY).append(name).append(" ")
                            .append(net.minecraft.util.text.translation.I18n.translateToLocalFormatted(
                                    "msg.jiezhirbmkmod.issuer.offline")).append('\n');
                    continue;
                }
                ItemStack blank = issuer.takeCard();
                if (blank == null) {
                    ++noCard;
                    detail.append(TextFormatting.GRAY).append(name).append(" ")
                            .append(net.minecraft.util.text.translation.I18n.translateToLocalFormatted(
                                    "msg.jiezhirbmkmod.issuer.nocard")).append('\n');
                    continue;
                }
                // v17.7: 卡上盖持有人纪元戳 —— /rbmkrevoke 按人吊销的依据
                ItemStack issued = ItemRbmkOpCard.makeIssued(byLine,
                        issuer.getUsesPerCard(), issuer.getTierMask(), name);
                // addItemStackToInventory 吸收多少就 shrink 多少，剩余脚下落地，绝不吞卡
                target.inventory.addItemStackToInventory(issued);
                if (!issued.isEmpty()) {
                    EntityItem drop = new EntityItem(target.world,
                            target.posX, target.posY + 0.5D, target.posZ, issued);
                    target.world.spawnEntity(drop);
                }
                target.sendStatusMessage(new TextComponentString(TextFormatting.AQUA
                        + "[jiezhirbmkmod] " + TextFormatting.GREEN
                        + net.minecraft.util.text.translation.I18n.translateToLocalFormatted(
                                "msg.jiezhirbmkmod.issuer.received",
                                issuerName, pos.getX(), pos.getY(), pos.getZ())), false);
                ++ok;
                if (firstIssued.isEmpty()) {
                    firstIssued = name;
                }
            }
            StringBuilder summary = new StringBuilder();
            summary.append(TextFormatting.AQUA).append("[jiezhirbmkmod] ")
                    .append(TextFormatting.GREEN)
                    .append(net.minecraft.util.text.translation.I18n.translateToLocalFormatted(
                            "msg.jiezhirbmkmod.issuer.done", Integer.valueOf(ok)));
            if (noCard > 0 || offline > 0) {
                summary.append(' ').append(TextFormatting.YELLOW)
                        .append(net.minecraft.util.text.translation.I18n.translateToLocalFormatted(
                                "msg.jiezhirbmkmod.issuer.summary",
                                Integer.valueOf(noCard), Integer.valueOf(offline)));
            }
            operator.sendStatusMessage(new TextComponentString(summary.toString()), false);
            if (detail.length() > 0) {
                operator.sendStatusMessage(new TextComponentString(detail.toString().trim()), false);
            }
            // v17.7: 审计 —— 签发结果（含失败计数）落盘
            com.jiezhirbmkmod.dfc.AuditLog.log(issuerName, "ISSUE",
                    "issuer @ " + pos.getX() + "," + pos.getY() + "," + pos.getZ()
                            + " ok=" + ok + " noCard=" + noCard + " offline=" + offline
                            + " usesPerCard=" + issuer.getUsesPerCard()
                            + " tierMask=" + issuer.getTierMask());
            // v17.6: 结果回包 —— 把实际按当前配置产出的卡带回 GUI 展示，
            // 签发过后的卡"最后出现"在签发器界面里，而不是只在聊天栏报数
            net.minecraft.nbt.NBTTagCompound sampleTag = null;
            if (ok > 0) {
                ItemStack sample = ItemRbmkOpCard.makeIssued(byLine,
                        issuer.getUsesPerCard(), issuer.getTierMask(), firstIssued);
                sampleTag = sample.writeToNBT(new net.minecraft.nbt.NBTTagCompound());
            }
            NetGuard.sendTo(
                    new com.jiezhirbmkmod.network.msg.MessageIssuerResult(
                            ok, noCard, offline, firstIssued, sampleTag), operator);
        }

        private static EntityPlayerMP findPlayer(MinecraftServer server, String name) {
            EntityPlayer exact = server.getPlayerList().getPlayerByUsername(name);
            if (exact instanceof EntityPlayerMP) {
                return (EntityPlayerMP) exact;
            }
            // 大小写不敏感兜底（客户端 GUI 列表来自同一数据源，一般不会走到）
            for (EntityPlayerMP p : server.getPlayerList().getPlayers()) {
                if (p.getName().equalsIgnoreCase(name)) {
                    return p;
                }
            }
            return null;
        }
    }
}
