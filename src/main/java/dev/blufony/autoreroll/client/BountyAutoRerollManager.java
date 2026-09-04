package dev.blufony.autoreroll.client;

import dev.blufony.autoreroll.AutoRerollMod;
import dev.blufony.autoreroll.client.mixin.BountyTableContainerElementInvoker;
import dev.blufony.autoreroll.config.BountyTableConfig;
import iskallia.vault.bounty.Bounty;
import iskallia.vault.client.gui.screen.bounty.BountyScreen;
import iskallia.vault.client.gui.screen.bounty.element.BountyElement;
import iskallia.vault.client.gui.screen.bounty.element.BountyTableContainerElement;
import net.minecraft.client.Minecraft;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.ref.WeakReference;
import java.util.function.BooleanSupplier;

/**
 * Drives automatic rerolling of the selected bounty in the Bounty Table,
 * reactively: instead of rerolling blindly on a timer, we intercept the
 * server's refresh response (see ClientboundRefreshBountiesMessageMixin),
 * inspect the freshly generated bounty's reward pool and decide whether to
 * stop (desired rarity reached) or fire the next reroll.
 *
 * Reuses the safety rails proven on the bounty-table timer loop:
 * - weak container reference, invoker-mixin dispatch (properly remapped)
 * - stops when the screen closes, the container vanishes, the vanilla
 *   reroll button is disabled (pearls/selection/status), or on errors.
 */
