package dev.sgs.validation.client;

import dev.sgs.validation.ProbeMod;
import dev.sgs.validation.lobby.LobbyNetwork;
import java.util.*;
import net.blay09.mods.balm.api.Balm;
import net.blay09.mods.balm.api.event.TickPhase;
import net.blay09.mods.balm.api.event.TickType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/** Two real connections drive native controls and assert server replies, not local draft state. */
public final class LobbyFlowSmokeChecks {
  private static final String ROLE=System.getProperty("sgs.lobbyFlowRole", "");
  private static int ticks,stage,after,heroOfferFrames;
  private static long roomId=-1,rawRequest;
  private static String peer="";
  private static boolean done;
  private static boolean warmed;
  private static boolean temporaryRoom;
  private static long modalRevision;
  public static void initialize(){if(!ROLE.isEmpty())Balm.getEvents().onTickEvent(TickType.Client,TickPhase.End,LobbyFlowSmokeChecks::tick);}
  private static void next(int value){stage=value;after=ticks+6;ProbeMod.LOG.info("SGS_LOBBY_FLOW_STAGE role={} stage={}",ROLE,stage);}
  private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
  private static void capture(Minecraft c,String name){Screenshot.grab(c.gameDirectory,"sgs-flow-"+ROLE+"-"+name+".png",c.getMainRenderTarget(),m->{});}
  private static void click(LobbyScreen s,String action){int[] b=s.smokeBounds(action);require(b[2]>0&&b[3]>0,"empty button "+action);s.smokeAction(action);}
  private static boolean control(LobbyScreen s,String action){try{s.smokeBounds(action);return true;}catch(IllegalArgumentException missing){return false;}}
  private static LobbyNetwork.RoomView own(LobbyNetwork.View v){return v.rooms().stream().filter(r->r.id()==v.ownRoom()).findFirst().orElseThrow();}
  private static boolean chat(LobbyNetwork.View v,String message){return v.chat().stream().anyMatch(line->line.endsWith(message));}
  private static void tick(Minecraft c){
    if(done)return;
    try {
      if(++ticks>12000)throw new IllegalStateException("timeout");
      if(c.player==null||c.level==null||ticks<after)return;
      if(stage==0){LobbyClient.open();next(1);return;}
      if(c.screen instanceof MatchScreen match){match(c,match);return;}
      if(!(c.screen instanceof LobbyScreen s)||s.smokeView()==null||s.smokePending())return;
      var v=s.smokeView();
      if(!warmed){warmed=true;c.getToasts().clear();after=ticks+20;return;}
      if(ROLE.equals("host"))host(c,s,v);else guest(c,s,v);
    }catch(Exception e){dev.sgs.validation.client.art.PlayerSkinRenderer.clearUvFixtures();done=true;ProbeMod.LOG.error("SGS_LOBBY_FLOW_FAIL role="+ROLE+" stage="+stage,e);}
  }
  private static void host(Minecraft c,LobbyScreen s,LobbyNetwork.View v){
    switch(stage){
      case 1 -> {capture(c,"home");click(s,"open_rooms");next(2);}
      case 2 -> {require(s.smokePage().equals("MODES"),"mode page missing");capture(c,"modes");click(s,"mode:eight");next(3);}
      case 3 -> {capture(c,"rooms");modalRevision=v.revision();click(s,"create");next(4);}
      case 4 -> {require(s.smokeModal().equals("SETTINGS"),"settings missing");s.smokeSetField("name","录像流程验证");s.smokeSetField("password","flow123");click(s,"turn");click(s,"response");click(s,"selection");click(s,"fixed");click(s,"allow_spectators");click(s,"double_renegade");next(5);}
      case 5 -> {if(v.revision()<=modalRevision)return;require(s.smokeModal().equals("SETTINGS"),"broadcast closed active editor");ProbeMod.LOG.info("SGS_LOBBY_FLOW_BROADCAST_EDITOR_PASS");capture(c,"settings");click(s,"ban_slot:0");next(6);}
      case 6 -> {s.smokeSetField("heroSearch","曹操");require(control(s,"ban:liubei"),"draft hero query changed button binding before apply");click(s,"hero_search");next(61);}
      case 61 -> {require(control(s,"ban:caocao")&&!control(s,"ban:liubei"),"hero search button targets not applied");s.smokeSetField("heroSearch","");click(s,"hero_search");next(62);}
      case 62 -> {click(s,"ban:liubei");ProbeMod.LOG.info("SGS_LOBBY_FLOW_HERO_SEARCH_BINDING_PASS");next(7);}
      case 7 -> {capture(c,"bans");click(s,"confirm_bans");next(8);}
      case 8 -> {click(s,"confirm_settings");next(9);}
      case 9 -> {if(!v.success()&&v.ownRoom()<0&&v.reason().contains("状态")&&s.smokeModal().equals("SETTINGS")){click(s,"confirm_settings");next(9);return;}require(v.success()&&v.ownRoom()>=0,"create rejected "+v.reason());var r=own(v);var q=r.settings();require(q.turnSeconds()==20&&q.responseSeconds()==20&&q.selectionSeconds()==45&&q.fixedSeats()&&q.doubleRenegade()&&q.allowSpectators()&&q.locked()&&q.bannedHeroes().equals(Set.of("liubei")),"server settings mismatch "+q);roomId=r.id();capture(c,"waiting");s.smokeSkinFixture(c.player.getUUID().toString(),false);next(90);}
      case 90 -> {String uuid=c.player.getUUID().toString();require(s.smokeSkinDescription(uuid).contains("TEST_UV64;model=wide"),"wide TEST UV fixture not installed");capture(c,"skin-TEST_UV64-wide");s.smokeSkinFixture(uuid,true);next(91);}
      case 91 -> {String uuid=c.player.getUUID().toString();require(s.smokeSkinDescription(uuid).contains("TEST_UV64;model=slim"),"slim TEST UV fixture not installed");capture(c,"skin-TEST_UV64-slim");s.smokeClearSkinFixture(uuid);next(92);}
      case 92 -> {String source=s.smokeSkinDescription(c.player.getUUID().toString());require(!source.contains("TEST_UV64"),"TEST UV fixture survived cleanup");capture(c,"skin-minecraft-cache-restored");ProbeMod.LOG.info("SGS_LOBBY_FLOW_SKIN_UV_FIXTURE_PASS wide=true slim=true restored={} online_custom_skin_verified=false",source);next(10);}
      case 10 -> {if(!chat(v,"GUEST_JOINED")||own(v).members()!=2||own(v).ready()!=1)return;peer=own(v).seats().stream().filter(a->a.kind()==1&&!a.owner()).findFirst().orElseThrow().uuid();require(own(v).seats().get(7).uuid().equals(peer),"guest seat7 lost");capture(c,"two-humans");click(s,"ai_fill");next(11);}
      case 11 -> {require(v.success()&&own(v).ai()==6&&own(v).members()==2&&own(v).seats().get(7).uuid().equals(peer),"fill replaced human");capture(c,"ai-filled");click(s,"ai_clear");next(12);}
      case 12 -> {require(v.success()&&own(v).ai()==0&&own(v).members()==2,"clear removed human");click(s,"seat:1");next(13);}
      case 13 -> {capture(c,"seat-menu");click(s,"ai_add:1");next(14);}
      case 14 -> {require(v.success()&&own(v).ai()==1,"single AI add failed");click(s,"ai_remove:1");next(15);}
      case 15 -> {require(v.success()&&own(v).ai()==0,"single AI remove failed");click(s,"seat:7");next(16);}
      case 16 -> {capture(c,"management");click(s,"kick:"+peer);next(17);}
      case 17 -> {require(v.success()&&own(v).members()==1,"kick failed");click(s,"invite:"+peer);next(18);}
      case 18 -> {require(v.success(),"invite rejected");click(s,"modal_close");next(19);}
      case 19 -> {if(own(v).members()!=2||!chat(v,"REJOINED"))return;click(s,"settings");next(20);}
      case 20 -> {s.smokeSetField("name","录像配置已保存");click(s,"allow_chat");next(21);}
      case 21 -> {capture(c,"edit-settings");click(s,"confirm_settings");next(22);}
      case 22 -> {require(v.success()&&!own(v).settings().allowChat()&&own(v).name().equals("录像配置已保存")&&own(v).settings().locked()&&own(v).ready()==0,"atomic setting update failed");next(23);}
      case 23 -> {if(own(v).members()!=1)return;click(s,"ai_fill");next(24);}
      case 24 -> {require(v.success()&&own(v).ai()==7,"final AI fill failed");click(s,"ready");next(25);}
      case 25 -> {require(v.success()&&v.ownReady(),"host ready failed");capture(c,"start-ready");click(s,"start");next(26);}
      default -> {}
    }
  }
  private static void guest(Minecraft c,LobbyScreen s,LobbyNetwork.View v){
    switch(stage){
      case 1 -> {click(s,"open_rooms");next(2);}
      case 2 -> {click(s,"mode:eight");next(3);}
      case 3 -> {if(!temporaryRoom){click(s,"create");next(100);return;}var found=v.rooms().stream().filter(r->r.name().equals("录像流程验证")).findFirst();if(found.isEmpty())return;roomId=found.get().id();s.smokeSetField("search","不匹配的查询");require(control(s,"join:"+roomId),"draft room query rebound before apply");click(s,"search");next(103);}
      case 100 -> {s.smokeSetField("name","广播编辑验证");click(s,"confirm_settings");next(101);}
      case 101 -> {require(v.ownRoom()>=0,"temporary room create failed");if(v.rooms().stream().anyMatch(r->r.name().equals("录像流程验证"))){click(s,"leave");next(102);return;}click(s,v.ownReady()?"unready":"ready");next(101);after=ticks+20;}
      case 102 -> {require(v.ownRoom()<0,"temporary room leave failed");temporaryRoom=true;next(3);}
      case 103 -> {require(!control(s,"join:"+roomId),"room search retained hidden room action");s.smokeSetField("search","");click(s,"search");next(104);}
      case 104 -> {require(control(s,"join:"+roomId),"room search did not restore action");ProbeMod.LOG.info("SGS_LOBBY_FLOW_ROOM_SEARCH_BINDING_PASS");click(s,"join:"+roomId);next(4);}
      case 4 -> {require(s.smokeModal().equals("PASSWORD"),"password modal missing");s.smokeSetField("joinPassword","wrong");click(s,"password_join");next(5);}
      case 5 -> {require(!v.success()&&v.ownRoom()<0&&v.reason().contains("密码"),"wrong password accepted "+v.reason());capture(c,"wrong-password");ProbeMod.LOG.info("SGS_LOBBY_FLOW_PASSWORD_REJECTED role=guest");s.smokeSetField("joinPassword","flow123");click(s,"password_join");next(6);}
      case 6 -> {require(v.success()&&v.ownRoom()==roomId,"password join failed");click(s,"seat:7");next(7);}
      case 7 -> {click(s,"sit:7");next(8);}
      case 8 -> {require(v.success()&&own(v).seats().get(7).uuid().equals(c.player.getUUID().toString()),"seat move failed");click(s,"chat_open");next(9);}
      case 9 -> {s.smokeSetField("chat","GUEST_JOINED");click(s,"chat_send");next(10);}
      case 10 -> {require(v.success()&&chat(v,"GUEST_JOINED"),"chat receipt missing");capture(c,"chat");click(s,"modal_close");next(11);}
      case 11 -> {click(s,"ready");next(12);}
      case 12 -> {if(v.ownRoom()>=0)return;require(s.smokePage().equals("ROOMS"),"kick did not return to rooms");next(13);}
      case 13 -> {if(v.inviteRoom()!=roomId)return;capture(c,"invitation");click(s,"accept");next(14);}
      case 14 -> {require(v.success()&&v.ownRoom()==roomId,"invitation failed password room");click(s,"chat_open");next(15);}
      case 15 -> {s.smokeSetField("chat","REJOINED");click(s,"chat_send");next(16);}
      case 16 -> {require(v.success()&&chat(v,"REJOINED"),"rejoin chat missing");click(s,"modal_close");next(17);}
      case 17 -> {if(own(v).settings().allowChat())return;rawRequest=LobbyClient.request(v.revision(),"chat","eight",0,"BLOCKED_MESSAGE");next(18);}
      case 18 -> {if(v.request()!=rawRequest)return;require(!v.success()&&v.reason().contains("不允许聊天")&&!chat(v,"BLOCKED_MESSAGE"),"chat gate bypassed");ProbeMod.LOG.info("SGS_LOBBY_FLOW_CHAT_REJECTED role=guest");capture(c,"chat-disabled");click(s,"leave");next(19);}
      case 19 -> {require(v.success()&&v.ownRoom()<0,"leave failed");next(20);}
      case 20 -> {var r=v.rooms().stream().filter(a->a.id()==roomId).findFirst().orElseThrow();if(!r.playing())return;require(r.settings().allowSpectators()&&r.settings().locked(),"watch rules lost");click(s,"spectate:"+roomId);next(21);}
      case 21 -> {s.smokeSetField("joinPassword","flow123");capture(c,"spectator-password");click(s,"password_join");next(22);}
      case 24 -> {require(v.ownRoom()<0,"spectator became member");capture(c,"returned-lobby");pass();}
      default -> {}
    }
  }
  private static void match(Minecraft c,MatchScreen s){
    var snapshot=s.stateForSmoke().snapshot();
    if(ROLE.equals("host") && IdentityIntroSmokeChecks.advance(c,s))return;
    if(ROLE.equals("host") && OpeningHandSmokeChecks.advance(c,s,true))return;
    if(ROLE.equals("host")){
      require(stage>=26,"unexpected host match");require(!snapshot.heroCandidates().contains("liubei"),"banned hero offer");
      if(snapshot.status()==0){if(stage!=26 || snapshot.heroCandidates().isEmpty())return;require(snapshot.heroCandidates().size()==4,"normal identity room must offer four real candidates");if(heroOfferFrames++<3)return;capture(c,"hero-offer");s.pressForSmoke("hero:"+snapshot.heroCandidates().getFirst());s.pressForSmoke("hero-confirm");next(27);return;}
      if(snapshot.status()==1){require(snapshot.seats().size()==8&&snapshot.seats().stream().filter(a->a.human()).count()==1,"final match roster");capture(c,"running");pass();}
    }else{
      require(stage==22||stage==23,"unexpected guest match");require(snapshot.viewerSeat()<0,"spectator assigned player");require(snapshot.identityIntro()==null&&snapshot.heroCandidates().isEmpty()&&snapshot.seats().stream().allMatch(a->a.hand().isEmpty()&&(!a.handVisible())&&(a.identityOrdinal()<0||!a.alive()||a.seat()==snapshot.lordSeatOrdinal())),"spectator private data leak");
      if(snapshot.status()!=1)return;
      if(stage==22){next(23);return;}
      capture(c,"spectator");MatchClient.hide();next(24);
    }
  }
  private static void pass(){dev.sgs.validation.client.art.PlayerSkinRenderer.clearUvFixtures();done=true;ProbeMod.LOG.info("SGS_LOBBY_FLOW_PASS role={} native_ui=true stage={}",ROLE,stage);}
}
