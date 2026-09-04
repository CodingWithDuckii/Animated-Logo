package dev.codeitsduckydev.animatedlogo;

import dev.codeitsduckydev.animatedlogo.gui.DonationNotificationWidget;
import dev.codeitsduckydev.animatedlogo.mixin.ScreenAccessor;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.sound.PositionedSoundInstance;
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
    public static final Identifier DONATION_TOAST_SOUND_ID = Identifier.of(MOD_ID, "toast");
    public static final SoundEvent DONATION_TOAST_SOUND_EVENT = SoundEvent.of(DONATION_TOAST_SOUND_ID);
    public static DonationNotificationWidget DONATION_WIDGET;

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing Startup Animation");
        Registry.register(Registries.SOUND_EVENT, STARTUP_SOUND_ID, STARTUP_SOUND_EVENT);
        Registry.register(Registries.SOUND_EVENT, DONATION_TOAST_SOUND_ID, DONATION_TOAST_SOUND_EVENT);

        // Remove any temp frames left behind by an interrupted recording.
        AnimatedLogoRecorder.cleanupTmpDir();

        // Show the donation notification on the title screen (i.e. after the
        // boot animation finishes), unless the player dismissed it.
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof TitleScreen titleScreen
                    && ModConfig.get().showDonationNotification()
                    && titleScreen.children().stream().noneMatch(child -> child instanceof DonationNotificationWidget)) {
                DonationNotificationWidget notification = new DonationNotificationWidget(
                        Math.max(8, scaledWidth - 238), 8);
                DONATION_WIDGET = notification;
                ((ScreenAccessor) (Object) titleScreen).animatedLogo$addDrawableChild(notification);
                client.getSoundManager().play(PositionedSoundInstance.master(DONATION_TOAST_SOUND_EVENT, 1.0F));
            }
        });
    }
}
