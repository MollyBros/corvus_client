package com.example.corvus;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

/**
 * "Человекоподобное" наведение: пружина с инерцией + шум + дрейф точки прицеливания
 * + квантование под шаг мыши (GCD). Это не настоящая нейросеть, а лёгкая модель;
 * метод aimAt() можно заменить на вызов своей обученной модели.
 */
public class Rotations {
    private static final Random RNG = new Random();

    private static float yawVel, pitchVel;
    private static double offX, offY, offZ;
    private static int offTimer = 0;

    // параметры (можно крутить)
    private static final float DAMPING = 0.50f;   // инерция
    private static final float MAX_STEP = 35f;    // макс. градусов за тик
    private static final float AIMED_THRESHOLD = 10f; // допуск для удара (градусы)

    public static void reset() {
        yawVel = 0;
        pitchVel = 0;
    }

    /** Один шаг наведения. Возвращает true, если уже смотрим достаточно точно на цель. */
    public static boolean aimAt(MinecraftClient mc, LivingEntity t) {
        ClientPlayerEntity p = mc.player;
        if (p == null) return false;

        // точка прицеливания медленно "плавает" внутри хитбокса
        if (offTimer-- <= 0) {
            double w = t.getWidth() * 0.25;
            offX = (RNG.nextDouble() * 2 - 1) * w;
            offZ = (RNG.nextDouble() * 2 - 1) * w;
            offY = (RNG.nextDouble() * 2 - 1) * 0.15;
            offTimer = 8 + RNG.nextInt(12);
        }
        Vec3d aim = new Vec3d(t.getX() + offX, t.getY() + t.getHeight() * 0.65 + offY, t.getZ() + offZ);
        Vec3d eye = p.getEyePos();

        double dx = aim.x - eye.x, dy = aim.y - eye.y, dz = aim.z - eye.z;
        float targetYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float targetPitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));

        float dYaw = MathHelper.wrapDegrees(targetYaw - p.getYaw());
        float dPitch = targetPitch - p.getPitch();

        // пружина: инерция + доводка + шум
        yawVel = yawVel * DAMPING + dYaw * (float) Settings.aimGain + (float) RNG.nextGaussian() * (float) Settings.aimNoise;
        pitchVel = pitchVel * DAMPING + dPitch * (float) Settings.aimGain + (float) RNG.nextGaussian() * (float) Settings.aimNoise * 0.6f;
        float moveYaw = MathHelper.clamp(yawVel, -MAX_STEP, MAX_STEP);
        float movePitch = MathHelper.clamp(pitchVel, -MAX_STEP, MAX_STEP);

        // квантование под шаг мыши (как у реальной мыши с текущей чувствительностью)
        double sens = mc.options.getMouseSensitivity().getValue() * 0.6 + 0.2;
        float gcd = (float) (sens * sens * sens * 8.0 * 0.15);
        if (gcd > 0) {
            moveYaw = Math.round(moveYaw / gcd) * gcd;
            movePitch = Math.round(movePitch / gcd) * gcd;
        }

        float newYaw = p.getYaw() + moveYaw;
        float newPitch = MathHelper.clamp(p.getPitch() + movePitch, -90f, 90f);
        p.setYaw(newYaw);
        p.setPitch(newPitch);

        return Math.abs(MathHelper.wrapDegrees(targetYaw - newYaw)) < AIMED_THRESHOLD
                && Math.abs(targetPitch - newPitch) < AIMED_THRESHOLD;
    }
}
