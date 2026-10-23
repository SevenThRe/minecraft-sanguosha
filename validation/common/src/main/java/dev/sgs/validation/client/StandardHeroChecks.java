package dev.sgs.validation.client;

import dev.sgs.core.Phase;
import dev.sgs.core.card.*;
import dev.sgs.core.engine.PromptType;
import dev.sgs.core.match.dto.MatchViews.*;
import dev.sgs.validation.ProbeMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import java.util.*;

/** Only real cards/widgets. A pass proves the named primary skill, never the entire hero. */
public final class StandardHeroChecks {
    private static boolean sent,confirmed,summary;
    private static long sentAt,lastPrompt=-1;
    private static int orderChoices;
    private static String expected;
    private static final Set<String> observed=new LinkedHashSet<>(),capturedPrompts=new HashSet<>();
    private static final Set<String> FEMALE=Set.of("huangyueying","zhenji","daqiao","sunshangxiang","diaochan");
    private StandardHeroChecks(){}
    private static String primary(String hero){return switch(hero){
        case "sunquan"->"zhiheng";case "huatuo"->"qingnang";case "ganning"->"qixi";case "lumeng"->"keji";
        case "zhouyu"->"yingzi";case "guojia"->"yiji";case "daqiao"->"guose";case "zhenji"->"luoshen";
        case "xuchu"->"luoyi";case "sunshangxiang"->"jieyin";case "zhugeliang"->"guanxing";case "diaochan"->"lijian";
        case "zhangliao"->"tuxi";case "xiahoudun"->"ganglie";default->throw new IllegalStateException("unsupported standard wave hero "+hero);
    };}
    private static void capture(Minecraft client,String name){Screenshot.grab(client.gameDirectory,name,client.getMainRenderTarget(),message->{});}
    public static boolean tick(Minecraft client,MatchScreen screen){
        if(System.getProperty("sgs.standardWave","").isEmpty())return false;
        var s=screen.stateForSmoke().snapshot();if(s.status()==0)return false;
        var me=s.seats().stream().filter(SeatView::self).findFirst().orElseThrow();
        if(!me.heroId().equals(System.getProperty("sgs.preferredHero","")))throw new IllegalStateException("preferred hero not selected: "+me.heroId());
        if(expected==null)expected=primary(me.heroId());
        var skills=s.heroPresentations().stream().filter(h->h.id().equals(me.heroId())).findFirst().orElseThrow().skills().stream().map(dev.sgs.core.addon.SkillPresentation::id).toList();
        for(var a:s.skillActivations())if(a.seat()==s.viewerSeat() && skills.contains(a.skillId()) && observed.add(a.skillId())){
            ProbeMod.LOG.info("SGS_STANDARD_SKILL_CONFIRMED hero={} skill={} authoritative_event=true sequence={}",me.heroId(),a.skillId(),a.sequence());
            capture(client,"sgs-standard-"+me.heroId()+"-"+a.skillId()+"-confirmed.png");
        }
        if(!confirmed && observed.contains(expected)){confirmed=true;
            ProbeMod.LOG.info("SGS_STANDARD_HERO_PASS hero={} skill={} authoritative_event=true coverage=primary_only",me.heroId(),expected);
            capture(client,"sgs-standard-hero-confirmed.png");
        }
        if(s.status()==2){
            if(!summary){summary=true;var missed=skills.stream().filter(id->!observed.contains(id)).toList();
                ProbeMod.LOG.info("SGS_STANDARD_HERO_COVERAGE hero={} observed={} not_observed={} primary={} primary_confirmed={} all_skills_confirmed={}",me.heroId(),observed,missed,expected,confirmed,missed.isEmpty());}
            if(!confirmed)throw new IllegalStateException("primary skill not confirmed: "+expected+" observed="+observed);return false;
        }
        if(s.prompt()!=null && s.prompt().askerSeat()==s.viewerSeat()){
            var p=s.prompt();var type=PromptType.values()[p.typeOrdinal()];
            boolean special=Set.of(PromptType.TUXI_TARGET,PromptType.GANGLIE_DISCARD,PromptType.LIJIAN_TARGET,PromptType.GUANXING_ORDER,PromptType.LIULI_TARGET).contains(type);
            boolean invoke=type==PromptType.SKILL_INVOKE && skills.contains(p.skillId());
            boolean qingguo=me.heroId().equals("zhenji") && p.allowedTypeOrdinals().contains(CardType.SHAN.ordinal());
            if(special || invoke || qingguo){
                if(lastPrompt==p.promptId())return true;
                if(qingguo && me.hand().stream().noneMatch(c->Suit.values()[c.suitOrdinal()]==Suit.SPADE || Suit.values()[c.suitOrdinal()]==Suit.CLUB))return false;
                String skill=p.skillId().isEmpty() && qingguo?"qingguo":p.skillId();
                if(capturedPrompts.add(skill+":"+type))capture(client,"sgs-standard-"+me.heroId()+"-"+skill+"-prompt.png");
                if(invoke)screen.pressForSmoke(p.skillId());
                else if(qingguo){
                    var black=me.hand().stream().filter(c->Suit.values()[c.suitOrdinal()]==Suit.SPADE || Suit.values()[c.suitOrdinal()]==Suit.CLUB).findFirst().orElseThrow();
                    if(!screen.qingguoForSmoke())screen.pressForSmoke("qingguo");screen.selectForSmoke(List.of(black.instanceId()),-1);screen.pressForSmoke("respond");
                }else if(type==PromptType.GUANXING_ORDER){
                    if(p.choices().isEmpty())screen.pressForSmoke("decline");else{
                        screen.pressForSmoke("skill-choice:"+p.choices().getFirst().instanceId());screen.pressForSmoke("deck-bottom");screen.pressForSmoke("skill-choice-confirm");}
                }else if(type==PromptType.TUXI_TARGET || type==PromptType.LIJIAN_TARGET){
                    if(p.allowedTargetSeats().isEmpty())screen.pressForSmoke("decline");else{
                        int seat=p.allowedTargetSeats().stream().filter(t->s.seats().stream().anyMatch(v->v.seat()==t && v.teamId()!=me.teamId())).findFirst().orElse(p.allowedTargetSeats().getFirst());
                        screen.pressForSmoke("skill-target:"+seat);screen.pressForSmoke("skill-choice-confirm");}
                }else if(type==PromptType.GANGLIE_DISCARD){
                    if(p.choices().isEmpty())screen.pressForSmoke("decline");else{screen.pressForSmoke("skill-choice:"+p.choices().getFirst().instanceId());screen.pressForSmoke("skill-choice-confirm");}
                }else{
                    // A hand cost retains the displayed attack range. Other costs safely decline.
                    var cost=p.choices().stream().filter(c->me.hand().stream().anyMatch(h->h.instanceId()==c.instanceId())).findFirst();
                    if(cost.isEmpty() || p.allowedTargetSeats().isEmpty())screen.pressForSmoke("decline");else{
                        int seat=p.allowedTargetSeats().stream().filter(t->s.seats().stream().anyMatch(v->v.seat()==t && v.teamId()!=me.teamId())).findFirst().orElse(p.allowedTargetSeats().getFirst());
                        screen.pressForSmoke("skill-choice:"+cost.get().instanceId());screen.selectForSmoke(List.of(),seat);screen.pressForSmoke("skill-choice-confirm");}
                }
                lastPrompt=p.promptId();ProbeMod.LOG.info("SGS_STANDARD_NATIVE_INPUT hero={} skill={} type={} prompt={} native_ui=true",me.heroId(),skill,type,p.promptId());return true;
            }
        }
        if(s.prompt()!=null || s.currentSeat()!=s.viewerSeat() || s.phaseOrdinal()!=Phase.PLAY.ordinal())return false;
        if(confirmed && (me.heroId().equals("daqiao") && screen.guoseForSmoke() || me.heroId().equals("ganning") && screen.qixiForSmoke())){
            screen.pressForSmoke(me.heroId().equals("daqiao")?"guose":"qixi");return true;}
        if(confirmed)return false;
        if(sent){if(System.currentTimeMillis()-sentAt<700)return true;sent=false;return false;}
        if(Set.of("yingzi","yiji","luoyi","luoshen","guanxing","tuxi","ganglie").contains(expected))return false;
        if(expected.equals("keji")){screen.pressForSmoke("end");return submitted(me.heroId(),expected);}
        if(me.hand().isEmpty())return false;
        if(capturedPrompts.add("active:"+expected))capture(client,"sgs-standard-"+me.heroId()+"-"+expected+"-action.png");
        var card=me.hand().getFirst();int target=-1;
        if(expected.equals("qingnang") || expected.equals("jieyin")){
            var wounded=s.seats().stream().filter(p->p.alive() && p.hp()<p.maxHp() && (!expected.equals("jieyin") || !p.self() && !FEMALE.contains(p.heroId())))
                .sorted(Comparator.comparingInt(p->p.teamId()==me.teamId()?0:1)).findFirst();
            if(wounded.isEmpty() || expected.equals("jieyin") && me.hand().size()<2)return false;target=wounded.get().seat();
        }
        if(expected.equals("qixi") || expected.equals("guose")){
            boolean guose=expected.equals("guose");
            var candidate=me.hand().stream().filter(c->guose?Suit.values()[c.suitOrdinal()]==Suit.DIAMOND && c.typeOrdinal()!=CardType.LEBU.ordinal():Suit.values()[c.suitOrdinal()]==Suit.SPADE || Suit.values()[c.suitOrdinal()]==Suit.CLUB).findFirst();
            if(candidate.isEmpty())return false;
            var other=s.seats().stream().filter(p->p.alive() && !p.self() && p.teamId()!=me.teamId() && (guose?p.delay().stream().noneMatch(d->d.typeOrdinal()==CardType.LEBU.ordinal()):p.handCount()>0 || p.weapon()!=null || p.armor()!=null)).findFirst();
            if(other.isEmpty())return false;card=candidate.get();target=other.get().seat();
            if(guose?!screen.guoseForSmoke():!screen.qixiForSmoke())screen.pressForSmoke(expected);
            screen.selectForSmoke(List.of(card.instanceId()),target);screen.pressForSmoke("play");
        }else{
            if(expected.equals("lijian")){
                var male=s.seats().stream().filter(p->p.alive() && !p.self() && !FEMALE.contains(p.heroId()) && p.teamId()!=me.teamId()).findFirst();
                if(male.isEmpty())return false;target=male.get().seat();}
            screen.selectForSmoke(me.hand().stream().limit(expected.equals("jieyin")?2:1).map(CardView::instanceId).toList(),target);screen.pressForSmoke(expected);
        }
        return submitted(me.heroId(),expected);
    }
    private static boolean submitted(String hero,String skill){sent=true;sentAt=System.currentTimeMillis();ProbeMod.LOG.info("SGS_STANDARD_NATIVE_INPUT hero={} skill={} type=ACTIVE native_ui=true",hero,skill);return true;}
}
