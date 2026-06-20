package dev.blufony.autoreroll.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.blufony.autoreroll.config.AutoRerollConfig;
import iskallia.vault.client.atlas.TextureAtlasRegion;
import iskallia.vault.client.gui.framework.ScreenTextures;
import iskallia.vault.client.gui.framework.element.ButtonElement;
import iskallia.vault.client.gui.framework.render.spi.IElementRenderer;
import iskallia.vault.client.gui.framework.spatial.spi.ISpatial;
import org.jetbrains.annotations.NotNull;

public class AutoBuyToggle extends ButtonElement<AutoBuyToggle> {
    private boolean autoBuyEnabled = false;

    public AutoBuyToggle(ISpatial spatial, Runnable onClick) {
        super(spatial, ScreenTextures.LANDSCAPE_BUTTON_TOGGLE_OFF_TEXTURES, onClick);
        this.autoBuyEnabled = AutoRerollConfig.AUTO_BUY.get();
    }

    public AutoBuyToggle setAutoBuy(boolean enabled) {
        this.autoBuyEnabled = enabled;
        return this;
    }

    public boolean isAutoBuy() {
        return this.autoBuyEnabled;
    }

    @Override
    public void render(IElementRenderer renderer, @NotNull PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        ButtonTextures textures = this.autoBuyEnabled
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
