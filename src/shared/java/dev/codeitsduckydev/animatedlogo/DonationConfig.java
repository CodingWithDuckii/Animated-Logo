package dev.codeitsduckydev.animatedlogo;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Stores whether the donation notification has been permanently dismissed. */
public final class DonationConfig {
    private static final Path FILE = FabricLoader.getInstance()
            .getConfigDir().resolve("animated-logo-donation-dismissed.txt");

    private DonationConfig() {
    }

    public static boolean isDismissed() {
        try {
            return Files.exists(FILE) && Files.readString(FILE).trim().equalsIgnoreCase("dismissed");
        } catch (IOException e) {
            AnimatedLogo.LOGGER.warn("Failed to read donation notification config", e);
            return false;
        }
    }

    public static void setDismissed() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, "dismissed");
        } catch (IOException e) {
            AnimatedLogo.LOGGER.warn("Failed to write donation notification config", e);
        }
    }
}
