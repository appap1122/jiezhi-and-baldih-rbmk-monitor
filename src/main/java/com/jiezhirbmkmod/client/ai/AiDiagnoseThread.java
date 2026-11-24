package com.jiezhirbmkmod.client.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jiezhirbmkmod.dfc.RbmkConfig;
import com.jiezhirbmkmod.dfc.RbmkSnapshot;
import com.jiezhirbmkmod.network.msg.MessageTerminalCommand;
import com.jiezhirbmkmod.network.msg.MessageTerminalCommand.CmdLine;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * v17.1 terminal 'diagnose': sends the live reactor readings to an
 * OpenAI-compatible chat API on a background thread and posts the answer back
 * to the terminal through {@code Minecraft.addScheduledTask}.
 */
@SideOnly(Side.CLIENT)
public class AiDiagnoseThread extends Thread {

    public interface Callback {
        void onResult(boolean ok, List<CmdLine> lines);
    }

    private static final String MODEL = "gpt-3.5-turbo";
    private static final String ERROR_TEXT = "[ERROR] AI 诊断失败，请检查网络或 API 配置。";

    private final RbmkSnapshot data;
    private final Callback callback;

    private AiDiagnoseThread(RbmkSnapshot data, Callback callback) {
        super("RBMK-AI-diagnose");
        this.setDaemon(true);
        this.data = data;
        this.callback = callback;
    }

    public static void start(RbmkSnapshot data, Callback callback) {
        new AiDiagnoseThread(data, callback).start();
    }

    @Override
    public void run() {
        List<CmdLine> lines = new ArrayList<CmdLine>();
        boolean ok = false;
        try {
            if (!RbmkConfig.hasAiKey()) {
                throw new IllegalStateException("no API key");
            }
            double temperature = this.data == null ? 0.0D : this.data.avgHeat;
            double maxTemperature = this.data == null ? 0.0D : this.data.maxHeatLimit;
            double flux = this.data == null ? 0.0D : (double) this.data.flux;
            double rodLevel = this.data == null ? 0.0D : this.data.avgRodLevel;

            String stateJson = String.format(Locale.ROOT,
                    "{\"temperature\": %.1f, \"max_temperature\": %.1f, \"flux\": %.1f, \"rod_level\": %.1f}",
                    Double.valueOf(temperature), Double.valueOf(maxTemperature),
                    Double.valueOf(flux), Double.valueOf(rodLevel));
            String prompt = String.format(Locale.ROOT,
                    "你是顶级核电站专家。当前 RBMK 反应堆状态：温度 %s℃（上限 %s℃），中子通量 %sF，"
                    + "控制棒插入率 %s%%。请用 50 字以内给出专业的安全评估和下一步操作建议。",
                    AiDiagnoseThread.fmt(temperature), AiDiagnoseThread.fmt(maxTemperature),
                    AiDiagnoseThread.fmt(flux), AiDiagnoseThread.fmt(rodLevel));

            JsonObject root = new JsonObject();
            root.addProperty("model", MODEL);
            root.addProperty("temperature", Double.valueOf(0.3D));
            JsonArray messages = new JsonArray();
            JsonObject sys = new JsonObject();
            sys.addProperty("role", "system");
            sys.addProperty("content", "你是 RBMK 核反应堆安全诊断专家。回答必须简短、可执行。");
            messages.add(sys);
            JsonObject user = new JsonObject();
            user.addProperty("role", "user");
            user.addProperty("content", prompt + "\n读数 JSON: " + stateJson);
            messages.add(user);
            root.add("messages", messages);

            String body = AiDiagnoseThread.post(root.toString());
            String answer = AiDiagnoseThread.extract(body);
            lines.add(new CmdLine(MessageTerminalCommand.C_OK, "[AI] 专家诊断:"));
            String[] parts = answer.split("\\r?\\n");
            int added = 0;
            for (int i = 0; i < parts.length && added < 10; ++i) {
                String line = parts[i].trim();
                if (line.isEmpty()) {
                    continue;
                }
                lines.add(new CmdLine(MessageTerminalCommand.C_OUT, "  " + line));
                ++added;
            }
            ok = true;
        }
        catch (Throwable t) {
            lines.clear();
            lines.add(new CmdLine(MessageTerminalCommand.C_ERR, ERROR_TEXT));
        }
        final boolean success = ok;
        final List<CmdLine> result = lines;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null) {
            mc.addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    if (AiDiagnoseThread.this.callback != null) {
                        AiDiagnoseThread.this.callback.onResult(success, result);
                    }
                }
            });
        } else if (this.callback != null) {
            this.callback.onResult(success, result);
        }
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.1f", Double.valueOf(v));
    }

    private static String post(String jsonBody) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(RbmkConfig.aiApiUrl).openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setRequestProperty("Authorization", "Bearer " + RbmkConfig.aiApiKey);
        byte[] payload = jsonBody.getBytes(StandardCharsets.UTF_8);
        OutputStream os = conn.getOutputStream();
        try {
            os.write(payload);
        }
        finally {
            os.close();
        }
        int code = conn.getResponseCode();
        InputStream is = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
        String body = AiDiagnoseThread.readAll(is);
        if (code < 200 || code >= 300) {
            throw new java.io.IOException("HTTP " + code);
        }
        return body;
    }

    private static String extract(String body) throws Exception {
        JsonObject root = new JsonParser().parse(body).getAsJsonObject();
        if (root.has("error")) {
            throw new java.io.IOException("api error");
        }
        JsonArray choices = root.getAsJsonArray("choices");
        if (choices == null || choices.size() == 0) {
            throw new java.io.IOException("no choices");
        }
        JsonObject message = choices.get(0).getAsJsonObject().getAsJsonObject("message");
        if (message == null || !message.has("content")) {
            throw new java.io.IOException("no content");
        }
        String content = message.get("content").getAsString();
        if (content == null || content.trim().isEmpty()) {
            throw new java.io.IOException("empty content");
        }
        return content.trim();
    }

    private static String readAll(InputStream is) throws Exception {
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
}
