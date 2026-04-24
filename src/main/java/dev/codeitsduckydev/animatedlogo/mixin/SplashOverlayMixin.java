package dev.codeitsduckydev.animatedlogo.mixin;

import dev.codeitsduckydev.animatedlogo.AnimatedLogo;
import dev.codeitsduckydev.animatedlogo.util.ColorUtils;
import dev.codeitsduckydev.animatedlogo.util.VersionedRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.*;
import net.minecraft.resource.ResourceReload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.IntSupplier;

import static dev.codeitsduckydev.animatedlogo.AnimatedLogo.LOGGER;

@Mixin(targets = {"net.minecraft.client.gui.screen.SplashOverlay", "net.minecraft.class_425"})
@SuppressWarnings({"unused", "FieldMayBeFinal"})
public class SplashOverlayMixin {
    @Unique private ResourceReload reloadField;
    @Unique private boolean fieldsInited = false;

    @Unique private int count = 0;
    @Unique private Identifier[] frames;
    @Unique private boolean inited = false;
    @Unique private static final int FRAMES = 12;
    @Unique private static final int IMAGE_PER_FRAME = 4;
    @Unique private static final int FRAMES_PER_FRAME = 2;
    @Unique private float f = 0;
    @Unique private boolean animationDone = false;
    @Unique private long postAnimationFadeStartTime = -1;
    @Unique private static final long POST_ANIMATION_FADE_DURATION_MS = 1000;
    @Unique private boolean postAnimationFadeDone = false;

    @Unique
    private static final int MOJANG_RED = 0xFFEF323D; // Standard Mojang background color

    @Unique
    private static int whiteARGB = ColorHelper.getArgb(255, 255, 255, 255);

    @Unique
    private static IntSupplier LOADING_FILL = () -> whiteARGB;
    @Unique
    private static IntSupplier LOADING_BORDER = () -> whiteARGB;

    @Unique private boolean soundPlayed = false;
    @Unique private boolean animationReady = false;
    @Unique private boolean isFadingOut = false;
    @Unique private boolean isFadingFinished = false;

    @Unique private long animationStartTime = -1;
    @Unique private static final float TOTAL_ANIMATION_DURATION = 3.0f; // in seconds
    @Unique private long animationDelayStartTime = -1;
    @Unique private static final long ANIMATION_DELAY_MS = 1;
    @Unique private long fadeOutStartTime = -1;
    @Unique private static final long FADE_OUT_DURATION_MS = 1000; // in milliseconds
    @Unique private static float loadingBarProgress = 0.0f; // in seconds

    @Unique private static boolean HAS_LOADED_ONCE = false;

    @Unique
    private void initFields(Object instance) {
        if (fieldsInited) return;
        LOGGER.info("Initializing SplashOverlay fields for: " + instance.getClass().getName());
        try {
            // Try to find 'reload' field (ResourceReload)
            for (java.lang.reflect.Field field : instance.getClass().getDeclaredFields()) {
                if (ResourceReload.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    this.reloadField = (ResourceReload) field.get(instance);
                    LOGGER.info("Found reload field: " + field.getName());
                    break;
                }
            }
        } catch (Throwable t) {
            LOGGER.error("Failed to initialize SplashOverlay fields via reflection", t);
        }
        fieldsInited = true;
    }

