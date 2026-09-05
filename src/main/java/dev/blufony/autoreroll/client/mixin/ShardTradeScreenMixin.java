package dev.blufony.autoreroll.client.mixin;

import dev.blufony.autoreroll.AutoRerollMod;
import dev.blufony.autoreroll.client.AutoRerollManager;
import dev.blufony.autoreroll.client.gui.AutoBuyToggle;
import dev.blufony.autoreroll.client.gui.IconButtonElement;
import dev.blufony.autoreroll.client.gui.SlotTargetToggle;
import dev.blufony.autoreroll.config.AutoRerollConfig;
import dev.blufony.autoreroll.util.FilterStorage;
import dev.blufony.autoreroll.util.ItemMatcher;
import dev.blufony.autoreroll.util.NotifyUtil;
import com.simibubi.create.foundation.config.ui.ConfigScreen;
import com.simibubi.create.foundation.config.ui.SubMenuConfigScreen;
import iskallia.vault.VaultMod;
import iskallia.vault.client.atlas.TextureAtlasRegion;
import iskallia.vault.client.data.ClientShardTradeData;
import iskallia.vault.client.gui.framework.ScreenTextures;
import iskallia.vault.client.gui.framework.element.ButtonElement;
import iskallia.vault.client.gui.framework.element.FakeItemSlotElement;
import iskallia.vault.client.gui.framework.element.spi.ElementStore;
import iskallia.vault.client.gui.framework.render.TooltipDirection;
import iskallia.vault.client.gui.framework.render.Tooltips;
import iskallia.vault.client.gui.framework.spatial.Spatials;
import iskallia.vault.client.gui.framework.screen.AbstractElementContainerScreen;
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

import java.util.List;
import java.util.function.Consumer;

@Mixin(value = iskallia.vault.client.gui.screen.ShardTradeScreen.class, priority = 1000)
public abstract class ShardTradeScreenMixin {

    // Auto-reroll button textures using Vault's blue button
    private static final ButtonElement.ButtonTextures AUTO_REROLL_BUTTON_TEXTURES = new ButtonElement.ButtonTextures(
        TextureAtlasRegion.of(ModTextureAtlases.SCREEN, VaultMod.id("gui/screen/button/blue/normal.9")),
        TextureAtlasRegion.of(ModTextureAtlases.SCREEN, VaultMod.id("gui/screen/button/blue/hover.9")),
        TextureAtlasRegion.of(ModTextureAtlases.SCREEN, VaultMod.id("gui/screen/button/blue/pressed.9")),
        TextureAtlasRegion.of(ModTextureAtlases.SCREEN, VaultMod.id("gui/screen/button/blue/disabled.9"))
    );

    // Cycle icon overlay
    private static final TextureAtlasRegion CYCLE_ICON = TextureAtlasRegion.of(
        ModTextureAtlases.SCREEN,
        VaultMod.id("gui/screen/button/cycle")
    );

    private static final TextureAtlasRegion CYCLE_ICON_ACTIVE = TextureAtlasRegion.of(
        ModTextureAtlases.SCREEN,
        VaultMod.id("gui/screen/button/cycle_highlight")
    );
    
    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(ShardTradeContainer container, Inventory inventory, Component title, CallbackInfo ci) {
        try {
            ElementStore store = ((AbstractElementContainerScreenAccessor) this).autoreroll$getElementStore();
            
            if (store == null) {
                System.err.println("[AutoReroll] ElementStore is null");
                return;
            }
            
            var guiElements = store.getGuiEventElementList();
            ButtonElement<?> resetButton = null;
            
            // Identify the vanilla reset button by its texture set (no reflection).
            for (var element : guiElements) {
                if (element instanceof ButtonElement<?> btn
                    && ((ButtonElementAccessor) btn).autoreroll$getTextures() == ScreenTextures.BUTTON_RESET_TRADES_TEXTURES) {
                    resetButton = btn;
                    break;
                }
            }
            
            if (resetButton == null) {
                System.err.println("[AutoReroll] Reset button not found");
                return;
            }
            
            modifyButtonClick(resetButton);
            addAutoRerollButton(store, resetButton);
            addFilterSlot(store);
            addSlotTargetToggle(store);
            addAutoBuyToggle(store);
            
        } catch (Exception e) {
            System.err.println("[AutoReroll] Error during button modification: " + e.getMessage());
        }
    }
    
    private boolean modifyButtonClick(ButtonElement<?> button) {
        try {
            Consumer<ButtonElement<?>> originalOnClick = ((ButtonElementAccessor) button).autoreroll$getOnClick();
            
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
            
            ((ButtonElementAccessor) button).autoreroll$setOnClick(wrappedOnClick);
            
            return true;
            
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
            
            int resetX = resetButton.getWorldSpatial().x();
            int resetY = resetButton.getWorldSpatial().y();

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
                    NotifyUtil.playSound(ModSounds.SKILL_TREE_LEARN_SFX);
                        
                        AutoRerollManager.start(() -> {});
                }
            );
            
