package dev.blufony.autoreroll;

import dev.blufony.autoreroll.config.AutoRerollConfig;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(AutoRerollMod.MOD_ID)
public class AutoRerollMod {
    public static final String MOD_ID = "auto_reroll";
    
    public AutoRerollMod() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
        
        ModLoadingContext.get()
            .registerConfig(ModConfig.Type.CLIENT, AutoRerollConfig.CLIENT_SPEC, "auto_reroll-client.toml");
    }
    
    private void setup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
        });
    }
}
