package dev.codeitsduckydev.animatedlogo;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.codeitsduckydev.animatedlogo.gui.ModOptionsScreen;

/**
 * Mod Menu integration: registers the options screen behind the
 * "Configure" button for Animated Logo in the Mods menu.
 */
public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ModOptionsScreen::new;
    }
}
