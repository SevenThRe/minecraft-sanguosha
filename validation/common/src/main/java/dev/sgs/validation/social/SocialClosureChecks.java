package dev.sgs.validation.social;

import dev.sgs.validation.ProbeMod;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Opt-in disposable world checks, including a second real server process on the same world. */
public final class SocialClosureChecks {
    private static final UUID REPORTER=UUID.fromString("00000000-0000-0000-0000-000000000071"),TARGET=UUID.fromString("00000000-0000-0000-0000-000000000072");
    public static void run(MinecraftServer server){
        String phase=System.getProperty("sgs.socialClosurePhase","");if(phase.isEmpty())return;
        try{
            var data=SocialData.get(server);var ledger=FeedbackData.get(server).ledger();
            var commands=server.getCommands().getDispatcher();var source=server.createCommandSourceStack();
            if(phase.equals("seed")){
                require(ledger.reports().isEmpty() && data.moderation().entries().isEmpty(),"fresh world required");
                long id=FeedbackService.begin(server,4,List.of(REPORTER,TARGET));ledger.assignSeat(id,REPORTER,0);ledger.assignSeat(id,TARGET,1);
                require(ledger.report(REPORTER,id,1,"GRIEFING","重复拖延",System.currentTimeMillis()).accepted(),"report creation");FeedbackData.get(server).persist(server);
                require(commands.execute("sgsfeedback pending",source)==1,"pending command");
                try{commands.execute("sgsfeedback review 1 suspend 60 核实拖延",source.withPermission(0));throw new IllegalStateException("non-admin review accepted");}
                catch(com.mojang.brigadier.exceptions.CommandSyntaxException expected){}
                require(commands.execute("sgsfeedback review 1 suspend 60 核实拖延",source)==1,"review command");
                require(commands.execute("sgsfeedback review 1 warn 重复",source)==0,"duplicate decision");
                require(commands.execute("sgsfeedback pending",source)==0,"processed removed from queue");
                data.skins().select(TARGET,"caocao","legacy",false,Set.of());data.persist(server);
            }else if(phase.equals("restore")){
                require(ledger.reports().size()==1,"old report persisted");
                require(data.moderation().decision(1)!=null,"decision persisted");
                require(data.skins().selected(TARGET,"caocao",false,Set.of()).equals("legacy"),"skin persisted");
                var player=new ServerPlayer(server,server.overworld(),new com.mojang.authlib.GameProfile(TARGET,"SocialTarget"),net.minecraft.server.level.ClientInformation.createDefault());
                require(ModerationService.accessDenied(player)!=null,"ban blocks entry after restart");
                require(commands.execute("sgsfeedback report 1",source)==1,"audit details");
                require(commands.execute("sgsfeedback revoke "+TARGET+" 复核解除",source)==1,"revoke command");
                require(ModerationService.accessDenied(player)==null,"revocation restores entry");
                require(data.moderation().entries().size()==2,"append-only audit");
                require(commands.execute("sgsfeedback revoke "+TARGET+" 重复",source)==0,"duplicate revoke rejected");
            }else if(phase.equals("verify")){
                require(data.moderation().entries().size()==2 && data.moderation().suspendedUntil(TARGET,System.currentTimeMillis())==0,"revocation survives second restart");
                require(data.skins().selected(TARGET,"caocao",false,Set.of()).equals("legacy"),"skin preserved after moderation");
            }else throw new IllegalArgumentException("unknown phase");
            ProbeMod.LOG.info("SGS_SOCIAL_CLOSURE_PASS phase={}",phase);
        }catch(Throwable error){ProbeMod.LOG.error("SGS_SOCIAL_CLOSURE_FAIL phase="+phase,error);}
        finally{server.halt(false);}
    }
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
}
