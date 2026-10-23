package dev.sgs.validation.client;

import dev.sgs.validation.ProbeMod;
import net.blay09.mods.balm.api.Balm;
import net.blay09.mods.balm.api.event.TickPhase;
import net.blay09.mods.balm.api.event.TickType;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.Musics;
import net.minecraft.sounds.SoundSource;

/** Opt-in verification of real MusicManager injection, not an audio-output/subjective listening test. */
final class MusicSmokeChecks {
    private static boolean passed;
    static void initialize(){
        if(Boolean.getBoolean("sgs.musicSmoke"))Balm.getEvents().onTickEvent(TickType.Client,TickPhase.End,MusicSmokeChecks::tick);
    }
    private static void tick(Minecraft client){
        if(passed || !SgsMusicPolicy.active())return;
        try{
            float music=client.options.getSoundSourceVolume(SoundSource.MUSIC);
            float master=client.options.getSoundSourceVolume(SoundSource.MASTER);
            client.getMusicManager().startPlaying(Musics.GAME);
            if(client.getMusicManager().isPlayingMusic(Musics.GAME))throw new IllegalStateException("vanilla game music started during SGS");
            client.getMusicManager().startPlaying(Musics.MENU);
            if(client.getMusicManager().isPlayingMusic(Musics.MENU))throw new IllegalStateException("vanilla menu music started during SGS");
            if(music!=client.options.getSoundSourceVolume(SoundSource.MUSIC) || master!=client.options.getSoundSourceVolume(SoundSource.MASTER))
                throw new IllegalStateException("music policy mutated sound settings");
            passed=true;ProbeMod.LOG.info("SGS_MUSIC_NATIVE_PASS game_blocked=true menu_blocked=true volume_unchanged=true");
        }catch(RuntimeException e){passed=true;ProbeMod.LOG.error("SGS_MUSIC_NATIVE_FAIL",e);client.stop();}
    }
}
