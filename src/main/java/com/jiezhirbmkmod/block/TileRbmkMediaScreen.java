package com.jiezhirbmkmod.block;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Master tile of the 4x3 media screen. Holds the playback state in NBT and
 * (client side only) owns the decoder thread through the client media bridge.
 */
public class TileRbmkMediaScreen
extends TileRbmkMonitorScreen {

    private boolean mediaPlaying;
    private String mediaFile = "";
    private int mediaVolume = 80;
    private long mediaProgressMs;
    private String mediaError = "";

    public boolean isMediaPlaying() {
        return this.mediaPlaying;
    }

    public String getMediaFile() {
        return this.mediaFile == null ? "" : this.mediaFile;
    }

    public int getMediaVolume() {
        return this.mediaVolume;
    }

    public long getMediaProgressMs() {
        return this.mediaProgressMs;
    }

    public String getMediaError() {
        return this.mediaError == null ? "" : this.mediaError;
    }

    /** server side: update the playback state and push it to the watching clients */
    public void setMediaState(boolean playing, String file, int volume, String error) {
        this.mediaPlaying = playing;
        this.mediaFile = file == null ? "" : file;
        this.mediaVolume = Math.max(0, Math.min(100, volume));
        this.mediaError = error == null ? "" : error;
        if (!playing) {
            this.mediaProgressMs = 0L;
        }
        this.markDirty();
        if (this.world != null && !this.world.isRemote) {
            this.world.notifyBlockUpdate(this.pos, this.world.getBlockState(this.pos),
                    this.world.getBlockState(this.pos), 3);
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound c) {
        super.readFromNBT(c);
        this.mediaPlaying = c.getBoolean("MediaPlaying");
        this.mediaFile = c.hasKey("MediaFile") ? c.getString("MediaFile") : "";
        this.mediaVolume = c.hasKey("MediaVolume") ? c.getInteger("MediaVolume") : 80;
        this.mediaProgressMs = c.getLong("MediaProgress");
        this.mediaError = c.hasKey("MediaError") ? c.getString("MediaError") : "";
        if (this.world != null && this.world.isRemote) {
            this.clientSync();
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound c) {
        c = super.writeToNBT(c);
        c.setBoolean("MediaPlaying", this.mediaPlaying);
        c.setString("MediaFile", this.mediaFile == null ? "" : this.mediaFile);
        c.setInteger("MediaVolume", this.mediaVolume);
        c.setLong("MediaProgress", this.mediaProgressMs);
        c.setString("MediaError", this.mediaError == null ? "" : this.mediaError);
        return c;
    }

    @Override
    public void update() {
        super.update();
        if (this.world != null && this.world.isRemote) {
            this.clientTick();
        }
    }

    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(this.pos, 3, this.getUpdateTag());
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        if (pkt != null && pkt.getNbtCompound() != null) {
            this.readFromNBT(pkt.getNbtCompound());
        }
    }

    @Override
    public void invalidate() {
        if (this.world != null && this.world.isRemote) {
            this.clientStop();
        }
        super.invalidate();
    }

    @SideOnly(Side.CLIENT)
    private void clientTick() {
        com.jiezhirbmkmod.client.media.MediaClient.tick(this);
    }

    @SideOnly(Side.CLIENT)
    private void clientSync() {
        com.jiezhirbmkmod.client.media.MediaClient.sync(this);
    }

    @SideOnly(Side.CLIENT)
    private void clientStop() {
        com.jiezhirbmkmod.client.media.MediaClient.forget(this);
    }

    @SideOnly(Side.CLIENT)
    public java.awt.image.BufferedImage pollMediaFrame() {
        return com.jiezhirbmkmod.client.media.MediaClient.pollFrame(this);
    }

    @SideOnly(Side.CLIENT)
    public boolean hasMediaError() {
        return com.jiezhirbmkmod.client.media.MediaClient.hasError(this);
    }

    @SideOnly(Side.CLIENT)
    public int getMediaTexture() {
        return com.jiezhirbmkmod.client.media.MediaClient.getTexture(this);
    }

    @SideOnly(Side.CLIENT)
    public void setMediaTexture(int tex) {
        com.jiezhirbmkmod.client.media.MediaClient.setTexture(this, tex);
    }

    /** nearest media master around a point, used by the terminal commands */
    public static TileRbmkMediaScreen findNear(World world, BlockPos center, int radius) {
        if (world == null || center == null || radius <= 0) {
            return null;
        }
        int minX = center.getX() - radius;
        int maxX = center.getX() + radius;
        int minZ = center.getZ() - radius;
        int maxZ = center.getZ() + radius;
        int minY = center.getY() - radius;
        int maxY = center.getY() + radius;
        double maxSq = (double) radius * (double) radius;
        TileRbmkMediaScreen best = null;
        double bestSq = maxSq + 1.0D;
        for (int cx = minX >> 4; cx <= maxX >> 4; ++cx) {
            for (int cz = minZ >> 4; cz <= maxZ >> 4; ++cz) {
                net.minecraft.world.chunk.Chunk chunk;
                try {
                    chunk = world.getChunkProvider().getLoadedChunk(cx, cz);
                }
                catch (Throwable ignored) {
                    continue;
                }
                if (chunk == null) {
                    continue;
                }
                for (TileEntity te : chunk.getTileEntityMap().values()) {
                    if (!(te instanceof TileRbmkMediaScreen)) {
                        continue;
                    }
                    BlockPos p = te.getPos();
                    if (p.getX() < minX || p.getX() > maxX || p.getZ() < minZ || p.getZ() > maxZ
                            || p.getY() < minY || p.getY() > maxY) {
                        continue;
                    }
                    double d = p.distanceSq(center);
                    if (d <= maxSq && d < bestSq) {
                        bestSq = d;
                        best = (TileRbmkMediaScreen) te;
                    }
                }
            }
        }
        return best;
    }
}
