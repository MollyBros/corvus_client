package com.example.corvus;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;

/**
 * Target HUD: карточка со скруглёнными углами, аватар, ник, дистанция,
 * HP-полоска с "хвостом" урона, поглощение, броня и метка Winning/Losing.
 * Плавно появляется и исчезает.
 */
public class TargetHud {
    private static LivingEntity shown = null;
    private static float alpha = 0f;
    private static float hpAnim = 0f;    // основная полоска
    private static float trailAnim = 0f; // "хвост" урона
    private static long lastNs = 0;
    private static DrawContext g;

    private static final int W = 160, H = 52;

    public static void render(DrawContext ctx, MinecraftClient mc) {
        long now = System.nanoTime();
        float dt = lastNs == 0 ? 0.016f : Math.min(0.1f, (now - lastNs) / 1e9f);
        lastNs = now;

        // актуальная цель
        LivingEntity t = CorvusClient.target;
        boolean active = t != null && t.isAlive() && System.currentTimeMillis() < CorvusClient.targetUntil;
        if (t != null && t != shown) {
            shown = t;
            hpAnim = trailAnim = t.getHealth();
        }
        if (shown == null) return;

        float fade = 1f - (float) Math.exp(-dt * (active ? 14f : 8f));
        alpha += ((active ? 1f : 0f) - alpha) * fade;
        if (!active && alpha < 0.02f) { shown = null; alpha = 0; return; }

        g = ctx;
        TextRenderer tr = mc.textRenderer;
        Theme th = Settings.theme;
        int base = th.panel & 0xFFFFFF;

        float hp = Math.max(0f, shown.getHealth());
        float max = Math.max(1f, shown.getMaxHealth());
        float abs = Math.max(0f, shown.getAbsorptionAmount());

        // анимации полоски: основная быстрая, хвост медленный и догоняет
        hpAnim += (hp - hpAnim) * (1f - (float) Math.exp(-dt * 12f));
        if (trailAnim > hpAnim) trailAnim += (hpAnim - trailAnim) * (1f - (float) Math.exp(-dt * 2.5f));
        else trailAnim = hpAnim;

        if (Settings.targetStyle == 1) {
            drawCompact(mc, tr, th, base, hp, max, abs);
            return;
        }

        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        int x = sw / 2 + 26;
        int y = sh / 2 + 26 + Math.round((1f - alpha) * 6f);

        // тень + карточка
        for (int i = 3; i >= 1; i--) rrect(x - i, y - i, W + i * 2, H + i * 2, 7 + i, 0x12000000);
        rrect(x, y, W, H, 7, 0xFF000000 | mix(base, 0x000000, 0.15f));
        rrect(x, y, 3, H, 1, th.accent);

        // аватар (буква ника в плитке цвета темы)
        String name = shown.getName().getString();
        rrect(x + 10, y + 9, 34, 34, 6, 0xFF000000 | mix(base, th.accent & 0xFFFFFF, 0.28f));
        String ch = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();
        text(tr, ch, x + 27 - tr.getWidth(ch) / 2, y + 22, 0xFFFFFFFF, true);

        // ник и дистанция
        int tx = x + 52;
        double dist = mc.player.distanceTo(shown);
        String d = String.format("%.1fm", dist);
        int dw = tr.getWidth(d);
        text(tr, fit(tr, name, W - 52 - dw - 16), tx, y + 8, 0xFFFFFFFF, true);
        text(tr, d, x + W - 8 - dw, y + 8, th.dim, false);

        // HP-полоска
        int bx = tx, by = y + 22, bw = W - 52 - 10, bh = 7;
        rrect(bx, by, bw, bh, 3, 0xFF000000 | mix(base, 0xFFFFFF, 0.14f));
        float trailR = Math.min(1f, trailAnim / max);
        float hpR = Math.min(1f, hpAnim / max);
        int trailW = Math.round(bw * trailR);
        int hpW = Math.round(bw * hpR);
        if (trailW > hpW) rrect(bx, by, Math.max(trailW, 4), bh, 3, 0xCCFFFFFF);          // хвост урона
        if (hpW > 0) rrect(bx, by, Math.max(hpW, 4), bh, 3, hpColor(hpR, th.accent));   // здоровье
        if (abs > 0) {                                                                    // поглощение
            int absW = Math.min(bw, Math.round(bw * Math.min(1f, abs / max)));
            rrect(bx, by + bh - 2, Math.max(absW, 2), 2, 1, 0xFFFFD34D);
        }

        // HP-текст, броня, метка
        String hpText = String.format("%.1f", hp) + (abs > 0 ? " +" + String.format("%.0f", abs) : "") + " HP";
        text(tr, hpText, tx, y + 34, 0xFFFFFFFF, false);

        int armor = shown.getArmor();
        String ar = armor + " AR";
        text(tr, ar, tx + tr.getWidth(hpText) + 8, y + 34, th.dim, false);

        boolean winning = mc.player.getHealth() + mc.player.getAbsorptionAmount() >= hp + abs;
        String tag = winning ? "WIN" : "LOSE";
        int tagW = tr.getWidth(tag);
        int tagC = winning ? 0xFF3DDC84 : 0xFFFF4D5E;
        rrect(x + W - 8 - tagW - 6, y + H - 17, tagW + 6, 11, 3, (tagC & 0xFFFFFF) | 0x40000000);
        text(tr, tag, x + W - 8 - tagW - 3, y + H - 15, tagC, false);
    }


