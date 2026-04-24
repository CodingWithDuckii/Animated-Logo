package dev.codeitsduckydev.animatedlogo;

import dev.codeitsduckydev.animatedlogo.util.VersionedRenderer;
import net.fabricmc.api.ModInitializer;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;


public class AnimatedLogo implements ModInitializer {
    public static final String MOD_ID = "animated-mojang-logo";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Identifier STARTUP_SOUND_ID;
    public static SoundEvent STARTUP_SOUND_EVENT;

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing Animated Logo v1.5...");
        try {
            STARTUP_SOUND_ID = VersionedRenderer.createIdentifier(MOD_ID, "startup");
            // SoundEvent.of(Identifier)
            Method soundEventOf = SoundEvent.class.getMethod("of", Identifier.class);
            STARTUP_SOUND_EVENT = (SoundEvent) soundEventOf.invoke(null, STARTUP_SOUND_ID);
            
            // Registry.register(Registry, Identifier, Object)
            Method registryRegister = Registry.class.getMethod("register", Registry.class, Identifier.class, Object.class);
            registryRegister.invoke(null, Registries.SOUND_EVENT, STARTUP_SOUND_ID, STARTUP_SOUND_EVENT);
        } catch (Throwable t) {
            LOGGER.error("Failed to initialize startup sound using reflection", t);
            // Fallback to direct call if reflection fails (might happen if remapped correctly)
            try {
                STARTUP_SOUND_ID = Identifier.of(MOD_ID, "startup");
                STARTUP_SOUND_EVENT = SoundEvent.of(STARTUP_SOUND_ID);
                Registry.register(Registries.SOUND_EVENT, STARTUP_SOUND_ID, STARTUP_SOUND_EVENT);
            } catch (Throwable t2) {
                LOGGER.error("Final fallback for startup sound failed", t2);
            }
        }
    }
}
