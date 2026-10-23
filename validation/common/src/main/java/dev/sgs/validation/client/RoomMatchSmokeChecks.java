package dev.sgs.validation.client;

import dev.sgs.core.Phase;
import dev.sgs.core.card.CardType;
import dev.sgs.core.card.Suit;
import dev.sgs.core.card.Rank;
import dev.sgs.core.match.dto.MatchViews.*;
import static dev.sgs.core.match.dto.MatchViews.HIDDEN;
import dev.sgs.validation.ProbeMod;
import java.util.*;
import net.blay09.mods.balm.api.Balm;
import net.blay09.mods.balm.api.event.TickPhase;
import net.blay09.mods.balm.api.event.TickType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/** Real client driver using only its authorized view and actual UI buttons. Not a human test. */
public final class RoomMatchSmokeChecks {
    private static int ticks,stage,errors,lastTurn=-1;
    private static final Map<Integer,Integer> deathCaptureFrames=new HashMap<>();
    private static final Set<Integer> chainReleaseCaptures=new HashSet<>();
    private static final Set<String> motionCaptures=new HashSet<>();
    private static long sentRevision=-1,lastCard=-1;
    private static final Set<Long> blocked=new HashSet<>();
    private static boolean done;
    private static int plays,prompts;
    private static boolean fangtianPlayRecorded;
    private static int skillInvocations;
    private static int jianxiongConfirmed;
    private static Set<Long> pendingJianxiong=Set.of();
    private static int skillProbeStage,skillProbeFrames,gainFrames;
    private static int invalidSkillNonce;
    private static long skillProbeId;
    private static boolean capturedSkillGain;
    private static int hujiaFrames;
    private static boolean capturedHujia;
    private static int resultPlayers,resultOutcome;
    private static int resultFrames;
    private static int checkedViews;
    private static int processingViews,publicMoveViews,processingFrames;
    private static boolean capturedProcessing;
    private static int chainViews,chainDamageViews;
    private static boolean capturedChain;
    private static int chainCaptureFrames;
    private static int wuguConfirmed,pendingWuguTurn;
    private static long pendingWugu=-1;
    private static String expectedWuguLog="";
    private static long offerSeen=-1;
    private static int offerFrames;
    private static boolean capturedOffer, capturedSelectedTarget;
    private static long selectedTargetCard=-1;
    private static int selectedTargetFrames;
    private static int heroOfferCaptureFrames;
    private static List<String> reservedHeroOffer;
    private static boolean heroAssignmentChecked;
    private static long zoneSeen=-1,zoneSelectedPrompt=-1;
    private static int zoneFrames,zoneTarget=-1,zoneBeforeCount;
    private static boolean capturedZone,confirmedZone;
    private static int zoneHideStage,zoneHideFrames;
    private static long zoneHidePrompt,zoneHideDeadline,zoneHideChoice;
    private static int wuxieScopeFrames;
    private static boolean capturedWuxieScope;
    private static int wuxieScopeErrors;
    private static long observedWuxieWindow=-1;
    private static final String ROLE=System.getProperty("sgs.matchRole","");
    private static final int HUMANS=ROLE.isEmpty()?1:2;
    private static final boolean TEAM=Boolean.getBoolean("sgs.teamSmoke");
    private static int teamSharedViews,teamPrivateViews,teamFrames;
    private static boolean teamRevoked,teamCaptured;
    public static void initialize(){AutoplaySmokeChecks.initialize();if(Boolean.getBoolean("sgs.matchSmoke"))Balm.getEvents().onTickEvent(TickType.Client,TickPhase.End,RoomMatchSmokeChecks::tick);}
    private static void tick(Minecraft client){
        if(done || heroAssignmentChecked && Boolean.getBoolean("sgs.heroOffersSmoke"))return;
        try{
            if(++ticks>18000)throw new IllegalStateException("match timeout stage="+stage);
            if((RevealedGiftWorldRestartProbe.enabled() || TargetRedirectWorldRestartProbe.enabled() || (TurnEndWorldRestartProbe.enabled() || DrawWorldRestartProbe.enabled())) && ticks%200==0){
                String details="";
                if(client.screen instanceof LobbyScreen lobby){var v=lobby.smokeView();
                    details="page="+lobby.smokePage()+" modal="+lobby.smokeModal()+" pending="+lobby.smokePending()
                        +(v==null?" view=null":" own="+v.ownRoom()+" request="+v.request()+" revision="+v.revision()+" success="+v.success()+" reason="+v.reason()+" rooms="+v.rooms().stream().map(r->r.id()+":"+r.mode()+":"+r.members()).toList());}
                ProbeMod.LOG.info("SGS_ROOM_FLOW_DIAGNOSTIC role={} stage={} screen={} {}",ROLE,stage,client.screen==null?"null":client.screen.getClass().getSimpleName(),details);
                if(ticks==200 || ticks%1200==0)Screenshot.grab(client.gameDirectory,"sgs-room-flow-"+ticks+".png",client.getMainRenderTarget(),m->{});
            }
            if(client.player==null)return;
            if(stage==0){
                if(Boolean.getBoolean("sgs.nativeWorldRestore") || FangtianWorldRestartProbe.restoring() || WushuangWorldRestartProbe.restoring() || ActiveJijiangWorldRestartProbe.restoring() || RevealedGiftWorldRestartProbe.restoring()){
                    if(!(client.screen instanceof MatchScreen)){if(ticks%20==0){LobbyClient.open();MatchClient.resume();}return;}stage=5;
                }else if(ReconnectSmokeChecks.isResume()){
                    if(!(client.screen instanceof MatchScreen))return;
                    stage=5;
                }else {if(NativeTableEntry.open(client))stage=1;return;}
            }
            if(client.screen instanceof LobbyScreen lobby && lobby.smokeView()!=null && !lobby.smokePending()){
                if(PackInfoSmokeChecks.tick(client,lobby))return;
                if(stage==70){
                    var own=lobby.smokeView().rooms().stream().filter(r->r.id()==lobby.smokeView().ownRoom()).findFirst().orElseThrow();
                    if(own.teammateSharing()){lobby.smokePress("share_off");return;}
                    teamRevoked=true;stage=5;MatchClient.resume();return;
                }
                if(stage==6){
                    var room=lobby.smokeView().rooms().stream().filter(r->r.id()==lobby.smokeView().ownRoom()).findFirst().orElseThrow();
                    if(room.playing())return;
                    if(room.ready()!=0 || room.ai()!=resultPlayers-HUMANS || room.members()!=HUMANS)throw new IllegalStateException("room reset lost seats or readiness");
                    if(processingViews==0 || publicMoveViews==0 || !capturedProcessing)throw new IllegalStateException("no real processing-zone evidence");
                    if(TEAM && (teamSharedViews==0 || teamPrivateViews==0))throw new IllegalStateException("team sharing grant/revoke not observed");
                    done=true;ProbeMod.LOG.info("SGS_ROOM_MATCH_SMOKE_PASS players={} plays={} responses={} outcome={} native_ui=true returned_room=true humans={} role={} private_views={} wugu_nondefault_received={} chain_views={} chain_damage_views={} processing_views={} public_move_views={} skill_invocations={} jianxiong_confirmed={} team_shared_views={} team_private_views={}",resultPlayers,plays,prompts,resultOutcome,HUMANS,ROLE,checkedViews,wuguConfirmed,chainViews,chainDamageViews,processingViews,publicMoveViews,skillInvocations,jianxiongConfirmed,teamSharedViews,teamPrivateViews);
                    if(ROLE.isEmpty())client.stop(); // Two-client harness keeps both connected until both pass.
                    return;
                }
                if(!ROLE.isEmpty()){driveMixedLobby(lobby);return;}
                if(stage==1){lobby.smokePress(System.getProperty("sgs.matchPlayers","6").equals("8")?"mode:eight":"mode:six");lobby.smokePress("create");stage=2;return;}
                if(stage==2){if(lobby.smokeView().ownRoom()<0)return;lobby.smokePress("ai_fill");stage=3;return;}
                if(stage==3){lobby.smokePress("ready");stage=4;return;}
                if(stage==4){lobby.smokePress("start");stage=5;return;}
            }
            if(!(client.screen instanceof MatchScreen screen))return;
            var motion=screen.cardMotionFrameForSmoke();
            if(motion!=null && motionCaptures.add(motion.reason()+"-"+motion.faceUp())){
                Screenshot.grab(client.gameDirectory,"sgs-motion-"+motion.reason().toLowerCase(java.util.Locale.ROOT)+"-"+motion.faceUp()+".png",client.getMainRenderTarget(),m->{});
                ProbeMod.LOG.info("SGS_CARD_MOTION_NATIVE sequence={} reason={} face={} progress={}",motion.sequence(),motion.reason(),motion.faceUp(),motion.progress());
            }
            var view=screen.stateForSmoke();var s=view.snapshot();
            if(MatchChatMultiplayerChecks.tick(client,screen))return;
            if(SocialSkinSyncChecks.tick(client,screen))return;
            var releaseFrame=screen.chainReleaseFrameForSmoke();
            if(releaseFrame!=null){
                int phase=releaseFrame[1]<150?0:1;
                if(chainReleaseCaptures.add(phase)){
                    Screenshot.grab(client.gameDirectory,"sgs-chain-release-"+phase+".png",client.getMainRenderTarget(),m->{});
                    ProbeMod.LOG.info("SGS_CHAIN_RELEASE_CAPTURE seat={} elapsed={} phase={} server_transition=true",releaseFrame[0],releaseFrame[1],phase);
                }
            }
            for(var dead:s.seats())if(!dead.alive()) {
                int observed=deathCaptureFrames.merge(dead.seat(),1,Integer::sum);
                if(observed==3) {
                    Screenshot.grab(client.gameDirectory,"sgs-death-seat-"+dead.seat()+".png",client.getMainRenderTarget(),m->{});
                    ProbeMod.LOG.info("SGS_DEATH_CAPTURE seat={} identity={} self={} server_alive=false",dead.seat(),dead.identityOrdinal(),dead.self());
                }
            }
            if(OpeningHandSmokeChecks.advance(client,screen,!ROLE.equals("guest") && !Boolean.getBoolean("sgs.fangtianSmoke") && !WushuangWorldRestartProbe.enabled() && !ActiveJijiangWorldRestartProbe.enabled() && !(RevealedGiftWorldRestartProbe.enabled() || TargetRedirectWorldRestartProbe.enabled() || (TurnEndWorldRestartProbe.enabled() || DrawWorldRestartProbe.enabled()))))return;
            if(SelectionWaitingSmokeChecks.tick(client,screen))return;
            if(WushuangWorldRestartProbe.tick(client,screen))return;
            if(ActiveJijiangWorldRestartProbe.tick(client,screen))return;
            if(RevealedGiftWorldRestartProbe.tick(client,screen))return;
            if(DrawWorldRestartProbe.tick(client,screen))return;
            if(TurnEndWorldRestartProbe.tick(client,screen))return;
            if(TargetRedirectWorldRestartProbe.tick(client,screen))return;
            if(Boolean.getBoolean("sgs.wuxieScopeSmoke") && s.counterspellWindow()!=null && s.prompt()!=null
                && s.prompt().askerSeat()==s.viewerSeat() && s.prompt().typeOrdinal()==dev.sgs.core.engine.PromptType.WUXIE_COUNTER.ordinal()
                && observedWuxieWindow!=s.counterspellWindow().windowId()){
                var window=s.counterspellWindow();observedWuxieWindow=window.windowId();
                ProbeMod.LOG.info("SGS_WUXIE_WINDOW_NATIVE window={} prompt={} deadline={} private_asker=self native_ui=true",window.windowId(),s.prompt().promptId(),window.deadlineMillis());
            }
            if(capturedWuxieScope && screen.errorsForSmoke()>wuxieScopeErrors
                && Set.of("WINDOW_CLOSED","STATE_CHANGED","INVALID_CHOICE").contains(screen.errorForSmoke())){
                capturedWuxieScope=false;wuxieScopeFrames=0;
            }
            if(screen.wuxiePendingForSmoke())return;
            if(ResponseMaskSmokeChecks.tick(client,screen))return;
            if(GiftSmokeChecks.tick(client,screen))return;
            if(TeamPresentationChecks.tick(client,screen))return;
            if(HeroSkinSmokeChecks.tick(client,screen))return;
            if(TypographyChecks.tick(client,screen))return;
            if(PortraitOverlayChecks.tick(client,screen))return;
            if(EquipmentEffectsChecks.tick(client,screen))return;
            // An actual prompt can end while a screenshot panel is open. Close the
            // panel independently of that old prompt before driving a new request.
            if(processingFrames>0 && !capturedProcessing){
                if(!screen.cardsOpenForSmoke()){processingFrames=0;return;}
                if(++processingFrames<3)return;
                if(!s.processing().isEmpty()){
                    Screenshot.grab(client.gameDirectory,"sgs-processing-zone.png",client.getMainRenderTarget(),message->{});capturedProcessing=true;
                }
                screen.pressForSmoke("cards-close");processingFrames=0;return;
            }
            if(SkillFeedbackChecks.tick(client,screen))return;
            if(CardAvailabilityChecks.tick(client,screen))return;
            if(ProductChecks.tick(client,screen))return;
            if(SdkPackSmokeChecks.tick(client,screen))return;
            if(StandardHeroChecks.tick(client,screen))return;
            if(TEAM && s.status()==1 && !teamCaptured){
                if(++teamFrames<3)return;
                Screenshot.grab(client.gameDirectory,"sgs-two-v-two-teams.png",client.getMainRenderTarget(),message->{});teamCaptured=true;
                if(ROLE.equals("host") && !teamRevoked){stage=70;MatchClient.hide();return;}
            }
            if(AutoplayOverlaySmokeChecks.tick(client,screen))return;
            if(AutoplaySmokeChecks.tick(client,screen))return;
            if(ReconnectSmokeChecks.tick(client,screen))return;
            if(jianxiongConfirmed>0 && !capturedSkillGain){
                if(++gainFrames<3)return;
                Screenshot.grab(client.gameDirectory,"sgs-jianxiong-gain.png",client.getMainRenderTarget(),message->{});capturedSkillGain=true;
            }
            if(!capturedProcessing && !s.processing().isEmpty() && s.prompt()!=null && s.prompt().askerSeat()==s.viewerSeat()){
                processingFrames=1;screen.pressForSmoke("menu");screen.pressForSmoke("menu:3");return;
            }
            if(!capturedChain && s.seats().stream().anyMatch(SeatView::chained)){
                if(chainCaptureFrames++==0)client.getToasts().clear();
                if(chainCaptureFrames==3 || chainCaptureFrames==10)
                    Screenshot.grab(client.gameDirectory,"sgs-chain-wrap-"+chainCaptureFrames+".png",client.getMainRenderTarget(),message->{});
                if(chainCaptureFrames>=24){
                    Screenshot.grab(client.gameDirectory,"sgs-chain-state.png",client.getMainRenderTarget(),message->{});capturedChain=true;
                }
            }
            if(errors!=screen.errorsForSmoke()){
                errors=screen.errorsForSmoke();
                if(!screen.errorForSmoke().equals("STATE_CHANGED") && lastCard>=0)blocked.add(lastCard);
                sentRevision=-1;
            }
            // Window hide/restore is a local UI probe: advance every tick, not at action-send cadence.
            // Short server deadlines still apply while the choice window is hidden.
            if((zoneHideStage==1 || zoneHideStage==2) && s.prompt()!=null
                && s.prompt().askerSeat()==s.viewerSeat()
                && s.prompt().typeOrdinal()==dev.sgs.core.engine.PromptType.CARD_ZONE_SELECT.ordinal()){
                if(!exerciseZoneHide(client,screen,s.prompt()))return;
            }
            if(sentRevision==view.revision() || ticks%3!=0)return;
            if(s.status()==0){
                if(IdentityIntroSmokeChecks.advance(client,screen))return;
                if(HeroBanSmokeChecks.advance(client,screen))return;
                if(s.seats().stream().anyMatch(p->p.self() && p.heroId()!=null))return;
                if(s.heroCandidates().isEmpty())return;
                if(Boolean.getBoolean("sgs.heroOffersSmoke")){
                    if(s.heroCandidates().size()!=4 || new HashSet<>(s.heroCandidates()).size()!=4)throw new IllegalStateException("expected four distinct hero candidates");
                    reservedHeroOffer=List.copyOf(s.heroCandidates());
                    if(++heroOfferCaptureFrames<3)return;
                    Screenshot.grab(client.gameDirectory,"sgs-four-hero-offer.png",client.getMainRenderTarget(),message->{});
                    ProbeMod.LOG.info("SGS_HERO_RESERVATION_NATIVE role={} candidates={}",ROLE,String.join(",",reservedHeroOffer));
                }
                String preferred=System.getProperty("sgs.preferredHero","zhaoyun");
                String hero=s.heroCandidates().contains(preferred)?preferred:s.heroCandidates().getFirst();
                if(Boolean.getBoolean("sgs.fangtianSmoke") && ROLE.equals("guest") && hero.equals("liubei"))hero=s.heroCandidates().stream().filter(h->!h.equals("liubei")).findFirst().orElseThrow();
                ProbeMod.LOG.info("SGS_HERO_OFFER role={} preferred={} offered={} chosen={}",ROLE,preferred,s.heroCandidates(),hero);
                screen.pressForSmoke("hero:"+hero);screen.pressForSmoke("hero-confirm");sentRevision=view.revision();return;
            }
            if(s.status()==2){
                if(stage==6)return;
                if(screen.resultStageForSmoke()<2){
                    if(++resultFrames<(screen.resultStageForSmoke()==0?80:4))return;
                    screen.clickForSmoke(screen.resultStageForSmoke()==0?"result-next":"result-details");
                    resultFrames=0;sentRevision=-1;return;
                }
                if(plays==0)throw new IllegalStateException("no client play action occurred");
                stage=6;resultPlayers=s.seats().size();resultOutcome=s.outcomeOrdinal();
                Screenshot.grab(client.gameDirectory,"sgs-room-match-result.png",client.getMainRenderTarget(),message->{
                    client.execute(()->screen.pressForSmoke("finish"));
                });return;
            }
            int expected=TEAM?4:Integer.parseInt(System.getProperty("sgs.matchPlayers","6"));
            if(Boolean.getBoolean("sgs.heroOffersSmoke") && !heroAssignmentChecked){
                var ids=s.seats().stream().map(SeatView::heroId).toList();
                var own=s.seats().stream().filter(SeatView::self).findFirst().orElseThrow();
                if(reservedHeroOffer==null || !reservedHeroOffer.contains(own.heroId()) || ids.contains(null) || new HashSet<>(ids).size()!=expected)
                    throw new IllegalStateException("hero reservation or assignment uniqueness failed");
                heroAssignmentChecked=true;ProbeMod.LOG.info("SGS_HERO_ASSIGNMENTS_NATIVE_PASS role={} players={} heroes={}",ROLE,expected,String.join(",",ids));
                return;
            }
            if(s.seats().size()!=expected || s.seats().stream().filter(SeatView::human).count()!=HUMANS)throw new IllegalStateException("wrong human/AI seating");
            var me=s.seats().stream().filter(SeatView::self).findFirst().orElseThrow();
            if(zoneTarget>=0 && !confirmedZone && (s.prompt()==null || s.prompt().promptId()!=zoneSelectedPrompt)
                && s.seats().get(zoneTarget).handCount()==zoneBeforeCount-1){
                confirmedZone=true;ProbeMod.LOG.info("SGS_CARD_ZONE_NATIVE_CONFIRMED target={} before={} after={} opaque_hand=true",zoneTarget,zoneBeforeCount,s.seats().get(zoneTarget).handCount());
            }
            if(s.seats().stream().anyMatch(p->!p.self() && !p.hand().isEmpty()
                && (!TEAM || p.teamId()!=me.teamId() || !p.handVisible())))throw new IllegalStateException("private hand leaked");
            if(FangtianSmokeChecks.tick(client,screen)){
                if(FangtianSmokeChecks.completed() && !fangtianPlayRecorded){plays++;fangtianPlayRecorded=true;}
                return;
            }
            if(lastTurn!=s.turnNumber()){lastTurn=s.turnNumber();blocked.clear();}
            if(s.prompt()!=null && s.prompt().askerSeat()==s.viewerSeat()){
                var prompt=s.prompt();
                if(Boolean.getBoolean("sgs.wuxieScopeSmoke") && !capturedWuxieScope
                    && prompt.typeOrdinal()==dev.sgs.core.engine.PromptType.WUXIE_COUNTER.ordinal()){
                    if(++wuxieScopeFrames<3)return;
                    Screenshot.grab(client.gameDirectory,"sgs-wuxie-pass-scope.png",client.getMainRenderTarget(),m->{});
                    screen.clickForSmoke("wuxie-pass-scope");capturedWuxieScope=true;sentRevision=view.revision();
                    wuxieScopeErrors=screen.errorsForSmoke();
                    ProbeMod.LOG.info("SGS_WUXIE_SCOPE_NATIVE_INPUT prompt={} native_ui=true scope=current_trick",prompt.promptId());return;
                }
                if(prompt.typeOrdinal()==dev.sgs.core.engine.PromptType.SKILL_INVOKE.ordinal() && prompt.skillId().equals("hujia") && !capturedHujia){
                    if(++hujiaFrames<3)return;
                    Screenshot.grab(client.gameDirectory,"sgs-hujia-invoke.png",client.getMainRenderTarget(),message->{});capturedHujia=true;
                }
                if(prompt.typeOrdinal()==dev.sgs.core.engine.PromptType.SKILL_INVOKE.ordinal()){
                    if(prompt.skillId().equals("jianxiong")){
                        if(Boolean.getBoolean("sgs.caocaoSmoke") && skillProbeStage<3){
                            if(skillProbeStage==0){
                                if(++skillProbeFrames<3)return;
                                Screenshot.grab(client.gameDirectory,"sgs-jianxiong-invoke.png",client.getMainRenderTarget(),message->{});
                                skillProbeId=prompt.promptId();invalidSkillNonce=(int)MatchClient.nextNonce();lastCard=-1;
                                MatchClient.send(dev.sgs.core.match.MatchAction.promptResponse(invalidSkillNonce,skillProbeId,true,Long.MAX_VALUE,-1));skillProbeStage=1;return;
                            }
                            if(prompt.promptId()!=skillProbeId)throw new IllegalStateException("invalid skill response closed the prompt");
                            if(skillProbeStage==1){
                                if(!screen.errorForSmoke().equals("INVALID_CHOICE"))return;
                                MatchClient.send(dev.sgs.core.match.MatchAction.promptResponse(invalidSkillNonce,skillProbeId,true,-1,-1));skillProbeStage=2;return;
                            }
                            if(!screen.errorForSmoke().equals("REPLAY"))return;
                            skillProbeStage=3;ProbeMod.LOG.info("SGS_SKILL_INVOKE_REJECTIONS_PASS invalid_card_and_replay=true prompt_preserved=true");
                        }
                        pendingJianxiong=s.processing().stream().map(CardView::instanceId).collect(java.util.stream.Collectors.toSet());
                        ProbeMod.LOG.info("SGS_SKILL_INVOKE_UI skill={} prompt={} accepted=true",prompt.skillId(),prompt.promptId());skillInvocations++;
                    }
                    screen.clickForSmoke("invoke");
                    if(prompt.skillId().equals("qilin_gong"))ProbeMod.LOG.info("SGS_QILIN_NATIVE_INVOKE prompt={} real_hit_test=true",prompt.promptId());
                    prompts++;sentRevision=view.revision();return;
                }
                if(prompt.typeOrdinal()==dev.sgs.core.engine.PromptType.CARD_ZONE_SELECT.ordinal()){
                    if(zoneSeen!=prompt.promptId()){zoneSeen=prompt.promptId();zoneFrames=0;return;}
                    if(++zoneFrames<2)return;
                    if(!capturedZone){Screenshot.grab(client.gameDirectory,"sgs-card-zone-selection.png",client.getMainRenderTarget(),m->{});capturedZone=true;}
                    if(!exerciseZoneHide(client,screen,prompt))return;
                    var subject=s.seats().get(prompt.subjectSeat());long token;
                    if(prompt.hiddenHandAvailable() && subject.handCount()>0){
                        int index=subject.handCount()>1?1:0;token=-(index+2L);
                        screen.pressForSmoke("zone-hand:"+index);zoneTarget=subject.seat();zoneBeforeCount=subject.handCount();zoneSelectedPrompt=prompt.promptId();
                    }else{
                        var chosen=prompt.choices().getFirst();token=chosen.instanceId();
                        screen.pressForSmoke("skill-choice:"+token);
                    }
                    screen.clickForSmoke("skill-choice-confirm");
                    if(prompt.skillId().equals("qilin_gong"))ProbeMod.LOG.info("SGS_QILIN_NATIVE_CHOICE prompt={} card={} choices={} real_hit_test=true",prompt.promptId(),token,prompt.choices().size());
                    ProbeMod.LOG.info("SGS_CARD_ZONE_NATIVE_SELECT prompt={} target={} choice={} hidden_ids_sent=false native_ui=true",prompt.promptId(),subject.seat(),token);
                    lastCard=-1;prompts++;sentRevision=view.revision();return;
                }
                if(prompt.typeOrdinal()==dev.sgs.core.engine.PromptType.FANJIAN_SUIT.ordinal()){
                    // A fixed public guess uses no information from Zhou Yu's hidden hand.
                    lastCard=-1;screen.pressForSmoke("suit:0");
                    ProbeMod.LOG.info("SGS_SKILL_CHOICE_UI type=FANJIAN_SUIT prompt={} suit=0 native_ui=true",prompt.promptId());
                    prompts++;sentRevision=view.revision();return;
                }
                if(prompt.typeOrdinal()==dev.sgs.core.engine.PromptType.WEAPON_COST_FIRST.ordinal() || prompt.typeOrdinal()==dev.sgs.core.engine.PromptType.WEAPON_COST_SECOND.ordinal()){
                    if(prompt.choices().isEmpty())screen.clickForSmoke("decline");else{screen.clickForSmoke("skill-choice:"+prompt.choices().getFirst().instanceId());screen.clickForSmoke("skill-choice-confirm");}
                    lastCard=-1;prompts++;sentRevision=view.revision();return;
                }
                if(prompt.typeOrdinal()==dev.sgs.core.engine.PromptType.WEAPON_DISCARD.ordinal()){
                    if(prompt.choices().isEmpty())screen.clickForSmoke("decline");
                    else{long selected=prompt.choices().getFirst().instanceId();screen.clickForSmoke("skill-choice:"+selected);screen.clickForSmoke("skill-choice-confirm");ProbeMod.LOG.info("SGS_CIXIONG_NATIVE_DISCARD prompt={} card={} target_owned=true real_hit_test=true",prompt.promptId(),selected);}
                    lastCard=-1;prompts++;sentRevision=view.revision();return;
                }
                if(prompt.typeOrdinal()==dev.sgs.core.engine.PromptType.GUI_CAI_REPLACE.ordinal()){
                    var chosen=prompt.choices().stream().filter(c->me.hand().stream().anyMatch(h->h.instanceId()==c.instanceId())).findFirst();
                    if(chosen.isEmpty())screen.pressForSmoke("decline");
                    else{
                        screen.selectForSmoke(List.of(chosen.get().instanceId()),-1);screen.pressForSmoke("skill-choice-confirm");
                        ProbeMod.LOG.info("SGS_SKILL_CHOICE_UI type=GUI_CAI_REPLACE prompt={} card={} native_ui=true",prompt.promptId(),chosen.get().instanceId());
                    }
                    lastCard=-1;prompts++;sentRevision=view.revision();return;
                }
                if(prompt.typeOrdinal()==dev.sgs.core.engine.PromptType.FANKUI_TAKE.ordinal()
                    || prompt.typeOrdinal()==dev.sgs.core.engine.PromptType.YIJI_GIVE.ordinal()){
                    if(prompt.choices().isEmpty() && !prompt.hiddenHandAvailable())screen.pressForSmoke("decline");
                    else{
                        boolean feedback=prompt.typeOrdinal()==dev.sgs.core.engine.PromptType.FANKUI_TAKE.ordinal();
                        long id=prompt.hiddenHandAvailable()?(feedback?-2:0):prompt.choices().getFirst().instanceId();
                        int recipient=prompt.allowedTargetSeats().isEmpty()?-1:prompt.allowedTargetSeats().stream()
                            .filter(seat->seat!=s.viewerSeat() && s.seats().stream().anyMatch(p->p.seat()==seat && p.alive()
                                && (!TEAM || p.teamId()==me.teamId()))).findFirst().orElse(prompt.allowedTargetSeats().getFirst());
                        screen.pressForSmoke(feedback && id<=-2?"zone-hand:0":"skill-choice:"+id);
                        screen.selectForSmoke(List.of(),recipient);screen.pressForSmoke("skill-choice-confirm");
                        ProbeMod.LOG.info("SGS_SKILL_CHOICE_UI type={} prompt={} card={} target={} native_ui=true",
                            dev.sgs.core.engine.PromptType.values()[prompt.typeOrdinal()],prompt.promptId(),id,recipient);
                    }
                    lastCard=-1;prompts++;sentRevision=view.revision();return;
                }
                if(prompt.typeOrdinal()==dev.sgs.core.engine.PromptType.WUGU_PICK.ordinal()){
                    if(!capturedOffer && prompt.choices().size()>1){
                        if(offerSeen!=prompt.promptId()){offerSeen=prompt.promptId();offerFrames=0;return;}
                        if(++offerFrames<2)return;
                        Screenshot.grab(client.gameDirectory,"sgs-wugu-offer.png",client.getMainRenderTarget(),message->{});capturedOffer=true;
                    }
                    int index=prompt.choices().size()-1;var chosen=prompt.choices().get(index);
                    if(index>0){
                        pendingWugu=chosen.instanceId();pendingWuguTurn=s.turnNumber();
                        expectedWuguLog="seat "+s.viewerSeat()+" picked "+CardType.values()[chosen.typeOrdinal()].id()+"_"+Suit.values()[chosen.suitOrdinal()].id()+"_"+Rank.values()[chosen.rankOrdinal()].id()+" from wugu";
                    }
                    // Controls use the remaining-choice index; the public draft retains claimed slots.
                    screen.clickForSmoke("choice:"+index);
                }
                else{
                    Optional<CardView> card=Optional.empty();
                    boolean convert=false;
                    if("zhaoyun".equals(me.heroId())){
                        card=me.hand().stream().filter(c->(c.typeOrdinal()==CardType.SHAN.ordinal() && prompt.allowedTypeOrdinals().contains(CardType.SHA.ordinal()))
                            || (Set.of(CardType.SHA.ordinal(),CardType.HUOSHA.ordinal(),CardType.LEISHA.ordinal()).contains(c.typeOrdinal()) && prompt.allowedTypeOrdinals().contains(CardType.SHAN.ordinal()))).findFirst();
                        convert=card.isPresent();
                    }
                    if(card.isEmpty())card=me.hand().stream().filter(c->prompt.allowedTypeOrdinals().contains(c.typeOrdinal()))
                        .filter(c->prompt.typeOrdinal()!=dev.sgs.core.engine.PromptType.HUOGONG_PAY.ordinal()
                            || (!prompt.choices().isEmpty() && c.suitOrdinal()==prompt.choices().getFirst().suitOrdinal())).findFirst();
                    if(card.isPresent()){
                        if(prompt.typeOrdinal()==dev.sgs.core.engine.PromptType.HUJIA_SHAN.ordinal())
                            ProbeMod.LOG.info("SGS_HUJIA_OFFER_UI prompt={} card={} provider={} protected={}",prompt.promptId(),card.get().instanceId(),s.viewerSeat(),prompt.subjectSeat());
                        setLongdan(screen,convert);screen.selectForSmoke(List.of(card.get().instanceId()),-1);screen.pressForSmoke("respond");
                    }
                    else screen.pressForSmoke("decline");
                }
                prompts++;sentRevision=view.revision();return;
            }
            if(s.prompt()!=null || s.counterspellWindow()!=null || !s.processing().isEmpty() || s.currentSeat()!=s.viewerSeat())return;
            if(s.phaseOrdinal()==Phase.DISCARD.ordinal()){
                int count=Math.max(0,me.handCount()-Math.max(0,me.hp()));
                screen.selectForSmoke(me.hand().stream().limit(count).map(CardView::instanceId).toList(),-1);screen.pressForSmoke("discard");sentRevision=view.revision();return;
            }
            if(s.phaseOrdinal()!=Phase.PLAY.ordinal())return;
            if(!TargetHintSmokeChecks.tick(client,screen))return;
            if(!SelectionLiftChecks.exercise(client,screen))return;
            for(var card:me.hand()){
                if(blocked.contains(card.instanceId()))continue;
                var type=CardType.values()[card.typeOrdinal()];
                boolean convert=type==CardType.SHAN && "zhaoyun".equals(me.heroId());
                if((type==CardType.SHAN && !convert) || type==CardType.WUXIE || type==CardType.LEBU || type==CardType.SHANDIAN || type==CardType.JIEDAO)continue;
                if(type==CardType.TAO && me.hp()>=me.maxHp())continue;
                var target=s.seats().stream().filter(p->p.alive() && !p.self() && (!TEAM || p.teamId()!=me.teamId()))
                    .filter(p->s.selfTargetAvailability().stream().filter(h->h.cardId()==card.instanceId() && h.targetSeat()==p.seat()).allMatch(h->h.reason().isEmpty()))
                    .min(Comparator.comparingInt(p->Math.floorMod(p.seat()-s.viewerSeat(),s.seats().size()))).orElse(null);
                if(target==null && s.selfTargetAvailability().stream().anyMatch(h->h.cardId()==card.instanceId()))continue;
                setLongdan(screen,convert);if(!screen.handCardSelectableForSmoke(card.instanceId()))continue;
                screen.selectForSmoke(List.of(card.instanceId()),target==null?-1:target.seat());
                if(screen.targetQueryPendingForSmoke())return;
                if(!capturedSelectedTarget && target!=null && s.selfTargetAvailability().stream().anyMatch(h->h.cardId()==card.instanceId() && h.targetSeat()==target.seat() && h.reason().isEmpty())){
                    if(selectedTargetCard!=card.instanceId()){selectedTargetCard=card.instanceId();selectedTargetFrames=0;return;}
                    if(++selectedTargetFrames<3)return;
                    if(!screen.attackTargetMarkersForSmoke().containsKey(target.seat()))throw new IllegalStateException("ordinary target marker missing");
                    Screenshot.grab(client.gameDirectory,"sgs-selected-target.png",client.getMainRenderTarget(),m->{});
                    ProbeMod.LOG.info("SGS_SELECTED_TARGET_NATIVE_PASS card={} target={}",card.instanceId(),target.seat());capturedSelectedTarget=true;
                }
                lastCard=card.instanceId();screen.pressForSmoke("play");plays++;sentRevision=view.revision();return;
            }
            lastCard=-1;screen.pressForSmoke("end");sentRevision=view.revision();
        }catch(RuntimeException error){done=true;if(client.screen instanceof MatchScreen failed){ProbeMod.LOG.error("SGS_ROOM_MATCH_SMOKE_CONTEXT {}",failed.controlsForSmoke());Screenshot.grab(client.gameDirectory,"sgs-room-match-failure.png",client.getMainRenderTarget(),message->{});}ProbeMod.LOG.error("SGS_ROOM_MATCH_SMOKE_FAIL",error);client.stop();}
    }
    private static void setLongdan(MatchScreen screen,boolean enabled){if(screen.longdanForSmoke()!=enabled)screen.pressForSmoke("longdan");}
    private static boolean exerciseZoneHide(Minecraft client,MatchScreen screen,PromptView prompt){
        if(zoneHideStage==3)return true;
        if(zoneHideStage>0 && prompt.promptId()!=zoneHidePrompt && System.currentTimeMillis()>=zoneHideDeadline){
            ProbeMod.LOG.info("SGS_CARD_ZONE_PROBE_EXPIRED old_prompt={} next_prompt={} retry=true",zoneHidePrompt,prompt.promptId());
            zoneHideStage=0;zoneHideFrames=0;
        }
        if(zoneHideStage==0){
            var subject=screen.stateForSmoke().snapshot().seats().get(prompt.subjectSeat());
            if(prompt.hiddenHandAvailable() && subject.handCount()>0){
                int index=subject.handCount()>1?1:0;zoneHideChoice=-(index+2L);screen.pressForSmoke("zone-hand:"+index);
            }else{
                zoneHideChoice=prompt.choices().getFirst().instanceId();
                screen.pressForSmoke("skill-choice:"+zoneHideChoice);
            }
            zoneHidePrompt=prompt.promptId();zoneHideDeadline=prompt.deadlineMillis();
            screen.pressForSmoke("zone-hide");zoneHideStage=1;zoneHideFrames=0;return false;
        }
        if(prompt.promptId()!=zoneHidePrompt || prompt.deadlineMillis()!=zoneHideDeadline || screen.zoneSelectedForSmoke()!=zoneHideChoice)
            throw new IllegalStateException("Zone hide changed state: prompt="+zoneHidePrompt+"->"+prompt.promptId()
                +" deadline="+zoneHideDeadline+"->"+prompt.deadlineMillis()+" selected="+zoneHideChoice+"->"+screen.zoneSelectedForSmoke()
                +" now="+System.currentTimeMillis()+" stage="+zoneHideStage);
        if(++zoneHideFrames<3)return false;
        if(zoneHideStage==1){
            if(!screen.zoneHiddenForSmoke() || screen.enabledForSmoke("skill-choice-confirm"))throw new IllegalStateException("Hidden choice still exposes confirmation");
            Screenshot.grab(client.gameDirectory,"sgs-card-zone-hidden.png",client.getMainRenderTarget(),m->{});
            screen.pressForSmoke("zone-restore");zoneHideStage=2;zoneHideFrames=0;return false;
        }
        if(screen.zoneHiddenForSmoke())throw new IllegalStateException("Target choice window did not restore");
        Screenshot.grab(client.gameDirectory,"sgs-card-zone-restored.png",client.getMainRenderTarget(),m->{});
        ProbeMod.LOG.info("SGS_CARD_ZONE_HIDE_RESTORE_PASS prompt={} deadline={} selection_preserved=true native_ui=true",zoneHidePrompt,zoneHideDeadline);
        zoneHideStage=3;return true;
    }
    /** Inspect every decoded network snapshot, not just frames observed by the tick driver. */
    public static boolean received(MatchSnapshot snapshot){
        if(!Boolean.getBoolean("sgs.matchSmoke") || done)return true;
        try{
            if(snapshot.status()==0)return true;
            if(snapshot.prompt()!=null && snapshot.prompt().askerSeat()==snapshot.viewerSeat()
                && snapshot.prompt().typeOrdinal()==dev.sgs.core.engine.PromptType.WUXIE_COUNTER.ordinal()){
                var actor=snapshot.seats().stream().filter(SeatView::self).findFirst().orElseThrow();
                if(actor.hand().stream().flatMap(c->c.playOptions().stream()).noneMatch(o->o.available()&&o.declaredTypeOrdinal()==CardType.WUXIE.ordinal()))
                    throw new IllegalStateException("Counterspell question without a legal physical or transformed response");
            }
            if(snapshot.discardPileCount()!=HIDDEN)throw new IllegalStateException("discard count debug information exposed");
            var self=snapshot.seats().stream().filter(SeatView::self).toList();
            var player=Minecraft.getInstance().player;
            if(self.size()!=1 || player==null || !self.getFirst().name().equals(player.getGameProfile().getName()))
                throw new IllegalStateException("snapshot delivered to wrong player");
            if(self.getFirst().handCount()!=self.getFirst().hand().size())throw new IllegalStateException("own hand incomplete");
            if(!pendingJianxiong.isEmpty() && snapshot.logLines().stream().anyMatch(line->line.startsWith("seat "+snapshot.viewerSeat()+" used jianxiong:"))
                && self.getFirst().hand().stream().anyMatch(c->pendingJianxiong.contains(c.instanceId()))){
                jianxiongConfirmed++;pendingJianxiong=Set.of();
                ProbeMod.LOG.info("SGS_JIANXIONG_GAIN_CONFIRMED server_hand_and_log=true count={}",jianxiongConfirmed);
            }
            if(pendingWugu>=0){
                if(snapshot.turnNumber()!=pendingWuguTurn)pendingWugu=-1;
                else if(self.getFirst().hand().stream().anyMatch(c->c.instanceId()==pendingWugu) && snapshot.logLines().contains(expectedWuguLog)){
                    wuguConfirmed++;ProbeMod.LOG.info("SGS_WUGU_SELECTION_CONFIRMED card={} nondefault=true server_hand_and_log=true",pendingWugu);pendingWugu=-1;
                }
            }
            for(var seat:snapshot.seats()){
                boolean teammate=TEAM && !seat.self() && seat.teamId()==self.getFirst().teamId();
                if(!seat.self() && !seat.hand().isEmpty() && (!teammate || !seat.handVisible()))throw new IllegalStateException("other hand leaked");
                if(TEAM){
                    if(seat.teamId()!=(seat.seat()==0 || seat.seat()==3?0:1) || seat.identityOrdinal()!=HIDDEN)
                        throw new IllegalStateException("incorrect team or identity exposed");
                    if(teammate && seat.handVisible()){
                        if(seat.hand().size()!=seat.handCount())throw new IllegalStateException("shared hand incomplete");
                        teamSharedViews++;
                    }else if(teammate){
                        if(!seat.hand().isEmpty())throw new IllegalStateException("revoked hand retained");
                        teamPrivateViews++;
                    }
                }
                if(snapshot.status()==1 && seat.alive() && !seat.self() && seat.seat()!=snapshot.lordSeatOrdinal() && seat.identityOrdinal()!=HIDDEN)
                    throw new IllegalStateException("hidden identity leaked");
            }
            var prompt=snapshot.prompt();
            var processingIds=new HashSet<Long>();
            for(var card:snapshot.processing())if(!processingIds.add(card.instanceId()))throw new IllegalStateException("duplicate processing card");
            for(var seat:snapshot.seats()){
                var located=new ArrayList<CardView>(seat.hand());located.addAll(seat.delay());
                for(var equipment:new CardView[]{seat.weapon(),seat.armor(),seat.horsePlus(),seat.horseMinus()})if(equipment!=null)located.add(equipment);
                if(located.stream().anyMatch(c->processingIds.contains(c.instanceId())))throw new IllegalStateException("processing card simultaneously in player zone");
            }
            if(!snapshot.processing().isEmpty())processingViews++;
            if(!snapshot.publicMoves().isEmpty())publicMoveViews++;
            if(snapshot.publicMoves().stream().anyMatch(m->Set.of("DRAW","SHUFFLE").contains(m.reason())
                ||m.reason().equals("SKILL_GIVE")&&!(Set.of("EQUIPMENT","JUDGMENT","DISCARD","PROCESSING").contains(m.fromKind())
                    ||Set.of("EQUIPMENT","JUDGMENT","DISCARD").contains(m.toKind()))))throw new IllegalStateException("private movement exposed");
            if(prompt!=null && prompt.askerSeat()!=snapshot.viewerSeat() && (!prompt.allowedTypeOrdinals().isEmpty()
                || !prompt.allowedTargetSeats().isEmpty() || prompt.hiddenHandAvailable()
                || (!prompt.choices().isEmpty() && (prompt.typeOrdinal()!=dev.sgs.core.engine.PromptType.HUOGONG_PAY.ordinal() || prompt.choices().size()!=1))))
                throw new IllegalStateException("other decision options leaked");
            if(snapshot.seats().stream().anyMatch(SeatView::chained))chainViews++;
            if(snapshot.logLines().stream().anyMatch(line->line.contains("chain=true") && line.contains(" damage ")))chainDamageViews++;
            checkedViews++;return true;
        }catch(RuntimeException error){done=true;ProbeMod.LOG.error("SGS_ROOM_MATCH_SMOKE_FAIL",error);Minecraft.getInstance().stop();return false;}
    }
    private static void driveMixedLobby(LobbyScreen lobby){
        var view=lobby.smokeView();
        if(stage==1){lobby.smokePress(TEAM?"mode:two_v_two":System.getProperty("sgs.matchPlayers","6").equals("8")?"mode:eight":"mode:six");stage=2;return;}
        if(ROLE.equals("guest")){
            if(view.ownRoom()<0){
                String mode=TEAM?"two_v_two":System.getProperty("sgs.matchPlayers","6").equals("8")?"eight":"six";
                var room=view.rooms().stream().filter(r->r.mode().equals(mode)).findFirst();
                if(room.isPresent())lobby.smokePress("join:"+room.get().id());
                return;
            }
            if(!view.ownReady())lobby.smokePress("ready");
            return;
        }
        if(!ROLE.equals("host"))throw new IllegalArgumentException("unknown match role");
        if(view.ownRoom()<0){
            if(Boolean.getBoolean("sgs.fangtianSmoke") || WushuangWorldRestartProbe.enabled() || ActiveJijiangWorldRestartProbe.enabled() || (RevealedGiftWorldRestartProbe.enabled() || TargetRedirectWorldRestartProbe.enabled() || (TurnEndWorldRestartProbe.enabled() || DrawWorldRestartProbe.enabled()))){lobby.smokePress("open_create");lobby.smokeAction("fixed");
                if(FangtianWorldRestartProbe.enabled() || WushuangWorldRestartProbe.enabled() || ActiveJijiangWorldRestartProbe.enabled() || (RevealedGiftWorldRestartProbe.enabled() || TargetRedirectWorldRestartProbe.enabled() || (TurnEndWorldRestartProbe.enabled() || DrawWorldRestartProbe.enabled())))for(String kind:List.of("turn","response","selection")){int attempts=0;while(lobby.smokeDraftSeconds(kind)!=300){if(++attempts>12)throw new IllegalStateException("300 seconds unavailable");lobby.smokeAction(kind);}}
                lobby.smokeAction("confirm_settings");}
            else lobby.smokePress("create");return;
        }
        var room=view.rooms().stream().filter(r->r.id()==view.ownRoom()).findFirst().orElseThrow();
        if(room.members()!=2)return;
        if(room.ai()!=room.capacity()-HUMANS){lobby.smokePress("ai_fill");return;}
        if(TEAM && !room.teammateSharing()){lobby.smokePress("share_on");return;}
        if(!view.ownReady()){lobby.smokePress("ready");return;}
        if(room.ready()==HUMANS && !room.playing()){lobby.smokePress("start");stage=5;}
    }
}
