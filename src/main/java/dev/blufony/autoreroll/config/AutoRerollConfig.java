package dev.blufony.autoreroll.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.Arrays;
import java.util.List;

public class AutoRerollConfig {
    public static final ForgeConfigSpec.Builder CLIENT_BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec CLIENT_SPEC;

    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> TARGETS;
    public static final ForgeConfigSpec.IntValue MAX_REROLLS;
    public static final ForgeConfigSpec.IntValue PAUSE_BETWEEN_REROLLS_MS;

    static {
        CLIENT_BUILDER.push("auto_reroll");

        TARGETS = CLIENT_BUILDER
            .comment("List of target item ResourceLocations to auto-reroll for")
            .defineList("targets", () -> Arrays.asList(
                "the_vault:pyretic_focus",
                "the_vault:empowered_chaotic_focus",
                "the_vault:opportunistic_focus",
                "the_vault:resilient_focus",
                "the_vault:inscription",
                "the_vault:vorpal_focus",
                "the_vault:booster_pack"
            ), obj -> obj instanceof String);

        MAX_REROLLS = CLIENT_BUILDER
            .comment("Maximum number of rerolls before giving up")
            .defineInRange("maxRerolls", 100, 1, Integer.MAX_VALUE);

        PAUSE_BETWEEN_REROLLS_MS = CLIENT_BUILDER
            .comment("Pause between rerolls in milliseconds")
            .defineInRange("pauseBetweenRerollsMs", 100, 100, Integer.MAX_VALUE);

        CLIENT_BUILDER.pop();
        CLIENT_SPEC = CLIENT_BUILDER.build();
    }
}
