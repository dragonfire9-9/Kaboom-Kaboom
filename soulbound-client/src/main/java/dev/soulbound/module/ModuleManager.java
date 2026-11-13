package dev.soulbound.module;

import dev.soulbound.module.bots.CrystalBot;
import dev.soulbound.module.bots.SwordBot;
import dev.soulbound.module.misc.AutoMlg;
import dev.soulbound.module.visual.CustomHud;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class ModuleManager {
    private final List<Module> modules = new ArrayList<>();

    /** Register modules here, e.g. {@code register(new MyModule());} */
    public void init() {
        register(new SwordBot());
        register(new CrystalBot());
        register(new AutoMlg());
        register(new CustomHud());
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
