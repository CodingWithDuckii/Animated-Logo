package dev.codeitsduckydev.animatedlogo.gui;

import dev.codeitsduckydev.animatedlogo.AnimatedLogoRecorder;
import dev.codeitsduckydev.animatedlogo.AnimatedLogoRecorder.Phase;
import dev.codeitsduckydev.animatedlogo.AnimatedLogoRecorder.RecorderSession;
import dev.codeitsduckydev.animatedlogo.ModConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.nio.file.Path;

/**
 * Shows what the recording is doing while the intro video is being rendered
 * and reports the result afterwards. The buttons reflect the current phase:
 * "Cancel" while rendering, "Open Folder" + "OK" once saved. The phase can
 * change at any time from the render thread, so the buttons are rebuilt
 * whenever it does (tick()), otherwise a stale "Cancel" would linger after
 * the video finished.
 */
public class RecordingStatusScreen extends Screen {
    private static final int MAX_BAR_WIDTH = 260;

    private final RecorderSession session;
    private Phase shownPhase;
    /** Lets the user paste the path to their own ffmpeg install. */
    private TextFieldWidget ffmpegField;
    /** Shown in red under the messages when the typed path is not usable. */
    private String ffmpegPathError;

    public RecordingStatusScreen(RecorderSession session) {
        super(Text.literal("Animated Logo - Recording"));
        this.session = session;
        this.shownPhase = session.phase;
    }

