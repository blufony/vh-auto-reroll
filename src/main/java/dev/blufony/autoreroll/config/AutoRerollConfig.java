package dev.blufony.autoreroll.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.Arrays;
import java.util.List;

public class AutoRerollConfig {
    public static final ForgeConfigSpec.Builder CLIENT_BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec CLIENT_SPEC;

    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> GENERAL_TARGETS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> BOOSTER_PACK_TARGETS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> INSCRIPTION_TARGETS;
    public static final ForgeConfigSpec.IntValue MAX_REROLLS;
    public static final ForgeConfigSpec.IntValue PAUSE_BETWEEN_REROLLS_MS;

    static {
        CLIENT_BUILDER.push("auto_reroll");

        GENERAL_TARGETS = CLIENT_BUILDER
            .comment("List of target items to auto-reroll for.")
            .defineList("general_targets", () -> Arrays.asList(
                "the_vault:pyretic_focus",
                "the_vault:empowered_chaotic_focus",
                "the_vault:opportunistic_focus",
                "the_vault:resilient_focus",
                "the_vault:vorpal_focus"
            ), obj -> obj instanceof String);

        BOOSTER_PACK_TARGETS = CLIENT_BUILDER
            .comment("List of target booster packs to auto-reroll for.")
            .defineList("booster_pack_targets", () -> Arrays.asList(
                "the_vault:booster_pack{id:'the_vault:resource_pack'}",
                "the_vault:booster_pack{id:'the_vault:evolution_pack'}",
                "the_vault:booster_pack{id:'the_vault:mega_evolution_pack'}",
                "the_vault:booster_pack{id:'the_vault:stat_pack'}",
                "the_vault:booster_pack{id:'the_vault:mega_stat_pack'}",
                "the_vault:booster_pack{id:'the_vault:mix_pack'}",
                "the_vault:booster_pack{id:'the_vault:mega_mix_pack'}",
                "the_vault:booster_pack{id:'the_vault:arcane_pack'}",
                "the_vault:booster_pack{id:'the_vault:mega_arcane_pack'}"
            ), obj -> obj instanceof String);

        INSCRIPTION_TARGETS = CLIENT_BUILDER
            .comment("List of target inscriptions to auto-reroll for.")
            .defineList("inscription_targets", () -> Arrays.asList(
                "the_vault:inscription{data:{entries:[{color:15769088,count:1,pool:'the_vault:vault/rooms/omega/mine'}],isSuper:0b,model:15,size:10}}",
                "the_vault:inscription{data:{entries:[{color:15769088,count:1,pool:'the_vault:vault/rooms/omega/pixel'}],isSuper:0b,model:15,size:10}}",
                "the_vault:inscription{data:{entries:[{color:15769088,count:1,pool:'the_vault:vault/rooms/omega/mush_room'}],isSuper:0b,model:15,size:10}}",
                "the_vault:inscription{data:{entries:[{color:15769088,count:1,pool:'the_vault:vault/rooms/omega/library'}],isSuper:0b,model:15,size:10}}",
                "the_vault:inscription{data:{entries:[{color:15769088,count:1,pool:'the_vault:vault/rooms/omega/blacksmith'}],isSuper:0b,model:15,size:10}}",
                "the_vault:inscription{data:{entries:[{color:15769088,count:1,pool:'the_vault:vault/rooms/omega/cove'}],isSuper:0b,model:15,size:10}}",
                "the_vault:inscription{data:{entries:[{color:15769088,count:1,pool:'the_vault:vault/rooms/omega/painting'}],isSuper:0b,model:15,size:10}}"
            ), obj -> obj instanceof String);

        MAX_REROLLS = CLIENT_BUILDER
            .comment("Maximum number of rerolls before giving up")
            .defineInRange("maxRerolls", 100, 1, Integer.MAX_VALUE);

        PAUSE_BETWEEN_REROLLS_MS = CLIENT_BUILDER
            .comment("Pause between rerolls in milliseconds (0 = disabled, recommended for normal use)")
            .defineInRange("pauseBetweenRerollsMs", 0, 0, Integer.MAX_VALUE);

        CLIENT_BUILDER.pop();
        CLIENT_SPEC = CLIENT_BUILDER.build();
    }
}