            autoRerollButton.setDisabled(() -> !hasInfiniteRerollPrestige() || !hasFilterItem());
            
            autoRerollButton.icon(() -> CYCLE_ICON);
            autoRerollButton.activeIcon(() -> CYCLE_ICON_ACTIVE);
            
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
                return AutoRerollConfig.AUTO_BUY.get()
                    ? new TextComponent("Start Auto-Reroll (Auto-Buy Enabled)")
                    : new TextComponent("Start Auto-Reroll");
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
                
                if (!carriedItem.isEmpty()) {
                    FilterStorage.saveFilterItem(carriedItem);
                    NotifyUtil.notifyPlayer("[AutoReroll] Filter slot updated!");
                } else {
                    FilterStorage.clearFilterItem();
                    NotifyUtil.notifyPlayer("[AutoReroll] Filter cleared!");
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
                    tooltipRenderer.renderTooltip(poseStack, new TextComponent("Click with Create Filter or Item to set Auto-Reroll Target"), mouseX, mouseY, TooltipDirection.RIGHT);
                }
                
                return true;
            });
            
            store.addElement(filterSlot);
            
        } catch (Exception e) {
            System.err.println("[AutoReroll] Error adding filter slot: " + e.getMessage());
        }
    }
    
    private void addSlotTargetToggle(ElementStore store) {
        try {
            if (!hasInfiniteRerollPrestige()) {
                return;
            }
            
            var guiElements = store.getGuiEventElementList();
            ButtonElement<?> omegaButton = null;
            
            for (var element : guiElements) {
                if (element instanceof ButtonElement<?> button) {
                    int btnX = button.getWorldSpatial().x();
                    int btnY = button.getWorldSpatial().y();
                    
                    if (btnX >= 75 && btnX <= 80 && btnY >= 40 && btnY <= 46) {
                        omegaButton = button;
                        break;
                    }
                }
            }
            
            if (omegaButton == null) {
                System.err.println("[AutoReroll] Omega trade button not found - slot target toggle disabled");
                return;
            }
            
            int centerX = omegaButton.getWorldSpatial().x();
            int centerY = omegaButton.getWorldSpatial().y();
            
            int toggleX = centerX - 14;
            int toggleY = centerY + 5;
            
            SlotTargetToggle toggle = new SlotTargetToggle(
                Spatials.positionXY(toggleX, toggleY),
                () -> {
                    AutoRerollConfig.SEARCH_ALL_SLOTS.set(!AutoRerollConfig.SEARCH_ALL_SLOTS.get());
                    AutoRerollConfig.CLIENT_SPEC.save();
                }
            );
            
            toggle.tooltip(() -> {
                if (!hasInfiniteRerollPrestige()) {
                    return new TextComponent("Unlock Whispers of the Market Prestige");
                }
                
                return toggle.isOmegaOnly()
                    ? new TextComponent("Omega slot only")
                    : new TextComponent("Search all slots");
            });
            
            ((SlotTargetToggle) store.addElement(toggle))
                .layout((screen, gui, parent, world) -> world.translateXY(gui));
            
        } catch (Exception e) {
            System.err.println("[AutoReroll] Error adding slot target toggle: " + e.getMessage());
        }
    }
    
    private void addAutoBuyToggle(ElementStore store) {
        try {
            if (!hasInfiniteRerollPrestige()) {
                return;
            }
            
            int toggleX = 29;
            int toggleY = 90;
            
            AutoBuyToggle toggle = new AutoBuyToggle(
                Spatials.positionXY(toggleX, toggleY),
                () -> {
                    boolean newMode = !AutoRerollConfig.AUTO_BUY.get();
                    AutoRerollConfig.AUTO_BUY.set(newMode);
                    AutoRerollConfig.CLIENT_SPEC.save();
                }
            );
            
            toggle.tooltip(() -> {
                if (!hasInfiniteRerollPrestige()) {
                    return new TextComponent("Unlock Whispers of the Market Prestige");
                }
                
                return AutoRerollConfig.AUTO_BUY.get()
                    ? new TextComponent("Auto-Buy On")
                    : new TextComponent("Auto-Buy Off");
            });
            
            ((AutoBuyToggle) store.addElement(toggle))
                .layout((screen, gui, parent, world) -> world.translateXY(gui));
            
        } catch (Exception e) {
            System.err.println("[AutoReroll] Error adding auto-buy toggle: " + e.getMessage());
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
