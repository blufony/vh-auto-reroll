package dev.blufony.autoreroll.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

/**
 * Dedicated, independent config storage for the Bounty Table UI.
 * Deliberately does NOT share any state with the Black Market
 * (ShardTradeScreen) config ({@link AutoRerollConfig} / {@code FilterStorage}).
 */
public class BountyTableConfig {
    private static final Logger LOGGER = LogManager.getLogger("AutoReroll");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Cached state
    private static boolean rareOnly = false;
    private static ItemStack filterItem = null;
    private static boolean loaded = false;

    /**
     * Task types the user wants to keep (enabled toggles). Empty means the
     * type filter is inactive (any bounty type is accepted).
     */
    private static final Set<String> enabledTaskTypes = new HashSet<>();

    private static Path getConfigPath() {
        return Paths.get("config", "vh_auto_reroll", "bounty_table.json");
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;

        Path path = getConfigPath();
        if (!Files.exists(path)) {
            return;
        }

        try {
            String jsonContent = Files.readString(path);
            if (jsonContent.isBlank()) {
                return;
            }

            JsonObject json = JsonParser.parseString(jsonContent).getAsJsonObject();

            if (json.has("enabledTaskTypes") && json.get("enabledTaskTypes").isJsonArray()) {
                enabledTaskTypes.clear();
                json.getAsJsonArray("enabledTaskTypes").forEach(e -> enabledTaskTypes.add(e.getAsString()));
            }
            if (json.has("filter") && json.get("filter").isJsonObject()) {
                JsonObject filter = json.getAsJsonObject("filter");
                if (filter.has("item") && !filter.get("item").getAsString().isEmpty()) {
                    ResourceLocation itemId = new ResourceLocation(filter.get("item").getAsString());
                    Item item = ForgeRegistries.ITEMS.getValue(itemId);
                    if (item != null) {
                        ItemStack stack = new ItemStack(item);
                        if (filter.has("tag") && !filter.get("tag").isJsonNull()) {
                            CompoundTag nbt = TagParser.parseTag(filter.get("tag").getAsString());
                            stack.setTag(nbt);
                        }
                        filterItem = stack;
                    } else {
                        LOGGER.warn("[BountyTable] Filter item '{}' not found in registry", itemId);
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.warn("[BountyTable] Failed to read config {}: {}", path, e.getMessage());
        } catch (Exception e) {
            LOGGER.warn("[BountyTable] Failed to parse config {}: {}", path, e.getMessage());
        }
    }

    private static void save() {
        Path path = getConfigPath();
        try {
            Files.createDirectories(path.getParent());

            JsonObject json = new JsonObject();
            json.addProperty("rareOnly", rareOnly);
            json.add("enabledTaskTypes", GSON.toJsonTree(enabledTaskTypes));

            JsonObject filter = new JsonObject();
            if (filterItem != null && !filterItem.isEmpty()) {
                filter.addProperty("item", String.valueOf(ForgeRegistries.ITEMS.getKey(filterItem.getItem())));
                CompoundTag nbt = filterItem.getTag();
                if (nbt != null && !nbt.isEmpty()) {
                    filter.addProperty("tag", nbt.toString());
                }
            }
            json.add("filter", filter);

            Files.writeString(path, GSON.toJson(json));
        } catch (IOException e) {
            LOGGER.error("[BountyTable] Failed to save config {}: {}", path, e.getMessage());
        }
    }

    // ---- Rare only flag ----

    public static boolean isRareOnly() {
        ensureLoaded();
        return rareOnly;
    }

    public static void setRareOnly(boolean enabled) {
        rareOnly = enabled;
        save();
    }

    // ---- Filter item ----

    public static ItemStack getFilterItem() {
        ensureLoaded();
        return filterItem != null ? filterItem : ItemStack.EMPTY;
    }

    public static void saveFilterItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            clearFilterItem();
            return;
        }
        filterItem = stack.copy();
        save();
    }

    public static void clearFilterItem() {
        filterItem = null;
        save();
    }

    // ---- Enabled bounty task types ----

    /** @return true if the given bounty task type id (e.g. "the_vault:mining") is enabled. */
    public static boolean isTaskTypeEnabled(String taskTypeId) {
        ensureLoaded();
        return enabledTaskTypes.contains(taskTypeId);
    }

    public static void setTaskTypeEnabled(String taskTypeId, boolean enabled) {
        if (enabled) {
            enabledTaskTypes.add(taskTypeId);
        } else {
            enabledTaskTypes.remove(taskTypeId);
        }
        save();
    }

    /**
     * @return true if the task-type filter is active (at least one type
     * enabled) AND the given task type is enabled. An empty enabled-set
     * means the filter is inactive and any type is accepted.
     */
    public static boolean isTaskTypeAccepted(String taskTypeId) {
        ensureLoaded();
        return enabledTaskTypes.isEmpty() || enabledTaskTypes.contains(taskTypeId);
    }
}
