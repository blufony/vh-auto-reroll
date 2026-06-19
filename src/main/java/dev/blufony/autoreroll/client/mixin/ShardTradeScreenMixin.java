package dev.blufony.autoreroll.client.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import iskallia.vault.client.gui.framework.element.ButtonElement;
import iskallia.vault.client.gui.framework.element.spi.ElementStore;
import iskallia.vault.client.gui.framework.element.spi.IGuiEventElement;
import iskallia.vault.client.gui.framework.render.Tooltips;
import iskallia.vault.container.inventory.ShardTradeContainer;
import iskallia.vault.client.data.ClientExpertiseData;
import iskallia.vault.client.data.ClientShardTradeData;
import iskallia.vault.skill.base.TieredSkill;
import iskallia.vault.skill.expertise.type.BlackMarketExpertise;
import iskallia.vault.skill.tree.PrestigeTree;
import iskallia.vault.skill.prestige.helper.PrestigeHelper;
import iskallia.vault.skill.prestige.BlackMarketRerollsPrestigePowerPower;
import iskallia.vault.skill.base.Skill;
import dev.blufony.autoreroll.client.AutoRerollManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.TextComponent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Consumer;

/**
 * Mixin to add shift-click auto-reroll functionality to the reset button.
 * When shift is held and the reset button is clicked, it starts auto-reroll instead of performing a normal reset.
 * 
 * Note: The old implementation (BlackMarketButtonHandler.java.bak) added a separate button next to the reset button.
 * This new approach modifies the existing reset button's behavior based on whether shift is held during click.
 */
@Mixin(value = iskallia.vault.client.gui.screen.ShardTradeScreen.class, priority = 1000)
public abstract class ShardTradeScreenMixin {
    private static final Logger LOGGER = LogManager.getLogger();
    
    static {
        LOGGER.info("==================================================");
        LOGGER.info("ShardTradeScreenMixin - Static initializer called");
        LOGGER.info("Auto-reroll mod is loading with shift-click support");
        LOGGER.info("==================================================");
    }
    
