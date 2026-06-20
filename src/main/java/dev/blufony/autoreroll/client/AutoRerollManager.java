package dev.blufony.autoreroll.client;

import dev.blufony.autoreroll.config.AutoRerollConfig;
import dev.blufony.autoreroll.util.FilterStorage;
import dev.blufony.autoreroll.util.ItemMatcher;
import dev.blufony.autoreroll.util.NotifyUtil;
import iskallia.vault.network.message.ShardTradeMessage;
import net.minecraft.client.Minecraft;

import net.minecraft.util.Tuple;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
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
    
    public static synchronized void onServerResponse(ShardTradeMessage message) {
        if (!isRunning || filterItemStack.isEmpty()) {
            return;
        }
        
        Map<Integer, Tuple<ItemStack, Integer>> availableTrades = message.getAvailableTrades();
        Level level = Minecraft.getInstance().level;
        
        boolean searchAllSlots = AutoRerollConfig.SEARCH_ALL_SLOTS.get();
        int[] slotsToCheck = searchAllSlots ? new int[]{0, 1, 2} : new int[]{1};
        
        boolean foundMatch = false;
        
        for (int slotIndex : slotsToCheck) {
            Tuple<ItemStack, Integer> trade = availableTrades.get(slotIndex);
            
            if (trade == null) {
                continue;
            }
            
            ItemStack item = trade.getA();
            
            if (filterIsSimpleMode) {
                if (ItemMatcher.matchesByItemId(item, filterItemStack.get())) {
                    foundMatch = true;
                    break;
                }
            } else if (ItemMatcher.matchesWithFilter(item, filterItemStack.get(), level)) {
                foundMatch = true;
                break;
            }
        }
        
        if (foundMatch) {
            LOGGER.info("Found matching trade after {} rerolls", currentRerollCount);
            NotifyUtil.notifyPlayer(
                "[AutoReroll] Auto-Reroll Succeeded!",
                iskallia.vault.init.ModSounds.VAULT_CHEST_OMEGA_OPEN
            );
            stop();
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
}