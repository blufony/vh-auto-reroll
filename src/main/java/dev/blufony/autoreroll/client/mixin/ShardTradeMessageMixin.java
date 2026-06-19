package dev.blufony.autoreroll.client.mixin;

import dev.blufony.autoreroll.client.AutoRerollManager;
import iskallia.vault.network.message.ShardTradeMessage;
import net.minecraftforge.network.NetworkEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Supplier;

@Mixin(value = ShardTradeMessage.class, remap = false)
public class ShardTradeMessageMixin {
    @Inject(method = "handle", at = @At("HEAD"))
    private static void onShardTradeReceived(ShardTradeMessage message, 
                                             Supplier<NetworkEvent.Context> contextSupplier,
                                             CallbackInfo ci) {
        if (AutoRerollManager.isRunning()) {
            AutoRerollManager.onServerResponse(message);
        }
    }
}
