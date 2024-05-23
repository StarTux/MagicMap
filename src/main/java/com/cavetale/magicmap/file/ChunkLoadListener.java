package com.cavetale.magicmap.file;

import java.util.function.Consumer;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import static com.cavetale.magicmap.MagicMapPlugin.plugin;

/**
 * Warn about unintended chunk loads during other operations.  This
 * singleton instance will warn to console as long as a callback is
 * set.
 */
public final class ChunkLoadListener implements Listener {
    private static ChunkLoadListener instance;
    @Setter private Consumer<Chunk> callback;

    public static ChunkLoadListener chunkLoadListener() {
        if (instance == null) {
            instance = new ChunkLoadListener();
            Bukkit.getPluginManager().registerEvents(instance, plugin());
            plugin().getLogger().info("[ChunkLoadListener] Enabled");
        }
        return instance;
    }

    @EventHandler
    private void onChunkLoad(ChunkLoadEvent event) {
        if (callback == null) return;
        callback.accept(event.getChunk());
    }
}
