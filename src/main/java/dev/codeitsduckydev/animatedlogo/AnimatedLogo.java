package dev.codeitsduckydev.animatedlogo;

import dev.codeitsduckydev.animatedlogo.gui.DevNoticeScreen;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class AnimatedLogo implements ModInitializer {
    public static final String MOD_ID = "animated-mojang-logo";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final Identifier STARTUP_SOUND_ID = Identifier.of("animated-mojang-logo", "startup");
    public static final SoundEvent STARTUP_SOUND_EVENT = SoundEvent.of(STARTUP_SOUND_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing Startup Animation");
        Registry.register(Registries.SOUND_EVENT, STARTUP_SOUND_ID, STARTUP_SOUND_EVENT);

        // Show the developer notice every launch, the first time the title
        // screen appears (i.e. after the boot animation finishes) -- unless
        // the player has opted out via the "Don't show this again" checkbox.
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof TitleScreen && !DevNoticeConfig.isDisabled()) {
                client.setScreen(new DevNoticeScreen(screen));
            }
        });
    }
}
