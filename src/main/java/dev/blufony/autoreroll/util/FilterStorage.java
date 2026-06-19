package dev.blufony.autoreroll.util;

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
import java.util.Optional;

public class FilterStorage {
    private static final Logger LOGGER = LogManager.getLogger(FilterStorage.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ItemStack cachedFilterItem = null;
    private static boolean cacheLoaded = false;
    
    private static Path getFilterPath() {
        return Paths.get("config", "auto_reroll", "filter.json");
    }
    
    public static Optional<ItemStack> loadFilterItem() {
        Path path = getFilterPath();
        
        if (!Files.exists(path)) {
            LOGGER.info("No filter file found at {}, skipping auto-reroll", path);
            return Optional.empty();
        }
        
        try {
            String jsonContent = Files.readString(path);
            
            if (jsonContent.isBlank()) {
                LOGGER.info("Filter file {} is empty, skipping auto-reroll", path);
                return Optional.empty();
            }
            
            JsonObject json = JsonParser.parseString(jsonContent).getAsJsonObject();
            
            if (!json.has("item") || json.get("item").getAsString().isEmpty()) {
                LOGGER.warn("Filter file {} missing 'item' field, skipping auto-reroll", path);
                return Optional.empty();
            }
            
            String itemStr = json.get("item").getAsString();
            ResourceLocation itemId = new ResourceLocation(itemStr);
            Item item = ForgeRegistries.ITEMS.getValue(itemId);
            
            if (item == null) {
                LOGGER.warn("Filter item '{}' not found in registry, skipping auto-reroll", itemId);
                return Optional.empty();
            }
            
            if (!itemId.getNamespace().equals("create")) {
                LOGGER.warn("Filter item '{}' is not a Create filter item (expected create:*), skipping auto-reroll", itemId);
                return Optional.empty();
            }
            
            ItemStack filterStack = new ItemStack(item);
            
            if (json.has("tag") && !json.get("tag").isJsonNull()) {
                String tagStr = json.get("tag").getAsString();
                CompoundTag nbt = TagParser.parseTag(tagStr);
                filterStack.setTag(nbt);
            }
            
            LOGGER.info("Successfully loaded filter item: {}", itemId);
            return Optional.of(filterStack);
            
        } catch (IOException e) {
            LOGGER.warn("Failed to read filter file {}: {}", path, e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            LOGGER.warn("Failed to parse filter file {}: {}", path, e.getMessage());
            return Optional.empty();
        }
    }
    
    public static void saveFilterItem(ItemStack filterStack) {
        Path path = getFilterPath();
        
        try {
            Files.createDirectories(path.getParent());
            
            String itemId = String.valueOf(ForgeRegistries.ITEMS.getKey(filterStack.getItem()));
            
            JsonObject json = new JsonObject();
            json.addProperty("item", itemId);
            
            CompoundTag nbt = filterStack.getTag();
            if (nbt != null && !nbt.isEmpty()) {
                json.addProperty("tag", nbt.toString());
            }
            
            String jsonContent = GSON.toJson(json);
            Files.writeString(path, jsonContent);
            LOGGER.info("Saved filter item to {}", path);
            
            // Update cache
            cachedFilterItem = filterStack.copy();
            cacheLoaded = true;
            
        } catch (IOException e) {
            LOGGER.error("Failed to save filter item to {}: {}", path, e.getMessage());
        }
    }
    
    public static ItemStack getFilterItem() {
        if (!cacheLoaded) {
                Optional<ItemStack> loaded = loadFilterItem();
            if (loaded.isPresent()) {
                cachedFilterItem = loaded.get();
            }
            cacheLoaded = true;
        }
        return cachedFilterItem != null ? cachedFilterItem : ItemStack.EMPTY;
    }
    
    public static void clearFilterItem() {
        Path path = getFilterPath();
        
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, "{}");
            LOGGER.info("Cleared filter from {}", path);
            
            // Clear cache
            cachedFilterItem = null;
            cacheLoaded = false;
            
        } catch (IOException e) {
            LOGGER.error("Failed to clear filter from {}: {}", path, e.getMessage());
        }
    }
}
