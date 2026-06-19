package dev.blufony.autoreroll.client;

import dev.blufony.autoreroll.config.AutoRerollConfig;
import dev.blufony.autoreroll.util.ItemMatcher;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Tuple;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Mod.EventBusSubscriber(modid = "auto_reroll", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class AutoRerollManager {
    private static final Logger LOGGER = LogManager.getLogger();
    
    private static boolean isRunning = false;
    private static int currentRerollCount = 0;
    private static boolean cancelRequested = false;
    private static List<ResourceLocation> targetItems;
    private static ScheduledExecutorService scheduler;
    
    public static void start() {
        if (isRunning) {
            LOGGER.warn("Auto reroll already running");
            return;
        }
        
        List<? extends String> targetStrings = AutoRerollConfig.TARGETS.get();
        if (targetStrings == null || targetStrings.isEmpty()) {
            LOGGER.error("No target items configured");
            return;
        }
        
        targetItems = targetStrings.stream()
            .map(ResourceLocation::new)
            .collect(Collectors.toList());
        isRunning = true;
        currentRerollCount = 0;
        cancelRequested = false;
        
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduleNextReroll();
        
        LOGGER.info("Auto reroll started, looking for: {}", targetItems);
    }
    
    public static void stop() {
        if (!isRunning) return;
        
        cancelRequested = true;
        isRunning = false;
        currentRerollCount = 0;
        
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        
        LOGGER.info("Auto reroll stopped");
    }
    
    private static void scheduleNextReroll() {
        if (cancelRequested || !isRunning) return;
        
        scheduler.schedule(() -> {
            if (cancelRequested || !isRunning) return;
            
            currentRerollCount++;
            LOGGER.debug("Triggering reroll #{}", currentRerollCount);
            
            iskallia.vault.init.ModNetwork.CHANNEL.sendToServer(
                iskallia.vault.network.message.ServerboundResetBlackMarketTradesMessage.INSTANCE
            );
            
            scheduleTradeCheck();
            
        }, 0, TimeUnit.MILLISECONDS);
    }
    
    private static void scheduleTradeCheck() {
        if (cancelRequested || !isRunning) return;
        
        scheduler.schedule(() -> {
            if (cancelRequested || !isRunning) return;
            
            try {
                Tuple<ItemStack, Integer> centerTrade = iskallia.vault.client.data.ClientShardTradeData.getTradeInfo(1);
                
                if (centerTrade == null) {
                    LOGGER.warn("No center trade available yet, waiting...");
                    scheduleTradeCheck();
                    return;
                }
                
                // Get item from Tuple - using official mapping names
                ItemStack centerItem = centerTrade.getA();
                
                if (ItemMatcher.matches(centerItem, targetItems)) {
                    LOGGER.info("Target item found after {} rerolls!", currentRerollCount);
                    stop();
                    return;
                }
                
                if (currentRerollCount >= AutoRerollConfig.MAX_REROLLS.get()) {
                    LOGGER.info("Max rerolls ({}) reached without finding target", AutoRerollConfig.MAX_REROLLS.get());
                    stop();
                    return;
                }
                
                scheduleNextReroll();
                
            } catch (Exception e) {
                LOGGER.error("Error checking trades", e);
                stop();
            }
        }, AutoRerollConfig.PAUSE_BETWEEN_REROLLS_MS.get(), TimeUnit.MILLISECONDS);
    }
    
    public static int getCurrentRerollCount() {
        return currentRerollCount;
    }
    
    public static boolean isRunning() {
        return isRunning;
    }
}
