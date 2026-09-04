package dev.blufony.autoreroll.client.mixin;

import dev.blufony.autoreroll.client.BountyAutoRerollManager;
import dev.blufony.autoreroll.client.gui.IconButtonElement;
import dev.blufony.autoreroll.client.gui.RareOnlyToggle;
import dev.blufony.autoreroll.client.gui.TaskTypeToggle;
import dev.blufony.autoreroll.config.BountyTableConfig;
import iskallia.vault.VaultMod;
import iskallia.vault.client.atlas.TextureAtlasRegion;
import iskallia.vault.client.gui.framework.ScreenTextures;
import iskallia.vault.client.gui.framework.element.ButtonElement;
import iskallia.vault.client.gui.framework.element.spi.ElementStore;
import iskallia.vault.client.gui.framework.element.spi.ISpatialElement;
import iskallia.vault.client.gui.framework.spatial.Spatials;
import iskallia.vault.client.gui.screen.bounty.element.BountyTableContainerElement;
import iskallia.vault.init.ModTextureAtlases;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.TextComponent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = BountyTableContainerElement.class, remap = false)
public abstract class BountyTableContainerElementMixin {
    private static final Logger LOGGER = LogManager.getLogger("AutoReroll");

    /** Position of the vanilla reroll button (see BountyTableContainerElement#createRerollButton). */
    @Unique
    private static final int VANILLA_REROLL_BUTTON_X = 95;
    @Unique
    private static final int VANILLA_REROLL_BUTTON_Y = 117;

    @Unique
    private static final TextureAtlasRegion CYCLE_ICON = TextureAtlasRegion.of(
        ModTextureAtlases.SCREEN,
        VaultMod.id("gui/screen/button/cycle")
    );

    @Unique
    private static final TextureAtlasRegion CYCLE_ICON_ACTIVE = TextureAtlasRegion.of(
        ModTextureAtlases.SCREEN,
        VaultMod.id("gui/screen/button/cycle_highlight")
    );

    @Unique
    private static ButtonElement.ButtonTextures autoRerollButtonTextures() {
        return new ButtonElement.ButtonTextures(
            TextureAtlasRegion.of(ModTextureAtlases.SCREEN, VaultMod.id("gui/screen/button/blue/normal.9")),
            TextureAtlasRegion.of(ModTextureAtlases.SCREEN, VaultMod.id("gui/screen/button/blue/hover.9")),
            TextureAtlasRegion.of(ModTextureAtlases.SCREEN, VaultMod.id("gui/screen/button/blue/pressed.9")),
            TextureAtlasRegion.of(ModTextureAtlases.SCREEN, VaultMod.id("gui/screen/button/blue/disabled.9"))
        );
    }

    /**
     * Vanilla leak fix: {@code refreshBountySelection()} rebuilds the bounty
     * selection on every reroll response, but only removes its tracked buttons
     * and rarity outlines - the HeaderElement and the three row LabelElements
     * ("ACTIVE"/"AVAILABLE"/"COMPLETE") are re-added every time and never
     * removed. With auto-reroll firing many refreshes, these orphaned labels
     * accumulate unboundedly and degrade rendering.
     *
     * Fix: on each refresh, snapshot the element list at HEAD; at RETURN,
     * record the elements added during this refresh (excluding anything that
     * existed before, which includes our own custom elements). At the next
     * refresh's HEAD, remove those leftovers. Vanilla removes its tracked
     * buttons itself first - removeElement on already-removed elements is a
     * harmless no-op.
     */
    @Unique
    private final List<ISpatialElement> autoreroll$baselineElements = new java.util.ArrayList<>();
    @Unique
    private final List<ISpatialElement> autoreroll$addedLastRefresh = new java.util.ArrayList<>();

    @Inject(method = "refreshBountySelection", at = @At("HEAD"))
    private void autoreroll$beforeRefreshSelection(CallbackInfo ci) {
        ElementStore store = ((BountyTableContainerElement) (Object) this).getElementStore();

        // Remove the previous refresh generation's leftover elements.
        if (!this.autoreroll$addedLastRefresh.isEmpty()) {
            List<ISpatialElement> current = store.getSpatialElementList();
            for (ISpatialElement stale : this.autoreroll$addedLastRefresh) {
                if (current.contains(stale)) {
                    store.removeElement(stale);
                }
            }
            this.autoreroll$addedLastRefresh.clear();
        }

        this.autoreroll$baselineElements.clear();
        this.autoreroll$baselineElements.addAll(store.getSpatialElementList());
    }

    @Inject(method = "refreshBountySelection", at = @At("RETURN"))
    private void autoreroll$afterRefreshSelection(CallbackInfo ci) {
        for (ISpatialElement element : ((BountyTableContainerElement) (Object) this).getElementStore().getSpatialElementList()) {
            if (!this.autoreroll$baselineElements.contains(element)) {
                this.autoreroll$addedLastRefresh.add(element);
            }
        }
        this.autoreroll$baselineElements.clear();
    }

