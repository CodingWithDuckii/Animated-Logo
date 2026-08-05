package dev.codeitsduckydev.animatedlogo.gui;

import dev.codeitsduckydev.animatedlogo.DevNoticeConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CheckboxWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;

import java.net.URI;
import java.util.List;

/**
 * One-time developer notice screen, shown after the splash animation.
 * The body text is clipped to a fixed content area above the footer
 * (checkbox + buttons) and scrolls with the mouse wheel if it overflows,
 * so long text can never overlap the footer controls.
 */
public class DevNoticeScreen extends Screen {
    private static final String YOUTUBE_URL = "https://youtu.be/FDcKyeK3LIg";
    private static final int LINE_GAP = 1;
    private static final int PARAGRAPH_GAP = 6;
    private static final int TEXT_TOP = 45;
    private static final int FOOTER_HEIGHT = 60; // space reserved for checkbox + buttons

    private final Screen parent;
    private CheckboxWidget dontShowAgainCheckbox;
    private int scrollOffset = 0;

    private final List<Text> paragraphs = List.of(
            Text.literal("Hi everyone, Duckii here \u2014 the developer behind Animated Logo, Back on Death, and RTP Mod."),
            Text.literal("These mods are moving to an organization, and I'm coming back to Minecraft development. More mods are on the way!"),
            Text.literal("J0intekk has been promoted to Staff Designer. If you don't know him, he's the logo designer behind Animated Logo, Auto Sorter, and Osmium (leak). Big thanks to him for all the great work on our logos!"),
            Text.literal("I'm also setting up a Discord server for updates and access, since my main account got locked out."),
            Text.literal("From now on, every mod will include proper credits (already true for most, but now official), and no more AI-written descriptions \u2014 we'll be using real images and content instead."),
            Text.literal("A comeback video is on the way too \u2014 it'll be posted on my YouTube channel, so get ready!")
    );

    public DevNoticeScreen(Screen parent) {
        super(Text.literal("Developer Notice"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        int buttonWidth = 120;
        int centerX = this.width / 2;
        int bottomY = this.height - 32;

        this.addDrawableChild(ButtonWidget.builder(Text.literal("YouTube"), btn ->
                Util.getOperatingSystem().open(URI.create(YOUTUBE_URL))
        ).dimensions(centerX - buttonWidth - 5, bottomY, buttonWidth, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Continue"), btn -> this.close())
                .dimensions(centerX + 5, bottomY, buttonWidth, 20).build());

        this.dontShowAgainCheckbox = CheckboxWidget.builder(Text.literal("Don't show this again"), this.textRenderer)
                .pos(centerX - buttonWidth, bottomY - 24)
                .checked(DevNoticeConfig.isDisabled())
                .build();
        this.addDrawableChild(this.dontShowAgainCheckbox);

        this.scrollOffset = 0;
    }

    @Override
    public void close() {
        if (this.dontShowAgainCheckbox != null) {
            DevNoticeConfig.setDisabled(this.dontShowAgainCheckbox.isChecked());
        }
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private int panelWidth() {
        return Math.min(this.width - 40, 360);
    }

    private int panelX() {
        return (this.width - panelWidth()) / 2;
    }

    private int textWidth() {
        return panelWidth() - 20;
    }

    private int contentBottom() {
        return this.height - FOOTER_HEIGHT;
    }

    private int totalContentHeight() {
        int width = textWidth();
        int height = 0;
        for (Text paragraph : paragraphs) {
            List<OrderedText> lines = this.textRenderer.wrapLines(paragraph, width);
            height += lines.size() * (this.textRenderer.fontHeight + LINE_GAP);
            height += PARAGRAPH_GAP;
        }
        return height;
    }

    private int maxScroll() {
        int visible = contentBottom() - TEXT_TOP;
        return Math.max(0, totalContentHeight() - visible);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        this.scrollOffset = MathHelper.clamp(this.scrollOffset - (int) (verticalAmount * 14), 0, maxScroll());
        return true;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Avoid Screen#renderBackground here: it triggers the vanilla panorama
        // blur, which can already have been triggered once this frame during
        // the title screen transition, causing "Can only blur once per frame".
        context.fill(0, 0, this.width, this.height, 0xC0101010);

        int panelX = panelX();
        int textWidth = textWidth();
        int contentBottom = contentBottom();

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 0xFFFFD54A);

        // Clip body text to the area above the footer so long text scrolls
        // instead of overlapping the checkbox / buttons.
        context.enableScissor(panelX, TEXT_TOP, panelX + panelWidth(), contentBottom);

        int y = TEXT_TOP - this.scrollOffset;
        for (Text paragraph : paragraphs) {
            List<OrderedText> lines = this.textRenderer.wrapLines(paragraph, textWidth);
            for (OrderedText line : lines) {
                if (y + this.textRenderer.fontHeight >= TEXT_TOP && y <= contentBottom) {
                    context.drawTextWithShadow(this.textRenderer, line, panelX + 10, y, 0xFFE0E0E0);
                }
                y += this.textRenderer.fontHeight + LINE_GAP;
            }
            y += PARAGRAPH_GAP;
        }

        context.disableScissor();

        super.render(context, mouseX, mouseY, delta);
    }
}
