package com.jiezhirbmkmod.client.gui;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Multimedia dependency guide. Shown once when the player joins a world and the
 * optional JavaCV decoder is missing. Also re-openable from the terminal with
 * "media install".
 */
@SideOnly(Side.CLIENT)
public class GuiMediaInstallPrompt
extends GuiScreen {

    /** edit this to your own Gitee release page */
    public static final String DOWNLOAD_URL = "https://gitee.com/jiezhirbmkmod/RBMK-Monitor/releases";

    private static final String TITLE = "[SYSTEM] 多媒体解码库缺失警告";
    private static final int PANEL_MAX_H = 300;
    private static final int PANEL_MIN_H = 210;
    private static final String WARNING =
            "[警告] 当前多媒体解码库仅支持 Windows 64 位系统。Mac/Linux 玩家请勿下载，否则可能导致游戏崩溃。";
    private static final String[] BODY = new String[]{
            "当前设备未安装 JavaCV 多媒体解码库。",
            "【安装方法】请将 RBMK-Media-Deps-Windows.zip 解压后的所有 .jar 文件放入 "
                    + ".minecraft/mods/ 文件夹（与 RBMK 模组放在一起），重启游戏即可生效。",
            "【不安装的后果】RBMK 监视器的多媒体屏幕将无法播放 MP4 视频和 MP3 音频，"
                    + "屏幕仅显示 [UNSUPPORTED CODEC]。其余红石、中控、黑客功能不受影响。",
            "【媒体文件位置】MP4 / MP3 文件请放入 .minecraft/rbmk_media/ 文件夹。",
            "【以后的安装方法】随时在游戏中右键终端屏幕，输入指令 \"media install\" 即可再次调出此页面。"
    };

    private static final String CFG_NAME = "jiezhirbmkmod_media.properties";
    private static final String KEY_HIDE = "hideInstallPrompt";
    /** cached reflection result: Class.forName must never run per frame */
    private static Boolean decoderCache;
    private boolean decoderAvailable;

    /** reflectively probe the optional decoder, never hard-link against it */
    public static boolean isDecoderAvailable() {
        Boolean cached = decoderCache;
        if (cached == null) {
            boolean ok;
            try {
                ok = com.jiezhirbmkmod.client.media.MediaPlayerThread.isDecoderPresent();
            }
            catch (Throwable t) {
                try {
                    Class.forName("org.bytedeco.javacv.FFmpegFrameGrabber");
                    ok = true;
                }
                catch (Throwable ignored) {
                    ok = false;
                }
            }
            cached = Boolean.valueOf(ok);
            decoderCache = cached;
        }
        return cached.booleanValue();
    }

    private static File configFile() {
        return new File(net.minecraft.client.Minecraft.getMinecraft().gameDir,
                "config" + File.separator + CFG_NAME);
    }

    public static boolean isPromptHidden() {
        File file = GuiMediaInstallPrompt.configFile();
        if (!file.isFile()) {
            return false;
        }
        FileInputStream in = null;
        try {
            in = new FileInputStream(file);
            Properties props = new Properties();
            props.load(in);
            return Boolean.parseBoolean(props.getProperty(KEY_HIDE, "false"));
        }
        catch (Throwable ignored) {
            return false;
        }
        finally {
            if (in != null) {
                try {
                    in.close();
                }
                catch (IOException ignored) {
                }
            }
        }
    }

    private static void setPromptHidden() {
        File file = GuiMediaInstallPrompt.configFile();
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory()) {
            parent.mkdirs();
        }
        FileOutputStream out = null;
        try {
            Properties props = new Properties();
            props.setProperty(KEY_HIDE, "true");
            out = new FileOutputStream(file);
            props.store(out, "RBMK-Monitor multimedia prompt");
        }
        catch (Throwable ignored) {
        }
        finally {
            if (out != null) {
                try {
                    out.close();
                }
                catch (IOException ignored) {
                }
            }
        }
    }

    public static boolean shouldPrompt() {
        return !GuiMediaInstallPrompt.isDecoderAvailable() && !GuiMediaInstallPrompt.isPromptHidden();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private int panelWidth() {
        return Math.min(460, Math.max(220, this.width - 40));
    }

    private int panelX() {
        return (this.width - this.panelWidth()) / 2;
    }

    private int panelY() {
        return Math.max(2, (this.height - this.panelHeight()) / 2);
    }

    private int panelHeight() {
        return Math.min(PANEL_MAX_H, Math.max(PANEL_MIN_H, this.height - 20));
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        // resolve the (cached) probe once per screen open, never in drawScreen
        this.decoderAvailable = GuiMediaInstallPrompt.isDecoderAvailable();
        int x = this.panelX();
        int y = this.panelY() + this.panelHeight() - 30;
        // three buttons always stay inside the panel, even on tiny windows
        int inner = Math.max(90, this.panelWidth() - 24);
        int gap = 6;
        int bw = Math.max(40, (inner - gap * 2) / 3);
        this.buttonList.add(new GuiButton(0, x + 12, y, bw, 20, "立即前往 Gitee 下载"));
        this.buttonList.add(new GuiButton(1, x + 12 + bw + gap, y, bw, 20, "暂不安装，以后再说"));
        this.buttonList.add(new GuiButton(2, x + 12 + (bw + gap) * 2, y, bw, 20, "不再提示"));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        int x = this.panelX();
        int y = this.panelY();
        int w = this.panelWidth();
        int h = this.panelHeight();
        drawRect(x, y, x + w, y + h, 0xF0000000);
        drawRect(x, y, x + w, y + 2, 0xFF39FF14);
        drawRect(x, y + h - 2, x + w, y + h, 0xFF39FF14);
        drawRect(x, y, x + 2, y + h, 0xFF1E7A1E);
        drawRect(x + w - 2, y, x + w, y + h, 0xFF1E7A1E);

        int tx = x + 12;
        int ty = y + 10;
        this.fontRenderer.drawString(TITLE, tx, ty, 0xFF39FF14);
        ty += 16;
        // intro line
        ty = this.drawWrapped(BODY[0], tx, ty, w - 24, y + h - 44, 0xFF8BC34A);
        ty += 2;
        // platform warning, always drawn in yellow before the details
        ty = this.drawWrapped(WARNING, tx, ty, w - 24, y + h - 44, 0xFFFFD700);
        ty += 4;
        for (int i = 1; i < BODY.length; ++i) {
            ty = this.drawWrapped(BODY[i], tx, ty, w - 24, y + h - 44, 0xFF669933);
            ty += 4;
        }
        String status = this.decoderAvailable ? "JavaCV: OK" : "JavaCV core class not found";
        this.fontRenderer.drawString(status, tx, y + h - 18, 0xFFE74856);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    /** draw a wrapped block, clipped so it can never overlap the buttons */
    private int drawWrapped(String text, int x, int y, int width, int bottom, int color) {
        List<String> lines = this.wrap(text, width);
        for (int i = 0; i < lines.size() && y < bottom; ++i) {
            this.fontRenderer.drawString(lines.get(i), x, y, color);
            y += 10;
        }
        return y;
    }

    private List<String> wrap(String text, int width) {
        List<String> out = new ArrayList<String>();
        if (text == null) {
            return out;
        }
        List<?> formatted = this.fontRenderer.listFormattedStringToWidth(text, width);
        for (int i = 0; i < formatted.size(); ++i) {
            out.add(String.valueOf(formatted.get(i)));
        }
        return out;
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button == null) {
            return;
        }
        if (button.id == 0) {
            GuiMediaInstallPrompt.openUrl(DOWNLOAD_URL);
            this.mc.displayGuiScreen(null);
        } else if (button.id == 1) {
            this.mc.displayGuiScreen(null);
        } else if (button.id == 2) {
            GuiMediaInstallPrompt.setPromptHidden();
            this.mc.displayGuiScreen(null);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == 1) {
            this.mc.displayGuiScreen(null);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    /** opens the download page, tolerating every Minecraft/OS variant */
    private static void openUrl(String url) {
        try {
            URL target = new URL(url);
            Class<?> util = Class.forName("net.minecraft.util.Util");
            Object os;
            try {
                os = util.getMethod("getOperatingSystem").invoke(null);
            }
            catch (NoSuchMethodException e) {
                os = util.getMethod("getOSType").invoke(null);
            }
            try {
                os.getClass().getMethod("open", URL.class).invoke(os, target);
            }
            catch (NoSuchMethodException e) {
                os.getClass().getMethod("openURL", URL.class).invoke(os, target);
            }
        }
        catch (Throwable t) {
            System.err.println("[RBMK-Monitor] could not open the download page: " + t);
            GuiMediaInstallPrompt.notifyOpenFailed();
        }
    }

    /** tell the player in chat that the browser could not be launched */
    private static void notifyOpenFailed() {
        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
            if (mc != null && mc.player != null) {
                mc.player.sendMessage(new net.minecraft.util.text.TextComponentString(
                        net.minecraft.util.text.TextFormatting.RED
                        + "[RBMK] 无法自动打开浏览器，请手动访问：" + DOWNLOAD_URL));
            }
        }
        catch (Throwable ignored) {
        }
    }
}
