package com.jiezhirbmkmod.client.gui;


import com.jiezhirbmkmod.network.NetGuard;
import com.jiezhirbmkmod.alarm.AlarmConfig;
import com.jiezhirbmkmod.block.TileAlarmLight;
import com.jiezhirbmkmod.dfc.RbmkConfig;
import com.jiezhirbmkmod.network.ModNetwork;
import com.jiezhirbmkmod.network.msg.MessageAlarmLight;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 报警灯控制面板（潜行右键灯打开）。
 *
 * <h3>为什么是"一个面板"而不是"一个颜色编辑器"</h3>
 * 这盏灯有五个互相独立的旋钮，而且都需要"看着灯调"：
 * <table summary="">
 *   <tr><td>颜色</td><td>RGB 三滑条 —— 直接甩一桶染料只有 16 种颜色</td></tr>
 *   <tr><td>转速</td><td>0~120 rpm，0 = 只亮不转</td></tr>
 *   <tr><td>告警联动</td><td>开 = 颜色 / 转速 / 开关全交给显示屏的告警等级</td></tr>
 *   <tr><td>光锥实现</td><td>几何锥体 ⇄ 屏幕空间聚光灯，<b>点了立刻换，不用重启</b></td></tr>
 *   <tr><td>触发方式</td><td>右键 / 潜行右键 / 红石 / 任意 —— 决定"哪个手势算开关"</td></tr>
 * </table>
 *
 * <h3>联动中为什么要置灰</h3>
 * 联动中的灯每 10 刻会被 {@code AlertRules} 重写一遍颜色 / 转速 / 开关 ——
 * 面板上让玩家拖得动、拖完三秒又被改回去，比直接置灰更让人困惑。
 * 所以联动期间只有"光锥实现"还能动（它跟告警等级无关），其余几组连带说明文字一起变灰。
 * 触发方式也一起锁：联动中开关由告警等级决定，这时候改触发方式看不出任何效果，
 * 只会让人以为"点了没反应"。它的值仍然会存下来，解除联动后接着用。
 *
 * <h3>为什么点击后要锁 10 tick</h3>
 * 服务端改完要发 {@code SPacketUpdateTileEntity} 回来，客户端 TE 才会更新。
 * 不锁的话按钮文案会先闪回旧值再跳成新值；锁住期间用本地字段撑着，包一到就对上了。
 */
