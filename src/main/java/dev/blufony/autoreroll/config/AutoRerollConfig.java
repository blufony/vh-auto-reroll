package dev.blufony.autoreroll.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class AutoRerollConfig {
    public static final ForgeConfigSpec.Builder CLIENT_BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec CLIENT_SPEC;

    public static final ForgeConfigSpec.IntValue MAX_REROLLS;
    public static final ForgeConfigSpec.BooleanValue SEARCH_ALL_SLOTS;

    static {
        CLIENT_BUILDER.push("vh_auto_reroll");

        MAX_REROLLS = CLIENT_BUILDER
            .comment("Maximum number of rerolls before giving up")
            .defineInRange("maxRerolls", 1000, 1, Integer.MAX_VALUE);

        SEARCH_ALL_SLOTS = CLIENT_BUILDER
            .comment("Search all 3 trade slots instead of only the center slot")
            .define("searchAllSlots", true);

        CLIENT_BUILDER.pop();
        CLIENT_SPEC = CLIENT_BUILDER.build();
    }
}
