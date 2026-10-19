package com.kaboom.jjk.boss;

import com.kaboom.jjk.ability.AbilityEffects;
import com.kaboom.jjk.ability.DomainManager;
import com.kaboom.jjk.ability.DomainType;
import com.kaboom.jjk.ability.JJKDamage;
import com.kaboom.jjk.data.ModAttachments;
import com.kaboom.jjk.data.ModTags;
import com.kaboom.jjk.effect.ModEffects;
import com.kaboom.jjk.entity.CurseEntity;
import com.kaboom.jjk.entity.CurseType;
import com.kaboom.jjk.entity.ModEntities;
import com.kaboom.jjk.entity.TechniqueProjectile;
import com.kaboom.jjk.network.FxSender;
import com.kaboom.jjk.particle.ModParticles;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Attack patterns for every boss. */
public final class BossBrains {

    public static BossBrain create(BossType type) {
        return switch (type) {
            case SUKUNA -> new Sukuna();
            case MAHITO -> new Mahito();
            case JOGO -> new Jogo();
            case HANAMI -> new Hanami();
            case KENJAKU -> new Kenjaku();
            case MAHORAGA -> new Mahoraga();
            case TOJI -> new Toji();
        };
    }

    // ------------------------------------------------------------------ shared helpers

    static ServerLevel level(BossEntity b) {
        return (ServerLevel) b.level();
    }

    static void shoot(BossEntity boss, LivingEntity target, TechniqueProjectile.Kind kind, float damage, float scale, double speed, boolean pierce) {
        Vec3 dir = target.getEyePosition().subtract(boss.getEyePosition()).normalize();
        TechniqueProjectile p = TechniqueProjectile.create(boss.level(), boss, kind, damage, scale, 80, pierce);
        Vec3 start = boss.getEyePosition().add(dir.scale(1.5));
        p.setPos(start.x, start.y - scale * 0.4, start.z);
        p.setDeltaMovement(dir.scale(speed));
        boss.level().addFreshEntity(p);
        boss.playCast();
    }

