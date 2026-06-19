package dev.blufony.autoreroll.integration.jei;

import dev.blufony.autoreroll.util.FilterStorage;
import dev.blufony.autoreroll.util.NotifyUtil;
import iskallia.vault.client.gui.screen.ShardTradeScreen;
import iskallia.vault.skill.prestige.BlackMarketRerollsPrestigePowerPower;
import iskallia.vault.skill.base.Skill;
import iskallia.vault.skill.tree.PrestigeTree;
import iskallia.vault.skill.prestige.helper.PrestigeHelper;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler.Target;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.List;

@JeiPlugin
public class AutoRerollJeiPlugin implements IModPlugin {
    private static final Logger LOGGER = LogManager.getLogger(AutoRerollJeiPlugin.class);
    private static final ResourceLocation PLUGIN_ID = new ResourceLocation("vh_auto_reroll", "jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return PLUGIN_ID;
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGhostIngredientHandler(
            ShardTradeScreen.class,
            new FilterGhostHandler()
        );
        LOGGER.info("Registered JEI ghost ingredient handler for ShardTradeScreen");
    }

    public static class FilterGhostHandler implements IGhostIngredientHandler<ShardTradeScreen> {
        @Override
        public <T> List<Target<T>> getTargets(ShardTradeScreen screen, T ingredient, boolean doStart) {
            if (!(ingredient instanceof ItemStack)) {
                return Collections.emptyList();
            }
            
            Player player = Minecraft.getInstance().player;
            if (player == null) {
                return Collections.emptyList();
            }
            
            PrestigeTree prestige = PrestigeHelper.getPrestige(player);
            if (prestige == null || prestige.getAll(BlackMarketRerollsPrestigePowerPower.class, Skill::isUnlocked).isEmpty()) {
                return Collections.emptyList();
            }
            
            int posX = 29;
            int posY = 21;
            
            Target<T> target = new Target<>() {
                @Override
                public Rect2i getArea() {
                    return new Rect2i(
                        screen.getGuiLeft() + posX,
                        screen.getGuiTop() + posY,
                        18,
                        18
                    );
                }
                
                @Override
                public void accept(T ingredient) {
                    if (ingredient instanceof ItemStack stack && !stack.isEmpty()) {
                        FilterStorage.saveFilterItem(stack.copy());
                        NotifyUtil.notifyPlayer("[AutoReroll] Filter slot updated!");
                        LOGGER.info("JEI drag: Set filter to {}", stack.getItem().getDescriptionId());
                    }
                }
            };
            
            return Collections.singletonList(target);
        }
        
        @Override
        public void onComplete() {}
    }
}
