package dev.sgs.validation.client.mixin;

import dev.sgs.validation.client.SgsMusicPolicy;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.MusicManager;
import net.minecraft.sounds.Music;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Stop an already playing vanilla track and reject new ones throughout the SGS session. */
@Mixin(MusicManager.class)
public abstract class VanillaMusicMixin {
    @Shadow private SoundInstance currentMusic;
    @Shadow public abstract void stopPlaying();

    @Inject(method="tick",at=@At("HEAD"))
    private void sgs$stopVanillaMusic(CallbackInfo ci) {
        if(SgsMusicPolicy.active() && currentMusic!=null
            && currentMusic.getLocation().getNamespace().equals("minecraft"))stopPlaying();
    }

    @Inject(method="startPlaying",at=@At("HEAD"),cancellable=true)
    private void sgs$preventVanillaMusic(Music music,CallbackInfo ci) {
        if(SgsMusicPolicy.active() && music.getEvent().value().getLocation().getNamespace().equals("minecraft"))
            ci.cancel();
    }
}
