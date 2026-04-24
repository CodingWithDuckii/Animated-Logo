package dev.codeitsduckydev.animatedlogo.util;

import net.minecraft.util.math.MathHelper;

public class ColorUtils {
    /**
     * Applies an alpha factor to an ARGB color.
     * @param color The original ARGB color.
     * @param alpha The alpha factor (0.0 to 1.0).
     * @return The new ARGB color.
     */
    public static int applyAlphaToColor(int color, float alpha) {
        int rgb = color & 0x00FFFFFF;
        int a = MathHelper.clamp((int)(alpha * 255), 0, 255);
        return (a << 24) | rgb;
    }
}
