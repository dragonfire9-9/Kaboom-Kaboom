package dev.soulbound.module;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class ModuleManager {
    private final List<Module> modules = new ArrayList<>();

    /** Register modules here, e.g. {@code register(new MyModule());} */
    public void init() {
        modules.sort(Comparator.comparing(m -> m.getName().toLowerCase()));
    }

    public void register(Module module) { modules.add(module); }

    public List<Module> all() { return Collections.unmodifiableList(modules); }

    public List<Module> byCategory(Category category) {
        return modules.stream().filter(m -> m.getCategory() == category).toList();
    }

    public long activeCount() { return modules.stream().filter(Module::isEnabled).count(); }

    public void onTick() {
        for (Module m : modules) if (m.isEnabled()) m.onTick();
    }

    public void onKey(int key) {
        for (Module m : modules) if (m.getKeybind().isBound() && m.getKeybind().get() == key) m.toggle();
    }
}
