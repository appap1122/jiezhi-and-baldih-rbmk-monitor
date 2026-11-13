package com.jiezhirbmkmod.item;

import com.jiezhirbmkmod.item.CoilUtil;
import com.jiezhirbmkmod.item.ModItems;
import com.jiezhirbmkmod.item.ModSounds;
import java.util.UUID;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.SoundCategory;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public class SpeedCoilEffect {
    public static final float MULTIPLIER = 5.0f;
    private static final UUID MODIFIER_ID = UUID.fromString("7a1e5d2c-9b34-4f61-8c07-5d2ab3e6f901");
    private static final String MODIFIER_NAME = "jiezhirbmkmod.speed_coil";
    private static final int OP_MULTIPLY_TOTAL = 2;
    private static final double AMOUNT = 4.0;

    public static AttributeModifier newModifier() {
        return new AttributeModifier(MODIFIER_ID, MODIFIER_NAME, 4.0, 2).setSaved(false);
    }

    public static UUID modifierId() {
        return MODIFIER_ID;
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        boolean isActive;
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        EntityPlayer player = event.player;
        if (player == null) {
            return;
        }
        IAttributeInstance speed = player.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        boolean wantActive = CoilUtil.isHolding(player, ModItems.SPEED_COIL);
        boolean bl = isActive = speed.getModifier(MODIFIER_ID) != null;
        if (wantActive && !isActive) {
            speed.applyModifier(SpeedCoilEffect.newModifier());
            SpeedCoilEffect.playEquipSound(player);
        } else if (!wantActive && isActive) {
            speed.removeModifier(MODIFIER_ID);
        }
    }

    public static void playEquipSound(EntityPlayer player) {
        if (player == null || player.world == null || player.world.isRemote) {
            return;
        }
        player.world.playSound(null, player.posX, player.posY + (double)player.getEyeHeight(), player.posZ, ModSounds.COIL_EQUIP, SoundCategory.PLAYERS, 1.0f, 1.0f);
    }

    public static boolean isHoldingCoil(EntityPlayer player) {
        return CoilUtil.isHolding(player, ModItems.SPEED_COIL);
    }
}