    // ================= Стиль 2: Compact =================
    // Узкая карточка по центру под прицелом: ник, толстая HP-полоска с числом,
    // ряд экипировки (4 брони + 2 руки) с полосками прочности.
    private static void drawCompact(MinecraftClient mc, TextRenderer tr, Theme th, int base,
                                    float hp, float max, float abs) {
        int w = 144, h = 66;
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        int x = sw / 2 - w / 2;
        int y = sh / 2 + 32 + Math.round((1f - alpha) * 6f);

        for (int i = 3; i >= 1; i--) rrect(x - i, y - i, w + i * 2, h + i * 2, 7 + i, 0x12000000);
        rrect(x - 1, y - 1, w + 2, h + 2, 7, (th.accent & 0xFFFFFF) | 0x90000000); // рамка
        rrect(x, y, w, h, 6, 0xFF000000 | mix(base, 0x000000, 0.15f));

        // ник + дистанция
        String name = shown.getName().getString();
        String d = String.format("%.1fm", mc.player.distanceTo(shown));
        int dw = tr.getWidth(d);
        text(tr, fit(tr, name, w - dw - 24), x + 8, y + 7, 0xFFFFFFFF, true);
        text(tr, d, x + w - 8 - dw, y + 7, th.dim, false);

        // HP-полоска с числом внутри
        int bx = x + 8, by = y + 20, bw = w - 16, bh = 11;
        rrect(bx, by, bw, bh, 4, 0xFF000000 | mix(base, 0xFFFFFF, 0.14f));
        float trailR = Math.min(1f, trailAnim / max);
        float hpR = Math.min(1f, hpAnim / max);
        int trailW = Math.round(bw * trailR);
        int hpW = Math.round(bw * hpR);
        if (trailW > hpW) rrect(bx, by, Math.max(trailW, 4), bh, 4, 0xCCFFFFFF);
        if (hpW > 0) rrect(bx, by, Math.max(hpW, 4), bh, 4, 0xFF000000 | healthColor(hpR));
        if (abs > 0) {
            int absW = Math.min(bw, Math.round(bw * Math.min(1f, abs / max)));
            rrect(bx, by + bh - 2, Math.max(absW, 2), 2, 1, 0xFFFFD34D);
        }
        String hpText = String.format("%.1f", hp) + (abs > 0 ? " +" + String.format("%.0f", abs) : "");
        text(tr, hpText, bx + bw / 2 - tr.getWidth(hpText) / 2, by + 2, 0xFFFFFFFF, true);

        // экипировка
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
                EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};
        int step = 20;
        int ix0 = x + (w - (slots.length * step - 4)) / 2;
        int iy = y + 38;
        boolean drawItems = alpha > 0.85f; // иконки предметов не умеют полупрозрачность
        for (int i = 0; i < slots.length; i++) {
            int ix = ix0 + i * step;
            rrect(ix - 1, iy - 1, 18, 18, 3, 0xFF000000 | mix(base, 0xFFFFFF, 0.10f));
            ItemStack st = shown.getEquippedStack(slots[i]);
            if (st.isEmpty()) continue;
            if (drawItems) g.drawItem(st, ix, iy);
            if (st.isDamageable() && st.getMaxDamage() > 0) {
                float left = 1f - (float) st.getDamage() / st.getMaxDamage();
                rrect(ix, iy + 17, 16, 2, 1, 0xFF000000 | mix(base, 0x000000, 0.3f));
                rrect(ix, iy + 17, Math.max(1, Math.round(16 * left)), 2, 1, 0xFF000000 | healthColor(left));
            }
        }
    }

    /** Зелёный -> жёлтый -> красный по доле здоровья. */
    private static int healthColor(float r) {
        int green = 0x3DDC84, yellow = 0xFFD34D, red = 0xFF4D5E;
        if (r >= 0.5f) return mix(yellow, green, (r - 0.5f) / 0.5f);
        return mix(red, yellow, r / 0.5f);
    }

    /** Цвет полоски: при высоком HP - цвет темы, при низком - уходит в красный. */
    private static int hpColor(float r, int accent) {
        int red = 0xFF4D5E;
        if (r >= 0.5f) return 0xFF000000 | (accent & 0xFFFFFF);
        float t = 1f - (r / 0.5f); // 0 при 50% HP, 1 при 0%
        return 0xFF000000 | mix(accent & 0xFFFFFF, red, t);
    }

    // ---------- рисование ----------
    private static int al(int c) {
        int a = (int) (((c >>> 24) & 0xFF) * alpha);
        return (a << 24) | (c & 0xFFFFFF);
    }

    private static void rrect(int x, int y, int w, int h, int r, int color) {
        int c = al(color);
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 0) { g.fill(x, y, x + w, y + h, c); return; }
        g.fill(x, y + r, x + w, y + h - r, c);
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = r - (int) Math.round(Math.sqrt(r * r - dy * dy));
            g.fill(x + inset, y + i, x + w - inset, y + i + 1, c);
            g.fill(x + inset, y + h - i - 1, x + w - inset, y + h - i, c);
        }
    }

    private static void text(TextRenderer tr, String s, int x, int y, int color, boolean shadow) {
        int c = al(color);
        if ((c >>> 24) < 8) return;
        g.drawText(tr, s, x, y, c, shadow);
    }

    private static String fit(TextRenderer tr, String s, int maxW) {
        if (tr.getWidth(s) <= maxW) return s;
        while (s.length() > 1 && tr.getWidth(s + "..") > maxW) s = s.substring(0, s.length() - 1);
        return s + "..";
    }

    private static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int gg = (int) (((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = (int) ((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return (r << 16) | (gg << 8) | bl;
    }
}
