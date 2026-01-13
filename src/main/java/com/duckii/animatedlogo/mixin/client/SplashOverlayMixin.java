package com.duckii.animatedlogo.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.SplashOverlay;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
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
import java.util.HashMap;
import java.util.Map;

@Mixin(SplashOverlay.class)
public class SplashOverlayMixin {
    @Shadow private float progress;
    @Shadow private long reloadCompleteTime;
    @Unique private static final int animatedlogo$maxProbeFrames = 200;
    @Unique private static final int animatedlogo$frameMs = 34;
    @Unique private static final int animatedlogo$holdFrameIndex = 123;
    @Unique private static java.util.List<Identifier> animatedlogo$frames;
    @Unique private static Map<Identifier, Identifier> animatedlogo$cached;
    @Unique private static int animatedlogo$frameW = -1;
    @Unique private static int animatedlogo$frameH = -1;
    @Unique private static boolean animatedlogo$audioStarted = false;
    @Unique private static float animatedlogo$fadeInMs = 600f;
    @Unique private static float animatedlogo$fadeOutMs = 1000f;
    @Unique private static float animatedlogo$studiosDelayMs = 400f;
    @Unique private static float animatedlogo$studiosFadeMs = 600f;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void animatedlogo$render(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (AnimatedLogo.SPLASH_DONE) {
            ci.cancel();
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
                    break;
                }
            }
            animatedlogo$frames = list.isEmpty() ? java.util.List.of() : java.util.Collections.unmodifiableList(list);
            animatedlogo$cached = new HashMap<>();
            if (!animatedlogo$frames.isEmpty()) {
                try {
                    var res = client.getResourceManager().getResource(animatedlogo$frames.get(0));
                    if (res.isPresent()) {
                        try (InputStream is = new BufferedInputStream(res.get().getInputStream())) {
                            NativeImage ni = NativeImage.read(is);
                            animatedlogo$frameW = ni.getWidth();
                            animatedlogo$frameH = ni.getHeight();
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
        long elapsed = System.currentTimeMillis() - AnimatedLogo.START_TS;
        if (animatedlogo$frames == null || animatedlogo$frames.isEmpty()) {
            ci.cancel();
            return;
        }
        int rawIdx = (int)(elapsed / animatedlogo$frameMs);
        int idx = Math.min(rawIdx, animatedlogo$frames.size() - 1);
        
        boolean loadingComplete = progress >= 1.0f || reloadCompleteTime > 0L;
        
        if (idx >= animatedlogo$holdFrameIndex) {
            idx = animatedlogo$holdFrameIndex;
        }

        if (loadingComplete && AnimatedLogo.FADE_TS == null) {
            AnimatedLogo.FADE_TS = System.currentTimeMillis();
        }
        
        if (idx < 0 || idx >= animatedlogo$frames.size()) {
            ci.cancel();
            return;
        }
        Identifier tex = animatedlogo$frames.get(idx);
        
        int sw = client.getWindow().getScaledWidth();
        int sh = client.getWindow().getScaledHeight();
        
        if (!AnimatedLogo.SOUND_PLAYED && !animatedlogo$audioStarted) {
            if (client.getSoundManager() != null) {
                try {
                    client.getSoundManager().play(PositionedSoundInstance.master(AnimatedLogo.STARTUP_EVENT, 1.0F));
                    AnimatedLogo.SOUND_PLAYED = true;
                    animatedlogo$audioStarted = true;
                } catch (Exception ignored) {}
            }
        }
        float alpha = 1.0f;
        if (AnimatedLogo.FADE_TS != null) {
            long fadeElapsed = System.currentTimeMillis() - AnimatedLogo.FADE_TS;
            float t = Math.min(1f, fadeElapsed / animatedlogo$fadeOutMs);
            alpha = Math.max(0f, 1f - (t * t * (3f - 2f * t)));
            if (alpha <= 0f) {
                AnimatedLogo.SPLASH_DONE = true;
                if (loadingComplete) {
                    try {
                        client.setScreen(new net.minecraft.client.gui.screen.TitleScreen());
                    } catch (Exception ignored) {}
                }
                ci.cancel();
                return;
            }
        }
        Identifier drawId = tex;
        if (animatedlogo$cached != null) {
            Identifier cachedId = animatedlogo$cached.get(tex);
            if (cachedId == null) {
                try {
                    var res = client.getResourceManager().getResource(tex);
                    if (res.isPresent()) {
                        try (InputStream is = new BufferedInputStream(res.get().getInputStream())) {
                            NativeImage ni = NativeImage.read(is);
                            NativeImageBackedTexture t = new NativeImageBackedTexture(() -> "animated-logo-frame", ni);
                            Identifier dynId = Identifier.of("animated-logo", "dyn/" + tex.getPath().replace("textures/", ""));
                            client.getTextureManager().registerTexture(dynId, t);
                            animatedlogo$cached.put(tex, dynId);
                            drawId = dynId;
                            if (animatedlogo$frameW <= 0 || animatedlogo$frameH <= 0) {
                                animatedlogo$frameW = ni.getWidth();
                                animatedlogo$frameH = ni.getHeight();
                            }
                        }
                    }
                } catch (Exception ignored) {}
            } else {
                drawId = cachedId;
            }
        }
        int fw = animatedlogo$frameW > 0 ? animatedlogo$frameW : sw;
        int fh = animatedlogo$frameH > 0 ? animatedlogo$frameH : sh;
        double scale = Math.min((double)sw / (double)fw, (double)sh / (double)fh);
        int dw = Math.max(1, (int)Math.floor(fw * scale));
        int dh = Math.max(1, (int)Math.floor(fh * scale));
        int dx = (sw - dw) / 2;
        int dy = (sh - dh) / 2;

        long fadeInElapsed = elapsed;
        float fadeInAlpha = 1f;
        if (fadeInElapsed < animatedlogo$fadeInMs) {
            float ti = Math.min(1f, fadeInElapsed / animatedlogo$fadeInMs);
            fadeInAlpha = ti * ti * (3f - 2f * ti);
        }

        context.drawTexturedQuad(drawId, dx, dy, dw, dh, 0f, 1f, 0f, 1f);
        if (fadeInAlpha < 1f) {
            int overlayAlphaI = Math.max(0, Math.min(255, (int)((1f - fadeInAlpha) * 255f)));
            int color = (overlayAlphaI << 24);
            context.fill(0, 0, sw, sh, color);
        }

        float finalAlpha = alpha;
        if (alpha < 1f) {
            int overlayAlpha = Math.max(0, Math.min(255, (int)((1f - finalAlpha) * 255f)));
            int color = (overlayAlpha << 24);
            context.fill(0, 0, sw, sh, color);
        }
        long studiosElapsed = Math.max(0L, elapsed - (long)animatedlogo$studiosDelayMs);
        float studiosAlpha = 0f;
        if (studiosElapsed > 0L) {
            float ts = Math.min(1f, studiosElapsed / animatedlogo$studiosFadeMs);
            studiosAlpha = ts * ts * (3f - 2f * ts);
        }
        if (studiosAlpha > 0f) {
            String text = "Studios";
            int tw = client.textRenderer.getWidth(text);
            int tx = (sw - tw) / 2;
            int ty = dy + dh + Math.max(8, sh / 40);
            int a = Math.max(0, Math.min(255, (int)(studiosAlpha * 255f)));
            int color = (a << 24) | 0xFFFFFF;
            context.drawText(client.textRenderer, text, tx, ty, color, false);
        }
        ci.cancel();
    }
}
