package com.jiezhirbmkmod.item;

import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.registries.IForgeRegistryEntry;

@Mod.EventBusSubscriber(modid="jiezhirbmkmod")
public class ModSounds {
    public static final SoundEvent COIL_EQUIP = sound("coil_equip");
    public static final SoundEvent ALERT_WARN = sound("alert_warn");
    public static final SoundEvent ALERT_CRIT = sound("alert_crit");
    public static final SoundEvent SIREN_TWOTONE = sound("siren_twotone");
    public static final SoundEvent SIREN_YELP = sound("siren_yelp");
    public static final SoundEvent SIREN_AIRHORN = sound("siren_airhorn");

    private static final SoundEvent[] SIRENS = new SoundEvent[]{
            SIREN_TWOTONE, SIREN_YELP, SIREN_AIRHORN, ALERT_WARN, ALERT_CRIT};

    public static SoundEvent siren(int preset) {
        return SIRENS[com.jiezhirbmkmod.dfc.SirenPresets.wrap(preset)];
    }

    private static SoundEvent sound(String name) {
        return (SoundEvent) new SoundEvent(new ResourceLocation("jiezhirbmkmod", name)).setRegistryName("jiezhirbmkmod", name);
    }

    @SubscribeEvent
    public static void onRegisterSounds(RegistryEvent.Register<SoundEvent> event) {
        event.getRegistry().register(COIL_EQUIP);
        event.getRegistry().register(ALERT_WARN);
        event.getRegistry().register(ALERT_CRIT);
        event.getRegistry().register(SIREN_TWOTONE);
        event.getRegistry().register(SIREN_YELP);
        event.getRegistry().register(SIREN_AIRHORN);
    }
}
