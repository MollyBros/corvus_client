package com.example.corvus;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Меню Corvus Client. Полностью самостоятельная отрисовка (без ванильных виджетов).
 * Мышь и клавиши опрашиваются напрямую через GLFW, поэтому код не зависит
 * от сигнатур обработчиков ввода в конкретной версии Minecraft.
 */
public class CorvusScreen extends Screen {
    private static final int W = 380, H = 250, SIDE = 96;
    private static final String[] TABS = {"Combat", "Visuals", "HUD", "Server", "Theme"};
    private static final String[] SUBS = {"Aim and attack", "ESP", "Overlay elements", "Item binds", "Colors"};

    private int tab = 0;
    private float tabAnim = -1;
    private float open = 0;
    private float alpha = 0;
    private long lastNs = 0;
    private float k = 0.2f;

    private boolean down, pressed, prevDown;
    private boolean rsArmed = false;
    private String activeSlider = null;
    private int mx, my;
    private DrawContext g;
    private final Map<String, Float> anim = new HashMap<>();

    public CorvusScreen() {
        super(Text.literal("Corvus Client"));
    }

    // ================= основной цикл =================
    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // фон рисуем сами в render()
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        long handle = mc.getWindow().getHandle();

        long now = System.nanoTime();
        float dt = lastNs == 0 ? 0.016f : Math.min(0.1f, (now - lastNs) / 1e9f);
        lastNs = now;
        k = 1f - (float) Math.exp(-dt * 14f);
        open += (1f - open) * (1f - (float) Math.exp(-dt * 10f));
        alpha = Math.min(1f, open * 1.15f);

        // ввод
        g = ctx;
        mx = mouseX;
        my = mouseY;
        down = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        pressed = down && !prevDown;
        prevDown = down;
        if (!down) activeSlider = null;

        // Right Shift закрывает меню (после того, как клавишу отпустили при открытии)
        boolean rs = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
        if (!rs) rsArmed = true;
        else if (rsArmed) { mc.setScreen(null); return; }

        int wx = (width - W) / 2;
        int wy = (height - H) / 2 + (int) ((1f - open) * 14);
        Theme th = Settings.theme;
        int base = th.panel & 0xFFFFFF;

        // затемнение экрана
        ctx.fill(0, 0, width, height, ((int) (150 * alpha) << 24));

        // тень и окно
        for (int i = 4; i >= 1; i--) rrect(wx - i, wy - i, W + i * 2, H + i * 2, 8 + i, 0x14000000);
        rrect(wx, wy, W, H, 8, 0xFF000000 | mix(base, 0xFFFFFF, 0.03f));
        // боковая панель
        int side = 0xFF000000 | mix(base, 0x000000, 0.35f);
        rrect(wx, wy, SIDE, H, 8, side);
        fill(wx + SIDE - 8, wy, wx + SIDE, wy + H, side);
        fill(wx + SIDE, wy + 8, wx + SIDE + 1, wy + H - 8, 0xFF000000 | mix(base, 0xFFFFFF, 0.12f));

        // логотип
        rrect(wx + 14, wy + 14, 18, 18, 5, th.accent);
        text("C", wx + 20, wy + 19, 0xFF0B0B10, false);
        text("CORVUS", wx + 38, wy + 15, 0xFFFFFFFF, true);
        text("CLIENT", wx + 38, wy + 24, th.dim, false);

        // вкладки
        int tabY0 = wy + 50;
        float target = tabY0 + tab * 26;
        if (tabAnim < 0) tabAnim = target;
        tabAnim += (target - tabAnim) * k;
        rrect(wx + 8, Math.round(tabAnim), SIDE - 16, 22, 5, (th.accent & 0xFFFFFF) | 0x38000000);
        rrect(wx + 8, Math.round(tabAnim) + 4, 2, 14, 1, th.accent);
        for (int i = 0; i < TABS.length; i++) {
            int ty = tabY0 + i * 26;
            boolean hover = in(wx + 8, ty, SIDE - 16, 22);
            if (hover && pressed) tab = i;
            int col = i == tab ? 0xFFFFFFFF : hover ? 0xFFDDE6F5 : th.dim;
            text(TABS[i], wx + 20, ty + 7, col, i == tab);
        }
        text("v1.0.0", wx + 14, wy + H - 16, th.dim, false);

        // заголовок
        int cx = wx + SIDE + 16;
        int cw = W - SIDE - 32;
        text(TABS[tab], cx, wy + 14, 0xFFFFFFFF, true);
        text(SUBS[tab], cx + textRenderer.getWidth(TABS[tab]) + 8, wy + 14, th.dim, false);
        rrect(cx, wy + 28, 22, 2, 1, th.accent);

        int y = wy + 40;
        switch (tab) {
            case 0 -> tabCombat(cx, y, cw);
            case 1 -> tabVisuals(cx, y, cw);
            case 2 -> tabHud(cx, y, cw);
            case 3 -> tabServer(cx, y, cw);
            default -> tabTheme(cx, y, cw);
        }

