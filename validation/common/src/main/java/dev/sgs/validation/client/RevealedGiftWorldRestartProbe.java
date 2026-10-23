package dev.sgs.validation.client;

import dev.sgs.core.Phase;
import dev.sgs.core.engine.PromptType;
import dev.sgs.validation.ProbeMod;
import dev.sgs.validation.match.MatchNetwork;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/** Ordinary Zhou Yu/Guo Jia clients; real suit and listener buttons after a world restart. */
final class RevealedGiftWorldRestartProbe {
    private static int stage;
    private static long renderBase=-1,observedAt,acceptedAt,yingziPrompt;
    private static boolean parked;
    private static boolean hostDamageCaptured;
    private static long damageRevision=-1;
    private record Evidence(long capturedAtMillis,String uuid,MatchNetwork.View view,int clientNonce){}
    static boolean enabled(){return Boolean.getBoolean("sgs.revealedGiftWorldRestart");}
    static boolean restoring(){return enabled()&&System.getProperty("sgs.revealedGiftStage","").equals("restore");}
    private static String role(){return System.getProperty("sgs.matchRole","");}
    private static Path directory(){return Path.of(System.getProperty("sgs.revealedGiftEvidence"));}
    private static long selected(){return Long.parseLong(System.getProperty("sgs.revealedGiftCard"));}
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException(why);}
    private static Evidence read(String name){try{return MatchNetwork.JSON.fromJson(Files.readString(directory().resolve(name+".json")),Evidence.class);}catch(Exception e){throw new IllegalStateException(e);}}
    private static boolean rendered(MatchScreen screen){if(renderBase<0)renderBase=screen.completedRenderCountForSmoke();return screen.completedRenderCountForSmoke()-renderBase>=3;}
    private static void next(int value){stage=value;renderBase=-1;}
    private static void capture(Minecraft c,MatchScreen screen,String name){try{
        screen.verifySkillGeometryForSmoke();Files.createDirectories(directory());var path=directory().resolve(role()+"-"+name+".json");var temp=path.resolveSibling(path.getFileName()+".new");
        Files.writeString(temp,MatchNetwork.JSON.toJson(new Evidence(System.currentTimeMillis(),c.player.getUUID().toString(),screen.stateForSmoke(),MatchClient.nonceForSmoke())));
        Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
        Screenshot.grab(c.gameDirectory,"sgs-revealed-gift-"+name+".png",c.getMainRenderTarget(),m->{});
    }catch(Exception e){throw new IllegalStateException(e);}}
    static boolean tick(Minecraft c,MatchScreen screen){
        if(!enabled())return false;var view=screen.stateForSmoke();var s=view.snapshot();if(s.status()!=1||s.openingHand()!=null)return false;
        if(c.getWindow().getGuiScaledWidth()<80||c.getWindow().getGuiScaledHeight()<80)return true;
        c.getToasts().clear();boolean guest=role().equals("guest");require(guest||role().equals("host"),"unknown revealed-gift role");
        screen.hoverForSmoke(0,0);
        require(s.viewerSeat()==(guest?1:0)&&s.seats().size()==6&&s.seats().stream().filter(p->p.human()).count()==2,"ordinary six-seat humans changed");
        require(s.seats().get(s.viewerSeat()).control().source().equals("MANUAL"),"human taken over");
        require(s.seats().stream().filter(p->!p.self()).allMatch(p->p.hand().isEmpty()),"opponent private hand leaked");
        require(s.seats().get(0).heroId().equals("zhouyu")&&s.seats().get(1).heroId().equals("guojia"),"ordinary hero choices changed");
        var p=s.prompt();boolean suit=p!=null&&p.typeOrdinal()==PromptType.FANJIAN_SUIT.ordinal()&&p.askerSeat()==1&&p.subjectSeat()==0;
        if(restoring()){
            var before=read(role()+"-before");var b=before.view().snapshot();
            if(stage==0){
                require(suit&&s.processing().isEmpty()&&p.promptId()==b.prompt().promptId(),"guess/root/token not restored");
                require(s.publicCardReveals().isEmpty(),"unanswered guess disclosed a card");
                require(view.nonceFloor()==before.view().nonceFloor(),"input floor changed");
                if(!rendered(screen))return true;if(guest)screen.verifySuitGeometryForSmoke();capture(c,screen,"restored");next(1);return true;
            }
            if(guest&&stage==1){
                if(!Files.exists(directory().resolve("host-restored.json"))||!rendered(screen))return true;
                require(suit,"guess disappeared before actual input");screen.verifySuitGeometryForSmoke();capture(c,screen,"response");
                int nonce=MatchClient.nonceForSmoke();screen.clickForSmoke("suit:0");require(MatchClient.nonceForSmoke()==nonce+1,"suit input duplicated");
                ProbeMod.LOG.info("SGS_REVEALED_GIFT_SUIT_MOUSE suit=0 four_buttons=true real_hit_test=true single_nonce=true");next(2);return true;
            }
            if(guest&&stage==2){
                if(p==null||p.typeOrdinal()!=PromptType.SKILL_INVOKE.ordinal()||!p.skillId().equals("yiji")||p.askerSeat()!=1)return true;
                if(damageRevision<0){damageRevision=view.revision();renderBase=-1;return true;}
                if(!rendered(screen)||!Files.exists(directory().resolve("host-damage.json")))return true;
                require(s.publicCardReveals().stream().anyMatch(r->r.held()&&r.reason().equals("fanjian")&&r.cards().stream().anyMatch(card->card.instanceId()==selected()))
                    &&screen.publicRevealedCardsForSmoke().contains(selected()),"server disclosure not actually drawn at damage wait");
                require(s.seats().get(1).hp()==b.seats().get(1).hp()-1&&s.seats().get(1).handCount()==b.seats().get(1).handCount()
                    &&s.seats().get(0).handCount()==b.seats().get(0).handCount(),"revealed card delivered before damage listener returned");
                capture(c,screen,"damage");int nonce=MatchClient.nonceForSmoke();screen.clickForSmoke("decline");require(MatchClient.nonceForSmoke()==nonce+1,"listener input duplicated");
                ProbeMod.LOG.info("SGS_REVEALED_GIFT_YIJI_MOUSE decline=true real_hit_test=true single_nonce=true");next(3);return true;
            }
            if(guest&&stage==3||!guest&&stage==1){
                if(!guest&&p!=null&&p.typeOrdinal()==PromptType.SKILL_INVOKE.ordinal()&&p.skillId().equals("yiji")&&!hostDamageCaptured){
                    if(damageRevision<0){damageRevision=view.revision();renderBase=-1;return true;}
                    if(!rendered(screen))return true;
                    require(s.publicCardReveals().stream().anyMatch(r->r.held()&&r.cards().stream().anyMatch(card->card.instanceId()==selected()))
                        &&screen.publicRevealedCardsForSmoke().contains(selected()),"owner disclosure not actually drawn at damage wait");
                    capture(c,screen,"damage");hostDamageCaptured=true;return true;
                }
                // Hand-to-hand gifts stay out of the public movement feed. Use the completed
                // skill receipt plus each client's own real hand, never invent public disclosure.
                boolean delivered=s.skillActivations().stream().anyMatch(a->a.seat()==0&&a.skillId().equals("fanjian"));
                if(p!=null||!s.processing().isEmpty()||!delivered)return true;
                require(s.currentSeat()==0&&s.phaseOrdinal()==Phase.PLAY.ordinal()&&s.turnNumber()==b.turnNumber(),"parent did not return to original play");
                for(var old:b.seats()){
                    var now=s.seats().get(old.seat());require(now.hp()==old.hp()-(old.seat()==1?1:0),"wrong damage recipient");
                    require(now.handCount()==old.handCount()+(old.seat()==0?-1:old.seat()==1?1:0),"wrong original entity delivery");
                }
                require(s.skillActivations().stream().filter(a->a.seat()==0&&a.skillId().equals("fanjian")).count()==1,"activation duplicated or lost");
                var own=s.seats().get(s.viewerSeat()).hand();require(own.stream().anyMatch(card->card.instanceId()==selected())==guest,"original entity ownership differs");
                if(acceptedAt==0){acceptedAt=System.currentTimeMillis()+750;renderBase=-1;return true;}if(System.currentTimeMillis()<acceptedAt||!rendered(screen))return true;
                capture(c,screen,"accepted");ProbeMod.LOG.info("SGS_REVEALED_GIFT_PASS role={} card={} target=1 damage=1 source=0 native_mouse=true",role(),selected());next(4);
            }
            return true;
        }
        if(!guest&&stage==0){
            if(p!=null&&p.typeOrdinal()==PromptType.SKILL_INVOKE.ordinal()&&p.skillId().equals("yingzi")&&p.askerSeat()==0){
                if(yingziPrompt!=p.promptId()&&rendered(screen)){screen.clickForSmoke("decline");yingziPrompt=p.promptId();renderBase=-1;}return true;
            }
            if(s.currentSeat()!=0||s.phaseOrdinal()!=Phase.PLAY.ordinal()||p!=null||!rendered(screen))return true;
            require(s.seats().get(0).identityOrdinal()==0,"actual source lord missing");screen.clickSeatForSmoke(1);next(1);return true;
        }
        if(!guest&&stage==1){
            if(!rendered(screen))return true;capture(c,screen,"selected");int nonce=MatchClient.nonceForSmoke();screen.clickForSmoke("fanjian");
            require(MatchClient.nonceForSmoke()==nonce+1,"active submission duplicated");ProbeMod.LOG.info("SGS_REVEALED_GIFT_START target=1 real_skill_button=true real_target_hit_test=true single_nonce=true");next(2);return true;
        }
        if(!suit)return true;require(s.processing().isEmpty()&&s.publicCardReveals().isEmpty(),"unanswered guess revealed/moved an entity");
        if(guest)screen.verifySuitGeometryForSmoke();
        if(observedAt==0)observedAt=System.currentTimeMillis();
        if(!parked&&System.currentTimeMillis()-observedAt>=15000&&rendered(screen)){
            capture(c,screen,"before");parked=true;ProbeMod.LOG.info("SGS_REVEALED_GIFT_PARKED role={} prompt={} held_ms={}",role(),p.promptId(),System.currentTimeMillis()-observedAt);
        }
        return true;
    }
}
