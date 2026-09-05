package dev.blufony.autoreroll.client.mixin;

import iskallia.vault.client.gui.framework.element.ButtonElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.function.Consumer;

/**
 * Exposes private internals of the Vault Hunters button element so the
 * black-market mixins can (a) wrap the vanilla reset button's click handler
 * and (b) identify buttons by their texture set - both without raw
 * reflection, keeping references remap-safe in production.
 */
@Mixin(value = ButtonElement.class, remap = false)
public interface ButtonElementAccessor {
    @Accessor("onClick")
    void autoreroll$setOnClick(Consumer<ButtonElement<?>> onClick);

    @Accessor("onClick")
    Consumer<ButtonElement<?>> autoreroll$getOnClick();    @Accessor("textures")
    ButtonElement.ButtonTextures autoreroll$getTextures();
}
