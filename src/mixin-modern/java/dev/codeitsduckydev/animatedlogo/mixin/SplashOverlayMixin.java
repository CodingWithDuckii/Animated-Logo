package dev.codeitsduckydev.animatedlogo.mixin;

import dev.codeitsduckydev.animatedlogo.AnimatedLogo;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.SplashOverlay;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.sound.*;
import net.minecraft.resource.ResourceReload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;
import java.util.function.IntSupplier;

import static dev.codeitsduckydev.animatedlogo.AnimatedLogo.LOGGER;

@Mixin(SplashOverlay.class)
@SuppressWarnings({"unused", "FieldMayBeFinal"})
public class SplashOverlayMixin {
    @Mutable
    @Shadow @Final private ResourceReload reload;
    @Shadow private float progress;

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

    @Shadow
    @Final
    private static IntSupplier BRAND_ARGB; // Color of background
    @Unique
    private static int whiteARGB = ColorHelper.getArgb(255, 255, 255, 255);

    @Unique
    private static IntSupplier LOADING_FILL = () -> whiteARGB;
    @Unique
    private static IntSupplier LOADING_BORDER = () -> whiteARGB;

    @Unique
    private static IntSupplier TEXT_COLOR = () -> applyAlphaToColor(whiteARGB, 1.0f);


    @Unique private boolean soundPlayed = false;
    @Unique private boolean animationReady = false;
    @Unique private boolean isFadingOut = false;
    @Unique private boolean isFadingFinished = false;
    @Unique private boolean isFadingIn = false;

    @Unique private long animationStartTime = -1;
    @Unique private static final float TOTAL_ANIMATION_DURATION = 3.0f; // in seconds
    @Unique private long animationDelayStartTime = -1;
    @Unique private static final long ANIMATION_DELAY_MS = 1;
    @Unique private long fadeOutStartTime = -1;
    @Unique private static final long FADE_OUT_DURATION_MS = 1000; // in milliseconds
    @Unique private long fadeInStartTime = -1;
    @Unique private static final long FADE_IN_DURATION_MS = 700;
    @Unique private static float loadingBarProgress = 0.0f; // in seconds

    @Unique private static boolean HAS_LOADED_ONCE = false;
    @Unique private static boolean RELOAD_IN_PROGRESS = false;

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
        int colorFilled = applyAlphaToColor(LOADING_FILL.getAsInt(), opacity);
        int colorOutline = applyAlphaToColor(LOADING_BORDER.getAsInt(), opacity);

        context.fill(minX + 2, minY + 2, minX + filled, maxY - 2, colorFilled);
        context.fill(minX + 1, minY, maxX - 1, minY + 1, colorOutline);
        context.fill(minX + 1, maxY, maxX - 1, maxY - 1, colorOutline);
        context.fill(minX, minY, minX + 1, maxY, colorOutline);
        context.fill(maxX, minY, maxX - 1, maxY, colorOutline);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void init(MinecraftClient client, ResourceReload monitor, Consumer<Throwable> exceptionHandler, boolean reloading, CallbackInfo ci) {
        boolean isInitialLoad = !HAS_LOADED_ONCE;
        boolean isReload = reloading;

        if (!isInitialLoad && !isReload) {
            return;
        }

        if (isReload) {
            LOGGER.info("Resource pack reload detected, playing Animated Mojang Logo.");
            RELOAD_IN_PROGRESS = true;
            resetAnimationState();
        } else {
            LOGGER.info("Initial load, starting Animated Mojang Logo.");
        }

        animationDelayStartTime = System.currentTimeMillis();
    }

    @Unique
    private void resetAnimationState() {
        soundPlayed = false;
        animationReady = false;
        animationDone = false;
        isFadingOut = false;
        isFadingFinished = false;
        isFadingIn = false;
        animationStartTime = -1;
        postAnimationFadeStartTime = -1;
        postAnimationFadeDone = false;
        fadeOutStartTime = -1;
        fadeInStartTime = -1;
        loadingBarProgress = 0.0f;
        count = 0;
        f = 0;
    }

    @Unique
    private static boolean shouldOverrideVanilla() {
        return !HAS_LOADED_ONCE || RELOAD_IN_PROGRESS;
    }