@Mod.EventBusSubscriber(modid = AutoRerollMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class BountyAutoRerollManager {
    private static final Logger LOGGER = LogManager.getLogger();

    /** Reward pools treated as satisfying the "rare only" condition. */
    private static final String[] RARE_OR_BETTER_POOLS = {"rare", "legendary"};

    private static boolean isRunning = false;
    private static WeakReference<BountyTableContainerElement> containerRef = new WeakReference<>(null);
    /** Mirrors the vanilla reroll button's disabled state (pearls, selection, status). */
    private static BooleanSupplier vanillaDisabledCheck = () -> false;

    private BountyAutoRerollManager() {
    }

    public static synchronized boolean isRunning() {
        return isRunning;
    }

    /**
     * Toggles auto-reroll on/off. When turning on, the container backing the
     * bounty table screen is retained (weakly) so the vanilla reroll handler
     * can be re-invoked.
     */
    public static synchronized void toggle(BountyTableContainerElement container, BooleanSupplier disabledCheck) {
        if (isRunning) {
            stop();
        } else {
            start(container, disabledCheck);
        }
    }

    public static synchronized void start(BountyTableContainerElement container, BooleanSupplier disabledCheck) {
        if (container == null) {
            LOGGER.warn("Cannot start bounty auto-reroll: no container");
            return;
        }

        if (!(container instanceof BountyTableContainerElementInvoker)) {
            LOGGER.error("Container does not implement the reroll invoker mixin - is the mixin registered?");
            return;
        }

        containerRef = new WeakReference<>(container);
        vanillaDisabledCheck = disabledCheck != null ? disabledCheck : () -> false;
        isRunning = true;
        LOGGER.info("Bounty auto-reroll started");

        // Kick off the first reroll immediately; the server's refresh
        // response drives all subsequent ones.
        sendNextReroll(container);
    }

    public static synchronized void stop() {
        if (!isRunning) {
            return;
        }
        isRunning = false;
        containerRef.clear();
        vanillaDisabledCheck = () -> false;
        LOGGER.info("Bounty auto-reroll stopped");
    }

    /**
     * Called from ClientboundRefreshBountiesMessageMixin at the END of the
     * vanilla packet handler: the container's bounty lists and the bounty
     * selection have already been refreshed.
     *
     * Decides whether the freshly rolled bounty satisfies the "rare only"
     * condition; if not (and we are running), fires the next reroll.
     */
    public static synchronized void onBountiesRefreshed() {
        if (!isRunning) {
            return;
        }

        BountyTableContainerElement container = containerRef.get();
        if (container == null) {
            stop();
            return;
        }

        Bounty selected = getSelectedBounty(container);
        if (selected == null) {
            // Selection vanished (bounty activated by someone else etc.) - bail out.
            LOGGER.info("Bounty auto-reroll stopped: no bounty selected after refresh");
            stop();
            return;
        }

        String pool = rewardPoolOf(selected);

        // Bounty task-type filter: if any type switch is on, the rolled
        // bounty must be of one of the enabled types; a match stops the
        // loop immediately (unless rare-only also demands a better pool).
        if (!BountyTableConfig.isTaskTypeAccepted(taskTypeOf(selected))) {
            LOGGER.debug("Bounty auto-reroll: task type '{}' not desired, rerolling", taskTypeOf(selected));
            sendNextReroll(container);
            return;
        }

        // Rare-only mode: stop once the rolled bounty's reward pool is
        // rare or better; otherwise immediately fire the next reroll.
        if (BountyTableConfig.isRareOnly()) {
            if (isDesiredPool(pool)) {
                LOGGER.info("Bounty auto-reroll satisfied: reward pool '{}'", pool);
                stop();
                return;
            }

            LOGGER.debug("Bounty auto-reroll: reward pool '{}' not desired, rerolling", pool);
            sendNextReroll(container);
            return;
        }

        // Rare-only off: the task-type match above was the goal - stop here.
        LOGGER.info("Bounty auto-reroll satisfied: task type '{}', pool '{}'", taskTypeOf(selected), pool);
        stop();
    }

    /**
     * Safety-rail tick check: stops the loop as soon as the bounty table
     * screen is closed, the container vanishes, or the vanilla reroll button
     * becomes disabled (pearls, selection, status).
     */
    @SubscribeEvent
    public static synchronized void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !isRunning) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();

        // Stop as soon as the bounty table screen is closed.
        if (!(mc.screen instanceof BountyScreen)) {
            stop();
            return;
        }

        BountyTableContainerElement container = containerRef.get();
        if (container == null) {
            stop();
            return;
        }

        // Stop when the vanilla reroll button is disabled (pearls, selection, status).
        if (vanillaDisabledCheck.getAsBoolean()) {
            LOGGER.info("Bounty auto-reroll stopped: vanilla reroll button is disabled");
            stop();
            return;
        }
    }

    private static void sendNextReroll(BountyTableContainerElement container) {
        try {
            // Vanilla handler: validates pearl count against the reroll cost and
            // sends ServerboundRerollMessage to the server. It is a no-op when
            // insufficient pearls (the disabled check above covers that).
            ((BountyTableContainerElementInvoker) container).autoreroll$invokeHandleReroll();
            LOGGER.debug("Bounty auto-reroll: reroll sent, awaiting response");
        } catch (Exception e) {
            LOGGER.error("Failed to invoke bounty reroll handler - stopping", e);
            stop();
        }
    }

    private static Bounty getSelectedBounty(BountyTableContainerElement container) {
        try {
            BountyElement element = ((BountyTableContainerElementInvoker) container).autoreroll$getBountyElement();
            return element != null ? element.getSelectedBounty() : null;
        } catch (Exception e) {
            LOGGER.error("Failed to read selected bounty", e);
            return null;
        }
    }

    private static String rewardPoolOf(Bounty bounty) {
        try {
            return bounty.getTask().getProperties().getRewardPool();
        } catch (Exception e) {
            LOGGER.error("Failed to read reward pool of bounty - treating as not desired", e);
            return "unknown";
        }
    }

    private static String taskTypeOf(Bounty bounty) {
        try {
            return String.valueOf(bounty.getTask().getTaskType());
        } catch (Exception e) {
            LOGGER.error("Failed to read task type of bounty - treating as not desired", e);
            return "unknown";
        }
    }

    private static boolean isDesiredPool(String pool) {
        for (String desired : RARE_OR_BETTER_POOLS) {
            if (desired.equalsIgnoreCase(pool)) {
                return true;
            }
        }
        return false;
    }
}
