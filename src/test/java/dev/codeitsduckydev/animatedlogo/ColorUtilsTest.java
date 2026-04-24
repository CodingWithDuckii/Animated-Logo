package dev.codeitsduckydev.animatedlogo;

import dev.codeitsduckydev.animatedlogo.util.ColorUtils;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ColorUtilsTest {

    @Test
    public void testApplyAlphaToColor() {
        int color = 0x00FF0000; // Red
        float alpha = 0.5f;
        int expectedAlpha = (int)(0.5f * 255);
        int expectedColor = (expectedAlpha << 24) | color;
        
        // We can't easily run this without Minecraft classes unless we mock MathHelper
        // But we can verify the bitwise logic
        int result = ColorUtils.applyAlphaToColor(color, alpha);
        assertEquals(expectedColor, result);
    }

    @Test
    public void testApplyAlphaFull() {
        int color = 0x0000FF00; // Green
        int result = ColorUtils.applyAlphaToColor(color, 1.0f);
        assertEquals(0xFF00FF00, result);
    }

    @Test
    public void testApplyAlphaZero() {
        int color = 0x000000FF; // Blue
        int result = ColorUtils.applyAlphaToColor(color, 0.0f);
        assertEquals(0x000000FF, result);
    }
}
