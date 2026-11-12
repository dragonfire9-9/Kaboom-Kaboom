package com.deadcraft.item;

import com.deadcraft.client.ClientHooks;
import com.deadcraft.combat.DeadEnchantments;
import com.deadcraft.entity.DeadProjectile;
import com.deadcraft.registry.ModDataComponents;
import com.deadcraft.registry.ModItems;
import com.deadcraft.registry.ModParticles;
import com.deadcraft.registry.ModSounds;
import com.deadcraft.world.Hearing;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Firearms. Right-click to fire (hold for the SMG), press R to reload. Gunshots are very loud:
 * every creature within earshot comes running.
 */
public class GunItem extends Item {
    public enum GunType {
        //       mag, damage, pellets, spread, speed, cooldown, reload, recoil, auto, noise
        REVOLVER(6, 8f, 1, 0.8f, 5.0f, 9, 32, 7f, false, 48),
        SHOTGUN(2, 3.5f, 7, 9f, 3.6f, 18, 40, 13f, false, 56),
        RIFLE(5, 16f, 1, 0.1f, 7.0f, 26, 52, 10f, false, 64),
        SMG(30, 3.2f, 1, 4.5f, 4.8f, 2, 46, 2.2f, true, 40);

        public final int magazine;
        public final float damage;
        public final int pellets;
        public final float spread;
        public final float speed;
        public final int cooldown;
        public final int reloadTicks;
        public final float recoil;
        public final boolean automatic;
        public final double noise;

        GunType(int magazine, float damage, int pellets, float spread, float speed, int cooldown, int reloadTicks,
                float recoil, boolean automatic, double noise) {
            this.magazine = magazine;
            this.damage = damage;
            this.pellets = pellets;
            this.spread = spread;
            this.speed = speed;
            this.cooldown = cooldown;
            this.reloadTicks = reloadTicks;
            this.recoil = recoil;
            this.automatic = automatic;
            this.noise = noise;
        }

        public Item ammo() {
            return switch (this) {
                case REVOLVER, SMG -> ModItems.PISTOL_ROUNDS.get();
                case SHOTGUN -> ModItems.SHOTGUN_SHELLS.get();
                case RIFLE -> ModItems.RIFLE_ROUNDS.get();
            };
        }

        public Holder<SoundEvent> sound() {
            return switch (this) {
                case REVOLVER -> ModSounds.REVOLVER;
                case SHOTGUN -> ModSounds.SHOTGUN;
                case RIFLE -> ModSounds.RIFLE;
                case SMG -> ModSounds.SMG;
            };
        }
    }

    private final GunType type;

    public GunItem(GunType type, Properties props) {
        super(props);
        this.type = type;
    }

    public GunType type() { return type; }