    @Override
    protected void init() {
        super.init();
        this.clearChildren();
        int centerX = this.width / 2;
        int bottomY = this.height - 32;

        switch (this.session.phase) {
            case CONFIRM_START -> {
                this.addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), btn -> this.session.cancelRequested = true)
                        .dimensions(centerX - 115, bottomY, 110, 20).build());
                this.addDrawableChild(ButtonWidget.builder(Text.literal("OK"), btn -> this.session.startConfirmed = true)
                        .dimensions(centerX + 5, bottomY, 110, 20).build());
            }
            case NEEDS_FFMPEG -> {
                int fieldY = this.height - 76;
                this.ffmpegField = new TextFieldWidget(this.textRenderer, centerX - 150, fieldY, 300, 20,
                        Text.literal("ffmpeg path"));
                this.ffmpegField.setPlaceholder(Text.literal("Path to your ffmpeg.exe or its bin folder"));
                String configuredPath = ModConfig.get().ffmpegPath();
                if (!configuredPath.isBlank()) {
                    this.ffmpegField.setText(configuredPath);
                }
                this.ffmpegField.setChangedListener(text -> this.ffmpegPathError = null);
                this.addDrawableChild(this.ffmpegField);
                this.setInitialFocus(this.ffmpegField);
                this.addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), btn -> this.session.cancelRequested = true)
                        .dimensions(centerX - 209, bottomY, 70, 20).build());
                this.addDrawableChild(ButtonWidget.builder(Text.literal("Use path"), btn -> useFfmpegPath())
                        .dimensions(centerX - 133, bottomY, 110, 20).build());
                this.addDrawableChild(ButtonWidget.builder(Text.literal("Download ffmpeg"), btn -> this.session.installRequested = true)
                        .dimensions(centerX - 17, bottomY, 115, 20).build());
                this.addDrawableChild(ButtonWidget.builder(Text.literal("Without sound"), btn -> this.session.recordWithoutSound = true)
                        .dimensions(centerX + 104, bottomY, 105, 20).build());
            }
            case INSTALLING, ENCODING -> this.addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), btn -> this.session.cancelRequested = true)
                    .dimensions(centerX - 60, bottomY, 120, 20).build());
            case DONE -> {
                this.addDrawableChild(ButtonWidget.builder(Text.literal("Open Folder"), btn -> openFolder())
                        .dimensions(centerX - 115, bottomY, 110, 20).build());
                this.addDrawableChild(ButtonWidget.builder(Text.literal("OK"), btn -> this.close())
                        .dimensions(centerX + 5, bottomY, 110, 20).build());
            }
            case CANCELLED, FAILED -> this.addDrawableChild(ButtonWidget.builder(Text.literal("OK"), btn -> this.close())
                    .dimensions(centerX - 60, bottomY, 120, 20).build());
        }
    }

    /** Rebuilds the buttons when the render thread moves the session on. */
    @Override
    public void tick() {
        Phase phase = this.session.phase;
        if (phase != this.shownPhase) {
            this.shownPhase = phase;
            this.init();
        }
    }

    /**
     * Validates the typed path (ffmpeg.exe, its bin folder, or the install
     * root) and, when good, saves it in the config and lets recording go on.
     */
    private void useFfmpegPath() {
        String raw = this.ffmpegField != null ? this.ffmpegField.getText() : "";
        while (raw.startsWith("\"") && raw.endsWith("\"") && raw.length() > 1) {
            raw = raw.substring(1, raw.length() - 1).trim();
        }
        if (AnimatedLogoRecorder.isValidFfmpegPath(raw)) {
            ModConfig.get().setFfmpegPath(raw);
            this.session.pathProvided = true;
            this.ffmpegPathError = null;
        } else {
            this.ffmpegPathError = raw.isBlank()
                    ? "Enter the path to your own ffmpeg.exe or its bin folder first."
                    : "No ffmpeg there - the path must point at ffmpeg.exe, its bin folder, or the ffmpeg folder.";
        }
    }

    private void openFolder() {
        Path file = this.session.outputFile;
        if (file != null && file.getParent() != null) {
            Util.getOperatingSystem().open(file.getParent().toFile());
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return this.session.phase.isTerminal();
    }

    @Override
    public void close() {
        if (this.client != null && this.session.phase.isTerminal()) {
            this.client.setScreen(this.session.returnScreen);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xC0101010);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 0xFFFFFFFF);

        Phase phase = this.session.phase;
        switch (phase) {
            case CONFIRM_START -> {
                context.drawCenteredTextWithShadow(this.textRenderer,
                        Text.literal("Ready to record the intro animation."),
                        this.width / 2, 55, 0xFFFFFFFF);
                context.drawCenteredTextWithShadow(this.textRenderer,
                        Text.literal("The mod will look for ffmpeg on your system - it adds the startup sound - and run it in the background when saving."),
                        this.width / 2, 70, 0xFFAAAAAA);
            }
            case NEEDS_FFMPEG -> {
                context.drawCenteredTextWithShadow(this.textRenderer,
                        Text.literal("ffmpeg was not found - it adds the startup sound to the video."),
                        this.width / 2, 55, 0xFFFFFFFF);
                context.drawCenteredTextWithShadow(this.textRenderer,
                        Text.literal("Have your own copy? Paste its path below. Or download it, or record without sound."),
                        this.width / 2, 68, 0xFFAAAAAA);
                if (this.ffmpegPathError != null) {
                    context.drawCenteredTextWithShadow(this.textRenderer,
                            Text.literal(this.ffmpegPathError), this.width / 2, 82, 0xFFFF6B6B);
                }
            }
            case INSTALLING -> {
                String label = "Downloading ffmpeg\u2026 " + this.session.installProgress + "%";
                context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(label),
                        this.width / 2, 60, 0xFFAAAAAA);
                drawProgressBar(context, this.session.installProgress, 100);
            }
            case ENCODING -> {
                int total = Math.max(1, this.session.frameCount);
                int done = this.session.encodedFrames;
                if (done >= total && this.session.muxingAudio) {
                    context.drawCenteredTextWithShadow(this.textRenderer,
                            Text.literal("Adding the intro sound\u2026"), this.width / 2, 60, 0xFFAAAAAA);
                } else {
                    String label = "Rendering video\u2026 " + done + " / " + total + " frames";
                    context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(label),
                            this.width / 2, 60, 0xFFAAAAAA);
                }
                drawProgressBar(context, done, total);
            }
            case DONE -> {
                context.drawCenteredTextWithShadow(this.textRenderer,
                        Text.literal("Recording saved!"), this.width / 2, 55, 0xFF7FFF7F);
                context.drawCenteredTextWithShadow(this.textRenderer,
                        Text.literal(String.valueOf(this.session.outputFile)), this.width / 2, 72, 0xFFAAAAAA);
                if (this.session.soundIncluded) {
                    context.drawCenteredTextWithShadow(this.textRenderer,
                            Text.literal("Intro sound included"), this.width / 2, 90, 0xFF7FFF7F);
                } else if (this.session.soundMessage != null) {
                    int noteY = 90;
                    for (OrderedText line : this.textRenderer.wrapLines(
                            Text.literal(this.session.soundMessage), this.width - 120)) {
                        context.drawTextWithShadow(this.textRenderer, line, 60, noteY, 0xFFAAAAAA);
                        noteY += 10;
                    }
                }
            }
            case CANCELLED -> context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal(this.session.errorMessage != null ? this.session.errorMessage : "Recording cancelled."),
                    this.width / 2, 60, 0xFFFFD54A);
            case FAILED -> {
                context.drawCenteredTextWithShadow(this.textRenderer,
                        Text.literal("Recording failed"), this.width / 2, 55, 0xFFFF6B6B);
                String message = this.session.errorMessage != null ? this.session.errorMessage : "Unknown error.";
                for (OrderedText line : this.textRenderer.wrapLines(Text.literal(message), this.width - 80)) {
                    context.drawTextWithShadow(this.textRenderer, line, 40, 72, 0xFFCCCCCC);
                }
            }
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private void drawProgressBar(DrawContext context, int done, int total) {
        int barX = (this.width - MAX_BAR_WIDTH) / 2;
        int barY = 82;
        int filled = (int) ((float) done / total * MAX_BAR_WIDTH);
        context.fill(barX, barY, barX + MAX_BAR_WIDTH, barY + 4, 0xFF404040);
        context.fill(barX, barY, barX + filled, barY + 4, 0xFF4CAF50);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
