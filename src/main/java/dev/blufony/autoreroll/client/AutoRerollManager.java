package dev.blufony.autoreroll.client;

import dev.blufony.autoreroll.config.AutoRerollConfig;
import dev.blufony.autoreroll.util.FilterStorage;
import dev.blufony.autoreroll.util.ItemMatcher;
import dev.blufony.autoreroll.util.NotifyUtil;
import iskallia.vault.item.ItemShardPouch;
import iskallia.vault.network.message.ShardTradeMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Tuple;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.Optional;

@Mod.EventBusSubscriber(modid = "vh_auto_reroll", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class AutoRerollManager {
    private static final org.apache.logging.log4j.Logger LOGGER = org.apache.logging.log4j.LogManager.getLogger();
    
    private static boolean isRunning = false;
    private static int currentRerollCount = 0;
    private static Optional<ItemStack> filterItemStack = Optional.empty();
    private static boolean filterIsSimpleMode = false;
    private static Runnable resumeHandler;
    private static int pendingBuySlotIndex = -1;
    private static long buySentTick = -1;
    private static final int BUY_TIMEOUT_TICKS = 5;

    public static synchronized void start(Runnable onResume) {
        if (isRunning) {
            return;
        }
        
        filterItemStack = FilterStorage.loadFilterItem();
        if (filterItemStack.isEmpty()) {
            LOGGER.info("No valid filter item configured, auto-reroll disabled");
            return;
        }
        
        resumeHandler = onResume;
        isRunning = true;
        currentRerollCount = 0;
        filterIsSimpleMode = FilterStorage.isFilterSimpleMode();
        
        LOGGER.info("Auto-reroll started with {} filter: {}", filterIsSimpleMode ? "simple" : "Create filter", filterItemStack.get().getItem());
    }
    
    public static synchronized void stop() {
        if (!isRunning) {
            return;
        }
        
        isRunning = false;
        int finalCount = currentRerollCount;
        currentRerollCount = 0;
        buySentTick = -1;
        resetPendingBuy();
        
        if (resumeHandler != null) {
            resumeHandler.run();
            resumeHandler = null;
        }
        
        LOGGER.info("Auto-reroll stopped after {} rerolls", finalCount);
    }
    
    private static void sendResetRequest() {
        if (!isRunning) {
            return;
        }
        
        currentRerollCount++;
        
        iskallia.vault.init.ModNetwork.CHANNEL.sendToServer(
            iskallia.vault.network.message.ServerboundResetBlackMarketTradesMessage.INSTANCE
        );
    }
    
    private static void sendBuyRequest(int slotIndex) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            LOGGER.error("Cannot send buy request - player or level is null");
            resetPendingBuy();
            stop();
            return;
        }
        
        boolean isBmOpen = mc.screen instanceof iskallia.vault.client.gui.screen.ShardTradeScreen;
        if (!isBmOpen) {
            LOGGER.info("Black Market UI is closed - cannot send buy request");
            NotifyUtil.notifyPlayer(
                "[AutoReroll] Cannot auto-buy: Black Market UI is closed",
                iskallia.vault.init.ModSounds.BOOSTER_PACK_FAIL_SFX
            );
            resetPendingBuy();
            stop();
            return;
        }
        
        Player player = mc.player;
        var buyMessage = new iskallia.vault.network.message.ShardTradeTradeMessage(
            slotIndex,
            true,
            player.getUUID()
        );
        
        iskallia.vault.init.ModNetwork.CHANNEL.sendToServer(buyMessage);
        buySentTick = mc.level.getGameTime();
    }
    
    private static synchronized void resetPendingBuy() {
        pendingBuySlotIndex = -1;
        buySentTick = -1;
    }
    
    public static synchronized void onServerResponse(ShardTradeMessage message) {
        if (!isRunning || filterItemStack.isEmpty()) {
            return;
        }
        
        Map<Integer, Tuple<ItemStack, Integer>> availableTrades = message.getAvailableTrades();
        Level level = Minecraft.getInstance().level;
        
        if (pendingBuySlotIndex != -1) {
            boolean tradeStillPresent = availableTrades.containsKey(pendingBuySlotIndex);
            
            if (tradeStillPresent) {
                LOGGER.info("Auto-buy failed for slot {} - likely insufficient shards", pendingBuySlotIndex);
                NotifyUtil.notifyPlayer(
                    "[AutoReroll] Purchase failed - stopping",
                    iskallia.vault.init.ModSounds.BOOSTER_PACK_FAIL_SFX
                );
                resetPendingBuy();
                stop();
            } else {
                LOGGER.info("Auto-buy successful for slot {} - restarting rerolls", pendingBuySlotIndex);
                NotifyUtil.notifyPlayer(
                    "[AutoReroll] Purchase successful! Resuming rerolls",
                    iskallia.vault.init.ModSounds.VAULT_CHEST_OMEGA_OPEN
                );
                resetPendingBuy();
                currentRerollCount = 0;
                sendResetRequest();
            }
            return;
        }
        
        boolean searchAllSlots = AutoRerollConfig.SEARCH_ALL_SLOTS.get();
        int[] slotsToCheck = searchAllSlots ? new int[]{0, 1, 2} : new int[]{1};
        
        boolean foundMatch = false;
        int matchedSlotIndex = -1;
        
        for (int slotIndex : slotsToCheck) {
            Tuple<ItemStack, Integer> trade = availableTrades.get(slotIndex);
            
            if (trade == null) {
                continue;
            }
            
            ItemStack item = trade.getA();
            
            if (filterIsSimpleMode) {
                if (ItemMatcher.matchesByItemId(item, filterItemStack.get())) {
                    foundMatch = true;
                    matchedSlotIndex = slotIndex;
                    break;
                }
            } else if (ItemMatcher.matchesWithFilter(item, filterItemStack.get(), level)) {
                foundMatch = true;
                matchedSlotIndex = slotIndex;
                break;
            }
        }
        
        if (foundMatch) {
            if (AutoRerollConfig.AUTO_BUY.get() && matchedSlotIndex != -1) {
                Minecraft mc = Minecraft.getInstance();
                boolean isBmOpen = mc.screen instanceof iskallia.vault.client.gui.screen.ShardTradeScreen;
                
                if (!isBmOpen) {
                    LOGGER.info("Cannot auto-buy while Black Market UI is closed - stopping");
                    NotifyUtil.notifyPlayer(
                        "[AutoReroll] Cannot auto-buy: Black Market UI is closed",
                        iskallia.vault.init.ModSounds.BOOSTER_PACK_FAIL_SFX
                    );
                    stop();
                    return;
                }
                
                Tuple<ItemStack, Integer> matchedTrade = availableTrades.get(matchedSlotIndex);
                if (matchedTrade == null) {
                    LOGGER.warn("Trade data missing for matched slot {}, skipping", matchedSlotIndex);
                    sendResetRequest();
                    return;
                }
                
                int tradeCost = matchedTrade.getB();
                Player player = mc.player;
                int playerShards = ItemShardPouch.getShardCount(player);
                
                if (playerShards < tradeCost) {
                    LOGGER.info("Cannot afford trade at slot {} - need {} shards, have {}", 
                        matchedSlotIndex, tradeCost, playerShards);
                    NotifyUtil.notifyPlayer(
                        "[AutoReroll] Not enough shards for purchase - stopping",
                        iskallia.vault.init.ModSounds.BOOSTER_PACK_FAIL_SFX
                    );
                    stop();
                    return;
                }
                
                LOGGER.info("Found matching trade after {} rerolls - attempting auto-buy", currentRerollCount);
                pendingBuySlotIndex = matchedSlotIndex;
                sendBuyRequest(matchedSlotIndex);
            } else {
                LOGGER.info("Found matching trade after {} rerolls", currentRerollCount);
                NotifyUtil.notifyPlayer(
                    "[AutoReroll] Auto-Reroll Succeeded!",
                    iskallia.vault.init.ModSounds.VAULT_CHEST_OMEGA_OPEN
                );
                stop();
            }
            return;
        }
        
        if (currentRerollCount >= AutoRerollConfig.MAX_REROLLS.get()) {
            LOGGER.info("Max rerolls ({}) reached without finding match", AutoRerollConfig.MAX_REROLLS.get());
            NotifyUtil.notifyPlayer(
                "[AutoReroll] Failed to find target after " + AutoRerollConfig.MAX_REROLLS.get() + " rerolls",
                iskallia.vault.init.ModSounds.BOOSTER_PACK_FAIL_SFX
            );
            stop();
            return;
        }
        
        sendResetRequest();
    }
    
    public static int getCurrentRerollCount() {
        return currentRerollCount;
    }
    
    public static boolean isRunning() {
        return isRunning;
    }
    
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !isRunning || pendingBuySlotIndex == -1 || buySentTick == -1) {
            return;
        }
        
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null) {
            return;
        }
        
        long currentTick = level.getGameTime();
        long elapsed = currentTick - buySentTick;
        
        if (elapsed >= BUY_TIMEOUT_TICKS) {
            LOGGER.info("Auto-buy timed out after {} ticks - no server response", elapsed);
            NotifyUtil.notifyPlayer(
                "[AutoReroll] Purchase timed out - stopping",
                iskallia.vault.init.ModSounds.BOOSTER_PACK_FAIL_SFX
            );
            resetPendingBuy();
            stop();
        }
    }
}