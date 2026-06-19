package dev.blufony.autoreroll.client.mixin;

import dev.blufony.autoreroll.AutoRerollMod;
import dev.blufony.autoreroll.client.AutoRerollManager;
import dev.blufony.autoreroll.config.AutoRerollConfig;
import dev.blufony.autoreroll.client.gui.IconButtonElement;
import dev.blufony.autoreroll.util.FilterStorage;
import dev.blufony.autoreroll.util.ItemMatcher;
import com.simibubi.create.foundation.config.ui.ConfigScreen;
import com.simibubi.create.foundation.config.ui.SubMenuConfigScreen;
import iskallia.vault.VaultMod;
import iskallia.vault.client.atlas.TextureAtlasRegion;
import iskallia.vault.client.data.ClientExpertiseData;
import iskallia.vault.client.data.ClientShardTradeData;
import iskallia.vault.client.gui.framework.element.ButtonElement;
import iskallia.vault.client.gui.framework.element.FakeItemSlotElement;
import iskallia.vault.client.gui.framework.element.spi.ElementStore;
import iskallia.vault.client.gui.framework.render.Tooltips;
import iskallia.vault.client.gui.framework.render.TooltipDirection;
import iskallia.vault.client.gui.framework.spatial.Spatials;
import iskallia.vault.container.inventory.ShardTradeContainer;
import iskallia.vault.init.ModNetwork;
import iskallia.vault.init.ModSounds;
import iskallia.vault.init.ModTextureAtlases;
import iskallia.vault.network.message.ServerboundResetBlackMarketTradesMessage;
import iskallia.vault.skill.prestige.BlackMarketRerollsPrestigePowerPower;
import iskallia.vault.skill.base.Skill;
import iskallia.vault.skill.base.TieredSkill;
import iskallia.vault.skill.expertise.type.BlackMarketExpertise;
import iskallia.vault.skill.prestige.helper.PrestigeHelper;
import iskallia.vault.skill.tree.PrestigeTree;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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
            addFilterSlot(store);
            
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
                if (AutoRerollManager.isRunning()) {
                    return;
                }
                
                try {
                    originalOnClick.accept(btn);
                } catch (Exception e) {
                    System.err.println("[AutoReroll] Error in click handler: " + e.getMessage());
                }
            };
            
            ON_CLICK_FIELD.set(button, wrappedOnClick);
            

            
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
                    
                    if (mc.player == null || mc.screen == null) {
                        return;
                    }
                    
                    if (!hasInfiniteRerollPrestige()) {
                        return;
                    }
                    
                    if (!hasFilterItem()) {
                        return;
                    }
                    
                    if (AutoRerollManager.isRunning()) {
                        AutoRerollManager.stop();
                        return;
                    }
                    
                    ItemStack carriedItem = mc.player.containerMenu.getCarried();
                    
                    if (mc.screen.hasShiftDown()) {
                        SubMenuConfigScreen configScreen = new SubMenuConfigScreen(
                            (Screen)(Object)this,
                            "Auto Reroll Configuration",
                            ModConfig.Type.CLIENT,
                            AutoRerollConfig.CLIENT_SPEC,
                            AutoRerollConfig.CLIENT_SPEC.getValues()
                        );
                        ConfigScreen.modID = AutoRerollMod.MOD_ID;
                        ForgeHooksClient.pushGuiLayer(Minecraft.getInstance(), configScreen);
                        return;
                    }
                    
                    ModNetwork.CHANNEL.sendToServer(ServerboundResetBlackMarketTradesMessage.INSTANCE);
                        
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
                        
                        AutoRerollManager.start(() -> {});
                }
            );
            
            autoRerollButton.setDisabled(() -> !hasInfiniteRerollPrestige() || !hasFilterItem());
            
            autoRerollButton.icon(() -> CYCLE_ICON);
            autoRerollButton.activeIcon(() -> TextureAtlasRegion.of(
                ModTextureAtlases.SCREEN, 
                VaultMod.id("gui/screen/button/cycle_highlight")
            ));
            
            autoRerollButton.tooltip(() -> {
                Minecraft mc = Minecraft.getInstance();
                
                if (!hasInfiniteRerollPrestige()) {
                    return new TextComponent("Unlock Whispers of the Market Prestige to Auto-Reroll");
                }
                
                if (!hasFilterItem()) {
                    return new TextComponent("Set filter to Auto-Reroll");
                }
                
                if (mc.screen != null && mc.screen.hasShiftDown()) {
                    return new TextComponent("Open Config");
                }
                
                if (AutoRerollManager.isRunning()) {
                    return new TextComponent("Stop Auto-Reroll");
                }
                return new TextComponent("Start Auto-Reroll");
            });
            
            ((IconButtonElement) store.addElement(autoRerollButton))
                .layout((screen, gui, parent, world) -> world.translateXY(gui));
            
        } catch (Exception e) {
            System.err.println("[AutoReroll] Error adding auto-reroll button: " + e.getMessage());
        }
    }
    
    private void addFilterSlot(ElementStore store) {
        try {
            int posX = 29;
            int posY = 21;
            
            FakeItemSlotElement<?> filterSlot = new FakeItemSlotElement<>(
                Spatials.positionXY(posX, posY),
                () -> {
                    ItemStack filterItem = FilterStorage.getFilterItem();
                    return filterItem != null && !filterItem.isEmpty() ? filterItem : ItemStack.EMPTY;
                },
                () -> !hasInfiniteRerollPrestige()
            )
            .layout((screen, gui, parent, world) -> world.translateXY(gui))
            .whenClicked(isDisabled -> {
                Minecraft mc = Minecraft.getInstance();
                
                if (mc.player == null || isDisabled) {
                    return;
                }
                
                ItemStack carriedItem = mc.player.containerMenu.getCarried();
                
                if (!carriedItem.isEmpty() && ItemMatcher.isVaultFilterItem(carriedItem)) {
                    FilterStorage.saveFilterItem(carriedItem);
                    mc.player.displayClientMessage(
                        new TextComponent("[AutoReroll] Filter slot updated!"),
                        true
                    );
                } else {
                    FilterStorage.clearFilterItem();
                    mc.player.displayClientMessage(
                        new TextComponent("[AutoReroll] Filter cleared!"),
                        true
                    );
                }
            })
            .tooltip((tooltipRenderer, poseStack, mouseX, mouseY, tooltipFlag) -> {
                if (!hasInfiniteRerollPrestige()) {
                    tooltipRenderer.renderTooltip(poseStack, 
                        new TextComponent("Unlock Whispers of the Market Prestige to Auto-Reroll"), 
                        mouseX, mouseY, TooltipDirection.RIGHT);
                    return true;
                }
                
                ItemStack filterItem = FilterStorage.getFilterItem();
                
                if (filterItem != null && !filterItem.isEmpty()) {
                    tooltipRenderer.renderTooltip(poseStack, filterItem, mouseX, mouseY, TooltipDirection.RIGHT);
                } else {
                    tooltipRenderer.renderTooltip(poseStack, new TextComponent("Click with Create Filter to set Auto-Reroll Target"), mouseX, mouseY, TooltipDirection.RIGHT);
                }
                
                return true;
            });
            
            store.addElement(filterSlot);
            
        } catch (Exception e) {
            System.err.println("[AutoReroll] Error adding filter slot: " + e.getMessage());
        }
    }
    
    private boolean hasInfiniteRerollPrestige() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return false;
        }
        
        PrestigeTree prestige = PrestigeHelper.getPrestige(mc.player);
        return !prestige.getAll(BlackMarketRerollsPrestigePowerPower.class, Skill::isUnlocked).isEmpty();
    }
    
    private boolean hasFilterItem() {
        ItemStack filterItem = FilterStorage.getFilterItem();
        return filterItem != null && !filterItem.isEmpty();
    }
}
