package dev.sgs.validation.social;

import dev.sgs.core.Identity;
import dev.sgs.core.engine.GameOutcome;
import dev.sgs.core.match.dto.MatchViews.MatchSnapshot;
import dev.sgs.core.social.MatchLedger;
import dev.sgs.validation.ProbeMod;
import java.util.*;
import net.blay09.mods.balm.api.Balm;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** All ledger mutation and world IO stay on the Minecraft server thread. */
public final class FeedbackService {
  private record RequestWindow(net.minecraft.server.network.ServerGamePacketListenerImpl connection,long highest){}
  private static final Map<MinecraftServer,Map<UUID,RequestWindow>> WINDOWS=new IdentityHashMap<>();
  private FeedbackService(){}
  public static long begin(MinecraftServer server,int capacity,List<UUID> humans){
    if(!server.isSameThread())throw new IllegalStateException("match ledger needs server thread");
    var data=FeedbackData.get(server);long id=data.ledger().allocateMatchId(System.currentTimeMillis());
    data.ledger().begin(id,capacity,humans);data.persist(server);
    if(Boolean.getBoolean("sgs.giftSmoke"))GiftService.seedSmoke(server,id,humans);
    return id;
  }
  /** Called from the same server-thread delivery queue as the real engine snapshots. */
  public static void observe(MinecraftServer server,long matchId,UUID viewer,MatchSnapshot snapshot){
    if(!server.isSameThread())throw new IllegalStateException("snapshot ledger needs server thread");
    if(snapshot.status()==0 || snapshot.viewerSeat()<0)return;
    var data=FeedbackData.get(server);var ledger=data.ledger();var match=ledger.match(matchId);
    if(match==null)throw new IllegalStateException("match was not registered");
    if(!match.seats().containsKey(viewer)){
      ledger.assignSeat(matchId,viewer,snapshot.viewerSeat());data.setDirty();
      if(ledger.match(matchId).seats().size()==match.humans().size())data.persist(server);
    }
    if(snapshot.status()!=2)return;
    if(snapshot.outcomeOrdinal()<0 || snapshot.outcomeOrdinal()>=GameOutcome.values().length)throw new IllegalArgumentException("invalid engine outcome");
    if(ledger.match(matchId).outcome()!=GameOutcome.ONGOING)return;
    var seats=new ArrayList<MatchLedger.FinishSeat>();
    for(var seat:snapshot.seats()){
      var identity=seat.identityOrdinal()<0?Identity.LOYALIST:Identity.values()[seat.identityOrdinal()];
      seats.add(new MatchLedger.FinishSeat(seat.seat(),seat.human(),identity,seat.teamId(),seat.alive()));
    }
    var awarded=ledger.finish(matchId,GameOutcome.values()[snapshot.outcomeOrdinal()],seats,System.currentTimeMillis());
    data.persist(server);
    ProbeMod.LOG.info("SGS_SETTLEMENT_SAVED match={} humans={} rule={}",matchId,awarded.size(),MatchLedger.REWARD_RULE);
    for(var receipt:awarded){var online=server.getPlayerList().getPlayer(receipt.player());
      dev.sgs.validation.collection.CollectionService.reward(server,receipt,data.ledger().match(receipt.matchId()));
      if(online!=null)send(online,new FeedbackNetwork.Result(0,"SETTLEMENT",true,"OK",matchId,FeedbackNetwork.RewardView.of(receipt)));
    }
  }
  public static void onLogin(ServerPlayer player){
    var server=player.getServer();if(!server.isSameThread()){server.execute(()->onLogin(player));return;}
    try{var data=FeedbackData.get(server);for(var receipt:data.ledger().snapshot().receipts())if(receipt.player().equals(player.getUUID()))
      dev.sgs.validation.collection.CollectionService.reward(server,receipt,data.ledger().match(receipt.matchId()));
    }catch(RuntimeException error){ProbeMod.LOG.error("SGS ledger load blocked; preserving world data",error);}
  }
  public static void submit(ServerPlayer player,FeedbackNetwork.Intent packet){
    var server=player.getServer();if(!server.isSameThread()){server.execute(()->submit(player,packet));return;}
    long requestId=0,matchId=0;String kind="INVALID";
    try{
      if(server.getPlayerList().getPlayer(player.getUUID())!=player || player.connection==null)return;
      var command=FeedbackNetwork.JSON.fromJson(packet.json(),FeedbackNetwork.Command.class);
      if(command==null || command.requestId()<=0 || command.matchId()<=0 || command.kind()==null
          || (!command.kind().equals("REPORT") && !command.kind().equals("QUERY_RESULT")))throw new IllegalArgumentException("invalid request");
      requestId=command.requestId();matchId=command.matchId();kind=command.kind();
      var windows=WINDOWS.computeIfAbsent(server,ignored->new HashMap<>());var old=windows.get(player.getUUID());
      if(old!=null && old.connection()==player.connection && requestId<=old.highest()){
        send(player,new FeedbackNetwork.Result(requestId,kind,false,"REQUEST_REPLAY",matchId,null));return;
      }
      windows.put(player.getUUID(),new RequestWindow(player.connection,requestId));
      var data=FeedbackData.get(server);MatchLedger.Reply reply;
      if(kind.equals("REPORT")){
        reply=data.ledger().report(player.getUUID(),matchId,command.targetSeat(),command.reason(),command.detail(),System.currentTimeMillis());
        if(reply.accepted()){data.persist(server);player.sendSystemMessage(Component.literal("举报已提交，可用 /sgs reports 查看处理进度"));ProbeMod.LOG.info("SGS_REPORT_SAVED match={} reporter={} target_seat={}",matchId,player.getUUID(),command.targetSeat());}
      }else reply=data.ledger().result(player.getUUID(),matchId);
      send(player,new FeedbackNetwork.Result(requestId,kind,reply.accepted(),reply.reason(),matchId,FeedbackNetwork.RewardView.of(reply.receipt())));
    }catch(IllegalArgumentException error){send(player,new FeedbackNetwork.Result(requestId,kind,false,"INVALID_REQUEST",matchId,null));}
    catch(RuntimeException error){ProbeMod.LOG.error("SGS local feedback ledger failed",error);send(player,new FeedbackNetwork.Result(requestId,kind,false,"DATA_UNAVAILABLE",matchId,null));}
  }
  private static void send(ServerPlayer player,FeedbackNetwork.Result result){Balm.getNetworking().sendTo(player,new FeedbackNetwork.Response(FeedbackNetwork.JSON.toJson(result)));}
  public static void stopAll(){WINDOWS.clear();}
  public static void initializeCommands(){
    Balm.getCommands().register(dispatcher->dispatcher.register(Commands.literal("sgsfeedback").requires(source->source.hasPermission(2))
        .then(Commands.literal("reports").executes(context->{
          var list=FeedbackData.get(context.getSource().getServer()).ledger().reports();
          context.getSource().sendSuccess(()->Component.literal("SGS 本地举报记录 "+list.size()+"；最近20条："),false);
          for(var report:list.subList(Math.max(0,list.size()-20),list.size()))context.getSource().sendSuccess(()->Component.literal(
              "#"+report.id()+" match="+report.matchId()+" reporter="+report.reporter()+" target="+report.target()+" reason="+report.reason()+" detail="+report.detail()+" status="+ModerationService.status(context.getSource().getServer(),report.id())),false);
          return list.size();
        }))
        .then(Commands.literal("account").then(Commands.argument("uuid",com.mojang.brigadier.arguments.StringArgumentType.word()).executes(context->{
          try{var id=UUID.fromString(com.mojang.brigadier.arguments.StringArgumentType.getString(context,"uuid"));var account=FeedbackData.get(context.getSource().getServer()).ledger().account(id);
            context.getSource().sendSuccess(()->Component.literal(id+" XP="+account.xp()+" 荣誉="+account.honor()),false);return 1;
          }catch(IllegalArgumentException error){context.getSource().sendFailure(Component.literal("无效UUID"));return 0;}
        })))));
  }
}
