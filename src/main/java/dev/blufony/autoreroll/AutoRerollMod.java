package dev.blufony.autoreroll;

import dev.blufony.autoreroll.config.AutoRerollConfig;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(AutoRerollMod.MOD_ID)
public class AutoRerollMod {
    public static final String MOD_ID = "auto_reroll";
    private static final Logger LOGGER = LogManager.getLogger();
    
    public AutoRerollMod() {
        LOGGER.info("Auto Reroll Mod - Constructor called");
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
        
        ModLoadingContext.get()
            .registerConfig(ModConfig.Type.CLIENT, AutoRerollConfig.CLIENT_SPEC, "auto_reroll-client.toml");
        
        LOGGER.info("Auto Reroll mod loaded");
    }
    
    private void setup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            LOGGER.info("Auto Reroll mod initialization work completed");
        });
        LOGGER.info("Auto Reroll mod - Setup scheduled");
    }
}
