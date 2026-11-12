package com.deadcraft.magic;

import com.deadcraft.network.ManaSyncPayload;
import com.deadcraft.registry.ModArmorMaterials;
import com.deadcraft.registry.ModAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/** Player mana. Stored as attachments on the player, synced to the owning client for the HUD. */
public final class Mana {
    public static final float BASE_MAX = 100f;

    private Mana() {}

    public static boolean isAwakened(Player p) { return p.getData(ModAttachments.AWAKENED); }
    public static float get(Player p) { return p.getData(ModAttachments.MANA); }

    public static float max(Player p) {
        int stardust = 0;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack s = p.getItemBySlot(slot);
            if (s.getItem() instanceof ArmorItem a && a.getMaterial().is(ModArmorMaterials.STARDUST.getKey())) stardust++;
        }
        return BASE_MAX + stardust * 25f;
    }

    public static void set(Player p, float value) {
        float clamped = Math.max(0f, Math.min(max(p), value));
        p.setData(ModAttachments.MANA, clamped);
        if (p instanceof ServerPlayer sp) sync(sp);
    }

    public static void add(Player p, float amount) { set(p, get(p) + amount); }

    public static boolean trySpend(Player p, float cost) {
        if (p.getAbilities().instabuild) return true;
        if (get(p) < cost) return false;
        set(p, get(p) - cost);
        return true;
    }

    public static void awaken(ServerPlayer p) {
        p.setData(ModAttachments.AWAKENED, true);
        set(p, max(p));
    }

    /** Called every player tick on the server. */
    public static void tick(ServerPlayer p) {
        if (!isAwakened(p)) return;
        float max = max(p);
        float cur = get(p);
        if (cur < max) {
            p.setData(ModAttachments.MANA, Math.min(max, cur + 0.15f));
            if (p.tickCount % 10 == 0) sync(p);
        } else if (cur > max) {
            set(p, max);
        }
    }

    public static void sync(ServerPlayer p) {
        PacketDistributor.sendToPlayer(p, new ManaSyncPayload(get(p), max(p), isAwakened(p)));
    }
}
