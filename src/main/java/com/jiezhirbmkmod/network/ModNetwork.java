package com.jiezhirbmkmod.network;

import com.jiezhirbmkmod.network.msg.MessageRbmkCustomScreenAction;
import com.jiezhirbmkmod.network.msg.MessageRbmkJetExhaust;
import com.jiezhirbmkmod.network.msg.MessageRbmkMonitorScreen;
import com.jiezhirbmkmod.network.msg.MessageRbmkScreenPolicy;
import com.jiezhirbmkmod.network.msg.MessageSirenTune;
import com.jiezhirbmkmod.network.msg.MessageTerminalCommand;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

public class ModNetwork {
    public static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel("rbmk_monitor_network");

    public static void init() {
        int id = 0;
        CHANNEL.registerMessage(MessageRbmkMonitorScreen.Handler.class, MessageRbmkMonitorScreen.class, id++, Side.CLIENT);
        CHANNEL.registerMessage(MessageRbmkCustomScreenAction.Handler.class, MessageRbmkCustomScreenAction.class, id++, Side.SERVER);
        CHANNEL.registerMessage(MessageRbmkJetExhaust.Handler.class, MessageRbmkJetExhaust.class, id++, Side.CLIENT);
        CHANNEL.registerMessage(MessageRbmkScreenPolicy.Handler.class, MessageRbmkScreenPolicy.class, id++, Side.SERVER);
        // same class for both directions: client submits a command line, server replies with output lines
        CHANNEL.registerMessage(MessageTerminalCommand.Handler.class, MessageTerminalCommand.class, id++, Side.SERVER);
        CHANNEL.registerMessage(MessageTerminalCommand.Handler.class, MessageTerminalCommand.class, id++, Side.CLIENT);
        // one class, both directions: client submits the tuner form, server applies it and broadcasts the same form back
        CHANNEL.registerMessage(MessageSirenTune.Handler.class, MessageSirenTune.class, id++, Side.SERVER);
        CHANNEL.registerMessage(MessageSirenTune.Handler.class, MessageSirenTune.class, id++, Side.CLIENT);
    }
}

