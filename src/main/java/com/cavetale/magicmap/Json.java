package com.cavetale.magicmap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.function.Supplier;
import org.bukkit.plugin.java.JavaPlugin;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class Json {
    private final JavaPlugin plugin;
    private final Gson gson = new Gson();
    private final Gson prettyGson = new GsonBuilder().setPrettyPrinting().create();

    public <T> T load(final String fn, final Class<T> clazz, final Supplier<T> dfl) {
        File file = new File(plugin.getDataFolder(), fn);
        if (!file.exists()) {
            return dfl.get();
        }
        try (FileReader fr = new FileReader(file)) {
            return gson.fromJson(fr, clazz);
        } catch (FileNotFoundException fnfr) {
            return dfl.get();
        } catch (IOException ioe) {
            throw new IllegalStateException("Loading " + file, ioe);
        }
    }

    public <T> T load(final String fn, final Class<T> clazz) {
        return load(fn, clazz, () -> null);
    }

    public void save(final String fn, final Object obj, final boolean pretty) {
        File dir = plugin.getDataFolder();
        dir.mkdirs();
        File file = new File(dir, fn);
        try (FileWriter fw = new FileWriter(file)) {
            Gson gs = pretty ? prettyGson : gson;
            gs.toJson(obj, fw);
        } catch (IOException ioe) {
            throw new IllegalStateException("Saving " + file, ioe);
        }
    }

    public void save(final String fn, final Object obj) {
        save(fn, obj, false);
    }
}
