package com.kaboom.jjk.item;

import com.kaboom.jjk.JJKMod;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.List;

public final class ModArmorMaterials {
    public static final DeferredRegister<ArmorMaterial> MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, JJKMod.MODID);

    /** Maki's curse-sight glasses: no protection to speak of. */
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> CURSE_GLASSES = register("curse_glasses",
            1, 0, 0, 10, SoundEvents.ARMOR_EQUIP_GENERIC);

    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> JUJUTSU_UNIFORM = register("jujutsu_uniform", 2, 5, 4, 12, SoundEvents.ARMOR_EQUIP_LEATHER);
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> GOJO_OUTFIT = register("gojo_outfit", 2, 5, 4, 18, SoundEvents.ARMOR_EQUIP_LEATHER);
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> SUKUNA_KIMONO = register("sukuna_kimono", 3, 7, 6, 15, SoundEvents.ARMOR_EQUIP_NETHERITE);
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> TOJI_OUTFIT = register("toji_outfit", 2, 6, 5, 8, SoundEvents.ARMOR_EQUIP_LEATHER);
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> NANAMI_SUIT = register("nanami_suit", 2, 6, 5, 10, SoundEvents.ARMOR_EQUIP_LEATHER);
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> MAHITO_PATCHWORK = register("mahito_patchwork", 2, 5, 4, 10, SoundEvents.ARMOR_EQUIP_LEATHER);
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> GETO_KESA = register("geto_kesa", 2, 6, 5, 14, SoundEvents.ARMOR_EQUIP_LEATHER);

    static DeferredHolder<ArmorMaterial, ArmorMaterial> register(String name, int helmet, int chest, int legs, int enchant, Holder<SoundEvent> sound) {
        return MATERIALS.register(name, () -> new ArmorMaterial(
                Util.make(new EnumMap<>(ArmorItem.Type.class), map -> {
                    map.put(ArmorItem.Type.HELMET, helmet);
                    map.put(ArmorItem.Type.CHESTPLATE, chest);
                    map.put(ArmorItem.Type.LEGGINGS, legs);
                    map.put(ArmorItem.Type.BOOTS, Math.max(0, helmet - 1));
                    map.put(ArmorItem.Type.BODY, chest);
                }),
                enchant, sound, () -> Ingredient.of(Items.STRING),
                List.of(new ArmorMaterial.Layer(JJKMod.id(name))), 0f, 0f));
    }

    private ModArmorMaterials() {}
}
