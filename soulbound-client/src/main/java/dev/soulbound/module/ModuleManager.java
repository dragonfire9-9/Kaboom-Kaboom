package dev.soulbound.module;

import dev.soulbound.module.bots.CrystalBot;
import dev.soulbound.module.bots.BuilderBot;
import dev.soulbound.module.bots.SwordBot;
import dev.soulbound.module.bots.UhcBot;
import dev.soulbound.module.combat.AimAssist;
import dev.soulbound.module.combat.AutoHitCrystal;
import dev.soulbound.module.combat.TriggerBot;
import dev.soulbound.module.misc.AutoFish;
import dev.soulbound.module.misc.AutoMlg;
import dev.soulbound.module.misc.AutoTotem;
import dev.soulbound.module.misc.MiddleClickPearl;
import dev.soulbound.module.movement.AutoWalk;
import dev.soulbound.module.movement.SafeWalk;
import dev.soulbound.module.movement.ToggleSprint;
import dev.soulbound.module.util.AntiAfk;
import dev.soulbound.module.util.AutoEat;
import dev.soulbound.module.util.AutoRespawn;
import dev.soulbound.module.util.AutoTool;
import dev.soulbound.module.util.ChestStealer;
import dev.soulbound.module.visual.Esp;
import dev.soulbound.module.visual.Fullbright;
import dev.soulbound.module.visual.LowFire;
import dev.soulbound.module.visual.NoHurtCam;
import dev.soulbound.module.visual.Zoom;
import dev.soulbound.module.visual.CustomHud;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class ModuleManager {
    private final List<Module> modules = new ArrayList<>();

    /** Register modules here, e.g. {@code register(new MyModule());} */
    public void init() {
        register(new TriggerBot());
        register(new AimAssist());
        register(new AutoHitCrystal());
        register(new SwordBot());
        register(new UhcBot());
        register(new BuilderBot());
        register(new CrystalBot());
        register(new AutoMlg());
        register(new CustomHud());
        register(new Fullbright());
        register(new Esp());
        register(new Zoom());
        register(new NoHurtCam());
        register(new LowFire());
        register(new AutoTool());
        register(new AutoEat());
        register(new AutoRespawn());
        register(new ChestStealer());
        register(new AntiAfk());
        register(new ToggleSprint());
        register(new AutoWalk());
        register(new SafeWalk());
        register(new AutoTotem());
        register(new MiddleClickPearl());
        register(new AutoFish());
        modules.sort(Comparator.comparing(m -> m.getName().toLowerCase()));
    }

    public void register(Module module) { modules.add(module); }

    public List<Module> all() { return Collections.unmodifiableList(modules); }

    public List<Module> byCategory(Category category) {
        return modules.stream().filter(m -> m.getCategory() == category).toList();
    }

    @SuppressWarnings("unchecked")
    public <T extends Module> T get(Class<T> type) {
        for (Module m : modules) if (type.isInstance(m)) return (T) m;
        return null;
    }

    public long activeCount() { return modules.stream().filter(Module::isEnabled).count(); }

    public void onTick() {
        for (Module m : modules) if (m.isEnabled()) m.onTick();
    }

    public void onKey(int key) {
        for (Module m : modules) if (m.getKeybind().isBound() && m.getKeybind().get() == key) m.toggle();
    }
}
