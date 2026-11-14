package dev.soulbound.module.misc;

import dev.soulbound.module.Category;
import dev.soulbound.module.Module;
import dev.soulbound.setting.BoolSetting;
import dev.soulbound.setting.ModeSetting;
import dev.soulbound.setting.NumberSetting;
import dev.soulbound.setting.TextSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

import java.util.concurrent.ThreadLocalRandom;

/** Says GG in chat when a player you were fighting dies. */
public final class AutoGG extends Module {
    private final TextSetting message;
    private final ModeSetting style;
    private final NumberSetting delay, cooldown;
    private final BoolSetting includeName, onlyKiller;
    private PlayerEntity fighting;
    private long hitAt, sayAt = -1, lastSaid;
    private String pending;

    public AutoGG() {
        super("Auto GG", "Say GG when you win a fight.", Category.MISC);
        style = add(new ModeSetting("Style", "Preset message, or your own text below", "gg", "gg", "GG", "gg wp", "ez", "Custom"));
        message = add(new TextSetting("Message", "Your own message (use {name} for their name)", "gg {name}").visibleWhen(() -> style.is("Custom")));
        includeName = add(new BoolSetting("Include Name", "Add their name after preset messages", false).visibleWhen(() -> !style.is("Custom")));
        delay = add(new NumberSetting("Delay", "Wait before typing it", 1.2, 0, 5, 0.1, "s"));
        cooldown = add(new NumberSetting("Cooldown", "At most one message per this long", 10, 0, 120, 5, "s"));
        onlyKiller = add(new BoolSetting("Only Your Kills", "Only if you hit them in the last few seconds", true));
        dev.soulbound.event.Events.ATTACK.add(this::onAttack);
    }

    private void onAttack(Entity e) {
        if (e instanceof PlayerEntity pe) {
            fighting = pe;
            hitAt = System.currentTimeMillis();
        }
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.getNetworkHandler() == null) return;
        long now = System.currentTimeMillis();
        if (pending != null && now >= sayAt) {
            mc.getNetworkHandler().sendChatMessage(pending);
            pending = null;
            lastSaid = now;
        }
        if (fighting == null) return;
        boolean recent = now - hitAt < 6000;
        if (!recent && onlyKiller.isOn()) {
            fighting = null;
            return;
        }
        if (fighting.isDead() || fighting.getHealth() <= 0) {
            String name = fighting.getName().getString();
            fighting = null;
            if (now - lastSaid < cooldown.get() * 1000) return;
            String text = style.is("Custom") ? message.get().replace("{name}", name) : style.get() + (includeName.isOn() ? " " + name : "");
            if (text.isBlank()) return;
            pending = text;
            sayAt = now + (long) (delay.get() * 1000) + ThreadLocalRandom.current().nextLong(250);
        }
    }
}
