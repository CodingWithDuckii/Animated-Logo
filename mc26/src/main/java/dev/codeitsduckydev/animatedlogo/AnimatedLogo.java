package dev.codeitsduckydev.animatedlogo;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AnimatedLogo implements ModInitializer {
    public static final String MOD_ID = "animated-mojang-logo";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing Startup Animation");
    }
}

