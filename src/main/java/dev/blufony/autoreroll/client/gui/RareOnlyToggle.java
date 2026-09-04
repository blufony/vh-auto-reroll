package dev.blufony.autoreroll.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.blufony.autoreroll.config.BountyTableConfig;
import iskallia.vault.client.gui.framework.ScreenTextures;
import iskallia.vault.client.gui.framework.element.ButtonElement;
import iskallia.vault.client.gui.framework.render.spi.IElementRenderer;
import iskallia.vault.client.gui.framework.spatial.spi.ISpatial;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import org.jetbrains.annotations.NotNull;

/**
 * Horizontal toggle for the "rare only" condition, rendered to the right of
 * the auto-reroll button. Hand-drawn with colored quads so the switch is
 * gray when off and yellow (the game's "rare" colour) when on.
 *
 * State lives exclusively in {@link BountyTableConfig} (single source of
 * truth); this widget only reads it and never caches.
 */
public class RareOnlyToggle extends ButtonElement<RareOnlyToggle> {
    /** Track gray, clearly lighter than the border. */
    private static final int TRACK_COLOR = 0xFF6E6E6E;
    /** Border: dark gray, slightly brighter than the old track. */
    private static final int BORDER_COLOR = 0xFF3A3A3A;
    /** White (common bounty) knob when off. */
    private static final int KNOB_OFF_COLOR = 0xFFFFFFFF;
    /** Yellow (game "rare" colour) knob when on. */
    private static final int KNOB_ON_COLOR = 0xFFFFDB29;

    public RareOnlyToggle(ISpatial spatial, Runnable onClick) {
        super(spatial, ScreenTextures.LANDSCAPE_BUTTON_TOGGLE_OFF_TEXTURES, onClick);
    }

    public boolean isOn() {
        return BountyTableConfig.isRareOnly();
    }

    @Override
    public void render(IElementRenderer renderer, @NotNull PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        // Draw a simple two-tone horizontal switch with a border:
        // OFF = gray track with white (common) knob on the left half.
        // ON  = gray track with yellow (game "rare" colour) knob on the right half.
        boolean rareOnly = isOn();
        ISpatial s = this.getWorldSpatial();
        int x = s.x(), y = s.y(), z = s.z();
        int w = s.width(), h = s.height();

        // Lighter gray track.
        renderer.renderColoredQuad(poseStack, TRACK_COLOR, x, y, z, w, h);

        // Knob: white (common) when off, yellow (rare) when on.
        int knobColor = rareOnly ? KNOB_ON_COLOR : KNOB_OFF_COLOR;
        int knobX = rareOnly ? x + w / 2 : x;
        renderer.renderColoredQuad(poseStack, knobColor, knobX, y, z, w / 2, h);

        // Border drawn last so the fill doesn't cover it.
        renderer.renderColoredHollowRect(poseStack, BORDER_COLOR, s);
    }

    public static MutableComponent tooltip(boolean on) {
        return on
            ? new TextComponent("Rare Bounties Only").withStyle(ChatFormatting.YELLOW)
            : new TextComponent("Common+ Bounties").withStyle(ChatFormatting.GRAY);
    }
}
