package dev.codeitsduckydev.animatedlogo.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import dev.codeitsduckydev.animatedlogo.DonationConfig;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.net.URI;

/**
 * Legacy (1.21.1 and below) variant: pre-1.21.2 click handling used
 * plain double coordinates instead of the Click record.
 */
public final class DonationNotificationWidget extends ClickableWidget {
    private static final String PAYPAL_URL = "https://paypal.me/duckiiexe";
    private static final int PANEL_WIDTH = 230;
    private static final int PANEL_HEIGHT = 70;
    private static final int BUTTON_X = 10;
    private static final int BUTTON_Y = 42;
    private static final int BUTTON_WIDTH = 104;
    private static final int BUTTON_HEIGHT = 20;
    private static final long SLIDE_DURATION_MS = 450;

    private final int targetX;
    private final long slideStartTime = System.currentTimeMillis();

    public DonationNotificationWidget(int x, int y) {
        super(x, y, PANEL_WIDTH, PANEL_HEIGHT, Text.literal("Donate on PayPal"));
        this.targetX = x;
        this.active = true;
        this.visible = true;
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        if (topLayerOnly) {
            return;
        }
        drawPanel(context, mouseX, mouseY);
    }

    private boolean topLayerOnly = true;

    public void renderTopLayer(DrawContext context, int mouseX, int mouseY, float delta) {
        if (visible) {
            long elapsed = System.currentTimeMillis() - slideStartTime;
            float progress = Math.min(1.0f, (float) elapsed / SLIDE_DURATION_MS);
            float eased = 1.0f - (float) Math.pow(1.0f - progress, 3.0);
            setX((int) (targetX + (1.0f - eased) * (PANEL_WIDTH + 12)));
            drawPanel(context, mouseX, mouseY);
        }
    }

    private void drawPanel(DrawContext context, int mouseX, int mouseY) {
        int panelColor = 0xE6101018;
        int borderColor = 0xFF5A5A6A;
        int buttonColor = isHovered() ? 0xFF168BDE : 0xFF087CC1;

        context.fill(getX(), getY(), getX() + width, getY() + height, panelColor);
        context.fill(getX(), getY(), getX() + width, getY() + 1, borderColor);
        context.fill(getX(), getY() + height - 1, getX() + width, getY() + height, borderColor);
        context.fill(getX(), getY(), getX() + 1, getY() + height, borderColor);
        context.fill(getX() + width - 1, getY(), getX() + width, getY() + height, borderColor);

        context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer,
                Text.literal("Help keep Animated Logo alive"), getX() + 10, getY() + 9, 0xFFFFFFFF);
        context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer,
                Text.literal("Donate on PayPal"), getX() + 10, getY() + 23, 0xFFCCCCCC);

        int buttonLeft = getX() + BUTTON_X;
        int buttonTop = getY() + BUTTON_Y;
        context.fill(buttonLeft, buttonTop, buttonLeft + BUTTON_WIDTH, buttonTop + BUTTON_HEIGHT, buttonColor);
        context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer,
                Text.literal("PayPal"), buttonLeft + BUTTON_WIDTH / 2, buttonTop + 6, 0xFFFFFFFF);

        context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer,
                Text.literal("×"), getX() + width - 16, getY() + 6, 0xFFAAAAAA);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        int localX = (int) mouseX - getX();
        int localY = (int) mouseY - getY();
        if (localX >= width - 24 && localY < 28) {
            this.visible = false;
            DonationConfig.setDismissed();
            return;
        }
        if (localX >= BUTTON_X && localX < BUTTON_X + BUTTON_WIDTH
                && localY >= BUTTON_Y && localY < BUTTON_Y + BUTTON_HEIGHT) {
            Util.getOperatingSystem().open(URI.create(PAYPAL_URL));
        }
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        builder.put(net.minecraft.client.gui.screen.narration.NarrationPart.TITLE, getMessage());
    }
}
