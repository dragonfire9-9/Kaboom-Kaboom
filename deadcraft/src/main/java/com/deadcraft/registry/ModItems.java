package com.deadcraft.registry;

import com.deadcraft.DeadCraft;
import com.deadcraft.entity.DeadMob;
import com.deadcraft.item.*;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.common.SimpleTier;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(DeadCraft.MODID);

    /** Ordered lists used by the creative tabs. */
    public static final List<DeferredItem<? extends Item>> MATERIALS = new ArrayList<>();
    public static final List<DeferredItem<? extends Item>> ARSENAL = new ArrayList<>();
    public static final List<DeferredItem<? extends Item>> BLOCK_ITEMS = new ArrayList<>();
    public static final Map<DeadMob, DeferredItem<DeferredSpawnEggItem>> SPAWN_EGGS = new EnumMap<>(DeadMob.class);

    // ---- Materials ---------------------------------------------------------------------------
    public static final DeferredItem<Item> RAW_NECRITE = material("raw_necrite", Rarity.COMMON);
    public static final DeferredItem<Item> NECRITE_INGOT = material("necrite_ingot", Rarity.COMMON);
    public static final DeferredItem<Item> BLOODSTONE = material("bloodstone", Rarity.UNCOMMON);
    public static final DeferredItem<Item> RAW_VOIDSTEEL = material("raw_voidsteel", Rarity.UNCOMMON);
    public static final DeferredItem<Item> VOIDSTEEL_INGOT = material("voidsteel_ingot", Rarity.UNCOMMON);
    public static final DeferredItem<Item> STAR_SHARD = material("star_shard", Rarity.RARE);
    public static final DeferredItem<Item> HOLLOW_ESSENCE = material("hollow_essence", Rarity.UNCOMMON);
    public static final DeferredItem<Item> ECTOPLASM = material("ectoplasm", Rarity.COMMON);
    public static final DeferredItem<Item> DEMON_HORN = material("demon_horn", Rarity.UNCOMMON);
    public static final DeferredItem<Item> SCRAP_METAL = material("scrap_metal", Rarity.COMMON);
    // Boss trophies
    public static final DeferredItem<Item> FRACTURE_CORE = material("fracture_core", Rarity.EPIC);
    public static final DeferredItem<Item> CHRONOS_HOURGLASS = material("chronos_hourglass", Rarity.EPIC);
    public static final DeferredItem<Item> SKELETON_CROWN = material("skeleton_crown", Rarity.EPIC);
    public static final DeferredItem<Item> DIM_HEART = material("dim_heart", Rarity.EPIC);
    public static final DeferredItem<Item> PETRIFIED_EYE = material("petrified_eye", Rarity.EPIC);

    // ---- Food: five creatures drop meat that cooks --------------------------------------------
    public static final DeferredItem<Item> RAW_ASH_VENISON = food("raw_ash_venison", 3, 0.3f);
    public static final DeferredItem<Item> COOKED_ASH_VENISON = food("cooked_ash_venison", 8, 0.8f);
    public static final DeferredItem<Item> RAW_GLOOM_PORK = food("raw_gloom_pork", 3, 0.3f);
    public static final DeferredItem<Item> COOKED_GLOOM_PORK = food("cooked_gloom_pork", 8, 0.8f);
    public static final DeferredItem<Item> RAW_CINDER_FOWL = food("raw_cinder_fowl", 2, 0.3f);
    public static final DeferredItem<Item> COOKED_CINDER_FOWL = food("cooked_cinder_fowl", 6, 0.6f);
    public static final DeferredItem<Item> RAW_MARROW_BEEF = food("raw_marrow_beef", 3, 0.3f);
    public static final DeferredItem<Item> COOKED_MARROW_BEEF = food("cooked_marrow_beef", 9, 0.85f);
    public static final DeferredItem<Item> RAW_DUSK_HARE = food("raw_dusk_hare", 2, 0.3f);
    public static final DeferredItem<Item> COOKED_DUSK_HARE = food("cooked_dusk_hare", 5, 0.6f);

    // ---- Melee weapons -------------------------------------------------------------------------
    public static final Tier NECRITE_TIER = new SimpleTier(BlockTags.INCORRECT_FOR_IRON_TOOL, 900, 7f, 2.5f, 15, () -> Ingredient.of(NECRITE_INGOT.get()));
    public static final Tier BLOODSTONE_TIER = new SimpleTier(BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 1600, 8f, 3.5f, 18, () -> Ingredient.of(BLOODSTONE.get()));
    public static final Tier VOIDSTEEL_TIER = new SimpleTier(BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 2400, 9f, 4.5f, 14, () -> Ingredient.of(VOIDSTEEL_INGOT.get()));
    public static final Tier STARDUST_TIER = new SimpleTier(BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 2200, 9f, 4f, 25, () -> Ingredient.of(STAR_SHARD.get()));

    public static final DeferredItem<DeadWeaponItem> BONE_CLEAVER = weapon("bone_cleaver", NECRITE_TIER, 4, -3.0f, DeadWeaponItem.Trait.CLEAVE, Rarity.COMMON);
    public static final DeferredItem<DeadWeaponItem> BLOODREAVER = weapon("bloodreaver", BLOODSTONE_TIER, 3, -2.4f, DeadWeaponItem.Trait.LIFESTEAL, Rarity.UNCOMMON);
    public static final DeferredItem<DeadWeaponItem> VOIDSTEEL_LONGSWORD = weapon("voidsteel_longsword", VOIDSTEEL_TIER, 4, -2.6f, DeadWeaponItem.Trait.VOID, Rarity.RARE);
    public static final DeferredItem<DeadWeaponItem> STAR_EDGE = weapon("star_edge", STARDUST_TIER, 3, -2.2f, DeadWeaponItem.Trait.STARFALL, Rarity.RARE);
    public static final DeferredItem<DeadWeaponItem> HOLLOW_SCYTHE = weapon("hollow_scythe", BLOODSTONE_TIER, 6, -2.8f, DeadWeaponItem.Trait.REAP, Rarity.RARE);
    public static final DeferredItem<DeadWeaponItem> CHRONO_BLADE = weapon("chrono_blade", VOIDSTEEL_TIER, 4, -2.0f, DeadWeaponItem.Trait.TIME, Rarity.EPIC);
    public static final DeferredItem<DeadWeaponItem> FRACTURE_GREATSWORD = weapon("fracture_greatsword", VOIDSTEEL_TIER, 7, -3.2f, DeadWeaponItem.Trait.SHATTER, Rarity.EPIC);

    // ---- Guns and ammunition -------------------------------------------------------------------
    public static final DeferredItem<Item> PISTOL_ROUNDS = ammo("pistol_rounds");
    public static final DeferredItem<Item> SHOTGUN_SHELLS = ammo("shotgun_shells");
    public static final DeferredItem<Item> RIFLE_ROUNDS = ammo("rifle_rounds");
    public static final DeferredItem<GunItem> RUSTED_REVOLVER = gun("rusted_revolver", GunItem.GunType.REVOLVER);
    public static final DeferredItem<GunItem> DOOMSDAY_SHOTGUN = gun("doomsday_shotgun", GunItem.GunType.SHOTGUN);
    public static final DeferredItem<GunItem> MARKSMAN_RIFLE = gun("marksman_rifle", GunItem.GunType.RIFLE);
    public static final DeferredItem<GunItem> SCRAP_SMG = gun("scrap_smg", GunItem.GunType.SMG);

    // ---- Magic ---------------------------------------------------------------------------------
    public static final DeferredItem<GrimoireItem> GRIMOIRE_OF_AWAKENING = arsenal(ITEMS.registerItem("grimoire_of_awakening",
            GrimoireItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    public static final DeferredItem<ManaTonicItem> MANA_TONIC = arsenal(ITEMS.registerItem("mana_tonic",
            ManaTonicItem::new, new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<SpellItem> SOUL_STAFF = spell("soul_staff", SpellItem.Spell.SOUL_BOLT);
    public static final DeferredItem<SpellItem> EMBER_STAFF = spell("ember_staff", SpellItem.Spell.EMBER);
    public static final DeferredItem<SpellItem> FROST_TOME = spell("frost_tome", SpellItem.Spell.FROST_NOVA);
    public static final DeferredItem<SpellItem> VOID_TOME = spell("void_tome", SpellItem.Spell.BLINK);
    public static final DeferredItem<SpellItem> STORM_ROD = spell("storm_rod", SpellItem.Spell.STORM);
    public static final DeferredItem<SpellItem> BLOOD_CHALICE = spell("blood_chalice", SpellItem.Spell.BLOOD_PACT);

    // ---- Armour --------------------------------------------------------------------------------
    public static final DeferredItem<ArmorItem> NECRITE_HELMET = armor("necrite_helmet", ModArmorMaterials.NECRITE, ArmorItem.Type.HELMET, 24);
    public static final DeferredItem<ArmorItem> NECRITE_CHESTPLATE = armor("necrite_chestplate", ModArmorMaterials.NECRITE, ArmorItem.Type.CHESTPLATE, 24);
    public static final DeferredItem<ArmorItem> NECRITE_LEGGINGS = armor("necrite_leggings", ModArmorMaterials.NECRITE, ArmorItem.Type.LEGGINGS, 24);
    public static final DeferredItem<ArmorItem> NECRITE_BOOTS = armor("necrite_boots", ModArmorMaterials.NECRITE, ArmorItem.Type.BOOTS, 24);
    public static final DeferredItem<ArmorItem> BLOODSTONE_HELMET = armor("bloodstone_helmet", ModArmorMaterials.BLOODSTONE, ArmorItem.Type.HELMET, 33);
    public static final DeferredItem<ArmorItem> BLOODSTONE_CHESTPLATE = armor("bloodstone_chestplate", ModArmorMaterials.BLOODSTONE, ArmorItem.Type.CHESTPLATE, 33);
    public static final DeferredItem<ArmorItem> BLOODSTONE_LEGGINGS = armor("bloodstone_leggings", ModArmorMaterials.BLOODSTONE, ArmorItem.Type.LEGGINGS, 33);
    public static final DeferredItem<ArmorItem> BLOODSTONE_BOOTS = armor("bloodstone_boots", ModArmorMaterials.BLOODSTONE, ArmorItem.Type.BOOTS, 33);
    public static final DeferredItem<ArmorItem> VOIDSTEEL_HELMET = armor("voidsteel_helmet", ModArmorMaterials.VOIDSTEEL, ArmorItem.Type.HELMET, 40);
    public static final DeferredItem<ArmorItem> VOIDSTEEL_CHESTPLATE = armor("voidsteel_chestplate", ModArmorMaterials.VOIDSTEEL, ArmorItem.Type.CHESTPLATE, 40);
    public static final DeferredItem<ArmorItem> VOIDSTEEL_LEGGINGS = armor("voidsteel_leggings", ModArmorMaterials.VOIDSTEEL, ArmorItem.Type.LEGGINGS, 40);
    public static final DeferredItem<ArmorItem> VOIDSTEEL_BOOTS = armor("voidsteel_boots", ModArmorMaterials.VOIDSTEEL, ArmorItem.Type.BOOTS, 40);
    public static final DeferredItem<ArmorItem> STARDUST_HELMET = armor("stardust_helmet", ModArmorMaterials.STARDUST, ArmorItem.Type.HELMET, 37);
    public static final DeferredItem<ArmorItem> STARDUST_CHESTPLATE = armor("stardust_chestplate", ModArmorMaterials.STARDUST, ArmorItem.Type.CHESTPLATE, 37);
    public static final DeferredItem<ArmorItem> STARDUST_LEGGINGS = armor("stardust_leggings", ModArmorMaterials.STARDUST, ArmorItem.Type.LEGGINGS, 37);
    public static final DeferredItem<ArmorItem> STARDUST_BOOTS = armor("stardust_boots", ModArmorMaterials.STARDUST, ArmorItem.Type.BOOTS, 37);

    // ---- Travel --------------------------------------------------------------------------------
    public static final DeferredItem<RiftKeyItem> RIFT_KEY_HELLS_PARADISE = riftKey("rift_key_hells_paradise", 1);
    public static final DeferredItem<RiftKeyItem> RIFT_KEY_SKYFALL_TORN = riftKey("rift_key_skyfall_torn", 2);
    public static final DeferredItem<RiftKeyItem> RIFT_KEY_LIGHTS_DESCENT = riftKey("rift_key_lights_descent", 3);
    public static final DeferredItem<RiftKeyItem> RIFT_KEY_DISPOSED_SKIES = riftKey("rift_key_disposed_skies", 4);
    public static final DeferredItem<VehicleItem> RUIN_RUNNER = arsenal(ITEMS.registerItem("ruin_runner",
            p -> new VehicleItem(p, () -> ModEntities.RUIN_RUNNER.get()), new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    public static final DeferredItem<VehicleItem> SKY_SKIFF = arsenal(ITEMS.registerItem("sky_skiff",
            p -> new VehicleItem(p, () -> ModEntities.SKY_SKIFF.get()), new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    // ---- Boss sigils ---------------------------------------------------------------------------
    public static final DeferredItem<BossSigilItem> SIGIL_FRACTURE = sigil("sigil_fracture", DeadMob.FRACTURE);
    public static final DeferredItem<BossSigilItem> SIGIL_CHRONOS = sigil("sigil_chronos", DeadMob.CHRONOS);
    public static final DeferredItem<BossSigilItem> SIGIL_SKELETON = sigil("sigil_skeleton", DeadMob.SKELETON);
    public static final DeferredItem<BossSigilItem> SIGIL_DIM_LIGHT_DEMON = sigil("sigil_dim_light_demon", DeadMob.DIM_LIGHT_DEMON);
    public static final DeferredItem<BossSigilItem> SIGIL_PETRIFIED = sigil("sigil_petrified", DeadMob.PETRIFIED);

    // ---- Block items ---------------------------------------------------------------------------
    static {
        for (DeferredBlock<?> block : new DeferredBlock<?>[]{
                ModBlocks.NECRITE_ORE, ModBlocks.DEEPSLATE_NECRITE_ORE, ModBlocks.BLOODSTONE_ORE, ModBlocks.DEEPSLATE_BLOODSTONE_ORE,
                ModBlocks.VOIDSTEEL_ORE, ModBlocks.STARDUST_ORE, ModBlocks.NECRITE_BLOCK, ModBlocks.BLOODSTONE_BLOCK,
                ModBlocks.VOIDSTEEL_BLOCK, ModBlocks.STARDUST_BLOCK, ModBlocks.CHARRED_LOG, ModBlocks.GRAVESTONE,
                ModBlocks.BLOOD_PUDDLE, ModBlocks.DREAD_LANTERN}) {
            BLOCK_ITEMS.add(ITEMS.registerSimpleBlockItem(block));
        }
        for (DeadMob mob : DeadMob.values()) {
            int bg = mob.baseColor | 0xff000000;
            int fg = mob.accentColor | 0xff000000;
            SPAWN_EGGS.put(mob, ITEMS.registerItem(mob.id + "_spawn_egg",
                    p -> new DeferredSpawnEggItem(() -> ModEntities.type(mob), bg, fg, p), new Item.Properties()));
        }
    }

    private ModItems() {}

    private static <T extends Item> DeferredItem<T> arsenal(DeferredItem<T> item) {
        ARSENAL.add(item);
        return item;
    }

    private static DeferredItem<Item> material(String name, Rarity rarity) {
        DeferredItem<Item> item = ITEMS.registerSimpleItem(name, new Item.Properties().rarity(rarity));
        MATERIALS.add(item);
        return item;
    }

    private static DeferredItem<Item> food(String name, int nutrition, float saturation) {
        DeferredItem<Item> item = ITEMS.registerSimpleItem(name, new Item.Properties()
                .food(new FoodProperties.Builder().nutrition(nutrition).saturationModifier(saturation).build()));
        MATERIALS.add(item);
        return item;
    }

    private static DeferredItem<DeadWeaponItem> weapon(String name, Tier tier, int damage, float speed, DeadWeaponItem.Trait trait, Rarity rarity) {
        return arsenal(ITEMS.registerItem(name, p -> new DeadWeaponItem(tier, trait, p),
                new Item.Properties().rarity(rarity).attributes(SwordItem.createAttributes(tier, damage, speed))));
    }

    private static DeferredItem<Item> ammo(String name) {
        return arsenal(ITEMS.registerSimpleItem(name, new Item.Properties().stacksTo(64)));
    }

    private static DeferredItem<GunItem> gun(String name, GunItem.GunType type) {
        return arsenal(ITEMS.registerItem(name, p -> new GunItem(type, p),
                new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    }

    private static DeferredItem<SpellItem> spell(String name, SpellItem.Spell spell) {
        return arsenal(ITEMS.registerItem(name, p -> new SpellItem(spell, p),
                new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    }

    private static DeferredItem<ArmorItem> armor(String name, net.minecraft.core.Holder<ArmorMaterial> material, ArmorItem.Type type, int durability) {
        return arsenal(ITEMS.registerItem(name, p -> new ArmorItem(material, type, p),
                new Item.Properties().durability(type.getDurability(durability))));
    }

    private static DeferredItem<RiftKeyItem> riftKey(String name, int destination) {
        return arsenal(ITEMS.registerItem(name, p -> new RiftKeyItem(destination, p),
                new Item.Properties().durability(32).rarity(Rarity.RARE)));
    }

    private static DeferredItem<BossSigilItem> sigil(String name, DeadMob boss) {
        return arsenal(ITEMS.registerItem(name, p -> new BossSigilItem(boss, p),
                new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    }
}
