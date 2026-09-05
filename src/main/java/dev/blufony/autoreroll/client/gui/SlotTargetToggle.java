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
 * Toggle between "search all slots" and "omega slot only" for black-market
 * auto-reroll target selection.
 *
 * State lives exclusively in {@link AutoRerollConfig} (single source of
 * truth); this widget only reads it and never caches.
 */
public class SlotTargetToggle extends ButtonElement<SlotTargetToggle> {

    public SlotTargetToggle(ISpatial spatial, Runnable onClick) {
        super(spatial, ScreenTextures.BUTTON_TOGGLE_OFF_TEXTURES, onClick);
    }

    public boolean isOmegaOnly() {
        return !AutoRerollConfig.SEARCH_ALL_SLOTS.get();
    }

    @Override
    public void render(IElementRenderer renderer, @NotNull PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        ButtonTextures textures = this.isOmegaOnly()
            ? ScreenTextures.BUTTON_TOGGLE_ON_TEXTURES
            : ScreenTextures.BUTTON_TOGGLE_OFF_TEXTURES;

        TextureAtlasRegion texture = textures.selectTexture(
            this.isDisabled(),
            this.containsMouse(mouseX, mouseY),
            false
        );

        renderer.render(texture, poseStack, this.getWorldSpatial());
    }
}
