package dev.blufony.autoreroll.client.mixin;

import iskallia.vault.client.gui.framework.element.spi.ElementStore;
import iskallia.vault.client.gui.framework.screen.AbstractElementContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the protected element store of Vault Hunters' element-container
 * screens. {@code @Shadow} cannot be used for inherited fields in a mixin
 * targeting a subclass, so this accessor on the declaring class is used
 * instead.
 */
@Mixin(value = AbstractElementContainerScreen.class, remap = false)
public interface AbstractElementContainerScreenAccessor {
    @Accessor("elementStore")
    ElementStore autoreroll$getElementStore();
}
