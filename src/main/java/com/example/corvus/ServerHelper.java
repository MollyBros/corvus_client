package com.example.corvus;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.util.Hand;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Бинды на предметы: нажал клавишу -> предмет из хотбара (по id или по части названия)
 * берётся в руку, используется, и слот возвращается обратно.
 *
 * /bind add <клавиша> <предмет>   например: /bind add x ender_pearl
 *                                 или:      /bind add c дезориентация
 * /bind remove <клавиша>
 * /bind list
 * /bind clear
 */
public class ServerHelper {
    public static class Bind {
        public String key;
        public String item;
    }

    private static final Gson GSON = new Gson();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("corvus-binds.json");
    private static final List<Bind> binds = new ArrayList<>();
    private static final Set<Integer> down = new HashSet<>();

    private static int restoreSlot = -1;
    private static int restoreIn = 0;

    public static void register() {
        load();
        registerCommands();
    }

    // ---------- тик ----------
    public static void tick(MinecraftClient mc) {
        if (mc.player == null || mc.interactionManager == null) return;
        PlayerInventory inv = mc.player.getInventory();

        if (restoreIn > 0 && --restoreIn == 0 && restoreSlot >= 0) {
            inv.setSelectedSlot(restoreSlot);
            restoreSlot = -1;
        }
        if (mc.currentScreen != null) { down.clear(); return; }

        long handle = mc.getWindow().getHandle();
        for (Bind b : binds) {
            int code = codeOf(b.key);
            if (code < 0) continue;
            boolean pressed = GLFW.glfwGetKey(handle, code) == GLFW.GLFW_PRESS;
            if (pressed) {
                if (down.add(code)) use(mc, b.item);
            } else {
                down.remove(code);
            }
        }
    }

    private static void use(MinecraftClient mc, String query) {
        if (restoreSlot >= 0) return; // предыдущее использование ещё не завершено
        PlayerInventory inv = mc.player.getInventory();
        int slot = findSlot(inv, query);
        if (slot < 0) {
            mc.player.sendMessage(Text.literal("Не найдено в хотбаре: " + query), true);
            return;
        }
        int prev = inv.getSelectedSlot();
        if (slot != prev) {
            inv.setSelectedSlot(slot);
            restoreSlot = prev;
            restoreIn = 2;
        }
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
    }

    private static int findSlot(PlayerInventory inv, String query) {
        String q = query.toLowerCase();
        for (int i = 0; i < 9; i++) {
            ItemStack st = inv.getStack(i);
            if (st.isEmpty()) continue;
            String id = Registries.ITEM.getId(st.getItem()).toString();
            String name = st.getName().getString().toLowerCase();
            if (id.contains(q) || name.contains(q)) return i;
        }
        return -1;
    }

    private static int codeOf(String key) {
        try {
            return InputUtil.fromTranslationKey("key.keyboard." + key.toLowerCase()).getCode();
        } catch (IllegalArgumentException e) {
            return -1;
        }
    }

    // ---------- команды ----------
    private static void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(ClientCommandManager.literal("bind")
                .then(ClientCommandManager.literal("add")
                    .then(ClientCommandManager.argument("key", StringArgumentType.word())
                        .then(ClientCommandManager.argument("item", StringArgumentType.greedyString())
                            .executes(ctx -> {
                                String key = StringArgumentType.getString(ctx, "key");
                                String item = StringArgumentType.getString(ctx, "item");
                                if (codeOf(key) < 0) {
                                    ctx.getSource().sendFeedback(Text.literal("Неизвестная клавиша: " + key));
                                    return 0;
                                }
                                binds.removeIf(b -> b.key.equalsIgnoreCase(key));
                                Bind b = new Bind();
                                b.key = key;
                                b.item = item;
                                binds.add(b);
                                save();
                                ctx.getSource().sendFeedback(Text.literal("Бинд: " + key + " -> " + item));
                                return 1;
                            }))))
                .then(ClientCommandManager.literal("remove")
                    .then(ClientCommandManager.argument("key", StringArgumentType.word())
                        .executes(ctx -> {
                            String key = StringArgumentType.getString(ctx, "key");
                            boolean removed = binds.removeIf(b -> b.key.equalsIgnoreCase(key));
                            save();
                            ctx.getSource().sendFeedback(Text.literal(removed ? "Удалён: " + key : "Нет такого бинда"));
                            return 1;
                        })))
                .then(ClientCommandManager.literal("list")
                    .executes(ctx -> {
                        if (binds.isEmpty()) ctx.getSource().sendFeedback(Text.literal("Биндов нет"));
                        for (Bind b : binds)
                            ctx.getSource().sendFeedback(Text.literal(b.key + " -> " + b.item));
                        return 1;
                    }))
                .then(ClientCommandManager.literal("clear")
                    .executes(ctx -> {
                        binds.clear();
                        save();
                        ctx.getSource().sendFeedback(Text.literal("Бинды очищены"));
                        return 1;
                    }))));
    }

    // ---------- доступ для меню ----------
    public static List<Bind> getBinds() {
        return binds;
    }

    public static void removeBind(String key) {
        binds.removeIf(b -> b.key.equalsIgnoreCase(key));
        save();
    }

    // ---------- сохранение ----------
    private static void save() {
        try {
            Files.writeString(FILE, GSON.toJson(binds));
        } catch (IOException ignored) {}
    }

    private static void load() {
        try {
            if (!Files.exists(FILE)) return;
            Type t = new TypeToken<List<Bind>>() {}.getType();
            List<Bind> loaded = GSON.fromJson(Files.readString(FILE), t);
            if (loaded != null) binds.addAll(loaded);
        } catch (Exception ignored) {}
    }
}
