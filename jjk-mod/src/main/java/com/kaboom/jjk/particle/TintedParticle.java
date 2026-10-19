package com.kaboom.jjk.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.util.Mth;

/**
 * Client particle driven by {@link ColorParticleOptions}. Behaviour varies slightly per {@link Style}:
 * glows drift and fade, sparks shrink fast, slashes and lightning pop at a random roll, rings expand.
 */
public class TintedParticle extends TextureSheetParticle {
    public enum Style { GLOW, SPARK, SLASH, RING, LIGHTNING }

    private final SpriteSet sprites;
    private final Style style;
    private final float baseSize;

    protected TintedParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz,
                             ColorParticleOptions options, SpriteSet sprites, Style style) {
        super(level, x, y, z, 0, 0, 0);
        this.sprites = sprites;
        this.style = style;
        this.xd = dx;
        this.yd = dy;
        this.zd = dz;
        int c = options.color();
        this.rCol = ((c >> 16) & 0xFF) / 255f;
        this.gCol = ((c >> 8) & 0xFF) / 255f;
        this.bCol = (c & 0xFF) / 255f;
        this.alpha = 1f;
        this.lifetime = Math.max(2, options.lifetime() + (style == Style.GLOW ? random.nextInt(4) : 0));
        this.baseSize = 0.2f * options.scale();
        this.quadSize = baseSize;
        this.hasPhysics = false;
        this.gravity = 0;
        this.friction = style == Style.GLOW ? 0.9f : 0.8f;
        if (style == Style.SLASH || style == Style.LIGHTNING) {
            this.roll = this.oRoll = random.nextFloat() * Mth.TWO_PI;
        }
        setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        if (removed) return;
        setSpriteFromAge(sprites);
        float life = (float) age / lifetime;
        switch (style) {
            case GLOW -> {
                quadSize = baseSize * (1 - life * 0.5f);
                alpha = 1 - life * life;
            }
            case SPARK -> {
                quadSize = baseSize * (1 - life);
                alpha = 1 - life;
            }
            case SLASH, LIGHTNING -> {
                quadSize = baseSize * (0.8f + 0.4f * life);
                alpha = life < 0.3f ? 1 : 1 - (life - 0.3f) / 0.7f;
            }
            case RING -> {
                quadSize = baseSize * (0.3f + 2.2f * life);
                alpha = 1 - life;
            }
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    protected int getLightColor(float partialTick) {
        return 0xF000F0;
    }

    public record Provider(SpriteSet sprites, Style style) implements ParticleProvider<ColorParticleOptions> {
        @Override
        public Particle createParticle(ColorParticleOptions options, ClientLevel level, double x, double y, double z,
                                       double dx, double dy, double dz) {
            return new TintedParticle(level, x, y, z, dx, dy, dz, options, sprites, style);
        }
    }
}
