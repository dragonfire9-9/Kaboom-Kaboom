package dev.soulbound.mixin;

import net.minecraft.client.option.SimpleOption;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SimpleOption.class)
public interface SimpleOptionAccessor<T> {
    /** Sets the value without validation (e.g. gamma above 100%). */
    @Accessor("value")
    void soulbound$setRaw(T value);
}
