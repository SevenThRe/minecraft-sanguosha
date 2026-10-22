package dev.sgs.validation.social;

import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.context.CommandContext;
import dev.sgs.core.social.ModerationLedger;
import net.blay09.mods.balm.api.Balm;
import net.minecraft.commands.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

/** Local administrative decisions. Never sends reports to an external service. */
public final class ModerationService {
    public static String status(MinecraftServer server,long report){
        var e=SocialData.get(server).moderation().decision(report);
        if(e==null)return "待处理";
        String suffix="";
        if(e.action()==ModerationLedger.Action.SUSPEND){
            long now=System.currentTimeMillis();
            suffix=e.until()<=now?"（已到期）":SocialData.get(server).moderation().suspendedUntil(e.target(),now)==0?"（已解除）":"（生效中）";
        }
        return label(e.action())+suffix+"："+e.note();
    }
    private static String label(ModerationLedger.Action action){return switch(action){case DISMISS->"已驳回";case WARN->"已警告";case SUSPEND->"已禁赛";case REVOKE->"已解除禁赛";};}
    public static String accessDenied(ServerPlayer player){
        try{
            long until=SocialData.get(player.getServer()).moderation().suspendedUntil(player.getUUID(),System.currentTimeMillis());
            return until==0?null:"三国杀禁赛中，剩余约 "+Math.max(1,(until-System.currentTimeMillis()+59999)/60000)+" 分钟；/sgs reports 查看";
        }catch(RuntimeException error){return "举报管理数据暂不可用，请联系管理员";}
    }
    public static void onLogin(ServerPlayer player){
        try{
            String denied=accessDenied(player);if(denied!=null)player.sendSystemMessage(Component.literal(denied));
            var personal=SocialData.get(player.getServer()).moderation().entries().stream()
                .filter(e->e.target().equals(player.getUUID()) && e.action()!=ModerationLedger.Action.DISMISS).toList();
            if(!personal.isEmpty()){
                var last=personal.getLast();player.sendSystemMessage(Component.literal("三国杀处理记录："+label(last.action())+"，"+last.note()+"。/sgs reports 查看"));
            }
            var reports=FeedbackData.get(player.getServer()).ledger().reports().stream().filter(r->r.reporter().equals(player.getUUID())).toList();
            long processed=reports.stream().filter(r->SocialData.get(player.getServer()).moderation().decision(r.id())!=null).count();
            if(processed>0)player.sendSystemMessage(Component.literal("你的举报已有 "+processed+" 条处理结果，/sgs reports 查看"));
        }catch(RuntimeException error){dev.sgs.validation.ProbeMod.LOG.error("Moderation login summary failed",error);}
    }
    public static void initialize(){
        Balm.getEvents().onEvent(net.blay09.mods.balm.api.event.PlayerLoginEvent.class,e->onLogin(e.getPlayer()));
        Balm.getCommands().register(d->{
            d.register(Commands.literal("sgs").then(Commands.literal("reports")
                .executes(c->mine(c,1)).then(Commands.argument("page",IntegerArgumentType.integer(1)).executes(c->mine(c,IntegerArgumentType.getInteger(c,"page"))))));
            var review=Commands.literal("review");
            var id=Commands.argument("id",LongArgumentType.longArg(1));
            for(var action:List.of(ModerationLedger.Action.DISMISS,ModerationLedger.Action.WARN,ModerationLedger.Action.SUSPEND)){
                var node=Commands.literal(action.name().toLowerCase(Locale.ROOT));
                var note=Commands.argument("note",StringArgumentType.greedyString()).executes(c->review(c,action));
                if(action==ModerationLedger.Action.SUSPEND)node.then(Commands.argument("minutes",LongArgumentType.longArg(1,525600)).then(note));
                else node.then(note);
                id.then(node);
            }
            review.then(id);
            d.register(Commands.literal("sgsfeedback").requires(s->s.hasPermission(2))
                .then(review)
                .then(Commands.literal("pending").executes(c->pending(c)))
                .then(Commands.literal("report").then(Commands.argument("id",LongArgumentType.longArg(1)).executes(c->details(c))))
                .then(Commands.literal("revoke").then(Commands.argument("uuid",StringArgumentType.word())
                    .then(Commands.argument("note",StringArgumentType.greedyString()).executes(c->revoke(c))))));
        });
    }
    private static int mine(CommandContext<CommandSourceStack> c,int page) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player=c.getSource().getPlayerOrException();var server=player.getServer();
        try{
            var rows=FeedbackData.get(server).ledger().reports().stream().filter(r->r.reporter().equals(player.getUUID()))
                .sorted(Comparator.comparingLong(dev.sgs.core.social.MatchLedger.Report::id).reversed()).toList();
            int pages=Math.max(1,(rows.size()+9)/10);if(page>pages)throw new IllegalArgumentException("页码超出范围，共 "+pages+" 页");
            player.sendSystemMessage(Component.literal("我的举报 "+page+"/"+pages+"（共 "+rows.size()+" 条）"));
            for(var r:rows.subList((page-1)*10,Math.min(rows.size(),page*10)))player.sendSystemMessage(Component.literal(
                "#"+r.id()+" 对局 "+r.matchId()+" 座位 "+(r.targetSeat()+1)+" · "+status(server,r.id())));
            var moderation=SocialData.get(server).moderation();
            var personal=moderation.entries().stream().filter(e->e.target().equals(player.getUUID()) && e.action()!=ModerationLedger.Action.DISMISS).toList();
            for(var e:personal.subList(Math.max(0,personal.size()-3),personal.size()))player.sendSystemMessage(Component.literal(label(e.action())+"："+e.note()));
            var blocked=accessDenied(player);if(blocked!=null)player.sendSystemMessage(Component.literal(blocked));
            return rows.size();
        }catch(RuntimeException error){return fail(c,error);}
    }
    private static int pending(CommandContext<CommandSourceStack> c){
        try{
            var server=c.getSource().getServer();var data=SocialData.get(server);
            var rows=FeedbackData.get(server).ledger().reports().stream().filter(r->data.moderation().decision(r.id())==null).toList();
            say(c,"待处理举报 "+rows.size()+" 条，按最早顺序显示前20条：");
            for(var r:rows.stream().limit(20).toList())say(c,"#"+r.id()+" 对局="+r.matchId()+" 被举报者="+r.target()+" 原因="+r.reason()+" 说明="+r.detail());
            return rows.size();
        }catch(RuntimeException error){return fail(c,error);}
    }
    private static int details(CommandContext<CommandSourceStack> c){
        try{
            var server=c.getSource().getServer();long id=LongArgumentType.getLong(c,"id");
            var ledger=FeedbackData.get(server).ledger();var r=ledger.reports().stream().filter(row->row.id()==id).findFirst().orElseThrow(()->new IllegalArgumentException("举报不存在"));
            say(c,"#"+id+" 对局="+r.matchId()+" 举报者="+r.reporter()+" 被举报者="+r.target()+" 座位="+(r.targetSeat()+1));
            say(c,"原因="+r.reason()+" 说明="+r.detail()+" 时间="+r.createdAt()+" 状态="+status(server,id));
            var match=ledger.match(r.matchId());say(c,"对局人数="+match.capacity()+" 结果="+match.outcome()+" 结束时间="+match.completedAt());
            for(var e:SocialData.get(server).moderation().entries())if(e.target().equals(r.target()))say(c,"处理记录 #"+e.id()+" 举报="+e.reportId()+" "+label(e.action())+" 操作者="+e.moderator()+" 时间="+e.at()+" 截止="+e.until()+" "+e.note());
            return 1;
        }catch(RuntimeException error){return fail(c,error);}
    }
    private static String moderator(CommandContext<CommandSourceStack> c){return c.getSource().getEntity() instanceof ServerPlayer p?p.getUUID().toString():"console:"+c.getSource().getTextName();}
    private static int review(CommandContext<CommandSourceStack> c,ModerationLedger.Action action){
        try{
            var server=c.getSource().getServer();var data=SocialData.get(server);long id=LongArgumentType.getLong(c,"id");
            var e=data.moderation().review(FeedbackData.get(server).ledger(),id,moderator(c),action,StringArgumentType.getString(c,"note"),System.currentTimeMillis(),
                action==ModerationLedger.Action.SUSPEND?LongArgumentType.getLong(c,"minutes"):0);
            data.persist(server);say(c,"举报 #"+id+" "+label(action));
            var report=FeedbackData.get(server).ledger().reports().stream().filter(r->r.id()==id).findFirst().orElseThrow();
            notify(server,report.reporter(),"举报 #"+id+" "+label(action)+"："+e.note());
            if(action!=ModerationLedger.Action.DISMISS)notify(server,e.target(),"三国杀："+label(action)+"，"+e.note()+"。/sgs reports 查看");
            return 1;
        }catch(RuntimeException error){return fail(c,error);}
    }
    private static int revoke(CommandContext<CommandSourceStack> c){
        try{
            var server=c.getSource().getServer();var data=SocialData.get(server);var player=UUID.fromString(StringArgumentType.getString(c,"uuid"));
            data.moderation().revoke(player,moderator(c),StringArgumentType.getString(c,"note"),System.currentTimeMillis());data.persist(server);
            say(c,"已解除 "+player+" 的三国杀禁赛");notify(server,player,"三国杀禁赛已解除");return 1;
        }catch(RuntimeException error){return fail(c,error);}
    }
    private static void notify(MinecraftServer server,UUID id,String message){var p=server.getPlayerList().getPlayer(id);if(p!=null)p.sendSystemMessage(Component.literal(message));}
    private static void say(CommandContext<CommandSourceStack> c,String message){c.getSource().sendSuccess(()->Component.literal(message),false);}
    private static int fail(CommandContext<CommandSourceStack> c,RuntimeException error){
        c.getSource().sendFailure(Component.literal(error instanceof IllegalArgumentException?error.getMessage():"社交数据暂不可用，原数据保留"));
        if(!(error instanceof IllegalArgumentException))dev.sgs.validation.ProbeMod.LOG.error("Moderation operation failed",error);return 0;
    }
}
