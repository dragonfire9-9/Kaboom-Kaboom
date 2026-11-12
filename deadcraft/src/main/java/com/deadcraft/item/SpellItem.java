package com.deadcraft.item;

import com.deadcraft.entity.DeadProjectile;
import com.deadcraft.magic.Mana;
import com.deadcraft.registry.ModParticles;
import com.deadcraft.registry.ModSounds;
import com.deadcraft.util.Fx;
import com.deadcraft.world.Hearing;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;

/** Staves, tomes and the chalice. Each casts one spell for mana; requires a Grimoire of Awakening first. */
public class SpellItem extends Item {
    public enum Spell {
        SOUL_BOLT(15, 12, ModSounds.SOUL_BOLT),
        EMBER(25, 30, ModSounds.EMBER),
        FROST_NOVA(30, 60, ModSounds.FROST_NOVA),
        BLINK(20, 30, ModSounds.BLINK),
        STORM(40, 80, ModSounds.STORM),
        BLOOD_PACT(35, 200, ModSounds.HEAL);

        public final float cost;
        public final int cooldown;
        public final Holder<SoundEvent> sound;

        Spell(float cost, int cooldown, Holder<SoundEvent> sound) {
            this.cost = cost;
            this.cooldown = cooldown;
            this.sound = sound;
        }
    }

    private final Spell spell;

    public SpellItem(Spell spell, Properties props) {
        super(props);
        this.spell = spell;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!Mana.isAwakened(player)) {
            if (level.isClientSide) player.displayClientMessage(Component.translatable("message.deadcraft.not_awakened").withStyle(ChatFormatting.DARK_PURPLE), true);
            return InteractionResultHolder.fail(stack);
        }
        if (!player.getAbilities().instabuild && Mana.get(player) < spell.cost) {
            if (level.isClientSide) player.displayClientMessage(Component.translatable("message.deadcraft.no_mana").withStyle(ChatFormatting.BLUE), true);
            return InteractionResultHolder.fail(stack);
        }
        if (level instanceof ServerLevel server) {
            if (!Mana.trySpend(player, spell.cost)) return InteractionResultHolder.fail(stack);
            cast(server, player);
            server.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.CAST.get(), SoundSource.PLAYERS, 0.8f, 1.1f);
            server.playSound(null, player.getX(), player.getY(), player.getZ(), spell.sound, SoundSource.PLAYERS, 1.4f, 1f);
            for (int i = 0; i < 24; i++) {
                double a = Math.PI * 2 * i / 24;
                server.sendParticles(ModParticles.RUNE.get(), player.getX() + Math.cos(a) * 1.1, player.getY() + 0.05,
                        player.getZ() + Math.sin(a) * 1.1, 1, 0, 0.02, 0, 0.01);
            }
            Hearing.noise(server, player.position(), 20, player);
        }
        player.getCooldowns().addCooldown(this, spell.cooldown);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    private void cast(ServerLevel level, Player player) {
        switch (spell) {
            case SOUL_BOLT -> {
                DeadProjectile p = new DeadProjectile(level, player, DeadProjectile.Kind.SOUL, 8f);
                p.shootFromRotation(player, player.getXRot(), player.getYRot(), 0, 1.7f, 0.5f);
                level.addFreshEntity(p);
            }
            case EMBER -> {
                DeadProjectile p = new DeadProjectile(level, player, DeadProjectile.Kind.EMBER, 7f);
                p.shootFromRotation(player, player.getXRot(), player.getYRot(), 0, 1.3f, 0.5f);
                level.addFreshEntity(p);
            }
            case FROST_NOVA -> {
                double r = 6.5;
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(r),
                        e -> e != player && e.isAlive() && !isFriendly(player, e))) {
                    e.hurt(level.damageSources().indirectMagic(player, player), 6f);
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 3));
                    e.setTicksFrozen(Math.max(e.getTicksFrozen(), 240));
                }
                for (int ring = 1; ring <= 3; ring++) {
                    int n = 20 * ring;
                    for (int i = 0; i < n; i++) {
                        double a = Math.PI * 2 * i / n;
                        level.sendParticles(ModParticles.FROST.get(), player.getX() + Math.cos(a) * ring * 2, player.getY() + 0.3,
                                player.getZ() + Math.sin(a) * ring * 2, 1, 0, 0.05, 0, 0.02);
                    }
                }
                level.sendParticles(ModParticles.SHOCKWAVE.get(), player.getX(), player.getY() + 0.1, player.getZ(), 1, 0, 0, 0, 0);
            }
            case BLINK -> {
                Vec3 start = player.getEyePosition();
                Vec3 end = start.add(player.getLookAngle().scale(12));
                HitResult hit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
                Vec3 dir = player.getLookAngle();
                Vec3 target = hit.getLocation().subtract(dir.scale(0.6)).subtract(0, player.getEyeHeight() * 0.5, 0);
                for (int i = 0; i < 12; i++) {
                    AABB box = player.getBoundingBox().move(target.subtract(player.position()));
                    if (level.noCollision(player, box)) break;
                    target = target.subtract(dir.scale(0.8)).add(0, 0.2, 0);
                }
                level.sendParticles(ModParticles.SOUL.get(), player.getX(), player.getY(1), player.getZ(), 40, 0.3, 0.8, 0.3, 0.08);
                player.teleportTo(target.x, target.y, target.z);
                player.resetFallDistance();
                level.sendParticles(ModParticles.SOUL.get(), player.getX(), player.getY(1), player.getZ(), 40, 0.3, 0.8, 0.3, 0.08);
            }
            case STORM -> {
                Vec3 start = player.getEyePosition();
                Vec3 end = start.add(player.getLookAngle().scale(36));
                HitResult hit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
                Vec3 at = hit.getLocation();
                LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
                if (bolt != null) {
                    bolt.moveTo(at.x, at.y, at.z);
                    bolt.setVisualOnly(true);
                    level.addFreshEntity(bolt);
                }
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(3.5),
                        e -> e != player && e.isAlive() && !isFriendly(player, e))) {
                    e.hurt(level.damageSources().indirectMagic(player, player), 14f);
                    e.igniteForSeconds(3);
                }
                level.sendParticles(ModParticles.SPARK.get(), at.x, at.y + 0.5, at.z, 60, 1.2, 1.2, 1.2, 0.3);
                Fx.shake(level, at, 24, 0.6f);
            }
            case BLOOD_PACT -> {
                player.heal(8f);
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 0));
                level.sendParticles(ModParticles.BLOOD.get(), player.getX(), player.getY(1), player.getZ(), 40, 0.4, 0.8, 0.4, 0.05);
            }
        }
    }

    private static boolean isFriendly(Player caster, LivingEntity e) {
        if (e instanceof Player) return true;
        return e instanceof OwnableEntity o && caster.getUUID().equals(o.getOwnerUUID());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        String name = spell.name().toLowerCase(Locale.ROOT);
        lines.add(Component.translatable("spell.deadcraft." + name).withStyle(ChatFormatting.LIGHT_PURPLE));
        lines.add(Component.translatable("spell.deadcraft." + name + ".desc").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.deadcraft.mana_cost", (int) spell.cost).withStyle(ChatFormatting.BLUE));
    }
}
