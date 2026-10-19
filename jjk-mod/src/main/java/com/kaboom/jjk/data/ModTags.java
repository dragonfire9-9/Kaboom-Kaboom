package com.kaboom.jjk.data;

import com.kaboom.jjk.JJKMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;

public final class ModTags {
    /** Cursed spirits: grant jujutsu XP and are invisible to players without cursed energy. */
    public static final TagKey<EntityType<?>> CURSES = TagKey.create(Registries.ENTITY_TYPE, JJKMod.id("curses"));
    /** Our bosses (and any modded boss added by packs): Prison Realm holds them for less time. */
    public static final TagKey<EntityType<?>> BOSSES = TagKey.create(Registries.ENTITY_TYPE, JJKMod.id("bosses"));
    /** Structures that radiate negative emotion: curses spawn much more often inside them. */
    public static final TagKey<net.minecraft.world.level.levelgen.structure.Structure> HOTSPOTS =
            TagKey.create(Registries.STRUCTURE, JJKMod.id("negative_emotion_hotspots"));
    /** Cursed tools and ordinary weapons that benefit from the Zenin clan's weapon mastery. */
    public static final TagKey<Item> WEAPONS = TagKey.create(Registries.ITEM, JJKMod.id("weapons"));

    private ModTags() {}
}
