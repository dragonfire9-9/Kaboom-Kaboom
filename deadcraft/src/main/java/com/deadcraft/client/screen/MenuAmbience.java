package com.deadcraft.client.screen;

import com.deadcraft.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;

/** The faint static hiss that loops under the title screen. */
public class MenuAmbience extends AbstractTickableSoundInstance {
    public MenuAmbience() {
        super(ModSounds.UI_STATIC.get(), SoundSource.MASTER, SoundInstance.createUnseededRandom());
        this.looping = true;
        this.delay = 0;
        this.volume = 0.18f;
        this.relative = true;
        this.attenuation = Attenuation.NONE;
    }

    @Override
    public void tick() {
        if (!(Minecraft.getInstance().screen instanceof DeadTitleScreen)) {
            volume -= 0.02f;
            if (volume <= 0) stop();
        }
    }
}