    // NOTE: This field is no longer used. Auto-reroll is now triggered via shift-click on the reset button.
    // Kept for reference purposes only. The old implementation (BlackMarketButtonHandler.java.bak) 
    // added a separate button next to the reset button.
    @SuppressWarnings("unused")
    private Object unusedAutoRerollButtonReference;
    
    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(ShardTradeContainer container, Inventory inventory, Component title, CallbackInfo ci) {
        LOGGER.info("--------------------------------------------------");
        LOGGER.info("ShardTradeScreenMixin - onInit inject called");
        LOGGER.info("Screen class: {}", this.getClass().getName());
        LOGGER.info("Container: {}", container != null ? container.getClass().getSimpleName() : "NULL");
        LOGGER.info("--------------------------------------------------");
        
        try {
            // Access elementStore via reflection since it's protected and we're in a mixin
            Class<?> clazz = iskallia.vault.client.gui.screen.ShardTradeScreen.class;
            Field storeField = null;
            while (clazz != null && storeField == null) {
                try {
                    storeField = clazz.getDeclaredField("elementStore");
                } catch (NoSuchFieldException e) {
                    clazz = clazz.getSuperclass();
                }
            }
            
            if (storeField == null) {
                LOGGER.error("[CRITICAL] Could not find elementStore field in class hierarchy!");
                return;
            }
            
            storeField.setAccessible(true);
            ElementStore store = (ElementStore) storeField.get(this);
            LOGGER.debug("Accessed element store instance: {}", store != null ? store.getClass().getName() : "NULL");
            
            if (store == null) {
                LOGGER.error("[CRITICAL] Element store is null! Cannot proceed with button modification.");
                return;
            }
            
            // Get the list of elements
            var guiElements = store.getGuiEventElementList();
            LOGGER.info("Found {} GUI event elements in the element store", guiElements.size());
            LOGGER.info("----------------------------------------");
            LOGGER.info("DEBUG: Listing all elements...");
            
            boolean foundResetButton = false;
            int buttonIndex = 0;
            int resetButtonIndex = -1;
            
            // First pass: Log all elements to identify which is the reset button
            for (var element : guiElements) {
                LOGGER.info("Element #{}: {}", buttonIndex, element != null ? element.getClass().getSimpleName() : "NULL");
                
                if (element instanceof ButtonElement<?> btn) {
                    try {
                        // Get textures
                        var texturesField = ButtonElement.class.getDeclaredField("textures");
                        texturesField.setAccessible(true);
                        var textures = texturesField.get(btn);
                        
                        // Get position
                        var worldSpatialField = btn.getClass().getSuperclass().getDeclaredField("worldSpatial");
                        worldSpatialField.setAccessible(true);
                        var worldSpatial = worldSpatialField.get(btn);
                        
                        String posX = "unknown";
                        String posY = "unknown";
                        if (worldSpatial != null) {
                            try {
                                Method xMethod = worldSpatial.getClass().getMethod("x");
                                Method yMethod = worldSpatial.getClass().getMethod("y");
                                posX = String.valueOf(xMethod.invoke(worldSpatial));
                                posY = String.valueOf(yMethod.invoke(worldSpatial));
                            } catch (Exception e) {
                                // ignore
                            }
                        }
                        
                        String texturesStr = textures != null ? textures.toString() : "NULL";
                        boolean isResetButton = texturesStr.contains("RESET_TRADES") || textures == iskallia.vault.client.gui.framework.ScreenTextures.BUTTON_RESET_TRADES_TEXTURES;
                        
                        LOGGER.info("  → Button at ({}, {}) with textures: {} [IS RESET? {}]", posX, posY, texturesStr, isResetButton);
                        
                        if (isResetButton) {
                            resetButtonIndex = buttonIndex;
                        }
                    } catch (Exception e) {
                        LOGGER.warn("  → Could not inspect button details", e);
                    }
                }
                buttonIndex++;
            }
            
            LOGGER.info("----------------------------------------");
            LOGGER.info("DEBUG: Reset button found at index: {}", resetButtonIndex);
            LOGGER.info("----------------------------------------");
            
            // Second pass: Actually modify the reset button
            buttonIndex = 0;
            for (var element : guiElements) {
                LOGGER.debug("Checking element #{}: {}", buttonIndex, element != null ? element.getClass().getName() : "NULL");
                
                // Skip non-ButtonElements
                if (!(element instanceof ButtonElement<?> button)) {
                    buttonIndex++;
                    continue;
                }
                
                // Check if this is the reset button by examining its textures
                boolean isResetButton = false;
                try {
                    var texturesField = ButtonElement.class.getDeclaredField("textures");
                    texturesField.setAccessible(true);
                    var textures = texturesField.get(button);
                    
                    String texturesStr = textures != null ? textures.toString() : "NULL";
                    isResetButton = texturesStr.contains("RESET_TRADES") || textures == iskallia.vault.client.gui.framework.ScreenTextures.BUTTON_RESET_TRADES_TEXTURES;
                    
                    LOGGER.debug("Button textures: {} [IS RESET? {}]", texturesStr, isResetButton);
                    
                } catch (Exception e) {
                    LOGGER.debug("Could not check button textures", e);
                }
                
                // Only modify if this is the reset button
                if (!isResetButton) {
                    LOGGER.debug("Skipping non-reset button at index {}", buttonIndex);
                    buttonIndex++;
                    continue;
                }
                
                LOGGER.info("----------------------------------------");
                LOGGER.info("✓ Found RESET BUTTON at index {}!", buttonIndex);
                LOGGER.info("Button class: {}", button.getClass().getName());
                LOGGER.info("----------------------------------------");
                
                // Modify this button's onClick to check for shift key
                LOGGER.info("Attempting to modify reset button click handler for shift-click support...");
                boolean modified = modifyButtonClick(button);
                
                if (modified) {
                    foundResetButton = true;
                    LOGGER.info("✓ Successfully wrapped reset button click handler");
                    LOGGER.info("Shift-click will start auto-reroll");
                    LOGGER.info("Normal-click will perform standard reset");
                    LOGGER.info("----------------------------------------");
                    break; // We've found and modified our target button
                } else {
                    LOGGER.error("Failed to modify reset button click handler!");
                    LOGGER.info("----------------------------------------");
                }
                
                buttonIndex++;
            }
            
            if (!foundResetButton) {
                LOGGER.error("[WARNING] Could not find or modify the reset button!");
                LOGGER.error("Auto-reroll shift-click feature may not work.");
                LOGGER.error("Please check that the mod is loaded correctly.");
            } else {
                LOGGER.info("--------------------------------------------------");
                LOGGER.info("[SUCCESS] Reset button modification complete!");
                LOGGER.info("Auto-reroll mod is ready.");
                LOGGER.info("Instructions: Hold SHIFT and click the reset button to start auto-reroll.");
                LOGGER.info("--------------------------------------------------");
            }
            
        } catch (Exception e) {
            LOGGER.error("[ERROR] Unexpected error during button modification", e);
        }
    }
    
