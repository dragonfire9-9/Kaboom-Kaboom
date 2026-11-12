package com.deadcraft.client.particle;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Quaternionf;

/** One configurable particle class for every DeadCraft effect. */
public class DeadParticle extends TextureSheetParticle {
    public enum Style {
        //        life, gravity, size, grow, fullbright, friction, flat, r, g, b
        BLOOD(28, 0.9f, 0.18f, 0.0f, false, 0.92f, false, 1f, 1f, 1f),
        EMBER(36, -0.04f, 0.10f, -0.4f, true, 0.96f, false, 1f, 1f, 1f),
        SOUL(40, -0.02f, 0.14f, -0.3f, true, 0.95f, false, 1f, 1f, 1f),
        SPARK(14, 0.5f, 0.08f, -0.8f, true, 0.88f, false, 1f, 1f, 1f),
        STAR(30, 0.0f, 0.12f, -0.6f, true, 0.94f, false, 1f, 1f, 1f),
        RUNE(34, -0.03f, 0.16f, 0.2f, true, 0.9f, false, 1f, 1f, 1f),
        SLASH(8, 0.0f, 0.9f, 0.4f, true, 0.0f, false, 1f, 1f, 1f),
        ASH(60, 0.05f, 0.07f, 0.0f, false, 0.97f, false, 0.6f, 0.6f, 0.6f),
        FROST(30, 0.08f, 0.12f, -0.5f, true, 0.9f, false, 1f, 1f, 1f),
        MUZZLE_FLASH(3, 0.0f, 0.45f, 0.6f, true, 0.0f, false, 1f, 1f, 1f),
        CASING(40, 1.2f, 0.035f, 0.0f, false, 0.98f, false, 1f, 1f, 1f),
        GUNSMOKE(46, -0.012f, 0.22f, 0.9f, false, 0.94f, false, 0.82f, 0.8f, 0.78f),
        SHOCKWAVE(16, 0.0f, 0.6f, 12f, true, 0.0f, true, 1f, 0.92f, 0.85f);

        final int life;
        final float gravity, size, grow, friction, r, g, b;
        final boolean fullbright, flat;

        Style(int life, float gravity, float size, float grow, boolean fullbright, float friction, boolean flat, float r, float g, float b) {
            this.life = life;
            this.gravity = gravity;
            this.size = size;
            this.grow = grow;
            this.fullbright = fullbright;
            this.friction = friction;
            this.flat = flat;
            this.r = r;
            this.g = g;
            this.b = b;
        }
    }

    private final SpriteSet sprites;
    private final Style style;
    private final float baseSize;

    protected DeadParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites, Style style) {
        super(level, x, y, z, dx, dy, dz);
        this.sprites = sprites;
        this.style = style;
        this.lifetime = Math.max(2, (int) (style.life * (0.75f + random.nextFloat() * 0.5f)));
        this.gravity = style.gravity;
        this.friction = style.friction;
        this.baseSize = style.size * (0.8f + random.nextFloat() * 0.4f);
        this.quadSize = baseSize;
        this.hasPhysics = style == Style.BLOOD || style == Style.ASH || style == Style.SPARK || style == Style.CASING;
        this.rCol = style.r;
        this.gCol = style.g;
        this.bCol = style.b;
        if (style == Style.SLASH || style == Style.MUZZLE_FLASH) {
            this.roll = random.nextFloat() * Mth.TWO_PI;
            this.oRoll = roll;
            this.xd = this.yd = this.zd = 0;
        } else if (style == Style.SHOCKWAVE) {
            this.xd = this.yd = this.zd = 0;
        } else {
            this.xd = dx + (random.nextFloat() - 0.5f) * 0.02f;
            this.yd = dy + (random.nextFloat() - 0.5f) * 0.02f;
            this.zd = dz + (random.nextFloat() - 0.5f) * 0.02f;
        }
        setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        if (!removed) setSpriteFromAge(sprites);
        if (style == Style.SOUL || style == Style.EMBER) {
            xd += Mth.sin(age * 0.3f + (float) x) * 0.002f;
            zd += Mth.cos(age * 0.3f + (float) z) * 0.002f;
        }
        if (style == Style.CASING) {
            oRoll = roll;
            roll += (float) (Math.abs(xd) + Math.abs(zd)) * 6f;
            if (onGround && yd <= 0 && Math.abs(xd) + Math.abs(zd) > 0.01) {   // bounce
                yd = 0.12 * (1 - age / (float) lifetime);
                xd *= 0.5;
                zd *= 0.5;
            }
        }
        if (style == Style.BLOOD && onGround) {
            xd *= 0.3;
            zd *= 0.3;
        }
    }

    @Override
    public float getQuadSize(float partialTick) {
        float t = Mth.clamp((age + partialTick) / lifetime, 0f, 1f);
        return Math.max(0.001f, baseSize * (1f + style.grow * t));
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTick) {
        float t = Mth.clamp((age + partialTick) / lifetime, 0f, 1f);
        this.alpha = style == Style.BLOOD || style == Style.ASH ? 1f - t * t : 1f - t;
        if (style.flat) {
            renderRotatedQuad(buffer, camera, new Quaternionf().rotationX(-Mth.HALF_PI), partialTick);
            renderRotatedQuad(buffer, camera, new Quaternionf().rotationX(Mth.HALF_PI), partialTick);
        } else {
            super.render(buffer, camera, partialTick);
        }
    }

    @Override
    protected int getLightColor(float partialTick) {
        return style.fullbright ? 0xF000F0 : super.getLightColor(partialTick);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public record Provider(SpriteSet sprites, Style style) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double dx, double dy, double dz) {
            return new DeadParticle(level, x, y, z, dx, dy, dz, sprites, style);
        }
    }
}
