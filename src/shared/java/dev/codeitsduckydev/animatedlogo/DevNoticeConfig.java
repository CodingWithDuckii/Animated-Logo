package dev.codeitsduckydev.animatedlogo;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Tracks whether the player has opted out of the developer notice screen.
 * The notice is shown on every launch by default until disabled.
 */
public final class DevNoticeConfig {
    private static final Path FILE = FabricLoader.getInstance()
            .getConfigDir().resolve("animated-logo-dev-notice.txt");

    private DevNoticeConfig() {
    }

    public static boolean isDisabled() {
        try {
            if (!Files.exists(FILE)) {
                return false;
            }
            return Files.readString(FILE).trim().equalsIgnoreCase("disabled");
        } catch (IOException e) {
            AnimatedLogo.LOGGER.warn("Failed to read developer notice config", e);
            return false;
        }
    }

    public static void setDisabled(boolean disabled) {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, disabled ? "disabled" : "enabled");
        } catch (IOException e) {
            AnimatedLogo.LOGGER.warn("Failed to write developer notice config", e);
        }
    }
}
