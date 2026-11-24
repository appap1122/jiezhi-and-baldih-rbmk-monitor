package com.jiezhirbmkmod.client;

import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

/** v17.1 PPT remote key bindings: H next, F prev, M goto. */
@SideOnly(Side.CLIENT)
public final class KeyBindings {

    public static final String CATEGORY = "key.categories.jiezhirbmkmod";
    public static final KeyBinding PPT_NEXT = new KeyBinding("key.rbmk_ppt.next", Keyboard.KEY_H, CATEGORY);
    public static final KeyBinding PPT_PREV = new KeyBinding("key.rbmk_ppt.prev", Keyboard.KEY_F, CATEGORY);
    public static final KeyBinding PPT_GOTO = new KeyBinding("key.rbmk_ppt.goto", Keyboard.KEY_M, CATEGORY);

    private KeyBindings() {
    }

    public static void register() {
        ClientRegistry.registerKeyBinding(PPT_NEXT);
        ClientRegistry.registerKeyBinding(PPT_PREV);
        ClientRegistry.registerKeyBinding(PPT_GOTO);
    }
}
