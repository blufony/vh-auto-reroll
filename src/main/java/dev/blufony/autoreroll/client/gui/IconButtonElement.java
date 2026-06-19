package dev.blufony.autoreroll.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.blufony.autoreroll.client.AutoRerollManager;
import iskallia.vault.client.atlas.TextureAtlasRegion;
import iskallia.vault.client.gui.framework.element.ButtonElement;
import iskallia.vault.client.gui.framework.render.spi.IElementRenderer;
import iskallia.vault.client.gui.framework.spatial.spi.ISpatial;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

public class IconButtonElement extends ButtonElement<IconButtonElement> {
    private Supplier<TextureAtlasRegion> iconSupplier;
    private Supplier<TextureAtlasRegion> activeIconSupplier;

    public IconButtonElement(ISpatial spatial, ButtonTextures textures, Runnable onClick) {
        super(spatial, textures, onClick);
    }

    public IconButtonElement icon(Supplier<TextureAtlasRegion> icon) {
        this.iconSupplier = icon;
        return this;
    }
    
    public IconButtonElement activeIcon(Supplier<TextureAtlasRegion> activeIcon) {
        this.activeIconSupplier = activeIcon;
        return this;
    }

    @Override
    public void render(IElementRenderer renderer, @NotNull PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        super.render(renderer, poseStack, mouseX, mouseY, partialTick);
        
        TextureAtlasRegion iconToRender = null;
        
        if (this.activeIconSupplier != null && AutoRerollManager.isRunning()) {
            iconToRender = this.activeIconSupplier.get();
        } else if (this.iconSupplier != null) {
            iconToRender = this.iconSupplier.get();
        }
        
        if (iconToRender != null) {
            // Center the icon on the button
            int centerX = this.getWorldSpatial().x() + (this.getWorldSpatial().width() - iconToRender.width()) / 2;
            int centerY = this.getWorldSpatial().y() + (this.getWorldSpatial().height() - iconToRender.height()) / 2;
            renderer.render(iconToRender, poseStack, centerX, centerY, 1);
        }
    }
}
