package dev.blufony.autoreroll.util;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;

public class ItemMatcher {
    private static final Logger LOGGER = LogManager.getLogger(ItemMatcher.class);
    
    private static Method checkFilterMethod;
    private static boolean initialized = false;
    private static boolean available = false;
    
    private static void init() {
        if (initialized) return;
        initialized = true;
        
        try {
            Class<?> vfTestsClass = Class.forName("net.joseph.vaultfilters.VFTests");
            checkFilterMethod = vfTestsClass.getMethod(
                "checkFilter", 
                ItemStack.class, Object.class, boolean.class, Level.class
            );
            available = true;
            LOGGER.info("Vault-Filters integration loaded successfully");
        } catch (ClassNotFoundException e) {
            LOGGER.warn("Vault-Filters mod not found, filter matching unavailable");
        } catch (NoSuchMethodException e) {
            LOGGER.warn("Vault-Filters checkFilter method not found: {}", e.getMessage());
        }
    }
    
    public static boolean matchesWithFilter(ItemStack stack, ItemStack filterStack, Level level) {
        init();
        
        if (!available || checkFilterMethod == null) {
            return false;
        }
        
        try {
            Object result = checkFilterMethod.invoke(null, stack, filterStack, true, level);
            return (Boolean) result;
        } catch (Exception e) {
            LOGGER.warn("Failed to invoke VFTests.checkFilter: {}", e.getMessage());
            return false;
        }
    }
}
