package dev.soulbound.aim;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Random;

/**
 * Turns the player's view toward a requested point every rendered frame (not every tick), using
 * the same path as mouse input, so rotation is perfectly smooth. Modules call {@link #request}
 * each tick; if nobody asks for ~150 ms the controller lets go.
 */
public final class AimController {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final Random RND = new Random();

    private static Object owner;
    private static AimSettings profile;
    private static long lastRequest;
    private static Vec3d point;
    private static float yawRate, pitchRate;            // deg/s
    private static float carryYaw, carryPitch;          // remainders lost to the sensitivity grid
    private static float overYaw, overPitch;            // decaying overshoot bias
    private static float lastDesiredYaw = Float.NaN, lastDesiredPitch;
    private static final Deque<float[]> history = new ArrayDeque<>(); // time, yaw, pitch
    private static final float[] phase = {RND.nextFloat() * 10, RND.nextFloat() * 10, RND.nextFloat() * 10, RND.nextFloat() * 10};
    private static long lastFrame = System.nanoTime();

    private AimController() {}

    /** Ask to look at {@code target} this tick. */
    public static void request(Object who, Vec3d target, AimSettings settings) {
        owner = who;
        profile = settings;
        point = target;
        lastRequest = System.currentTimeMillis();
    }

    public static boolean isActive() { return point != null && System.currentTimeMillis() - lastRequest < 150; }

    public static boolean isOwnedBy(Object who) { return isActive() && owner == who; }

    /** Degrees between where the player looks and {@code target}. */
    public static float errorTo(Vec3d target) {
        ClientPlayerEntity p = mc.player;
        if (p == null || target == null) return 999;
        float[] a = angles(p.getEyePos(), target);
        return Math.abs(MathHelper.wrapDegrees(a[0] - p.getYaw())) + Math.abs(a[1] - p.getPitch());
    }

    public static float[] angles(Vec3d from, Vec3d to) {
        double dx = to.x - from.x, dy = to.y - from.y, dz = to.z - from.z;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horiz));
        return new float[]{yaw, MathHelper.clamp(pitch, -90f, 90f)};
    }

    /** Called every rendered frame (see MouseMixin). */
    public static void frame() {
        long nowNs = System.nanoTime();
        float dt = Math.min(0.05f, (nowNs - lastFrame) / 1e9f);
        lastFrame = nowNs;
        ClientPlayerEntity p = mc.player;
        if (p == null || dt <= 0) return;
        if (!isActive() || mc.currentScreen != null) {
            yawRate *= (float) Math.exp(-12 * dt);
            pitchRate *= (float) Math.exp(-12 * dt);
            history.clear();
            lastDesiredYaw = Float.NaN;
            return;
        }
        AimSettings s = profile;
        long now = System.currentTimeMillis();

        // where we want to look, delayed by the reaction time
        float[] want = angles(p.getEyePos(), point);
        history.addLast(new float[]{now, want[0], want[1]});
        float reaction = s.reaction.getFloat();
        float[] use = history.peekLast();
        for (float[] h : history) if (now - h[0] >= reaction) use = h;
        while (history.size() > 2 && now - history.peekFirst()[0] > reaction + 300) history.pollFirst();
        float desYaw = use[1], desPitch = use[2];

        // overshoot: a big jump in the target briefly pushes the aim a little past it
        if (!Float.isNaN(lastDesiredYaw)) {
            float jump = MathHelper.wrapDegrees(desYaw - lastDesiredYaw);
            float jumpP = desPitch - lastDesiredPitch;
            float big = Math.abs(MathHelper.wrapDegrees(desYaw - p.getYaw()));
            if (Math.abs(jump) > 8 || (big > 25 && Math.abs(overYaw) < 0.5f && RND.nextFloat() < 0.02f)) {
                overYaw = Math.signum(jump == 0 ? MathHelper.wrapDegrees(desYaw - p.getYaw()) : jump) * big * 0.12f * s.overshoot.getFloat();
                overPitch = jumpP * 0.08f * s.overshoot.getFloat();
            }
        }
        lastDesiredYaw = desYaw;
        lastDesiredPitch = desPitch;
        float decay = (float) Math.exp(-7 * dt);
        overYaw *= decay;
        overPitch *= decay;

        float eY = MathHelper.wrapDegrees(desYaw + overYaw - p.getYaw());
        float eP = desPitch + overPitch - p.getPitch();
        float max = s.speed.getFloat(), maxP = max * s.vertical.getFloat();
        float smooth = s.smoothness.getFloat();

        float dYaw, dPitch;
        switch (s.mode.get()) {
            case "Linear" -> {
                dYaw = MathHelper.clamp(eY, -max * dt, max * dt);
                dPitch = MathHelper.clamp(eP, -maxP * dt, maxP * dt);
            }
            case "Spring" -> {
                float k = 260 - 230 * smooth, c = 2 * (float) Math.sqrt(k) * 0.85f;
                yawRate = MathHelper.clamp(yawRate + (k * eY - c * yawRate) * dt, -max, max);
                pitchRate = MathHelper.clamp(pitchRate + (k * eP - c * pitchRate) * dt, -maxP, maxP);
                dYaw = yawRate * dt;
                dPitch = pitchRate * dt;
            }
            default -> { // Human: speed tapers as the error shrinks, with limited acceleration
                float knee = 6 + 40 * smooth;
                float wantY = Math.signum(eY) * max * (1 - (float) Math.exp(-Math.abs(eY) / knee));
                float wantP = Math.signum(eP) * maxP * (1 - (float) Math.exp(-Math.abs(eP) / knee));
                float acc = max * (10 - 7 * smooth) * dt;
                yawRate += MathHelper.clamp(wantY - yawRate, -acc, acc);
                pitchRate += MathHelper.clamp(wantP - pitchRate, -acc, acc);
                dYaw = yawRate * dt;
                dPitch = pitchRate * dt;
                if (Math.abs(dYaw) > Math.abs(eY) && Math.abs(eY) < 0.3f) dYaw = eY;
                if (Math.abs(dPitch) > Math.abs(eP) && Math.abs(eP) < 0.3f) dPitch = eP;
            }
        }

        // jitter: smooth layered noise, a little stronger while turning fast
        float j = s.jitter.getFloat();
        if (j > 0) {
            float t = now / 1000f;
            float moving = Math.min(1, (Math.abs(yawRate) + Math.abs(pitchRate)) / Math.max(1, max));
            float amp = j * (0.6f + 2.4f * moving) * dt;
            dYaw += amp * (float) (Math.sin(t * 7.1 + phase[0]) + 0.5 * Math.sin(t * 17.3 + phase[1]));
            dPitch += amp * 0.7f * (float) (Math.sin(t * 6.3 + phase[2]) + 0.5 * Math.sin(t * 15.7 + phase[3]));
        }

        // move through the same path as the mouse, optionally snapped to the sensitivity grid
        double sens = mc.options.getMouseSensitivity().getValue() * 0.6 + 0.2;
        double step = sens * sens * sens * 8.0 * 0.15; // degrees per mouse count
        double cx, cy;
        if (s.gcdFix.isOn()) {
            carryYaw += dYaw;
            carryPitch += dPitch;
            cx = Math.round(carryYaw / step);
            cy = Math.round(carryPitch / step);
            carryYaw -= (float) (cx * step);
            carryPitch -= (float) (cy * step);
            cx *= step / 0.15;
            cy *= step / 0.15;
        } else {
            cx = dYaw / 0.15;
            cy = dPitch / 0.15;
        }
        if (cx != 0 || cy != 0) p.changeLookDirection(cx, cy);
    }
}
