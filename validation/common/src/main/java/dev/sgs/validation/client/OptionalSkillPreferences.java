package dev.sgs.validation.client;

import java.nio.file.*;
import java.util.*;
import dev.sgs.core.engine.PromptType;
import dev.sgs.core.match.MatchAction;
import dev.sgs.validation.match.MatchNetwork;
import net.minecraft.client.Minecraft;

/** Auto-invoke only; target choice always remains with the player. */
public final class OptionalSkillPreferences {
    private static final Set<String> SUPPORTED=Set.of("jieming","leiji","fangzhu");
    private static final Properties values=new Properties();
    private static boolean loaded;
    private static String answered="";
    private static Path file(){return Minecraft.getInstance().gameDirectory.toPath().resolve("config/sgs/optional-skills.properties");}
    public static boolean supports(String id){return SUPPORTED.contains(id);}
    public static boolean enabled(String id){
        if(!loaded){loaded=true;if(Files.exists(file()))try(var in=Files.newInputStream(file())){values.load(in);}catch(java.io.IOException e){dev.sgs.validation.ProbeMod.LOG.warn("Cannot read optional skill preferences",e);}}
        return supports(id)&&Boolean.parseBoolean(values.getProperty(id,"false"));
    }
    public static void toggle(String id){
        if(!supports(id))return;values.setProperty(id,Boolean.toString(!enabled(id)));
        try{Files.createDirectories(file().getParent());try(var out=Files.newOutputStream(file())){values.store(out,"Auto-invoke only; targets remain manual");}}
        catch(java.io.IOException e){dev.sgs.validation.ProbeMod.LOG.warn("Cannot save optional skill preferences",e);}
        MatchClient.autoInvokeOptionalSkill();
    }
    static void accept(MatchNetwork.View view){
        var s=view.snapshot();var p=s.prompt();
        if(s.status()!=1||p==null||p.typeOrdinal()!=PromptType.SKILL_INVOKE.ordinal()||p.askerSeat()!=s.viewerSeat()||!enabled(p.skillId()))return;
        if(s.seats().stream().anyMatch(seat->seat.self()&&seat.control().automatic()))return;
        String key=view.matchId()+":"+view.connectionEpoch()+":"+p.promptId();
        if(key.equals(answered))return;answered=key;
        MatchClient.send(MatchAction.promptResponse(MatchClient.nextNonce(),p.promptId(),true,-1,-1));
    }
}
