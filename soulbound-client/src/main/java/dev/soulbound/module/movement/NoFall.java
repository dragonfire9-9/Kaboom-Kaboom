package dev.soulbound.module.movement;

import dev.soulbound.module.Category;
import dev.soulbound.module.Module;
import dev.soulbound.setting.BoolSetting;
import dev.soulbound.setting.NumberSetting;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

/** Takes no fall damage by telling the server you touched the ground while falling. */
public final class NoFall extends Module {
    private final NumberSetting minFall;
    private final BoolSetting notWithElytra;

    public NoFall() {
        super("No Fall", "Take no fall damage.", Category.MOVEMENT);
        minFall = add(new NumberSetting("Min Fall", "Only act on falls at least this long", 2.5, 2, 10, 0.5, "m"));
        notWithElytra = add(new BoolSetting("Not While Gliding", "Leave elytra flight alone", true));
    }

    @Override
    public void onTick() {
        var p = mc.player;
        if (p == null || mc.getNetworkHandler() == null || p.isCreative()) return;
        if (notWithElytra.isOn() && p.isGliding()) return;
        if (p.fallDistance > minFall.get() && p.getVelocity().y < -0.3) {
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true, p.horizontalCollision));
        }
    }
}
