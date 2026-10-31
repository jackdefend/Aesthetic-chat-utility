package dev.jackdefend.aestheticchat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Saved custom colors and nicknames, keyed by player UUID. Lives in the server's config folder. */
public final class PlayerStore {
    public static final class Entry {
        public Integer color;
        public String nickname;
        /** The nickname's own color (separate from the name color). */
        public Integer nickColor;
        /** The locator bar color this mod last set, so it can tell its own color from an admin's /waypoint color. */
        public Integer locatorSet;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TYPE = new TypeToken<Map<String, Entry>>() {}.getType();

    private final Path file;
    private final Map<UUID, Entry> data = new ConcurrentHashMap<>();

    private PlayerStore(Path file) { this.file = file; }

    public static PlayerStore load(Path file) {
        PlayerStore store = new PlayerStore(file);
        if (Files.exists(file)) {
            try {
                Map<String, Entry> raw = GSON.fromJson(Files.readString(file), TYPE);
                if (raw != null) {
                    raw.forEach((k, v) -> {
                        try { if (v != null) store.data.put(UUID.fromString(k), v); }
                        catch (IllegalArgumentException ignored) {}
                    });
                }
            } catch (Exception e) {
                AestheticChatUtility.LOGGER.error("Could not read player data", e);
            }
        }
        return store;
    }

    public Integer getColor(UUID id) {
        Entry e = data.get(id);
        return e == null ? null : e.color;
    }

    public Integer getNickColor(UUID id) {
        Entry e = data.get(id);
        return e == null ? null : e.nickColor;
    }

    public void setNickColor(UUID id, Integer rgb) {
        Entry e = data.computeIfAbsent(id, k -> new Entry());
        e.nickColor = rgb;
        cleanupAndSave(id);
    }

    public String getNickname(UUID id) {
        Entry e = data.get(id);
        return e == null ? null : e.nickname;
    }

    public Integer getLocatorSet(UUID id) {
        Entry e = data.get(id);
        return e == null ? null : e.locatorSet;
    }

    public void setLocatorSet(UUID id, Integer rgb) {
        Entry e = data.get(id);
        if (e == null && rgb == null) return;
        if (e != null && Objects.equals(e.locatorSet, rgb)) return;
        if (e == null) e = data.computeIfAbsent(id, k -> new Entry());
        e.locatorSet = rgb;
        cleanupAndSave(id);
    }

    public void setColor(UUID id, Integer rgb) {
        Entry e = data.computeIfAbsent(id, k -> new Entry());
        e.color = rgb;
        cleanupAndSave(id);
    }

    public void setNickname(UUID id, String nick) {
        Entry e = data.computeIfAbsent(id, k -> new Entry());
        e.nickname = nick;
        cleanupAndSave(id);
    }

    private void cleanupAndSave(UUID id) {
        Entry e = data.get(id);
        if (e != null && e.color == null && e.nickname == null && e.nickColor == null && e.locatorSet == null) data.remove(id);
        save();
    }

    private synchronized void save() {
        Map<String, Entry> out = new HashMap<>();
        data.forEach((k, v) -> out.put(k.toString(), v));
        try {
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(out));
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception atomicFail) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            AestheticChatUtility.LOGGER.error("Could not save player data", e);
        }
    }
}
