package com.jiezhirbmkmod.item;

import com.jiezhirbmkmod.item.JetpackEffect;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

public class JetpackLogoutHandler {
    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player != null) {
            JetpackEffect.forget(event.player.getUniqueID());
        }
    }

    @SubscribeEvent
    public void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.player != null) {
            JetpackEffect.forget(event.player.getUniqueID());
        }
    }
}