    /** Invisible cutting along the line to the target. */
    static void slashLine(BossEntity boss, LivingEntity target, float damage) {
        ServerLevel level = level(boss);
        Vec3 start = boss.getEyePosition();
        Vec3 end = target.position().add(0, target.getBbHeight() / 2, 0);
        Vec3 delta = end.subtract(start);
        for (int i = 1; i <= 8; i++) {
            Vec3 p = start.add(delta.scale(i / 8.0));
            level.sendParticles(ModParticles.slash(0xFFFFFFFF, 4f, 6), p.x, p.y, p.z, 1, 0.1, 0.1, 0.1, 0);
        }
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(start, end).inflate(1.2),
                e -> e != boss && e.isAlive() && e.getBoundingBox().inflate(0.8).clip(start, end).isPresent())) {
            e.invulnerableTime = 0;
            e.hurt(JJKDamage.technique(boss), damage);
        }
        boss.playCast();
    }

    static void areaHit(BossEntity boss, Vec3 center, double radius, float damage, double knock, int color) {
        ServerLevel level = level(boss);
        for (LivingEntity e : AbilityEffects.livingAround(boss, center, radius)) {
            if (e instanceof CurseEntity c && boss.getUUID().equals(c.getOwnerUUID())) continue;
            e.hurt(JJKDamage.technique(boss), damage);
            if (knock > 0) AbilityEffects.knock(boss, e, knock);
        }
        level.sendParticles(ModParticles.ring(color, (float) radius * 2.5f, 14), center.x, center.y + 0.2, center.z, 1, 0, 0, 0, 0);
        AbilityEffects.burstAt(level, center.add(0, 1, 0), ModParticles.glow(color, 2f, 16), 40, radius * 0.4);
        FxSender.shakeAround(level, center, 20, 0.6f, 10);
    }

    static void dashTo(BossEntity boss, LivingEntity target, double strength) {
        Vec3 dir = target.position().subtract(boss.position()).normalize();
        boss.setDeltaMovement(dir.x * strength, 0.35, dir.z * strength);
        boss.hurtMarked = true;
    }

    static float mult(int phase) {
        return 1f + 0.25f * (phase - 1);
    }

    // ------------------------------------------------------------------ bosses

    /** King of Curses: Dismantle spam, Cleave up close, Fuga, then Malevolent Shrine. */
    static final class Sukuna implements BossBrain {
        @Override
        public void tick(BossEntity b, LivingEntity t, int phase) {
            double dist = b.distanceTo(t);
            if (--b.cooldownA <= 0) {
                slashLine(b, t, 9f * mult(phase));
                b.cooldownA = phase == 3 ? 14 : 26;
            }
            if (--b.cooldownB <= 0 && dist < 6) {
                t.invulnerableTime = 0;
                t.hurt(JJKDamage.technique(b), 18f * mult(phase) + t.getMaxHealth() * 0.1f);
                level(b).sendParticles(ModParticles.slash(0xFFFFFFFF, 5f, 7), t.getX(), t.getY() + 1, t.getZ(), 6, 0.5, 0.5, 0.5, 0);
                FxSender.shakeAround(level(b), t.position(), 16, 0.8f, 8);
                b.cooldownB = 80;
            }
            if (phase >= 2 && --b.cooldownC <= 0) {
                shoot(b, t, TechniqueProjectile.Kind.FUGA, 30f, 1f, 1.8, false);
                b.cooldownC = 160;
            }
            if (dist > 10 && b.tickCount % 60 == 0) dashTo(b, t, 1.8);
        }

        @Override
        public void onPhaseChange(BossEntity b, int phase) {
            if (phase == 3) DomainManager.expand(b, 5000, 1.4f, DomainType.MALEVOLENT_SHRINE);
        }
    }

    /** Idle Transfiguration: soul touch, transfigured humans, Self-Embodiment of Perfection. */
    static final class Mahito implements BossBrain {
        @Override
        public void tick(BossEntity b, LivingEntity t, int phase) {
            if (--b.cooldownA <= 0 && b.distanceTo(t) < 4) {
                t.hurt(JJKDamage.sureHit(b), 12f * mult(phase)); // the soul has no armor
                t.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 0));
                t.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 1));
                AbilityEffects.burstAt(level(b), t.getEyePosition(), ModParticles.glow(0xFF8FC8AA, 1.6f, 14), 16, 0.4);
                b.playCast();
                b.cooldownA = 50;
            }
            if (--b.cooldownB <= 0) {
                for (int i = 0; i < 1 + phase; i++) summonAlly(b, CurseType.TRANSFIGURED_HUMAN);
                b.cooldownB = 220;
            }
            if (phase >= 2 && b.tickCount % 200 == 0) {
                b.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, 1));
                b.heal(b.getMaxHealth() * 0.04f); // reshapes his own body
            }
        }

        @Override
        public void onPhaseChange(BossEntity b, int phase) {
            if (phase == 3) DomainManager.expand(b, 3500, 1.2f, DomainType.SELF_EMBODIMENT_OF_PERFECTION);
        }
    }

    /** Disaster Flames: embers, eruptions, Maximum: Meteor, Coffin of the Iron Mountain. */
    static final class Jogo implements BossBrain {
        @Override
        public void tick(BossEntity b, LivingEntity t, int phase) {
            if (--b.cooldownA <= 0) {
                for (int i = 0; i < 2 + phase; i++) shoot(b, t, TechniqueProjectile.Kind.EMBER, 5f * mult(phase), 0.4f, 0.9, false);
                b.cooldownA = 40;
            }
            if (--b.cooldownB <= 0 && b.distanceTo(t) < 7) {
                areaHit(b, b.position(), 6, 12f * mult(phase), 1.2, 0xFFFF5A1A);
                for (LivingEntity e : AbilityEffects.livingAround(b, b.position(), 6)) e.igniteForSeconds(6);
                b.cooldownB = 100;
            }
            if (phase >= 2 && --b.cooldownC <= 0) {
                TechniqueProjectile m = TechniqueProjectile.create(b.level(), b, TechniqueProjectile.Kind.METEOR, 34f, 3.2f, 120, false);
                m.setPos(t.getX() + 5, t.getY() + 26, t.getZ());
                m.setDeltaMovement(-0.2, -0.9, 0);
                b.level().addFreshEntity(m);
                b.playCast();
                b.cooldownC = 240;
            }
        }

        @Override
        public void onPhaseChange(BossEntity b, int phase) {
            if (phase == 3) DomainManager.expand(b, 3800, 1.2f, DomainType.COFFIN_OF_THE_IRON_MOUNTAIN);
        }
    }

    /** Plant curse: root eruptions, Cursed Buds, flower healing, Ceremonial Sea of Light. */
    static final class Hanami implements BossBrain {
        @Override
        public void tick(BossEntity b, LivingEntity t, int phase) {
            if (--b.cooldownA <= 0) {
                Vec3 at = t.position();
                areaHit(b, at, 3, 10f * mult(phase), 0, 0xFF6A9A5A);
                t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 3));
                level(b).sendParticles(ParticleTypes.COMPOSTER, at.x, at.y + 0.5, at.z, 40, 1, 1, 1, 0.1);
                t.setDeltaMovement(t.getDeltaMovement().add(0, 0.8, 0));
                t.hurtMarked = true;
                b.cooldownA = 60;
            }
            if (--b.cooldownB <= 0) {
                // Cursed Buds sprout on the target and feed on its cursed energy.
                t.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 1));
                t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 160, 1));
                if (t instanceof ServerPlayer p) {
                    var data = ModAttachments.get(p);
                    data.setCurrentCE(data.getCurrentCE() - 40);
                }
                level(b).sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, t.getX(), t.getY() + 1, t.getZ(), 30, 0.4, 0.6, 0.4, 0);
                b.playCast();
                b.cooldownB = 160;
            }
            if (phase >= 2 && b.tickCount % 200 == 0) {
                b.heal(20f);
                level(b).sendParticles(ParticleTypes.HAPPY_VILLAGER, b.getX(), b.getY() + 1, b.getZ(), 20, 1, 1, 1, 0);
            }
            if (phase == 3 && --b.cooldownC <= 0) {
                shoot(b, t, TechniqueProjectile.Kind.ENERGY, 28f, 1.6f, 1.6, true);
                b.cooldownC = 180;
            }
        }
    }

    /** Geto's body: waves of curses and Uzumaki. */
    static final class Kenjaku implements BossBrain {
        @Override
        public void tick(BossEntity b, LivingEntity t, int phase) {
            if (--b.cooldownA <= 0) {
                summonAlly(b, CurseType.FLY_HEAD);
                summonAlly(b, CurseType.FLY_HEAD);
                summonAlly(b, CurseType.ROPPONGI_CURSE);
                if (phase == 3) summonAlly(b, CurseType.SMALLPOX_DEITY);
                b.cooldownA = 240 - phase * 30;
            }
            if (--b.cooldownB <= 0) {
                shoot(b, t, TechniqueProjectile.Kind.CURSE, 22f * mult(phase), 2.2f, 1.5, true);
                b.cooldownB = 120;
            }
            if (phase == 3 && --b.cooldownC <= 0) {
                // Anti-gravity: everything nearby is lifted, then slammed.
                for (LivingEntity e : AbilityEffects.livingAround(b, b.position(), 12)) {
                    if (e instanceof CurseEntity) continue;
                    e.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 30, 2));
                }
                b.cooldownC = 200;
            }
        }
    }

    /** Eight-Handled Sword Divergent Sila Divine General: adapts to whatever keeps hurting it. */
    static final class Mahoraga implements BossBrain {
        private final Map<String, Integer> hits = new HashMap<>();
        private final Set<String> adapted = new HashSet<>();

        @Override
        public void tick(BossEntity b, LivingEntity t, int phase) {
            if (--b.cooldownA <= 0 && b.distanceTo(t) < 5) {
                float dmg = 20f * mult(phase);
                if (t.getType().is(ModTags.CURSES)) dmg *= 2; // the Sword of Extermination
                t.hurt(JJKDamage.technique(b), dmg);
                level(b).sendParticles(ModParticles.slash(0xFFFFF6D0, 5f, 7), t.getX(), t.getY() + 1, t.getZ(), 4, 0.4, 0.4, 0.4, 0);
                b.cooldownA = 40;
            }
            if (b.distanceTo(t) > 8 && b.tickCount % 50 == 0) dashTo(b, t, 2.0);
        }

        @Override
        public float modifyIncomingDamage(BossEntity b, DamageSource source, float amount) {
            String key = source.getMsgId();
            if (source.getEntity() instanceof ServerPlayer p) key += ":" + ModAttachments.get(p).getTechnique().id();
            if (adapted.contains(key)) return amount * 0.1f;
            int n = hits.merge(key, 1, Integer::sum);
            if (n >= 4) {
                adapted.add(key);
                b.heal(b.getMaxHealth() * 0.05f);
                b.playCast(); // the wheel turns
                level(b).playSound(null, b.blockPosition(), com.kaboom.jjk.sound.ModSounds.MAHORAGA_ADAPT.get(),
                        net.minecraft.sounds.SoundSource.HOSTILE, 3f, 1f);
                if (source.getEntity() instanceof ServerPlayer p) {
                    p.displayClientMessage(net.minecraft.network.chat.Component.translatable("jjk.message.mahoraga_adapted")
                            .withStyle(net.minecraft.ChatFormatting.GOLD), true);
                }
            }
            return amount;
        }

        @Override
        public void onPhaseChange(BossEntity b, int phase) {
            b.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 60, phase - 1));
        }
    }

    /** The Sorcerer Killer: no cursed energy, a body that breaks the rules, and the Inverted Spear. */
    static final class Toji implements BossBrain {
        @Override
        public void tick(BossEntity b, LivingEntity t, int phase) {
            if (--b.cooldownA <= 0) {
                // Vanish and reappear behind the target.
                Vec3 behind = t.position().subtract(t.getLookAngle().multiply(1, 0, 1).normalize().scale(2));
                level(b).sendParticles(ParticleTypes.CLOUD, b.getX(), b.getY() + 1, b.getZ(), 12, 0.3, 0.5, 0.3, 0.05);
                b.teleportTo(behind.x, t.getY(), behind.z);
                b.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 30, 0, false, false));
                b.cooldownA = phase == 3 ? 60 : 100;
            }
            if (--b.cooldownB <= 0 && b.distanceTo(t) > 4) {
                dashTo(b, t, 2.4);
                b.cooldownB = 50;
            }
            if (b.distanceTo(t) < 3.5 && b.tickCount % 20 == 0) {
                // Inverted Spear of Heaven: nullifies techniques on contact.
                t.addEffect(new MobEffectInstance(ModEffects.NULLIFIED, 60, 0));
                t.removeEffect(ModEffects.INFINITY);
            }
        }

        @Override
        public void onPhaseChange(BossEntity b, int phase) {
            b.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 20 * 120, phase - 2));
        }
    }

    static void summonAlly(BossEntity boss, CurseType type) {
        CurseEntity c = ModEntities.curse(type).create(boss.level());
        if (c == null) return;
        double a = boss.getRandom().nextDouble() * Math.PI * 2;
        c.moveTo(boss.getX() + Math.cos(a) * 3, boss.getY() + (type.flying ? 2 : 0), boss.getZ() + Math.sin(a) * 3, 0, 0);
        c.setOwner(boss, 20 * 40);
        boss.level().addFreshEntity(c);
        level(boss).sendParticles(ModParticles.glow(0xFF2A1A30, 2f, 20), c.getX(), c.getY() + 0.5, c.getZ(), 20, 0.4, 0.4, 0.4, 0.02);
    }

    private BossBrains() {}
}
