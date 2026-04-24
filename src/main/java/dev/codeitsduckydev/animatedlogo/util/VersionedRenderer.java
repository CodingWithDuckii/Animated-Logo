package dev.codeitsduckydev.animatedlogo.util;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class VersionedRenderer {
    private static MethodHandle DRAW_TEXTURE_MH;
    private static MethodHandle SET_SHADER_COLOR_MH;
    private static Object GUI_TEXTURED_PIPELINE;
    private static boolean IS_NEW_API = false;

    static {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        try {
            // Try to find DrawContext.setShaderColor (1.21.2+)
            try {
                SET_SHADER_COLOR_MH = lookup.findVirtual(DrawContext.class, "setShaderColor", 
                    MethodType.methodType(void.class, float.class, float.class, float.class, float.class));
                IS_NEW_API = true;
            } catch (NoSuchMethodException e) {
                // Fallback to RenderSystem.setShaderColor (1.21.1 and earlier)
                Class<?> renderSystemClass = Class.forName("com.mojang.blaze3d.systems.RenderSystem");
                SET_SHADER_COLOR_MH = lookup.findStatic(renderSystemClass, "setShaderColor", 
                    MethodType.methodType(void.class, float.class, float.class, float.class, float.class));
            }

            // Try to find DrawContext.drawTexture
            if (IS_NEW_API) {
                // 1.21.2+ signature: (Identifier, int, int, float, float, int, int, int, int)
                DRAW_TEXTURE_MH = lookup.findVirtual(DrawContext.class, "drawTexture", 
                    MethodType.methodType(void.class, Identifier.class, int.class, int.class, float.class, float.class, int.class, int.class, int.class, int.class));
            } else {
                // 1.21.1 signature: (RenderPipeline, Identifier, int, int, float, float, int, int, int, int)
                Class<?> pipelineClass = Class.forName("net.minecraft.client.gl.RenderPipeline");
                Class<?> pipelinesClass = Class.forName("net.minecraft.client.gl.RenderPipelines");
                GUI_TEXTURED_PIPELINE = pipelinesClass.getField("GUI_TEXTURED").get(null);
                
                DRAW_TEXTURE_MH = lookup.findVirtual(DrawContext.class, "drawTexture", 
                    MethodType.methodType(void.class, pipelineClass, Identifier.class, int.class, int.class, float.class, float.class, int.class, int.class, int.class, int.class));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void setShaderColor(DrawContext context, float r, float g, float b, float a) {
        try {
            if (IS_NEW_API) {
                SET_SHADER_COLOR_MH.invoke(context, r, g, b, a);
            } else {
                SET_SHADER_COLOR_MH.invoke(r, g, b, a);
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public static void drawTexture(DrawContext context, Identifier texture, int x, int y, float u, float v, int width, int height, int textureWidth, int textureHeight) {
        try {
            if (IS_NEW_API) {
                DRAW_TEXTURE_MH.invoke(context, texture, x, y, u, v, width, height, textureWidth, textureHeight);
            } else {
                DRAW_TEXTURE_MH.invoke(context, GUI_TEXTURED_PIPELINE, texture, x, y, u, v, width, height, textureWidth, textureHeight);
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public static void fill(DrawContext context, int x1, int y1, int x2, int y2, int color) {
        context.fill(x1, y1, x2, y2, color);
    }
}