@SideOnly(Side.CLIENT)
public class GuiAlarmLightPanel
extends GuiScreen {

    private static final int W = 220;
    /** 从上到下：标题 4 / 副标题 18 / 色块 34-52 / hex 53 / RGB 64·84·104 / 转速 126
     *  / 联动+灯 148 / 触发 172 / 四档光锥 194-214 / 说明 218 / 导出+导入 238 / 应用+取消 266 */
    private static final int H = 288;

    /**
     * 四个光锥档位，顺序就是按钮从左到右的顺序。
     * <p>注意按钮 id 与模式值不是一一对应的：id 11 是 FBO，而
     * {@code SPOT_SSS} 的值是 2 —— 按玩家看到的顺序排，别按常量值排。</p>
     */
    private static final int[] SPOT_ORDER = new int[]{
        TileAlarmLight.SPOT_AUTO,
        TileAlarmLight.SPOT_SSS,
        TileAlarmLight.SPOT_GEOMETRY,
        TileAlarmLight.SPOT_TEXTURE
    };

    /** 四档按钮的 id，与 {@link #SPOT_ORDER} 同序 */
    private static final int[] SPOT_BUTTONS = new int[]{10, 11, 12, 13};

    /** 四档按钮的文案键，与 {@link #SPOT_ORDER} 同序 */
    private static final String[] SPOT_KEYS = new String[]{
        "gui.jiezhirbmkmod.alarm_light.spot_auto",
        "gui.jiezhirbmkmod.alarm_light.spot_sss",
        "gui.jiezhirbmkmod.alarm_light.spot_geo",
        "gui.jiezhirbmkmod.alarm_light.spot_tex"
    };

    private final TileAlarmLight tile;

    private Slider sliderR;
    private Slider sliderG;
    private Slider sliderB;
    private Slider sliderRpm;

    // 本地状态：服务端包回来之前的"乐观值"
    private boolean localOn;
    private boolean localAuto;
    private int localSpot;
    private int localTrigger;
    private boolean localCfgValid;

    // ---------- "取消"用的打开面板快照（v17.9） ----------
    // 面板上的每次点击都是当场发包生效的，等玩家按"取消"时服务端已经
    // 改了一路 —— 所以打开面板那一刻把整套状态拍下来，取消时按快照发回去。
    private int snapColor;
    private int snapRpm;
    private boolean snapOn;
    private int snapSpot;
    private int snapTrigger;
    private boolean snapAuto;
    private boolean snapValid;

    /** 刚发过包，先别用 tile 的旧值覆盖本地值 */
    private int lockTicks;

    public GuiAlarmLightPanel(TileAlarmLight tile) {
        this.tile = tile;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private int left() {
        return this.width / 2 - W / 2;
    }

    private int top() {
        return Math.max(4, this.height / 2 - H / 2);
    }

    private int currentColor() {
        return (this.sliderR.value << 16) | (this.sliderG.value << 8) | this.sliderB.value;
    }

    /** 从方块实体把几个旋钮读进本地字段 */
    private void pullFromTile() {
        if (this.tile == null || this.tile.getPos() == null) {
            return;
        }
        AlarmConfig c = this.tile.config();
        this.localOn = c.on;
        this.localAuto = this.tile.isAuto();
        this.localSpot = this.tile.spotMode();
        this.localTrigger = this.tile.triggerMode();
        this.localCfgValid = true;
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        if (!this.localCfgValid) {
            this.pullFromTile();
        }
        int l = this.left();
        int t = this.top();
        AlarmConfig c = this.tile == null ? null : this.tile.config();
        int rgb = this.tile == null ? 0xFF2A2A : this.tile.getColor();
        int rpm = c == null ? 20 : c.rpm;

        this.sliderR = new Slider(0, l, t + 64, W, 18, "R", (rgb >> 16) & 255, 0, 255);
        this.sliderG = new Slider(1, l, t + 84, W, 18, "G", (rgb >> 8) & 255, 0, 255);
        this.sliderB = new Slider(2, l, t + 104, W, 18, "B", rgb & 255, 0, 255);
        this.sliderRpm = new Slider(3, l, t + 126, W, 18,
                I18n.format("gui.jiezhirbmkmod.alarm_light.rpm"), rpm, 0, AlarmConfig.MAX_RPM);
        this.buttonList.add(this.sliderR);
        this.buttonList.add(this.sliderG);
        this.buttonList.add(this.sliderB);
        this.buttonList.add(this.sliderRpm);

        this.buttonList.add(new GuiButton(4, l, t + 148, (W - 4) / 2, 20, ""));
        this.buttonList.add(new GuiButton(6, l + W - (W - 4) / 2, t + 148, (W - 4) / 2, 20, ""));
        this.buttonList.add(new GuiButton(9, l, t + 172, W, 20, ""));
        // 四档光锥：自动 / FBO / 几何 / 贴图，等宽并排
        int spotW = 52;
        int spotGap = 3;
        for (int i = 0; i < SPOT_BUTTONS.length; ++i) {
            this.buttonList.add(new GuiButton(SPOT_BUTTONS[i],
                    l + i * (spotW + spotGap), t + 194, spotW, 20, ""));
        }
        this.buttonList.add(new GuiButton(7, l, t + H - 22, (W - 4) / 2, 20,
                I18n.format("gui.jiezhirbmkmod.alarm_light.apply")));
        this.buttonList.add(new GuiButton(8, l + W - (W - 4) / 2, t + H - 22, (W - 4) / 2, 20,
                I18n.format("gui.jiezhirbmkmod.alarm_light.cancel")));
        // 导出 / 导入：设置落成 .txt 与从 .txt 恢复（原生文件对话框，只在客户端跑）
        this.buttonList.add(new GuiButton(14, l, t + 238, (W - 4) / 2, 20,
                I18n.format("gui.jiezhirbmkmod.alarm_light.export_btn")));
        this.buttonList.add(new GuiButton(15, l + W - (W - 4) / 2, t + 238, (W - 4) / 2, 20,
                I18n.format("gui.jiezhirbmkmod.alarm_light.import_btn")));
        this.applyEnabledState();
        this.updateLabels();
        if (!this.snapValid) {
            // 只有面板第一次打开才拍快照：窗口拉伸会重走 initGui，
            // 那时控件可能已经被改过，重拍快照会把"改了一半"当成基准
            this.takeSnapshot();
        }
    }

    /**
     * 某一档在当前环境下<b>能不能真的画出来</b>。
     *
     * <p>只会有一档失灵：FBO（{@code SPOT_SSS}）。着色器编不出来、显卡不到 GL3.0、
     * 或者装了光影包主动让路，这时候 {@code RbmkConfig.sssAvailable} 是 false ——
     * 面板上就算选着 FBO，{@code TileAlarmLight#renderPath()} 也会退回几何锥体。
     * 按钮据此置灰，比"点了没反应、灯还是不亮"诚实。</p>
     *
     * <p>另外三档不吃这个开关：自动档按配置回落、几何是逐条 rayTraceBlocks、
     * 贴图是普通带 UV 世界几何，都不依赖自写 program。</p>
     */
    private static boolean spotUsable(int mode) {
        return mode != TileAlarmLight.SPOT_SSS || RbmkConfig.sssAvailable;
    }

    /** 联动中把颜色 / 转速 / 开关锁掉（光锥实现永远可点，除非本局 SSS 不可用） */
    private void applyEnabledState() {
        boolean editable = !this.localAuto;
        this.sliderR.enabled = editable;
        this.sliderG.enabled = editable;
        this.sliderB.enabled = editable;
        this.sliderRpm.enabled = editable;
        GuiButton lamp = this.find(6);
        if (lamp != null) {
            // 红石触发时"灯 亮/灭"这个按钮无效：灯态完全由电平决定。
            // 常亮档同理：灯永远亮着，没有"开/关"可点。
            // 留着可点，玩家点完只会发现灯不听话，而不是想到"该去断电"
            lamp.enabled = editable
                    && this.localTrigger != TileAlarmLight.TRIGGER_REDSTONE
                    && this.localTrigger != TileAlarmLight.TRIGGER_ALWAYS
                    && this.localTrigger != TileAlarmLight.TRIGGER_OFF;
        }
        GuiButton trigger = this.find(9);
        if (trigger != null) {
            // 联动中改触发方式看不出效果（开关由告警等级决定），一并置灰
            trigger.enabled = editable;
        }
        // 四档光锥：只有 FBO 会因为"着色器编不出来 / 光影让路"而失效，那档置灰
        for (int i = 0; i < SPOT_BUTTONS.length; ++i) {
            GuiButton spot = this.find(SPOT_BUTTONS[i]);
            if (spot != null) {
                spot.enabled = GuiAlarmLightPanel.spotUsable(SPOT_ORDER[i]);
            }
        }
    }

    private GuiButton find(int id) {
        for (int i = 0; i < this.buttonList.size(); i++) {
            GuiButton b = this.buttonList.get(i);
            if (b.id == id) {
                return b;
            }
        }
        return null;
    }

    /** 触发方式按钮的文案（五个模式一一对应，不要拼 key） */
    private String triggerKey() {
        switch (this.localTrigger) {
            case TileAlarmLight.TRIGGER_SNEAK:
                return "gui.jiezhirbmkmod.alarm_light.trigger_sneak";
            case TileAlarmLight.TRIGGER_REDSTONE:
                return "gui.jiezhirbmkmod.alarm_light.trigger_redstone";
            case TileAlarmLight.TRIGGER_ANY:
                return "gui.jiezhirbmkmod.alarm_light.trigger_any";
            case TileAlarmLight.TRIGGER_ALWAYS:
                return "gui.jiezhirbmkmod.alarm_light.trigger_always";
            case TileAlarmLight.TRIGGER_OFF:
                return "gui.jiezhirbmkmod.alarm_light.trigger_off";
            case TileAlarmLight.TRIGGER_CLICK:
            default:
                return "gui.jiezhirbmkmod.alarm_light.trigger_click";
        }
    }

    /** 开关按钮的文案跟着状态走 */
    private void updateLabels() {
        GuiButton link = this.find(4);
        if (link != null) {
            link.displayString = I18n.format(this.localAuto
                    ? "gui.jiezhirbmkmod.alarm_light.link_on"
                    : "gui.jiezhirbmkmod.alarm_light.link_off");
        }
        // 四档光锥：选中的那档加方括号。四档并排，谁在生效必须一眼看出来，
        // 光靠"点了没反应"是分不清"已经是这档"和"点了没生效"的。
        for (int i = 0; i < SPOT_BUTTONS.length; ++i) {
            GuiButton spot = this.find(SPOT_BUTTONS[i]);
            if (spot == null) {
                continue;
            }
            String label = I18n.format(SPOT_KEYS[i]);
            if (SPOT_ORDER[i] == TileAlarmLight.SPOT_AUTO) {
                // 「自动」不是一种画法，是"按环境选最优"。它此刻解析到哪条路
                // 必须直接写在按钮上：解析是全局同源的（autoPath），所有自动灯
                // 永远同一个答案 —— 有光影显示「自动·贴图」，没光影显示
                // 「自动·FBO」，SSS 编挂显示「自动·几何」。光影开关后文案
                // 跟着探测缓存走（约 1.6 秒滞后，与渲染一致）。
                int auto = TileAlarmLight.autoPath();
                label = I18n.format(auto == TileAlarmLight.PATH_SSS
                        ? "gui.jiezhirbmkmod.alarm_light.spot_auto_sss"
                        : auto == TileAlarmLight.PATH_TEXTURE
                        ? "gui.jiezhirbmkmod.alarm_light.spot_auto_tex"
                        : "gui.jiezhirbmkmod.alarm_light.spot_auto_geo");
            }
            spot.displayString = SPOT_ORDER[i] == this.localSpot
                    ? "[" + label + "]"
                    : label;
        }
        GuiButton lamp = this.find(6);
        if (lamp != null) {
            // 红石/常亮模式下这个按钮是灰的，文案改成为什么点不动
            lamp.displayString = I18n.format(
                    this.localTrigger == TileAlarmLight.TRIGGER_REDSTONE
                            ? "gui.jiezhirbmkmod.alarm_light.lamp_redstone"
                            : this.localTrigger == TileAlarmLight.TRIGGER_ALWAYS
                                    ? "gui.jiezhirbmkmod.alarm_light.lamp_always"
                                    : this.localTrigger == TileAlarmLight.TRIGGER_OFF
                                            ? "gui.jiezhirbmkmod.alarm_light.lamp_always_off"
                                            : (this.localOn
                                                    ? "gui.jiezhirbmkmod.alarm_light.lamp_on"
                                                    : "gui.jiezhirbmkmod.alarm_light.lamp_off"));
        }
        GuiButton trigger = this.find(9);
        if (trigger != null) {
            trigger.displayString = I18n.format(this.triggerKey());
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button == null) {
            return;
        }
        switch (button.id) {
            case 4:
                // 联动开关：立刻生效（要等服务端搜屏，成功后颜色会跳成告警色）
                this.send(this.localAuto ? MessageAlarmLight.AUTO_OFF : MessageAlarmLight.AUTO_ON);
                this.localAuto = !this.localAuto;
                this.applyEnabledState();
                this.updateLabels();
                break;
            case 10:
            case 11:
            case 12:
            case 13:
                // 四档光锥：点哪个就选哪个，当场生效、不关界面。
                // 已经是当前档时再点一下也照样发包 —— 面板可能在别处被改过，
                // "没变化"比"发送一次冗余包"更容易让人以为界面卡了。
                this.localSpot = SPOT_ORDER[button.id - SPOT_BUTTONS[0]];
                this.send(MessageAlarmLight.AUTO_KEEP);
                this.updateLabels();
                break;
            case 6:
                this.localOn = !this.localOn;
                this.send(MessageAlarmLight.AUTO_KEEP);
                this.updateLabels();
                break;
            case 9:
                // 触发方式：右键 → 潜行右键 → 红石 → 任意 → 常亮 → 常灭 → 右键。
                // 顺序按"最常用的排前面"：默认值是任意，按一下先回到右键。
                this.localTrigger = (this.localTrigger + 1) % (TileAlarmLight.TRIGGER_OFF + 1);
                this.send(MessageAlarmLight.AUTO_KEEP);
                this.applyEnabledState();
                this.updateLabels();
                break;
            case 7:
                this.send(MessageAlarmLight.AUTO_KEEP);
                this.mc.displayGuiScreen(null);
                break;
            case 8:
                // 取消：恢复打开面板前的整套设置（快照在首次 initGui 拍下）。
                // 每次点击都是当场发包生效的，所以这里要"反向发包"——
                // 先把联动状态拨回快照值，再把快照配置盖回去，最后关面板。
                // ★ 先算联动差异再 restoreSnapshot：restore 会把 localAuto
                //   设回快照值，之后再比较就永远相等了。
                byte autoByte = MessageAlarmLight.AUTO_KEEP;
                if (this.snapAuto != this.localAuto) {
                    autoByte = this.snapAuto ? MessageAlarmLight.AUTO_ON
                            : MessageAlarmLight.AUTO_OFF;
                }
                this.restoreSnapshot();
                this.send(autoByte);
                if (autoByte != MessageAlarmLight.AUTO_KEEP) {
                    // AUTO_ON 会把颜色交给告警等级（配置部分被服务端丢弃）；
                    // AUTO_OFF 恢复的是绑定那一刻的手动快照 —— 两种情况都要
                    // 再补一包 AUTO_KEEP，把"打开面板那一刻"的配置盖回去。
                    this.send(MessageAlarmLight.AUTO_KEEP);
                }
                this.chat("msg.jiezhirbmkmod.alarm_light.cancelled",
                        net.minecraft.util.text.TextFormatting.GRAY);
                this.mc.displayGuiScreen(null);
                break;
            case 14:
                this.exportSettings();
                break;
            case 15:
                this.importSettings();
                break;
            default:
                break;
        }
    }

    private void send(byte auto) {
        if (this.tile == null || this.tile.getPos() == null) {
            return;
        }
        this.lockTicks = 10;
        NetGuard.sendToServer(new MessageAlarmLight(this.tile.getPos(),
                this.currentColor(), this.sliderRpm.value, this.localOn, this.localSpot,
                this.localTrigger, auto));
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (this.lockTicks > 0) {
            // 自己的包还在路上，别让 tile 的旧值把乐观状态顶回去
            this.lockTicks--;
            return;
        }
        boolean wasAuto = this.localAuto;
        int wasSpot = this.localSpot;
        boolean wasOn = this.localOn;
        int wasTrigger = this.localTrigger;
        this.pullFromTile();
        if (wasAuto != this.localAuto || wasSpot != this.localSpot
                || wasOn != this.localOn || wasTrigger != this.localTrigger) {
            this.applyEnabledState();
            this.updateLabels();
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

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        int l = this.left();
        int t = this.top();
        int cx = this.width / 2;

        drawCenteredString(this.fontRenderer, I18n.format("gui.jiezhirbmkmod.alarm_light.title"),
                cx, t + 4, 0xFF39FF14);

        // 第二行：联动状态（联动中给出数据源坐标，否则给出"手动"）
        String sub;
        int subColor = 0xFF9AA0A6;
        if (this.localAuto && this.tile != null && this.tile.sourcePos() != null) {
            sub = I18n.format("gui.jiezhirbmkmod.alarm_light.linked_to",
                    this.tile.sourcePos().getX(), this.tile.sourcePos().getY(),
                    this.tile.sourcePos().getZ());
            subColor = 0xFFFFD23F;
        } else if (this.localAuto) {
            sub = I18n.format("gui.jiezhirbmkmod.alarm_light.linked");
            subColor = 0xFFFFD23F;
        } else {
            sub = I18n.format("gui.jiezhirbmkmod.alarm_light.manual");
        }
        drawCenteredString(this.fontRenderer, sub, cx, t + 18, subColor);

        // 色块预览：滑条拖动时实时跟手
        int rgb = this.currentColor();
        drawRect(l, t + 34, l + W, t + 52, 0xFF000000 | rgb);
        String hex = String.format("#%06X", Integer.valueOf(rgb));
        this.fontRenderer.drawString(hex, l, t + 53, 0xFFDDDDDD);

        if (!RbmkConfig.sssAvailable) {
            // 比"联动中"更优先：玩家会先怀疑"为什么光锥按钮点不动"
            this.drawHint(I18n.format("gui.jiezhirbmkmod.alarm_light.sss_off"), l, t + 218,
                    0xFFB26A6A);
        } else if (this.localAuto) {
            // 联动中说明为什么锁着 —— 只把控件置灰不说原因等于没做
            this.drawHint(I18n.format("gui.jiezhirbmkmod.alarm_light.locked"), l, t + 218,
                    0xFFFFD23F);
        } else if (this.localTrigger == TileAlarmLight.TRIGGER_REDSTONE) {
            // 红石模式没有"开关"按钮可点，得在这儿说一句灯态到底看什么
            this.drawHint(I18n.format("gui.jiezhirbmkmod.alarm_light.hint_redstone"), l, t + 218,
                    0xFF8AB4F8);
        } else if (this.localTrigger == TileAlarmLight.TRIGGER_SNEAK) {
            // 手势被对调了，不写一句玩家会以为面板打不开了
            this.drawHint(I18n.format("gui.jiezhirbmkmod.alarm_light.hint_sneak"), l, t + 218,
                    0xFF8AB4F8);
        } else if (this.localTrigger == TileAlarmLight.TRIGGER_ALWAYS) {
            // 常亮档：两个手势都只开面板，得说明"开关灯"不存在
            this.drawHint(I18n.format("gui.jiezhirbmkmod.alarm_light.hint_always"), l, t + 218,
                    0xFF8AB4F8);
        } else if (this.localTrigger == TileAlarmLight.TRIGGER_OFF) {
            // 常灭档：镜像于常亮
            this.drawHint(I18n.format("gui.jiezhirbmkmod.alarm_light.hint_off"), l, t + 218,
                    0xFF8AB4F8);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    /**
     * 画一行说明文字，超宽自动截断。
     *
     * <p>这条提示的位置只有一行高（下面就是「应用」按钮），所以不能让它换行。
     * 中文 20 字差不多就顶满 220px；英文同样的意思要 35 个字符，
     * 语言文件里两边的文案都按这个宽度写过，这里的截断只是防止翻译再变长时糊到按钮上。</p>
     */
    private void drawHint(String text, int x, int y, int color) {
        java.util.List<String> lines = this.fontRenderer.listFormattedStringToWidth(text, W);
        String one = lines.isEmpty() ? text : lines.get(0);
        this.fontRenderer.drawString(one, x, y, color);
    }

    // ==================== 快照 / 取消（v17.9） ====================

    /** 打开面板那一刻把整套状态拍下来，"取消"按它回滚 */
    private void takeSnapshot() {
        AlarmConfig c = this.tile == null ? null : this.tile.config();
        this.snapColor = c == null ? 0xFF2A2A : this.tile.getColor();
        this.snapRpm = c == null ? 20 : c.rpm;
        this.snapOn = this.localOn;
        this.snapSpot = this.localSpot;
        this.snapTrigger = this.localTrigger;
        this.snapAuto = this.localAuto;
        this.snapValid = true;
    }

    /** 把快照值写回控件与本地状态（不发包，发包由调用方按联动差异决定） */
    private void restoreSnapshot() {
        this.setSlider(this.sliderR, (this.snapColor >> 16) & 255);
        this.setSlider(this.sliderG, (this.snapColor >> 8) & 255);
        this.setSlider(this.sliderB, this.snapColor & 255);
        this.setSlider(this.sliderRpm, this.snapRpm);
        this.localOn = this.snapOn;
        this.localSpot = this.snapSpot;
        this.localTrigger = this.snapTrigger;
        this.localAuto = this.snapAuto;
        this.applyEnabledState();
        this.updateLabels();
    }

    private static void setSlider(Slider s, int value) {
        s.value = Math.max(0, Math.min(s.max, value));
        s.displayString = s.label + " " + s.value;
    }

    /** 客户端聊天栏提示（带颜色）；专用服务器永远不会加载这个类 */
    private void chat(String key, net.minecraft.util.text.TextFormatting color, Object... args) {
        if (this.mc.player == null) {
            return;
        }
        net.minecraft.util.text.TextComponentTranslation c =
                new net.minecraft.util.text.TextComponentTranslation(key, args);
        c.getStyle().setColor(color);
        this.mc.player.sendStatusMessage(c, false);
    }

    // ==================== 导出 / 导入设置（v17.9） ====================

    /**
     * 把当前面板状态写成 .txt（原生"另存为"对话框，PC 任何可写位置）。
     *
     * <p>对话框是模态的：弹出期间游戏画面会冻结（不响应鼠标键盘），选完恢复。
     * 这是 1.12.2 没有内置文件选择器的最务实解。</p>
     */
    private void exportSettings() {
        try {
            java.awt.FileDialog fd = new java.awt.FileDialog((java.awt.Frame) null,
                    I18n.format("gui.jiezhirbmkmod.alarm_light.export_dialog"),
                    java.awt.FileDialog.SAVE);
            fd.setDirectory(this.mc.gameDir.getAbsolutePath());
            if (this.tile != null && this.tile.getPos() != null) {
                fd.setFile(String.format(java.util.Locale.ROOT, "rbmk_alarm_%d_%d_%d.txt",
                        Integer.valueOf(this.tile.getPos().getX()),
                        Integer.valueOf(this.tile.getPos().getY()),
                        Integer.valueOf(this.tile.getPos().getZ())));
            }
            fd.setVisible(true);
            String name = fd.getFile();
            if (name == null) {
                return; // 用户取消了对话框，什么都不做
            }
            java.io.File out = new java.io.File(fd.getDirectory(), name);
            java.io.PrintWriter w = new java.io.PrintWriter(
                    new java.io.OutputStreamWriter(new java.io.FileOutputStream(out),
                            java.nio.charset.StandardCharsets.UTF_8));
            try {
                w.println("# RBK-Monitor 报警灯设置（本文件可被面板的「导入设置」识别）");
                w.println("# color=#RRGGBB / rpm=0.." + AlarmConfig.MAX_RPM
                        + " / on=true|false");
                w.println("# spot=auto|geometry|sss|texture");
                w.println("# trigger=click|sneak|redstone|any|always|off");
                w.println("rbmk-alarm-settings=v1");
                w.println("color=" + String.format(java.util.Locale.ROOT, "#%06X",
                        Integer.valueOf(this.currentColor())));
                w.println("rpm=" + Integer.valueOf(this.sliderRpm.value));
                w.println("on=" + Boolean.toString(this.localOn));
                w.println("spot=" + spotName(this.localSpot));
                w.println("trigger=" + triggerName(this.localTrigger));
                w.println("auto=" + Boolean.toString(this.localAuto));
            } finally {
                w.close();
            }
            this.chat("msg.jiezhirbmkmod.alarm_light.exported",
                    net.minecraft.util.text.TextFormatting.GREEN, out.getAbsolutePath());
        } catch (Throwable t) {
            this.chat("msg.jiezhirbmkmod.alarm_light.export_fail",
                    net.minecraft.util.text.TextFormatting.RED,
                    String.valueOf(t.getMessage() == null ? t.toString() : t.getMessage()));
        }
    }

    /**
     * 从「导出设置」写出的 .txt 恢复（原生"打开"对话框）。
     *
     * <p>严格解析：版本行必须是 v1；未知键忽略（向前兼容）；任何值非法
     * 都整包拒绝并报出行号，绝不半途应用一半 —— 半套设置比失败更难排查。</p>
     */
    private void importSettings() {
        try {
            java.awt.FileDialog fd = new java.awt.FileDialog((java.awt.Frame) null,
                    I18n.format("gui.jiezhirbmkmod.alarm_light.import_dialog"),
                    java.awt.FileDialog.LOAD);
            fd.setDirectory(this.mc.gameDir.getAbsolutePath());
            fd.setVisible(true);
            String name = fd.getFile();
            if (name == null) {
                return;
            }
            java.io.File in = new java.io.File(fd.getDirectory(), name);
            java.util.List<String> lines = java.nio.file.Files.readAllLines(in.toPath(),
                    java.nio.charset.StandardCharsets.UTF_8);
            // 目标状态从"当前面板"起步，文件里有值的键才覆盖
            int color = this.currentColor();
            int rpm = this.sliderRpm.value;
            boolean on = this.localOn;
            int spot = this.localSpot;
            int trigger = this.localTrigger;
            boolean auto = this.localAuto;
            int ln = 0;
            for (String raw : lines) {
                ln++;
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int eq = line.indexOf('=');
                if (eq <= 0) {
                    throw new IllegalArgumentException("line " + ln + " (no '='): " + line);
                }
                String k = line.substring(0, eq).trim().toLowerCase(java.util.Locale.ROOT);
                String v = line.substring(eq + 1).trim();
                if ("rbmk-alarm-settings".equals(k)) {
                    if (!"v1".equals(v)) {
                        throw new IllegalArgumentException("line " + ln
                                + " (unsupported version): " + v);
                    }
                } else if ("color".equals(k)) {
                    color = parseColor(v, ln);
                } else if ("rpm".equals(k)) {
                    rpm = parseInt(v, 0, AlarmConfig.MAX_RPM, ln);
                } else if ("on".equals(k)) {
                    on = parseBool(v, ln);
                } else if ("spot".equals(k)) {
                    spot = parseSpot(v, ln);
                } else if ("trigger".equals(k)) {
                    trigger = parseTrigger(v, ln);
                } else if ("auto".equals(k)) {
                    auto = parseBool(v, ln);
                }
                // 其它键：忽略（向前兼容）
            }
            // 应用到本地状态并发包（联动差异处理与「取消」同款两段式）
            this.setSlider(this.sliderR, (color >> 16) & 255);
            this.setSlider(this.sliderG, (color >> 8) & 255);
            this.setSlider(this.sliderB, color & 255);
            this.setSlider(this.sliderRpm, rpm);
            this.localOn = on;
            this.localSpot = spot;
            this.localTrigger = trigger;
            this.applyEnabledState();
            this.updateLabels();
            byte autoByte = MessageAlarmLight.AUTO_KEEP;
            if (auto != this.localAuto) {
                autoByte = auto ? MessageAlarmLight.AUTO_ON : MessageAlarmLight.AUTO_OFF;
                this.localAuto = auto;
            }
            this.send(autoByte);
            if (autoByte != MessageAlarmLight.AUTO_KEEP) {
                this.send(MessageAlarmLight.AUTO_KEEP);
            }
            this.chat("msg.jiezhirbmkmod.alarm_light.imported",
                    net.minecraft.util.text.TextFormatting.GREEN, in.getName());
        } catch (Throwable t) {
            this.chat("msg.jiezhirbmkmod.alarm_light.import_fail",
                    net.minecraft.util.text.TextFormatting.RED,
                    String.valueOf(t.getMessage() == null ? t.toString() : t.getMessage()));
        }
    }

    // ---- 导出/导入的格式助手（名字 ⇄ 值，双向唯一） ----

    private static String spotName(int spot) {
        switch (spot) {
            case TileAlarmLight.SPOT_GEOMETRY: return "geometry";
            case TileAlarmLight.SPOT_SSS: return "sss";
            case TileAlarmLight.SPOT_TEXTURE: return "texture";
            default: return "auto";
        }
    }

    private static String triggerName(int trigger) {
        switch (trigger) {
            case TileAlarmLight.TRIGGER_SNEAK: return "sneak";
            case TileAlarmLight.TRIGGER_REDSTONE: return "redstone";
            case TileAlarmLight.TRIGGER_ANY: return "any";
            case TileAlarmLight.TRIGGER_ALWAYS: return "always";
            case TileAlarmLight.TRIGGER_OFF: return "off";
            default: return "click";
        }
    }

    private static int parseColor(String v, int ln) {
        String s = v.trim();
        if (s.startsWith("#")) {
            s = s.substring(1);
        }
        if (s.length() != 6) {
            throw new IllegalArgumentException("line " + ln + " (color, want #RRGGBB): " + v);
        }
        return Integer.parseInt(s, 16) & 0xFFFFFF;
    }

    private static int parseInt(String v, int min, int max, int ln) {
        int i = Integer.parseInt(v.trim());
        if (i < min || i > max) {
            throw new IllegalArgumentException("line " + ln + " (want " + min + ".." + max + "): " + v);
        }
        return i;
    }

    private static boolean parseBool(String v, int ln) {
        String s = v.trim().toLowerCase(java.util.Locale.ROOT);
        if ("true".equals(s) || "yes".equals(s) || "1".equals(s)) {
            return true;
        }
        if ("false".equals(s) || "no".equals(s) || "0".equals(s)) {
            return false;
        }
        throw new IllegalArgumentException("line " + ln + " (want true|false): " + v);
    }

    private static int parseSpot(String v, int ln) {
        String s = v.trim().toLowerCase(java.util.Locale.ROOT);
        if ("auto".equals(s)) return TileAlarmLight.SPOT_AUTO;
        if ("geometry".equals(s)) return TileAlarmLight.SPOT_GEOMETRY;
        if ("sss".equals(s)) return TileAlarmLight.SPOT_SSS;
        if ("texture".equals(s)) return TileAlarmLight.SPOT_TEXTURE;
        return parseInt(s, TileAlarmLight.SPOT_AUTO, TileAlarmLight.SPOT_TEXTURE, ln);
    }

    private static int parseTrigger(String v, int ln) {
        String s = v.trim().toLowerCase(java.util.Locale.ROOT);
        if ("click".equals(s)) return TileAlarmLight.TRIGGER_CLICK;
        if ("sneak".equals(s)) return TileAlarmLight.TRIGGER_SNEAK;
        if ("redstone".equals(s)) return TileAlarmLight.TRIGGER_REDSTONE;
        if ("any".equals(s)) return TileAlarmLight.TRIGGER_ANY;
        if ("always".equals(s)) return TileAlarmLight.TRIGGER_ALWAYS;
        if ("off".equals(s)) return TileAlarmLight.TRIGGER_OFF;
        return parseInt(s, TileAlarmLight.TRIGGER_CLICK, TileAlarmLight.TRIGGER_OFF, ln);
    }

    /** 通用 0..max 滑条（画在 GuiButton 里面，省得自己处理点击） */
    private static class Slider
    extends GuiButton {
        private final String label;
        private final int barX;
        private final int barY;
        private final int barW;
        private final int max;
        private int value;

        Slider(int id, int x, int y, int width, int height, String label, int value,
                int min, int max) {
            super(id, x, y, width, height, label + " " + value);
            this.label = label;
            this.barX = x + 4;
            this.barY = y + height - 5;
            this.barW = Math.max(1, width - 8);
            this.max = Math.max(1, max);
            this.value = Math.max(min, Math.min(max, value));
            this.displayString = this.label + " " + this.value;
        }

        @Override
        public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
            if (!this.enabled) {
                return false;
            }
            if (super.mousePressed(mc, mouseX, mouseY)) {
                this.setFromMouse(mouseX);
                return true;
            }
            return false;
        }

        @Override
        protected void mouseDragged(Minecraft mc, int mouseX, int mouseY) {
            if (this.enabled) {
                this.setFromMouse(mouseX);
            }
        }

        private void setFromMouse(int mouseX) {
            float f = (float) (mouseX - this.barX) / (float) this.barW;
            int v = Math.round(Math.max(0.0F, Math.min(1.0F, f)) * this.max);
            this.value = Math.max(0, Math.min(this.max, v));
            this.displayString = this.label + " " + this.value;
        }

        @Override
        public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
            if (!this.visible) {
                return;
            }
            super.drawButton(mc, mouseX, mouseY, partialTicks);
            net.minecraft.client.gui.Gui.drawRect(this.barX, this.barY,
                    this.barX + this.barW, this.barY + 3, 0x66000000);
            net.minecraft.client.gui.Gui.drawRect(this.barX, this.barY,
                    this.barX + this.barW * this.value / this.max, this.barY + 3,
                    this.enabled ? 0xFF39FF14 : 0xFF6B6B6B);
        }
    }
}
