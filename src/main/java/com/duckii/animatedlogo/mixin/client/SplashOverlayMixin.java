package com.duckii.animatedlogo.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.SplashOverlay;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.registry.Registries;
import net.minecraft.resource.ResourceManager;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import com.duckii.animatedlogo.AnimatedLogo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import java.io.InputStream;
import java.io.BufferedInputStream;

@Mixin(SplashOverlay.class)
public class SplashOverlayMixin {
    @Shadow private float progress;
    @Shadow private long reloadCompleteTime;
    @Unique private static final int animatedlogo$maxProbeFrames = 200;
    @Unique private static final int animatedlogo$frameMs = 34;
    @Unique private static final int animatedlogo$holdFrameIndex = 123;
    @Unique private static java.util.List<Identifier> animatedlogo$frames;
    // timing and state are stored on the mod class to ensure single-run across overlays

    @Inject(method = "render", at = @At("TAIL"))
    private void animatedlogo$render(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (AnimatedLogo.SPLASH_DONE) {
            return;
        }
        if (AnimatedLogo.START_TS < 0L) {
            AnimatedLogo.START_TS = System.currentTimeMillis();
        }
        if (animatedlogo$frames == null || animatedlogo$frames.isEmpty()) {
            java.util.ArrayList<Identifier> list = new java.util.ArrayList<>();
            for (int i = 1; i <= animatedlogo$maxProbeFrames; i++) {
                Identifier id = Identifier.of("animated-logo", "textures/frames/frame_" + i + ".png");
                if (client.getResourceManager().getResource(id).isPresent()) {
                    list.add(id);
                } else {
                    // Optimization: stop probing once we hit a missing frame
                    // assuming frames are sequential (frame_1, frame_2, ... frame_N)
                    // This prevents unnecessary I/O checks for frames 125-200 if they don't exist.
                    break;
                }
            }
            animatedlogo$frames = list.isEmpty() ? java.util.List.of() : java.util.Collections.unmodifiableList(list);
        }
        long elapsed = System.currentTimeMillis() - AnimatedLogo.START_TS;
        if (animatedlogo$frames == null || animatedlogo$frames.isEmpty()) {
            return;
        }
        int rawIdx = (int)(elapsed / animatedlogo$frameMs);
        // Clamp to prevent looping and ensure we don't go out of bounds
        int idx = Math.min(rawIdx, animatedlogo$frames.size() - 1);
        
        boolean loadingComplete = progress >= 1.0f || reloadCompleteTime > 0L;
        
        // Hold logic: If we reached the hold frame, stay there regardless of loading state
        // (User requested to stop at 124 and then fade out)
        if (idx >= animatedlogo$holdFrameIndex) {
            idx = animatedlogo$holdFrameIndex;
        }

        if (loadingComplete && AnimatedLogo.FADE_TS == null) {
            AnimatedLogo.FADE_TS = System.currentTimeMillis();
        }
        
        // Safety check if frames are empty (though checked above)
        if (idx < 0 || idx >= animatedlogo$frames.size()) {
             return;
        }
        Identifier tex = animatedlogo$frames.get(idx);
        
        int sw = client.getWindow().getScaledWidth();
        int sh = client.getWindow().getScaledHeight();
        
        if (!AnimatedLogo.SOUND_PLAYED) {
            // Check if sound system is ready by checking if we can get the sound manager
            if (client.getSoundManager() != null) {
                // Play sound immediately if enough time passed to ensure audio engine init
                // Cheyao uses ~0.5s delay usually. We use 300ms.
                if (elapsed >= 300L) {
                    try {
                        // Attempt to play WAV file directly using Java Sound API
                        // This bypasses Minecraft's OGG-only SoundManager
                        Identifier soundId = Identifier.of("animated-logo", "sounds/logo.wav");
                        var resource = client.getResourceManager().getResource(soundId);
                        
                        if (resource.isPresent()) {
                            try (InputStream is = new BufferedInputStream(resource.get().getInputStream())) {
                                AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(is);
                                Clip clip = AudioSystem.getClip();
                                clip.open(audioInputStream);
                                clip.start();
                                AnimatedLogo.LOGGER.info("Startup sound (WAV) played directly via Java Sound API!");
                            }
                        } else {
                            // Fallback to standard sound event if WAV not found (legacy behavior)
                             client.getSoundManager().play(PositionedSoundInstance.master(AnimatedLogo.STARTUP_EVENT, 1.0F));
                             AnimatedLogo.LOGGER.info("Startup sound played via Minecraft SoundManager!");
                        }
                    } catch (Exception e) {
                        AnimatedLogo.LOGGER.error("Failed to play startup sound", e);
                        // Last ditch attempt with standard manager if Java Sound fails
                        try {
                             client.getSoundManager().play(PositionedSoundInstance.master(AnimatedLogo.STARTUP_EVENT, 1.0F));
                        } catch (Exception ignored) {}
                    }
                    AnimatedLogo.SOUND_PLAYED = true;
                }
            }
        }
        float alpha = 1.0f;
        if (AnimatedLogo.FADE_TS != null) {
            long fadeElapsed = System.currentTimeMillis() - AnimatedLogo.FADE_TS;
            float fadeDurationMs = 1000f;
            alpha = Math.max(0f, 1f - (fadeElapsed / fadeDurationMs));
            if (alpha <= 0f) {
                AnimatedLogo.SPLASH_DONE = true;
                return;
            }
        }
        context.drawTexturedQuad(tex, 0, 0, sw, sh, 0f, 1f, 0f, 1f);
        if (alpha < 1f) {
            int overlayAlpha = Math.max(0, Math.min(255, (int)((1f - alpha) * 255f)));
            int color = (overlayAlpha << 24);
            context.fill(0, 0, sw, sh, color);
        }
    }
}
