package dev.blufony.autoreroll.client;

import dev.blufony.autoreroll.config.AutoRerollConfig;
import dev.blufony.autoreroll.util.ItemMatcher;
import iskallia.vault.network.message.ShardTradeMessage;
import net.minecraft.util.Tuple;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Mod.EventBusSubscriber(modid = "auto_reroll", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class AutoRerollManager {
    private static boolean isRunning = false;
    private static int currentRerollCount = 0;
    private static boolean cancelRequested = false;
    private static List<? extends String> generalTargets;
    private static List<? extends String> boosterPackTargets;
    private static List<? extends String> inscriptionTargets;
    private static ScheduledExecutorService scheduler;
    
    public static synchronized void start() {
        if (isRunning) {
            return;
        }
        
        generalTargets = AutoRerollConfig.GENERAL_TARGETS.get();
        boosterPackTargets = AutoRerollConfig.BOOSTER_PACK_TARGETS.get();
        inscriptionTargets = AutoRerollConfig.INSCRIPTION_TARGETS.get();
        
        if ((generalTargets == null || generalTargets.isEmpty()) &&
            (boosterPackTargets == null || boosterPackTargets.isEmpty()) &&
            (inscriptionTargets == null || inscriptionTargets.isEmpty())) {
            return;
        }
        
        ensureSchedulerShutdown();
        
        isRunning = true;
        currentRerollCount = 0;
        cancelRequested = false;
        
        scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread t = new Thread(runnable, "auto-reroll-scheduler");
            t.setDaemon(true);
            return t;
        });
    }
    
    public static synchronized void stop() {
        if (!isRunning) {
            return;
        }
        
        cancelRequested = true;
        isRunning = false;
        int finalCount = currentRerollCount;
        currentRerollCount = 0;
        
        ensureSchedulerShutdown();
    }
    
    private static synchronized void ensureSchedulerShutdown() {
        if (scheduler != null) {
            List<Runnable> cancelledTasks = scheduler.shutdownNow();
            if (!cancelledTasks.isEmpty()) {
                // Cancelled tasks noted
            }
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
    
    private static void sendResetRequest() {
        if (cancelRequested || !isRunning) {
            return;
        }
        
        currentRerollCount++;
        
        iskallia.vault.init.ModNetwork.CHANNEL.sendToServer(
            iskallia.vault.network.message.ServerboundResetBlackMarketTradesMessage.INSTANCE
        );
    }
    
    public static synchronized void onServerResponse(ShardTradeMessage message) {
        if (cancelRequested || !isRunning) {
            return;
        }
        
        Map<Integer, Tuple<ItemStack, Integer>> availableTrades = message.getAvailableTrades();
        long delay = AutoRerollConfig.PAUSE_BETWEEN_REROLLS_MS.get();
        
        scheduler.schedule(() -> {
            if (cancelRequested || !isRunning) {
                return;
            }
            
            try {
                boolean searchAllSlots = AutoRerollConfig.SEARCH_ALL_SLOTS.get();
                int[] slotsToCheck = searchAllSlots ? new int[]{0, 1, 2} : new int[]{1};
                
                boolean foundMatch = false;
                
                for (int slotIndex : slotsToCheck) {
                    Tuple<ItemStack, Integer> trade = availableTrades.get(slotIndex);
                    
                    if (trade == null) {
                        continue;
                    }
                    
                    ItemStack item = trade.getA();
                    
                    var allTargets = Stream.concat(
                        Stream.concat(generalTargets.stream(), boosterPackTargets.stream()),
                        inscriptionTargets.stream()
                    ).collect(Collectors.toList());
                    
                    boolean isMatch = ItemMatcher.matchesWithNbt(item, allTargets);
                    
                    if (isMatch) {
                        foundMatch = true;
                        break;
                    }
                }
                
                if (foundMatch) {
                    stop();
                    return;
                }
                
                if (currentRerollCount >= AutoRerollConfig.MAX_REROLLS.get()) {
                    int maxRerolls = AutoRerollConfig.MAX_REROLLS.get();
                    stop();
                    return;
                }
                
                scheduleNextCycle();
                
            } catch (Exception e) {
                System.err.println("[AutoReroll] [ERROR] Exception during trade check:");
                e.printStackTrace();
                System.err.println("[AutoReroll] [ERROR] Error message: " + e.getMessage());
                stop();
            }
        }, delay, TimeUnit.MILLISECONDS);
    }
    
    private static void scheduleNextCycle() {
        sendResetRequest();
    }
    
    public static int getCurrentRerollCount() {
        return currentRerollCount;
    }
    
    public static boolean isRunning() {
        return isRunning;
    }
}
