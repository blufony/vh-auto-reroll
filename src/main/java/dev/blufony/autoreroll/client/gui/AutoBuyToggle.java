package dev.blufony.autoreroll.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.blufony.autoreroll.config.AutoRerollConfig;
import iskallia.vault.client.atlas.TextureAtlasRegion;
import iskallia.vault.client.gui.framework.ScreenTextures;
import iskallia.vault.client.gui.framework.element.ButtonElement;
import iskallia.vault.client.gui.framework.render.spi.IElementRenderer;
import iskallia.vault.client.gui.framework.spatial.spi.ISpatial;
import org.jetbrains.annotations.NotNull;

/**
 * Toggle for black-market auto-buy mode.
 *
 * State lives exclusively in {@link AutoRerollConfig} (single source of
 * truth); this widget only reads it and never caches.
 */
public class AutoBuyToggle extends ButtonElement<AutoBuyToggle> {

    public AutoBuyToggle(ISpatial spatial, Runnable onClick) {
        super(spatial, ScreenTextures.LANDSCAPE_BUTTON_TOGGLE_OFF_TEXTURES, onClick);
    }

    public boolean isAutoBuy() {
        return AutoRerollConfig.AUTO_BUY.get();
    }

    @Override
    public void render(IElementRenderer renderer, @NotNull PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        ButtonTextures textures = this.isAutoBuy()
            ? ScreenTextures.LANDSCAPE_BUTTON_TOGGLE_ON_TEXTURES
            : ScreenTextures.LANDSCAPE_BUTTON_TOGGLE_OFF_TEXTURES;

        TextureAtlasRegion texture = textures.selectTexture(
            this.isDisabled(),
            this.containsMouse(mouseX, mouseY),
            false
        );

        renderer.render(texture, poseStack, this.getWorldSpatial());
    }
}
