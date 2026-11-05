package com.example.corvus;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class CorvusClient implements ClientModInitializer {
    // для HUD
    public static LivingEntity target = null;
    public static long targetUntil = 0;

    private static KeyBinding guiKey;
    private static KeyBinding killauraKey;
    private static KeyBinding espKey;

    @Override
    public void onInitializeClient() {
        Settings.load();

        KeyBinding.Category cat = KeyBinding.Category.create(Identifier.of("corvus", "main"));
        guiKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.corvus.gui", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, cat));
        killauraKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.corvus.killaura", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, cat));
        espKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.corvus.esp", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, cat));

        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        HudOverlay.register();
        ServerHelper.register();
    }

    private void tick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        ServerHelper.tick(mc);

        while (guiKey.wasPressed()) {
            if (mc.currentScreen == null) mc.setScreen(new CorvusScreen());
        }
        while (killauraKey.wasPressed()) {
            Settings.killaura = !Settings.killaura;
            Rotations.reset();
            mc.player.sendMessage(Text.literal("KillAura: " + (Settings.killaura ? "ON" : "OFF")), true);
        }
        while (espKey.wasPressed()) {
            Settings.esp = !Settings.esp;
            mc.player.sendMessage(Text.literal("ESP: " + (Settings.esp ? "ON" : "OFF")), true);
        }

        double range = Settings.attackRange;
        double aimRange = range + 1.0;

        LivingEntity closest = null;
        double closestDist = Double.MAX_VALUE;

        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof LivingEntity le) || e == mc.player || e instanceof ArmorStandEntity) continue;

            double dist = mc.player.distanceTo(e);

            // ESP: клиентское свечение
            e.setGlowing(Settings.esp && le.isAlive() && dist <= Settings.espRange);

            // выбор ближайшей цели
            if (le.isAlive() && dist <= aimRange && dist < closestDist
                    && (le instanceof PlayerEntity || !le.getType().getSpawnGroup().isPeaceful())) {
                closest = le;
                closestDist = dist;
            }
        }

        if (Settings.killaura && closest != null) {
            boolean aimed = Rotations.aimAt(mc, closest);
            if (aimed && closestDist <= range && mc.player.getAttackCooldownProgress(0.5f) >= 1.0f) {
                mc.interactionManager.attackEntity(mc.player, closest);
                mc.player.swingHand(Hand.MAIN_HAND);
            }
            target = closest;
            targetUntil = System.currentTimeMillis() + 1500;
        } else {
            Rotations.reset();
        }
    }
}
