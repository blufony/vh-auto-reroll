package dev.blufony.autoreroll.client.mixin;

import iskallia.vault.client.gui.screen.bounty.element.BountyElement;
import iskallia.vault.client.gui.screen.bounty.element.BountyTableContainerElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes private vanilla internals of the bounty table container so the
 * auto-reroll loop can (a) trigger the exact vanilla reroll path
 * (pearl validation + ServerboundRerollMessage) and (b) read the currently
 * selected bounty after the server response refreshed the selection.
 * Using @Invoker/@Accessor ensures references are remapped correctly in production.
 */
@Mixin(BountyTableContainerElement.class)
public interface BountyTableContainerElementInvoker {
    @Invoker("handleReroll")
    void autoreroll$invokeHandleReroll();

    @Accessor("bountyElement")
    BountyElement autoreroll$getBountyElement();
}
