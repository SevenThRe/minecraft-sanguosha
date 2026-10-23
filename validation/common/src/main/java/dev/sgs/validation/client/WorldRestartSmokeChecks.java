package dev.sgs.validation.client;

import dev.sgs.core.Phase;
import dev.sgs.core.card.CardType;
import dev.sgs.core.engine.PromptType;
import dev.sgs.core.match.dto.MatchViews.*;
import dev.sgs.validation.ProbeMod;
import dev.sgs.validation.match.MatchNetwork;
import java.nio.file.*;
import java.util.*;
import net.blay09.mods.balm.api.Balm;
import net.blay09.mods.balm.api.event.TickPhase;
import net.blay09.mods.balm.api.event.TickType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/** Opt-in ordinary-world restart evidence. Files never feed cards, HP or a fabricated server view. */
public final class WorldRestartSmokeChecks {
    private static final String ROLE=System.getProperty("sgs.worldRestartRole","");
    private static final String STAGE=System.getProperty("sgs.worldRestartStage","");
    private static final boolean PENDING_TRICK=Boolean.getBoolean("sgs.worldRestartTrick");
    private static final boolean KUROU_RESCUE=System.getProperty("sgs.worldRestartBoundary","").equals("kurou_rescue");
    private static final String HP_LOSS_HERO=System.getProperty("sgs.worldRestartHero","huanggai");
    private static final String HP_LOSS_SKILL=System.getProperty("sgs.worldRestartHpLossSkill","kurou");
    private static final boolean HP_LOSS_WEAPON=Boolean.parseBoolean(System.getProperty("sgs.worldRestartHpLossWeapon","true"));
    private static int kurouSentHp=Integer.MIN_VALUE;
    private static boolean kurouWeaponSent;
    private static boolean kurouAckObserved;
    private static int tableEntryRetries;
    private static int tableEntryDiagnosticTick;
    private static final boolean HARVEST=System.getProperty("sgs.worldRestartBoundary","counterspell").equals("wugu_pick");
    private static final boolean ATTACK_YIJI_GIVE=System.getProperty("sgs.worldRestartBoundary","").equals("attack_yiji_give");
    private static final boolean ATTACK_YIJI=ATTACK_YIJI_GIVE || System.getProperty("sgs.worldRestartBoundary","").equals("attack_yiji");
    private static final boolean YIJI=ATTACK_YIJI || System.getProperty("sgs.worldRestartBoundary","").equals("yiji_invoke");
    private static final boolean ACTIVE_FANKUI=System.getProperty("sgs.worldRestartBoundary","").equals("lijian_fankui");
    private static final boolean FANKUI=ACTIVE_FANKUI || System.getProperty("sgs.worldRestartBoundary","").equals("fankui_take");
    private static final boolean GANGLIE=System.getProperty("sgs.worldRestartBoundary","").equals("ganglie_invoke");
    private static final boolean TIEJI=System.getProperty("sgs.worldRestartBoundary","").equals("tieji_invoke");
    private static final boolean LIEGONG=System.getProperty("sgs.worldRestartBoundary","").equals("liegong_invoke");
    private static final boolean TARGET_RULE=TIEJI || LIEGONG;
    private static final String TARGET_RULE_SKILL=LIEGONG?"liegong":"tieji";
    private static final String TARGET_RULE_HERO=LIEGONG?"huangzhong":"machao";
    private static final boolean QINGLONG_JIJIANG=System.getProperty("sgs.worldRestartBoundary","").equals("qinglong_jijiang");
    private static final boolean QINGLONG=QINGLONG_JIJIANG || System.getProperty("sgs.worldRestartBoundary","").equals("qinglong_follow");
    private static final boolean MILITARY_SIX=QINGLONG_JIJIANG || TIEJI;
    private static int qinglongAttackTurn=-1;
    private static final boolean GUANSHI=System.getProperty("sgs.worldRestartBoundary","").equals("guanshi_second");
    private static long guanshiFirstToken=-1,guanshiFirstCard=-1;private static int guanshiFirstHand,guanshiAttackTurn=-1;
    private static final boolean HANBING=System.getProperty("sgs.worldRestartBoundary","").equals("hanbing_second");
    private static long hanbingFirst=-1;private static boolean hanbingDismantled;
    private static final boolean CIXIONG=System.getProperty("sgs.worldRestartBoundary","").equals("cixiong_invoke");
    private static final String FOCUS_TRICK=System.getProperty("sgs.worldRestartTrickType","");
    private static final boolean BORROW=System.getProperty("sgs.worldRestartBoundary","").equals("borrow_dodge");
    private static final boolean FIRE_PAY=System.getProperty("sgs.worldRestartBoundary","").equals("huogong_pay");
    private static final boolean ATTACK_DODGE=System.getProperty("sgs.worldRestartBoundary","").equals("attack_dodge");
    private static int step,ticks,after,stableFrames;
    private static long room=-1,lastPrompt=-1,restoreNonce,parkAfterMillis;
    private static long parkedWindow=-1,sentRevision=-1;
    private static long acceptedAfterMillis,restoreRenderBase=-1;
    private static String pendingHero="";
    private static int selectionErrors;
    private static boolean done,parked,yijiTransferAccepted;
    private static long yijiFinishAfterMillis,yijiReturnSequence=-1;
    private static boolean yijiReturnSaved;
    private record Summary(int schema,String stage,String uuid,long capturedAtMillis,MatchNetwork.View view,int clientNonce){}
    public static void initialize(){if(!ROLE.isEmpty())Balm.getEvents().onTickEvent(TickType.Client,TickPhase.End,WorldRestartSmokeChecks::tick);}
    private static Path directory(){return Path.of(System.getProperty("sgs.worldRestartEvidence"));}
    private static void require(boolean ok,String reason){if(!ok)throw new IllegalStateException(reason);}
    private static void save(Minecraft c,String name,MatchScreen screen)throws Exception{
        screen.verifySkillGeometryForSmoke();
        var record=new Summary(1,name,c.player.getUUID().toString(),System.currentTimeMillis(),screen.stateForSmoke(),MatchClient.nonceForSmoke());
        var target=directory().resolve(name+".json");var temporary=directory().resolve(name+".json.new");
        Files.createDirectories(directory());Files.writeString(temporary,MatchNetwork.JSON.toJson(record));
        try{Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
        catch(AtomicMoveNotSupportedException ignored){Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING);}
        Screenshot.grab(c.gameDirectory,"sgs-world-restart-"+name+".png",c.getMainRenderTarget(),m->{});
        ProbeMod.LOG.info("SGS_WORLD_RESTART_SUMMARY stage={} match={} room={} seat={} nonce_floor={} deadline={} file={}",
            name,record.view().matchId(),record.view().roomId(),record.view().snapshot().viewerSeat(),record.view().nonceFloor(),record.view().snapshot().phaseDeadlineMillis(),target);
    }
    private static void next(int value){step=value;after=ticks+5;ProbeMod.LOG.info("SGS_WORLD_RESTART_STAGE stage={} step={}",STAGE,step);}
    private static void click(LobbyScreen s,String action){s.smokeBounds(action);s.smokeAction(action);}
    private static void tick(Minecraft c){
        if(done && !Boolean.getBoolean("sgs.viewAsResponseSmoke"))return;
        try{
            if(done){ViewAsResponseSmokeChecks.tick(c);return;}
            require(++ticks<18000,"world restart client timeout");
            if(c.player==null || c.level==null || ticks<after)return;
            if(c.screen instanceof MatchScreen match){match(c,match);return;}
            if(STAGE.equals("restore")){
                // Login restoration is server-owned. Opening an ordinary lobby may only resume
                // the view that MatchClient actually received, never synthesize match identity.
                if(step==0){LobbyClient.open();next(1);}else MatchClient.resume();
                return;
            }
            if(step==0){
                // Enter through the actual block interaction, including the server-owned survival anchor.
                if(c.gameMode==null || ticks<100)return;
                var table=new net.minecraft.core.BlockPos(1,90,0);
                if(!c.level.getBlockState(table).is(ProbeMod.table)){
                    if(!c.player.getInventory().getItem(0).is(ProbeMod.table.asItem()))return;
                    c.player.getInventory().selected=0;
                    c.gameMode.useItemOn(c.player,net.minecraft.world.InteractionHand.MAIN_HAND,
                        new net.minecraft.world.phys.BlockHitResult(new net.minecraft.world.phys.Vec3(1.5,90,0.5),net.minecraft.core.Direction.UP,table.below(),false));
                    after=ticks+40;return;
                }
                c.player.getInventory().selected=1;
                c.gameMode.useItemOn(c.player,net.minecraft.world.InteractionHand.MAIN_HAND,
                    new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(table),net.minecraft.core.Direction.SOUTH,table,false));
                ProbeMod.LOG.info("SGS_WORLD_RESTART_TABLE_ENTRY real_block_interaction=true");next(1);return;
            }
            if(step==1 && ticks>=tableEntryDiagnosticTick){
                tableEntryDiagnosticTick=ticks+100;
                ProbeMod.LOG.info("SGS_WORLD_RESTART_ENTRY_STATE screen={} position={} table={} retries={}",c.screen==null?"none":c.screen.getClass().getSimpleName(),c.player.position(),c.level.getBlockState(new net.minecraft.core.BlockPos(1,90,0)),tableEntryRetries);
            }
            if(step==1 && c.screen==null && ticks>=after+40 && tableEntryRetries<3 && c.gameMode!=null){
                var table=new net.minecraft.core.BlockPos(1,90,0);
                require(c.level.getBlockState(table).is(ProbeMod.table),"placed table vanished before lobby entry");
                c.player.getInventory().selected=1;
                c.gameMode.useItemOn(c.player,net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(table),net.minecraft.core.Direction.SOUTH,table,false));
                tableEntryRetries++;after=ticks+40;
                ProbeMod.LOG.info("SGS_WORLD_RESTART_ENTRY_RETRY attempt={} real_block_interaction=true",tableEntryRetries);
            }
            if(!(c.screen instanceof LobbyScreen s) || s.smokeView()==null || s.smokePending())return;
            var v=s.smokeView();
            switch(step){
                case 1->{require(v.heroes().size()==Integer.getInteger("sgs.worldRestartExpectedHeroes",dev.sgs.core.hero.StandardHeroes.CATALOG.size()),"ordinary catalog differs from the complete catalog");click(s,"open_rooms");next(2);}
                case 2->{click(s,MILITARY_SIX?"mode:six":"mode:two_v_two");next(3);}
                case 3->{click(s,"create");next(4);}
                case 4->{
                    require(s.smokeModal().equals("SETTINGS"),"ordinary settings modal missing");
                    s.smokeSetField("name","世界重启验证");
                    click(s,"fixed"); // Keep the ordinary host's real lobby seat; no program-side seat injection.
                    for(String kind:List.of("turn","response","selection")){
                        int attempts=0;while(s.smokeDraftSeconds(kind)!=300){require(++attempts<12,"300 seconds unavailable in native "+kind+" control");click(s,kind);}
                    }
                    click(s,"confirm_settings");next(5);
                }
                case 5->{
                    if(v.ownRoom()<0 && s.smokeModal().equals("SETTINGS") && v.reason().contains("状态")){click(s,"confirm_settings");next(5);return;}
                    require(v.success() && v.ownRoom()>=0,"ordinary create failed "+v.reason());
                    var r=v.rooms().stream().filter(a->a.id()==v.ownRoom()).findFirst().orElseThrow();
                    require(r.mode().equals(MILITARY_SIX?"six":"two_v_two") && r.settings().turnSeconds()==300 && r.settings().responseSeconds()==300
                        && r.settings().selectionSeconds()==300,"actual settings are not 2v2/300/300/300");
                    room=r.id();click(s,"ai_fill");next(6);
                }
                case 6->{var r=v.rooms().stream().filter(a->a.id()==room).findFirst().orElseThrow();
                    require(v.success() && r.ai()==(MILITARY_SIX?5:3) && r.members()==1,"explicit AI fill changed human roster");click(s,"ready");next(7);}
                case 7->{require(v.success() && v.ownReady(),"human ready failed");click(s,"start");next(8);}
                default->{}
            }
        }catch(Exception failure){done=true;ProbeMod.LOG.error("SGS_WORLD_RESTART_FAIL stage="+STAGE+" step="+step,failure);}
    }
    private static void match(Minecraft c,MatchScreen screen)throws Exception{
        if(c.getWindow().getGuiScaledWidth()<80 || c.getWindow().getGuiScaledHeight()<80)return;
        TinyViewportChecks.run(c,screen);c.getToasts().clear();
        var view=screen.stateForSmoke();var s=view.snapshot();
        require(s.viewerSeat()>=0,"restored into spectator instead of owned seat");
        var me=s.seats().stream().filter(SeatView::self).findFirst().orElseThrow();
        if(STAGE.equals("restore")){
            var before=MatchNetwork.JSON.fromJson(Files.readString(directory().resolve("before.json")),Summary.class);
            if(ATTACK_YIJI_GIVE && yijiTransferAccepted){
                if(view.nonceFloor()<restoreNonce+2)return;
                long root=before.view().snapshot().processing().getFirst().instanceId();
                if(s.processing().stream().anyMatch(card->card.instanceId()==root))return;
                var old=before.view().snapshot().seats().stream().filter(SeatView::self).findFirst().orElseThrow();
                long transferred=before.view().snapshot().prompt().choices().getFirst().instanceId();
                require(me.handCount()==old.handCount()-1 && me.hand().stream().noneMatch(card->card.instanceId()==transferred),"Yiji finish changed the retained physical hand");
                var finishes=s.publicMoves().stream().filter(move->move.reason().equals("FINISH") && move.cards().stream().anyMatch(card->card.instanceId()==root)).toList();
                require(finishes.size()<=1,"original attack cleanup duplicated");
                if(!finishes.isEmpty()){
                    var finish=finishes.getFirst();require(finish.actorSeat()==before.view().snapshot().currentSeat(),"original attack finished under wrong source");
                    require(yijiReturnSequence<0 || yijiReturnSequence==finish.sequence(),"original attack produced a second finish event");yijiReturnSequence=finish.sequence();
                }
                if(yijiReturnSequence<0)return;
                if(!yijiReturnSaved){save(c,"returned",screen);yijiReturnSaved=true;ProbeMod.LOG.info("SGS_YIJI_PARENT_RETURN root={} original_actor={} finish_sequence={}",root,before.view().snapshot().currentSeat(),yijiReturnSequence);return;}
                // The original bot may already end its legal play phase. Hold the next real
                // human request so the complete later state can be replayed without a race.
                if(s.prompt()==null || s.prompt().askerSeat()!=s.viewerSeat() || s.prompt().typeOrdinal()==PromptType.YIJI_GIVE.ordinal())return;
                if(yijiFinishAfterMillis==0){yijiFinishAfterMillis=System.currentTimeMillis()+750;return;}
                if(System.currentTimeMillis()<yijiFinishAfterMillis)return;
                save(c,"completed",screen);done=true;
                ProbeMod.LOG.info("SGS_YIJI_GIVE_COMPLETE root={} original_actor={} next_actor={} transfer_and_keep=true root_finish_once=true native_mouse=true",root,before.view().snapshot().currentSeat(),s.currentSeat());
                ProbeMod.LOG.info("SGS_WORLD_RESTART_PASS match={} room={} physical_hand_preserved=true fresh_nonce_accepted=true",view.matchId(),view.roomId());return;
            }
            if(!parked){
                require(s.status()==1 && me.online(),"restore is not a live owned match");
                var old=before.view();var b=old.snapshot();var previous=b.seats().stream().filter(SeatView::self).findFirst().orElseThrow();
                require(c.player.getUUID().toString().equals(before.uuid()),"offline UUID changed");
                require(view.matchId()==old.matchId() && view.roomId()==old.roomId(),"match or lobby room was recreated");
                require(s.viewerSeat()==b.viewerSeat() && s.currentSeat()==b.currentSeat() && s.turnNumber()==b.turnNumber() && s.phaseOrdinal()==b.phaseOrdinal(),"parked turn/phase/seat advanced");
                if(PENDING_TRICK){
                    require(s.prompt()!=null && b.prompt()!=null,"pending trick request missing");
                    require(s.prompt().promptId()==b.prompt().promptId() && s.prompt().typeOrdinal()==b.prompt().typeOrdinal(),"trick token/type changed");
                    if(KUROU_RESCUE)require(s.prompt().typeOrdinal()==PromptType.RESCUE_TAO.ordinal() && s.prompt().askerSeat()==s.viewerSeat()
                        && s.prompt().subjectSeat()==s.viewerSeat() && s.currentSeat()==s.viewerSeat() && me.heroId().equals(HP_LOSS_HERO)
                        && me.hp()==0 && s.processing().isEmpty() && s.counterspellWindow()==null,"HP-loss self rescue changed");
                    else if(TARGET_RULE)require(s.prompt().typeOrdinal()==PromptType.SKILL_INVOKE.ordinal() && s.prompt().skillId().equals(TARGET_RULE_SKILL) && s.prompt().askerSeat()==s.viewerSeat() && s.counterspellWindow()==null,"rule invocation changed");
                    else if(QINGLONG_JIJIANG)require(s.prompt().typeOrdinal()==PromptType.SKILL_INVOKE.ordinal() && s.prompt().skillId().equals("jijiang") && s.prompt().askerSeat()==s.viewerSeat() && s.prompt().subjectSeat()==s.viewerSeat(),"Qinglong lord invocation changed");
                    else if(QINGLONG)require(s.prompt().typeOrdinal()==PromptType.QINGLONG_SHA.ordinal() && s.prompt().skillId().equals("qinglong") && s.counterspellWindow()==null,"qinglong follow-up changed");
                    else if(GUANSHI)require(s.prompt().typeOrdinal()==PromptType.WEAPON_COST_SECOND.ordinal() && s.prompt().skillId().equals("guanshi") && s.counterspellWindow()==null && s.prompt().choices().equals(b.prompt().choices()),"guanshi tentative second choice changed");
                    else if(HANBING)require(s.prompt().typeOrdinal()==PromptType.CARD_ZONE_SELECT.ordinal() && s.prompt().skillId().equals("hanbing") && s.counterspellWindow()==null && s.prompt().choices().equals(b.prompt().choices()) && s.prompt().hiddenHandAvailable()==b.prompt().hiddenHandAvailable(),"hanbing second selection changed");
                    else if(CIXIONG)require(s.prompt().typeOrdinal()==PromptType.SKILL_INVOKE.ordinal() && s.prompt().skillId().equals("cixiong") && s.counterspellWindow()==null,"cixiong invocation changed");
                    else if(BORROW)require(s.prompt().typeOrdinal()==PromptType.DODGE_SHA.ordinal() && s.prompt().askerSeat()==s.viewerSeat() && s.currentSeat()==s.viewerSeat() && s.prompt().subjectSeat()!=s.viewerSeat() && s.counterspellWindow()==null && s.processing().equals(b.processing()),"borrowed strike parent changed");
                    else if(FIRE_PAY)require(s.prompt().typeOrdinal()==PromptType.HUOGONG_PAY.ordinal() && s.prompt().askerSeat()==s.viewerSeat() && s.prompt().subjectSeat()==s.viewerSeat() && s.prompt().choices().equals(b.prompt().choices()) && s.prompt().choices().size()==1 && s.counterspellWindow()==null,"reveal/cost payment changed");
                    else if(ATTACK_DODGE)require(s.prompt().typeOrdinal()==PromptType.DODGE_SHA.ordinal() && s.counterspellWindow()==null,"attack dodge changed");
                    else if(GANGLIE)require(s.prompt().typeOrdinal()==PromptType.SKILL_INVOKE.ordinal() && s.prompt().skillId().equals("ganglie") && s.counterspellWindow()==null,"ganglie invocation changed");
                    else if(FANKUI)require(s.prompt().typeOrdinal()==PromptType.FANKUI_TAKE.ordinal() && s.prompt().skillId().equals("fankui") && s.counterspellWindow()==null && s.prompt().choices().equals(b.prompt().choices()) && s.prompt().hiddenHandAvailable()==b.prompt().hiddenHandAvailable(),"feedback options changed");
                    else if(ATTACK_YIJI_GIVE)require(s.prompt().typeOrdinal()==PromptType.YIJI_GIVE.ordinal() && s.prompt().skillId().equals("yiji") && s.prompt().choices().equals(b.prompt().choices()) && s.prompt().choices().size()==2 && s.counterspellWindow()==null,"Yiji distribution choices changed");
                    else if(YIJI)require(s.prompt().typeOrdinal()==PromptType.SKILL_INVOKE.ordinal() && s.prompt().skillId().equals("yiji") && s.counterspellWindow()==null,"yiji invocation changed");
                    else if(HARVEST)require(s.prompt().typeOrdinal()==PromptType.WUGU_PICK.ordinal() && s.counterspellWindow()==null
                        && s.prompt().choices().equals(b.prompt().choices()) && !s.prompt().choices().isEmpty(),"physical harvest pool changed");
                    else require(s.counterspellWindow()!=null && b.counterspellWindow()!=null
                        && s.counterspellWindow().windowId()==b.counterspellWindow().windowId() && s.counterspellWindow().chainDepth()==b.counterspellWindow().chainDepth(),"trick window/depth changed");
                    require(s.processing().equals(b.processing()),"processing physical costs were replayed/replaced");
                }else require(s.prompt()==null && b.prompt()==null,"idle PLAY was restored as a different request");
                require(me.hp()==previous.hp() && me.maxHp()==previous.maxHp() && Objects.equals(me.heroId(),previous.heroId())
                    && me.identityOrdinal()==previous.identityOrdinal(),"hero/identity/HP was replaced");
                require(me.hand().equals(previous.hand()) && me.handCount()==previous.handCount(),"physical hand was re-dealt");
                require(view.nonceFloor()==old.nonceFloor() && MatchClient.nonceForSmoke()>=view.nonceFloor(),"nonce floor lost");
                long deadline=PENDING_TRICK?s.prompt().deadlineMillis():s.phaseDeadlineMillis();
                long oldDeadline=PENDING_TRICK?b.prompt().deadlineMillis():b.phaseDeadlineMillis();
                require(deadline>System.currentTimeMillis(),"restored deadline expired");
                require(deadline!=oldDeadline,"deadline did not rebase from saved remaining time");
                // Client ticks can precede the first rendered hand after reconnect, especially
                // when several native loader windows start together. Wait for real geometry.
                if(QINGLONG && !QINGLONG_JIJIANG){
                    var follow=me.hand().stream().filter(ca->Set.of(CardType.SHA.ordinal(),CardType.HUOSHA.ordinal(),CardType.LEISHA.ordinal()).contains(ca.typeOrdinal())).findFirst().orElseThrow();
                    require(screen.handCardSelectableForSmoke(follow.instanceId()),"qinglong legal new Sha was disabled by ordinary play quota");
                    require(me.hand().stream().filter(ca->!Set.of(CardType.SHA.ordinal(),CardType.HUOSHA.ordinal(),CardType.LEISHA.ordinal()).contains(ca.typeOrdinal())).noneMatch(ca->screen.handCardSelectableForSmoke(ca.instanceId())),"qinglong accepted a non-Sha card");
                    if(screen.handBoundsForSmoke(me.hand().indexOf(follow))==null)return;
                }
                if(QINGLONG_JIJIANG && me.handCount()>0 && screen.handBoundsForSmoke(0)==null)return;
                if(KUROU_RESCUE){var rescue=me.hand().stream().filter(ca->ca.typeOrdinal()==CardType.TAO.ordinal() || ca.typeOrdinal()==CardType.JIU.ordinal()).findFirst().orElseThrow();
                    if(screen.handBoundsForSmoke(me.hand().indexOf(rescue))==null)return;}
                if(restoreRenderBase<0)restoreRenderBase=screen.completedRenderCountForSmoke();
                if(screen.completedRenderCountForSmoke()-restoreRenderBase<3)return;
                if(++stableFrames<3)return;
                restoreNonce=view.nonceFloor();save(c,"restored",screen);parked=true;
                if(KUROU_RESCUE){
                    var cost=me.hand().stream().filter(ca->ca.typeOrdinal()==CardType.TAO.ordinal() || ca.typeOrdinal()==CardType.JIU.ordinal()).findFirst().orElseThrow();
                    var bounds=screen.handBoundsForSmoke(me.hand().indexOf(cost));require(bounds!=null,"rescue hand hitbox absent");
                    require(screen.mouseClicked(bounds[0]+bounds[2]/2d,bounds[1]+bounds[3]/2d,0) && screen.selectedCountForSmoke()==1,"rescue physical card click failed");
                    int nonce=MatchClient.nonceForSmoke();screen.clickForSmoke("respond");require(MatchClient.nonceForSmoke()==nonce+1,"rescue submitted more than once");
                    ProbeMod.LOG.info("SGS_KUROU_RESCUE_MOUSE card={} type={} real_hit_test=true single_nonce=true",cost.instanceId(),CardType.values()[cost.typeOrdinal()]);
                }
                else if(QINGLONG && !QINGLONG_JIJIANG){
                    var follow=me.hand().stream().filter(ca->Set.of(CardType.SHA.ordinal(),CardType.HUOSHA.ordinal(),CardType.LEISHA.ordinal()).contains(ca.typeOrdinal())).findFirst().orElseThrow();
                    int index=me.hand().indexOf(follow);var bounds=screen.handBoundsForSmoke(index);require(bounds!=null,"qinglong hand hitbox absent");
                    require(screen.mouseClicked(bounds[0]+bounds[2]/2d,bounds[1]+bounds[3]/2d,0) && screen.selectedCountForSmoke()==1,"qinglong actual hand click failed");
                    int nonce=MatchClient.nonceForSmoke();screen.clickForSmoke("respond");require(MatchClient.nonceForSmoke()==nonce+1,"qinglong duplicated submission");
                    ProbeMod.LOG.info("SGS_QINGLONG_RESTART_MOUSE card={} actual_hand_click=true real_hit_test=true single_nonce=true",follow.instanceId());
                }
                else if(TARGET_RULE){int nonce=MatchClient.nonceForSmoke();screen.clickForSmoke("invoke");require(MatchClient.nonceForSmoke()==nonce+1,"rule duplicated submission");ProbeMod.LOG.info("SGS_TARGET_RULE_RESTART_MOUSE skill={} invoke=true real_hit_test=true single_nonce=true",TARGET_RULE_SKILL);}
                else if(QINGLONG_JIJIANG){int nonce=MatchClient.nonceForSmoke();screen.clickForSmoke("invoke");require(MatchClient.nonceForSmoke()==nonce+1,"Qinglong Jijiang duplicated submission");ProbeMod.LOG.info("SGS_QINGLONG_JIJIANG_RESTART_MOUSE invoke=true real_hit_test=true single_nonce=true");}
                else if(GUANSHI){
                    long chosen=s.prompt().choices().getFirst().instanceId();screen.clickForSmoke("skill-choice:"+chosen);int nonce=MatchClient.nonceForSmoke();screen.clickForSmoke("skill-choice-confirm");
                    require(!screen.enabledForSmoke("skill-choice-confirm") && !screen.enabledForSmoke("decline"),"guanshi submission not locked");require(MatchClient.nonceForSmoke()==nonce+1,"guanshi duplicated submission");
                    ProbeMod.LOG.info("SGS_GUANSHI_RESTART_MOUSE second_card={} atomic_two_costs=true real_hit_test=true single_nonce=true",chosen);
                }
                else if(HANBING){screen.clickForSmoke("zone-hand:0");screen.clickForSmoke("skill-choice-confirm");ProbeMod.LOG.info("SGS_HANBING_RESTART_MOUSE second_choice=true opaque_position=0 real_hit_test=true");}
                else if(CIXIONG){screen.clickForSmoke("invoke");ProbeMod.LOG.info("SGS_CIXIONG_RESTART_MOUSE invoke=true real_hit_test=true");}
                else if(ATTACK_DODGE){
                    var cost=me.hand().stream().filter(card->(card.suitOrdinal()==dev.sgs.core.card.Suit.SPADE.ordinal()||card.suitOrdinal()==dev.sgs.core.card.Suit.CLUB.ordinal())&&card.typeOrdinal()!=CardType.SHAN.ordinal()).findFirst().orElseThrow();
                    require(me.heroId().equals("zhenji"),"attack response requires real Qingguo owner");
                    screen.clickForSmoke("qingguo");screen.selectForSmoke(List.of(cost.instanceId()),-1);screen.clickForSmoke("respond");
                    ProbeMod.LOG.info("SGS_QINGGUO_RESTART_MOUSE card={} physical_type={} real_hit_test=true",cost.instanceId(),CardType.values()[cost.typeOrdinal()]);
                }else if(FIRE_PAY){
                    var shown=s.prompt().choices().getFirst();var cost=me.hand().stream().filter(ca->ca.instanceId()==shown.instanceId()&&ca.suitOrdinal()==shown.suitOrdinal()).findFirst().orElseThrow();
                    int index=me.hand().indexOf(cost);var bounds=screen.handBoundsForSmoke(index);if(bounds==null)return;
                    screen.mouseClicked(bounds[0]+bounds[2]/2d,bounds[1]+bounds[3]/2d,0);screen.clickForSmoke("respond");
                    ProbeMod.LOG.info("SGS_HUOGONG_PAYMENT_MOUSE card={} shown={} real_hit_test=true",cost.instanceId(),shown.instanceId());
                }else if(HARVEST){
                    int previousNonce=MatchClient.nonceForSmoke();screen.clickForSmoke("choice:0");
                    require(!screen.enabledForSmoke("choice:0") && !screen.enabledForSmoke("decline"),"harvest submission controls were not locked");
                    require(MatchClient.nonceForSmoke()==previousNonce+1,"harvest mouse click submitted more than once");
                    ProbeMod.LOG.info("SGS_HARVEST_MOUSE_PASS real_hit_test=true choice_locked=true default_locked=true single_nonce=true");
                }else if(FANKUI){
                    long selected=s.prompt().hiddenHandAvailable()?-2:s.prompt().choices().getFirst().instanceId();
                    require(selected>0||s.prompt().hiddenHandAvailable(),"feedback has no actual legal option");
                    
                    screen.clickForSmoke(selected<=-2?"zone-hand:0":"skill-choice:"+selected);screen.clickForSmoke("skill-choice-confirm");ProbeMod.LOG.info("SGS_FANKUI_MOUSE_INPUT choice={} real_hit_test=true",selected);
                }else if(GANGLIE){screen.clickForSmoke("invoke");ProbeMod.LOG.info("SGS_GANGLIE_MOUSE_INPUT invoke=true real_hit_test=true");}
                else if(ATTACK_YIJI_GIVE){
                    long chosen=s.prompt().choices().getFirst().instanceId();int ally=s.seats().stream().filter(seat->seat.alive()&&!seat.self()&&seat.teamId()==me.teamId()).findFirst().orElseThrow().seat();
                    screen.clickForSmoke("skill-choice:"+chosen);screen.clickSeatForSmoke(ally);
                    int nonce=MatchClient.nonceForSmoke();screen.clickForSmoke("skill-choice-confirm");require(MatchClient.nonceForSmoke()==nonce+1,"Yiji transfer submitted more than once");
                    ProbeMod.LOG.info("SGS_YIJI_GIVE_MOUSE card={} recipient={} real_card_button=true real_portrait=true real_confirm=true single_nonce=true",chosen,ally);
                }
                else if(YIJI){screen.clickForSmoke("invoke");ProbeMod.LOG.info("SGS_YIJI_MOUSE_INPUT invoke=true real_hit_test=true");}
                else screen.pressForSmoke(PENDING_TRICK?"decline":"end");
                ProbeMod.LOG.info("SGS_WORLD_RESTART_FRESH_INPUT sent_nonce={} previous_floor={} pending_trick={}",MatchClient.nonceForSmoke(),restoreNonce,PENDING_TRICK);return;
            }
            if(view.nonceFloor()<=restoreNonce)return;
            if(KUROU_RESCUE){
                if(s.prompt()!=null || !s.processing().isEmpty())return;
                var old=before.view().snapshot().seats().stream().filter(SeatView::self).findFirst().orElseThrow();
                long completed=before.view().snapshot().skillActivations().stream().filter(a->a.seat()==s.viewerSeat()&&a.skillId().equals(HP_LOSS_SKILL)).count()+1;
                long current=s.skillActivations().stream().filter(a->a.seat()==s.viewerSeat()&&a.skillId().equals(HP_LOSS_SKILL)).count();
                if(current<completed){
                    if(!kurouAckObserved){kurouAckObserved=true;ProbeMod.LOG.info("SGS_KUROU_ACK_WAIT nonce_floor={} hp={} hand={} completion_receipt_pending=true",view.nonceFloor(),me.hp(),me.handCount());}
                    return;
                }
                var cost=old.hand().stream().filter(ca->ca.typeOrdinal()==CardType.TAO.ordinal() || ca.typeOrdinal()==CardType.JIU.ordinal()).findFirst().orElseThrow();
                require(me.hp()==1 && me.handCount()==old.handCount()+1 && me.hand().stream().noneMatch(ca->ca.instanceId()==cost.instanceId()),"rescue payment, recovery or survivor draw differs");
                var retained=old.hand().stream().filter(ca->ca.instanceId()!=cost.instanceId()).map(CardView::instanceId).collect(java.util.stream.Collectors.toSet());
                require(me.hand().stream().map(CardView::instanceId).collect(java.util.stream.Collectors.toSet()).containsAll(retained),"rescue changed another owned entity");
                require(s.currentSeat()==s.viewerSeat() && s.phaseOrdinal()==Phase.PLAY.ordinal() && s.turnNumber()==before.view().snapshot().turnNumber(),"HP-loss parent did not return to original play");
                require(s.skillActivations().stream().filter(a->a.seat()==s.viewerSeat()&&a.skillId().equals(HP_LOSS_SKILL)).count()
                    ==before.view().snapshot().skillActivations().stream().filter(a->a.seat()==s.viewerSeat()&&a.skillId().equals(HP_LOSS_SKILL)).count()+1,"HP-loss activation duplicated or lost");
            }else if(TARGET_RULE){
                if(s.prompt()!=null || !s.processing().isEmpty())return;
                var b=before.view().snapshot();var old=b.seats().stream().filter(SeatView::self).findFirst().orElseThrow();var oldTarget=b.seats().get(b.prompt().subjectSeat());var target=s.seats().get(oldTarget.seat());
                require(me.heroId().equals(TARGET_RULE_HERO) && me.hand().stream().map(ca->List.of(ca.instanceId(),(long)ca.typeOrdinal(),(long)ca.suitOrdinal(),(long)ca.rankOrdinal())).toList().equals(old.hand().stream().map(ca->List.of(ca.instanceId(),(long)ca.typeOrdinal(),(long)ca.suitOrdinal(),(long)ca.rankOrdinal())).toList()) && me.handCount()==old.handCount(),"rule changed the already-paid source hand");
                require(target.hp()==oldTarget.hp()-1 && target.handCount()==oldTarget.handCount(),"red Tieji did not hit once while preserving target Shan");
                require(s.skillActivations().stream().filter(a->a.seat()==s.viewerSeat()&&a.skillId().equals(TARGET_RULE_SKILL)).count()==1,"rule receipt duplicated or missing");
                require(s.currentSeat()==s.viewerSeat() && s.phaseOrdinal()==Phase.PLAY.ordinal(),"Tieji escaped original play phase");
            }else if(BORROW){
                if(s.prompt()!=null || !s.processing().isEmpty())return;
                var b=before.view().snapshot();var old=b.seats().stream().filter(SeatView::self).findFirst().orElseThrow();
                require(me.hp()==old.hp()-1 && me.hand().stream().map(ca->List.of(ca.instanceId(),(long)ca.typeOrdinal(),(long)ca.suitOrdinal(),(long)ca.rankOrdinal())).toList().equals(old.hand().stream().map(ca->List.of(ca.instanceId(),(long)ca.typeOrdinal(),(long)ca.suitOrdinal(),(long)ca.rankOrdinal())).toList()),"borrowed attack damage or hand replayed");
                require(s.currentSeat()==s.viewerSeat() && s.phaseOrdinal()==Phase.PLAY.ordinal(),"borrowed strike did not return to original play phase");
            }else if(FIRE_PAY){
                if(s.prompt()!=null || !s.processing().isEmpty())return;
                var b=before.view().snapshot();var old=b.seats().stream().filter(SeatView::self).findFirst().orElseThrow();
                long cost=b.prompt().choices().getFirst().instanceId();
                require(me.hp()==old.hp()-1 && me.handCount()==old.handCount()-1 && me.hand().stream().noneMatch(ca->ca.instanceId()==cost),"self fire cost or damage replayed");
                require(s.currentSeat()==s.viewerSeat() && s.phaseOrdinal()==Phase.PLAY.ordinal(),"fire parent did not return to original play phase");
            }else if(QINGLONG_JIJIANG){
                var b=before.view().snapshot();var old=b.seats().stream().filter(SeatView::self).findFirst().orElseThrow();
                if(s.prompt()!=null || !s.processing().isEmpty())return;
                require(me.handCount()==old.handCount() && me.hand().stream().map(ca->ca.instanceId()+"/"+ca.typeOrdinal()+"/"+ca.suitOrdinal()+"/"+ca.rankOrdinal()).toList().equals(old.hand().stream().map(ca->ca.instanceId()+"/"+ca.typeOrdinal()+"/"+ca.suitOrdinal()+"/"+ca.rankOrdinal()).toList()),"Qinglong Jijiang changed physical lord hand instead of using provider");
                var target=s.seats().stream().filter(p->p.seat()==1).findFirst().orElseThrow();var oldTarget=b.seats().stream().filter(p->p.seat()==1).findFirst().orElseThrow();require(target.hp()==oldTarget.hp()-1 && target.handCount()==oldTarget.handCount(),"provided new Sha damage or defense differs");
                require(s.currentSeat()==s.viewerSeat() && s.phaseOrdinal()==Phase.PLAY.ordinal(),"Qinglong Jijiang advanced turn");
                require(s.skillActivations().stream().anyMatch(a->a.seat()==s.viewerSeat()&&a.skillId().equals("jijiang")) && s.skillActivations().stream().anyMatch(a->a.seat()==s.viewerSeat()&&a.skillId().equals("qinglong")),"missing lord or weapon receipt");
            }else if(QINGLONG){
                var b=before.view().snapshot();var old=b.seats().stream().filter(SeatView::self).findFirst().orElseThrow();
                if(s.prompt()!=null || !s.processing().isEmpty() || me.handCount()!=old.handCount()-1)return;
                var target=s.seats().stream().filter(p->p.seat()==b.prompt().subjectSeat()).findFirst().orElseThrow();var oldTarget=b.seats().stream().filter(p->p.seat()==target.seat()).findFirst().orElseThrow();
                require(target.hp()==oldTarget.hp()-1,"qinglong new attack did not hit exactly once");require(s.currentSeat()==s.viewerSeat() && s.phaseOrdinal()==Phase.PLAY.ordinal(),"qinglong advanced turn");
                if(!s.skillActivations().stream().anyMatch(a->a.seat()==s.viewerSeat()&&a.skillId().equals("qinglong")))return;
            }else if(GUANSHI){
                var b=before.view().snapshot();var old=b.seats().stream().filter(SeatView::self).findFirst().orElseThrow();
                if(s.prompt()!=null || !s.processing().isEmpty() || me.handCount()!=old.handCount()-2)return;
                var target=s.seats().stream().filter(p->p.seat()==b.prompt().subjectSeat()).findFirst().orElseThrow();var oldTarget=b.seats().stream().filter(p->p.seat()==target.seat()).findFirst().orElseThrow();require(target.hp()==oldTarget.hp()-1,"guanshi did not force original damage exactly once");
                if(!s.skillActivations().stream().anyMatch(a->a.seat()==s.viewerSeat()&&a.skillId().equals("guanshi")))return;
            }else if(HANBING){
                var b=before.view().snapshot();var target=s.seats().stream().filter(p->p.seat()==b.prompt().subjectSeat()).findFirst().orElseThrow();var oldTarget=b.seats().stream().filter(p->p.seat()==target.seat()).findFirst().orElseThrow();
                if(s.prompt()!=null || !s.processing().isEmpty() || target.handCount()!=oldTarget.handCount()-1)return;
                require(target.hp()==oldTarget.hp(),"hanbing failed to prevent original damage");
                if(!s.skillActivations().stream().anyMatch(a->a.seat()==s.viewerSeat()&&a.skillId().equals("hanbing")))return;
            }else if(CIXIONG){
                if(s.prompt()!=null || !s.processing().isEmpty())return;
                require(s.currentSeat()==s.viewerSeat() && s.phaseOrdinal()==Phase.PLAY.ordinal(),"cixiong did not finish original attack in own play phase");
                if(!s.skillActivations().stream().anyMatch(a->a.seat()==s.viewerSeat()&&a.skillId().equals("cixiong")))return;
            }else if(ATTACK_DODGE){
                var old=before.view().snapshot().seats().stream().filter(SeatView::self).findFirst().orElseThrow();
                if(me.handCount()!=old.handCount()-1)return;
                require(me.hp()==old.hp(),"Qingguo did not dodge the original attack");
                if(!s.skillActivations().stream().anyMatch(a->a.seat()==s.viewerSeat()&&a.skillId().equals("qingguo")))return;
            }else if(GANGLIE){
                if(s.prompt()!=null&&s.prompt().promptId()==before.view().snapshot().prompt().promptId())return;
                if(!s.skillActivations().stream().anyMatch(a->a.seat()==s.viewerSeat()&&a.skillId().equals("ganglie")))return;
            }else if(FANKUI){
                var original=before.view().snapshot().seats().stream().filter(SeatView::self).findFirst().orElseThrow();
                if(me.handCount()!=original.handCount()+1)return;
                var oldIds=original.hand().stream().map(CardView::instanceId).collect(java.util.stream.Collectors.toSet());
                require(me.hand().stream().map(CardView::instanceId).filter(id->!oldIds.contains(id)).count()==1,"feedback did not receive one new entity");
            }else if(ATTACK_YIJI_GIVE){
                var b=before.view().snapshot();var old=b.seats().stream().filter(SeatView::self).findFirst().orElseThrow();
                long transferred=b.prompt().choices().getFirst().instanceId(),retained=b.prompt().choices().getLast().instanceId();
                if(s.prompt()==null || s.prompt().typeOrdinal()!=PromptType.YIJI_GIVE.ordinal() || s.prompt().promptId()==b.prompt().promptId())return;
                require(me.hp()==old.hp() && me.handCount()==old.handCount()-1 && me.hand().stream().noneMatch(card->card.instanceId()==transferred),"Yiji did not transfer exactly one card");
                require(s.prompt().choices().size()==1 && s.prompt().choices().getFirst().instanceId()==retained && s.processing().equals(b.processing()),"Yiji lost remaining choice or finished its parent too early");
                var ally=b.seats().stream().filter(seat->seat.alive()&&!seat.self()&&seat.teamId()==old.teamId()).findFirst().orElseThrow();
                require(s.seats().stream().filter(seat->seat.seat()==ally.seat()).findFirst().orElseThrow().handCount()==ally.handCount()+1,"Yiji recipient did not receive one physical card");
            }else if(YIJI){
                var original=before.view().snapshot().seats().stream().filter(SeatView::self).findFirst().orElseThrow();
                if(me.handCount()!=original.handCount()+2 || s.prompt()==null || s.prompt().typeOrdinal()!=PromptType.YIJI_GIVE.ordinal())return;
                var oldIds=original.hand().stream().map(CardView::instanceId).collect(java.util.stream.Collectors.toSet());
                var newIds=me.hand().stream().map(CardView::instanceId).filter(id->!oldIds.contains(id)).collect(java.util.stream.Collectors.toSet());
                require(newIds.size()==2 && s.prompt().choices().stream().map(CardView::instanceId).collect(java.util.stream.Collectors.toSet()).equals(newIds),"yiji retained cards are not precisely the new draw");
            }else if(HARVEST){long selected=before.view().snapshot().prompt().choices().getFirst().instanceId();
                if(me.hand().stream().noneMatch(card->card.instanceId()==selected) || s.processing().stream().anyMatch(card->card.instanceId()==selected))return;
            }else if(PENDING_TRICK){if(s.counterspellWindow()!=null && s.counterspellWindow().windowId()==before.view().snapshot().counterspellWindow().windowId())return;}
            else if(s.currentSeat()==s.viewerSeat() && s.phaseOrdinal()==Phase.PLAY.ordinal() && s.turnNumber()==before.view().snapshot().turnNumber())return;
            // Network state can advance before the framebuffer renders it. Capture a settled frame.
            if(acceptedAfterMillis==0){acceptedAfterMillis=System.currentTimeMillis()+750;return;}
            if(System.currentTimeMillis()<acceptedAfterMillis)return;
            save(c,"accepted",screen);
            if(ATTACK_YIJI_GIVE){
                yijiTransferAccepted=true;int nonce=MatchClient.nonceForSmoke();screen.clickForSmoke("decline");require(MatchClient.nonceForSmoke()==nonce+1,"Yiji keep submitted more than once");
                ProbeMod.LOG.info("SGS_YIJI_KEEP_MOUSE remaining=1 real_hit_test=true single_nonce=true");return;
            }
            done=true;
            ProbeMod.LOG.info("SGS_WORLD_RESTART_PASS match={} room={} physical_hand_preserved=true fresh_nonce_accepted=true",view.matchId(),view.roomId());return;
        }
        require(step>=8,"match arrived before real lobby start");
        if(OpeningHandSmokeChecks.advance(c,screen,!PENDING_TRICK))return;
        require(s.seats().size()==(MILITARY_SIX?6:4) && s.seats().stream().filter(SeatView::human).count()==1,"ordinary roster changed");
        if(parked)return;
        if(s.status()==0){
            if(IdentityIntroSmokeChecks.advance(c,screen))return;
            // Other seats keep publishing selection revisions after our pick succeeds.
            // A selected local hero is authoritative: never press its now-disabled button again.
            if(me.heroId()!=null && !me.heroId().isBlank())return;
            if(!pendingHero.isEmpty()){
                boolean rejected=screen.errorsForSmoke()>selectionErrors;
                boolean taken=!s.heroCandidates().contains(pendingHero) || s.seats().stream().anyMatch(p->!p.self() && pendingHero.equals(p.heroId()));
                if(!rejected && !taken)return;
                ProbeMod.LOG.info("SGS_WORLD_RESTART_HERO_RETRY previous={} rejected={} taken={} reason={}",pendingHero,rejected,taken,screen.errorForSmoke());
                pendingHero="";
            }
            var available=s.heroCandidates().stream().filter(id->s.seats().stream().noneMatch(p->!p.self() && id.equals(p.heroId()))).toList();
            if(available.isEmpty())return;
            String requested=System.getProperty("sgs.worldRestartHero","");if(!requested.isEmpty())require(available.contains(requested),"requested hero was not legally offered: "+requested+" "+available);
            String hero=!requested.isEmpty()?requested:available.contains("zhaoyun")?"zhaoyun":available.getFirst();
            selectionErrors=screen.errorsForSmoke();screen.pressForSmoke("hero:"+hero);screen.pressForSmoke("hero-confirm");pendingHero=hero;
            ProbeMod.LOG.info("SGS_WORLD_RESTART_HERO chosen={} offered={} legal_offer=true",hero,s.heroCandidates());return;
        }
        require(s.status()==1 && me.alive(),"match finished before a safe idle checkpoint");
        if(KUROU_RESCUE && s.prompt()!=null && s.prompt().askerSeat()==s.viewerSeat() && s.prompt().subjectSeat()==s.viewerSeat()
            && s.prompt().typeOrdinal()==PromptType.RESCUE_TAO.ordinal() && me.heroId().equals(HP_LOSS_HERO) && me.hp()==0 && s.processing().isEmpty()){
            require(me.hand().stream().anyMatch(ca->ca.typeOrdinal()==CardType.TAO.ordinal() || ca.typeOrdinal()==CardType.JIU.ordinal()),"ordinary seed has no self-rescue card");
            if(parkedWindow!=s.prompt().promptId()){parkedWindow=s.prompt().promptId();parkAfterMillis=System.currentTimeMillis()+15000;stableFrames=0;}
            if(System.currentTimeMillis()<parkAfterMillis||++stableFrames<5)return;
            save(c,"before",screen);parked=true;ProbeMod.LOG.info("SGS_WORLD_RESTART_PARKED match={} room={} holding_real_hp_loss=true token={}",view.matchId(),view.roomId(),parkedWindow);return;
        }
        if(TARGET_RULE && s.prompt()!=null && s.prompt().askerSeat()==s.viewerSeat() && s.prompt().typeOrdinal()==PromptType.SKILL_INVOKE.ordinal() && s.prompt().skillId().equals(TARGET_RULE_SKILL)){
            require(s.processing().size()==1 && me.heroId().equals(TARGET_RULE_HERO),"rule must retain one actual attack cost");
            if(parkedWindow!=s.prompt().promptId()){parkedWindow=s.prompt().promptId();parkAfterMillis=System.currentTimeMillis()+15000;stableFrames=0;}
            if(System.currentTimeMillis()<parkAfterMillis || ++stableFrames<5)return;
            save(c,"before",screen);parked=true;ProbeMod.LOG.info("SGS_WORLD_RESTART_PARKED match={} room={} holding_real_tieji=true token={}",view.matchId(),view.roomId(),parkedWindow);return;
        }
        if(GUANSHI && s.prompt()!=null && s.prompt().askerSeat()==s.viewerSeat() && s.prompt().skillId().equals("guanshi")){
            if(s.prompt().typeOrdinal()==PromptType.WEAPON_COST_FIRST.ordinal()){
                if(guanshiFirstToken==s.prompt().promptId())return;guanshiFirstToken=s.prompt().promptId();guanshiFirstCard=s.prompt().choices().getFirst().instanceId();guanshiFirstHand=me.handCount();
                screen.clickForSmoke("skill-choice:"+guanshiFirstCard);int nonce=MatchClient.nonceForSmoke();screen.clickForSmoke("skill-choice-confirm");require(MatchClient.nonceForSmoke()==nonce+1 && !screen.enabledForSmoke("skill-choice-confirm"),"guanshi first submit duplicate/unlocked");
                ProbeMod.LOG.info("SGS_GUANSHI_FIRST_MOUSE card={} tentative=true real_hit_test=true single_nonce=true",guanshiFirstCard);return;
            }
            if(s.prompt().typeOrdinal()==PromptType.WEAPON_COST_SECOND.ordinal()){
                require(me.handCount()==guanshiFirstHand && me.hand().stream().anyMatch(ca->ca.instanceId()==guanshiFirstCard),"guanshi first cost was prematurely paid");
                require(s.prompt().choices().stream().noneMatch(ca->ca.instanceId()==guanshiFirstCard),"guanshi duplicated first choice");
                if(parkedWindow!=s.prompt().promptId()){parkedWindow=s.prompt().promptId();parkAfterMillis=System.currentTimeMillis()+15000;stableFrames=0;}
                if(System.currentTimeMillis()<parkAfterMillis||++stableFrames<5)return;save(c,"before",screen);parked=true;ProbeMod.LOG.info("SGS_WORLD_RESTART_PARKED match={} room={} holding_real_guanshi_second=true token={}",view.matchId(),view.roomId(),parkedWindow);return;
            }
        }
        if(HANBING && s.prompt()!=null && s.prompt().askerSeat()==s.viewerSeat() && s.prompt().typeOrdinal()==PromptType.CARD_ZONE_SELECT.ordinal() && s.prompt().skillId().equals("hanbing")){
            if(hanbingFirst<0){hanbingFirst=s.prompt().promptId();screen.clickForSmoke("zone-hand:0");screen.clickForSmoke("skill-choice-confirm");ProbeMod.LOG.info("SGS_HANBING_FIRST_MOUSE prompt={} opaque_position=0 real_hit_test=true",hanbingFirst);return;}
            if(s.prompt().promptId()==hanbingFirst)return;
            if(parkedWindow!=s.prompt().promptId()){parkedWindow=s.prompt().promptId();parkAfterMillis=System.currentTimeMillis()+15000;stableFrames=0;}
            if(System.currentTimeMillis()<parkAfterMillis||++stableFrames<5)return;
            require(s.prompt().hiddenHandAvailable(),"hanbing second stage has no actual hand");save(c,"before",screen);parked=true;ProbeMod.LOG.info("SGS_WORLD_RESTART_PARKED match={} room={} holding_real_hanbing_second=true token={}",view.matchId(),view.roomId(),parkedWindow);return;
        }
        if(QINGLONG_JIJIANG && s.prompt()!=null && s.prompt().askerSeat()==s.viewerSeat() && s.prompt().typeOrdinal()==PromptType.SKILL_INVOKE.ordinal() && s.prompt().skillId().equals("jijiang")){
            require(me.heroId().equals("liubei") && me.identityOrdinal()==dev.sgs.core.Identity.LORD.ordinal() && s.viewerSeat()==0,"native Jijiang requires actually assigned lord Liu Bei");require(s.processing().size()==1,"Qinglong Jijiang original cost missing");
            if(parkedWindow!=s.prompt().promptId()){parkedWindow=s.prompt().promptId();parkAfterMillis=System.currentTimeMillis()+15000;stableFrames=0;}
            if(System.currentTimeMillis()<parkAfterMillis||++stableFrames<5)return;
            save(c,"before",screen);parked=true;ProbeMod.LOG.info("SGS_WORLD_RESTART_PARKED match={} room={} holding_real_qinglong_jijiang=true token={}",view.matchId(),view.roomId(),parkedWindow);return;
        }
        if(QINGLONG && s.prompt()!=null && s.prompt().askerSeat()==s.viewerSeat() && s.prompt().typeOrdinal()==PromptType.QINGLONG_SHA.ordinal()){
            require(s.processing().size()==1,"qinglong prematurely finished original attack");
            if(parkedWindow!=s.prompt().promptId()){parkedWindow=s.prompt().promptId();parkAfterMillis=System.currentTimeMillis()+15000;stableFrames=0;}
            if(System.currentTimeMillis()<parkAfterMillis||++stableFrames<5)return;
            save(c,"before",screen);parked=true;ProbeMod.LOG.info("SGS_WORLD_RESTART_PARKED match={} room={} holding_real_qinglong_follow=true token={}",view.matchId(),view.roomId(),parkedWindow);return;
        }
        if(CIXIONG && s.prompt()!=null && s.prompt().askerSeat()==s.viewerSeat() && s.prompt().typeOrdinal()==PromptType.SKILL_INVOKE.ordinal() && s.prompt().skillId().equals("cixiong")){
            require(s.processing().size()==1,"cixiong must retain the actual attack cost");
            if(parkedWindow!=s.prompt().promptId()){parkedWindow=s.prompt().promptId();parkAfterMillis=System.currentTimeMillis()+15000;stableFrames=0;}
            if(System.currentTimeMillis()<parkAfterMillis||++stableFrames<5)return;
            save(c,"before",screen);parked=true;ProbeMod.LOG.info("SGS_WORLD_RESTART_PARKED match={} room={} holding_real_cixiong=true token={}",view.matchId(),view.roomId(),parkedWindow);return;
        }
        if(BORROW && s.prompt()!=null && s.prompt().askerSeat()==s.viewerSeat() && s.prompt().typeOrdinal()==PromptType.DODGE_SHA.ordinal()
            && s.currentSeat()==s.viewerSeat() && s.processing().size()==2 && s.processing().stream().anyMatch(ca->ca.typeOrdinal()==CardType.JIEDAO.ordinal()) && s.processing().stream().anyMatch(ca->ca.typeOrdinal()==CardType.SHA.ordinal())){
            require(me.hp()>1&&!me.chained(),"borrowed strike fixture requires a surviving unchained recipient");
            if(parkedWindow!=s.prompt().promptId()){parkedWindow=s.prompt().promptId();parkAfterMillis=System.currentTimeMillis()+15000;stableFrames=0;}
            if(System.currentTimeMillis()<parkAfterMillis||++stableFrames<5)return;
            save(c,"before",screen);parked=true;ProbeMod.LOG.info("SGS_WORLD_RESTART_PARKED match={} room={} holding_real_borrowed_attack=true token={}",view.matchId(),view.roomId(),parkedWindow);return;
        }
        if(FIRE_PAY && s.prompt()!=null && s.prompt().askerSeat()==s.viewerSeat() && s.prompt().typeOrdinal()==PromptType.HUOGONG_PAY.ordinal() && s.prompt().choices().size()==1 && s.processing().stream().anyMatch(ca->ca.typeOrdinal()==CardType.HUOGONG.ordinal())){
            require(s.prompt().subjectSeat()==s.viewerSeat(),"expected legal self fire payment");
            if(parkedWindow!=s.prompt().promptId()){parkedWindow=s.prompt().promptId();parkAfterMillis=System.currentTimeMillis()+15000;stableFrames=0;}
            if(System.currentTimeMillis()<parkAfterMillis||++stableFrames<5)return;
            save(c,"before",screen);parked=true;ProbeMod.LOG.info("SGS_WORLD_RESTART_PARKED match={} room={} holding_real_reveal_cost=true token={}",view.matchId(),view.roomId(),parkedWindow);return;
        }
        if(ATTACK_DODGE && s.prompt()!=null && s.prompt().askerSeat()==s.viewerSeat() && s.prompt().typeOrdinal()==PromptType.DODGE_SHA.ordinal()
            && me.hand().stream().anyMatch(card->(card.suitOrdinal()==dev.sgs.core.card.Suit.SPADE.ordinal()||card.suitOrdinal()==dev.sgs.core.card.Suit.CLUB.ordinal())&&card.typeOrdinal()!=CardType.SHAN.ordinal())
            && s.processing().size()==1 && Set.of(CardType.SHA.ordinal(),CardType.HUOSHA.ordinal(),CardType.LEISHA.ordinal()).contains(s.processing().getFirst().typeOrdinal())){
            if(parkedWindow!=s.prompt().promptId()){parkedWindow=s.prompt().promptId();parkAfterMillis=System.currentTimeMillis()+15000;stableFrames=0;}
            if(System.currentTimeMillis()<parkAfterMillis||++stableFrames<5)return;
            save(c,"before",screen);parked=true;ProbeMod.LOG.info("SGS_WORLD_RESTART_PARKED match={} room={} holding_real_attack=true token={}",view.matchId(),view.roomId(),parkedWindow);return;
        }
        if(FANKUI && s.prompt()!=null && s.prompt().askerSeat()==s.viewerSeat() && s.prompt().typeOrdinal()==PromptType.FANKUI_TAKE.ordinal()
            && (ACTIVE_FANKUI?s.processing().isEmpty() && s.currentSeat()!=s.viewerSeat()
                && s.seats().stream().anyMatch(seat->seat.seat()==s.currentSeat()&&seat.heroId().equals("diaochan"))
                :s.processing().stream().anyMatch(card->Set.of(CardType.NANMAN.ordinal(),CardType.WANJIAN.ordinal(),CardType.JUEDOU.ordinal()).contains(card.typeOrdinal())))){
            if(parkedWindow!=s.prompt().promptId()){parkedWindow=s.prompt().promptId();parkAfterMillis=System.currentTimeMillis()+15000;stableFrames=0;}
            if(System.currentTimeMillis()<parkAfterMillis || ++stableFrames<5)return;
            save(c,"before",screen);parked=true;ProbeMod.LOG.info("SGS_WORLD_RESTART_PARKED match={} room={} holding_real_fankui=true token={}",view.matchId(),view.roomId(),parkedWindow);return;
        }
        if(GANGLIE && s.prompt()!=null && s.prompt().askerSeat()==s.viewerSeat() && s.prompt().typeOrdinal()==PromptType.SKILL_INVOKE.ordinal() && s.prompt().skillId().equals("ganglie")
            && s.processing().stream().anyMatch(card->Set.of(CardType.NANMAN.ordinal(),CardType.WANJIAN.ordinal(),CardType.JUEDOU.ordinal()).contains(card.typeOrdinal()))){
            if(parkedWindow!=s.prompt().promptId()){parkedWindow=s.prompt().promptId();parkAfterMillis=System.currentTimeMillis()+15000;stableFrames=0;}
            if(System.currentTimeMillis()<parkAfterMillis || ++stableFrames<5)return;
            save(c,"before",screen);parked=true;ProbeMod.LOG.info("SGS_WORLD_RESTART_PARKED match={} room={} holding_real_ganglie=true token={}",view.matchId(),view.roomId(),parkedWindow);return;
        }
        if(ATTACK_YIJI_GIVE && s.prompt()!=null && s.prompt().askerSeat()==s.viewerSeat() && s.prompt().typeOrdinal()==PromptType.YIJI_GIVE.ordinal() && s.prompt().choices().size()==2
            && s.processing().size()==1 && Set.of(CardType.SHA.ordinal(),CardType.HUOSHA.ordinal(),CardType.LEISHA.ordinal()).contains(s.processing().getFirst().typeOrdinal())){
            if(parkAfterMillis==0){parkAfterMillis=System.currentTimeMillis()+15000;parkedWindow=s.prompt().promptId();stableFrames=0;ProbeMod.LOG.info("SGS_YIJI_GIVE_WAIT millis=15000 native_new_cards_only=true");}
            require(s.prompt().promptId()==parkedWindow,"Yiji distribution changed while held");
            if(System.currentTimeMillis()<parkAfterMillis||++stableFrames<5)return;save(c,"before",screen);parked=true;
            ProbeMod.LOG.info("SGS_WORLD_RESTART_PARKED match={} room={} holding_real_yiji_distribution=true token={}",view.matchId(),view.roomId(),parkedWindow);return;
        }
        if(YIJI && !ATTACK_YIJI_GIVE && s.prompt()!=null && s.prompt().askerSeat()==s.viewerSeat() && s.prompt().typeOrdinal()==PromptType.SKILL_INVOKE.ordinal() && s.prompt().skillId().equals("yiji")
            && (ATTACK_YIJI?s.processing().size()==1 && me.heroId().equals("guojia") && s.currentSeat()!=s.viewerSeat() && Set.of(CardType.SHA.ordinal(),CardType.HUOSHA.ordinal(),CardType.LEISHA.ordinal()).contains(s.processing().getFirst().typeOrdinal())
                :s.processing().stream().anyMatch(card->Set.of(CardType.NANMAN.ordinal(),CardType.WANJIAN.ordinal(),CardType.JUEDOU.ordinal()).contains(card.typeOrdinal())))){
            if(parkedWindow!=s.prompt().promptId()){parkedWindow=s.prompt().promptId();parkAfterMillis=System.currentTimeMillis()+15000;stableFrames=0;}
            if(System.currentTimeMillis()<parkAfterMillis || ++stableFrames<5)return;
            save(c,"before",screen);parked=true;ProbeMod.LOG.info("SGS_WORLD_RESTART_PARKED match={} room={} holding_real_yiji=true token={}",view.matchId(),view.roomId(),parkedWindow);return;
        }
        if(HARVEST && s.prompt()!=null && s.prompt().askerSeat()==s.viewerSeat() && s.prompt().typeOrdinal()==PromptType.WUGU_PICK.ordinal()){
            if(parkedWindow!=s.prompt().promptId()){parkedWindow=s.prompt().promptId();parkAfterMillis=System.currentTimeMillis()+15000;stableFrames=0;}
            if(System.currentTimeMillis()<parkAfterMillis || ++stableFrames<5)return;
            require(!s.prompt().choices().isEmpty() && s.processing().stream().anyMatch(card->card.typeOrdinal()==CardType.WUGU.ordinal()),"harvest is not real processing");
            save(c,"before",screen);parked=true;
            ProbeMod.LOG.info("SGS_WORLD_RESTART_PARKED match={} room={} holding_real_harvest=true token={} choices={}",view.matchId(),view.roomId(),parkedWindow,s.prompt().choices().size());return;
        }
        if(PENDING_TRICK && !BORROW && !FIRE_PAY && !HARVEST && !YIJI && !FANKUI && !GANGLIE && !ATTACK_DODGE && !CIXIONG && !HANBING && !GUANSHI && s.counterspellWindow()!=null && s.prompt()!=null && s.prompt().askerSeat()==s.viewerSeat()
            && s.prompt().typeOrdinal()==PromptType.WUXIE_COUNTER.ordinal() && s.processing().stream().anyMatch(card->
                FOCUS_TRICK.isEmpty()?(card.typeOrdinal()==CardType.WUZHONG.ordinal() || card.typeOrdinal()==CardType.GUOHE.ordinal() || card.typeOrdinal()==CardType.SHUNSHOU.ordinal()):card.typeOrdinal()==CardType.valueOf(FOCUS_TRICK.toUpperCase(java.util.Locale.ROOT)).ordinal())){
            if(parkedWindow!=s.counterspellWindow().windowId()){parkedWindow=s.counterspellWindow().windowId();parkAfterMillis=System.currentTimeMillis()+15000;stableFrames=0;}
            if(System.currentTimeMillis()<parkAfterMillis || ++stableFrames<5)return;
            require(s.prompt().deadlineMillis()-System.currentTimeMillis()>180000,"pending trick budget too small");
            save(c,"before",screen);parked=true;
            ProbeMod.LOG.info("SGS_WORLD_RESTART_PARKED match={} room={} holding_real_trick=true window={} token={}",view.matchId(),view.roomId(),parkedWindow,s.prompt().promptId());return;
        }
        if(s.prompt()!=null && s.prompt().askerSeat()==s.viewerSeat()){
            var p=s.prompt();if(lastPrompt==p.promptId())return;
            var type=PromptType.values()[p.typeOrdinal()];
            if(ATTACK_YIJI_GIVE && type==PromptType.SKILL_INVOKE && p.skillId().equals("yiji") && s.processing().size()==1
                && Set.of(CardType.SHA.ordinal(),CardType.HUOSHA.ordinal(),CardType.LEISHA.ordinal()).contains(s.processing().getFirst().typeOrdinal())){
                screen.clickForSmoke("invoke");lastPrompt=p.promptId();return;
            }
            if(GUANSHI && type==PromptType.SKILL_INVOKE && p.skillId().equals("guanshi")){screen.clickForSmoke("invoke");lastPrompt=p.promptId();return;}
            if(HANBING && type==PromptType.SKILL_INVOKE && p.skillId().equals("hanbing")){screen.clickForSmoke("invoke");lastPrompt=p.promptId();return;}
            if(HANBING && type==PromptType.CARD_ZONE_SELECT && p.skillId().equals("guohe")){
                var subject=s.seats().stream().filter(seat->seat.seat()==p.subjectSeat()).findFirst().orElseThrow();int position=Math.min(2,subject.handCount()-1);require(position>=0,"preparation target has no hand");screen.clickForSmoke("zone-hand:"+position);screen.clickForSmoke("skill-choice-confirm");lastPrompt=p.promptId();ProbeMod.LOG.info("SGS_HANBING_PREPARE_GUOHE_MOUSE opaque_position={} real_hit_test=true",position);return;
            }
            if(FIRE_PAY && type==PromptType.HUOGONG_REVEAL){
                var card=me.hand().getFirst();int index=me.hand().indexOf(card);var bounds=screen.handBoundsForSmoke(index);if(bounds==null)return;
                screen.mouseClicked(bounds[0]+bounds[2]/2d,bounds[1]+bounds[3]/2d,0);screen.clickForSmoke("respond");lastPrompt=p.promptId();return;
            }
            if(type==PromptType.CARD_ZONE_SELECT || type==PromptType.FANKUI_TAKE){
                screen.clickForSmoke(p.hiddenHandAvailable()?"zone-hand:0":"skill-choice:"+p.choices().getFirst().instanceId());
                screen.clickForSmoke("skill-choice-confirm");
            }
            else if((BORROW||YIJI||FANKUI||GANGLIE) && Set.of(PromptType.DODGE_SHA,PromptType.DUEL_SHA,PromptType.RESPOND_SHA_NANMAN).contains(type))screen.pressForSmoke("decline");
            else if(type==PromptType.WUGU_PICK && !p.choices().isEmpty())screen.pressForSmoke("choice:0");
            else if(type==PromptType.FANJIAN_SUIT)screen.pressForSmoke("suit:0");
            else if(type==PromptType.SKILL_INVOKE || !p.choices().isEmpty() || !p.allowedTargetSeats().isEmpty())screen.pressForSmoke("decline");
            else{
                var card=me.hand().stream().filter(h->p.allowedTypeOrdinals().contains(h.typeOrdinal())).findFirst();
                if(card.isPresent()){screen.selectForSmoke(List.of(card.get().instanceId()),-1);screen.pressForSmoke("respond");}
                else screen.pressForSmoke("decline");
            }
            lastPrompt=p.promptId();ProbeMod.LOG.info("SGS_WORLD_RESTART_RESPONSE prompt={} type={} native_ui=true",p.promptId(),type);return;
        }
        if(s.prompt()!=null || s.counterspellWindow()!=null || !s.processing().isEmpty() || s.currentSeat()!=s.viewerSeat())return;
        if(PENDING_TRICK){
            if(sentRevision==view.revision())return;
            if(s.phaseOrdinal()==Phase.DISCARD.ordinal()){
                int excess=Math.max(0,me.handCount()-Math.max(0,me.hp()));
                long keep=BORROW?me.hand().stream().filter(card->card.typeOrdinal()==CardType.JIEDAO.ordinal()).mapToLong(CardView::instanceId).findFirst().orElse(-1):ATTACK_DODGE?me.hand().stream().filter(card->(card.suitOrdinal()==0||card.suitOrdinal()==2)&&card.typeOrdinal()!=CardType.SHAN.ordinal()).mapToLong(CardView::instanceId).findFirst().orElse(-1):-1;
                screen.selectForSmoke(me.hand().stream().filter(card->card.instanceId()!=keep).limit(excess).map(CardView::instanceId).toList(),-1);screen.pressForSmoke("discard");sentRevision=view.revision();return;
            }
            if(s.phaseOrdinal()!=Phase.PLAY.ordinal())return;
            if(KUROU_RESCUE){
                require(me.heroId().equals(HP_LOSS_HERO) && me.hp()>0,"HP-loss preparation is not the living declared skill owner");
                if(HP_LOSS_WEAPON && me.hp()==1 && me.weapon()==null){
                    if(kurouWeaponSent)return;
                    var weapon=me.hand().stream().filter(ca->ca.typeOrdinal()==CardType.ZHUQUE.ordinal()).findFirst().orElseThrow();
                    var bounds=screen.handBoundsForSmoke(me.hand().indexOf(weapon));if(bounds==null)return;
                    require(screen.mouseClicked(bounds[0]+bounds[2]/2d,bounds[1]+bounds[3]/2d,0),"ordinary weapon hand click failed");
                    screen.clickForSmoke("play");kurouWeaponSent=true;sentRevision=view.revision();
                    ProbeMod.LOG.info("SGS_KUROU_EQUIP_MOUSE card={} actual_hand_click=true real_hit_test=true",weapon.instanceId());return;
                }
                if(kurouSentHp==me.hp())return;
                int nonce=MatchClient.nonceForSmoke();screen.clickForSmoke(HP_LOSS_SKILL);require(MatchClient.nonceForSmoke()==nonce+1,"HP-loss activation duplicated submission");
                kurouSentHp=me.hp();sentRevision=view.revision();ProbeMod.LOG.info("SGS_KUROU_PREPARE_MOUSE hp_before={} real_skill_button=true single_nonce=true",me.hp());return;
            }
            if(TARGET_RULE){
                require(me.heroId().equals(TARGET_RULE_HERO),"source is not the legally selected rule owner");
                for(var card:me.hand())if(Set.of(CardType.SHA.ordinal(),CardType.HUOSHA.ordinal(),CardType.LEISHA.ordinal()).contains(card.typeOrdinal())){
                    require(s.selfTargetAvailability().stream().filter(a->a.cardId()==card.instanceId()&&a.targetSeat()==1).allMatch(a->a.reason().isEmpty()),"seed attack target unavailable");
                    int index=me.hand().indexOf(card);var bounds=screen.handBoundsForSmoke(index);if(bounds==null)return;
                    require(screen.mouseClicked(bounds[0]+bounds[2]/2d,bounds[1]+bounds[3]/2d,0),"physical Sha hand click failed");screen.clickSeatForSmoke(1);screen.clickForSmoke("play");sentRevision=view.revision();ProbeMod.LOG.info("SGS_TIEJI_ATTACK_MOUSE card={} target=1 real_hit_test=true",card.instanceId());return;
                }
                throw new IllegalStateException("normal seed has no real Sha");
            }
            if(QINGLONG){
                require(qinglongAttackTurn!=s.turnNumber(),"normal attack did not reach qinglong; no retry of exhausted Sha");
                if(me.weapon()==null || me.weapon().typeOrdinal()!=CardType.QINGLONG.ordinal()){
                    var weapon=me.hand().stream().filter(ca->ca.typeOrdinal()==CardType.QINGLONG.ordinal()).findFirst();if(weapon.isPresent()){screen.selectForSmoke(List.of(weapon.get().instanceId()),-1);screen.clickForSmoke("play");sentRevision=view.revision();return;}
                }else for(var card:me.hand())if(Set.of(CardType.SHA.ordinal(),CardType.HUOSHA.ordinal(),CardType.LEISHA.ordinal()).contains(card.typeOrdinal())){
                    var enemy=s.seats().stream().filter(p->p.seat()==1 && p.alive()&&!p.self()&&(QINGLONG_JIJIANG || p.teamId()!=me.teamId())).filter(p->s.selfTargetAvailability().stream().filter(h->h.cardId()==card.instanceId()&&h.targetSeat()==p.seat()).allMatch(h->h.reason().isEmpty())).findFirst();
                    if(enemy.isPresent()){qinglongAttackTurn=s.turnNumber();screen.selectForSmoke(List.of(card.instanceId()),enemy.get().seat());screen.clickForSmoke("play");sentRevision=view.revision();return;}
                }
                throw new IllegalStateException("normal qinglong source has no actual weapon or legal attack");
            }
            if(GUANSHI){
                require(guanshiAttackTurn!=s.turnNumber(),"normal attack did not reach guanshi; ending smoke instead of retrying an exhausted Sha");
                if(me.weapon()==null || me.weapon().typeOrdinal()!=CardType.GUANSHI.ordinal()){
                    var weapon=me.hand().stream().filter(ca->ca.typeOrdinal()==CardType.GUANSHI.ordinal()).findFirst();if(weapon.isPresent()){screen.selectForSmoke(List.of(weapon.get().instanceId()),-1);screen.clickForSmoke("play");sentRevision=view.revision();return;}
                }else for(var card:me.hand())if(Set.of(CardType.SHA.ordinal(),CardType.HUOSHA.ordinal(),CardType.LEISHA.ordinal()).contains(card.typeOrdinal())){
                    var enemy=s.seats().stream().filter(p->p.alive()&&!p.self()&&p.teamId()!=me.teamId()).filter(p->s.selfTargetAvailability().stream().filter(h->h.cardId()==card.instanceId()&&h.targetSeat()==p.seat()).allMatch(h->h.reason().isEmpty())).findFirst();
                    if(enemy.isPresent()){guanshiAttackTurn=s.turnNumber();screen.selectForSmoke(List.of(card.instanceId()),enemy.get().seat());screen.clickForSmoke("play");sentRevision=view.revision();return;}
                }
                screen.pressForSmoke("end");sentRevision=view.revision();return;
            }
            if(HANBING){
                if(me.weapon()==null || me.weapon().typeOrdinal()!=CardType.HANBING.ordinal()){
                    var weapon=me.hand().stream().filter(ca->ca.typeOrdinal()==CardType.HANBING.ordinal()).findFirst();if(weapon.isPresent()){screen.selectForSmoke(List.of(weapon.get().instanceId()),-1);screen.clickForSmoke("play");sentRevision=view.revision();return;}
                }else{
                    var enemy=s.seats().stream().filter(p->p.alive()&&!p.self()&&p.teamId()!=me.teamId()).findFirst();
                    if(enemy.isPresent()){
                        if(!hanbingDismantled){var prep=me.hand().stream().filter(ca->ca.typeOrdinal()==CardType.GUOHE.ordinal()).findFirst();if(prep.isPresent()){screen.selectForSmoke(List.of(prep.get().instanceId()),enemy.get().seat());screen.clickForSmoke("play");hanbingDismantled=true;sentRevision=view.revision();return;}}
                        for(var card:me.hand())if(Set.of(CardType.SHA.ordinal(),CardType.HUOSHA.ordinal(),CardType.LEISHA.ordinal()).contains(card.typeOrdinal())){screen.selectForSmoke(List.of(card.instanceId()),enemy.get().seat());screen.clickForSmoke("play");sentRevision=view.revision();return;}
                    }
                }
                screen.pressForSmoke("end");sentRevision=view.revision();return;
            }
            if(CIXIONG){
                if(me.weapon()==null || me.weapon().typeOrdinal()!=CardType.CIXIONG.ordinal()){
                    var weapon=me.hand().stream().filter(ca->ca.typeOrdinal()==CardType.CIXIONG.ordinal()).findFirst();
                    if(weapon.isPresent()){screen.selectForSmoke(List.of(weapon.get().instanceId()),-1);screen.clickForSmoke("play");sentRevision=view.revision();return;}
                }else for(var card:me.hand())if(Set.of(CardType.SHA.ordinal(),CardType.HUOSHA.ordinal(),CardType.LEISHA.ordinal()).contains(card.typeOrdinal())){
                    var target=s.seats().stream().filter(p->p.alive()&&!p.self()&&p.teamId()!=me.teamId()).filter(p->dev.sgs.core.hero.StandardHeroes.CATALOG.stream().anyMatch(h->h.id().equals(p.heroId())&&h.male()))
                        .filter(p->s.selfTargetAvailability().stream().filter(h->h.cardId()==card.instanceId()&&h.targetSeat()==p.seat()).allMatch(h->h.reason().isEmpty())).findFirst();
                    if(target.isPresent()){screen.selectForSmoke(List.of(card.instanceId()),target.get().seat());screen.clickForSmoke("play");sentRevision=view.revision();return;}
                }
                screen.pressForSmoke("end");sentRevision=view.revision();return;
            }
            if(BORROW){
                var card=me.hand().stream().filter(ca->ca.typeOrdinal()==CardType.JIEDAO.ordinal()).findFirst();
                var holder=s.seats().stream().filter(p->p.alive()&&!p.self()&&p.teamId()!=me.teamId()).filter(p->p.weapon()!=null&&Set.of(CardType.QINGGANG.ordinal(),CardType.QILIN_GONG.ordinal(),CardType.FANGTIAN.ordinal(),CardType.ZHUQUE.ordinal(),CardType.GUDING.ordinal()).contains(p.weapon().typeOrdinal())).findFirst();
                if(card.isPresent()&&holder.isPresent()){
                    var cost=card.get();var bounds=screen.handBoundsForSmoke(me.hand().indexOf(cost));if(bounds==null)return;
                    screen.mouseClicked(bounds[0]+bounds[2]/2d,bounds[1]+bounds[3]/2d,0);screen.clickSeatForSmoke(holder.get().seat());screen.clickSeatForSmoke(s.viewerSeat());screen.clickForSmoke("play");sentRevision=view.revision();
                    ProbeMod.LOG.info("SGS_BORROW_NATIVE_MOUSE card={} holder={} victim=self real_hit_test=true",cost.instanceId(),holder.get().seat());return;
                }
                screen.pressForSmoke("end");sentRevision=view.revision();return;
            }
            if(FIRE_PAY){
                var card=me.hand().stream().filter(ca->ca.typeOrdinal()==CardType.HUOGONG.ordinal()).findFirst().orElseThrow();
                require(me.handCount()>1,"self fire requires another real hand card");
                int index=me.hand().indexOf(card);var bounds=screen.handBoundsForSmoke(index);if(bounds==null)return;
                screen.mouseClicked(bounds[0]+bounds[2]/2d,bounds[1]+bounds[3]/2d,0);screen.clickSeatForSmoke(s.viewerSeat());screen.clickForSmoke("play");sentRevision=view.revision();return;
            }
            if(ATTACK_DODGE || ATTACK_YIJI){screen.pressForSmoke("end");sentRevision=view.revision();return;}
            for(var card:me.hand())if(HARVEST?card.typeOrdinal()==CardType.WUGU.ordinal():(YIJI||FANKUI||GANGLIE)&&card.typeOrdinal()==CardType.JUEDOU.ordinal() || card.typeOrdinal()==CardType.WUZHONG.ordinal() || card.typeOrdinal()==CardType.GUOHE.ordinal() || card.typeOrdinal()==CardType.SHUNSHOU.ordinal()){
                int target=-1;
                if(card.typeOrdinal()!=CardType.WUZHONG.ordinal() && card.typeOrdinal()!=CardType.WUGU.ordinal()){
                    var valid=s.seats().stream().filter(p->p.alive()&&!p.self()).filter(p->s.selfTargetAvailability().stream().filter(h->h.cardId()==card.instanceId()&&h.targetSeat()==p.seat()).allMatch(h->h.reason().isEmpty())).findFirst();
                    if(valid.isEmpty())continue;target=valid.get().seat();
                }
                screen.selectForSmoke(List.of(card.instanceId()),target);screen.pressForSmoke("play");sentRevision=view.revision();return;
            }
            screen.pressForSmoke("end");sentRevision=view.revision();return;
        }
        if(s.phaseOrdinal()!=Phase.PLAY.ordinal())return;
        if(!ViewAsUiSmokeChecks.tick(c,screen))return;
        if(parkAfterMillis==0){parkAfterMillis=System.currentTimeMillis()+15000;
            ProbeMod.LOG.info("SGS_WORLD_RESTART_IDLE_WAIT millis=15000 genuine_deadline_consumption=true");}
        if(System.currentTimeMillis()<parkAfterMillis)return;
        if(++stableFrames<5)return;
        require(view.roomId()==room && view.nonceFloor()>0,"room identity or selection nonce missing");
        require(s.phaseDeadlineMillis()-System.currentTimeMillis()>180000,"insufficient real turn time for process restart");
        save(c,"before",screen);parked=true;
        ProbeMod.LOG.info("SGS_WORLD_RESTART_PARKED match={} room={} holding_real_play=true",view.matchId(),view.roomId());
    }
}
