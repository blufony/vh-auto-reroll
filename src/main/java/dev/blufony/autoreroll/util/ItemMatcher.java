package dev.blufony.autoreroll.util;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class ItemMatcher {
    public static boolean matches(ItemStack stack, List<ResourceLocation> targets) {
        ResourceLocation itemId = stack.getItem().builtInRegistryHolder().key().location();
        return targets.contains(itemId);
    }
}
