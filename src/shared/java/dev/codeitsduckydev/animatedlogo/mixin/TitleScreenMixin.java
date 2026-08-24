package dev.codeitsduckydev.animatedlogo.mixin;

import dev.codeitsduckydev.animatedlogo.AnimatedLogo;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public class TitleScreenMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void animatedLogo$renderDonationWidget(DrawContext context, int mouseX, int mouseY,
                                                    float delta, CallbackInfo ci) {
        if (AnimatedLogo.DONATION_WIDGET != null) {
            AnimatedLogo.DONATION_WIDGET.renderTopLayer(context, mouseX, mouseY, delta);
        }
    }
}
