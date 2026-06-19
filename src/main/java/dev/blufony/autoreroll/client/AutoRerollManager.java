package dev.blufony.autoreroll.client;

import dev.blufony.autoreroll.config.AutoRerollConfig;
import dev.blufony.autoreroll.util.FilterStorage;
import dev.blufony.autoreroll.util.ItemMatcher;
import iskallia.vault.network.message.ShardTradeMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Tuple;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Mod.EventBusSubscriber(modid = "vh_auto_reroll", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class AutoRerollManager {
    private static final org.apache.logging.log4j.Logger LOGGER = org.apache.logging.log4j.LogManager.getLogger();
    
    private static boolean isRunning = false;
    private static int currentRerollCount = 0;
    private static boolean cancelRequested = false;
    private static Optional<ItemStack> filterItemStack = Optional.empty();
    private static boolean filterIsSimpleMode = false;
    private static Runnable resumeHandler;
    private static long pauseBetweenRerollsMs;
    private static ScheduledExecutorService scheduler;

    public static synchronized void start(Runnable onResume) {
        if (isRunning) {
            return;
        }
        
        filterItemStack = FilterStorage.loadFilterItem();
        if (filterItemStack.isEmpty()) {
            LOGGER.info("No valid filter item configured, auto-reroll disabled");
            return;
        }
        
        ensureSchedulerShutdown();
        
        resumeHandler = onResume;
        pauseBetweenRerollsMs = AutoRerollConfig.PAUSE_BETWEEN_REROLLS_MS.get();
        isRunning = true;
        currentRerollCount = 0;
        cancelRequested = false;
        filterIsSimpleMode = FilterStorage.isFilterSimpleMode();
        
        scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread t = new Thread(runnable, "auto-reroll-scheduler");
            t.setDaemon(true);
            return t;
        });
        
        LOGGER.info("Auto-reroll started with {} filter: {}", filterIsSimpleMode ? "simple" : "Create filter", filterItemStack.get().getItem());
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
        
        if (resumeHandler != null) {
            resumeHandler.run();
            resumeHandler = null;
        }
        
        LOGGER.info("Auto-reroll stopped after {} rerolls", finalCount);
    }
    
    private static synchronized void ensureSchedulerShutdown() {
        if (scheduler != null) {
            List<Runnable> cancelledTasks = scheduler.shutdownNow();
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
        
        if (currentRerollCount > 1 && pauseBetweenRerollsMs > 0) {
            scheduler.schedule(() -> sendResetRequest(), pauseBetweenRerollsMs, TimeUnit.MILLISECONDS);
            return;
        }
        
        iskallia.vault.init.ModNetwork.CHANNEL.sendToServer(
            iskallia.vault.network.message.ServerboundResetBlackMarketTradesMessage.INSTANCE
        );
    }
    
    public static synchronized void onServerResponse(ShardTradeMessage message) {
        if (cancelRequested || !isRunning || filterItemStack.isEmpty()) {
            return;
        }
        
        Map<Integer, Tuple<ItemStack, Integer>> availableTrades = message.getAvailableTrades();
        Level level = Minecraft.getInstance().level;
        
        scheduler.schedule(() -> {
            if (cancelRequested || !isRunning || filterItemStack.isEmpty()) {
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
                    
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null) {
                        mc.execute(() -> {
                            mc.player.displayClientMessage(
                                new TextComponent("[AutoReroll] Auto-Reroll Succeeded!"),
                                true
                            );
                            
                            if (mc.level != null) {
                                mc.level.playSound(
                                    mc.player,
                                    mc.player.getX(),
                                    mc.player.getY(),
                                    mc.player.getZ(),
                                    iskallia.vault.init.ModSounds.VAULT_CHEST_OMEGA_OPEN,
                                    SoundSource.BLOCKS,
                                    0.75F,
                                    1.0F
                                );
                            }
                        });
                    }
                    
                    stop();
                    return;
                }
                
                if (currentRerollCount >= AutoRerollConfig.MAX_REROLLS.get()) {
                    LOGGER.info("Max rerolls ({}) reached without finding match", AutoRerollConfig.MAX_REROLLS.get());
                    
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null) {
                        mc.execute(() -> {
                            mc.player.displayClientMessage(
                                new TextComponent("[AutoReroll] Failed to find target after " + AutoRerollConfig.MAX_REROLLS.get() + " rerolls"),
                                true
                            );
                            
                            if (mc.level != null) {
                                mc.level.playSound(
                                    mc.player,
                                    mc.player.getX(),
                                    mc.player.getY(),
                                    mc.player.getZ(),
                                    iskallia.vault.init.ModSounds.BOOSTER_PACK_FAIL_SFX,
                                    SoundSource.BLOCKS,
                                    0.75F,
                                    1.0F
                                );
                            }
                        });
                    }
                    
                    stop();
                    return;
                }
                
                scheduleNextCycle();
                
            } catch (Exception e) {
                LOGGER.error("Exception during trade check:", e);
                stop();
            }
        }, pauseBetweenRerollsMs, TimeUnit.MILLISECONDS);
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
