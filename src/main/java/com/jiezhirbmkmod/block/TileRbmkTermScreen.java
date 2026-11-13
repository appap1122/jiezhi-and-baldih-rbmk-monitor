package com.jiezhirbmkmod.block;

import com.jiezhirbmkmod.network.msg.MessageTerminalCommand;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;

/**
 * The 4x3 wall display variant of the terminal: same multiblock hardware as
 * the other RBMK displays (same family, bound with the same RBMK remote),
 * but the canvas page shows the command prompt: last output lines in the
 * classic green-on-black palette, readable from a distance in vanilla and
 * crisper with HD font packs.
 *
 * The buffer lives client side only: the terminal GUI (opened by right-
 * clicking the screen) mirrors every rendered line into this tile, the TESR
 * canvas then draws it.
 */
public class TileRbmkTermScreen extends TileRbmkMonitorScreen {

    private static final int MAX_TERM_LINES = 120;
    private final List<MessageTerminalCommand.CmdLine> termLines = new ArrayList<MessageTerminalCommand.CmdLine>();

    public void pushTermLine(MessageTerminalCommand.CmdLine line) {
        if (line == null) {
            return;
        }
        this.termLines.add(line);
        while (this.termLines.size() > MAX_TERM_LINES) {
            this.termLines.remove(0);
        }
    }

    public void clearTerm() {
        this.termLines.clear();
    }

    public List<MessageTerminalCommand.CmdLine> termSnapshot() {
        return new ArrayList<MessageTerminalCommand.CmdLine>(this.termLines);
    }

    @Override
    public void readFromNBT(NBTTagCompound c) {
        super.readFromNBT(c);
        this.termLines.clear();
    }
}
