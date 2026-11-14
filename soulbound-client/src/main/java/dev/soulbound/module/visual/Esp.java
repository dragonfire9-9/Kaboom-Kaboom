package dev.soulbound.module.visual;

import dev.soulbound.module.Category;
import dev.soulbound.module.Module;
import dev.soulbound.setting.BoolSetting;
import dev.soulbound.setting.HeaderSetting;
import dev.soulbound.setting.ModeSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;

/** Draws a glowing outline around entities, visible through walls. */
public final class Esp extends Module {
    public static Esp INSTANCE;
    private final BoolSetting players, monsters, animals, items, invisible;
    private final ModeSetting color;

    public Esp() {
        super("ESP", "Outlines players and mobs through walls.", Category.VISUAL);
        INSTANCE = this;
        add(new HeaderSetting("SHOW"));
        players = add(new BoolSetting("Players", "Outline other players", true));
        monsters = add(new BoolSetting("Monsters", "Outline hostile mobs", false));
        animals = add(new BoolSetting("Animals", "Outline passive mobs", false));
        items = add(new BoolSetting("Items", "Outline dropped items", false));
        invisible = add(new BoolSetting("Invisible", "Also outline invisible entities", true));
        add(new HeaderSetting("LOOK"));
        color = add(new ModeSetting("Color", "Outline colour", "Health", "Theme", "Team", "Health", "White"));
    }

    public static boolean outline(Entity e) {
        Esp m = INSTANCE;
        if (m == null || !m.isEnabled() || e == mc.player || e == mc.getCameraEntity()) return false;
        if (e.isInvisible() && !m.invisible.isOn()) return false;
        if (e instanceof PlayerEntity) return m.players.isOn();
        if (e instanceof Monster) return m.monsters.isOn();
        if (e instanceof AnimalEntity) return m.animals.isOn();
        if (e instanceof ItemEntity) return m.items.isOn();
        return false;
    }

    /** Colour for the outline, or -1 to keep the team colour. */
    public static int color(Entity e) {
        Esp m = INSTANCE;
        if (m == null || !m.isEnabled() || !outline(e)) return -1;
        return switch (m.color.get()) {
            case "Theme" -> dev.soulbound.gui.render.Theme.accent() & 0xFFFFFF;
            case "White" -> 0xFFFFFF;
            case "Health" -> {
                if (!(e instanceof LivingEntity le)) yield 0xFFFFFF;
                float t = Math.max(0, Math.min(1, le.getHealth() / le.getMaxHealth()));
                int r = (int) (255 * Math.min(1, 2 * (1 - t))), g = (int) (255 * Math.min(1, 2 * t));
                yield r << 16 | g << 8 | 60;
            }
            default -> -1;
        };
    }
}
