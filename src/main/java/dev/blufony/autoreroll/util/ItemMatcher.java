package dev.blufony.autoreroll.util;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

public class ItemMatcher {
    private static final Logger LOGGER = LogManager.getLogger(ItemMatcher.class);
    private static final Gson GSON = new Gson();

    public static class TargetEntry {
        ResourceLocation itemId;
        CompoundTag nbtData;

        public TargetEntry(ResourceLocation itemId) {
            this.itemId = itemId;
            this.nbtData = null;
        }

        public TargetEntry(ResourceLocation itemId, CompoundTag nbtData) {
            this.itemId = itemId;
            this.nbtData = nbtData;
        }

        public boolean matchesNbt(ItemStack stack) {
            if (this.nbtData == null || this.nbtData.isEmpty()) {
                return true;
            }

            CompoundTag stackTag = stack.getTag();
            if (stackTag == null && this.nbtData.isEmpty()) {
                return true;
            }
            if (stackTag == null) {
                return false;
            }

            for (String key : this.nbtData.getAllKeys()) {
                if (!stackTag.contains(key)) {
                    return false;
                }
                var targetTag = this.nbtData.get(key);
                var stackTagValue = stackTag.get(key);

                if (targetTag.getClass() != stackTagValue.getClass()) {
                    return false;
                }

                if (!targetTag.equals(stackTagValue)) {
                    return false;
                }
            }

            return true;
        }
    }

    public static boolean matches(ItemStack stack, List<ResourceLocation> targets) {
        ResourceLocation itemId = stack.getItem().builtInRegistryHolder().key().location();
        return targets.contains(itemId);
    }

    public static boolean matchesWithNbt(ItemStack stack, List<String> targets) {
        ResourceLocation itemId = stack.getItem().builtInRegistryHolder().key().location();

        for (String target : targets) {
            try {
                TargetEntry entry = parseTarget(target);
                if (entry.itemId.equals(itemId) && entry.matchesNbt(stack)) {
                    return true;
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to parse target entry '{}': {}", target, e.getMessage());
            }
        }

        return false;
    }

    private static TargetEntry parseTarget(String target) {
        if (target.startsWith("{") || target.startsWith("[")) {
            try {
                JsonElement json = GSON.fromJson(target, JsonElement.class);
                if (json instanceof JsonObject obj) {
                    String idStr = obj.get("item").getAsString();
                    ResourceLocation itemId = new ResourceLocation(idStr);

                    CompoundTag nbtData = null;
                    if (obj.has("nbt") && !obj.get("nbt").isJsonNull()) {
                        String nbtString = obj.get("nbt").getAsString();
                        nbtData = TagParser.parseTag(nbtString);
                    }

                    return new TargetEntry(itemId, nbtData);
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to parse JSON target '{}': {}", target, e.getMessage());
            }
            return null;
        }

        int nbtStart = target.indexOf('{');
        if (nbtStart > 0) {
            String idPart = target.substring(0, nbtStart).trim();
            String nbtPart = target.substring(nbtStart);

            try {
                CompoundTag nbtData = TagParser.parseTag(nbtPart);
                ResourceLocation itemId = new ResourceLocation(idPart);
                return new TargetEntry(itemId, nbtData);
            } catch (Exception e) {
                LOGGER.warn("Failed to parse NBT in target '{}': {}", target, e.getMessage());
            }
            return null;
        }

        return new TargetEntry(new ResourceLocation(target));
    }
}