    public static int ammo(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.AMMO.get(), 0);
    }

    public static void setAmmo(ItemStack stack, int ammo) {
        stack.set(ModDataComponents.AMMO.get(), ammo);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.fail(stack);
        if (ammo(stack) <= 0) {
            dryFire(level, player);
            return InteractionResultHolder.fail(stack);
        }
        if (type.automatic) {
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(stack);
        }
        fire(level, player, stack);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (!(entity instanceof Player player)) return;
        int used = getUseDuration(stack, entity) - remaining;
        if (used % type.cooldown != 0) return;
        if (ammo(stack) <= 0) {
            dryFire(level, player);
            player.stopUsingItem();
            return;
        }
        fire(level, player, stack);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return type.automatic ? 72000 : 0;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    private void fire(Level level, Player player, ItemStack stack) {
        if (level.isClientSide) {
            ClientHooks.gunFired(type.recoil);
            return;
        }
        ServerLevel server = (ServerLevel) level;
        int hollowPoint = DeadEnchantments.level(level, DeadEnchantments.HOLLOW_POINT, stack);
        float damage = type.damage * (1f + 0.15f * hollowPoint);
        DeadProjectile.Kind kind = type == GunType.SHOTGUN ? DeadProjectile.Kind.PELLET : DeadProjectile.Kind.BULLET;
        for (int i = 0; i < type.pellets; i++) {
            DeadProjectile bullet = new DeadProjectile(level, player, kind, damage);
            bullet.shootFromRotation(player, player.getXRot(), player.getYRot(), 0f, type.speed, type.spread);
            level.addFreshEntity(bullet);
        }
        if (!player.getAbilities().instabuild) setAmmo(stack, ammo(stack) - 1);
        if (!type.automatic) player.getCooldowns().addCooldown(this, type.cooldown);
        level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), type.sound(), SoundSource.PLAYERS,
                2.5f, 0.92f + level.random.nextFloat() * 0.16f);
        Vec3 muzzle = player.getEyePosition().add(player.getLookAngle().scale(0.9))
                .add(Vec3.directionFromRotation(0, player.getYRot() + 90).scale(-0.25)).add(0, -0.2, 0);
        server.sendParticles(ModParticles.MUZZLE_FLASH.get(), muzzle.x, muzzle.y, muzzle.z, 1, 0, 0, 0, 0);
        server.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE, muzzle.x, muzzle.y, muzzle.z, 3, 0.02, 0.02, 0.02, 0.01);
        Hearing.noise(server, player.position(), type.noise, player);
    }

    private void dryFire(Level level, Player player) {
        level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), ModSounds.EMPTY.get(), SoundSource.PLAYERS, 1f, 1f);
        if (level.isClientSide) {
            player.displayClientMessage(Component.translatable("message.deadcraft.reload_hint").withStyle(ChatFormatting.GRAY), true);
        }
    }

    /** Server side: load as many rounds as the player carries. */
    public void reload(ServerPlayer player, ItemStack stack) {
        int loaded = ammo(stack);
        if (loaded >= type.magazine || player.getCooldowns().isOnCooldown(this)) return;
        int needed = type.magazine - loaded;
        int taken = 0;
        if (player.getAbilities().instabuild) {
            taken = needed;
        } else {
            Item ammo = type.ammo();
            for (ItemStack s : player.getInventory().items) {
                if (taken >= needed) break;
                if (s.is(ammo)) {
                    int t = Math.min(s.getCount(), needed - taken);
                    s.shrink(t);
                    taken += t;
                }
            }
        }
        if (taken == 0) {
            player.displayClientMessage(Component.translatable("message.deadcraft.no_ammo").withStyle(ChatFormatting.RED), true);
            return;
        }
        setAmmo(stack, loaded + taken);
        int quick = DeadEnchantments.level(player.level(), DeadEnchantments.QUICK_RELOAD, stack);
        int ticks = Math.max(8, (int) (type.reloadTicks * (1f - 0.2f * quick)));
        player.getCooldowns().addCooldown(this, ticks);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.RELOAD.get(), SoundSource.PLAYERS, 1f,
                1.25f - Math.min(0.5f, ticks / 120f));
    }

    @Override
    public boolean isBarVisible(ItemStack stack) { return true; }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13f * ammo(stack) / type.magazine);
    }

    @Override
    public int getBarColor(ItemStack stack) { return 0xffd27f; }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !ItemStack.isSameItem(oldStack, newStack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.deadcraft.ammo", ammo(stack), type.magazine).withStyle(ChatFormatting.GOLD));
        lines.add(Component.translatable("tooltip.deadcraft.gun_damage",
                type.pellets > 1 ? type.pellets + "×" + type.damage : String.valueOf(type.damage)).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.deadcraft.uses_ammo", type.ammo().getDescription()).withStyle(ChatFormatting.DARK_GRAY));
        lines.add(Component.translatable("tooltip.deadcraft.reload_key").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public int getEnchantmentValue() { return 12; }

    @Override
    public boolean isEnchantable(ItemStack stack) { return true; }
}
