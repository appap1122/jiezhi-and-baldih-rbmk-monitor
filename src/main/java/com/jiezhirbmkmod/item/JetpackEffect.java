package com.jiezhirbmkmod.item;

import com.jiezhirbmkmod.client.ClientJetState;
import com.jiezhirbmkmod.item.JetHeat;
import com.jiezhirbmkmod.item.JetPhysics;
import com.jiezhirbmkmod.item.ModItems;
import com.jiezhirbmkmod.network.ModNetwork;
import com.jiezhirbmkmod.network.msg.MessageRbmkJetExhaust;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovementInput;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.relauncher.Side;

public class JetpackEffect {
    private static final Map<UUID, JetHeat> HEAT_MAP = new HashMap<UUID, JetHeat>();
    private static final Map<UUID, Boolean> FIRING_PREV = new HashMap<UUID, Boolean>();

    public static JetHeat heatOf(EntityPlayer player) {
        UUID id = player.getUniqueID();
        JetHeat h = HEAT_MAP.get(id);
        if (h == null) {
            h = new JetHeat();
            HEAT_MAP.put(id, h);
        }
        return h;
    }

    public static JetHeat heatOf(UUID id) {
        JetHeat h = HEAT_MAP.get(id);
        if (h == null) {
            h = new JetHeat();
            HEAT_MAP.put(id, h);
        }
        return h;
    }

    public static void forget(UUID id) {
        HEAT_MAP.remove(id);
        FIRING_PREV.remove(id);
    }

    public static void clearAll() {
        HEAT_MAP.clear();
        FIRING_PREV.clear();
    }

    public static boolean hasJetpack(EntityPlayer player) {
        ItemStack chest = player.getItemStackFromSlot(EntityEquipmentSlot.CHEST);
        return chest != null && !chest.isEmpty() && chest.getItem() == ModItems.JETPACK;
    }

    public static boolean hasSwitch(EntityPlayer player) {
        ItemStack main = player.getHeldItemMainhand();
        ItemStack off = player.getHeldItemOffhand();
        return JetpackEffect.isSwitch(main) || JetpackEffect.isSwitch(off);
    }

    public static boolean isSwitch(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() == ModItems.JET_SWITCH;
    }

    public static boolean wantsFire(EntityPlayer player) {
        if (!JetpackEffect.hasJetpack(player) || !JetpackEffect.hasSwitch(player)) {
            return false;
        }
        return JetpackEffect.isUsingSwitch(player);
    }

    private static boolean isUsingSwitch(EntityPlayer player) {
        return player.isHandActive() && player.getActiveItemStack() != ItemStack.EMPTY && JetpackEffect.isSwitch(player.getActiveItemStack());
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.side != Side.SERVER) {
            return;
        }
        EntityPlayer player = event.player;
        if (player == null || player.world == null) {
            return;
        }
        JetpackEffect.stepPlayer(player);
    }

    public static void stepPlayer(EntityPlayer player) {
        boolean prev;
        boolean want;
        World w = player.world;
        if (w == null) {
            return;
        }
        JetHeat heat = JetpackEffect.heatOf(player);
        JetHeat.State st = heat.tick(want = JetpackEffect.wantsFire(player));
        boolean firing = st == JetHeat.State.FIRING;
        if (firing != (prev = Boolean.TRUE.equals(FIRING_PREV.get(player.getUniqueID())))) {
            FIRING_PREV.put(player.getUniqueID(), firing);
            JetpackEffect.sendJetState(player, firing, heat.getHeatRatio());
        } else if (firing) {
            JetpackEffect.sendJetState(player, true, heat.getHeatRatio());
        }
        if (!firing) {
            return;
        }
        boolean rising = w.isRemote && JetpackEffect.isClientRising(player);
        double up = rising ? 1.0 : 0.0;
        double[] cur = new double[]{player.motionX, player.motionY, player.motionZ};
        double[] next = JetPhysics.step(cur, player.rotationYaw, 1.0, 0.0, up, rising);
        player.motionX = next[0];
        player.motionY = next[1];
        player.motionZ = next[2];
        player.fallDistance = 0.0f;
    }

    private static void sendJetState(EntityPlayer player, boolean firing, float heatRatio) {
        if (!(player instanceof EntityPlayerMP)) {
            return;
        }
        ModNetwork.CHANNEL.sendTo((IMessage)new MessageRbmkJetExhaust(player, firing, heatRatio), (EntityPlayerMP)player);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.PlayerTickEvent event) {
        MovementInput in;
        if (event.phase != TickEvent.Phase.END || event.side != Side.CLIENT) {
            return;
        }
        EntityPlayer player = event.player;
        if (player == null || player.world == null) {
            return;
        }
        if (player != Minecraft.getMinecraft().player) {
            return;
        }
        Boolean firing = ClientJetState.isFiring(player);
        if (firing == null || !firing.booleanValue()) {
            return;
        }
        boolean rising = JetpackEffect.isClientRising(player);
        double forward = 0.0;
        double strafe = 0.0;
        if (player instanceof EntityPlayerSP && (in = ((EntityPlayerSP)player).movementInput) != null) {
            forward = in.moveForward;
            strafe = in.moveStrafe;
        }
        double up = rising ? 1.0 : 0.0;
        double[] cur = new double[]{player.motionX, player.motionY, player.motionZ};
        double[] next = JetPhysics.step(cur, player.rotationYaw, forward, strafe, up, rising);
        player.motionX = next[0];
        player.motionY = next[1];
        player.motionZ = next[2];
        player.fallDistance = 0.0f;
    }

    private static boolean isClientRising(EntityPlayer player) {
        if (!(player instanceof EntityPlayerSP)) {
            return false;
        }
        MovementInput in = ((EntityPlayerSP)player).movementInput;
        return in != null && in.jump;
    }

    public static Vec3d exhaustPos(EntityPlayer player) {
        EnumFacing back = player.getHorizontalFacing().getOpposite();
        double bx = back.getXOffset();
        double bz = back.getZOffset();
        return new Vec3d(player.posX - bx * 0.3, player.posY + 0.15, player.posZ - bz * 0.3);
    }
}

