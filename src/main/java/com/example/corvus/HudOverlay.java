package com.example.corvus;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public class HudOverlay {
    public static void register() {
        HudElementRegistry.attachElementAfter(
                VanillaHudElements.MISC_OVERLAYS,
                Identifier.of("corvus", "hud"),
                (ctx, tickCounter) -> render(ctx));
    }

    private static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden) return;
        TextRenderer tr = mc.textRenderer;

        drawModuleList(ctx, mc, tr);
        if (Settings.infoPanel) drawInfoPanel(ctx, mc, tr);
        if (Settings.targetHud) TargetHud.render(ctx, mc);
    }

    // ---- Инфо-панель: ник, FPS, пинг, координаты ----
    private static void drawInfoPanel(DrawContext ctx, MinecraftClient mc, TextRenderer tr) {
        Theme th = Settings.theme;
        String nick = mc.player.getName().getString();
        int fps = mc.getCurrentFps();

        int ping = -1;
        if (mc.getNetworkHandler() != null) {
            PlayerListEntry e = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
            if (e != null) ping = e.getLatency();
        }
        String pingText = ping < 0 ? "-" : ping + " ms";
        int pingColor = ping < 0 ? th.dim : ping < 60 ? 0xFF3DDC84 : ping < 150 ? 0xFFFFD34D : 0xFFFF4D5E;
        String xyz = String.format("%.1f  %.1f  %.1f", mc.player.getX(), mc.player.getY(), mc.player.getZ());

        String[] labels = {"FPS", "Ping", "XYZ"};
        String[] values = {String.valueOf(fps), pingText, xyz};
        int[] colors = {0xFFFFFFFF, pingColor, 0xFFFFFFFF};

        int labelW = 0, valueW = 0;
        for (String l : labels) labelW = Math.max(labelW, tr.getWidth(l));
        for (String v : values) valueW = Math.max(valueW, tr.getWidth(v));
        labelW += 8;

        int x = 4, y = 17, pad = 6, line = 11;
        int w = Math.max(tr.getWidth(nick), labelW + valueW) + pad * 2 + 2;
        int h = pad * 2 + line * 4 - 2;

        int base = th.panel & 0xFFFFFF;
        rrect(ctx, x, y, w, h, 5, 0xC8000000 | mixRgb(base, 0x000000, 0.2f));
        rrect(ctx, x, y + 4, 2, h - 8, 1, th.accent);

        int tx = x + pad + 2;
        int ty = y + pad;
        ctx.drawText(tr, nick, tx, ty, th.accent, true);
        for (int i = 0; i < labels.length; i++) {
            int ly = ty + line * (i + 1);
            ctx.drawText(tr, labels[i], tx, ly, th.dim, false);
            ctx.drawText(tr, values[i], tx + labelW, ly, colors[i], false);
        }
    }

    private static void rrect(DrawContext ctx, int x, int y, int w, int h, int r, int c) {
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 0) { ctx.fill(x, y, x + w, y + h, c); return; }
        ctx.fill(x, y + r, x + w, y + h - r, c);
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = r - (int) Math.round(Math.sqrt(r * r - dy * dy));
            ctx.fill(x + inset, y + i, x + w - inset, y + i + 1, c);
            ctx.fill(x + inset, y + h - i - 1, x + w - inset, y + h - i, c);
        }
    }

    private static int mixRgb(int a, int b, float t) {
        int r = (int) (((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = (int) (((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = (int) ((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return (r << 16) | (g << 8) | bl;
    }

    // ---- Список включённых модулей справа сверху + вотермарка ----
    private static void drawModuleList(DrawContext ctx, MinecraftClient mc, TextRenderer tr) {
        ctx.drawText(tr, "Corvus Client", 4, 4, Settings.theme.accent, true);
        if (!Settings.moduleList) return;

        List<String> mods = new ArrayList<>();
        if (Settings.killaura) mods.add("KillAura");
        if (Settings.esp) mods.add("ESP");
        mods.sort((a, b) -> tr.getWidth(b) - tr.getWidth(a));

        int sw = mc.getWindow().getScaledWidth();
        int y = 4;
        for (String m : mods) {
            int w = tr.getWidth(m);
            int x = sw - w - 6;
            ctx.fill(x - 3, y - 1, sw, y + 10, 0x90000000);
            ctx.fill(sw - 2, y - 1, sw, y + 10, Settings.theme.accent); // акцентная полоска
            ctx.drawText(tr, m, x, y + 1, 0xFFFFFFFF, true);
            y += 11;
        }
    }
}
