package dev.codeitsduckydev.animatedlogo;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Persistent options for the mod, stored as a single JSON file in the
 * config directory ({@code config/animated-logo.json}). All settings can
 * be changed from the in-game options screen (reachable via Mod Menu).
 *
 * On first run, the value is seeded from the legacy plain-text config
 * (donation notification dismissal) so existing players keep their choice.
 */
public final class ModConfig {
    private static final String FILE_NAME = "animated-logo.json";
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    private static final Path LEGACY_DONATION = FabricLoader.getInstance().getConfigDir().resolve("animated-logo-donation-dismissed.txt");

    private static volatile ModConfig instance;

    private boolean animateOnStartup = true;
    private boolean animateOnResourceReload = true;
    private boolean playStartupSound = true;
    private boolean showDonationNotification = true;
    /** Path to the user's own ffmpeg install (exe, bin folder, or install root); empty = not set. */
    private String ffmpegPath = "";

    private ModConfig() {
    }

    public static ModConfig get() {
        ModConfig config = instance;
        if (config == null) {
            synchronized (ModConfig.class) {
                if (instance == null) {
                    instance = load();
                }
                config = instance;
            }
        }
        return config;
    }

    private static ModConfig load() {
        ModConfig config = new ModConfig();
        JsonObject json = readFile();
        if (json != null) {
            config.animateOnStartup = readBoolean(json, "animateOnStartup", true);
            config.animateOnResourceReload = readBoolean(json, "animateOnResourceReload", true);
            config.playStartupSound = readBoolean(json, "playStartupSound", true);
            config.showDonationNotification = readBoolean(json, "showDonationNotification", true);
            config.ffmpegPath = readString(json, "ffmpegPath", "");
            return config;
        }

        // No config file yet: migrate the legacy opt-out, then delete the old file.
        migrateLegacyFile(LEGACY_DONATION, "dismissed", value -> config.showDonationNotification = !value);
        deleteQuietly(LEGACY_DONATION);
        return config;
    }

    private static JsonObject readFile() {
        if (!Files.exists(FILE)) {
            return null;
        }
        try (Reader reader = Files.newBufferedReader(FILE)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            return json != null ? json : null;
        } catch (Exception e) {
            AnimatedLogo.LOGGER.warn("Failed to read animated logo config, using defaults", e);
            return null;
        }
    }

    private static boolean readBoolean(JsonObject json, String key, boolean fallback) {
        return json.has(key) ? json.get(key).getAsBoolean() : fallback;
    }

    private static String readString(JsonObject json, String key, String fallback) {
        return json.has(key) ? json.get(key).getAsString() : fallback;
    }

    private static void migrateLegacyFile(Path file, String marker, java.util.function.Consumer<Boolean> apply) {
        try {
            if (Files.exists(file) && Files.readString(file).trim().equalsIgnoreCase(marker)) {
                apply.accept(true);
            }
        } catch (IOException e) {
            AnimatedLogo.LOGGER.warn("Failed to migrate legacy config {}", file, e);
        }
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            AnimatedLogo.LOGGER.warn("Failed to remove legacy config {}", file, e);
        }
    }

    private synchronized void save() {
        try {
            Files.createDirectories(FILE.getParent());
            JsonObject json = new JsonObject();
            json.addProperty("animateOnStartup", animateOnStartup);
            json.addProperty("animateOnResourceReload", animateOnResourceReload);
            json.addProperty("playStartupSound", playStartupSound);
            json.addProperty("showDonationNotification", showDonationNotification);
            json.addProperty("ffmpegPath", ffmpegPath);
            Files.writeString(FILE, new GsonBuilder().setPrettyPrinting().create().toJson(json));
        } catch (IOException e) {
            AnimatedLogo.LOGGER.warn("Failed to write animated logo config", e);
        }
    }

    public boolean animateOnStartup() {
        return animateOnStartup;
    }

    public boolean animateOnResourceReload() {
        return animateOnResourceReload;
    }

    public boolean playStartupSound() {
        return playStartupSound;
    }

    public boolean showDonationNotification() {
        return showDonationNotification;
    }

    /** Path the user gave for their own ffmpeg install; empty when not set. */
    public String ffmpegPath() {
        return ffmpegPath;
    }

    public void setFfmpegPath(String path) {
        this.ffmpegPath = path == null ? "" : path.trim();
        save();
    }

    public void setAnimateOnStartup(boolean value) {
        this.animateOnStartup = value;
        save();
    }

    public void setAnimateOnResourceReload(boolean value) {
        this.animateOnResourceReload = value;
        save();
    }

    public void setPlayStartupSound(boolean value) {
        this.playStartupSound = value;
        save();
    }

    public void setShowDonationNotification(boolean value) {
        this.showDonationNotification = value;
        save();
    }
}
