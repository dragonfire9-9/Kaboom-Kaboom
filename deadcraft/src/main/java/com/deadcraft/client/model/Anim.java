package com.deadcraft.client.model;

import net.minecraft.util.Mth;

/** Small animation helpers shared by the DeadCraft models. */
final class Anim {
    private Anim() {}

    static float swing(float limbSwing, float amount, float speed, float scale, float offset) {
        return Mth.cos(limbSwing * speed + offset) * scale * amount;
    }

    /**
     * A jittery "horror twitch": mostly still, then a sharp jerk. Deterministic per entity id
     * so creatures twitch out of sync.
     */
    static float twitch(float age, int seed, float strength) {
        float phase = age * 0.11f + (seed % 97) * 0.37f;
        float gate = Mth.sin(phase * 0.71f);
        if (gate < 0.93f) return 0f;
        return Mth.sin(age * 2.7f + seed) * strength * (gate - 0.93f) / 0.07f;
    }

    static float breathe(float age, float speed, float scale) {
        return Mth.sin(age * speed) * scale;
    }
}