    // Draw vanilla loading bar
    // Copied from: net.minecraft.client.gui.screen.SplashOverlay.renderProgressBar
    @Unique
    private void drawLoadingBar(DrawContext context, float opacity, float progress) {
        int screenWidth = context.getScaledWindowWidth();
        int screenHeight = context.getScaledWindowHeight();

        int centerX = screenWidth / 2;
        int progressBarY = (int)(screenHeight * 0.8325);

        double logoHeight = Math.min(screenWidth * 0.75, screenHeight) * 0.25;
        double logoWidth = logoHeight * 4.0;
        int halfLogoWidth = (int)(logoWidth * 0.5);

        int minX = centerX - halfLogoWidth;
        int maxX = centerX + halfLogoWidth;
        int minY = progressBarY - 5;
        int maxY = progressBarY + 5;

        int filled = MathHelper.ceil((float)(maxX - minX - 2) * progress);
        int colorFilled = ColorUtils.applyAlphaToColor(LOADING_FILL.getAsInt(), opacity);
        int colorOutline = ColorUtils.applyAlphaToColor(LOADING_BORDER.getAsInt(), opacity);

        context.fill(minX + 2, minY + 2, minX + filled, maxY - 2, colorFilled);
        context.fill(minX + 1, minY, maxX - 1, minY + 1, colorOutline);
        context.fill(minX + 1, maxY, maxX - 1, maxY - 1, colorOutline);
        context.fill(minX, minY, minX + 1, maxY, colorOutline);
        context.fill(maxX, minY, maxX - 1, maxY, colorOutline);
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void preRender(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (HAS_LOADED_ONCE) {
            return;
        }

        if (animationDelayStartTime == -1) {
            LOGGER.info("Starting animation sequence");
            animationDelayStartTime = System.currentTimeMillis();
        }

        initFields(this);

        long elapsed = System.currentTimeMillis() - animationDelayStartTime;

        if (elapsed < ANIMATION_DELAY_MS) {
            VersionedRenderer.fill(context, 0, 0,
                    context.getScaledWindowWidth(), context.getScaledWindowHeight(),
                    ColorHelper.withAlpha((int)((elapsed * 255) / ANIMATION_DELAY_MS / 10),
                            ColorUtils.applyAlphaToColor(MOJANG_RED, 1.0f)));
            ci.cancel();
            return;
        }

        if (!animationDone) {
            drawAnimatedIntro(context);
            ci.cancel();
            return;
        }

        if (!postAnimationFadeDone) {
            drawPostAnimationFade(context, mouseX, mouseY, delta);
            ci.cancel();
        }
    }

    @Unique
    private void drawAnimatedIntro(DrawContext context) {
        float reloadProgress = reloadField != null ? reloadField.getProgress() : 1.0f;
        boolean reloadComplete = reloadField != null ? reloadField.isComplete() : true;

        if (!reloadComplete && !isFadingOut && !isFadingFinished) {

            VersionedRenderer.fill(context, 0, 0,
                    context.getScaledWindowWidth(), context.getScaledWindowHeight(),
                    ColorUtils.applyAlphaToColor(MOJANG_RED, 1.0f));

            drawLoadingBar(context, 1.0f, Math.max(loadingBarProgress, reloadProgress));
            loadingBarProgress = reloadProgress;

            return;
        }

        if (reloadComplete && !isFadingOut && !isFadingFinished) {
            LOGGER.info("Reload complete, starting fade out");
            isFadingOut = true;
            fadeOutStartTime = System.currentTimeMillis();
        }

        if (isFadingOut && !isFadingFinished) {
            long elapsedFade = System.currentTimeMillis() - fadeOutStartTime;
            float fadeFactor = 1.0f - MathHelper.clamp((float)elapsedFade / FADE_OUT_DURATION_MS, 0.0f, 1.0f);

            VersionedRenderer.fill(context, 0, 0,
                    context.getScaledWindowWidth(), context.getScaledWindowHeight(),
                    ColorUtils.applyAlphaToColor(MOJANG_RED, 1.0f));

            drawLoadingBar(context, fadeFactor, 1.0f);
            loadingBarProgress = reloadProgress;

            if (fadeFactor <= 0.0) {
                LOGGER.info("Fade out finished, preparing animation");
                isFadingFinished = true;
            }

            return;
        }

        if (isFadingFinished && !animationReady) {
            animationReady = true;
            animationStartTime = System.nanoTime();

            if (!soundPlayed) {
                try {
                    MinecraftClient.getInstance().getSoundManager().play(
                            PositionedSoundInstance.master(AnimatedLogo.STARTUP_SOUND_EVENT, 1.0F)
                    );
                    LOGGER.info("Playing startup sound");
                } catch (Throwable t) {
                    LOGGER.error("Failed to play startup sound", t);
                }
                soundPlayed = true;
            }

            if (!inited) {
                LOGGER.info("Loading animation frames");
                this.frames = new Identifier[FRAMES];
                for (int i = 0; i < FRAMES; i++) {
                    this.frames[i] = VersionedRenderer.createIdentifier("animated-mojang-logo", "textures/gui/frame_" + i + ".png");
                }
                inited = true;
            }
        }

        if (animationReady) {
            double elapsedSeconds = (System.nanoTime() - animationStartTime) / 1_000_000_000.0;
            double animationProgress = Math.min(elapsedSeconds / TOTAL_ANIMATION_DURATION, 1.0);

            int totalFrameCount = FRAMES * IMAGE_PER_FRAME * FRAMES_PER_FRAME;
            count = (int)(animationProgress * totalFrameCount);

            if (animationProgress >= 1.0) {
                LOGGER.info("Animation finished, starting post-animation fade");
                animationDone = true;
                count = totalFrameCount - 1;
                if (postAnimationFadeStartTime == -1) {
                    postAnimationFadeStartTime = System.currentTimeMillis();
                    postAnimationFadeDone = false;
                }
            }

            int screenWidth = context.getScaledWindowWidth();
            int screenHeight = context.getScaledWindowHeight();
            int width = screenWidth / 2;
            int height = width * 256 / 1024;
            int x = (screenWidth - width) / 2;
            int y = (screenHeight - height) / 2;

            int frameIndex = count / IMAGE_PER_FRAME / FRAMES_PER_FRAME;
            int subFrameY = 256 * ((count % (IMAGE_PER_FRAME * FRAMES_PER_FRAME)) / FRAMES_PER_FRAME);

            VersionedRenderer.fill(context, 0, 0,
                    context.getScaledWindowWidth(), context.getScaledWindowHeight(),
                    ColorUtils.applyAlphaToColor(MOJANG_RED, 1.0f));

            VersionedRenderer.setShaderColor(context, 1.0f, 1.0f, 1.0f, 1.0f);
            VersionedRenderer.drawTexture(context, frames[frameIndex], x, y,
                    0.0f, (float)subFrameY, width, height,
                    1024, 1024);

        }
    }

    @Unique
    private void drawPostAnimationFade(DrawContext context, int mouseX, int mouseY, float delta) {
        if (postAnimationFadeStartTime == -1) {
            postAnimationFadeStartTime = System.currentTimeMillis();
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen != null) {
            try {
                client.currentScreen.render(context, mouseX, mouseY, delta);
            } catch (Throwable t) {
                // Ignore if screen rendering fails during fade
            }
        }

        long elapsed = System.currentTimeMillis() - postAnimationFadeStartTime;
        float fade = 1.0f - MathHelper.clamp((float) elapsed / POST_ANIMATION_FADE_DURATION_MS, 0.0f, 1.0f);

        VersionedRenderer.fill(context, 0, 0,
                context.getScaledWindowWidth(), context.getScaledWindowHeight(),
                ColorUtils.applyAlphaToColor(MOJANG_RED, fade));

        int screenWidth = context.getScaledWindowWidth();
        int screenHeight = context.getScaledWindowHeight();
        int width = screenWidth / 2;
        int height = width * 256 / 1024;
        int x = (screenWidth - width) / 2;
        int y = (screenHeight - height) / 2;
        int finalSubFrameY = 256 * ((count % (IMAGE_PER_FRAME * FRAMES_PER_FRAME)) / FRAMES_PER_FRAME);

        Identifier finalFrame = frames[FRAMES - 1];
        VersionedRenderer.setShaderColor(context, 1.0f, 1.0f, 1.0f, fade);
        VersionedRenderer.drawTexture(context, finalFrame, x, y,
                0.0f, (float)finalSubFrameY, width, height,
                1024, 1024);
        VersionedRenderer.setShaderColor(context, 1.0f, 1.0f, 1.0f, 1.0f);

        if (fade <= 0.0f) {
            LOGGER.info("Post-animation fade finished");
            postAnimationFadeDone = true;
            HAS_LOADED_ONCE = true;
            MinecraftClient.getInstance().setOverlay(null);
        }
    }
}
