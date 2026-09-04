package dev.codeitsduckydev.animatedlogo.gui;

import dev.codeitsduckydev.animatedlogo.AnimatedLogoRecorder;
import dev.codeitsduckydev.animatedlogo.ModConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CheckboxWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * In-game options for Animated Logo. Reachable from the Mods menu (Mod Menu)
 * via the configuration button. Every toggle applies and saves immediately.
 */
public class ModOptionsScreen extends Screen {
    private final Screen parent;
    private final List<CheckboxWidget> checkboxes = new ArrayList<>();
    private CheckboxWidget animateOnStartupBox;
    private CheckboxWidget animateOnReloadBox;
    private CheckboxWidget playSoundBox;
    private CheckboxWidget showDonationBox;

    public ModOptionsScreen(Screen parent) {
        super(Text.literal("Animated Logo Options"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        this.checkboxes.clear();

        int boxWidth = Math.min(this.width - 80, 300);
        int left = (this.width - boxWidth) / 2;
        int y = 44;

        this.animateOnStartupBox = addCheckbox(left, y, "Animated intro on game start", ModConfig.get().animateOnStartup());
        y += 24;
        this.animateOnReloadBox = addCheckbox(left, y, "Animated intro on resource reload", ModConfig.get().animateOnResourceReload());
        y += 24;
        this.playSoundBox = addCheckbox(left, y, "Play intro sound", ModConfig.get().playStartupSound());
        y += 24;
        this.showDonationBox = addCheckbox(left, y, "Show donation notification on title screen", ModConfig.get().showDonationNotification());
        y += 28;

        int buttonY = this.height - 32;
        int recordWidth = Math.min(180, boxWidth / 2 - 4);
        int doneWidth = Math.min(120, boxWidth / 2 - 4);
        int centerX = this.width / 2;

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Record Intro Animation"), btn -> startRecording())
                .dimensions(centerX - recordWidth - 3, buttonY, recordWidth, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Done"), btn -> this.close())
                .dimensions(centerX + 3, buttonY, doneWidth, 20).build());
    }

    private CheckboxWidget addCheckbox(int x, int y, String label, boolean checked) {
        CheckboxWidget box = CheckboxWidget.builder(Text.literal(label), this.textRenderer)
                .pos(x, y)
                .checked(checked)
                .build();
        this.addDrawableChild(box);
        this.checkboxes.add(box);
        return box;
    }

    /** Applies checkbox changes to the config every tick (vanilla checkbox has no press callback). */
    @Override
    public void tick() {
        ModConfig config = ModConfig.get();
        if (this.animateOnStartupBox != null && this.animateOnStartupBox.isChecked() != config.animateOnStartup()) {
            config.setAnimateOnStartup(this.animateOnStartupBox.isChecked());
        }
        if (this.animateOnReloadBox != null && this.animateOnReloadBox.isChecked() != config.animateOnResourceReload()) {
            config.setAnimateOnResourceReload(this.animateOnReloadBox.isChecked());
        }
        if (this.playSoundBox != null && this.playSoundBox.isChecked() != config.playStartupSound()) {
            config.setPlayStartupSound(this.playSoundBox.isChecked());
        }
        if (this.showDonationBox != null && this.showDonationBox.isChecked() != config.showDonationNotification()) {
            config.setShowDonationNotification(this.showDonationBox.isChecked());
        }
    }

    private void startRecording() {
        // The status screen takes over immediately and returns here when
        // the export is done, so no screen juggling is needed.
        AnimatedLogoRecorder.startRecording(this);
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xC0101010);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Changes apply immediately"),
                this.width / 2, 33, 0xFF9E9E9E);
        // Show the saved ffmpeg location so it is clear the path is kept.
        String ffmpeg = ModConfig.get().ffmpegPath();
        int ffmpegY = 152;
        if (ffmpeg.isBlank()) {
            context.drawTextWithShadow(this.textRenderer,
                    Text.literal("ffmpeg: not set - you can point the mod at your own copy when recording"),
                    40, ffmpegY, 0xFF9E9E9E);
        } else {
            boolean ok = AnimatedLogoRecorder.isValidFfmpegPath(ffmpeg);
            context.drawTextWithShadow(this.textRenderer,
                    Text.literal("ffmpeg: " + (ok ? "ok" : "saved, but not found anymore")),
                    40, ffmpegY, ok ? 0xFF9ECE9A : 0xFFFF6B6B);
            for (OrderedText line : this.textRenderer.wrapLines(Text.literal(ffmpeg), this.width - 100)) {
                ffmpegY += 10;
                context.drawTextWithShadow(this.textRenderer, line, 40, ffmpegY, 0xFFAAAAAA);
            }
        }
        super.render(context, mouseX, mouseY, delta);
    }
}