    @Inject(method = "createRerollButton", at = @At("RETURN"))
    private void onCreateRerollButton(CallbackInfo ci) {        BountyTableContainerElement target = (BountyTableContainerElement) (Object) this;

        ButtonElement vanillaRerollButton = findVanillaRerollButton(target);
        addAutoRerollButton(target, vanillaRerollButton);
        addRareOnlyToggle(target);
        addTaskTypeToggles(target);
    }

    /**
     * Locates the vanilla reroll button added by createRerollButton().
     * Primary strategy: it is the element just added by createRerollButton(),
     * i.e. the tail of the spatial element list. Fallback: the vanilla
     * implementation places it at the fixed position (95, 117).
     */
    @Unique
    private ButtonElement findVanillaRerollButton(BountyTableContainerElement target) {
        List<ISpatialElement> spatialElements = target.getElementStore().getSpatialElementList();
        if (!spatialElements.isEmpty()
            && spatialElements.get(spatialElements.size() - 1) instanceof ButtonElement<?> button) {
            return button;
        }

        // Fallback: match by the vanilla button's fixed position.
        for (ISpatialElement element : spatialElements) {
            if (element instanceof ButtonElement<?> button
                && button.getWorldSpatial().x() == VANILLA_REROLL_BUTTON_X
                && button.getWorldSpatial().y() == VANILLA_REROLL_BUTTON_Y) {
                return button;
            }
        }

        LOGGER.warn("Could not find vanilla reroll button for disabled-state mirroring");
        return null;
    }

    @Unique
    private void addAutoRerollButton(BountyTableContainerElement target,
                                     ButtonElement vanillaRerollButton) {
        int customButtonX = 115;
        // +2 px vertical nudge baked into the initial position (spatial is immutable).
        int customButtonY = 119;
        ElementStore elementStore = target.getElementStore();

        IconButtonElement autoRerollButton = new IconButtonElement(
            Spatials.positionXY(customButtonX, customButtonY),
            autoRerollButtonTextures(),
            () -> BountyAutoRerollManager.toggle(target,
                vanillaRerollButton != null ? vanillaRerollButton::isDisabled : null)
        );

        autoRerollButton.icon(() -> CYCLE_ICON);
        autoRerollButton.activeIcon(() -> CYCLE_ICON_ACTIVE);

        autoRerollButton.tooltip(() -> {
            if (autoRerollButton.isDisabled()) {
                return new TextComponent("Rerolls Unavailable").withStyle(ChatFormatting.GRAY);
            }
            return BountyAutoRerollManager.isRunning()
                ? new TextComponent("Auto-Reroll: On").withStyle(ChatFormatting.GREEN)
                : new TextComponent("Auto-Reroll: Off").withStyle(ChatFormatting.RED);
        });

        // Mirror the vanilla reroll button's disabled state instead of
        // duplicating its checks (selected bounty, status, pearl cost).
        if (vanillaRerollButton != null) {
            autoRerollButton.setDisabled(vanillaRerollButton::isDisabled);
        }

        // No extra .layout(...) here: as a child of the container it already inherits
        // the container's gui translation, matching the vanilla reroll button.
        elementStore.addElement(autoRerollButton);
    }

    @Unique
    private void addRareOnlyToggle(BountyTableContainerElement target) {
        // Right of the auto-reroll button: reroll button ends at 115+18=133,
        // so 2px margin -> x = 135. Horizontal (landscape) toggle: 18x9.
        int toggleX = 135;
        int toggleY = 122; // vertically centered on the 18px reroll row

        RareOnlyToggle toggle = new RareOnlyToggle(
            Spatials.positionXY(toggleX, toggleY),
            () -> BountyTableConfig.setRareOnly(!BountyTableConfig.isRareOnly())
        );

        toggle.tooltip(() -> RareOnlyToggle.tooltip(toggle.isOn()));

        target.getElementStore().addElement(toggle);
    }

    /**
     * Adds one toggle switch per bounty task type, in a horizontal row to the
     * right of the Rare Only switch (2px margins between switches).
     * Only the task types listed here can be kept by the auto-reroll loop;
     * an all-off row means "accept any type".
     */
    @Unique
    private void addTaskTypeToggles(BountyTableContainerElement target) {
        int startX = 9; // left-most column
        int toggleY = 117;

        String[][] taskTypes = {
            {"the_vault:kill_entity", "Kill"},
            {"the_vault:completion", "Completion"},
            {"the_vault:item_submission", "Submit Items"},
            {"the_vault:item_discovery", "Discover Items"},
            {"the_vault:mining", "Mining"},
        };

        ElementStore elementStore = target.getElementStore();
        int x = startX;

        for (String[] type : taskTypes) {
            String typeId = type[0];
            String label = type[1];

            TaskTypeToggle toggle = new TaskTypeToggle(
                Spatials.positionXY(x, toggleY),
                typeId,
                label,
                () -> BountyTableConfig.setTaskTypeEnabled(typeId, !BountyTableConfig.isTaskTypeEnabled(typeId))
            );

            toggle.tooltip(() -> TaskTypeToggle.tooltip(toggle.isOn(), label));
            elementStore.addElement(toggle);
            x += 9 + 2; // width + 2px margin
        }
    }
}
