package com.jiezhirbmkmod.block;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * v17.1 master tile of the PPT screen. Extends the media screen tile so the
 * whole 4x3 multiblock / sync machinery is reused; only the slide counters and
 * the client-side slide texture cache are new.
 */
public class TileRbmkPptScreen
extends TileRbmkMediaScreen {

    public static final String NBT_SLIDE = "currentSlide";
    public static final String NBT_TOTAL = "totalSlides";
    public static final String NBT_PICTURE_MODE = "pictureMode";
    public static final String NBT_PICTURE_FILE = "pictureFile";

    private int currentSlide = 1;
    private int totalSlides = 1;
    private boolean pictureMode;
    private String pictureFile = "";

    // client-side slide texture cache, rebuilt only when the page changes
    @SideOnly(Side.CLIENT)
    private int pptTexture = -1;
    @SideOnly(Side.CLIENT)
    private int pptTextureSlide = -1;
    @SideOnly(Side.CLIENT)
    private Object pptDynamicTexture;

    public int getCurrentSlide() {
        return this.currentSlide;
    }

    public int getTotalSlides() {
        return this.totalSlides;
    }

    public void setTotalSlides(int total) {
        int t = Math.max(1, Math.min(999, total));
        boolean changed = t != this.totalSlides;
        this.totalSlides = t;
        if (this.currentSlide > t) {
            this.currentSlide = t;
            changed = true;
        }
        if (changed) {
            this.syncPpt();
        }
    }

    public void setSlide(int slide) {
        int t = Math.max(1, this.totalSlides);
        int s = Math.max(1, Math.min(t, slide));
        if (s == this.currentSlide) {
            this.syncPpt();
            return;
        }
        this.currentSlide = s;
        this.syncPpt();
    }

    public void nextSlide() {
        this.setSlide(this.currentSlide + 1);
    }

    public void prevSlide() {
        this.setSlide(this.currentSlide - 1);
    }

    // ---- v17.1 picture viewer -------------------------------------------

    public boolean isPictureMode() {
        return this.pictureMode;
    }

    public String getPictureFile() {
        return this.pictureFile == null ? "" : this.pictureFile;
    }

    /** show one image from rbmk_media/pictures (empty name = black screen) */
    public void setPicture(String file) {
        this.pictureMode = true;
        this.pictureFile = file == null ? "" : file;
        this.syncPpt();
    }

    public void clearPicture() {
        this.pictureMode = true;
        this.pictureFile = "";
        this.syncPpt();
    }

    /** leave picture mode and go back to the slide deck */
    public void showSlides() {
        this.pictureMode = false;
        this.pictureFile = "";
        this.syncPpt();
    }

    private void syncPpt() {
        this.markDirty();
        if (this.world != null && !this.world.isRemote) {
            this.world.notifyBlockUpdate(this.pos, this.world.getBlockState(this.pos),
                    this.world.getBlockState(this.pos), 3);
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound c) {
        super.readFromNBT(c);
        this.currentSlide = c.hasKey(NBT_SLIDE) ? c.getInteger(NBT_SLIDE) : 1;
        this.totalSlides = c.hasKey(NBT_TOTAL) ? c.getInteger(NBT_TOTAL) : 1;
        this.totalSlides = Math.max(1, Math.min(999, this.totalSlides));
        this.currentSlide = Math.max(1, Math.min(this.totalSlides, this.currentSlide));
        this.pictureMode = c.hasKey(NBT_PICTURE_MODE) ? c.getBoolean(NBT_PICTURE_MODE) : false;
        this.pictureFile = c.hasKey(NBT_PICTURE_FILE) ? c.getString(NBT_PICTURE_FILE) : "";
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound c) {
        c = super.writeToNBT(c);
        c.setInteger(NBT_SLIDE, this.currentSlide);
        c.setInteger(NBT_TOTAL, this.totalSlides);
        c.setBoolean(NBT_PICTURE_MODE, this.pictureMode);
        c.setString(NBT_PICTURE_FILE, this.pictureFile == null ? "" : this.pictureFile);
        return c;
    }

    // ---- client slide texture cache --------------------------------------

    @SideOnly(Side.CLIENT)
    public int getPptTextureId() {
        return this.pptTexture;
    }

    @SideOnly(Side.CLIENT)
    public int getPptTextureSlide() {
        return this.pptTextureSlide;
    }

    @SideOnly(Side.CLIENT)
    public Object getPptDynamicTexture() {
        return this.pptDynamicTexture;
    }

    @SideOnly(Side.CLIENT)
    public void setPptTexture(int id, int slide, Object dynamicTexture) {
        this.pptTexture = id;
        this.pptTextureSlide = slide;
        this.pptDynamicTexture = dynamicTexture;
    }

    @SideOnly(Side.CLIENT)
    public void invalidatePptTexture() {
        // keep the GL id: the renderer deletes it when it rebuilds the texture
        this.pptTextureSlide = -2;
        this.pptDynamicTexture = null;
    }

    /** .minecraft/rbmk_media/ppt (client side only) */
    @SideOnly(Side.CLIENT)
    public static java.io.File slidesDir() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
        java.io.File gameDir = mc == null || mc.gameDir == null ? new java.io.File(".") : mc.gameDir;
        return new java.io.File(gameDir, "rbmk_media" + java.io.File.separator + "ppt");
    }

    /** create the slide folder when missing; true when it had to be created */
    @SideOnly(Side.CLIENT)
    public static boolean ensureSlidesDir() {
        java.io.File dir = TileRbmkPptScreen.slidesDir();
        if (dir.isDirectory()) {
            return false;
        }
        try {
            return dir.mkdirs();
        }
        catch (Throwable ignored) {
            return false;
        }
    }

    /** number of slide_X.png files currently in the folder */
    @SideOnly(Side.CLIENT)
    public static int countSlides() {
        int count = 0;
        java.io.File[] files = TileRbmkPptScreen.slidesDir().listFiles();
        if (files == null) {
            return 0;
        }
        for (int i = 0; i < files.length; ++i) {
            java.io.File f = files[i];
            if (f != null && f.isFile()
                    && f.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".png")) {
                ++count;
            }
        }
        return count;
    }

    // ---- client picture texture cache ------------------------------------

    @SideOnly(Side.CLIENT)
    private int picTexture = -1;
    @SideOnly(Side.CLIENT)
    private String picTextureName = "";
    @SideOnly(Side.CLIENT)
    private Object picDynamicTexture;
    @SideOnly(Side.CLIENT)
    private float picAspect = 1.0F;

    @SideOnly(Side.CLIENT)
    public int getPictureTextureId() {
        return this.picTexture;
    }

    @SideOnly(Side.CLIENT)
    public String getPictureTextureName() {
        return this.picTextureName;
    }

    @SideOnly(Side.CLIENT)
    public float getPictureAspect() {
        return this.picAspect;
    }

    @SideOnly(Side.CLIENT)
    public Object getPictureDynamicTexture() {
        return this.picDynamicTexture;
    }

    @SideOnly(Side.CLIENT)
    public void setPictureTexture(int id, String name, Object dynamicTexture, float aspect) {
        this.picTexture = id;
        this.picTextureName = name == null ? "" : name;
        this.picDynamicTexture = dynamicTexture;
        this.picAspect = aspect <= 0.0F ? 1.0F : aspect;
    }

    @SideOnly(Side.CLIENT)
    public void invalidatePictureTexture() {
        // keep the GL id: the renderer deletes it when it rebuilds the texture
        this.picTextureName = "";
        this.picDynamicTexture = null;
    }

    // ---- .minecraft/rbmk_media/pictures ----------------------------------

    @SideOnly(Side.CLIENT)
    public static java.io.File picturesDir() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
        java.io.File gameDir = mc == null || mc.gameDir == null ? new java.io.File(".") : mc.gameDir;
        return new java.io.File(gameDir, "rbmk_media" + java.io.File.separator + "pictures");
    }

    /** auto-create the pictures folder on first use; true when created now */
    @SideOnly(Side.CLIENT)
    public static boolean ensurePicturesDir() {
        java.io.File dir = TileRbmkPptScreen.picturesDir();
        if (dir.isDirectory()) {
            return false;
        }
        try {
            return dir.mkdirs();
        }
        catch (Throwable ignored) {
            return false;
        }
    }

    /** sorted .png/.jpg/.jpeg names from the pictures folder */
    @SideOnly(Side.CLIENT)
    public static java.util.List<String> listPictures() {
        java.util.List<String> out = new java.util.ArrayList<String>();
        java.io.File[] files = TileRbmkPptScreen.picturesDir().listFiles();
        if (files == null) {
            return out;
        }
        for (int i = 0; i < files.length; ++i) {
            java.io.File f = files[i];
            if (f == null || !f.isFile()) {
                continue;
            }
            String name = f.getName().toLowerCase(java.util.Locale.ROOT);
            if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")) {
                out.add(f.getName());
            }
        }
        java.util.Collections.sort(out);
        return out;
    }

    @Override
    public void invalidate() {
        if (this.world != null && this.world.isRemote) {
            this.releaseTextures();
        }
        super.invalidate();
    }

    @SideOnly(Side.CLIENT)
    private void releaseTextures() {
        try {
            if (this.pptTexture > 0) {
                org.lwjgl.opengl.GL11.glDeleteTextures(this.pptTexture);
                this.pptTexture = -1;
            }
            if (this.picTexture > 0) {
                org.lwjgl.opengl.GL11.glDeleteTextures(this.picTexture);
                this.picTexture = -1;
            }
        }
        catch (Throwable ignored) {
        }
        this.pptDynamicTexture = null;
        this.picDynamicTexture = null;
    }

    /** nearest PPT master around a point, used by the terminal and the remote */
    public static TileRbmkPptScreen findNear(World world, BlockPos center, int radius) {
        if (world == null || center == null || radius <= 0) {
            return null;
        }
        double maxSq = (double) radius * (double) radius;
        TileRbmkPptScreen best = null;
        double bestSq = maxSq + 1.0D;
        for (int cx = (center.getX() - radius) >> 4; cx <= (center.getX() + radius) >> 4; ++cx) {
            for (int cz = (center.getZ() - radius) >> 4; cz <= (center.getZ() + radius) >> 4; ++cz) {
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
                for (net.minecraft.tileentity.TileEntity te : chunk.getTileEntityMap().values()) {
                    if (!(te instanceof TileRbmkPptScreen)) {
                        continue;
                    }
                    BlockPos p = te.getPos();
                    double d = p.distanceSq(center);
                    if (d <= maxSq && d < bestSq) {
                        bestSq = d;
                        best = (TileRbmkPptScreen) te;
                    }
                }
            }
        }
        return best;
    }
}
