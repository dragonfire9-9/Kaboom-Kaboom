package dev.soulbound.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.soulbound.SoulBound;
import dev.soulbound.module.Configurable;
import dev.soulbound.module.Module;
import dev.soulbound.setting.Setting;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path file = FabricLoader.getInstance().getConfigDir().resolve("soulbound.json");

    public void load() {
        if (!Files.exists(file)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            JsonObject client = obj(root, "client");
            for (Configurable page : SoulBound.settings().allPages()) readSettings(obj(client, page.getName()), page);
            JsonObject modules = obj(root, "modules");
            for (Module m : SoulBound.modules().all()) {
                JsonObject mo = obj(modules, m.getName());
                readSettings(mo, m);
                if (mo.has("keybind")) m.getKeybind().fromJson(mo.get("keybind"));
                if (mo.has("enabled") && mo.get("enabled").getAsBoolean()) m.setEnabled(true);
            }
        } catch (Exception e) {
            SoulBound.LOGGER.error("Failed to load config", e);
        }
    }

    public void save() {
        JsonObject root = new JsonObject();
        JsonObject client = new JsonObject();
        for (Configurable page : SoulBound.settings().allPages()) client.add(page.getName(), writeSettings(page));
        root.add("client", client);
        JsonObject modules = new JsonObject();
        for (Module m : SoulBound.modules().all()) {
            JsonObject mo = writeSettings(m);
            mo.addProperty("enabled", m.isEnabled());
            mo.add("keybind", m.getKeybind().toJson());
            modules.add(m.getName(), mo);
        }
        root.add("modules", modules);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(root));
        } catch (IOException e) {
            SoulBound.LOGGER.error("Failed to save config", e);
        }
    }

    private static JsonObject obj(JsonObject parent, String key) {
        JsonElement e = parent.get(key);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
    }

    private static void readSettings(JsonObject o, Configurable c) {
        for (Setting<?> s : c.getSettings()) {
            JsonElement e = o.get(s.getName());
            if (e != null) {
                try { s.fromJson(e); } catch (Exception ignored) { }
            }
        }
    }

    private static JsonObject writeSettings(Configurable c) {
        JsonObject o = new JsonObject();
        for (Setting<?> s : c.getSettings()) o.add(s.getName(), s.toJson());
        return o;
    }
}
