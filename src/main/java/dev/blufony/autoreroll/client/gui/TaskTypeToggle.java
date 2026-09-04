package dev.blufony.autoreroll.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.blufony.autoreroll.config.BountyTableConfig;
import iskallia.vault.client.atlas.TextureAtlasRegion;
import iskallia.vault.client.gui.framework.ScreenTextures;
import iskallia.vault.client.gui.framework.element.ButtonElement;
import iskallia.vault.client.gui.framework.render.spi.IElementRenderer;
import iskallia.vault.client.gui.framework.spatial.spi.ISpatial;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import org.jetbrains.annotations.NotNull;

/**
 * Small toggle switch for a single bounty task type (e.g. kill_entity,
 * mining). Persisted per type via {@link BountyTableConfig}.
 *
 * State lives exclusively in {@link BountyTableConfig} (single source of
 * truth); this widget only reads it and never caches.
 */
public class TaskTypeToggle extends ButtonElement<TaskTypeToggle> {
    private final String taskTypeId;
    private final String label;

    public TaskTypeToggle(ISpatial spatial, String taskTypeId, String label, Runnable onClick) {
        super(spatial, ScreenTextures.BUTTON_TOGGLE_OFF_TEXTURES, onClick);
        this.taskTypeId = taskTypeId;
        this.label = label;
    }

    public String getTaskTypeId() {
        return this.taskTypeId;
    }

    public boolean isOn() {
        return BountyTableConfig.isTaskTypeEnabled(this.taskTypeId);
    }

    @Override
    public void render(IElementRenderer renderer, @NotNull PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        ButtonTextures textures = this.isOn()
            ? ScreenTextures.BUTTON_TOGGLE_ON_TEXTURES
            : ScreenTextures.BUTTON_TOGGLE_OFF_TEXTURES;

        TextureAtlasRegion texture = textures.selectTexture(
            this.isDisabled(),
            this.containsMouse(mouseX, mouseY),
            false
        );

        renderer.render(texture, poseStack, this.getWorldSpatial());
    }

    public static MutableComponent tooltip(boolean on, String label) {
        return on
            ? new TextComponent(label + ": \u2713").withStyle(ChatFormatting.GREEN)
            : new TextComponent(label).withStyle(ChatFormatting.GRAY);
    }
}
