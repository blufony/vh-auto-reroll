package dev.blufony.autoreroll.client;

import dev.blufony.autoreroll.config.AutoRerollConfig;
import dev.blufony.autoreroll.util.ItemMatcher;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Tuple;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Mod.EventBusSubscriber(modid = "auto_reroll", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class AutoRerollManager {
    private static boolean isRunning = false;
    private static int currentRerollCount = 0;
    private static boolean cancelRequested = false;
    private static List<ResourceLocation> targetItems;
    private static ScheduledExecutorService scheduler;
    
    public static synchronized void start() {
        if (isRunning) {
            return;
        }
        
        List<? extends String> targetStrings = AutoRerollConfig.TARGETS.get();
        if (targetStrings == null || targetStrings.isEmpty()) {
            return;
        }
        
        ensureSchedulerShutdown();
        
        targetItems = targetStrings.stream()
            .map(ResourceLocation::new)
            .collect(Collectors.toList());
        isRunning = true;
        currentRerollCount = 0;
        cancelRequested = false;
        
        scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread t = new Thread(runnable, "auto-reroll-scheduler");
            t.setDaemon(true);
            return t;
        });
        
        scheduleNextReroll();
    }
    
    public static synchronized void stop() {
        if (!isRunning) return;
        
        cancelRequested = true;
        isRunning = false;
        currentRerollCount = 0;
        
        ensureSchedulerShutdown();
    }
    
    private static synchronized void ensureSchedulerShutdown() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            scheduler = null;
        }
    }
    
    private static void scheduleNextReroll() {
        if (cancelRequested || !isRunning) return;
        
        scheduler.schedule(() -> {
            if (cancelRequested || !isRunning) return;
            
            currentRerollCount++;
            
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
                    scheduleTradeCheck();
                    return;
                }
                
                ItemStack centerItem = centerTrade.getA();
                
                if (ItemMatcher.matches(centerItem, targetItems)) {
                    stop();
                    return;
                }
                
                if (currentRerollCount >= AutoRerollConfig.MAX_REROLLS.get()) {
                    stop();
                    return;
                }
                
                scheduleNextReroll();
                
            } catch (Exception e) {
                System.err.println("[AutoReroll] Error checking trades: " + e.getMessage());
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
