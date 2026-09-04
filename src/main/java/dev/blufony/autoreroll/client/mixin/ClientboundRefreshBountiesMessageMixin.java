package dev.blufony.autoreroll.client.mixin;

import dev.blufony.autoreroll.client.BountyAutoRerollManager;
import iskallia.vault.network.message.bounty.ClientboundRefreshBountiesMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Supplier;

/**
 * Intercepts the server's bounty refresh response (sent after every reroll,
 * see ServerboundRerollMessage.handle) and forwards it to the bounty
 * auto-reroll manager AFTER the vanilla handler has replaced the container's
 * bounty lists and refreshed the selection - so the manager sees the freshly
 * rolled bounty.
 */
@Mixin(value = ClientboundRefreshBountiesMessage.class, remap = false)
public class ClientboundRefreshBountiesMessageMixin {
    private static final Logger LOGGER = LogManager.getLogger("AutoReroll");

    @Inject(method = "handle", at = @At("RETURN"))
    private static void autoreroll$afterHandle(ClientboundRefreshBountiesMessage message, Supplier<?> contextSupplier, CallbackInfo ci) {
        try {
            BountyAutoRerollManager.onBountiesRefreshed();
        } catch (Exception e) {
            LOGGER.error("Error processing bounty refresh in auto-reroll", e);
        }
    }
}