        String hint = "Right Shift / Esc - close";
        text(hint, wx + W - 12 - textRenderer.getWidth(hint), wy + H - 14, th.dim, false);
    }

    // ================= вкладки =================
    private void tabCombat(int x, int y, int w) {
        boolean ka = toggleRow("ka", "KillAura", "Auto-attack with smooth aim", x, y, w, Settings.killaura);
        if (ka != Settings.killaura) {
            Settings.killaura = ka;
            Rotations.reset();
        }
        y += 36;
        Settings.attackRange = slider("range", "Attack range", x, y, w, 3.0, 6.0, Settings.attackRange, "%.1f");
        y += 40;
        Settings.aimGain = slider("gain", "Aim speed", x, y, w, 0.10, 0.60, Settings.aimGain, "%.2f");
        y += 40;
        Settings.aimNoise = slider("noise", "Aim noise", x, y, w, 0.0, 0.5, Settings.aimNoise, "%.2f");
    }

    private void tabVisuals(int x, int y, int w) {
        Settings.esp = toggleRow("esp", "ESP", "Glow through walls", x, y, w, Settings.esp);
        y += 36;
        Settings.espRange = slider("esprange", "ESP range", x, y, w, 16.0, 128.0, Settings.espRange, "%.0f");
    }

    private void tabHud(int x, int y, int w) {
        Settings.targetHud = toggleRow("thud", "Target HUD", "Name, health and distance", x, y, w, Settings.targetHud);
        y += 36;
        String[] styles = {"Classic", "Compact"};
        if (choiceRow("Target HUD style", styles[Settings.targetStyle], x, y, w))
            Settings.targetStyle = (Settings.targetStyle + 1) % styles.length;
        y += 36;
        Settings.moduleList = toggleRow("mlist", "Module list", "Enabled modules on screen", x, y, w, Settings.moduleList);
        y += 36;
        Settings.infoPanel = toggleRow("info", "Info panel", "Nick, FPS, ping and coordinates", x, y, w, Settings.infoPanel);
    }

    private void tabServer(int x, int y, int w) {
        int base = Settings.theme.panel & 0xFFFFFF;
        int card = 0xFF000000 | mix(base, 0xFFFFFF, 0.08f);

        rrect(x, y, w, 30, 6, card);
        text("Add a bind in chat", x + 10, y + 6, 0xFFFFFFFF, false);
        text("/bind add <key> <item>", x + 10, y + 17, Settings.theme.accent, false);
        y += 36;

        List<ServerHelper.Bind> list = new ArrayList<>(ServerHelper.getBinds());
        if (list.isEmpty()) {
            text("No binds yet. Example: /bind add x ender_pearl", x + 2, y + 6, Settings.theme.dim, false);
            return;
        }
        int shown = Math.min(5, list.size());
        for (int i = 0; i < shown; i++) {
            ServerHelper.Bind b = list.get(i);
            rrect(x, y, w, 24, 6, card);
            String key = b.key.toUpperCase();
            text(key, x + 10, y + 8, Settings.theme.accent, true);
            int kw = textRenderer.getWidth(key);
            text("->", x + 10 + kw + 6, y + 8, Settings.theme.dim, false);
            text(fit(b.item, w - kw - 80), x + 10 + kw + 24, y + 8, 0xFFFFFFFF, false);

            int bx = x + w - 22, by = y + 4;
            boolean hover = in(bx, by, 16, 16);
            rrect(bx, by, 16, 16, 4, 0xFF000000 | mix(base, hover ? 0xFF5566 : 0xFFFFFF, hover ? 0.55f : 0.14f));
            text("x", bx + 6, by + 4, 0xFFFFFFFF, false);
            if (hover && pressed) ServerHelper.removeBind(b.key);
            y += 28;
        }
        if (list.size() > shown) text("+" + (list.size() - shown) + " more", x + 2, y + 2, Settings.theme.dim, false);
    }

    private void tabTheme(int x, int y, int w) {
        Theme[] all = Theme.values();
        int sw = (w - 16) / 3, sh = 56;
        for (int i = 0; i < all.length; i++) {
            Theme t = all[i];
            int sx = x + (i % 3) * (sw + 8);
            int sy = y + (i / 3) * (sh + 8);
            boolean sel = t == Settings.theme;
            boolean hover = in(sx, sy, sw, sh);
            int cardBase = t.panel & 0xFFFFFF;
            int card = 0xFF000000 | mix(cardBase, 0xFFFFFF, hover ? 0.14f : 0.08f);

            if (sel) rrect(sx, sy, sw, sh, 6, t.accent);
            rrect(sx + (sel ? 1 : 0), sy + (sel ? 1 : 0), sw - (sel ? 2 : 0), sh - (sel ? 2 : 0), 6, card);
            rrect(sx + sw / 2 - 9, sy + 11, 18, 18, 9, t.accent);
            int nw = textRenderer.getWidth(t.label);
            text(t.label, sx + (sw - nw) / 2, sy + 38, sel ? 0xFFFFFFFF : t.dim, sel);
            if (hover && pressed) Settings.theme = t;
        }
        text("Applies to the menu and the HUD", x + 2, y + 2 * (sh + 8) + 4, Settings.theme.dim, false);
    }

    // ================= компоненты =================
    private boolean toggleRow(String id, String label, String desc, int x, int y, int w, boolean val) {
        int base = Settings.theme.panel & 0xFFFFFF;
        boolean hover = in(x, y, w, 30);
        rrect(x, y, w, 30, 6, 0xFF000000 | mix(base, 0xFFFFFF, hover ? 0.14f : 0.08f));
        text(label, x + 10, y + 6, 0xFFFFFFFF, true);
        text(desc, x + 10, y + 17, Settings.theme.dim, false);

        float goal = val ? 1f : 0f;
        float a = anim.getOrDefault(id, goal);
        a += (goal - a) * k;
        anim.put(id, a);

        int sx = x + w - 40, sy = y + 8;
        int off = mix(base, 0xFFFFFF, 0.22f);
        rrect(sx, sy, 28, 14, 7, 0xFF000000 | mix(off, Settings.theme.accent & 0xFFFFFF, a));
        rrect(sx + 2 + Math.round(a * 14), sy + 2, 10, 10, 5, 0xFFFFFFFF);

        if (hover && pressed) val = !val;
        return val;
    }

    /** Строка-выбор: клик переключает вариант, текущее значение справа. */
    private boolean choiceRow(String label, String value, int x, int y, int w) {
        int base = Settings.theme.panel & 0xFFFFFF;
        boolean hover = in(x, y, w, 30);
        rrect(x, y, w, 30, 6, 0xFF000000 | mix(base, 0xFFFFFF, hover ? 0.14f : 0.08f));
        text(label, x + 10, y + 11, 0xFFFFFFFF, true);
        text(value, x + w - 10 - textRenderer.getWidth(value), y + 11, Settings.theme.accent, true);
        return hover && pressed;
    }

    private double slider(String id, String label, int x, int y, int w, double min, double max, double val, String fmt) {
        int base = Settings.theme.panel & 0xFFFFFF;
        boolean hover = in(x, y, w, 34);
        int tx = x + 10, tw = w - 20, ty = y + 24;

        if (pressed && hover) activeSlider = id;
        if (id.equals(activeSlider) && down) {
            double t = Math.max(0, Math.min(1, (mx - tx) / (double) tw));
            val = min + (max - min) * t;
        }

        rrect(x, y, w, 34, 6, 0xFF000000 | mix(base, 0xFFFFFF, hover ? 0.14f : 0.08f));
        text(label, x + 10, y + 6, 0xFFFFFFFF, true);
        String v = String.format(fmt, val);
        text(v, x + w - 10 - textRenderer.getWidth(v), y + 6, Settings.theme.accent, true);

        double t = (val - min) / (max - min);
        int fw = (int) Math.round(tw * t);
        rrect(tx, ty, tw, 4, 2, 0xFF000000 | mix(base, 0xFFFFFF, 0.22f));
        if (fw > 0) rrect(tx, ty, Math.max(fw, 4), 4, 2, Settings.theme.accent);
        rrect(tx + fw - 4, ty - 2, 8, 8, 4, 0xFFFFFFFF);
        return val;
    }

    // ================= рисование =================
    private boolean in(int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private int al(int c) {
        int a = (int) (((c >>> 24) & 0xFF) * alpha);
        return (a << 24) | (c & 0xFFFFFF);
    }

    private void fill(int x1, int y1, int x2, int y2, int c) {
        g.fill(x1, y1, x2, y2, al(c));
    }

    /** Скруглённый прямоугольник (приближение кругом). */
    private void rrect(int x, int y, int w, int h, int r, int color) {
        int c = al(color);
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 0) {
            g.fill(x, y, x + w, y + h, c);
            return;
        }
        g.fill(x, y + r, x + w, y + h - r, c);
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = r - (int) Math.round(Math.sqrt(r * r - dy * dy));
            g.fill(x + inset, y + i, x + w - inset, y + i + 1, c);
            g.fill(x + inset, y + h - i - 1, x + w - inset, y + h - i, c);
        }
    }

    private void text(String s, int x, int y, int color, boolean shadow) {
        int c = al(color);
        if ((c >>> 24) < 8) return; // слишком прозрачный текст игра рисует непрозрачным
        g.drawText(textRenderer, s, x, y, c, shadow);
    }

    private String fit(String s, int maxW) {
        if (textRenderer.getWidth(s) <= maxW) return s;
        while (s.length() > 1 && textRenderer.getWidth(s + "..") > maxW) s = s.substring(0, s.length() - 1);
        return s + "..";
    }

    /** Смешивание двух RGB-цветов (t = 0 -> a, t = 1 -> b). */
    private static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int gg = (int) (((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = (int) ((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return (r << 16) | (gg << 8) | bl;
    }

    @Override
    public void removed() {
        Settings.save();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
