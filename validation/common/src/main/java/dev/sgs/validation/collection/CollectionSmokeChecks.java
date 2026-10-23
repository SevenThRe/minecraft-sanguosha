package dev.sgs.validation.collection;

import dev.sgs.core.collection.*;
import dev.sgs.validation.ProbeMod;
import java.nio.file.Files;
import java.util.*;
import net.minecraft.server.MinecraftServer;

/** Disposable real dedicated server: real command tree, permissions, world DB and config reload. */
public final class CollectionSmokeChecks {
    public static void run(MinecraftServer server) {
        var file=server.getServerDirectory().resolve("config/sgs/collection.properties");String original=null;
        try {
            original=Files.readString(file);if(!original.equals(CollectionPolicy.TEMPLATE))throw new IllegalStateException("Fresh collection config required");
            String configured=original.replace("enabled=false","enabled=true").replace("guaranteed_new_below=10","guaranteed_new_below=2")
                .replace("free.standard=true","free.standard=false").replace("market.hero_price=100","market.hero_price=10").replace("draw_pool=","draw_pool=caocao,liubei,guanyu");
            Files.writeString(file,configured);
            var commands=server.getCommands().getDispatcher();var source=server.createCommandSourceStack();
            require(commands.execute("sgs reload",source)==1,"reload command");
            var id=UUID.fromString("8aeb5919-283e-4a72-ae27-444055905774");
            server.getProfileCache().add(new com.mojang.authlib.GameProfile(id,"CollectionProbe"));
            require(commands.execute("sgs admin give hero CollectionProbe caocao",source)==1,"hero admin grant");
            require(commands.execute("sgs admin give skin CollectionProbe caocao action",source)==1,"skin admin grant");
            require(commands.execute("sgs admin give tokens CollectionProbe 2",source)==1,"token admin grant");
            require(commands.execute("sgs admin give souls CollectionProbe 15",source)==1,"soul admin grant");
            try{commands.execute("sgs admin give hero CollectionProbe liubei",source.withPermission(0));throw new AssertionError("permission bypass");}
            catch(com.mojang.brigadier.exceptions.CommandSyntaxException expected){}
            var p=CollectionService.policy(server);var db=CollectionService.database(server);var first=new Random(){public int nextInt(int bound){return 0;}};
            var r1=db.apply("smoke-draw-1",id,"draw","",0,p,CollectionService.catalog(),first);require(!r1.duplicate() && !r1.hero().equals("caocao"),"new guarantee");
            var r2=db.apply("smoke-draw-2",id,"draw","",0,p,CollectionService.catalog(),first);require(r2.duplicate() && r2.soulsAward()==20,"duplicate conversion");
            db.apply("smoke-draw-2",id,"draw","",0,p,CollectionService.catalog(),first);
            String missing=p.pool(CollectionService.catalog()).stream().filter(h->!dbAccount(db,id).heroes().contains(h)).findFirst().orElseThrow();
            db.apply("smoke-buy",id,"buy",missing,0,p,CollectionService.catalog(),first);
            var a=db.account(id,0);require(a.tokens()==4 && a.souls()==25 && a.heroes().size()==3 && a.skins().contains("caocao/action"),"balances and skins");
            CollectionService.reopenForSmoke(server);require(CollectionService.database(server).account(id,0).equals(a),"world database reopen");
            require(CollectionService.ownedPools(server,List.of(id),CollectionService.catalog(),4).get(id).equals(a.heroes()),"match ownership bridge");
            Files.writeString(file,"enabled=invalid\n");require(commands.execute("sgs reload",source)==0,"invalid reload rejected");
            require(CollectionService.policy(server).equals(p),"old collection policy preserved");
            Files.writeString(file,original.replace("enabled=true","enabled=false"));require(commands.execute("sgs reload",source)==1,"free-mode reload");
            require(CollectionService.ownedPools(server,List.of(id),CollectionService.catalog(),4).isEmpty(),"free-mode pool unrestricted");
            require(CollectionService.database(server).account(id,0).equals(a),"disable preserves assets");
            Files.writeString(file,original);require(commands.execute("sgs reload",source)==1,"default earning reload");
            var beginner=new UUID(9923,1);
            require(CollectionService.ownedPools(server,List.of(beginner),CollectionService.catalog(),8).get(beginner).contains("caocao"),"new player standard roster");
            long now=System.currentTimeMillis();
            var match=new dev.sgs.core.social.MatchLedger.MatchRecord(now-120000,4,List.of(id),Map.of(id,0),dev.sgs.core.engine.GameOutcome.TEAM_A_WIN,now);
            var receipt=new dev.sgs.core.social.MatchLedger.Receipt(match.id(),id,false,20,0,20,0,match.id()+":"+id,dev.sgs.core.social.MatchLedger.REWARD_RULE);
            CollectionService.reward(server,receipt,match);CollectionService.reward(server,receipt,match);
            require(CollectionService.database(server).account(id,0).tokens()==a.tokens()+1,"single losing-match payment");
            require(CollectionService.database(server).account(id,0).souls()==a.souls()+3,"loser soul payment");
            require(commands.execute("sgs admin take souls CollectionProbe 3",source)==1,"admin debit command");
            require(commands.execute("sgs admin balance CollectionProbe",source)==1,"admin balance command");
            try{commands.execute("sgs admin take tokens CollectionProbe 1",source.withPermission(0));throw new AssertionError("debit permission bypass");}
            catch(com.mojang.brigadier.exceptions.CommandSyntaxException expected){}
            require(commands.execute("sgs admin take tokens CollectionProbe 999999",source)==0,"overdraft rejected");
            CollectionService.reopenForSmoke(server);CollectionService.reward(server,receipt,match);
            require(CollectionService.database(server).account(id,0).souls()==a.souls(),"reward replay after restart does not repay");
            require(CollectionService.database(server).progress(id,now,CollectionService.policy(server).rewards()).daily()==1,"task progress persisted");
            Files.writeString(file,original+"\ntasks.daily.matches=0\n");require(commands.execute("sgs reload",source)==0,"invalid task reload rejected");
            require(CollectionService.policy(server).rewards().daily().matches()==3,"old task policy preserved");
            ProbeMod.LOG.info("SGS_COLLECTION_SMOKE_PASS admin=true permissions=true guarantee=true duplicate=true purchase=true reopen=true ownership=true reload=true earning=true tasks=true debit=true starter=true");
        }catch(Exception | AssertionError e){ProbeMod.LOG.error("SGS_COLLECTION_SMOKE_FAIL",e);}
        finally{if(original!=null)try{Files.writeString(file,original);}catch(Exception e){ProbeMod.LOG.error("Collection smoke restore failed",e);}server.halt(false);}
    }
    private static CollectionAccount dbAccount(CollectionDatabase db,UUID player){try{return db.account(player,0);}catch(Exception e){throw new IllegalStateException(e);}}
    private static void require(boolean pass,String message){if(!pass)throw new IllegalStateException(message);}
}
