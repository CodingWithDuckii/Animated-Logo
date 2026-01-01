package com.duckii.animatedlogo;

import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.minecraft.util.Identifier;
import net.minecraft.sound.SoundEvent;
import net.minecraft.registry.Registry;
import net.minecraft.registry.Registries;

public class AnimatedLogo implements ModInitializer {
	public static final String MOD_ID = "animated-logo";
	public static final Identifier STARTUP_ID = Identifier.of(MOD_ID, "startup");
	public static final SoundEvent STARTUP_EVENT = SoundEvent.of(STARTUP_ID);
	public static volatile boolean SPLASH_DONE = false;
	public static volatile boolean SOUND_PLAYED = false;
	public static volatile long START_TS = -1L;
	public static volatile Long FADE_TS = null;

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		Registry.register(Registries.SOUND_EVENT, STARTUP_ID, STARTUP_EVENT);
	}
}
