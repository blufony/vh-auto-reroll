package dev.blufony.autoreroll.client.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import iskallia.vault.client.gui.framework.ScreenTextures;
import iskallia.vault.client.gui.framework.element.ButtonElement;
import iskallia.vault.client.gui.framework.element.spi.ElementStore;
import iskallia.vault.client.gui.framework.render.Tooltips;
import iskallia.vault.client.gui.framework.spatial.Spatials;
import iskallia.vault.client.atlas.TextureAtlasRegion;
import iskallia.vault.init.ModTextureAtlases;
import iskallia.vault.VaultMod;
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
import dev.blufony.autoreroll.client.gui.IconButtonElement;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.sounds.SoundSource;
import iskallia.vault.init.ModNetwork;
import iskallia.vault.init.ModSounds;
import iskallia.vault.network.message.ServerboundResetBlackMarketTradesMessage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Consumer;

@Mixin(value = iskallia.vault.client.gui.screen.ShardTradeScreen.class, priority = 1000)
public abstract class ShardTradeScreenMixin {
    private static Field ELEMENT_STORE_FIELD;
    private static Field ON_CLICK_FIELD;
    private static Field TEXTURES_FIELD;
    private static Method WORLD_SPATIAL_X_METHOD;
    private static Method WORLD_SPATIAL_Y_METHOD;
    private static Object RESET_TRADES_TEXTURES;
    private static ButtonElement.ButtonTextures AUTO_REROLL_BUTTON_TEXTURES;
    
    // Define the auto-reroll button textures using Vault's blue button
    static {
        try {
        // Create blue button texture regions (with .9 suffix for nine-slice)
        TextureAtlasRegion BLUE_BUTTON_NORMAL = TextureAtlasRegion.of(
            ModTextureAtlases.SCREEN, 
            VaultMod.id("gui/screen/button/blue/normal.9")
        );
        TextureAtlasRegion BLUE_BUTTON_HOVER = TextureAtlasRegion.of(
            ModTextureAtlases.SCREEN, 
            VaultMod.id("gui/screen/button/blue/hover.9")
        );
        TextureAtlasRegion BLUE_BUTTON_PRESSED = TextureAtlasRegion.of(
            ModTextureAtlases.SCREEN, 
            VaultMod.id("gui/screen/button/blue/pressed.9")
        );
        TextureAtlasRegion BLUE_BUTTON_DISABLED = TextureAtlasRegion.of(
            ModTextureAtlases.SCREEN, 
            VaultMod.id("gui/screen/button/blue/disabled.9")
        );
        
        AUTO_REROLL_BUTTON_TEXTURES = new ButtonElement.ButtonTextures(
            BLUE_BUTTON_NORMAL,
            BLUE_BUTTON_HOVER,

            BLUE_BUTTON_PRESSED,
            BLUE_BUTTON_DISABLED
        );


        } catch (Exception e) {
            System.err.println("[AutoReroll] Failed to create button textures: " + e.getMessage());
        }
    }
    
    // Cycle icon overlay
    private static final TextureAtlasRegion CYCLE_ICON = TextureAtlasRegion.of(
        ModTextureAtlases.SCREEN, 
        VaultMod.id("gui/screen/button/cycle")
    );
    
