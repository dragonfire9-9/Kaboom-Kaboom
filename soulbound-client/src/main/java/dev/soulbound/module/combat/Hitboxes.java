package dev.soulbound.module.combat;

import dev.soulbound.module.Category;
import dev.soulbound.module.Module;
import dev.soulbound.setting.BoolSetting;
import dev.soulbound.setting.NumberSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;

/** Makes entities a little easier to hit by widening how far your crosshair counts as "on" them. */
public final class Hitboxes extends Module {
    public static Hitboxes INSTANCE;
    private final NumberSetting players, monsters, animals;
    private final BoolSetting onlyWeapon;

    public Hitboxes() {
        super("Hitboxes", "Widen entity hitboxes for your crosshair.", Category.COMBAT);
        INSTANCE = this;
        players = add(new NumberSetting("Players", "Extra margin around players", 0.15, 0, 1, 0.05, "m"));
        monsters = add(new NumberSetting("Monsters", "Extra margin around hostile mobs", 0.1, 0, 1, 0.05, "m"));
        animals = add(new NumberSetting("Animals", "Extra margin around passive mobs", 0, 0, 1, 0.05, "m"));
        onlyWeapon = add(new BoolSetting("Only With Weapon", "Only while holding a sword or axe", true));
    }

    public static float extra(Entity e) {
        Hitboxes h = INSTANCE;
        if (h == null || !h.isEnabled() || mc.player == null || e == mc.player) return 0;
        if (h.onlyWeapon.isOn()) {
            var held = mc.player.getMainHandStack();
            if (!held.isIn(net.minecraft.registry.tag.ItemTags.SWORDS) && !held.isIn(net.minecraft.registry.tag.ItemTags.AXES)) return 0;
        }
        if (e instanceof PlayerEntity) return h.players.getFloat();
        if (e instanceof Monster) return h.monsters.getFloat();
        if (e instanceof AnimalEntity) return h.animals.getFloat();
        return 0;
    }
}
