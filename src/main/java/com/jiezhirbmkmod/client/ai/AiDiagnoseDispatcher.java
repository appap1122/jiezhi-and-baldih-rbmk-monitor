package com.jiezhirbmkmod.client.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jiezhirbmkmod.dfc.RbmkConfig;
import com.jiezhirbmkmod.dfc.RbmkSnapshot;
import com.jiezhirbmkmod.network.msg.MessageTerminalCommand;
import com.jiezhirbmkmod.network.msg.MessageTerminalCommand.CmdLine;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * v17.0 AI diagnosis dispatcher.
 *
 * <p>Mode comes from {@link RbmkConfig#aiMode}: {@code local} always runs the
 * built-in rule tree, {@code ai} always POSTs to the configured endpoint,
 * {@code auto} uses the API only when a key is configured.</p>
 *
 * <p>All callbacks run on the Minecraft client thread.</p>
 */
@SideOnly(Side.CLIENT)
public final class AiDiagnoseDispatcher {

    public interface Callback {
        void onResult(List<CmdLine> lines);
    }

    private static final String MODEL = "gpt-3.5-turbo";
    private static final int MAX_REPLY_LINES = 14;
    private static final AtomicBoolean BUSY = new AtomicBoolean(false);

    private AiDiagnoseDispatcher() {
    }

    public static boolean isBusy() {
        return BUSY.get();
    }

    /** Run a diagnosis with the currently configured mode. */
    public static void diagnose(final RbmkSnapshot data, final Callback cb) {
        if (cb == null) {
            return;
        }
        String mode = RbmkConfig.aiModeName();
        boolean hasKey = RbmkConfig.hasAiKey();
        boolean useAi = "ai".equals(mode) || ("auto".equals(mode) && hasKey);
        if ("ai".equals(mode) && !hasKey) {
            List<CmdLine> out = new ArrayList<CmdLine>();
            out.add(new CmdLine(MessageTerminalCommand.C_ERR,
                    "未配置 API Key，请输入 ai key <密钥> 进行配置。"));
            cb.onResult(out);
            return;
        }
        if (!useAi) {
            cb.onResult(local(data));
            return;
        }
        if (!BUSY.compareAndSet(false, true)) {
            List<CmdLine> out = new ArrayList<CmdLine>();
            out.add(new CmdLine(MessageTerminalCommand.C_WARN,
                    "[AI] 上一个诊断请求仍在进行，请稍候再试。"));
            cb.onResult(out);
            return;
        }
        final String context = buildContext(data);
        List<CmdLine> pending = new ArrayList<CmdLine>();
        pending.add(new CmdLine(MessageTerminalCommand.C_DIM,
                "[AI] 正在请求 " + RbmkConfig.aiApiUrl + " … (超时 30s)"));
        cb.onResult(pending);
        Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                List<CmdLine> out = new ArrayList<CmdLine>();
                try {
                    String reply = requestAi(context);
                    out.add(new CmdLine(MessageTerminalCommand.C_OK, "[AI] 诊断结果:"));
                    List<String> lines = wrap(reply, MAX_REPLY_LINES);
                    for (int i = 0; i < lines.size(); ++i) {
                        out.add(new CmdLine(MessageTerminalCommand.C_OUT, "  " + lines.get(i)));
                    }
                }
                catch (Throwable t) {
                    out.add(new CmdLine(MessageTerminalCommand.C_ERR,
                            "[AI] 请求失败: " + shortError(t)));
                    out.add(new CmdLine(MessageTerminalCommand.C_DIM,
                            "  可改用 diagnose local 走本地逻辑树，或 ai status 检查配置。"));
                }
                finally {
                    BUSY.set(false);
                }
                post(out, cb);
            }
        }, "rbmk-ai-diagnose");
        worker.setDaemon(true);
        worker.start();
    }

    // ---------------------------------------------------------------- local

    /** Built-in rule tree: no network, instant answer. */
    public static List<CmdLine> local(RbmkSnapshot d) {
        List<CmdLine> out = new ArrayList<CmdLine>();
        out.add(new CmdLine(MessageTerminalCommand.C_DIM, "[LOCAL] 本地逻辑树诊断:"));
        if (d == null) {
            out.add(new CmdLine(MessageTerminalCommand.C_ERR,
                    "  未找到可诊断的 RBMK 屏幕（24 格内），请先 connect 绑定反应堆。"));
            return out;
        }
        if (d.meltdown) {
            out.add(new CmdLine(MessageTerminalCommand.C_ERR,
                    "  严重：堆芯已进入熔毁状态，立即 SCRAM 并撤离。"));
        }
        else if (!d.linked || d.offline) {
            out.add(new CmdLine(MessageTerminalCommand.C_ERR,
                    "  未连接/离线：检查屏幕与反应堆控制台的绑定，或远处区块是否加载。"));
        }
        else {
            boolean crit = false;
            boolean warn = false;
            double ratio = d.maxHeatLimit > 0.0D ? d.maxHeat / d.maxHeatLimit : 0.0D;
            if (ratio >= 0.9D) {
                out.add(new CmdLine(MessageTerminalCommand.C_ERR, String.format(Locale.ROOT,
                        "  严重：最高柱温 %.0f/%.0f C（%.0f%%），非常接近堆芯上限。",
                        Double.valueOf(d.maxHeat), Double.valueOf(d.maxHeatLimit),
                        Double.valueOf(ratio * 100.0D))));
                crit = true;
            }
            else if (ratio >= 0.75D) {
                out.add(new CmdLine(MessageTerminalCommand.C_WARN, String.format(Locale.ROOT,
                        "  警告：最高柱温 %.0f/%.0f C（%.0f%%），请降温或加深控制棒。",
                        Double.valueOf(d.maxHeat), Double.valueOf(d.maxHeatLimit),
                        Double.valueOf(ratio * 100.0D))));
                warn = true;
            }
            else if (d.avgHeat > RbmkConfig.defaultWarnC) {
                out.add(new CmdLine(MessageTerminalCommand.C_WARN, String.format(Locale.ROOT,
                        "  警告：平均柱温 %.0f C 高于预警阈值 %d C。",
                        Double.valueOf(d.avgHeat), Integer.valueOf(RbmkConfig.defaultWarnC))));
                warn = true;
            }
            double rate = RbmkConfig.defaultRateWarnD / 10.0D;
            if (rate > 0.0D && d.tempDelta > rate) {
                out.add(new CmdLine(MessageTerminalCommand.C_WARN, String.format(Locale.ROOT,
                        "  警告：温升速率 %.1f C/s 超过阈值 %.1f C/s。",
                        Double.valueOf(d.tempDelta), Double.valueOf(rate))));
                warn = true;
            }
            if (d.flux <= 0 && d.avgHeat > RbmkConfig.coldThreshold) {
                out.add(new CmdLine(MessageTerminalCommand.C_WARN,
                        "  警告：通量为 0 但堆芯仍热，燃料/中子源可能已耗尽。"));
                warn = true;
            }
            if (d.controlCount <= 0) {
                out.add(new CmdLine(MessageTerminalCommand.C_WARN,
                        "  提示：未检测到控制棒，无法自动/手动停堆。"));
                warn = true;
            }
            if (d.meltRisk > 0.6D) {
                out.add(new CmdLine(MessageTerminalCommand.C_WARN, String.format(Locale.ROOT,
                        "  警告：熔毁风险系数 %.2f 偏高。", Double.valueOf(d.meltRisk))));
                warn = true;
            }
            if (!crit && !warn) {
                out.add(new CmdLine(MessageTerminalCommand.C_OK,
                        "  状态正常：温度、通量与温升速率均在安全范围内。"));
            }
            out.add(new CmdLine(MessageTerminalCommand.C_OUT, String.format(Locale.ROOT,
                    "  数据: 柱 %d/%d  平均 %.0f C  最高 %.0f C  通量 %d F  温升 %.1f C/s  控制棒 %d",
                    Integer.valueOf(d.columnCount), Integer.valueOf(d.columnCount),
                    Double.valueOf(d.avgHeat), Double.valueOf(d.maxHeat), Integer.valueOf(d.flux),
                    Double.valueOf(d.tempDelta), Integer.valueOf(d.controlCount))));
        }
        out.add(new CmdLine(MessageTerminalCommand.C_DIM,
                "  模式 local：不发网络请求。diagnose auto 在配置 Key 后自动改用 AI。"));
        return out;
    }

    // ---------------------------------------------------------------- remote

    private static String requestAi(String context) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(RbmkConfig.aiApiUrl).openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setRequestProperty("Authorization", "Bearer " + RbmkConfig.aiApiKey);

        JsonObject root = new JsonObject();
        root.addProperty("model", MODEL);
        root.addProperty("temperature", Double.valueOf(0.3D));
        JsonArray messages = new JsonArray();
        JsonObject sys = new JsonObject();
        sys.addProperty("role", "system");
        sys.addProperty("content",
                "你是 RBMK 核反应堆安全诊断专家。只根据给出的读数给出简短诊断，"
                + "格式为：结论 / 依据 / 建议，总字数不超过 300 字。");
        messages.add(sys);
        JsonObject user = new JsonObject();
        user.addProperty("role", "user");
        user.addProperty("content", context);
        messages.add(user);
        root.add("messages", messages);

        byte[] payload = root.toString().getBytes(StandardCharsets.UTF_8);
        OutputStream os = conn.getOutputStream();
        try {
            os.write(payload);
        }
        finally {
            os.close();
        }
        int code = conn.getResponseCode();
        InputStream is = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
        String body = readAll(is);
        if (code < 200 || code >= 300) {
            throw new IOException("HTTP " + code + ": " + snippet(body, 160));
        }
        return extractContent(body);
    }

    private static String extractContent(String body) throws IOException {
        try {
            JsonObject root = new JsonParser().parse(body).getAsJsonObject();
            if (root.has("error")) {
                throw new IOException(snippet(root.get("error").toString(), 160));
            }
            JsonArray choices = root.getAsJsonArray("choices");
            if (choices == null || choices.size() == 0) {
                throw new IOException("响应中没有 choices: " + snippet(body, 160));
            }
            JsonObject msg = choices.get(0).getAsJsonObject().getAsJsonObject("message");
            String content = msg == null ? null : msg.get("content").getAsString();
            if (content == null || content.trim().isEmpty()) {
                throw new IOException("模型返回空内容");
            }
            return content.trim();
        }
        catch (IOException e) {
            throw e;
        }
        catch (Throwable t) {
            throw new IOException("无法解析响应: " + snippet(body, 160));
        }
    }

    private static String buildContext(RbmkSnapshot d) {
        StringBuilder sb = new StringBuilder();
        sb.append("RBMK 报警屏实时读数：\n");
        if (d == null) {
            sb.append("(未绑定反应堆，无读数)\n");
            return sb.toString();
        }
        sb.append("连接状态: ").append(d.linked ? "已连接" : "未连接")
          .append(d.offline ? " / 离线" : "").append('\n');
        sb.append("柱数: ").append(d.columnCount).append('\n');
        sb.append("平均柱温: ").append(String.format(Locale.ROOT, "%.1f", Double.valueOf(d.avgHeat))).append(" C\n");
        sb.append("最高柱温: ").append(String.format(Locale.ROOT, "%.1f", Double.valueOf(d.maxHeat))).append(" C\n");
        sb.append("温度上限: ").append(String.format(Locale.ROOT, "%.1f", Double.valueOf(d.maxHeatLimit))).append(" C\n");
        sb.append("温升速率: ").append(String.format(Locale.ROOT, "%.2f", Double.valueOf(d.tempDelta))).append(" C/s\n");
        sb.append("中子通量: ").append(d.flux).append(" F\n");
        sb.append("燃料柱: ").append(d.fuelCount).append('\n');
        sb.append("控制棒数: ").append(d.controlCount)
          .append("，平均插入 ").append(String.format(Locale.ROOT, "%.0f", Double.valueOf(d.avgRodLevel))).append("%\n");
        sb.append("蒸汽总量: ").append(String.format(Locale.ROOT, "%.0f", Double.valueOf(d.steamTotal))).append('\n');
        sb.append("熔毁风险: ").append(String.format(Locale.ROOT, "%.2f", Double.valueOf(d.meltRisk))).append('\n');
        sb.append("熔毁中: ").append(d.meltdown ? "是" : "否").append('\n');
        return sb.toString();
    }

    // ---------------------------------------------------------------- utils

    private static void post(final List<CmdLine> lines, final Callback cb) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) {
            cb.onResult(lines);
            return;
        }
        mc.addScheduledTask(new Runnable() {
            @Override
            public void run() {
                cb.onResult(lines);
            }
        });
    }

    private static String readAll(InputStream is) throws IOException {
        if (is == null) {
            return "";
        }
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        }
        finally {
            is.close();
        }
    }

    private static List<String> wrap(String text, int maxLines) {
        List<String> out = new ArrayList<String>();
        String[] raw = text.split("\\r?\\n");
        for (int i = 0; i < raw.length && out.size() < maxLines; ++i) {
            String line = raw[i].trim();
            while (line.length() > 78 && out.size() < maxLines) {
                out.add(line.substring(0, 78));
                line = line.substring(78);
            }
            if (!line.isEmpty()) {
                out.add(line);
            }
        }
        return out;
    }

    private static String snippet(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.replace('\n', ' ').trim();
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }

    private static String shortError(Throwable t) {
        String m = t.getMessage();
        return t.getClass().getSimpleName() + (m == null ? "" : ": " + snippet(m, 120));
    }
}
