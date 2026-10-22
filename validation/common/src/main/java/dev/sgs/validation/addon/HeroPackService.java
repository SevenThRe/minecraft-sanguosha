package dev.sgs.validation.addon;
import dev.sgs.core.addon.*;
import dev.sgs.validation.ProbeMod;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
/** Administrator-installed world-local packs; no client can upload or enable code. */
public final class HeroPackService {
    private static final Map<MinecraftServer,HeroPackLoader.Loaded> LOADED=new IdentityHashMap<>();
    private HeroPackService(){}
    public static void start(MinecraftServer server){
        var root=server.getWorldPath(LevelResource.ROOT).resolve("sgs-devkit");
        try{var loaded=HeroPackLoader.loadConfigured(root);LOADED.put(server,loaded);AddonRuntime.installForServer(loaded.catalog());
            ProbeMod.LOG.info("SGS_HERO_PACKS_LOADED packs={} heroes={}",loaded.receipts().size(),loaded.catalog().heroes().size());
        }catch(Exception error){throw new IllegalStateException("Invalid server hero-pack configuration: "+root,error);}
    }
    public static void stop(){AddonRuntime.resetAfterServerStopped();for(var l:LOADED.values())try{l.close();}catch(Exception e){ProbeMod.LOG.warn("Hero pack close failed",e);}LOADED.clear();}
}