    static {
        try {
            Minecraft mc = Minecraft.getInstance();
            Class<?> clazz = iskallia.vault.client.gui.screen.ShardTradeScreen.class;
            
            while (clazz != null && ELEMENT_STORE_FIELD == null) {
                try {
                    ELEMENT_STORE_FIELD = clazz.getDeclaredField("elementStore");
                } catch (NoSuchFieldException e) {
                    clazz = clazz.getSuperclass();
                }
            }
            
            if (ELEMENT_STORE_FIELD != null) {
                ELEMENT_STORE_FIELD.setAccessible(true);
            }
            
            TEXTURES_FIELD = ButtonElement.class.getDeclaredField("textures");
            TEXTURES_FIELD.setAccessible(true);
            
            Class<?> worldSpatialClass = ButtonElement.class.getSuperclass();
            Field worldSpatialField = worldSpatialClass.getDeclaredField("worldSpatial");
            worldSpatialField.setAccessible(true);
            
            WORLD_SPATIAL_X_METHOD = worldSpatialClass.getMethod("x");
            WORLD_SPATIAL_Y_METHOD = worldSpatialClass.getMethod("y");
            
            ON_CLICK_FIELD = ButtonElement.class.getDeclaredField("onClick");
            ON_CLICK_FIELD.setAccessible(true);
            
            // Get the reset button textures constant reference
            Class<?> screenTexturesClass = Class.forName("iskallia.vault.client.gui.framework.ScreenTextures");
            RESET_TRADES_TEXTURES = screenTexturesClass.getField("BUTTON_RESET_TRADES_TEXTURES").get(null);
            
        } catch (Exception e) {
            System.err.println("[AutoReroll] Failed to initialize cached reflection fields: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(ShardTradeContainer container, Inventory inventory, Component title, CallbackInfo ci) {
        if (ELEMENT_STORE_FIELD == null) {
            System.err.println("[AutoReroll] elementStore field not found - auto-reroll disabled");
            return;
        }
        
        try {
            ElementStore store = (ElementStore) ELEMENT_STORE_FIELD.get(this);
            
            if (store == null) {
                System.err.println("[AutoReroll] ElementStore is null");
                return;
            }
            
            var guiElements = store.getGuiEventElementList();
            int resetButtonIndex = -1;
            
            for (int i = 0; i < guiElements.size(); i++) {
                var element = guiElements.get(i);
                
                if (!(element instanceof ButtonElement<?>)) {
                    continue;
                }
                
                ButtonElement<?> btn = (ButtonElement<?>) element;
                boolean isResetButton = false;
                
                try {
                    Object textures = TEXTURES_FIELD.get(btn);
                    if (RESET_TRADES_TEXTURES != null && textures == RESET_TRADES_TEXTURES) {
                        isResetButton = true;
                    }
                } catch (Exception e) {
                    // Skip if we can't check textures
                }
                
                if (isResetButton) {
                    resetButtonIndex = i;
                    break;
                }
            }
            
            if (resetButtonIndex == -1) {
                System.err.println("[AutoReroll] Reset button not found");
                return;
            }
            
            ButtonElement<?> resetButton = (ButtonElement<?>) guiElements.get(resetButtonIndex);
            modifyButtonClick(resetButton);
            addAutoRerollButton(store, resetButton);
            
        } catch (Exception e) {
            System.err.println("[AutoReroll] Error during button modification: " + e.getMessage());
        }
    }
    
    private boolean modifyButtonClick(ButtonElement<?> button) {
        try {
            Consumer<ButtonElement<?>> originalOnClick = (Consumer<ButtonElement<?>>) ON_CLICK_FIELD.get(button);
            
            if (originalOnClick == null) {
                return false;
            }
            
            Consumer<ButtonElement<?>> wrappedOnClick = btn -> {
                Minecraft mc = Minecraft.getInstance();
                
                if (mc.screen != null && mc.screen.hasShiftDown()) {
                    try {
                        if (!AutoRerollManager.isRunning()) {
                            originalOnClick.accept(btn);
                            AutoRerollManager.start(() -> {});
                        }
                    } catch (Exception e) {
                        System.err.println("[AutoReroll] Error in shift-click handler: " + e.getMessage());
                    }
                } else {
                    try {
                        originalOnClick.accept(btn);
                    } catch (Exception e) {
                        System.err.println("[AutoReroll] Error in normal click handler: " + e.getMessage());
                    }
                }
            };
            
            ON_CLICK_FIELD.set(button, wrappedOnClick);
            
            button.tooltip(Tooltips.shift(
                Tooltips.multi(() -> {
                    if (AutoRerollManager.isRunning()) {
                        return List.of(new TextComponent("Auto-Reroll Active"));
                    }
                    
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
                
                Tooltips.multi(() -> {
                    if (AutoRerollManager.isRunning()) {
                        return List.of(new TextComponent("Auto-Reroll Active"));
                    }
                    return List.of(new TextComponent("Shift-click to start auto-reroll"));
                })
            ));
            
            return true;
            
        } catch (IllegalAccessException e) {
            System.err.println("[AutoReroll] Cannot access onClick field: " + e.getMessage());
            return false;
        } catch (Exception e) {
            System.err.println("[AutoReroll] Error modifying button: " + e.getMessage());
            return false;
        }
    }
    
    private void addAutoRerollButton(ElementStore store, ButtonElement<?> resetButton) {
        try {
            if (AUTO_REROLL_BUTTON_TEXTURES == null) {
                System.err.println("[AutoReroll] Auto-reroll button textures not initialized");
                return;
            }
            
            int resetX = (int) WORLD_SPATIAL_X_METHOD.invoke(resetButton);
            int resetY = (int) WORLD_SPATIAL_Y_METHOD.invoke(resetButton);

            int autoRerollX = resetX + 21;
            int autoRerollY = resetY + 2;
            
            IconButtonElement autoRerollButton = new IconButtonElement(
                Spatials.positionXY(autoRerollX, autoRerollY),
                AUTO_REROLL_BUTTON_TEXTURES,
                () -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.screen != null && mc.screen.hasShiftDown()) {
                        System.out.println("[AutoReroll] Shift-click action configured");
                    } else {
                        // Send reset packet
                        ModNetwork.CHANNEL.sendToServer(ServerboundResetBlackMarketTradesMessage.INSTANCE);
                        
                        // Play sound
                        if (mc.level != null && mc.player != null) {
                            mc.level.playSound(
                                mc.player,
                                mc.player.getX(),
                                mc.player.getY(),
                                mc.player.getZ(),
                                ModSounds.SKILL_TREE_LEARN_SFX,
                                SoundSource.BLOCKS,
                                0.75F,
                                1.0F
                            );
                        }
                        
                        // Start auto-reroll (no resume handler needed for button click)
                        AutoRerollManager.start(() -> {});
                    }
                }
            );
            
            // Add cycle icon overlay - use highlight when auto-rerolling
            autoRerollButton.icon(() -> CYCLE_ICON);
            autoRerollButton.activeIcon(() -> TextureAtlasRegion.of(
                ModTextureAtlases.SCREEN, 
                VaultMod.id("gui/screen/button/cycle_highlight")
            ));
            
            autoRerollButton.tooltip(() -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.screen != null && mc.screen.hasShiftDown()) {
                    return new TextComponent("Configure Targets"); // TODO: Implement target configuration
                }
                return new TextComponent("Start Auto-Reroll");
            });
            
            ((IconButtonElement) store.addElement(autoRerollButton))
                .layout((screen, gui, parent, world) -> world.translateXY(gui));
            
        } catch (Exception e) {
            System.err.println("[AutoReroll] Error adding auto-reroll button: " + e.getMessage());
        }
    }
}
