package dev.blufony.autoreroll.util;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

public class NotifyUtil {
    private static final float VOLUME = 0.75F;
    private static final float PITCH = 1.0F;
    
    public static void notifyPlayer(String message, SoundEvent sound) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        
        mc.execute(() -> {
            if (message != null) {
                mc.player.displayClientMessage(new TextComponent(message), true);
            }
            
            if (sound != null && mc.level != null) {
                mc.level.playSound(
                    mc.player,
                    mc.player.getX(),
                    mc.player.getY(),
                    mc.player.getZ(),
                    sound,
                    SoundSource.BLOCKS,
                    VOLUME,
                    PITCH
                );
            }
        });
    }
    
    public static void notifyPlayer(String message) {
        notifyPlayer(message, null);
    }
    
    public static void playSound(SoundEvent sound) {
        notifyPlayer(null, sound);
    }
}