    // Stop rendering of title
    @ModifyArg(method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIFFIIIIIII)V", ordinal = 0),
            index = 7)
    private int removeText1(int i) {
        return shouldOverrideVanilla() ? 0 : i;
    }
    @ModifyArg(method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIFFIIIIIII)V", ordinal = 1),
            index = 7)
    private int removeText2(int u) {
        return shouldOverrideVanilla() ? 0 : u;
    }

    // Stop rendering of loading bar
    @ModifyArg(method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/SplashOverlay;renderProgressBar(Lnet/minecraft/client/gui/DrawContext;IIIIF)V", ordinal = 0),
            index = 5)
    private float removeBar(float opacity) {
        return shouldOverrideVanilla() ? 0 : opacity;
    }


    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void preRender(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (HAS_LOADED_ONCE && !RELOAD_IN_PROGRESS) {
            return;
        }

        long elapsed = System.currentTimeMillis() - animationDelayStartTime;

        if (elapsed < ANIMATION_DELAY_MS) {
            context.fill(RenderPipelines.GUI, 0, 0,
                    context.getScaledWindowWidth(), context.getScaledWindowHeight(),
                    ColorHelper.withAlpha((int)((elapsed * 255) / ANIMATION_DELAY_MS / 10),
                            applyAlphaToColor(BRAND_ARGB.getAsInt(), 1.0f)));
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
    private void drawLoopingFrame(DrawContext context) {
        if (!inited || frames == null) {
            return;
        }

        int screenWidth = context.getScaledWindowWidth();
        int screenHeight = context.getScaledWindowHeight();
        int width = screenWidth / 2;
        int height = width * 256 / 1024;
        int x = (screenWidth - width) / 2;
        int y = (screenHeight - height) / 2;

        long loopTicks = System.currentTimeMillis() / 100L;
        int totalFrameCount = FRAMES * IMAGE_PER_FRAME * FRAMES_PER_FRAME;
        int loopingCount = (int)(loopTicks % totalFrameCount);

        int frameIndex = loopingCount / IMAGE_PER_FRAME / FRAMES_PER_FRAME;
        int subFrameY = 256 * ((loopingCount % (IMAGE_PER_FRAME * FRAMES_PER_FRAME)) / FRAMES_PER_FRAME);

        context.drawTexture(RenderPipelines.GUI_TEXTURED, frames[frameIndex], x, y,
                0, subFrameY, width, height,
                1024, 256, 1024, 1024, applyAlphaToColor(TEXT_COLOR.getAsInt(), 1.0f));
    }

    @Unique
    private void drawAnimatedIntro(DrawContext context) {
        if (!inited) {
            this.frames = new Identifier[FRAMES];
            for (int i = 0; i < FRAMES; i++) {
                this.frames[i] = Identifier.of("animated-mojang-logo", "textures/gui/frame_" + i + ".png");
            }
            inited = true;
        }

        // Keep the splash deliberately blank while the resource pack is loading.
        // Rendering the logo here makes the client look frozen because the game
        // thread can be busy preparing the new resources.
        if (!reload.isComplete()) {

            context.fill(RenderPipelines.GUI, 0, 0,
                    context.getScaledWindowWidth(), context.getScaledWindowHeight(),
                    applyAlphaToColor(BRAND_ARGB.getAsInt(), 1.0f));

            drawLoadingBar(context, 1.0f, Math.max(loadingBarProgress, reload.getProgress()));
            loadingBarProgress = reload.getProgress();
            return;
        }

        // Once loading is complete, reveal the first logo frame before starting
        // the animation. This makes the transition feel intentional and avoids
        // showing any logo pixels while resources are still being prepared.
        if (!animationReady) {
            if (!isFadingIn) {
                isFadingIn = true;
                fadeInStartTime = System.currentTimeMillis();
            }

            float fadeFactor = MathHelper.clamp(
                    (float) (System.currentTimeMillis() - fadeInStartTime) / FADE_IN_DURATION_MS,
                    0.0f, 1.0f);
            context.fill(RenderPipelines.GUI, 0, 0,
                    context.getScaledWindowWidth(), context.getScaledWindowHeight(),
                    applyAlphaToColor(BRAND_ARGB.getAsInt(), 1.0f));
            drawFrame(context, 0, fadeFactor);

            if (fadeFactor >= 1.0f) {
                animationReady = true;
                animationStartTime = System.nanoTime();

                if (!soundPlayed) {
                    MinecraftClient.getInstance().getSoundManager().play(
                            PositionedSoundInstance.master(AnimatedLogo.STARTUP_SOUND_EVENT, 1.0F)
                    );
                    LOGGER.info("Playing startup sound");
                    soundPlayed = true;
                }
            }
            return;
        }

        if (animationReady) {
            double elapsedSeconds = (System.nanoTime() - animationStartTime) / 1_000_000_000.0;
            double animationProgress = Math.min(elapsedSeconds / TOTAL_ANIMATION_DURATION, 1.0);

            int totalFrameCount = FRAMES * IMAGE_PER_FRAME * FRAMES_PER_FRAME;
            count = (int)(animationProgress * totalFrameCount);

            if (animationProgress >= 1.0) {
                animationDone = true;
                count = totalFrameCount - 1;
                if (postAnimationFadeStartTime == -1) {
                    postAnimationFadeStartTime = System.currentTimeMillis();
                    postAnimationFadeDone = false;
                }
            }

            context.fill(RenderPipelines.GUI, 0, 0,
                    context.getScaledWindowWidth(), context.getScaledWindowHeight(),
                    applyAlphaToColor(BRAND_ARGB.getAsInt(), 1.0f));
            drawFrame(context, count, 1.0f);
        }
    }

    @Unique
    private void drawFrame(DrawContext context, int frameCount, float opacity) {
        int screenWidth = context.getScaledWindowWidth();
        int screenHeight = context.getScaledWindowHeight();
        int width = screenWidth / 2;
        int height = width * 256 / 1024;
        int x = (screenWidth - width) / 2;
        int y = (screenHeight - height) / 2;
        int frameIndex = MathHelper.clamp(frameCount / IMAGE_PER_FRAME / FRAMES_PER_FRAME, 0, FRAMES - 1);
        int subFrameY = 256 * ((frameCount % (IMAGE_PER_FRAME * FRAMES_PER_FRAME)) / FRAMES_PER_FRAME);

        context.drawTexture(RenderPipelines.GUI_TEXTURED, frames[frameIndex], x, y,
                0, subFrameY, width, height,
                1024, 256, 1024, 1024, applyAlphaToColor(TEXT_COLOR.getAsInt(), opacity));
    }

    @Unique
    private void drawPostAnimationFade(DrawContext context, int mouseX, int mouseY, float delta) {
        if (postAnimationFadeStartTime == -1) {
            postAnimationFadeStartTime = System.currentTimeMillis();
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen != null) {
            client.currentScreen.render(context, mouseX, mouseY, delta);
        }

        long elapsed = System.currentTimeMillis() - postAnimationFadeStartTime;
        float fade = 1.0f - MathHelper.clamp((float) elapsed / POST_ANIMATION_FADE_DURATION_MS, 0.0f, 1.0f);

        context.fill(RenderPipelines.GUI, 0, 0,
                context.getScaledWindowWidth(), context.getScaledWindowHeight(),
                applyAlphaToColor(BRAND_ARGB.getAsInt(), fade));

        int screenWidth = context.getScaledWindowWidth();
        int screenHeight = context.getScaledWindowHeight();
        int width = screenWidth / 2;
        int height = width * 256 / 1024;
        int x = (screenWidth - width) / 2;
        int y = (screenHeight - height) / 2;
        int finalSubFrameY = 256 * ((count % (IMAGE_PER_FRAME * FRAMES_PER_FRAME)) / FRAMES_PER_FRAME);

        Identifier finalFrame = frames[FRAMES - 1];
        context.drawTexture(RenderPipelines.GUI_TEXTURED, finalFrame, x, y,
                0, finalSubFrameY, width, height,
                1024, 256, 1024, 1024, applyAlphaToColor(TEXT_COLOR.getAsInt(), fade));

        if (fade <= 0.0f) {
            postAnimationFadeDone = true;
            if (!HAS_LOADED_ONCE) {
                HAS_LOADED_ONCE = true;
            }
            RELOAD_IN_PROGRESS = false;
            MinecraftClient.getInstance().setOverlay(null);
        }
    }

    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/DrawContext;drawTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIFFIIIIIII)V",
            ordinal = 1, shift = At.Shift.AFTER))
    private void onAfterRenderLogo(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci,
                                   @Local(ordinal = 2) int scaledWidth, @Local(ordinal = 3) int scaledHeight,
                                   @Local(ordinal = 3) float alpha, @Local(ordinal = 4) int x, @Local(ordinal = 5) int y,
                                   @Local(ordinal = 0) double height, @Local(ordinal = 6) int halfHeight,
                                   @Local(ordinal = 1) double width, @Local(ordinal = 7) int halfWidth) {
        if (!animationDone) return;
        if (HAS_LOADED_ONCE && !RELOAD_IN_PROGRESS) return;

        // Title (last frame)
        int finalFrameScreenWidth = context.getScaledWindowWidth();
        int finalFrameScreenHeight = context.getScaledWindowHeight();
        int finalFrameWidth = finalFrameScreenWidth / 2;
        int finalFrameHeight = finalFrameWidth * 256 / 1024;
        int finalFrameX = (finalFrameScreenWidth - finalFrameWidth) / 2;
        int finalFrameY = (finalFrameScreenHeight - finalFrameHeight) / 2;
        int finalSubFrameY = 256 * ((count % (IMAGE_PER_FRAME * FRAMES_PER_FRAME)) / FRAMES_PER_FRAME);

        Identifier finalFrame = frames[FRAMES - 1];
        context.drawTexture(RenderPipelines.GUI_TEXTURED, finalFrame, finalFrameX, finalFrameY,
                0, finalSubFrameY, finalFrameWidth, finalFrameHeight,
                1024, 256, 1024, 1024, applyAlphaToColor(TEXT_COLOR.getAsInt(), alpha));

        if (alpha <= 0.0f) {
            if (!HAS_LOADED_ONCE) {
                HAS_LOADED_ONCE = true;
            }
            RELOAD_IN_PROGRESS = false;
        }
    }

    @Unique
    private static int applyAlphaToColor(int color, float alpha) {
        int rgb = color & 0x00FFFFFF;
        int a = MathHelper.clamp((int)(alpha * 255), 0, 255);
        return (a << 24) | rgb;
    }
}