    /**
     * Modifies a button's click handler to support shift-click for auto-reroll.
     * Shift-click starts auto-reroll, normal click performs the original action.
     * 
     * @param button The button element to modify
     * @return true if modification was successful, false otherwise
     */
    private boolean modifyButtonClick(ButtonElement<?> button) {
        LOGGER.debug("Entering modifyButtonClick()");
        
        try {
            // Get the onClick field using reflection
            LOGGER.debug("Looking for 'onClick' field in ButtonElement class...");
            Field onClickField = ButtonElement.class.getDeclaredField("onClick");
            onClickField.setAccessible(true);
            LOGGER.debug("Found onClick field: {}", onClickField.getType().getName());
            
            @SuppressWarnings("unchecked")
            Consumer<ButtonElement<?>> originalOnClick = (Consumer<ButtonElement<?>>) onClickField.get(button);
            
            if (originalOnClick == null) {
                LOGGER.warn("Original onClick handler is null, cannot wrap it");
                return false;
            }
            
            LOGGER.info("Successfully retrieved original onClick handler: {}", originalOnClick.toString());
            LOGGER.info("ORIGINAL onClick hashCode: {}", System.identityHashCode(originalOnClick));
            
            // Create a new consumer that checks for shift key
            LOGGER.info("Creating wrapped onClick handler with shift-key detection...");
            Consumer<ButtonElement<?>> wrappedOnClick = btn -> {
                LOGGER.info("### VERIFICATION LOG ### Wrapped onClick IS BEING INVOKED! ###");
                LOGGER.info(">>> BUTTON CLICK DETECTED <<<");
                LOGGER.info("Button instance: {}", btn != null ? btn.getClass().getSimpleName() : "NULL");
                
                Minecraft mc = Minecraft.getInstance();
                LOGGER.debug("Minecraft instance: {}", mc != null ? "present" : "NULL");
                
                if (mc.screen != null) {
                    LOGGER.debug("Current screen: {}", mc.screen.getClass().getSimpleName());
                    LOGGER.debug("Shift key state: {}", mc.screen.hasShiftDown() ? "PRESSED" : "NOT PRESSED");
                } else {
                    LOGGER.warn("Screen is null, cannot determine shift key state");
                }
                
                if (mc.screen != null && mc.screen.hasShiftDown()) {
                    // Shift-click: Execute original reset first (to play sound), then start auto-reroll
                    LOGGER.info("==========================================");
                    LOGGER.info("SHIFT-CLICK DETECTED!");
                    LOGGER.info("==========================================");
                    LOGGER.info("Action: Performing reset + starting auto-reroll");
                    LOGGER.info("Previous state: {}", AutoRerollManager.isRunning() ? "RUNNING" : "STOPPED");
                    
                    try {
                        // First execute the original reset to trigger the sound
                        originalOnClick.accept(btn);
                        LOGGER.info("Original reset completed (sound should have played)");
                        
                        // Then start auto-reroll
                        AutoRerollManager.start();
                        LOGGER.info("Auto-reroll started successfully!");
                        LOGGER.info("Next state: RUNNING");
                        LOGGER.info("==========================================");
                    } catch (Exception e) {
                        LOGGER.error("Failed to execute shift-click action", e);
                    }
                } else {
                    // Normal click: Execute original functionality
                    LOGGER.info("==========================================");
                    LOGGER.info("NORMAL CLICK DETECTED (no shift key)");
                    LOGGER.info("==========================================");
                    LOGGER.info("Action: Performing standard reset");
                    LOGGER.info("Executing original onClick handler...");
                    
                    try {
                        originalOnClick.accept(btn);
                        LOGGER.info("Original reset completed successfully");
                    } catch (Exception e) {
                        LOGGER.error("Error executing original onClick handler", e);
                    }
                    
                    LOGGER.info("==========================================");
                }
            };
            
            LOGGER.info("Wrapped onClick hashCode: {}", System.identityHashCode(wrappedOnClick));
            
            // Replace the onClick handler
            LOGGER.debug("Setting new wrapped onClick handler...");
            onClickField.set(button, wrappedOnClick);
            LOGGER.info("Button onClick handler successfully replaced!");
            
            // Verify the change by reading back
            Consumer<ButtonElement<?>> readBackOnClick = (Consumer<ButtonElement<?>>) onClickField.get(button);
            LOGGER.info("VERIFICATION - Read back onClick: {}", readBackOnClick != null ? readBackOnClick.toString() : "NULL");
            LOGGER.info("VERIFICATION - Read back hashCode: {}", System.identityHashCode(readBackOnClick));
            LOGGER.info("VERIFICATION - Is same object? {}", readBackOnClick == wrappedOnClick);
            LOGGER.info("VERIFICATION - Same as original? {}", readBackOnClick == originalOnClick);
            
            // Set up shift-based tooltip
            LOGGER.info("Setting up shift-based tooltip for reset button...");
            
            button.tooltip(Tooltips.shift(
                // Normal tooltip (without shift key)
                Tooltips.multi(() -> {
                    // If auto-reroll is active, show that status
                    if (AutoRerollManager.isRunning()) {
                        return List.of(new TextComponent("Auto-Reroll Active"));
                    }
                    
                    // Otherwise, preserve original Vault mod tooltip logic
                    PrestigeTree prestige = PrestigeHelper.getPrestige(Minecraft.getInstance().player);
                    boolean hasInfiniteRerolls = !prestige.getAll(BlackMarketRerollsPrestigePowerPower.class, Skill::isUnlocked).isEmpty();
                    if (hasInfiniteRerolls) {
                        return List.of(new TextComponent("Infinite Rerolls"));
                    }

                    int totalRolls = 0;
                    boolean hasExpertise = false;

                    for (TieredSkill learnedTalentNode : ClientExpertiseData.getLearnedTalentNodes()) {
                        if (learnedTalentNode.getChild() instanceof BlackMarketExpertise blackMarketExpertise) {
                            totalRolls += blackMarketExpertise.getNumberOfRolls();
                            hasExpertise = true;
                        }
                    }

                    if (hasExpertise) {
                        int numOfRollsLeft = totalRolls - ClientShardTradeData.getRerollsUsed();
                        return List.of(new TextComponent("Rolls Left: " + numOfRollsLeft));
                    } else {
                        return List.of(new TextComponent("Unlock Marketer Expertise to Re-roll"));
                    }
                }),
                
                // Shift tooltip (when shift is held) - shows "Auto-Reroll Active" when running
                Tooltips.multi(() -> {
                    if (AutoRerollManager.isRunning()) {
                        return List.of(new TextComponent("Auto-Reroll Active"));
                    }
                    return List.of(new TextComponent("Shift-click to start auto-reroll"));
                })
            ));
            
            LOGGER.info("✓ Shift-based tooltip configured successfully");
            
            return true;
            
        } catch (NoSuchFieldException e) {
            LOGGER.error("Could not find onClick field in ButtonElement", e);
            return false;
        } catch (IllegalAccessException e) {
            LOGGER.error("Could not access onClick field in ButtonElement", e);
            return false;
        } catch (Exception e) {
            LOGGER.error("Unexpected error while modifying button onClick handler", e);
            return false;
        }
    }
}
