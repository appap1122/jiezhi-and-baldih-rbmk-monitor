package com.jiezhirbmkmod.block;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;

/**
 * TileEntity of the floor-standing RBMK terminal block.
 *
 * Server side it only stores the bound console position (bound either by
 * right-clicking the block with the RBMK remote, or with the 'bind' command
 * inside the terminal GUI). Client side nothing is rendered on the block
 * itself, the content lives in the GUI that opens on right-click.
 */
public class TileRbmkTerminal extends TileEntity {

    private BlockPos consolePos;

    public BlockPos getConsolePos() {
        return this.consolePos;
    }

    public void bindConsole(BlockPos console) {
        this.consolePos = console == null ? null : console.toImmutable();
        this.markDirty();
    }

    public void clearBinding() {
        this.consolePos = null;
        this.markDirty();
    }

    @Override
    public void readFromNBT(NBTTagCompound c) {
        super.readFromNBT(c);
        if (c.hasKey("ConsoleX")) {
            this.consolePos = new BlockPos(c.getInteger("ConsoleX"), c.getInteger("ConsoleY"), c.getInteger("ConsoleZ"));
        } else {
            this.consolePos = null;
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound c) {
        super.writeToNBT(c);
        if (this.consolePos != null) {
            c.setInteger("ConsoleX", this.consolePos.getX());
            c.setInteger("ConsoleY", this.consolePos.getY());
            c.setInteger("ConsoleZ", this.consolePos.getZ());
        }
        return c;
    }
}
