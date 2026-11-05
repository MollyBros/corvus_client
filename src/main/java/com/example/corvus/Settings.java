package com.example.corvus;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/** Настройки клиента. Хранятся в config/corvus.json. */
public class Settings {
    public static boolean killaura = false;
    public static boolean esp = false;
    public static boolean targetHud = true;
    public static boolean moduleList = true;
    public static boolean infoPanel = true;
    public static Theme theme = Theme.BLUE;
    public static int targetStyle = 0; // 0 = Classic, 1 = Compact

    public static double attackRange = 4.0;
    public static double espRange = 64.0;
    public static double aimGain = 0.30;
    public static double aimNoise = 0.12;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("corvus.json");

    private static class Data {
        boolean targetHud = true, moduleList = true, infoPanel = true;
        String theme = "BLUE";
        int targetStyle = 0;
        double attackRange = 4.0, espRange = 64.0, aimGain = 0.30, aimNoise = 0.12;
    }

    public static void load() {
        try {
            if (!Files.exists(FILE)) return;
            Data d = GSON.fromJson(Files.readString(FILE), Data.class);
            if (d == null) return;
            targetHud = d.targetHud;
            moduleList = d.moduleList;
            infoPanel = d.infoPanel;
            targetStyle = Math.max(0, Math.min(1, d.targetStyle));
            try { theme = Theme.valueOf(d.theme); } catch (Exception e) { theme = Theme.BLUE; }
            attackRange = d.attackRange;
            espRange = d.espRange;
            aimGain = d.aimGain;
            aimNoise = d.aimNoise;
        } catch (Exception ignored) {}
    }

    public static void save() {
        try {
            Data d = new Data();
            d.targetHud = targetHud;
            d.moduleList = moduleList;
            d.infoPanel = infoPanel;
            d.targetStyle = targetStyle;
            d.theme = theme.name();
            d.attackRange = attackRange;
            d.espRange = espRange;
            d.aimGain = aimGain;
            d.aimNoise = aimNoise;
            Files.writeString(FILE, GSON.toJson(d));
        } catch (Exception ignored) {}
    }
}
