package dev.soulbound.event;

import net.minecraft.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Tiny client-side event hub for things Fabric API doesn't expose. */
public final class Events {
    private Events() {}

    /** Fired right before the local player attacks an entity (any source: you, a bot, a module). */
    public static final List<Consumer<Entity>> ATTACK = new ArrayList<>();

    public static void attack(Entity target) {
        for (Consumer<Entity> c : ATTACK) {
            try { c.accept(target); } catch (Exception ignored) { }
        }
    }
}
