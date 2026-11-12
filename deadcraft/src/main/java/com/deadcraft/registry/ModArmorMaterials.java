package com.deadcraft.registry;

import com.deadcraft.DeadCraft;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.List;
import java.util.function.Supplier;

public final class ModArmorMaterials {
    public static final DeferredRegister<ArmorMaterial> MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, DeadCraft.MODID);

    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> NECRITE = register("necrite", 2, 5, 6, 2, 3, 15, SoundEvents.ARMOR_EQUIP_CHAIN, 0.5f, 0f, () -> ModItems.NECRITE_INGOT.get());
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> BLOODSTONE = register("bloodstone", 3, 6, 7, 3, 4, 18, SoundEvents.ARMOR_EQUIP_DIAMOND, 1.5f, 0f, () -> ModItems.BLOODSTONE.get());
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> VOIDSTEEL = register("voidsteel", 3, 6, 8, 3, 5, 12, SoundEvents.ARMOR_EQUIP_NETHERITE, 3.0f, 0.1f, () -> ModItems.VOIDSTEEL_INGOT.get());
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> STARDUST = register("stardust", 3, 6, 8, 3, 5, 25, SoundEvents.ARMOR_EQUIP_GOLD, 2.5f, 0.05f, () -> ModItems.STAR_SHARD.get());

    private ModArmorMaterials() {}

    private static DeferredHolder<ArmorMaterial, ArmorMaterial> register(String name, int boots, int legs, int chest, int helmet,
            int body, int enchantability, Holder<net.minecraft.sounds.SoundEvent> sound, float toughness, float knockback,
            Supplier<ItemLike> repair) {
        return MATERIALS.register(name, () -> {
            EnumMap<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
            defense.put(ArmorItem.Type.BOOTS, boots);
            defense.put(ArmorItem.Type.LEGGINGS, legs);
            defense.put(ArmorItem.Type.CHESTPLATE, chest);
            defense.put(ArmorItem.Type.HELMET, helmet);
            defense.put(ArmorItem.Type.BODY, body);
            return new ArmorMaterial(defense, enchantability, sound, () -> Ingredient.of(repair.get()),
                    List.of(new ArmorMaterial.Layer(DeadCraft.id(name))), toughness, knockback);
        });
    }
}
