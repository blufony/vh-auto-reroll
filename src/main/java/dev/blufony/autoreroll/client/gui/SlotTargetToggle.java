package dev.blufony.autoreroll.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.blufony.autoreroll.config.AutoRerollConfig;
import iskallia.vault.client.atlas.TextureAtlasRegion;
import iskallia.vault.client.gui.framework.ScreenTextures;
import iskallia.vault.client.gui.framework.element.ButtonElement;
import iskallia.vault.client.gui.framework.render.spi.IElementRenderer;
import iskallia.vault.client.gui.framework.spatial.spi.ISpatial;
import org.jetbrains.annotations.NotNull;

public class SlotTargetToggle extends ButtonElement<SlotTargetToggle> {
    private boolean omegaOnlyMode = false;

    public SlotTargetToggle(ISpatial spatial, Runnable onClick) {
        super(spatial, ScreenTextures.BUTTON_TOGGLE_OFF_TEXTURES, onClick);
        this.omegaOnlyMode = !AutoRerollConfig.SEARCH_ALL_SLOTS.get();
    }

    public SlotTargetToggle setOmegaOnly(boolean mode) {
        this.omegaOnlyMode = mode;
        return this;
    }

    public boolean isOmegaOnly() {
        return this.omegaOnlyMode;
    }

    @Override
    public void render(IElementRenderer renderer, @NotNull PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        ButtonTextures textures = this.omegaOnlyMode
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
