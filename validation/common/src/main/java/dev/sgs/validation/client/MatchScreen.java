package dev.sgs.validation.client;

import dev.sgs.core.Phase;
import dev.sgs.validation.client.layout.HandFanLayout;
import dev.sgs.validation.client.layout.DecisionOverlayLayout;
import dev.sgs.validation.client.layout.ReferenceBattleLayout;
import dev.sgs.validation.client.layout.PortraitOverlayLayout;
import dev.sgs.validation.client.layout.OwnedCardChoice;
import dev.sgs.validation.client.widget.BoardIconButton;
import dev.sgs.validation.client.widget.HeroChoiceWidget;
import net.minecraft.client.gui.components.EditBox;
import dev.sgs.core.card.*;
import dev.sgs.core.engine.PromptType;
import dev.sgs.core.match.MatchAction;
import dev.sgs.core.match.dto.MatchViews.*;
import dev.sgs.validation.client.art.*;
import dev.sgs.validation.match.MatchNetwork;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import dev.sgs.validation.client.widget.SgsButton;
import dev.sgs.validation.client.widget.SkillWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Native interactive room match. Rules remain on the server; illustrations are independent layers. */
public final class MatchScreen extends Screen {
    private MatchNetwork.View view;
    private final dev.sgs.validation.client.layout.CardMotionTimeline cardMotions=new dev.sgs.validation.client.layout.CardMotionTimeline();
    private final Map<Long,Rect> previousHandAnchors=new LinkedHashMap<>();
    private CardMotionRenderer.Frame motionFrame;


    private final dev.sgs.validation.client.layout.PublicRevealTimeline revealTimeline=new dev.sgs.validation.client.layout.PublicRevealTimeline();
    private List<Long> drawnPublicReveals=List.of();
    private List<CardArtCatalog.Art> art=List.of();
    private final Map<Long,Rect> handHits=new LinkedHashMap<>();
    private final Map<Integer,Rect> seatHits=new LinkedHashMap<>();
    private final Map<Integer,Long> chainStarted=new HashMap<>();
    private final Map<Integer,Long> chainReleased=new HashMap<>();
    private int[] renderedChainRelease;

    private final dev.sgs.validation.client.layout.DamageFeedback damageFeedback=new dev.sgs.validation.client.layout.DamageFeedback();
    private final Set<Long> selected=new LinkedHashSet<>();
    private String visiblePaperTip="";
    private final Map<Integer,String> relationBadges=new HashMap<>();
    private final Map<Integer,Rect> identityMarkHits=new LinkedHashMap<>();
    private final Map<String,Rect> identityChoiceHits=new LinkedHashMap<>();
    private int identityPickerSeat=-1;
    private int target=-1,secondary=-1,handOffset;
    private final Set<Integer> attackTargets=new LinkedHashSet<>();
    private long skillChoiceCard=-1,weaponCostPendingPrompt;
    private int skillChoiceDestination=-1;
    private long resultPresentationAt;
    private int resultStage; // 0 victory/defeat presentation, 1 server rewards, 2 final details
    private long wuxiePendingPromptId;
    private boolean openingPending;
    private boolean identityPending;
    private String banSubmitted="";
    private int identityPendingSlot=-1;
    private int skillCostPage;
    private boolean zoneChoiceHidden;
    private long zonePendingPrompt;
    private long harvestPendingPrompt;
    private final Map<Integer,Rect> countHits=new LinkedHashMap<>();
    private final Map<Long,Rect> equipmentHits=new LinkedHashMap<>();
    private final Map<Rect,String> skillTips=new LinkedHashMap<>();
    private final Map<Integer,Integer> attackTargetMarkers=new LinkedHashMap<>();
    private final Map<Rect,String> zoneTips=new LinkedHashMap<>();
    private long hoverCard=-1;
    private int fixtureCount,previewedSeat=-1;
    private boolean fixtureShared,portraitFixture;
    private String reason="";
    private String costSkill="";
    private int errors;
    private long errorShownUntil;
    private boolean duanliang;
    private String mythicTransform="";
    private long convertedCard=-1;
    private int convertedType=-1,conversionPage;
    private int nativeRenderedFrames,nativeConversionFrames;


    private boolean longdan;
    private boolean wusheng;
    private boolean qixi,jijiu,qingguo,guose;
    private boolean logOpen;
    private boolean cardsOpen;
    private int logPage;
    private String errorCode="";
    private Button confirm;
    private String overlay="",feedbackMessage="",reportReason="ABUSE";
    private String skinPreview="";
    private long skinRequest;
    private int reportTarget=-1;
    private int giftTarget=-1,giftAmount=1;
    private String giftKind="sgs:poppy";
    private boolean giftPalette,emotePalette,markPreview,showDistances;
    private boolean seatClockRendered;
    private int giftPage;
    private String lastEmote="";
    private final EmoteAnimations emoteAnimations=new EmoteAnimations();
    private final Map<Integer,Long> seatDeadlines=new HashMap<>(),seatObserved=new HashMap<>();
    private long giftRequest;
    private dev.sgs.validation.social.GiftNetwork.Result giftState;
    private final GiftAnimations giftAnimations=new GiftAnimations();
    private EditBox reportDetail;
    private boolean reportPending;
    private long reportRequestId;
    private long lastSkillSequence,countdownDeadline,countdownObservedAt;
    private long identityRevealStarted=-1;
    private final Deque<PublicSkillActivationView> skillNotices=new ArrayDeque<>();
    private PublicSkillActivationView activeNotice;
    private long noticeStarted;
    private boolean equipmentEffectsFixture;
    private long equipmentFixtureElapsed=-1;
    private int announcedPortraitSeat=-1;
    private final Map<String,Button> buttonsById=new HashMap<>();
    private record Rect(int x,int y,int w,int h){boolean contains(double mx,double my){return mx>=x && mx<x+w && my>=y && my<y+h;}}
    public MatchScreen(MatchNetwork.View view){super(Component.literal("三国杀"));this.view=view;
        MatchClient.identityMarks.bind(view.matchId(),view.snapshot().viewerSeat());
        cardMotions.accept(view.matchId(),view.connectionEpoch(),view.snapshot().viewerSeat(),view.revision(),view.snapshot().cardMotions(),net.minecraft.Util.getMillis());
        // A fresh/reconnected screen establishes a baseline instead of replaying old announcements.
        lastSkillSequence=view.snapshot().skillActivations().stream().mapToLong(PublicSkillActivationView::sequence).max().orElse(0);
    }
    public void update(MatchNetwork.View next){
        if(next.matchId()==view.matchId() && next.connectionEpoch()==view.connectionEpoch() && next.revision()<=view.revision()){
            if(next.revision()==view.revision() && !next.skins().equals(view.skins()))view=next;
            return;
        }
        previousHandAnchors.putAll(handHits);while(previousHandAnchors.size()>128)previousHandAnchors.remove(previousHandAnchors.keySet().iterator().next());
        cardMotions.accept(next.matchId(),next.connectionEpoch(),next.snapshot().viewerSeat(),next.revision(),next.snapshot().cardMotions(),net.minecraft.Util.getMillis());
        boolean newerDamageView=next.revision()>view.revision();
        boolean damageBaseline=view.connectionEpoch()!=next.connectionEpoch();
        boolean chainBaseline=damageBaseline || view.matchId()!=next.matchId() || view.snapshot().viewerSeat()!=next.snapshot().viewerSeat();
        if(view.matchId()!=next.matchId() || next.snapshot().status()==2)identityPickerSeat=-1;
        if(next.revision()>view.revision() && "STATE_CHANGED".equals(errorCode))reason="";
        var previous=view.snapshot();view=next;
        if(previous.viewerSeat()!=next.snapshot().viewerSeat())identityPickerSeat=-1;
        MatchClient.identityMarks.bind(next.matchId(),next.snapshot().viewerSeat());
        var current=next.snapshot();
        if(current.heroBan()==null)banSubmitted="";
        if(damageBaseline || chainBaseline || current.status()==0)damageFeedback.clear();
        else if(newerDamageView && previous.status()==1){
            long damageNow=net.minecraft.Util.getMillis();
            for(var seat:current.seats())previous.seats().stream()
                .filter(old->old.seat()==seat.seat() && old.alive()).findFirst()
                .ifPresent(old->damageFeedback.observe(seat.seat(),old.hp(),seat.hp(),damageNow));
        }
        if(previous.status()!=2 && current.status()==2){
            String closed=overlay;boolean hadCards=cardsOpen,hadLogs=logOpen;
            overlay="";cardsOpen=false;logOpen=false;giftPalette=false;emotePalette=false;skinPreview="";feedbackMessage="";resultStage=0;
            if(!closed.isEmpty() || hadCards || hadLogs)dev.sgs.validation.ProbeMod.LOG.info("SGS_TERMINAL_MODAL_CLOSED name={} cards={} logs={}",closed,hadCards,hadLogs);
        }
        var oldIntro=previous.identityIntro();var newIntro=current.identityIntro();
        if(oldIntro!=null && newIntro!=null && oldIntro.stage()==0 && newIntro.stage()==1)
            identityRevealStarted=net.minecraft.Util.getMillis();
        if(newIntro==null)identityRevealStarted=-1;
        if(oldIntro==null || newIntro==null || oldIntro.stage()!=newIntro.stage() || oldIntro.chosenBackSlot()!=newIntro.chosenBackSlot()){
            identityPending=false;identityPendingSlot=-1;
        }
        var oldOpening=previous.openingHand();var newOpening=current.openingHand();
        if(oldOpening==null || newOpening==null || oldOpening.stage()!=newOpening.stage() || oldOpening.deadlineMillis()!=newOpening.deadlineMillis())openingPending=false;
        if(wuxiePendingPromptId!=currentWuxiePromptId())wuxiePendingPromptId=0;
        long chainNow=net.minecraft.Util.getMillis();
        if(chainBaseline){chainStarted.clear();chainReleased.clear();}
        for(var seat:current.seats()){
            if(!seat.alive()){chainStarted.remove(seat.seat());chainReleased.remove(seat.seat());continue;}
            if(!seat.chained()){
                chainStarted.remove(seat.seat());
                if(!chainBaseline && previous.seats().stream().anyMatch(old->old.seat()==seat.seat() && old.chained()))chainReleased.put(seat.seat(),chainNow);
            } else {
                chainReleased.remove(seat.seat());
                if(!chainBaseline && previous.seats().stream().anyMatch(old->old.seat()==seat.seat() && !old.chained()))
                chainStarted.put(seat.seat(),chainNow);
            }
        }
        chainStarted.keySet().removeIf(id->current.seats().stream().noneMatch(seat->seat.seat()==id));
        chainReleased.keySet().removeIf(id->current.seats().stream().noneMatch(seat->seat.seat()==id));
        if(chainBaseline){skillNotices.clear();activeNotice=null;previousHandAnchors.clear();lastSkillSequence=current.skillActivations().stream().mapToLong(PublicSkillActivationView::sequence).max().orElse(0);}
        for(var activation:current.skillActivations())if(activation.sequence()>lastSkillSequence){
            if(activation.seat()==current.viewerSeat()){reason="";errorShownUntil=0;}
            skillNotices.addLast(activation);lastSkillSequence=activation.sequence();
            while(skillNotices.size()>4)skillNotices.removeFirst();
        }
        var me=self();selected.removeIf(id->me==null || ownedCards(me).stream().noneMatch(card->card.instanceId()==id));
        if(previous.seats().stream().filter(SeatView::self).mapToLong(p->p.control().version()).findFirst().orElse(-1)!=current.seats().stream().filter(SeatView::self).mapToLong(p->p.control().version()).findFirst().orElse(-1)
            || previous.currentSeat()!=current.currentSeat() || previous.phaseOrdinal()!=current.phaseOrdinal()
            || (previous.prompt()==null?0:previous.prompt().promptId())!=(current.prompt()==null?0:current.prompt().promptId())) {
            selected.clear();attackTargets.clear();target=-1;secondary=-1;skillChoiceCard=-1;weaponCostPendingPrompt=0;skillChoiceDestination=-1;skillCostPage=0;zoneChoiceHidden=false;zonePendingPrompt=0;harvestPendingPrompt=0;longdan=false;wusheng=false;qixi=false;jijiu=false;qingguo=false;guose=false;duanliang=false;mythicTransform="";reason="";
            costSkill="";
        }
        if(selected.size()!=1 || !selected.contains(convertedCard)
            || (previous.prompt()==null?0:previous.prompt().promptId())!=(current.prompt()==null?0:current.prompt().promptId())){convertedCard=-1;convertedType=-1;}
        if(convertedCard>=0 && (me==null || ownedCards(me).stream().noneMatch(c->c.instanceId()==convertedCard && c.playOptions().stream().anyMatch(o->o.available() && o.declaredTypeOrdinal()==convertedType)))){convertedCard=-1;convertedType=-1;}
        if(current.selfSkillAvailability().stream().anyMatch(a->a.skillId().equals(costSkill) && !a.available()))costSkill="";
        if(me!=null)selected.removeIf(id->me.hand().stream().filter(c->c.instanceId()==id).anyMatch(c->!handCardBlockedReason(c).isEmpty()));
        var ownedChoice=ownedPromptChoice();
        if(ownedChoice!=null){
            if(!ownedChoice.allowsCard(skillChoiceCard)){skillChoiceCard=-1;selected.clear();}
            if(!ownedChoice.targets().isEmpty() && !ownedChoice.allowsTarget(target))target=-1;
        }
        rebuildWidgets();
    }
    public void error(String code){if(!banSubmitted.isEmpty()){banSubmitted="";rebuildWidgets();}errors++;errorCode=code;reason=code.equals("HERO_TAKEN")?"该武将已被其他玩家选走，请重新选择":Component.translatable("game.sgs.error."+code).getString();if(reason.startsWith("game.sgs.error."))reason=code;errorShownUntil=System.currentTimeMillis()+(code.equals("HERO_TAKEN")?6000:1800);if(identityPending || openingPending || wuxiePendingPromptId!=0 || zonePendingPrompt!=0 || harvestPendingPrompt!=0 || weaponCostPendingPrompt!=0){weaponCostPendingPrompt=0;zonePendingPrompt=0;harvestPendingPrompt=0;identityPending=false;identityPendingSlot=-1;openingPending=false;wuxiePendingPromptId=0;rebuildWidgets();}}
    private SeatView self(){return view.snapshot().seats().stream().filter(SeatView::self).map(this::fixtureSeat).findFirst().orElse(null);}
    private SeatView fixtureSeat(SeatView seat){
        if(equipmentEffectsFixture)return new SeatView(seat.seat(),seat.name(),seat.human(),seat.online(),seat.alive(),seat.heroId(),seat.kingdomOrdinal(),seat.hp(),seat.maxHp(),seat.identityOrdinal(),seat.handCount(),
            seat.weapon(),seat.armor(),seat.horsePlus(),seat.horseMinus(),seat.delay(),seat.hand(),seat.self(),seat.handVisible(),seat.chained() || !false && (seat.self() || seat.seat()%2==0),seat.teamId(),seat.treasure(),seat.statuses()).withControl(seat.control());
        if(portraitFixture){
            var weapon=new CardView(-9100-seat.seat(),CardType.ZHUGE_NU.ordinal(),0,0);
            boolean metadata=false;
            if(metadata)weapon=new CardView(-9100-seat.seat(),CardType.ZHUGE_NU.ordinal(),Suit.SPADE.ordinal(),Rank.TEN.ordinal());
            var delayed=new CardView(-9200-seat.seat(),CardType.LEBU.ordinal(),0,0);
            return new SeatView(seat.seat(),seat.name(),seat.human(),seat.online(),seat.alive(),seat.heroId(),seat.kingdomOrdinal(),seat.hp(),seat.maxHp(),seat.identityOrdinal(),seat.handCount(),
                weapon,metadata?new CardView(-9300-seat.seat(),CardType.BAGUA.ordinal(),Suit.HEART.ordinal(),Rank.KING.ordinal()):seat.armor(),
                metadata?new CardView(-9400-seat.seat(),CardType.JUEYING.ordinal(),Suit.CLUB.ordinal(),Rank.QUEEN.ordinal()):seat.horsePlus(),
                metadata?new CardView(-9500-seat.seat(),CardType.CHITU.ordinal(),Suit.DIAMOND.ordinal(),Rank.ACE.ordinal()):seat.horseMinus(),
                List.of(delayed),seat.hand(),seat.self(),seat.handVisible(),seat.chained(),seat.teamId(),seat.treasure(),
                seat.self()?List.of(new StatusView("fixture:distance","距离+3","仅用于视频位置对照，不代表已实现谋蒋济技能"))
                    :List.of(new StatusView("fixture:luoyi","裸衣","布局样本：非真实技能状态")));
        }
        if(fixtureCount==0 || (!seat.self() && !fixtureShared))return seat;
        int count=seat.self()?fixtureCount:5;var cards=new ArrayList<CardView>();
        CardType[] types={CardType.SHA,CardType.SHAN,CardType.TAO,CardType.JIU,CardType.HUOSHA,CardType.LEISHA,CardType.WUXIE,CardType.ZHUGE_NU};
        for(int i=0;i<count;i++)cards.add(new CardView(-1000-seat.seat()*100-i,types[i%types.length].ordinal(),i%4,i%13));
        return new SeatView(seat.seat(),seat.name(),seat.human(),seat.online(),seat.alive(),seat.heroId(),seat.kingdomOrdinal(),seat.self()?3:seat.hp(),seat.self()?3:seat.maxHp(),seat.identityOrdinal(),count,
            seat.weapon(),seat.armor(),seat.horsePlus(),seat.horseMinus(),seat.delay(),List.copyOf(cards),seat.self(),true,seat.chained(),seat.teamId(),seat.treasure(),seat.statuses());
    }
    private List<CardView> ownedCards(SeatView seat){
        var cards=new ArrayList<>(seat.hand());
        for(var card:new CardView[]{seat.treasure(),seat.weapon(),seat.armor(),seat.horsePlus(),seat.horseMinus()})if(card!=null)cards.add(card);
        return cards;
    }
    private boolean myPrompt(){var s=view.snapshot();return (self()==null || !self().control().automatic()) && s.prompt()!=null && s.prompt().askerSeat()==s.viewerSeat();}
    private OwnedCardChoice ownedPromptChoice(){
        if(!myPrompt() || self()==null)return null;
        var p=view.snapshot().prompt();
        return OwnedCardChoice.from(PromptType.values()[p.typeOrdinal()],p.choices().stream().map(CardView::instanceId).toList(),
            ownedCards(self()).stream().map(CardView::instanceId).toList(),p.hiddenHandAvailable(),p.allowedTargetSeats());
    }
    private void selectOwnedPromptCard(long id){
        var choice=ownedPromptChoice();if(choice==null || !choice.allowsCard(id))return;
        boolean clear=skillChoiceCard==id;selected.clear();skillChoiceCard=clear?-1:id;
        if(!clear)selected.add(id);
        convertedCard=-1;convertedType=-1;reason="";rebuildWidgets();
    }
    private boolean zonePrompt(){return myPrompt() && PromptType.values()[view.snapshot().prompt().typeOrdinal()].supportsHandPositions();}
    private boolean zoneModal(){return zonePrompt() && !zoneChoiceHidden;}
    private boolean draftPrompt(){return myPrompt() && view.snapshot().prompt().typeOrdinal()==PromptType.WUGU_PICK.ordinal();}
    private static boolean handResponsePrompt(PromptType type){
        return switch(type){
            case DODGE_SHA,RESCUE_TAO,WUXIE_COUNTER,DUEL_SHA,RESPOND_SHA_NANMAN,HUJIA_SHAN,JIJIANG_SHA,QINGLONG_SHA,JIEDAO_RESPONSE,HUOGONG_REVEAL,HUOGONG_PAY->true;
            default->false;
        };
    }
    /** Mirrors the existing response controls, using the owner's actual server request. */
    private boolean responseCardSelectable(CardView card){
        if(!myPrompt())return true;
        var prompt=view.snapshot().prompt();var type=PromptType.values()[prompt.typeOrdinal()];
        if(!handResponsePrompt(type))return true;
        if(type==PromptType.HUOGONG_REVEAL)return true;
        if(type==PromptType.HUOGONG_PAY)return prompt.choices().size()==1 && card.suitOrdinal()==prompt.choices().getFirst().suitOrdinal();
        var me=self();
        // Any two distinct hand cards remain available as Zhangba costs, even if neither is Sha.
        if(prompt.allowedTypeOrdinals().contains(CardType.SHA.ordinal()) && me!=null && me.weapon()!=null
            && me.weapon().typeOrdinal()==CardType.ZHANGBA.ordinal() && me.hand().size()>=2)return true;
        return prompt.allowedTypeOrdinals().contains(declaredType(card).ordinal()) || hasConversion(card);
    }
    /** Same decision drives the paint, hit handling, stale selection cleanup and final submit. */
    private String handCardBlockedReason(CardView card){
        if(fixtureCount>0)return "";
        var s=view.snapshot();var me=self();if(me==null || !me.alive() || s.status()!=1)return "当前不能选择手牌";
        if(me.control().automatic())return "托管中";
        var actual=CardType.values()[card.typeOrdinal()];var suit=Suit.values()[card.suitOrdinal()];
        if(myPrompt()){
            var p=s.prompt();var type=PromptType.values()[p.typeOrdinal()];
            var owned=ownedPromptChoice();
            if(owned!=null)return owned.allowsCard(card.instanceId())?"":"此牌不在本次可选范围内";
            if(type==PromptType.GUI_CAI_REPLACE || type==PromptType.GANGLIE_DISCARD || type==PromptType.LIULI_TARGET)
                return p.choices().stream().anyMatch(c->c.instanceId()==card.instanceId())?"":"此牌不在本次可选范围内";
            if(type==PromptType.YIJI_GIVE)return p.choices().stream().anyMatch(c->c.instanceId()==card.instanceId())?"":"此牌不在遗计分配范围内";
            if(!handResponsePrompt(type))return "当前请求不需要选择手牌";
            return responseCardSelectable(card)?"":"此牌不能响应当前请求";
        }
        if(s.prompt()!=null || s.counterspellWindow()!=null || !s.processing().isEmpty() || s.openingHand()!=null || s.currentSeat()!=s.viewerSeat())return "当前不能出牌";
        if(s.phaseOrdinal()==Phase.DISCARD.ordinal())return "";
        if(s.phaseOrdinal()!=Phase.PLAY.ordinal())return "当前不是出牌阶段";
        if(!costSkill.isEmpty()){
            if(costSkill.equals("qiangxi") && actual.category()!=dev.sgs.core.card.CardCategory.WEAPON)return "强袭只能弃置武器，或不选牌失去体力";
            int maximum=s.heroPresentations().stream().filter(h->h.id().equals(me.heroId())).flatMap(h->h.skills().stream())
                .filter(m->m.id().equals(costSkill)).mapToInt(dev.sgs.core.addon.SkillPresentation::maximumCards).findFirst().orElse(160);
            return !selected.contains(card.instanceId()) && selected.size()>=maximum?"已选够此技能所需的牌":"";
        }
        if(wusheng && suit!=Suit.HEART && suit!=Suit.DIAMOND)return "武圣需要红色牌";
        if(qixi && suit!=Suit.SPADE && suit!=Suit.CLUB)return "奇袭需要黑色牌";
        if(guose && suit!=Suit.DIAMOND)return "国色需要方块牌";
        if(duanliang && ((suit!=Suit.SPADE && suit!=Suit.CLUB) || (actual.category()!=dev.sgs.core.card.CardCategory.BASIC && !actual.isEquipment())))return "断粮需要黑色基本牌或装备牌";
        if(longdan && actual!=CardType.SHA && actual!=CardType.HUOSHA && actual!=CardType.LEISHA && actual!=CardType.SHAN)return "龙胆需要杀或闪";
        var option=card.playOptions().stream().filter(o->o.declaredTypeOrdinal()==declaredType(card).ordinal()).findFirst();
        if(hasConversion(card))return "";
        return option.map(o->o.available()?"":cardUnavailableText(o.reason())).orElse("此牌当前不可使用");
    }
    private static String cardUnavailableText(String code){return switch(code){
        case "SHA_LIMIT"->"本阶段出杀次数已用尽";case "JIU_LIMIT"->"本回合已使用过酒";case "FULL_HP"->"体力已满，不能使用桃";
        case "SHAN_NOT_PLAYABLE","RESPONSE_ONLY"->"此牌只能在对应请求中使用";case "OUT_OF_RANGE"->"没有距离内的合法目标";
        case "DUPLICATE_DELAYED_TRICK"->"判定区已有同名牌";case "TARGET_PROHIBITED"->"没有允许的目标";
        case "TARGET_NO_CARDS"->"没有可选牌的目标";case "TARGET_NO_WEAPON","TARGET_REQUIRED"->"没有合法目标";
        default->"此牌当前不可使用";
    };}
    private void beginSkillCost(String id){costSkill=id;selected.clear();attackTargets.clear();longdan=false;wusheng=false;qixi=false;jijiu=false;qingguo=false;guose=false;duanliang=false;mythicTransform="";reason="请选择"+skillName(id)+"所需的牌，再点确定或技能发动";rebuildWidgets();}
    private Button button(String key,String label,int x,int y,int width,Runnable action){
        var b=addRenderableWidget(SgsButton.make(Component.literal(label),ignored->action.run()).bounds(x,y,width,18).build());buttonsById.put(key,b);return b;
    }
    private Button decisionButton(String key,String label,boolean first,Runnable action){
        var rect=first?decisionLayout().confirm():decisionLayout().cancel();
        var b=button(key,label,rect.x(),rect.y(),rect.width(),action);b.setHeight(rect.height());return b;
    }
    private void icon(String key,BoardIconButton.Icon type,String title,int x,int y,int size,Runnable action){
        buttonsById.put(key,addRenderableWidget(new BoardIconButton(x,y,size,type,title,key.startsWith("social:"),ignored->action.run())));
    }
    private void openOverlay(String name){if(name.equals("FLOWERS") || name.equals("EGGS")){toggleGiftPalette();return;}giftPalette=false;emotePalette=false;if(name.equals("SKINS") && self()!=null){skinPreview=view.skins().getOrDefault(self().seat(),"classic");}overlay=name;feedbackMessage="";rebuildWidgets();
        if(name.equals("REWARDS"))FeedbackClient.request(view.matchId(),"QUERY_RESULT",-1,"","");

    }
    public void gift(dev.sgs.validation.social.GiftNetwork.Result r){
        if(r.matchId()!=view.matchId())return;
        if(r.accepted()){
            if(r.kind().startsWith("EMOTE:")){emoteAnimations.accept(r);lastEmote=r.kind();dev.sgs.validation.ProbeMod.LOG.info("SGS_EMOTE_RECEIVED seat={} emote={}",r.from(),r.kind());return;}
            giftState=r;giftAnimations.accept(r);
            if(r.from()==view.snapshot().viewerSeat() && r.requestId()==giftRequest){giftRequest=0;feedbackMessage="";}
        }else{
            if(r.requestId()==giftRequest)giftRequest=0;
            feedbackMessage=switch(r.reason()){
                case "INSUFFICIENT_BALANCE"->"虚拟消耗品余额不足";case "RATE_LIMITED"->"请稍后再送";
                case "TARGET_HUMAN_ONLY"->"请选择本局其他真人";case "SELF_TARGET"->"不能赠送给自己";
                case "NOT_IN_ACTIVE_MATCH"->"仅可在进行中的牌局使用";case "DATA_UNAVAILABLE"->"数据库暂不可用，请查询余额后重试";
                default->"赠送未完成："+r.reason();
            };
            reason=feedbackMessage;errorShownUntil=System.currentTimeMillis()+2400;
        }
        rebuildWidgets();
    }






    private GiftAnimations.Anchor giftAnchor(int seat){var r=seatHits.get(seat);return r==null?null:new GiftAnimations.Anchor(r.x()+r.w()/2f,r.y()+r.h()/2f);}
    private List<dev.sgs.core.social.GiftDefinition> giftCatalog(){return giftState==null || giftState.catalog()==null?dev.sgs.core.social.GiftCatalog.builtins():giftState.catalog();}
    private boolean socialPaletteVisible(){return (giftPalette || emotePalette) && view.snapshot().status()==1 && overlay.isEmpty() && !cardsOpen && !logOpen && !zoneModal();}
    private int giftCell(){return Math.clamp(Math.round(66*board().unit()),28,46);}
    private Rect socialPaletteBox(){int cell=giftCell(),w=4*(cell+3)+12;boolean paged=giftPalette && (giftCatalog().stream().filter(d->d.positive()).count()>4 || giftCatalog().stream().filter(d->!d.positive()).count()>4);int h=2*(cell+3)+29+(paged?18:0);var rail=board().social();return new Rect(rail.x(),Math.max(30,rail.y()-h-6),Math.min(w,width-12),h);}
    private void toggleGiftPalette(){giftPalette=!giftPalette;emotePalette=false;feedbackMessage="";
        if(giftPalette)GiftClient.request(view.matchId(),-1,"QUERY",0);rebuildWidgets();}
    private void sendSelectedGift(){
        var gift=giftCatalog().stream().filter(d->d.id().equals(giftKind)).findFirst().orElse(null);
        if(gift==null || giftTarget<0 || giftRequest!=0)return;
        if(giftState==null || giftState.flowerBalance()<gift.cost()){feedbackMessage="鲜花余额不足";return;}
        giftRequest=GiftClient.request(view.matchId(),giftTarget,gift.id(),gift.received());feedbackMessage="正在赠送…";rebuildWidgets();
    }
    private void buildSocialPalette(){
        var r=socialPaletteBox();int cell=giftCell();
        if(giftPalette){
            var catalog=giftCatalog();int pages=Math.max(1,(Math.max((int)catalog.stream().filter(d->d.positive()).count(),(int)catalog.stream().filter(d->!d.positive()).count())+3)/4);giftPage=Math.clamp(giftPage,0,pages-1);
            for(int row=0;row<2;row++){
                boolean positive=row==0;var gifts=catalog.stream().filter(d->d.positive()==positive).skip(giftPage*4L).limit(4).toList();
                for(int i=0;i<gifts.size();i++){var gift=gifts.get(i);boolean available=giftState!=null && giftState.flowerBalance()>=gift.cost() && giftRequest==0 && GiftSprites.available(gift);
                    var choice=new dev.sgs.validation.client.widget.GiftChoiceButton(r.x()+6+i*(cell+3),r.y()+23+row*(cell+3),cell,gift,gift.id().equals(giftKind),available,ignored->{
                        giftKind=gift.id();if(giftTarget>=0)sendSelectedGift();else {feedbackMessage="";rebuildWidgets();}
                    });buttonsById.put("gift:"+gift.id(),addRenderableWidget(choice));
                }
            }
            if(pages>1){button("gift-prev","‹",r.x()+r.w()-42,r.y()+r.h()-18,18,()->{giftPage=(giftPage+pages-1)%pages;rebuildWidgets();});button("gift-next","›",r.x()+r.w()-22,r.y()+r.h()-18,18,()->{giftPage=(giftPage+1)%pages;rebuildWidgets();});}
        }else {
            var ids=dev.sgs.core.social.Emotes.IDS;
            for(int i=0;i<ids.size();i++){String id=ids.get(i);int x=r.x()+6+(i%4)*(cell+3),y=r.y()+23+(i/4)*(cell+3);
                var choice=new SgsButton(x,y,cell,cell,Component.literal(dev.sgs.core.social.Emotes.name(id)),ignored->{GiftClient.request(view.matchId(),-1,"EMOTE:"+id,0);emotePalette=false;rebuildWidgets();}){
                    @Override protected boolean parchment(){return false;}
                    @Override protected void renderWidget(GuiGraphics g,int mx,int my,float partial){g.fill(getX(),getY(),getX()+getWidth(),getY()+getHeight(),0xee302016);g.renderOutline(getX(),getY(),getWidth(),getHeight(),isHoveredOrFocused()?0xffffc55b:0xff745333);EmoteAnimations.draw(g,id,getX()+3,getY()+3,getWidth()-6);}
                };choice.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(dev.sgs.core.social.Emotes.name(id))));buttonsById.put("emote:"+id,addRenderableWidget(choice));
            }
        }
    }
    private void drawSocialPalette(GuiGraphics g){
        var r=socialPaletteBox();g.pose().pushPose();g.pose().translate(0,0,245);
        g.fill(r.x()-2,r.y()-2,r.x()+r.w()+2,r.y()+r.h()+2,0xff906e43);g.fill(r.x(),r.y(),r.x()+r.w(),r.y()+r.h(),0xf3432e20);
        if(giftPalette){
            GiftSprites.draw(g,"POPPY",r.x()+5,r.y()+4,12);text(g,giftState==null?"同步余额…":Long.toString(giftState.flowerBalance()),r.x()+20,r.y()+5,0xffffd68b,r.w()-24);
            int cell=giftCell();g.fill(r.x()+1,r.y()+23,r.x()+3,r.y()+23+cell,0xff8ba95f);g.fill(r.x()+1,r.y()+26+cell,r.x()+3,r.y()+26+2*cell,0xffba7753);
        }else{text(g,"表情",r.x()+6,r.y()+5,0xffead3a2,r.w()-12);}
        g.pose().popPose();
    }
    public void feedback(dev.sgs.validation.social.FeedbackNetwork.Result r){
        if(r.matchId()!=view.matchId())return;
        if(r.kind().equals("REPORT")){
            if(!reportPending || r.requestId()!=reportRequestId)return;
            reportPending=false;reportRequestId=0;
            var submit=buttonsById.get("report-submit");if(submit!=null)submit.active=true;
            feedbackMessage=r.accepted()?"举报已提交，可查看处理记录":"未提交："+feedbackReason(r.reason());
        }
        else if(!r.accepted())feedbackMessage=r.reason().equals("RESULT_NOT_READY")?"服务器奖励尚未就绪，请稍后重新查询":"查询失败："+feedbackReason(r.reason());
        else if(r.reward()!=null)feedbackMessage="";
    }
    private static String feedbackReason(String c){return switch(c){case "DUPLICATE_REPORT"->"同一目标与原因已举报";case "RATE_LIMITED"->"提交过于频繁";case "TARGET_AI"->"AI不是举报对象";case "INVALID_TARGET"->"请选择本局其他真人";case "DETAIL_TOO_LONG"->"说明不能超过240字";default->c;};}
    @Override protected void init(){
        var owner=self();if(owner!=null)selected.removeIf(id->owner.hand().stream().filter(c->c.instanceId()==id).anyMatch(c->!handCardBlockedReason(c).isEmpty()));
        if(art.isEmpty())art=CardArtCatalog.load(minecraft.getResourceManager());buttonsById.clear();
        var s=view.snapshot();int center=width/2;
        var board=board();int size=Math.max(12,Math.round(36*board.unit())),gap=Math.max(3,Math.round(18*board.unit())),tx=width-6*(size+gap)-4;
        icon("rewards",BoardIconButton.Icon.REWARDS,"奖励结算",tx,2,size,()->openOverlay("REWARDS"));
        icon("help",BoardIconButton.Icon.HELP,"规则说明",tx+size+gap,2,size,()->openOverlay("HELP"));
        icon("settings",BoardIconButton.Icon.SETTINGS,"界面设置",tx+2*(size+gap),2,size,()->openOverlay("SETTINGS"));
        icon("receipt",BoardIconButton.Icon.RECEIPT,"结算凭证",tx+3*(size+gap),2,size,()->openOverlay("REWARDS"));
        icon("heroes",BoardIconButton.Icon.HERO,"本局武将",tx+4*(size+gap),2,size,()->openOverlay("HERO"));
        icon("menu",BoardIconButton.Icon.MENU,"菜单",tx+5*(size+gap),2,size,()->openOverlay("MENU"));
        BoardIconButton.Icon[] kinds={BoardIconButton.Icon.FLOWER,BoardIconButton.Icon.REPORT,BoardIconButton.Icon.AUTOPLAY,BoardIconButton.Icon.DISTANCE,BoardIconButton.Icon.SMILE,BoardIconButton.Icon.CHAT};
        String[] captions={"鲜花","举报",self()!=null && self().control().automatic()?"取消托管":"托管",showDistances?"隐藏距离":"距离","表情","聊天"};
        String[] keys={"flowers","report","autoplay","distance","emotes","chat"};
        for(int i=0;i<6;i++){int n=i;var cell=board.socialButton(i);
            icon("social:"+i,kinds[i],captions[i],cell.x(),cell.y(),cell.width(),()->{
                switch(n){
                    case 0->toggleGiftPalette();
                    case 1->openOverlay("REPORT");
                    case 2->{if(self()!=null)MatchClient.setAutoplay(!self().control().automatic());}
                    case 3->{showDistances=!showDistances;rebuildWidgets();}
                    case 4->{emotePalette=!emotePalette;giftPalette=false;rebuildWidgets();}
                    case 5->minecraft.setScreen(new MatchChatScreen());
                }
            });
            var control=buttonsById.get("social:"+i);buttonsById.put(keys[i],control);
            if((i==0 || i==1 || i==2 || i==4) && s.viewerSeat()<0 || (i==2 || i==3) && s.status()!=1){
                control.active=false;
                control.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(s.viewerSeat()<0?"此操作仅限参战玩家":"对局开始后可用")));
            }
            if(i==3 && (self()==null || !self().alive()))control.active=false;
        }
        buttonsById.put("eggs",buttonsById.get("flowers"));
        if(s.viewerSeat()<0)for(String key:List.of("rewards","receipt"))buttonsById.get(key).active=false;
        if(socialPaletteVisible())buildSocialPalette();
        if(!overlay.isEmpty()){buildOverlay();return;}
        if(cardsOpen){button("cards-close","返回牌局",center-48,height-38,96,()->{cardsOpen=false;rebuildWidgets();});return;}
        if(logOpen){
            button("log-prev","上页",center-100,height-38,60,()->{logPage++;rebuildWidgets();});
            button("log-next","下页",center-34,height-38,60,()->{logPage=Math.max(0,logPage-1);rebuildWidgets();});
            button("log-close","关闭",center+32,height-38,60,()->{logOpen=false;rebuildWidgets();});
            return;
        }
        if(s.status()==2){buildResultControls();return;}
        if(self()!=null && self().control().automatic()){
            if(autoplayVeilVisible()){
                var r=board().box(676,811,320,88);
                var b=addRenderableWidget(new dev.sgs.validation.client.widget.AutoplayCancelButton(r.x(),r.y(),r.width(),r.height(),ignored->MatchClient.setAutoplay(false)));
                buttonsById.put("autoplay-cancel",b);
            }
            return;
        }
        if(s.viewerSeat()<0){button("spectator-leave","退出旁观",center-48,height-38,96,MatchClient::hide);return;}
        if(self()!=null && !self().alive())return;
        if(s.identityIntro()!=null){buildIdentityIntro();return;}
        if(s.heroBan()!=null){buildHeroBan();return;}
        if(s.openingHand()!=null){buildOpeningControls();return;}
        if(s.status()==0){
            if(selectionWaiting(s))return;
            var candidates=selectionCandidateIds(s);
            String chosen=self()==null?null:self().heroId();int count=candidates.size();
            for(int i=0;i<count;i++){
                String id=candidates.get(i);var hero=s.heroPresentations().stream().filter(h->h.id().equals(id)).findFirst().orElse(null);
                var box=selectionCandidate(i,count);
                if(hero==null){var missing=button("hero:"+id,"武将资料未到："+id,box.x(),box.y(),box.width(),()->{});missing.setHeight(box.height());missing.active=false;continue;}
                var card=new HeroChoiceWidget(hero,box.x(),box.y(),box.width(),box.height(),id.equals(chosen),chosen==null,
                    ignored->{reason="";errorCode="";MatchClient.send(MatchAction.selectHero(MatchClient.nextNonce(),id));});
                buttonsById.put("hero:"+id,addRenderableWidget(card));
            }
            return;
        }
        if(myPrompt()){
            var prompt=s.prompt();
            if(zonePrompt() && zoneChoiceHidden){
                var restore=decisionButton("zone-restore","显示选牌窗口",true,()->{zoneChoiceHidden=false;rebuildWidgets();});
                restore.active=zonePendingPrompt!=prompt.promptId();return;
            }
            if(buildSkillChoices()) {addSkillWidget();return;}
            if(prompt.typeOrdinal()==PromptType.SKILL_INVOKE.ordinal()){
                confirm=decisionButton("invoke",prompt.skillId().equals("yaowu_recover")?"回复体力":"发动"+skillName(prompt.skillId()),true,()->response(true));
                decisionButton("decline",prompt.skillId().equals("yaowu_recover")?"摸一张牌":"不发动",false,()->response(false));
                addSkillWidget();return;
            }
            if(draftPrompt()){
                buildHarvestChoices(prompt);
                return;
            }
            String replyLabel=prompt.typeOrdinal()==PromptType.QINGLONG_SHA.ordinal()?"使用新的杀":prompt.typeOrdinal()==PromptType.HUOGONG_REVEAL.ordinal()?"展示所选":prompt.typeOrdinal()==PromptType.HUOGONG_PAY.ordinal()?"弃置并火攻":"打出所选";
            confirm=decisionButton("respond",replyLabel,true,()->response(true));
            boolean wuxie=prompt.typeOrdinal()==PromptType.WUXIE_COUNTER.ordinal();
            var decline=decisionButton("decline",prompt.typeOrdinal()==PromptType.QINGLONG_SHA.ordinal()?"停止追杀":prompt.typeOrdinal()==PromptType.HUOGONG_REVEAL.ordinal()?"自动展示":wuxie?"不出":"放弃响应",false,()->response(false));
            if(wuxie){
                // This is a server scope intent, independent of whether the private hand has Wuxie.
                var lane=decisionLayout().timer();var row=decisionLayout().confirm();
                int responseGap=Math.max(2,Math.min(8,lane.width()/32)),cell=Math.max(1,Math.min(116,(lane.width()-2*responseGap)/3));
                int left=lane.x()+(lane.width()-cell*3-responseGap*2)/2;
                confirm.setX(left);confirm.setWidth(cell);decline.setX(left+cell+responseGap);decline.setWidth(cell);
                var pass=button("wuxie-pass-scope","本轮不出",left+2*(cell+responseGap),row.y(),cell,
                    ()->passWuxieScope(prompt.promptId()));
                pass.setHeight(row.height());
                pass.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("当前这张锦囊及其后续目标都不再询问，下一张恢复")));
                if(wuxiePending())lockWuxieButtons();
            }
        }else if(s.prompt()==null && s.counterspellWindow()==null && s.processing().isEmpty() && s.currentSeat()==s.viewerSeat()){
            if(s.phaseOrdinal()==Phase.PLAY.ordinal()){
                confirm=decisionButton("play","确定",true,this::play);
                decisionButton("end","回合结束",false,()->MatchClient.send(MatchAction.endPlay(MatchClient.nextNonce())));
            }else if(s.phaseOrdinal()==Phase.DISCARD.ordinal())confirm=button("discard","确认弃牌",center-46,controlY(),92,
                ()->MatchClient.send(MatchAction.confirmDiscard(MatchClient.nextNonce(),List.copyOf(selected))));
        }
        if(s.counterspellWindow()!=null && !myPrompt()){addSkillWidget();return;}
        var convertible=conversionCard();
        if(convertible!=null && hasConversion(convertible)){var conversionBox=board.box(1190,666,64,32);var conversionButton=button("convert","转为",conversionBox.x(),conversionBox.y(),conversionBox.width(),this::openConversion);conversionButton.setHeight(conversionBox.height());}
        var clear=board.box(1154,666,32,32);icon("cancel",BoardIconButton.Icon.LEAVE,"取消选择",clear.x(),clear.y(),Math.max(12,clear.width()),()->{selected.clear();convertedCard=-1;convertedType=-1;attackTargets.clear();costSkill="";target=-1;secondary=-1;reason="";rebuildWidgets();});

        addSkillWidget();
    }
    private int overlayWidth(){return Math.min(380,width-24);}
    private boolean hasConversion(CardView card){return card.playOptions().stream().anyMatch(o->o.available() && o.declaredTypeOrdinal()!=card.typeOrdinal());}
    private CardView conversionCard(){return self()==null || selected.size()!=1?null:ownedCards(self()).stream().filter(c->selected.contains(c.instanceId())).findFirst().orElse(null);}
    private void openConversion(){
        var card=conversionCard();if(card==null || !hasConversion(card)){reason="先选择一张可转化的牌";return;}
        conversionPage=0;openOverlay("CONVERT");
    }
    private void chooseConversion(CardView card,int declared){
        if(conversionCard()==null || conversionCard().instanceId()!=card.instanceId()
            || card.playOptions().stream().noneMatch(o->o.available() && o.declaredTypeOrdinal()==declared))return;
        longdan=false;wusheng=false;qixi=false;jijiu=false;qingguo=false;guose=false;duanliang=false;mythicTransform="";costSkill="";
        convertedCard=card.instanceId();convertedType=declared;target=-1;secondary=-1;attackTargets.clear();overlay="";reason="";rebuildWidgets();
    }
    private int overlayHeight(){
        if(!overlay.equals("CONVERT"))return 204;
        var card=conversionCard();int count=card==null?0:(int)card.playOptions().stream().filter(o->o.available()).count();
        int rows=Math.max(1,(Math.min(12,count)+2)/3);
        return 35+rows*28+(count>12?28:0)+34;
    }
    private int overlayTop(){return Math.max(20,(height-overlayHeight()-6)/2);}
    private void buildOverlay(){
        int w=overlayWidth(),x=(width-w)/2,y=overlayTop();
        if(overlay.equals("CONVERT")){
            var card=conversionCard();
            if(card!=null){
                var options=card.playOptions().stream().filter(o->o.available()).toList();
                int pages=Math.max(1,(options.size()+11)/12);conversionPage=Math.min(conversionPage,pages-1);
                for(int i=conversionPage*12;i<Math.min(options.size(),(conversionPage+1)*12);i++){
                    int declared=options.get(i).declaredTypeOrdinal(),slot=i%12;
                    String label=cardName(new CardView(card.instanceId(),declared,card.suitOrdinal(),card.rankOrdinal()));
                    button("convert:"+declared,label,x+12+(slot%3)*(w-24)/3,y+35+(slot/3)*28,(w-30)/3,()->chooseConversion(card,declared));
                }
                if(pages>1){button("convert-prev","上页",x+16,y+147,64,()->{conversionPage=Math.max(0,conversionPage-1);rebuildWidgets();});button("convert-next","下页",x+w-80,y+147,64,()->{conversionPage=Math.min(pages-1,conversionPage+1);rebuildWidgets();});}
            }
        }
        if(overlay.equals("SKINS"))buildSkinOverlay(x,y,w);
        if(overlay.equals("SETTINGS"))for(int i=1;i<=3;i++){int value=i;button("scale:"+i,"缩放 "+i,x+16+(i-1)*(w-32)/3,y+57,(w-40)/3,()->{minecraft.options.guiScale().set(value);minecraft.resizeDisplay();});}
        if(overlay.equals("MENU")){
            String[] labels={"举报玩家","奖励结算","公共日志","公开牌区","规则说明","返回房间"};
            for(int i=0;i<6;i++){int n=i;button("menu:"+i,labels[i],x+18+(i%2)*(w-36)/2,y+42+(i/2)*34,(w-44)/2,()->{
                if(n==0)openOverlay("REPORT");else if(n==1)openOverlay("REWARDS");else if(n==2){overlay="";logOpen=true;rebuildWidgets();}else if(n==3){overlay="";cardsOpen=true;rebuildWidgets();}else if(n==4)openOverlay("HELP");else if(view.snapshot().status()==2)MatchClient.leaveFinished();else MatchClient.hide();
            });}
            buttonsById.put("report",buttonsById.get("menu:0"));buttonsById.put("hide",buttonsById.get("menu:5"));
            if(view.snapshot().viewerSeat()<0)for(String key:List.of("menu:0","menu:1")){
                var control=buttonsById.get(key);control.active=false;control.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("此操作仅限参战玩家")));
            }
        }
        if(overlay.equals("REPORT")){
            var players=view.snapshot().seats().stream().filter(p->p.human() && !p.self()).toList();
            for(int i=0;i<players.size();i++){var p=players.get(i);button("report-target:"+p.seat(),(p.seat()==reportTarget?"✓ ":"")+p.name(),x+10+(i%3)*((w-20)/3),y+33+(i/3)*20,(w-26)/3,()->{reportTarget=p.seat();rebuildWidgets();});}
            int ry=y+34+Math.max(1,(players.size()+2)/3)*20;
            String[] codes={"ABUSE","GRIEFING","CHEATING","OTHER"},labels={"辱骂骚扰","恶意破坏","疑似作弊","其他"};
            for(int i=0;i<4;i++){String code=codes[i];button("report-reason:"+code,(reportReason.equals(code)?"✓":"")+labels[i],x+10+i*(w-20)/4,ry,(w-24)/4,()->{reportReason=code;rebuildWidgets();});}
            String draft=reportDetail==null?"":reportDetail.getValue();
            reportDetail=new EditBox(font,x+13,ry+27,w-26,18,Component.literal("补充说明"));reportDetail.setMaxLength(240);reportDetail.setBordered(false);reportDetail.setTextColor(0xff4a301d);reportDetail.setValue(draft);addRenderableWidget(reportDetail);
            var submit=button("report-submit","提交举报",x+w/2-102,ry+53,96,()->{
                if(reportPending)return;
                if(reportTarget<0){feedbackMessage="请选择本局其他真人";return;}
                reportPending=true;feedbackMessage="正在提交…";
                var reportButton=buttonsById.get("report-submit");if(reportButton!=null)reportButton.active=false;
                reportRequestId=FeedbackClient.request(view.matchId(),"REPORT",reportTarget,reportReason,reportDetail.getValue());
            });submit.active=!reportPending;
            button("report-mine","处理记录",x+w/2+6,ry+53,96,()->{
                if(minecraft.player!=null)minecraft.player.connection.sendCommand("sgs reports");
                minecraft.setScreen(new MatchChatScreen());
            });
        }
        button("overlay-close","返回牌局",x+w/2-48,y+overlayHeight()-21,96,()->{overlay="";rebuildWidgets();});
    }
    private net.minecraft.resources.ResourceLocation skinClassic(){
        var me=self();if(me==null)return null;
        var presentation=view.snapshot().heroPresentations().stream().filter(p->p.id().equals(me.heroId())).findFirst().orElse(null);
        if(presentation!=null && !presentation.portrait().isEmpty()){
            var texture=net.minecraft.resources.ResourceLocation.tryParse(presentation.portrait());
            if(texture!=null && minecraft.getResourceManager().getResource(texture).isPresent())return texture;
        }
        return HeroRenderer.classic(me.heroId());
    }
    private boolean skinStripAvailable(){return self()!=null && self().heroId()!=null && view.snapshot().status()==1
        && view.snapshot().identityIntro()==null && view.snapshot().openingHand()==null && !draftPrompt() && !zonePrompt();}
    private Rect skinStrip(){
        var r=board().self();var rail=PortraitOverlayLayout.nameRail(r.x(),r.y(),r.width(),r.height());
        return new Rect(rail.x(),rail.y(),rail.width(),rail.height());
    }
    private void drawSkinStrip(GuiGraphics g,int mx,int my){
        if(!skinStripAvailable() || !overlay.isEmpty() || cardsOpen || logOpen)return;
        if(skinStrip().contains(mx,my)){
            var strip=skinStrip();g.renderOutline(strip.x(),strip.y(),strip.w(),strip.h(),0xffffd47c);
        }
    }
    private void buildSkinOverlay(int x,int y,int w){
        var me=self();if(me==null)return;var skins=HeroSkins.available(me.heroId(),skinClassic());
        int cell=(w-36)/Math.max(1,skins.size());
        for(int i=0;i<skins.size();i++){
            var skin=skins.get(i);var pick=button("skin:"+skin.id(),(skinPreview.equals(skin.id())?"✓ ":"")+skin.name(),
                x+18+i*cell,y+127,cell-8,()->{skinPreview=skin.id();feedbackMessage="";rebuildWidgets();});
            pick.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("预览："+skin.name())));
        }
        button("skin-apply","使用此皮肤",x+w/2-48,y+149,96,()->{
            skinRequest=MatchClient.selectSkin(me.heroId(),skinPreview);feedbackMessage="正在更换…";
        }).active=!skins.isEmpty();
    }
    public void skinResult(dev.sgs.validation.match.SkinNetwork.Result result){
        if(result.request()!=skinRequest)return;
        skinRequest=0;feedbackMessage=result.reason();
        if(result.accepted()){overlay="";rebuildWidgets();}
    }
    private void drawSkinOverlay(GuiGraphics g,int x,int y,int w){
        var me=self();if(me==null)return;var skins=HeroSkins.available(me.heroId(),skinClassic());
        text(g,heroName(me.heroId())+" · 选择皮肤并确认",x+12,y+27,0xff513622,w-24);
        int cell=(w-36)/Math.max(1,skins.size()),ph=80,pw=57;
        var current=view.skins().getOrDefault(me.seat(),"classic");
        for(int i=0;i<skins.size();i++){
            var skin=skins.get(i);int px=x+18+i*cell+(cell-8-pw)/2,py=y+43;
            HeroRenderer.portraitCrop(g,skin.texture(),px,py,pw,ph);
            g.renderOutline(px-2,py-2,pw+4,ph+4,skinPreview.equals(skin.id())?0xffffb22f:0xff8e693d);
            if(current.equals(skin.id())){g.fill(px,py+ph-12,px+pw,py+ph,0xe538271c);text(g,"使用中",px+12,py+ph-10,0xffffdfa0,pw-12);}
        }
        if(skins.isEmpty())text(g,"此武将暂无可用立绘",x+18,y+63,0xff6c482b,w-36);
    }
    private void drawOverlay(GuiGraphics g){
        int w=overlayWidth(),x=(width-w)/2,y=overlayTop();
        g.pose().pushPose();g.pose().translate(0,0,350);
        g.fill(x-3,y-3,x+w+3,y+overlayHeight()+3,0xff8f6939);g.fill(x,y,x+w,y+overlayHeight(),0xffe5c799);
        String title=switch(overlay){case "CONVERT"->"转为";case "FLOWERS"->"送鲜花";case "EGGS"->"投鸡蛋";case "REPORT"->"举报玩家";case "REWARDS"->"奖励结算";case "SETTINGS"->"界面设置";case "HERO"->"本局武将";case "SKINS"->"武将换肤";case "MENU"->"菜单";default->"对局规则";};
        InkText.drawCentered(g,font,title,width/2,y+9,0xff4a2e19);
        if(overlay.equals("CONVERT")){nativeConversionFrames++;}
        else if(overlay.equals("SKINS"))drawSkinOverlay(g,x,y,w);
        else if(overlay.equals("REPORT")){
            int count=(int)view.snapshot().seats().stream().filter(p->p.human() && !p.self()).count();
            if(count==0)text(g,"本局没有其他真人可举报。",x+13,y+34,0xff513622,w-26);
            int ry=y+34+Math.max(1,(count+2)/3)*20;g.fill(x+9,ry+23,x+w-9,ry+47,0xfff5dfb7);
            text(g,"说明（可选，最多240字）",x+12,ry+14,0xff755435,w-24);
        }else if(overlay.equals("REWARDS"))drawReward(g,x+16,y+37,w-32);
        else if(overlay.equals("SETTINGS"))text(g,"选择界面缩放",x+18,y+35,0xff4e3522,w-36);
        else if(overlay.equals("HERO")){
            var heroes=view.snapshot().heroPresentations();int columns=Math.max(1,Math.min(8,heroes.size())),cell=(w-24)/columns;
            for(int i=0;i<heroes.size();i++){var h=heroes.get(i);int hx=x+12+(i%columns)*cell,hy=y+37+(i/columns)*86;
                HeroRenderer.portrait(g,font,h.id(),hx,hy,cell-5,60,0xffa67b40,true);text(g,h.name(),hx,hy+65,0xff4e3522,cell-4);
            }
        }else if(!overlay.equals("MENU")){
            String[] lines={"军六／军八：身份局，同一共享规则核心。","2v2：与队友协作击败敌方，按座位顺序行动。","每点体力显示一个独立半心，V1无国战。","点击手牌上抬；选择目标后确认使用。","自己的技能位于武将牌底部，悬停武将可查阅技能。","金色进度条显示服务器剩余操作时间。"};
            for(int i=0;i<lines.length;i++)text(g,lines[i],x+12,y+37+i*18,0xff4e3522,w-24);
        }
        if(!feedbackMessage.isEmpty())text(g,feedbackMessage,x+12,y+165,0xff754029,w-24);
        g.pose().popPose();
    }
    private void drawReward(GuiGraphics g,int x,int y,int w){
        var reward=FeedbackClient.reward(view.matchId());
        if(reward==null){text(g,"等待服务器结算本局奖励…",x,y,0xff6b4c2c,w);return;}
        InkText.drawCentered(g,font,"经验 +"+reward.xpAward()+"    荣誉 +"+reward.honorAward(),x+w/2,y,0xff6e3c1e);
        text(g,"累计经验 "+reward.totalXp()+" · 累计荣誉 "+reward.totalHonor(),x,y+20,0xff5b4028,w);
        text(g,"结算凭证 "+reward.receiptId(),x,y+38,0xff775b39,w);
        text(g,"奖励已由服务器保存，无需重复领取。",x,y+56,0xff775b39,w);
    }
    private void resultPage(int stage){
        resultStage=stage;overlay="";cardsOpen=false;logOpen=false;feedbackMessage="";
        if(stage==1 && view.snapshot().viewerSeat()>=0)FeedbackClient.request(view.matchId(),"QUERY_RESULT",-1,"","");
        rebuildWidgets();
    }
    private Button resultButton(String id,String label,int x,int y,int w,Runnable action){
        var box=board().box(x,y,w,48);var control=button(id,label,box.x(),box.y(),box.width(),action);control.setHeight(box.height());return control;
    }
    private void buildResultControls(){
        if(resultStage==0)resultButton("result-next","继续",740,799,192,()->resultPage(1));
        else{
            if(resultStage==1){
                resultButton("result-details","对局详情 ›",1260,736,240,()->resultPage(2));
                if(view.snapshot().viewerSeat()>=0)resultButton("result-query","重新查询奖励",700,799,272,
                    ()->FeedbackClient.request(view.matchId(),"QUERY_RESULT",-1,"",""));
            }else{
                resultButton("result-rewards","‹ 奖励结算",1260,736,240,()->resultPage(1));
                resultButton("finish","返回等待室",700,799,272,MatchClient::leaveFinished);
            }
            if(view.snapshot().viewerSeat()>=0)resultButton("result-report","遇到问题？举报",130,799,280,()->openOverlay("REPORT"));
        }
    }
    /** Mirrors the server's winner rule; no private information is used before a terminal snapshot. */
    private boolean resultWon(SeatView seat,MatchSnapshot snapshot){
        if(seat==null)return false;
        var outcomes=dev.sgs.core.engine.GameOutcome.values();
        if(snapshot.outcomeOrdinal()<0 || snapshot.outcomeOrdinal()>=outcomes.length)return false;
        return switch(outcomes[snapshot.outcomeOrdinal()]){
            case LORD_WIN->seat.identityOrdinal()==dev.sgs.core.Identity.LORD.ordinal() || seat.identityOrdinal()==dev.sgs.core.Identity.LOYALIST.ordinal();
            case REBELS_WIN->seat.identityOrdinal()==dev.sgs.core.Identity.REBEL.ordinal();
            case RENEGADE_WIN->seat.identityOrdinal()==dev.sgs.core.Identity.RENEGADE.ordinal() && seat.alive();
            case TEAM_A_WIN->seat.teamId()==0;case TEAM_B_WIN->seat.teamId()==1;default->false;
        };
    }
    private String resultRole(SeatView seat){
        if(seat.teamId()>=0){var viewer=self();return viewer==null?(seat.teamId()==0?"绿方":"红方"):seat.teamId()==viewer.teamId()?"友方":"敌方";}
        if(seat.identityOrdinal()<0 || seat.identityOrdinal()>=dev.sgs.core.Identity.values().length)return "身份未公开";
        return switch(dev.sgs.core.Identity.values()[seat.identityOrdinal()]){case LORD->"主公";case LOYALIST->"忠臣";case REBEL->"反贼";case RENEGADE->"内奸";};
    }
    private void drawResult(GuiGraphics g,MatchSnapshot snapshot){
        if(resultStage==0){drawSeats(g,snapshot);drawHand(g,-999,-999);}
        g.fill(0,0,width,height,resultStage==0?0x9920140d:0xdf302015);
        var frame=board().box(82,103,1508,776);g.fill(frame.x(),frame.y(),frame.right(),frame.bottom(),resultStage==0?0x993e291a:0xd54e3421);
        g.renderOutline(frame.x(),frame.y(),frame.width(),frame.height(),0xffae8550);
        var reward=FeedbackClient.reward(view.matchId());
        boolean spectator=snapshot.viewerSeat()<0;
        boolean draw=snapshot.outcomeOrdinal()==dev.sgs.core.engine.GameOutcome.DRAW.ordinal();
        String verdict=spectator?outcome(snapshot.outcomeOrdinal()):draw?"平局":(reward!=null?reward.won():resultWon(self(),snapshot))?"胜利":"失败";
        if(resultPresentationAt==0)resultPresentationAt=net.minecraft.Util.getMillis();
        float reveal=Math.min(1f,(net.minecraft.Util.getMillis()-resultPresentationAt)/700f);
        selectionText(g,resultStage==0?verdict:resultStage==1?"奖励结算":"对局详情",300,resultStage==0?128+Math.round((1-reveal)*28):128,1072,resultStage==0?94:65,0xfff0ca80);
        if(resultStage==0){
            var flourish=board().box(836-Math.round(400*reveal),258,Math.max(1,Math.round(800*reveal)),3);
            g.fill(flourish.x(),flourish.y(),flourish.right(),flourish.bottom(),0xffc89a52);
            selectionText(g,outcome(snapshot.outcomeOrdinal()),300,298,1072,47,0xffe3bd7c);
            selectionText(g,"对局已结束 · 全部公开身份见对局详情",300,423,1072,31,0xffd6b98e);
            selectionText(g,"继续查看服务器奖励结算",300,601,1072,30,0xffd6b98e);
            return;
        }
        if(resultStage==1){
            var own=self();
            selectionText(g,own==null?"旁观本局":own.name(),146,265,640,43,0xfff0d8a5);
            selectionText(g,"本局结果："+verdict,146,343,640,34,0xffe7bd7d);
            if(spectator){
                selectionText(g,"旁观者不参与本局奖励结算",840,320,650,39,0xffe0cba8);
            }else if(reward==null){
                selectionText(g,"等待服务器奖励凭证",820,310,690,39,0xffe0cba8);
                selectionText(g,"可重新查询；奖励仅由服务器结算入账",820,385,690,26,0xffcdb58c);
            }else{
                selectionText(g,"经验 +"+reward.xpAward()+"    荣誉 +"+reward.honorAward(),810,300,700,44,0xfff2cd86);
                selectionText(g,"累计经验 "+reward.totalXp()+"    累计荣誉 "+reward.totalHonor(),810,383,700,31,0xffe5cda6);
                selectionText(g,"凭证："+reward.receiptId(),810,462,700,25,0xffcdb58c);
                selectionText(g,"规则："+reward.ruleVersion(),810,512,700,23,0xffcdb58c);
                selectionText(g,"奖励已由服务器保存，无需重复领取",810,580,700,28,0xffe5cda6);
            }
            if(!feedbackMessage.isEmpty())selectionText(g,feedbackMessage,150,651,1360,29,0xffffbe95);
        }else{
            selectionText(g,"角色回合数："+snapshot.turnNumber(),760,215,755,26,0xffd8bc91);
            int rows=Math.max(1,(snapshot.seats().size()+1)/2),stride=Math.min(118,458/rows);
            for(int i=0;i<snapshot.seats().size();i++){
                var seat=snapshot.seats().get(i);int column=i/rows,row=i%rows,x=132+column*720,y=272+row*stride;
                var cell=board().box(x,y,688,stride-12);g.fill(cell.x(),cell.y(),cell.right(),cell.bottom(),seat.self()?0xff8a6237:0xff5f402a);
                var portrait=board().box(x+12,y+8,66,stride-28);HeroRenderer.portraitExact(g,font,seat.heroId(),portrait.x(),portrait.y(),portrait.width(),portrait.height(),0xffab8450,seat.alive(),HeroSkins.syncedTexture(seat.heroId(),view.skins().getOrDefault(seat.seat(),"classic"),HeroRenderer.classic(seat.heroId())));
                selectionText(g,resultRole(seat),x+86,y+10,95,29,0xffefd08f);
                selectionText(g,seat.name()+"  · "+(seat.human()?"玩家":"AI"),x+194,y+10,360,29,0xfff0ddbd);
                selectionText(g,heroName(seat.heroId())+"  · "+(seat.alive()?"存活":"阵亡"),x+190,y+51,365,25,0xffd8bd94);
                selectionText(g,draw?"平":resultWon(seat,snapshot)?"胜":"负",x+589,y+22,75,43,0xfff0ca80);
            }
        }
    }
    private void addSkillWidget(){
        buildSkillWidgets();
        applySkillAvailability();
        var widgets=children().stream().filter(child->child instanceof SkillWidget).map(child->(SkillWidget)child).toList();
        var layout=decisionLayout();
        for(int i=0;i<widgets.size();i++){
            var bounds=layout.skill(i,widgets.size());var widget=widgets.get(i);
            widget.setX(bounds.x());widget.setY(bounds.y());
            widget.setWidth(bounds.width());widget.setHeight(bounds.height());
        }
    }
    private void applySkillAvailability(){
        // Empty legacy views and unknown extension skills retain their existing behavior.
        for(var availability:view.snapshot().selfSkillAvailability()){
            if(availability.available())continue;
            var control=buttonsById.get(availability.skillId());
            if(!(control instanceof SkillWidget widget))continue;
            widget.disableWithReason(skillUnavailableReason(availability.reason()));
        }
    }
    private static String skillUnavailableReason(String reason){
        if(reason==null)return "当前不可发动";
        return switch(reason){
            case "SKILL_USED_THIS_TURN"->"本回合已发动";
            case "PLAYER_DEAD"->"已阵亡，无法发动";
            case "GAME_NOT_RUNNING"->"当前未进入对局";
            case "NOT_YOUR_TURN"->"尚未轮到你的回合";
            case "WRONG_PHASE"->"当前阶段不可发动";
            case "LORD_ONLY"->"仅主公可发动";
            default->"当前不可发动："+reason;
        };
    }
    private void buildSkillWidgets(){
        var me=self();if(me==null)return;
        var s=view.snapshot();boolean myPlay=self()!=null && !self().control().automatic() && s.prompt()==null && s.counterspellWindow()==null && s.processing().isEmpty() && s.currentSeat()==s.viewerSeat() && s.phaseOrdinal()==Phase.PLAY.ordinal();
        var presentation=s.heroPresentations().stream().filter(h->h.id().equals(me.heroId())).findFirst().orElse(null);
        if(presentation==null)return;
        int count=presentation.skills().size(),cols=Math.min(4,count),rows=Math.max(1,(count+3)/4);
        int rowHeight=Math.max(10,decisionLayout().skillRow().height()/rows);
        for(int i=0;i<count;i++){
            var meta=presentation.skills().get(i);int cell=Math.max(12,skillWidth()/Math.max(1,cols));
            boolean triggered=myPrompt() && s.prompt().typeOrdinal()==PromptType.SKILL_INVOKE.ordinal() && meta.id().equals(s.prompt().skillId());
            boolean active=switch(meta.kind()){
                case PASSIVE->false;case TRIGGERED->triggered;case ACTIVE,LIMITED->myPlay;
                case TOGGLE->switch(meta.id()){
                    case "qingguo"->myPrompt() && s.prompt().allowedTypeOrdinals().contains(CardType.SHAN.ordinal());
                    case "longdan"->myPlay || myPrompt() && (s.prompt().allowedTypeOrdinals().contains(CardType.SHAN.ordinal())||s.prompt().allowedTypeOrdinals().contains(CardType.SHA.ordinal()));
                    case "wusheng"->myPlay || myPrompt() && s.prompt().allowedTypeOrdinals().contains(CardType.SHA.ordinal());
                    case "jijiu"->myPrompt() && s.prompt().typeOrdinal()==PromptType.RESCUE_TAO.ordinal() && s.currentSeat()!=s.viewerSeat();
                    case "kanpo"->myPrompt() && s.prompt().allowedTypeOrdinals().contains(CardType.WUXIE.ordinal());
                    case "jiuchi"->myPlay || myPrompt() && s.prompt().allowedTypeOrdinals().contains(CardType.JIU.ordinal());
                    case "qixi","guose"->myPlay;
                    default->myPlay || myPrompt() && ownedCards(me).stream().anyMatch(this::hasConversion);
                };
            };
            active=active && (!meta.lordOnly() || me.identityOrdinal()==dev.sgs.core.Identity.LORD.ordinal());
            boolean selectedState=costSkill.equals(meta.id()) || mythicTransform.equals(meta.id()) || switch(meta.id()){case "longdan"->longdan;case "wusheng"->wusheng;case "qixi"->qixi;case "jijiu"->jijiu;case "qingguo"->qingguo;case "guose"->guose;case "duanliang"->duanliang;default->false;};
            var spec=new SkillWidget.Spec(meta.id(),meta.name(),meta.description(),SkillWidget.Kind.valueOf(meta.kind().name()));
            var widget=new SkillWidget(spec,skillLeft()+(i%cols)*cell,skillY()+(i/cols)*rowHeight,cell-1,active,selectedState,ignored->{
                if(triggered){response(true);return;}
                if(meta.id().equals("longdan")){longdan=!longdan;mythicTransform="";costSkill="";selected.clear();attackTargets.clear();rebuildWidgets();return;}
                if(meta.id().equals("wusheng")){wusheng=!wusheng;mythicTransform="";costSkill="";selected.clear();attackTargets.clear();rebuildWidgets();return;}
                if(Set.of("huoji","kanpo","lianhuan","jiuchi","shuangxiong").contains(meta.id())){
                    mythicTransform=mythicTransform.equals(meta.id())?"":meta.id();costSkill="";selected.clear();attackTargets.clear();
                    longdan=false;wusheng=false;qixi=false;jijiu=false;qingguo=false;guose=false;duanliang=false;rebuildWidgets();return;
                }
                if(meta.kind()==dev.sgs.core.addon.SkillPresentation.Kind.TOGGLE && meta.id().contains(":")){openConversion();return;}
                if(meta.kind()==dev.sgs.core.addon.SkillPresentation.Kind.TOGGLE)costSkill="";
                if(meta.id().equals("qixi")){qixi=!qixi;selected.clear();attackTargets.clear();rebuildWidgets();return;}
                if(meta.id().equals("jijiu")){jijiu=!jijiu;selected.clear();attackTargets.clear();rebuildWidgets();return;}
                if(meta.id().equals("qingguo")){qingguo=!qingguo;selected.clear();attackTargets.clear();rebuildWidgets();return;}
                if(meta.id().equals("guose")){guose=!guose;selected.clear();attackTargets.clear();rebuildWidgets();return;}
                if(meta.id().equals("duanliang")){duanliang=!duanliang;selected.clear();attackTargets.clear();rebuildWidgets();return;}
                if(meta.id().equals("qiangxi") && selected.isEmpty() && !costSkill.equals(meta.id())){beginSkillCost(meta.id());reason="选择一张武器，或直接确定失去1点体力";return;}
                if(meta.id().equals("dimeng") && !costSkill.equals(meta.id())){beginSkillCost(meta.id());return;}
                if(meta.minimumCards()>0 && selected.isEmpty() && !costSkill.equals(meta.id())){beginSkillCost(meta.id());return;}
                if(meta.maximumCards()>0 && (selected.size()<meta.minimumCards() || selected.size()>meta.maximumCards())){reason="此技能需要"+meta.minimumCards()+"至"+meta.maximumCards()+"张牌";return;}
                if(meta.maximumCards()>0 && !meta.allowEquipmentCosts() && me.hand().stream().filter(c->selected.contains(c.instanceId())).count()!=selected.size()){reason="此技能只能使用手牌作为代价";return;}
                if(meta.target()!=dev.sgs.core.addon.SkillPresentation.Target.NONE && target<0){reason="请选择技能目标";return;}
                if(meta.target()==dev.sgs.core.addon.SkillPresentation.Target.OTHER && target==s.viewerSeat()){reason="请选择其他角色";return;}
                if(meta.target()!=dev.sgs.core.addon.SkillPresentation.Target.NONE && s.seats().stream().noneMatch(candidate->candidate.seat()==target && candidate.alive())){reason="请选择存活角色";return;}
                if(meta.id().equals("jieyin") && s.seats().stream().noneMatch(candidate->candidate.seat()==target && candidate.hp()<candidate.maxHp())){reason="结姻需选择受伤的其他男性角色";return;}
                MatchClient.send(MatchAction.activeSkill(MatchClient.nextNonce(),meta.id(),meta.target()==dev.sgs.core.addon.SkillPresentation.Target.NONE?-1:target,meta.maximumCards()==0?List.of():List.copyOf(selected)));
            });widget.setHeight(rowHeight);buttonsById.put(meta.id(),addRenderableWidget(widget));
            // Compatibility alias for existing native drivers; points to the same component.
            if(meta.id().equals("rende") || meta.id().equals("paoxiao"))buttonsById.put("skill",widget);
        }
    }
    /** Private choices come exclusively from the server-filtered prompt, never another hand. */
    private boolean buildSkillChoices(){
        var prompt=view.snapshot().prompt();var type=PromptType.values()[prompt.typeOrdinal()];
        if(type==PromptType.EXPANSION_CHOICE || type==PromptType.EXPANSION_OPTION){buildExpansionChoices(prompt,type);return true;}
        if(type.supportsHandPositions()){buildZoneChoices(prompt);return true;}
        if(type==PromptType.WEAPON_COST_FIRST || type==PromptType.WEAPON_COST_SECOND){buildWeaponCosts(prompt,type==PromptType.WEAPON_COST_SECOND);return true;}
        if(type==PromptType.FANJIAN_SUIT){
            String[] labels={"黑桃 ♠","红桃 ♥","梅花 ♣","方块 ♦"};
            var lane=decisionLayout().confirm();int left=handLeft(),cell=Math.max(12,(handRight()-left-9)/4);
            for(int i=0;i<4;i++){int suit=i;var b=button("suit:"+i,labels[i],left+i*(cell+3),lane.y(),cell,
                ()->MatchClient.send(MatchAction.skillChoice(MatchClient.nextNonce(),prompt.promptId(),-1,suit)));b.setHeight(lane.height());}
            return true;
        }
        if(!EnumSet.of(PromptType.GUI_CAI_REPLACE,PromptType.YIJI_GIVE,
            PromptType.TUXI_TARGET,PromptType.GANGLIE_DISCARD,PromptType.WEAPON_DISCARD,PromptType.LIJIAN_TARGET,
            PromptType.GUANXING_ORDER,PromptType.LIULI_TARGET).contains(type))return false;
        boolean targetOnly=prompt.choices().isEmpty() && prompt.hiddenHandAvailable() && !prompt.allowedTargetSeats().isEmpty();
        // The target-only sentinel is a protocol detail, never an offered hidden card.
        boolean hiddenChoice=prompt.hiddenHandAvailable() && !targetOnly;
        if(targetOnly){
            int count=prompt.allowedTargetSeats().size(),cols=Math.min(3,Math.max(1,count));
            int cell=Math.max(12,(handRight()-handLeft()-6)/cols),rows=(count+cols-1)/cols;
            int top=decisionLayout().caption().y()-rows*22-7;
            for(int i=0;i<count;i++){
                int seat=prompt.allowedTargetSeats().get(i);
                var candidate=view.snapshot().seats().stream().filter(p->p.seat()==seat).findFirst().orElse(null);
                String label=(target==seat?"✓ ":"")+(seat+1)+"号 "+(candidate==null?"角色":candidate.name());
                button("skill-target:"+seat,label,handLeft()+i%cols*(cell+2),top+i/cols*22,cell-2,
                    ()->{target=seat;reason="";rebuildWidgets();});
            }
        }else if(ownedPromptChoice()==null && type!=PromptType.GUI_CAI_REPLACE){
            int count=prompt.choices().size()+(hiddenChoice?1:0),cols=Math.min(3,Math.max(1,count));
            int cell=Math.max(12,(handRight()-handLeft()-6)/cols),rows=(count+cols-1)/cols;
            int top=decisionLayout().caption().y()-rows*22-7-(type==PromptType.GUANXING_ORDER?25:0);
            for(int i=0;i<count;i++){
                boolean hidden=hiddenChoice && i==0;
                var card=hidden?null:prompt.choices().get(i-(hiddenChoice?1:0));
                long id=hidden?0:card.instanceId();
                String suit=hidden?"":switch(Suit.values()[card.suitOrdinal()]){case SPADE->"♠";case HEART->"♥";case CLUB->"♣";case DIAMOND->"♦";case NONE->"";};
                String label=hidden?"随机手牌（不展示）":cardName(card)+" "+suit+Rank.values()[card.rankOrdinal()].label();
                button("skill-choice:"+id,(skillChoiceCard==id?"✓ ":"")+label,handLeft()+i%cols*(cell+2),top+i/cols*22,cell-2,
                    ()->{skillChoiceCard=id;reason="";rebuildWidgets();});
            }
        }
        if(type==PromptType.GUANXING_ORDER){
            int cell=Math.max(12,(handRight()-handLeft()-4)/2),y=decisionLayout().caption().y()-29;
            for(int i=0;i<2;i++){int destination=i;
                button("skill-destination:"+i,(skillChoiceDestination==i?"✓ ":"")+(i==0?"牌堆顶":"牌堆底"),handLeft()+i*(cell+4),y,cell,
                    ()->{skillChoiceDestination=destination;reason="";rebuildWidgets();});
            }
        }
        String confirmText=switch(type){
            case YIJI_GIVE->"交给所选角色";case GUI_CAI_REPLACE->"替换判定牌";
            case WEAPON_DISCARD->"弃置这张手牌";case TUXI_TARGET->"突袭所选角色";case GANGLIE_DISCARD->"选择弃置牌";
            case LIJIAN_TARGET->"确认第二名男性";case GUANXING_ORDER->"确认位置";
            case LIULI_TARGET->"弃牌并转移杀";default->"取得所选";
        };
        confirm=decisionButton("skill-choice-confirm",confirmText,true,()->{
            long id=targetOnly?0:ownedPromptChoice()!=null?skillChoiceCard:type==PromptType.GUI_CAI_REPLACE && selected.size()==1?selected.iterator().next():skillChoiceCard;
            if(!(id==0 && prompt.hiddenHandAvailable()) && prompt.choices().stream().noneMatch(c->c.instanceId()==id)){
                reason=type==PromptType.GUI_CAI_REPLACE?"请选择自己的一张手牌":"请选择本次请求中的牌";return;
            }
            int recipient=type==PromptType.GUANXING_ORDER?skillChoiceDestination:prompt.allowedTargetSeats().isEmpty()?-1:target;
            if(!prompt.allowedTargetSeats().isEmpty() && !prompt.allowedTargetSeats().contains(recipient)){
                reason=type==PromptType.GUANXING_ORDER?"请选择牌堆顶或牌堆底":"请选择本次请求允许的目标角色";return;
            }
            MatchClient.send(MatchAction.skillChoice(MatchClient.nextNonce(),prompt.promptId(),id,recipient));
        });
        var owned=ownedPromptChoice();
        if(owned!=null)confirm.active=owned.allowsCard(skillChoiceCard)
            && (owned.targets().isEmpty() || owned.allowsTarget(target));
        String declineText=switch(type){case WEAPON_DISCARD->"让对方摸一张";case YIJI_GIVE->"余牌留给自己";case GANGLIE_DISCARD->"不弃牌并受伤";case GUANXING_ORDER->"余牌原序置顶";case LIULI_TARGET->"不转移";default->"放弃";};
        decisionButton("decline",declineText,false,()->MatchClient.send(
            MatchAction.promptResponse(MatchClient.nextNonce(),prompt.promptId(),false,-1,-1)));
        return true;
    }
    private void buildExpansionChoices(PromptView prompt,PromptType type){
        int left=handLeft(),width=Math.max(120,handRight()-left),cols=3,cell=(width-8)/cols;
        int top=decisionLayout().caption().y()-119;
        boolean options=type==PromptType.EXPANSION_OPTION;
        String[] labels=prompt.skillId().split("\\|");
        if(!prompt.choices().isEmpty()){
            int pages=Math.max(1,(prompt.choices().size()+5)/6);skillCostPage=Math.clamp(skillCostPage,0,pages-1);
            for(int i=skillCostPage*6;i<Math.min(prompt.choices().size(),skillCostPage*6+6);i++){
                var card=prompt.choices().get(i);long id=card.instanceId();int local=i%6;
                button("skill-choice:"+id,(skillChoiceCard==id?"✓ ":"")+cardName(card)+" "+Suit.values()[card.suitOrdinal()].name()+" "+Rank.values()[card.rankOrdinal()].label(),left+local%3*(cell+3),top+local/3*23,cell,
                    ()->{skillChoiceCard=id;rebuildWidgets();});
            }
            if(pages>1){var prev=button("exp-prev","‹",left,top+46,35,()->{skillCostPage--;rebuildWidgets();});prev.active=skillCostPage>0;
                var next=button("exp-next",(skillCostPage+1)+"/"+pages+" ›",left+39,top+46,80,()->{skillCostPage++;rebuildWidgets();});next.active=skillCostPage+1<pages;}
            top+=72;
        }
        for(int i=0;i<prompt.allowedTargetSeats().size();i++){
            int value=prompt.allowedTargetSeats().get(i);String label;
            if(options)label=labels.length>value+1?labels[value+1]:switch(prompt.skillId()){
                case "yinghun"->value==0?"摸X弃一":"摸一弃X";case "benghuai"->value==0?"失去体力":"减少体力上限";
                case "zhiji"->value==0?"回复体力":"摸两张牌";default->"选项 "+(value+1);};
            else {var seat=view.snapshot().seats().stream().filter(s->s.seat()==value).findFirst().orElse(null);label=(value+1)+"号 "+(seat==null?"角色":heroName(seat.heroId()));}
            if(options&&prompt.skillId().startsWith("guhuo_declare|")){String declared=label.toLowerCase(java.util.Locale.ROOT);label=art.stream().filter(a->a.id().equals(declared)).map(CardArtCatalog.Art::name).findFirst().orElse(label);}
            button("skill-target:"+value,(target==value?"✓ ":"")+label,left+i%3*(cell+3),top+i/3*22,cell,()->{target=value;rebuildWidgets();});
        }
        decisionButton("skill-choice-confirm","确定",true,()->{
            long id=prompt.choices().isEmpty()?0:skillChoiceCard;
            if(!prompt.choices().isEmpty()&&prompt.choices().stream().noneMatch(c->c.instanceId()==id)){reason="请选择本次请求的牌";return;}
            int destination=prompt.allowedTargetSeats().isEmpty()?-1:target;
            if(!prompt.allowedTargetSeats().isEmpty()&&!prompt.allowedTargetSeats().contains(destination)){reason="请选择本次请求的选项";return;}
            MatchClient.send(MatchAction.skillChoice(MatchClient.nextNonce(),prompt.promptId(),id,destination));
        });
        decisionButton("decline","取消",false,()->MatchClient.send(MatchAction.promptResponse(MatchClient.nextNonce(),prompt.promptId(),false,-1,-1)));
    }
    private SeatView zoneSubject(PromptView prompt){return view.snapshot().seats().stream().filter(seat->seat.seat()==prompt.subjectSeat()).findFirst().orElse(null);}
    private ReferenceBattleLayout.Box harvestPanel(){
        int count=view.snapshot().prompt().choices().size(),columns=Math.max(1,Math.min(4,count)),rows=Math.max(1,(count+3)/4);
        int w=columns*190+64,h=rows*242+140;
        return board().box((1672-w)/2,(941-h)/2,w,h);
    }
    private void submitHarvest(PromptView prompt,CardView card){
        if(!draftPrompt() || view.snapshot().prompt().promptId()!=prompt.promptId() || harvestPendingPrompt!=0)return;
        harvestPendingPrompt=prompt.promptId();skillChoiceCard=card==null?-1:card.instanceId();
        buttonsById.forEach((key,control)->{if(key.startsWith("choice:") || key.equals("decline"))control.active=false;});
        MatchClient.send(MatchAction.promptResponse(MatchClient.nextNonce(),prompt.promptId(),card!=null,card==null?-1:card.instanceId(),-1));
    }
    private void buildHarvestChoices(PromptView prompt){
        var box=harvestPanel();float unit=board().unit();int count=prompt.choices().size();
        int cardW=Math.max(1,Math.round(160*unit)),cardH=Math.max(1,Math.round(224*unit)),step=Math.max(1,Math.round(190*unit));
        for(int i=0;i<count;i++){
            var card=prompt.choices().get(i);int columns=Math.min(4,count-i/4*4),column=i%4;
            int x=box.x()+box.width()/2-(columns-1)*step/2-cardW/2+column*step,y=box.y()+Math.round((78+i/4*242)*unit);
            var widget=new ZoneChoiceButton(card,card.instanceId(),i,x,y,cardW,cardH,()->submitHarvest(prompt,card));
            widget.active=harvestPendingPrompt==0;
            buttonsById.put("choice:"+i,addRenderableWidget(widget));
        }
        int footerW=Math.max(1,Math.round(220*unit)),footerH=Math.max(12,Math.round(32*unit));
        var automatic=button("decline","使用默认选择",box.x()+box.width()/2-footerW/2,box.bottom()-Math.round(46*unit),footerW,()->submitHarvest(prompt,null));
        automatic.setHeight(footerH);automatic.active=harvestPendingPrompt==0;
    }
    private void buildWeaponCosts(PromptView prompt,boolean second){
        var cards=prompt.choices();int pages=Math.max(1,(cards.size()+5)/6);skillCostPage=Math.clamp(skillCostPage,0,pages-1);
        int cell=Math.max(12,(handRight()-handLeft()-6)/3),top=decisionLayout().caption().y()-70;
        for(int i=skillCostPage*6;i<Math.min(cards.size(),skillCostPage*6+6);i++){
            var card=cards.get(i);int local=i%6;long id=card.instanceId();
            var control=button("skill-choice:"+id,(skillChoiceCard==id?"✓ ":"")+cardName(card)+" "+Rank.values()[card.rankOrdinal()].label(),handLeft()+local%3*(cell+2),top+local/3*22,cell-2,()->{skillChoiceCard=id;reason="";rebuildWidgets();});
            control.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(cardName(card)+" · "+switch(Suit.values()[card.suitOrdinal()]){case SPADE->"黑桃";case HEART->"红桃";case CLUB->"梅花";case DIAMOND->"方块";case NONE->"";}+" "+Rank.values()[card.rankOrdinal()].label())));
        }
        if(pages>1){
            var prev=button("cost-prev","‹",handLeft(),top+45,40,()->{skillCostPage--;rebuildWidgets();});prev.active=skillCostPage>0;
            var next=button("cost-next",(skillCostPage+1)+"/"+pages+" ›",handLeft()+44,top+45,70,()->{skillCostPage++;rebuildWidgets();});next.active=skillCostPage+1<pages;
        }
        decisionButton("skill-choice-confirm",second?"弃置两张并命中":"暂选第一张",true,()->{
            if(cards.stream().noneMatch(c->c.instanceId()==skillChoiceCard)){reason="请选择服务器允许的代价牌";return;}
            if(weaponCostPendingPrompt==prompt.promptId())return;weaponCostPendingPrompt=prompt.promptId();lockWeaponCosts();
            MatchClient.send(MatchAction.skillChoice(MatchClient.nextNonce(),prompt.promptId(),skillChoiceCard,-1));
        });
        decisionButton("decline","取消发动（不弃牌）",false,()->{
            if(weaponCostPendingPrompt==prompt.promptId())return;weaponCostPendingPrompt=prompt.promptId();lockWeaponCosts();
            MatchClient.send(MatchAction.promptResponse(MatchClient.nextNonce(),prompt.promptId(),false,-1,-1));
        });
        if(weaponCostPendingPrompt==prompt.promptId())lockWeaponCosts();
    }
    private void lockWeaponCosts(){buttonsById.forEach((key,control)->{if(key.startsWith("skill-choice:")||Set.of("skill-choice-confirm","decline","cost-prev","cost-next").contains(key))control.active=false;});}
    private void drawHarvestPanel(GuiGraphics g,PromptView prompt){
        var box=harvestPanel();float unit=board().unit();
        g.fill(0,0,width,height,0x96080604);g.fill(box.x()-2,box.y()-2,box.right()+2,box.bottom()+2,0xffac844b);g.fill(box.x(),box.y(),box.right(),box.bottom(),0xff3c2c1e);
        InkText.fitted(g,font,"五谷丰登 · 选择一张公开牌",box.x()+box.width()/2f,box.y()+Math.round(16*unit),box.width()-20,Math.max(10,Math.round(24*unit)),0xffebce92);
        long remaining=Math.max(0,(prompt.deadlineMillis()-System.currentTimeMillis()+999)/1000);
        String status=harvestPendingPrompt==prompt.promptId()?"正在取得所选牌…":"点击牌面取得 · 剩余 "+remaining+" 秒";
        InkText.fitted(g,font,status,box.x()+box.width()/2f,box.y()+Math.round(49*unit),box.width()-20,Math.max(8,Math.round(18*unit)),0xffd2bb92);
    }
    private List<CardView> zonePublicCards(PromptView prompt,String section){
        var subject=zoneSubject(prompt);if(subject==null)return List.of();
        var visible=new LinkedHashMap<Long,CardView>();
        if(section.equals("equipment")){for(var card:new CardView[]{subject.weapon(),subject.armor(),subject.horsePlus(),subject.horseMinus()})if(card!=null)visible.put(card.instanceId(),card);}
        else if(section.equals("judgment"))for(var card:subject.delay())visible.put(card.instanceId(),card);
        return prompt.choices().stream().filter(card->visible.containsKey(card.instanceId())).map(card->visible.get(card.instanceId())).toList();
    }
    private int zoneHandCount(PromptView prompt){var subject=zoneSubject(prompt);return subject==null || !PromptType.values()[prompt.typeOrdinal()].supportsHandPositions() || !prompt.hiddenHandAvailable()?0:Math.max(0,subject.handCount());}
    private List<String> zoneRows(PromptView prompt){
        var rows=new ArrayList<String>();
        if(!zonePublicCards(prompt,"equipment").isEmpty())rows.add("equipment");
        if(!zonePublicCards(prompt,"judgment").isEmpty())rows.add("judgment");
        if(zoneHandCount(prompt)>0)rows.add("hand");
        return rows;
    }
    private ReferenceBattleLayout.Box zoneChoicePanel(){
        int rows=zoneRows(view.snapshot().prompt()).size();
        int h=rows>2?790:rows>1?650:440;
        return board().box(330,(941-h)/2,1012,h);
    }
    private void buildZoneChoices(PromptView prompt){
        var box=zoneChoicePanel();float unit=board().unit();int pad=Math.max(6,Math.round(22*unit));
        int hideW=Math.max(50,Math.round(138*unit));
        var hide=button("zone-hide","隐藏窗口",box.right()-pad-hideW,box.y()+Math.max(3,Math.round(8*unit)),hideW,()->{zoneChoiceHidden=true;rebuildWidgets();});
        hide.setHeight(Math.max(12,Math.round(28*unit)));
        var rows=zoneRows(prompt);int rowHeight=(box.height()-Math.round(124*unit))/Math.max(1,rows.size());
        int cardH=Math.min(Math.round(238*unit),rowHeight-Math.round(26*unit));
        int cardW=Math.max(14,Math.round(cardH*80f/112f));
        for(int row=0;row<rows.size();row++){
            String section=rows.get(row);boolean hidden=section.equals("hand");var cards=zonePublicCards(prompt,section);
            int count=hidden?zoneHandCount(prompt):cards.size();
            var layout=dev.sgs.validation.client.layout.ZoneCardRowLayout.fit(box.width()-2*pad,cardW,Math.max(3,Math.round(12*unit)),count);
            int y=box.y()+Math.round(79*unit)+row*rowHeight;
            for(int i=0;i<count;i++){
                var card=hidden?null:cards.get(i);long id=hidden?dev.sgs.core.engine.HandSelection.positionToken(i):card.instanceId();
                var widget=new ZoneChoiceButton(card,id,i,box.x()+pad+layout.x(i),y,cardW,cardH,()->{skillChoiceCard=id;reason="";rebuildWidgets();});
                widget.setWidth(layout.hitWidth(i));
                buttonsById.put(hidden?"zone-hand:"+i:"skill-choice:"+id,addRenderableWidget(widget));
            }
        }
        int footer=box.bottom()-Math.max(17,Math.round(41*unit)),wide=Math.max(55,Math.round(220*unit));
        confirm=button("skill-choice-confirm","确认选择",box.x()+box.width()/2-wide/2,footer,wide,()->{
            long id=skillChoiceCard;boolean allowed=dev.sgs.core.engine.HandSelection.allowsPosition(PromptType.values()[prompt.typeOrdinal()],prompt.hiddenHandAvailable(),zoneHandCount(prompt),id)
                || id>0 && prompt.choices().stream().anyMatch(card->card.instanceId()==id);
            if(!allowed){reason="请选择一张牌";return;}
            if(zonePendingPrompt==prompt.promptId())return;
            zonePendingPrompt=prompt.promptId();lockZoneButtons();
            MatchClient.send(MatchAction.skillChoice(MatchClient.nextNonce(),prompt.promptId(),id,-1));
        });
        confirm.setHeight(Math.max(12,Math.round(28*unit)));
        if(zonePendingPrompt==prompt.promptId())lockZoneButtons();
    }
    private void lockZoneButtons(){
        buttonsById.forEach((key,control)->{if(key.startsWith("zone-") || key.startsWith("skill-choice:") || key.equals("skill-choice-confirm") || key.equals("decline"))control.active=false;});
    }


    private final class ZoneChoiceButton extends SgsButton {
        private final CardView card;private final long token;private final int faceWidth;
        ZoneChoiceButton(CardView card,long token,int slot,int x,int y,int w,int h,Runnable action){
            super(x,y,w,h,Component.literal(card==null?"背面手牌":cardName(card)),ignored->action.run());this.card=card;this.token=token;this.faceWidth=w;
        }
        @Override protected void renderWidget(GuiGraphics g,int mx,int my,float partial){
            int x=getX(),y=getY(),w=faceWidth,h=getHeight();boolean chosen=skillChoiceCard==token;float scale=Math.min(w/80f,h/112f);
            if(card!=null)drawCard(g,card,x,y,scale,chosen);
            else dev.sgs.validation.client.art.CardBackRenderer.draw(g,x,y,w,h);
            if(chosen || isHoveredOrFocused())g.renderOutline(x-1,y-1,w+2,h+2,chosen?0xffffd273:0xffecd49b);
        }
    }
    private void drawZoneChoicePanel(GuiGraphics g,MatchSnapshot snapshot){
        var box=zoneChoicePanel();var prompt=snapshot.prompt();
        g.fill(0,0,width,height,0x96080604);g.fill(box.x()-2,box.y()-2,box.right()+2,box.bottom()+2,0xffac844b);g.fill(box.x(),box.y(),box.right(),box.bottom(),0xff3c2c1e);
        float unit=board().unit();String title=switch(prompt.skillId()){
            case "fankui"->"反馈";case "guohe"->"过河拆桥";case "qilin_gong"->"麒麟弓";case "hanbing"->"寒冰剑";case "shunshou"->"顺手牵羊";default->skillName(prompt.skillId());
        };
        InkText.fitted(g,font,title,box.x()+box.width()/2f,box.y()+Math.round(13*unit),Math.round(500*unit),Math.max(12,Math.round(22*unit)),0xffebce92);
        var rows=zoneRows(prompt);int rowHeight=(box.height()-Math.round(124*unit))/Math.max(1,rows.size());
        for(int row=0;row<rows.size();row++){
            String label=switch(rows.get(row)){case "equipment"->"装备";case "judgment"->"判定";default->"手牌";};
            InkText.fitted(g,font,label,box.x()+box.width()/2f,box.y()+Math.round(57*unit)+row*rowHeight,Math.round(100*unit),Math.max(8,Math.round(15*unit)),0xffd2bb92);
        }
        if(rows.isEmpty())InkText.drawCentered(g,font,"没有可选择的牌",box.x()+box.width()/2,box.y()+box.height()/2,0xffdac19a);
        if(!reason.isEmpty())InkText.drawCentered(g,font,reason,box.x()+box.width()/2,box.bottom()-Math.round(61*unit),0xffffb08c);
    }
    private long currentWuxiePromptId(){
        var prompt=view.snapshot().prompt();
        return myPrompt() && prompt.typeOrdinal()==PromptType.WUXIE_COUNTER.ordinal()?prompt.promptId():0;
    }
    private void lockWuxieButtons(){
        for(String key:List.of("respond","decline","wuxie-pass-scope")){
            var control=buttonsById.get(key);if(control!=null)control.active=false;
        }
    }
    private boolean beginWuxieSubmit(long promptId){
        if(promptId==0 || currentWuxiePromptId()!=promptId || wuxiePendingPromptId!=0)return false;
        wuxiePendingPromptId=promptId;lockWuxieButtons();return true;
    }
    private void passWuxieScope(long promptId){
        if(!beginWuxieSubmit(promptId))return;
        MatchClient.send(MatchAction.passWuxieScope(MatchClient.nextNonce(),promptId));
    }
    private void response(boolean accept){
        var prompt=view.snapshot().prompt();
        if(prompt!=null && prompt.typeOrdinal()==PromptType.WUXIE_COUNTER.ordinal() && wuxiePendingPromptId!=0)return;
        if(prompt.typeOrdinal()==PromptType.SKILL_INVOKE.ordinal()){
            MatchClient.send(MatchAction.promptResponse(MatchClient.nextNonce(),prompt.promptId(),accept,-1,-1));return;
        }
        if(accept && selected.size()==2 && prompt.allowedTypeOrdinals().contains(CardType.SHA.ordinal())){
            var me=self();
            if(me.weapon()==null || me.weapon().typeOrdinal()!=CardType.ZHANGBA.ordinal()
                || me.hand().stream().filter(c->selected.contains(c.instanceId())).count()!=2){reason="丈八蛇矛需要两张自己的手牌";return;}
            MatchClient.send(MatchAction.promptCardsResponse(MatchClient.nextNonce(),prompt.promptId(),List.copyOf(selected)));return;
        }
        if(accept && selected.size()!=1){reason="请选择一张响应牌，或丈八的两张手牌";return;}
        long card=selected.stream().findFirst().orElse(-1L);
        if(accept && card<0){reason="先选择响应手牌";return;}
        int declared=-1;
        if(accept){
            var selectedCard=ownedCards(self()).stream().filter(c->c.instanceId()==card).findFirst().orElse(null);
            if(selectedCard==null){reason="请选择自己的牌";return;}
            if(qingguo && self().hand().stream().noneMatch(c->c.instanceId()==selectedCard.instanceId())){reason="倾国只能使用黑色手牌，不能使用装备";return;}
            boolean fire=prompt.typeOrdinal()==PromptType.HUOGONG_REVEAL.ordinal() || prompt.typeOrdinal()==PromptType.HUOGONG_PAY.ordinal();
            if(fire && self().hand().stream().noneMatch(c->c.instanceId()==selectedCard.instanceId())){reason="火攻需要选择自己的手牌";return;}
            if(prompt.typeOrdinal()==PromptType.HUOGONG_PAY.ordinal()
                && (prompt.choices().size()!=1 || selectedCard.suitOrdinal()!=prompt.choices().getFirst().suitOrdinal())){reason="请选择与展示牌同花色的手牌";return;}
            declared=fire?selectedCard.typeOrdinal():declaredType(selectedCard).ordinal();
            if(!fire && !prompt.allowedTypeOrdinals().contains(declared) && hasConversion(selectedCard)){openConversion();return;}
            if(!prompt.allowedTypeOrdinals().contains(declared)){reason="所选牌不能响应此请求；可选择转化方式";return;}
        }
        if(prompt.typeOrdinal()==PromptType.WUXIE_COUNTER.ordinal() && !beginWuxieSubmit(prompt.promptId()))return;
        MatchClient.send(MatchAction.promptResponse(MatchClient.nextNonce(),prompt.promptId(),accept,card,declared));
    }
    private boolean fangtianSelection(){
        var me=self();var snap=view.snapshot();
        if(me==null || snap.status()!=1 || !costSkill.isEmpty() || selected.size()!=1 || me.handCount()!=1 || snap.prompt()!=null
            || snap.counterspellWindow()!=null || !snap.processing().isEmpty() || snap.currentSeat()!=snap.viewerSeat()
            || snap.phaseOrdinal()!=Phase.PLAY.ordinal() || (me.weapon()==null || me.weapon().typeOrdinal()!=CardType.FANGTIAN.ordinal()))return false;
        return me.hand().stream().filter(c->selected.contains(c.instanceId())).anyMatch(c->Set.of(CardType.SHA,CardType.HUOSHA,CardType.LEISHA).contains(declaredType(c)));
    }
    private void play(){
        if(!costSkill.isEmpty()){var skill=buttonsById.get(costSkill);if(skill!=null && skill.active)skill.onPress();return;}
        if(view.snapshot().counterspellWindow()!=null || !view.snapshot().processing().isEmpty()){reason="等待当前牌结算结束";return;}
        if(selected.size()!=1){reason="请选择一张手牌";return;}
        long id=selected.iterator().next();
        var card=ownedCards(self()).stream().filter(c->c.instanceId()==id).findFirst().orElseThrow();
        String unavailable=handCardBlockedReason(card);if(!unavailable.isEmpty()){selected.remove(id);reason=unavailable;return;}
        if(guose && card.suitOrdinal()!=Suit.DIAMOND.ordinal()){reason="国色需要选择一张方块手牌或装备";return;}
        var declared=declaredType(card);
        if(card.playOptions().stream().noneMatch(o->o.available() && o.declaredTypeOrdinal()==declared.ordinal()) && hasConversion(card)){openConversion();return;}
        String blocked=targetHint(target);if(blocked!=null && !blocked.isEmpty()){reason=targetHintText(blocked);return;}
        if(fangtianSelection()){
            var targets=attackTargets.isEmpty() && target>=0?List.of(target):List.copyOf(attackTargets);
            if(targets.isEmpty()){reason="方天画戟：请选择1至3名目标，再次点击可取消";return;}
            MatchClient.send(MatchAction.playAttackTargets(MatchClient.nextNonce(),id,declared.ordinal(),targets));return;
        }
        MatchClient.send(declared.ordinal()==card.typeOrdinal()?MatchAction.playCard(MatchClient.nextNonce(),id,target,secondary)
            :MatchAction.playCardAs(MatchClient.nextNonce(),id,declared.ordinal(),target,secondary));
    }
    private String targetHint(int seat){
        var snapshot=view.snapshot();
        var choice=ownedPromptChoice();
        if(choice!=null && !choice.targets().isEmpty())return choice.allowsTarget(seat)?"":"TARGET_PROHIBITED";
        if(snapshot.status()!=1 || snapshot.prompt()!=null || snapshot.counterspellWindow()!=null || !snapshot.processing().isEmpty()
            || snapshot.currentSeat()!=snapshot.viewerSeat() || snapshot.phaseOrdinal()!=Phase.PLAY.ordinal() || selected.size()!=1 || self()==null)return null;
        long id=selected.iterator().next();var card=self().hand().stream().filter(c->c.instanceId()==id).findFirst().orElse(null);
        if(card==null || declaredType(card).ordinal()!=card.typeOrdinal())return null;
        return snapshot.selfTargetAvailability().stream().filter(h->h.cardId()==id && h.targetSeat()==seat).map(h->h.reason()).findFirst().orElse(null);
    }
    private String targetHintText(String code){return switch(code){
        case "OUT_OF_RANGE"->"距离不足";case "TARGET_SELF"->"不能选择自己";case "TARGET_DEAD"->"角色已阵亡";
        case "TARGET_NO_CARDS"->"目标没有可选牌";case "TARGET_PROHIBITED"->"不能成为此牌目标";default->"此目标不可选";
    };}
    private boolean hasSelfSkill(String id){return self()!=null && view.snapshot().heroPresentations().stream().filter(h->h.id().equals(self().heroId())).flatMap(h->h.skills().stream()).anyMatch(skill->skill.id().equals(id));}
    private CardType declaredType(CardView card){
        if(selected.size()==1 && selected.contains(card.instanceId()) && convertedCard==card.instanceId() && convertedType>=0
            && card.playOptions().stream().anyMatch(o->o.available() && o.declaredTypeOrdinal()==convertedType))return CardType.values()[convertedType];
        var actual=CardType.values()[card.typeOrdinal()];
        var suit=Suit.values()[card.suitOrdinal()];
        if(!mythicTransform.isEmpty())return switch(mythicTransform){
            case "huoji"->CardType.HUOGONG;case "kanpo"->CardType.WUXIE;case "lianhuan"->CardType.TIESUO;
            case "jiuchi"->CardType.JIU;case "shuangxiong"->CardType.JUEDOU;default->actual;};
        if(qingguo && self()!=null && hasSelfSkill("qingguo") && myPrompt()
            && view.snapshot().prompt().allowedTypeOrdinals().contains(CardType.SHAN.ordinal())
            && (suit==Suit.SPADE || suit==Suit.CLUB) && self().hand().stream().anyMatch(c->c.instanceId()==card.instanceId()))return CardType.SHAN;
        if(guose && self()!=null && hasSelfSkill("guose") && suit==Suit.DIAMOND
            && view.snapshot().prompt()==null && view.snapshot().counterspellWindow()==null && view.snapshot().processing().isEmpty() && view.snapshot().currentSeat()==view.snapshot().viewerSeat()
            && view.snapshot().phaseOrdinal()==Phase.PLAY.ordinal())return CardType.LEBU;
        if(duanliang && self()!=null && hasSelfSkill("duanliang") && (suit==Suit.SPADE || suit==Suit.CLUB)
            && (actual.category()==dev.sgs.core.card.CardCategory.BASIC || actual.isEquipment()))return CardType.BINGLIANG;
        if(qixi && self()!=null && hasSelfSkill("qixi") && (suit==Suit.SPADE || suit==Suit.CLUB))return CardType.GUOHE;
        if(jijiu && self()!=null && hasSelfSkill("jijiu") && (suit==Suit.HEART || suit==Suit.DIAMOND))return CardType.TAO;
        if(wusheng && self()!=null && hasSelfSkill("wusheng")
            && (Suit.values()[card.suitOrdinal()]==Suit.HEART || Suit.values()[card.suitOrdinal()]==Suit.DIAMOND))return CardType.SHA;
        if(!longdan || self()==null || !hasSelfSkill("longdan"))return actual;
        return actual==CardType.SHA || actual==CardType.HUOSHA || actual==CardType.LEISHA?CardType.SHAN:actual==CardType.SHAN?CardType.SHA:actual;
    }
    public String heroName(String id){return view.snapshot().heroPresentations().stream().filter(h->h.id().equals(id)).map(dev.sgs.core.addon.HeroPresentation::name).findFirst().orElse(id==null || id.isEmpty()?"待选武将":id);}
    private String skillName(String id){return view.snapshot().heroPresentations().stream().flatMap(h->h.skills().stream()).filter(s->s.id().equals(id)).map(dev.sgs.core.addon.SkillPresentation::name).findFirst().orElse(switch(id){case "guhuo_response"->"蛊惑响应";case "guhuo_question"->"质疑蛊惑";case "zhiba_gain"->"制霸 · 获得拼点牌";case "zhiba_accept"->"制霸 · 接受拼点";case "yaowu_recover"->"耀武 · 回复体力或摸牌";case "bagua"->"八卦阵";case "zhuque"->"朱雀羽扇";case "qilin_gong"->"麒麟弓";case "cixiong"->"雌雄双股剑";case "hanbing"->"寒冰剑";case "guanshi"->"贯石斧";case "qinglong"->"青龙偃月刀";case "fangtian"->"方天画戟";default->id;});}
    private String cardName(CardView card){
        String id=CardType.values()[card.typeOrdinal()].id();
        return art.stream().filter(a->a.id().equals(id)).map(CardArtCatalog.Art::name).findFirst()
            .orElseGet(()->Component.translatable("sgs.card."+id).getString());
    }
    private void text(GuiGraphics g,String value,int x,int y,int color,int limit){g.drawString(font,font.plainSubstrByWidth(value,Math.max(1,limit)),x,y,color,false);}
    private long completedRenderCount;
    private boolean skinNameHovered;
    private String renderedSelfName="";


    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        nativeRenderedFrames++;
        attackTargetMarkers.clear();
        identityMarkHits.clear();
        identityChoiceHits.clear();
        // Native windows may briefly report 1x1 during reconnect/minimize. A full board cannot
        // fit; stale hover hitboxes must not open a hand preview with reversed clamp bounds.
        if(width<80 || height<80 || g.guiWidth()<80 || g.guiHeight()<80){
            handHits.clear();equipmentHits.clear();seatHits.clear();countHits.clear();return;
        }
        visiblePaperTip="";
        if(errorShownUntil>0 && System.currentTimeMillis()>errorShownUntil){reason="";errorShownUntil=0;}
        skinNameHovered=skinStripAvailable() && overlay.isEmpty() && !cardsOpen && !logOpen && skinStrip().contains(mx,my);
        CardRenderer.background(g,width,height);var s=view.snapshot();
        if(s.status()==2 && !cardsOpen && !logOpen){
            // Let the final blow identify its victim before the result replaces the board.
            if(damageFeedback.active(net.minecraft.Util.getMillis())){drawSeats(g,s);return;}
            drawResult(g,s);
            if(socialPaletteVisible())drawSocialPalette(g);
        if(!overlay.isEmpty())drawOverlay(g);
            g.pose().pushPose();if(!overlay.isEmpty())g.pose().translate(0,0,360);
            super.render(g,mx,my,partial);g.pose().popPose();return;
        }
        g.renderOutline(1,1,width-2,height-2,0xff8b663c);
        previewedSeat=-1;
        int header=Math.max(15,Math.round(55*board().unit()));g.fill(0,0,width,header,0xee36271d);g.hLine(0,width,header,0xffaa8247);g.hLine(0,width,header+2,0xff664627);
        String phase=s.phaseOrdinal()>=0 && s.phaseOrdinal()<Phase.values().length?switch(Phase.values()[s.phaseOrdinal()]){
            case NONE->"结算";case PREPARE->"准备";case JUDGMENT->"判定";case DRAW->"摸牌";case PLAY->"出牌";case DISCARD->"弃牌";case END->"结束";}:"准备";
        int members=s.seats().size();String mode=members==4?"2v2 对战":members==8?"军八身份场":"军六身份场";
        var headerBox=board().box(137,8,540,39);g.renderOutline(headerBox.x(),headerBox.y(),headerBox.width(),headerBox.height(),0xff896239);
        String[] headFields={mode,"成员 "+members+"/"+members,"房间号:"+view.roomId()};int[] headX={178,365,516};
        for(int i=0;i<3;i++){var p=board().box(headX[i],17,1,1);float scale=Math.max(.6f,board().unit()*2.6f);g.pose().pushPose();g.pose().translate(p.x(),p.y(),0);g.pose().scale(scale,scale,1);InkText.draw(g,font,headFields[i],0,0,0xffe1bd80);g.pose().popPose();}
        var social=board().social();g.fill(social.x()-2,social.y()-2,social.right()+2,social.bottom()+2,0xff956e3d);g.fill(social.x(),social.y(),social.right(),social.bottom(),0xe036271e);
        if(s.identityIntro()!=null){
            drawIdentityIntro(g,s);
        }else if(s.heroBan()!=null){
            drawHeroBan(g,s);
        }else if(s.openingHand()!=null){
            drawOpening(g,s,mx,my);
        }else if(s.status()==0){
            drawSelection(g,s);drawSelectionSeatMap(g,s);
        }else{
            drawSeats(g,s);
            drawSkinStrip(g,mx,my);
            drawHand(g,mx,my);
            long deadline=s.counterspellWindow()!=null?s.counterspellWindow().deadlineMillis():s.prompt()!=null?s.prompt().deadlineMillis():s.phaseDeadlineMillis();
            String prompt=myPrompt()?(s.prompt().typeOrdinal()==PromptType.SKILL_INVOKE.ordinal()?s.prompt().skillId().equals("yaowu_recover")?"耀武：选择回复体力或摸一张牌":"是否发动"+skillName(s.prompt().skillId())+"？":"请响应："+promptName(s.prompt().typeOrdinal()))
                :"";
            if(fangtianSelection())prompt="方天画戟：已选"+attackTargets.size()+"/3目标 · 再次点击取消";
            if(wuxiePending())prompt="";
            boolean chainCard=self()!=null && selected.size()==1 && self().hand().stream().anyMatch(c->selected.contains(c.instanceId()) && c.typeOrdinal()==CardType.TIESUO.ordinal());
            if(chainCard && !myPrompt() && s.counterspellWindow()==null && s.processing().isEmpty()){
                prompt="铁索：选1~2目标；无目标重铸";
                if(confirm!=null)confirm.setMessage(Component.literal(target<0?"重铸所选":"使用铁索"));
            }else if(confirm!=null && !myPrompt() && s.counterspellWindow()==null && s.processing().isEmpty() && s.phaseOrdinal()==Phase.PLAY.ordinal())confirm.setMessage(Component.literal("确定"));
            if(s.prompt()!=null && s.prompt().typeOrdinal()==PromptType.HUOGONG_PAY.ordinal() && !s.prompt().choices().isEmpty()){
                var revealed=s.prompt().choices().getFirst();
                String suit=switch(Suit.values()[revealed.suitOrdinal()]){case SPADE->"♠";case CLUB->"♣";case HEART->"♥";case DIAMOND->"♦";default->"";};
                prompt=myPrompt()?"火攻：弃置"+suit+"手牌 · "+cardName(revealed):"火攻展示："+suit+" "+cardName(revealed);
            }
            boolean ownDecision=myPrompt() || s.prompt()==null && s.counterspellWindow()==null
                && s.processing().isEmpty() && s.currentSeat()==s.viewerSeat();
            if(s.status()==1 && ownDecision && (self()==null || !self().control().automatic()))drawDecisionTimer(g,prompt,deadline);
            if(myPrompt() && s.prompt().typeOrdinal()==PromptType.GUI_CAI_REPLACE.ordinal() && !s.processing().isEmpty()){
                var current=s.processing().getLast();
                text(g,"当前判定："+cardName(current)+" · "+Rank.values()[current.rankOrdinal()].label(),handLeft(),decisionLayout().caption().y()-14,0xffe8bc86,handRight()-handLeft());
            }
            if(s.status()==1){drawPublicCards(g,s,mx,my);drawPublicReveals(g,s);motionFrame=CardMotionRenderer.draw(g,cardMotions,net.minecraft.Util.getMillis(),this::motionAnchor,(card,x,y,scale)->drawCard(g,card,x,y,scale,false));drawSkillAnnouncement(g,s);}
            if(s.status()==1 && !reason.isEmpty()){
                g.pose().pushPose();g.pose().translate(0,0,300);
                int errorY=decisionLayout().caption().y()-15;
                g.fill(handLeft()-2,errorY-2,handRight()+2,errorY+10,0xf03b271d);
                text(g,reason,handLeft(),errorY,0xffb18b,handRight()-handLeft());g.pose().popPose();
            }

        }
        drawAutoplayVeil(g);
        if(cardsOpen){
            g.pose().pushPose();g.pose().translate(0,0,250);
            g.fill(18,35,width-18,height-14,0xffaa8047);g.fill(20,37,width-20,height-16,0xff33251b);
            text(g,"公开牌区 · 服务端状态",30,44,0xffead1a2,width-60);
            drawPublicCardPanel(g,s);g.pose().popPose();
        }
        else if(logOpen){
            g.pose().pushPose();g.pose().translate(0,0,250);
            int left=20,right=width-20,rows=Math.max(1,(height-110)/12);
            var logs=s.logLines();int pages=Math.max(1,(logs.size()+rows-1)/rows);logPage=Math.clamp(logPage,0,pages-1);
            int end=Math.max(0,logs.size()-logPage*rows),start=Math.max(0,end-rows);
            g.fill(left-2,36,right+2,height-12,0xffaa8047);g.fill(left,38,right,height-14,0xff33251b);
            g.drawCenteredString(font,"公共日志 · "+(pages-logPage)+" / "+pages,width/2,46,0xffead1a2);
            for(int i=start;i<end;i++){
                String line=logs.get(i);
                if(s.seats().size()==4 && line.startsWith("game finished:"))line="对局结束 · "+outcome(s.outcomeOrdinal());
                if(line.startsWith("2v2 game started:"))line="2v2对局开始，按座位顺序行动";
                text(g,line,left+10,64+(i-start)*12,0xffdfc69e,right-left-20);
            }
            g.pose().popPose();
        }
        else if(zoneModal()){
            g.pose().pushPose();g.pose().translate(0,0,250);drawZoneChoicePanel(g,s);g.pose().popPose();
        }
        else if(draftPrompt()){
            g.pose().pushPose();g.pose().translate(0,0,250);
            drawHarvestPanel(g,s.prompt());
            g.pose().popPose();
        }
        if(socialPaletteVisible())drawSocialPalette(g);
        if(!overlay.isEmpty())drawOverlay(g);
        g.pose().pushPose();if(!overlay.isEmpty() || cardsOpen || logOpen || draftPrompt() || zoneModal() || socialPaletteVisible())g.pose().translate(0,0,!overlay.isEmpty()?360:260);
        super.render(g,mx,my,partial);
        g.pose().popPose();
        giftAnimations.render(g,font,this::giftAnchor,board().unit());
        emoteAnimations.render(g,this::giftAnchor,board().unit());
        drawIdentityPicker(g,mx,my);
        completedRenderCount++;
        if(identityPickerSeat>=0 || autoplayVeilVisible())return;
        if(!overlay.isEmpty() || cardsOpen || logOpen || draftPrompt() || zonePrompt() || s.identityIntro()!=null || s.heroBan()!=null || s.openingHand()!=null)return;
        if(fixtureCount>0)text(g,"布局样本 · "+fixtureCount+"张（仅客户端验图）",handLeft(),height-7-Math.round(112*handScale())-selectedLift()-12,0xffffe5a3,width-150);
        if(portraitFixture)text(g,"布局样本 · 装备／判定／状态（非真实对局效果）",handLeft(),board().hand().y()-15,0xffffe5a3,width-150);
        if(skinStripAvailable() && skinStrip().contains(mx,my))return;
        if(hoverCard!=-1 && self()!=null){
            var hovered=self().hand().stream().filter(c->c.instanceId()==hoverCard).findFirst();
            if(hovered.isPresent() && !handCardBlockedReason(hovered.get()).isEmpty()){paperTip(g,handCardBlockedReason(hovered.get()),mx,my);return;}
        }
        for(var original:s.seats()){
            var seat=fixtureSeat(original);
            var badge=countHits.get(seat.seat());
            if(badge!=null && badge.contains(mx,my)){drawHandPreview(g,seat,mx,my);return;}
        }
        for(var hit:identityMarkHits.values())if(hit.contains(mx,my))return;
        for(var entry:zoneTips.entrySet())if(entry.getKey().contains(mx,my)){paperTip(g,entry.getValue(),mx,my);return;}
        for(var entry:skillTips.entrySet())if(entry.getKey().contains(mx,my)){skillDescriptionTip(g,entry.getValue(),mx,my);return;}
        for(var child:children())if(child instanceof SkillWidget widget && widget.isHoveredOrFocused())return;
        for(var seat:s.seats()){
            var rect=seatHits.get(seat.seat());
            if(rect!=null && rect.contains(mx,my)){
                heroDetails(g,seat,mx,my);
                break;
            }
        }
    }
    private void heroDetails(GuiGraphics g,SeatView seat,int mx,int my){
        var hero=view.snapshot().heroPresentations().stream().filter(h->h.id().equals(seat.heroId())).findFirst().orElse(null);
        if(hero==null){paperTip(g,heroName(seat.heroId()),mx,my);return;}
        var style=FactionStyle.of(seat.kingdomOrdinal());
        String faction=seat.kingdomOrdinal()<0?"":switch(dev.sgs.core.Kingdom.values()[seat.kingdomOrdinal()]){case SHU->"蜀";case WEI->"魏";case WU->"吴";case QUN->"群";};
        int portraitW=100,portraitH=140,textW=222,panelW=portraitW+textW+24;
        var descriptions=hero.skills().stream().map(skill->font.split(Component.literal(skill.description()),textW-16)).toList();
        var biography=hero.description().isBlank()?List.<net.minecraft.util.FormattedCharSequence>of():font.split(Component.literal(hero.description()),textW-16);
        int contentH=40+biography.size()*11+(biography.isEmpty()?0:6);
        for(var lines:descriptions)contentH+=20+lines.size()*11+8;
        int panelH=Math.max(portraitH+16,contentH+8);
        float scale=Math.min(1f,Math.min((width-16f)/panelW,(height-16f)/panelH));
        int actualW=(int)Math.ceil(panelW*scale),actualH=(int)Math.ceil(panelH*scale);
        int left=mx+14+actualW<=width-8?mx+14:mx-actualW-14;
        left=Math.clamp(left,8,Math.max(8,width-actualW-8));
        int top=Math.clamp(my-24,8,Math.max(8,height-actualH-8));
        visiblePaperTip=hero.name()+" · "+hero.skills().stream().map(skill->skill.name()+"："+skill.description()).collect(java.util.stream.Collectors.joining("\n"));
        g.pose().pushPose();g.pose().translate(left,top,600);g.pose().scale(scale,scale,1);
        g.fill(-2,-2,panelW+2,panelH+2,0xffb28b51);g.fill(0,0,panelW,panelH,0xf52d211b);
        HeroChoiceWidget.drawCard(g,hero,8,8,portraitW,portraitH,false,false);
        int tx=portraitW+16;
        g.fill(tx,0,panelW,24,style.main());
        text(g,faction+"  "+hero.name(),tx+8,8,0xffffe3ac,textW-16);
        text(g,"体力 "+seat.hp()+" / "+seat.maxHp()+(seat.chained()?" · 连环":"")+(!seat.alive()?" · 阵亡":""),tx+8,28,0xffd9c5a3,textW-16);
        int row=40;
        for(var line:biography){g.drawString(font,line,tx+8,row,0xffcbb898,false);row+=11;}
        if(!biography.isEmpty())row+=6;
        for(int i=0;i<hero.skills().size();i++){
            var skill=hero.skills().get(i);
            String kind=skill.lordOnly()?"主公技":switch(skill.kind()){case ACTIVE->"主动";case TRIGGERED->"触发";case PASSIVE->"被动";case TOGGLE->"转化";case LIMITED->"限定";};
            g.fill(tx+7,row,tx+textW-7,row+15,0xff524b31);
            text(g,skill.name()+"  · "+kind,tx+11,row+3,0xfff0d998,textW-24);row+=20;
            for(var line:descriptions.get(i)){g.drawString(font,line,tx+8,row,0xffefe1c7,false);row+=11;}
            row+=8;
        }
        g.pose().popPose();
    }
    private ReferenceBattleLayout board(){return ReferenceBattleLayout.fit(width,height,view.snapshot().seats().size());}
    private ReferenceBattleLayout.Box identityBackBox(int slot,int count){
        int columns=Math.max(1,Math.min(4,(count+1)/2)),rows=(count+columns-1)/columns;
        int row=slot/columns,rowCount=Math.min(columns,count-row*columns),left=836-(rowCount*136+(rowCount-1)*28)/2;
        return board().box(left+(slot%columns)*164,252+row*208,136,184);
    }
    private void buildIdentityIntro(){
        var snapshot=view.snapshot();var intro=snapshot.identityIntro();if(intro==null)return;
        if(intro.stage()==0){
            for(int slot=0;slot<snapshot.seats().size();slot++){
                int chosen=slot;var box=identityBackBox(slot,snapshot.seats().size());
                var card=new IdentityBackButton(slot,box,()->{if(identityPending)return;identityPending=true;identityPendingSlot=chosen;
                    MatchClient.send(MatchAction.revealIdentity(MatchClient.nextNonce(),chosen));rebuildWidgets();});
                card.active=!identityPending;buttonsById.put("identity-back:"+slot,addRenderableWidget(card));
            }
        }else if(intro.stage()==1){
            var box=board().box(740,673,192,47);
            var confirm=button("identity-confirm","确定身份",box.x(),box.y(),box.width(),()->{if(identityPending || !identityRevealReady())return;identityPending=true;
                MatchClient.send(MatchAction.confirmIdentity(MatchClient.nextNonce()));rebuildWidgets();});confirm.setHeight(box.height());
            confirm.active=!identityPending && self()!=null && self().identityOrdinal()>=0 && identityRevealReady();
        }
    }
    private final class IdentityBackButton extends SgsButton {
        private final int slot;
        IdentityBackButton(int slot,ReferenceBattleLayout.Box box,Runnable run){
            super(box.x(),box.y(),box.width(),box.height(),Component.literal("抽取身份"),ignored->run.run());this.slot=slot;
        }
        @Override protected void renderWidget(GuiGraphics g,int mx,int my,float partial){
            int x=getX(),y=getY(),w=getWidth(),h=getHeight();
            drawIdentityCard(g,"?",x,y,w,h);
            if(isHoveredOrFocused() || identityPendingSlot==slot)g.renderOutline(x-2,y-2,w+4,h+4,0xffffcf7a);
        }
    }
    private void drawIdentityIntro(GuiGraphics g,MatchSnapshot snapshot){
        var intro=snapshot.identityIntro();if(intro==null)return;
        // The table backs are decorative seats; only the centre backs submit reveal intents.
        var opponents=opponentSeats(snapshot);
        for(int i=0;i<opponents.size();i++)drawIdentitySeatBack(g,board().opponent(i,opponents.size()));
        if(self()!=null)drawIdentitySeatBack(g,board().self());
        g.fill(0,0,width,height,0x77110b07);
        var panel=board().box(350,151,972,629);g.fill(panel.x(),panel.y(),panel.right(),panel.bottom(),0xd72e2017);g.renderOutline(panel.x(),panel.y(),panel.width(),panel.height(),0xffa27d45);
        selectionText(g,intro.stage()==0?"抽取身份":"本场身份",470,181,732,36,0xfff0ce8b);
        if(intro.stage()==0){
            selectionText(g,identityPending?"抽取中…":"点击抽取身份",420,695,832,24,0xffe4c79a);
        }else{
            var me=self();int ordinal=me==null?-1:me.identityOrdinal();
            String role="身份信息同步中",goal="等待服务器返回本人的身份资料";
            if(ordinal>=0 && ordinal<dev.sgs.core.Identity.values().length){
                var identity=dev.sgs.core.Identity.values()[ordinal];
                role=switch(identity){case LORD->"主公";case LOYALIST->"忠臣";case REBEL->"反贼";case RENEGADE->"内奸";};
                goal=switch(identity){case LORD->"消灭所有反贼与内奸，取得胜利";case LOYALIST->"保护主公，协助主公消灭反贼与内奸";case REBEL->"击败主公，取得胜利";case RENEGADE->"消灭其他角色，最后与主公决胜并独自存活";};
            }
            var paper=board().box(498,279,224,294);
            float reveal=identityRevealStarted<0?1:Math.clamp((net.minecraft.Util.getMillis()-identityRevealStarted)/420f,0,1);
            float flip=Math.max(.035f,Math.abs((float)Math.cos(Math.PI*reveal)));
            g.pose().pushPose();g.pose().translate(paper.x()+paper.width()/2f,paper.y(),0);g.pose().scale(flip,1,1);
            drawIdentityCard(g,reveal<.5f || ordinal<0?"?":role.substring(0,1),-paper.width()/2,0,paper.width(),paper.height());
            g.pose().popPose();
            var confirm=buttonsById.get("identity-confirm");
            if(confirm!=null)confirm.active=!identityPending && ordinal>=0 && reveal>=1;
            selectionText(g,"本场身份："+role,778,328,465,36,0xffffd78f);
            selectionText(g,goal,770,405,472,54,0xffecd6ae);
            if(intro.stage()==2)selectionText(g,"身份已确认 · 等待其他玩家",470,679,732,31,0xfff0ce8b);
            else if(identityPending)selectionText(g,"等待服务器确认…",470,730,732,23,0xffd9bd8f);
        }
        if(!reason.isEmpty())selectionText(g,reason,385,724,902,24,0xffffa991);
        var bar=board().box(525,757,623,11);long now=System.currentTimeMillis();
        if(intro.deadlineMillis()!=countdownDeadline){countdownDeadline=intro.deadlineMillis();countdownObservedAt=now;}
        float fraction=Math.clamp((intro.deadlineMillis()-now)/(float)Math.max(1,intro.deadlineMillis()-countdownObservedAt),0,1);
        g.fill(bar.x(),bar.y(),bar.right(),bar.bottom(),0xff4b3523);g.fill(bar.x(),bar.y(),bar.x()+Math.round(bar.width()*fraction),bar.bottom(),0xffd8ab64);
    }
    private void drawIdentitySeatBack(GuiGraphics g,ReferenceBattleLayout.Box box){
        g.fill(box.x(),box.y(),box.right(),box.bottom(),0xff4c3522);g.renderOutline(box.x(),box.y(),box.width(),box.height(),0xff9d783f);
        InkText.fitted(g,font,"将",box.x()+box.width()/2f,box.y()+box.height()*.4f,box.width()*.65f,box.height()*.23f,0xffcda361);
    }
    private void drawIdentityCard(GuiGraphics g,String label,int x,int y,int w,int h){
        BoardSkin.nine(g,BoardSkin.PAPER,x,y,w,h,192,64,8);
        int size=Math.max(8,Math.round(w*.68f));
        IdentitySeal.drawCard(g,font,label,x+(w-size)/2,y+(h-size)/2,size);
    }

    private void decideOpening(boolean replace){
        var opening=view.snapshot().openingHand();
        if(opening==null || opening.stage()!=0 || openingPending || view.snapshot().viewerSeat()<0)return;
        openingPending=true;
        MatchClient.send(replace?MatchAction.replaceOpeningHand(MatchClient.nextNonce()):MatchAction.keepOpeningHand(MatchClient.nextNonce()));
        rebuildWidgets();
    }
    private void buildOpeningControls(){
        var opening=view.snapshot().openingHand();if(opening==null || opening.stage()!=0)return;
        var replaceBox=board().box(602,442,218,48);var keepBox=board().box(852,442,218,48);
        var replace=button("opening-replace","换牌",replaceBox.x(),replaceBox.y(),replaceBox.width(),()->decideOpening(true));replace.setHeight(replaceBox.height());
        var keep=button("opening-keep","取消",keepBox.x(),keepBox.y(),keepBox.width(),()->decideOpening(false));keep.setHeight(keepBox.height());
        replace.active=keep.active=!openingPending;
        replace.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("本局开局免费整手更换一次，牌数不变")));
        keep.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("保留当前初始手牌；超时也会保留")));
    }
    private void drawOpening(GuiGraphics g,MatchSnapshot snapshot,int mx,int my){
        drawSeats(g,snapshot);drawHand(g,mx,my);
        var opening=snapshot.openingHand();var panel=board().box(454,243,764,333);
        g.fill(panel.x(),panel.y(),panel.right(),panel.bottom(),0xec3d2a1b);g.renderOutline(panel.x(),panel.y(),panel.width(),panel.height(),0xffb78e51);
        selectionText(g,opening.stage()==0?"是否更换初始手牌？":"等待其他玩家决定",480,278,712,43,0xfff0cd86);
        selectionText(g,openingPending?"等待服务器确认…":opening.stage()==0?"每位玩家开局可免费整手更换一次，数量不变":"已完成开局选择，回合将在全体决定后开始",480,357,712,28,0xffdfc59f);
        if(opening.stage()!=0)selectionText(g,"真实手牌如下",480,439,712,28,0xffd9bd92);
        long now=System.currentTimeMillis();
        if(opening.deadlineMillis()!=countdownDeadline){countdownDeadline=opening.deadlineMillis();countdownObservedAt=now;}
        float fraction=Math.clamp((opening.deadlineMillis()-now)/(float)Math.max(1,opening.deadlineMillis()-countdownObservedAt),0,1);
        var bar=board().box(514,522,644,10);g.fill(bar.x(),bar.y(),bar.right(),bar.bottom(),0xff65492a);
        g.fill(bar.x(),bar.y(),bar.x()+Math.round(bar.width()*fraction),bar.bottom(),0xffddb265);
        if(!reason.isEmpty())selectionText(g,reason,480,548,712,22,0xffffb395);
    }
    private boolean selectionLordSkill(String id){return view.snapshot().heroPresentations().stream().filter(hero->hero.id().equals(id)).flatMap(hero->hero.skills().stream()).anyMatch(skill->skill.lordOnly());}
    private ReferenceBattleLayout.Box banCard(int i){return board().box(306+(i%5)*220,170+(i/5)*285,180,240);}
    private void buildHeroBan(){
        var s=view.snapshot();var ban=s.heroBan();if(ban.stage()!=0)return;
        String chosen=ban.choices().stream().filter(c->c.seat()==s.viewerSeat()).map(HeroBanChoice::heroId).findFirst().orElse("");
        for(int i=0;i<ban.candidates().size();i++){
            String id=ban.candidates().get(i);var hero=s.heroPresentations().stream().filter(h->h.id().equals(id)).findFirst().orElse(null);if(hero==null)continue;
            var b=banCard(i);var card=new HeroChoiceWidget(hero,b.x(),b.y(),b.width(),b.height(),id.equals(chosen),chosen.isEmpty()&&banSubmitted.isEmpty(),ignored->{
                banSubmitted=id;reason="";MatchClient.send(MatchAction.banHero(MatchClient.nextNonce(),id));rebuildWidgets();
            });buttonsById.put("ban:"+id,addRenderableWidget(card));
        }
    }
    private void drawHeroBan(GuiGraphics g,MatchSnapshot s){
        var ban=s.heroBan();boolean own=ban.choices().stream().anyMatch(c->c.seat()==s.viewerSeat());
        selectionText(g,ban.stage()==1?"本局禁将":own?"已禁将 · 等待其他玩家":"选择一名武将禁用",320,105,1032,35,0xffedc57f);
        if(ban.stage()==1 || s.viewerSeat()<0){
            for(int i=0;i<s.seats().size();i++){
                final int seat=i;var b=board().box(380+(i%4)*240,180+(i/4)*285,174,225);
                var choice=ban.choices().stream().filter(c->c.seat()==seat).findFirst().orElse(null);
                if(choice!=null){
                    HeroRenderer.portrait(g,font,choice.heroId(),b.x(),b.y(),b.width(),b.height(),0xffb59559,false);
                    InkText.fitted(g,font,"禁",b.x()+b.width()/2f,b.y()+b.height()*.42f,b.width()*.5f,b.height()*.25f,0xffffd47f);
                    InkText.fitted(g,font,heroName(choice.heroId()),b.x()+b.width()/2f,b.bottom()-b.height()*.12f,b.width()*.92f,b.height()*.09f,0xffedce94);
                }else dev.sgs.validation.client.art.CardBackRenderer.draw(g,b.x(),b.y(),b.width(),b.height());
                InkText.fitted(g,font,(seat+1)+"号位",b.x()+b.width()/2f,b.bottom()+b.height()*.025f,b.width(),b.height()*.08f,0xffedce94);
            }
        }else selectionText(g,ban.choices().size()+" / "+s.seats().size()+" 已选定",570,760,532,24,0xffead3a6);
        // Pregame cards occupy the normal action lane; keep this timer below both rows.
        g.pose().pushPose();
        g.pose().translate(0,board().box(0,810,1,1).y()-decisionLayout().caption().y(),0);
        drawDecisionTimer(g,ban.stage()==1?"禁将结果":"禁将",ban.deadlineMillis());
        g.pose().popPose();
    }
    private List<String> selectionCandidateIds(MatchSnapshot snapshot){
        var ids=snapshot.heroCandidates();
        // Stable sorting changes only presentation; submission always uses the server's immutable hero ID.
        return ids.size()>4?ids.stream().sorted(Comparator.comparing((String id)->!selectionLordSkill(id))).toList():ids;
    }
    private ReferenceBattleLayout.Box selectionCandidate(int index,int count){
        if(count<=4 && self()!=null && self().teamId()<0 && view.snapshot().seats().size()>=6)return board().box(520+index*254,350,208,286);
        if(count<=4){int columns=Math.max(1,count),start=836-(columns*172+(columns-1)*32)/2;return board().box(start+index*204,505,172,225);}
        int rows=Math.max(2,(count+3)/4),row=index/4,column=index%4;
        int stride=520/rows,cardHeight=Math.min(225,stride-32),cardWidth=Math.min(192,Math.round(cardHeight*172f/225f));
        int cell=254,lastCount=Math.min(4,count-row*4),left=520+(4-lastCount)*cell/2;
        return board().box(left+column*cell+(cell-cardWidth)/2,244+row*stride,cardWidth,cardHeight);
    }
    private boolean selectionWaiting(MatchSnapshot snapshot){
        if(snapshot.status()!=0 || snapshot.identityIntro()!=null || snapshot.heroBan()!=null || snapshot.openingHand()!=null
            || (snapshot.seats().size()!=6 && snapshot.seats().size()!=8))return false;
        return snapshot.seats().stream().anyMatch(seat->seat.self() && seat.teamId()<0 && seat.heroId()!=null && !seat.heroId().isBlank());
    }
    private void drawSelectionWaiting(GuiGraphics g,MatchSnapshot snapshot){
        relationBadges.clear();seatHits.clear();countHits.clear();zoneTips.clear();equipmentHits.clear();skillTips.clear();handHits.clear();
        var opponents=opponentSeats(snapshot);
        for(int i=0;i<opponents.size();i++)drawSelectionWaitingSeat(g,snapshot,opponents.get(i),board().opponent(i,opponents.size()));
        var me=self();if(me!=null)drawSelectionWaitingSeat(g,snapshot,me,board().self());
        selectionText(g,"请等待其他玩家选择武将",430,437,812,43,0xffedce94);
        drawDecisionTimer(g,"已选定："+(me==null?"":heroName(me.heroId())),snapshot.selectionDeadlineMillis());
    }
    private void drawSelectionWaitingSeat(GuiGraphics g,MatchSnapshot snapshot,SeatView seat,ReferenceBattleLayout.Box box){
        // A missing hero is deliberately undisclosed by the server, even if its owner already chose.
        if(seat.heroId()!=null && !seat.heroId().isBlank()){
            drawSeat(g,snapshot,seat,box.x(),box.y(),box.width(),box.height());return;
        }
        dev.sgs.validation.client.art.CardBackRenderer.draw(g,box.x(),box.y(),box.width(),box.height());
        g.renderOutline(box.x(),box.y(),box.width(),box.height(),0xffab844b);
        var label=board().box(0,0,1,24);
        InkText.fitted(g,font,seat.name(),box.x()+box.width()/2f,box.bottom()-label.height()-4,box.width()-12,label.height(),0xffedcf98);
        drawIdentityBadge(g,seat,box.right()-17,box.y()+1);
    }
    private void drawSelection(GuiGraphics g,MatchSnapshot s){
        if(selectionWaiting(s)){drawSelectionWaiting(g,s);return;}
        var me=self();boolean team=me!=null && me.teamId()>=0,expanded=!team && s.seats().size()>=6;
        var friend=team?s.seats().stream().filter(p->!p.self() && p.teamId()==me.teamId()).findFirst().orElse(null):null;
        if(expanded){
            var side=board().box(65,115,382,635);g.fill(side.x(),side.y(),side.right(),side.bottom(),0x9c3c2a1b);g.renderOutline(side.x(),side.y(),side.width(),side.height(),0xffa67d45);
            var lord=s.seats().stream().filter(p->p.seat()==s.lordSeatOrdinal()).findFirst().orElse(null);
            selectionText(g,lord!=null&&lord.heroId()!=null?"主公已选":"主公选将",100,145,312,27,0xffd9b67d);
            String role=me==null || me.identityOrdinal()<0?"身份待确认":switch(dev.sgs.core.Identity.values()[me.identityOrdinal()]){case LORD->"主公";case LOYALIST->"忠臣";case REBEL->"反贼";case RENEGADE->"内奸";};
            drawSelectionPreview(g,lord,138,190,"主公");
            selectionText(g,"你的身份",100,620,312,22,0xffd9b67d);
            selectionText(g,role,100,654,312,40,0xffffd284);
            selectionText(g,me==null?"旁观":(me.seat()+1)+"号位",100,710,312,22,0xffe5c798);
            selectionText(g,s.heroCandidates().isEmpty()?"等待主公选择武将":"选择你的武将",505,270,1050,29,0xffedc57f);
            var ids=selectionCandidateIds(s);
            for(int i=0;i<ids.size();i++){
                var card=selectionCandidate(i,ids.size());String category=selectionLordSkill(ids.get(i))?"含主公技":"其他武将";
                InkText.fitted(g,font,category,card.x()+card.width()/2f,card.bottom()+Math.max(3,Math.round(9*board().unit())),card.width()+8,Math.max(7,Math.round(15*board().unit())),selectionLordSkill(ids.get(i))?0xffffd58b:0xffcbb897);
            }
        }else{
            drawSelectionPreview(g,me,team?420:718,"自己");if(team)drawSelectionPreview(g,friend,1016,"队友");
            selectionText(g,"请选择你的武将",350,460,972,30,0xffedc57f);
        }
        String order=me==null?"等待座位信息":"你是第"+(me.seat()+1)+"个行动";
        String label=s.heroCandidates().isEmpty()?"等待选将":me!=null && me.heroId()!=null?order+" · 已选定，等待其他玩家":order+"，请选择你的武将";
        selectionText(g,label,350,expanded?804:824,972,28,0xffe6b76b);
        if(!reason.isEmpty())selectionText(g,reason,300,expanded?845:856,1072,22,0xffffaa89);
        var bar=board().box(525,895,623,12);long now=System.currentTimeMillis(),deadline=s.selectionDeadlineMillis();
        if(deadline!=countdownDeadline){countdownDeadline=deadline;countdownObservedAt=now;}
        float fraction=Math.clamp((deadline-now)/(float)Math.max(1,deadline-countdownObservedAt),0,1);
        g.fill(bar.x()-1,bar.y()-1,bar.right()+1,bar.bottom()+1,0xff695034);g.fill(bar.x(),bar.y(),bar.right(),bar.bottom(),0xff38291d);
        g.fill(bar.x(),bar.y(),bar.x()+Math.round(bar.width()*fraction),bar.bottom(),0xffdca14e);
    }
    private void drawSelectionSeatMap(GuiGraphics g,MatchSnapshot snapshot){
        var panel=board().box(1410,74,245,163);g.pose().pushPose();g.pose().translate(0,0,235);
        g.fill(panel.x(),panel.y(),panel.right(),panel.bottom(),0xbb3d2a1f);g.renderOutline(panel.x(),panel.y(),panel.width(),panel.height(),0xff80603a);
        var me=self();var others=opponentSeats(snapshot);
        for(var seat:snapshot.seats()){
            var actual=seat.self()?board().self():board().opponent(others.indexOf(seat),others.size());
            float nx=(actual.x()+actual.width()/2f-board().offsetX())/(1672*board().unit()),ny=(actual.y()+actual.height()/2f-board().offsetY())/(941*board().unit());
            int size=Math.max(12,Math.round(34*board().unit())),x=panel.x()+4+Math.round(nx*(panel.width()-size-8)),y=panel.y()+3+Math.round(ny*(panel.height()-size-14));
            boolean friend=me!=null && me.teamId()>=0 && me.teamId()==seat.teamId();String label=seat.self()?"我":friend?"友":me!=null && me.teamId()>=0?"敌":"?";
            g.fill(x,y,x+size,y+size,friend||seat.self()?0xff476b3c:0xff713e32);
            // Enemy hero IDs stay hidden even if a malicious/misconfigured producer supplies one.
            if((seat.self() || friend || seat.seat()==snapshot.lordSeatOrdinal()) && seat.heroId()!=null && !seat.heroId().isBlank())HeroRenderer.portraitExact(g,font,seat.heroId(),x,y,size,size,0xffa28a54,true,HeroSkins.syncedTexture(seat.heroId(),view.skins().getOrDefault(seat.seat(),"classic"),HeroRenderer.classic(seat.heroId())));
            else InkText.fitted(g,font,label,x+size/2f,y+1,size-2,size-2,0xffebd0a2);
            if(seat.self())g.renderOutline(x-2,y-2,size+4,size+4,0xffa8e37d);
            text(g,Integer.toString(seat.seat()+1),x+size/2-2,y+size+1,0xffdfbd89,size);
            zoneTips.put(new Rect(x,y,size,size+10),(seat.seat()+1)+"号位 · "+(seat.self()?"自己":friend?"队友":"对手"));
        }
        g.pose().popPose();
    }
    private void drawSeatClock(GuiGraphics g,MatchSnapshot s,SeatView seat,int x,int y,int w,String fallback){
        int actor=s.prompt()!=null?s.prompt().askerSeat():s.currentSeat();
        long deadline=s.counterspellWindow()!=null?s.counterspellWindow().deadlineMillis():s.prompt()!=null?s.prompt().deadlineMillis():s.phaseDeadlineMillis(),now=System.currentTimeMillis();
        boolean waiting=seat.alive() && (s.counterspellWindow()!=null || seat.seat()==actor);
        if(s.status()!=1 || !waiting || deadline<=now){
            int labelH=Math.max(10,Math.min(14,Math.round(22*board().unit())));
            InkText.fitted(g,font,fallback,x+w/2f,y,w-3,labelH,0xffe8cf9b);return;
        }
        if(!Objects.equals(seatDeadlines.get(actor),deadline)){seatDeadlines.put(actor,deadline);seatObserved.put(actor,now);}
        seatClockRendered=true;
        float fraction=Math.clamp((deadline-now)/(float)Math.max(1,deadline-seatObserved.getOrDefault(actor,now)),0,1);
        int h=Math.max((int)Math.ceil(19/InkText.pixels()),Math.round(18*board().unit()));
        float edge=1/InkText.pixels(),barHeight=3/InkText.pixels();
        InkText.label(g,font,fallback,x+w/2f,y,w-3,h-barHeight-edge*2,0xffffe6a6);
        g.pose().pushPose();g.pose().translate(InkText.snap(x),InkText.snap(y+h-barHeight),0);g.pose().scale(edge,edge,1);
        int physicalWidth=Math.round(w/edge),filled=Math.round(Math.max(0,physicalWidth-2)*fraction);
        g.fill(0,0,physicalWidth,4,0xff6b512f);g.fill(1,1,physicalWidth-1,3,0xff34291c);
        if(filled>0){g.fill(1,1,1+filled,3,0xffd5a038);g.fill(1,1,1+filled,2,0xffffdc83);}
        g.pose().popPose();
    }
    private void selectionText(GuiGraphics g,String value,int x,int y,int w,int h,int color){var box=board().box(x,y,w,h);InkText.fitted(g,font,value,box.x()+box.width()/2f,box.y(),box.width(),box.height(),color);}
    private void drawSelectionPreview(GuiGraphics g,SeatView seat,int referenceX,String label){drawSelectionPreview(g,seat,referenceX,82,label);}
    private void drawSelectionPreview(GuiGraphics g,SeatView seat,int referenceX,int referenceY,String label){
        var box=board().box(referenceX,referenceY,236,310);
        var hero=seat==null || seat.heroId()==null?null:view.snapshot().heroPresentations().stream().filter(h->h.id().equals(seat.heroId())).findFirst().orElse(null);
        if(hero!=null){
            HeroChoiceWidget.drawCard(g,hero,box.x(),box.y(),box.width(),box.height(),true,false);
            HealthRenderer.vertical(g,box.right()-11,box.y()+24,seat.hp(),seat.maxHp(),box.height()-50);
        }else{
            g.fill(box.x(),box.y(),box.right(),box.bottom(),0xb7453424);g.renderOutline(box.x(),box.y(),box.width(),box.height(),0xffa47c45);
            InkText.fitted(g,font,"等待选将",box.x()+box.width()/2f,box.y()+box.height()/2f-6,box.width()-10,14,0xffd5b880);
        }
        if(seat!=null)drawIdentityBadge(g,seat,box.right()-18,box.y()+3);
        var name=board().box(referenceX-40,referenceY+328,316,32);
        InkText.fitted(g,font,label,name.x()+name.width()/2f,name.y(),name.width(),Math.max(10,name.height()*.65f),0xffecd4a3);
        if(seat!=null)g.drawCenteredString(font,seat.name()+(seat.control().automatic()?(seat.control().source().equals("DISCONNECT")?" · 离线托管":" · 托管"):!seat.online()?" · 离线":""),name.x()+name.width()/2,name.bottom()+1,0xffdfcaa4);
    }
    private float handScale(){return board().hand().height()/112f;}
    private int selectedLift(){return Math.max(4,Math.round(24*board().unit()));}
    private DecisionOverlayLayout.Layout decisionLayout(){
        var d=board().decision();var me=self();int badge=me==null?15:Math.max(15,font.width(Integer.toString(me.handCount()))+6);
        var r=d.skillRow();int overlap=Math.max(0,board().self().x()+badge-r.x());
        return new DecisionOverlayLayout.Layout(d.caption(),d.timer(),d.confirm(),d.cancel(),
            new DecisionOverlayLayout.Rect(r.x()+overlap,r.y(),Math.max(4,r.width()-overlap),r.height()));
    }
    private int controlY(){return decisionLayout().confirm().y();}
    private int heroWidth(){return board().self().width();}
    private int heroHeight(){return board().self().height();}
    private int selfX(){return board().self().x();}
    private int selfY(){return board().self().y();}
    private int skillLeft(){return decisionLayout().skillRow().x();}
    private int skillWidth(){return decisionLayout().skillRow().width();}
    private int skillY(){return decisionLayout().skillRow().y();}
    private String counterspellWaitingLabel(MatchSnapshot snapshot){
        var window=snapshot.counterspellWindow();if(window==null)return "等待无懈响应";
        var subject=snapshot.seats().stream().filter(seat->seat.seat()==window.subjectSeat()).findFirst().orElse(null);
        return "等待无懈响应 · 链深度 "+window.chainDepth()+(subject==null?"":" · 目标 "+subject.name());
    }
    private void drawDecisionTimer(GuiGraphics g,String caption,long deadline){
        var layout=decisionLayout();var bar=layout.timer();var title=layout.caption();
        int left=bar.x(),right=bar.right(),top=bar.y();
        long now=System.currentTimeMillis();
        if(deadline!=countdownDeadline){countdownDeadline=deadline;countdownObservedAt=now;}
        if(!caption.isEmpty())g.fill(title.x()-2,title.y()-1,title.right()+2,title.bottom()+1,0xa03b2d20);
        float textScale=Math.max(.55f,Math.min(1.8f,board().unit()*2.35f));
        g.pose().pushPose();g.pose().translate(title.x()+title.width()/2f,title.y(),0);g.pose().scale(textScale,textScale,1);
        InkText.drawCentered(g,font,caption,0,0,0xfff1d8a4);g.pose().popPose();
        if(deadline<=0)return;
        long remaining=Math.max(0,deadline-now),span=Math.max(1,deadline-countdownObservedAt);
        int pixels=Math.clamp((int)((right-left-2)*Math.min(1d,remaining/(double)span)),0,Math.max(0,right-left-2));
        g.fill(left-1,top-1,right+1,bar.bottom()+1,0xff745332);
        g.fill(left,top,right,bar.bottom(),0xff35291f);
        if(view.snapshot().status()==1 && view.snapshot().phaseOrdinal()==Phase.PLAY.ordinal()){
            int labelW=Math.max(23,Math.round(84*board().unit()));
            g.fill(left-labelW-5,top-1,left-4,bar.bottom()+1,0xd83e632e);
            InkText.fitted(g,font,"出牌阶段",left-labelW/2f-5,top-1,labelW,Math.max(8,bar.height()+2),0xffdbe9b7);
        }
        if(pixels>0){g.fill(left+1,top,left+1+pixels,bar.bottom(),remaining<3000?0xffcc6c42:0xffe9af56);g.hLine(left+1,left+pixels,top,0xffffda83);}
        text(g,(remaining+999)/1000+"秒",right-32,title.y()+1,0xfff2ce83,32);
    }
    private void drawSkillAnnouncement(GuiGraphics g,MatchSnapshot s){
        announcedPortraitSeat=-1;
        long now=System.currentTimeMillis();
        if(equipmentEffectsFixture && activeNotice!=null && equipmentFixtureElapsed>=0)noticeStarted=now-equipmentFixtureElapsed;
        if(activeNotice!=null && now-noticeStarted>(EquipmentActivationRenderer.supports(activeNotice.skillId())?EquipmentActivationRenderer.DURATION:2600))activeNotice=null;
        if(activeNotice==null && !skillNotices.isEmpty()){activeNotice=skillNotices.removeFirst();noticeStarted=now;}
        if(activeNotice==null)return;
        var actor=s.seats().stream().filter(p->p.seat()==activeNotice.seat()).findFirst().orElse(null);
        if(actor==null)return;
        var notice=board().notice();int w=notice.width(),x=notice.x(),y=notice.y();
        boolean equipment=EquipmentActivationRenderer.supports(activeNotice.skillId());
        var portrait=seatHits.get(actor.seat());
        if(portrait!=null && now-noticeStarted<2200){
            announcedPortraitSeat=actor.seat();
            g.pose().pushPose();g.pose().translate(0,0,200);
            if(equipment)EquipmentActivationRenderer.draw(g,font,activeNotice.skillId(),
                PortraitOverlayLayout.activation(portrait.x(),portrait.y(),portrait.w(),portrait.h()),now-noticeStarted);
            else SkillActivationRenderer.draw(g,font,skillName(activeNotice.skillId()),
                PortraitOverlayLayout.activation(portrait.x(),portrait.y(),portrait.w(),portrait.h()),now-noticeStarted,
                FactionStyle.of(actor.kingdomOrdinal()).trim());
            g.pose().popPose();
        }
        if(equipment)return;
        String label=heroName(actor.heroId())+" 发动了技能 "+skillName(activeNotice.skillId());
        g.pose().pushPose();g.pose().translate(0,0,200);
        g.fill(x,y,x+w,y+notice.height(),0x75392d21);
        float scale=Math.max(.6f,board().unit()*2.6f);g.pose().translate(x+w/2f,y+Math.max(0,(notice.height()-10*scale)/2),0);g.pose().scale(scale,scale,1);InkText.drawCentered(g,font,label,0,0,0xffffd77b);g.pose().popPose();
    }
    private String skillDescription(String id){return view.snapshot().heroPresentations().stream().flatMap(h->h.skills().stream()).filter(s->s.id().equals(id)).map(dev.sgs.core.addon.SkillPresentation::description).findFirst().orElse(switch(id){
        case "rende"->"出牌阶段，将任意数量手牌交给其他角色；本回合累计交出至少两张时回复1点体力。";
        case "jijiang"->"主公技，需要使用或打出杀时，可请求其他蜀角色依行动顺序提供杀。";
        case "jianxiong"->"受到伤害且存活后，可获得造成伤害且仍在处理区的全部实体牌。";
        case "hujia"->"主公技，需要闪时，可请求其他魏角色依行动顺序提供闪。";
        case "longdan"->"将杀（含火杀、雷杀）当闪，或将闪当普通杀使用或打出。";
        case "wusheng"->"将一张红色手牌或装备当普通杀使用或打出，仍须满足距离与次数。";
        case "kurou"->"出牌阶段，失去1点体力，然后摸两张牌。若濒死，先结算救援，存活才摸牌。";
        case "jizhi"->"使用非延时锦囊时，可以摸一张牌；重铸不属于使用。";
        case "qicai"->"锁定技，使用锦囊无距离限制，仍须满足其他目标条件。";
        case "qinglong"->"杀被闪抵消后，可以立即对同一目标使用新的杀；可继续追杀，取消则结束。";
        case "guanshi"->"杀被闪抵消后，可以弃置两张牌（不能包括贯石斧本身），令原杀仍生效；两张一起支付。";
        case "hanbing"->"杀将造成伤害时，若目标有手牌或装备，可防止伤害，依次弃置其两张牌。";
        case "cixiong"->"使用杀指定异性武将后，可令其弃一张手牌或让你摸一张；由目标自行选择。";
        case "qilin_gong"->"使用杀对目标角色造成伤害时，可以弃置其装备区里的一张坐骑；由你选择。";
        case "paoxiao"->"锁定技，出牌阶段使用杀无次数限制。";
        default->"满足技能条件时由服务端裁决。";});}
    private void skillDescriptionTip(GuiGraphics g,String id,int mx,int my){
        visiblePaperTip=skillName(id)+"："+skillDescription(id);
        float scale=Math.max(1,14/(InkText.EM*InkText.pixels()));
        int w=Math.min(220,(int)((width-16)/scale));var lines=font.split(InkText.component(skillDescription(id)),w-20);
        int h=32+lines.size()*13,x=Math.clamp(mx-Math.round(w*scale)/2,8,Math.max(8,width-Math.round(w*scale)-8)),y=Math.clamp(my-Math.round(h*scale)-7,8,Math.max(8,height-Math.round(h*scale)-8));
        g.pose().pushPose();g.pose().translate(InkText.snap(x),InkText.snap(y),450);g.pose().scale(scale,scale,1);
        g.fill(2,3,w+2,h+3,0x7020150d);g.fill(-1,-1,w+1,h+1,0xff967344);g.fill(0,0,w,h,0xffefe0c2);
        g.fill(1,1,w-1,2,0xfffff0d7);InkText.draw(g,font,skillName(id),10,8,0xff50321e);
        g.fill(10,23,w-10,24,0xffc9b28b);int row=29;
        for(var line:lines){g.drawString(font,line,10,row,0xff49311f,false);row+=13;}g.pose().popPose();
    }
    private int handLeft(){return board().hand().x();}
    private int handRight(){return board().hand().right();}
    private HandFanLayout handLayout(int count){return HandFanLayout.fit(count,handLeft(),Math.max(50,handRight()-handLeft()),Math.round(80*handScale()),handOffset);}
    private void paperTip(GuiGraphics g,String message,int mx,int my){
        visiblePaperTip=message;
        float scale=Math.max(1,14/(InkText.EM*InkText.pixels()));
        int w=Math.max(24,Math.min(Math.min(260,(int)((width-16)/scale)),InkText.width(font,message)+20));
        var lines=font.split(InkText.component(message),w-20);int h=14+lines.size()*13;
        int x=Math.clamp(mx+8,8,Math.max(8,width-Math.round(w*scale)-8)),y=Math.clamp(my-Math.round(h*scale)-6,8,Math.max(8,height-Math.round(h*scale)-8));
        g.pose().pushPose();g.pose().translate(InkText.snap(x),InkText.snap(y),450);g.pose().scale(scale,scale,1);
        g.fill(2,3,w+2,h+3,0x7020150d);g.fill(-1,-1,w+1,h+1,0xff967344);g.fill(0,0,w,h,0xffefe0c2);
        g.fill(1,1,w-1,2,0xfffff0d7);int row=7;
        for(var line:lines){g.drawString(font,line,10,row,0xff49311f,false);row+=13;}g.pose().popPose();
    }
    /** Real server processing cards, falling back to the most recent public use/response. */
    private void drawPublicReveals(GuiGraphics g,MatchSnapshot s){
        long now=net.minecraft.Util.getMillis();drawnPublicReveals=List.of();
        revealTimeline.accept(view.matchId(),view.connectionEpoch(),s.publicCardReveals(),now);
        var visible=revealTimeline.visible(now);if(visible.isEmpty())return;
        var event=visible.get();var area=board().box(526,384,622,212);var cards=event.cards();
        float scale=Math.min(1.5f*board().unit(),Math.max(1,area.height()-16)/112f);
        int cw=Math.max(1,Math.round(80*scale)),ch=Math.max(1,Math.round(112*scale));
        int step=Math.max(1,Math.min(cw+4,Math.max(1,area.width()-cw)/Math.max(1,cards.size()-1)));
        int x=area.x()+(area.width()-cw-step*(cards.size()-1))/2,y=area.y()+Math.max(10,Math.round(26*board().unit()));
        String label=heroName(s.seats().get(event.ownerSeat()).heroId())+" · 展示";
        text(g,label,x,y-Math.max(10,Math.round(22*board().unit())),0xffebd4a3,Math.max(cw,80));
        for(int i=0;i<cards.size();i++)drawCard(g,cards.get(i),x+i*step,y,scale,false);
        drawnPublicReveals=cards.stream().map(CardView::instanceId).toList();
    }

    private ReferenceBattleLayout.Box motionAnchor(dev.sgs.validation.client.layout.CardMotionTimeline.Flight flight,boolean destination){
        var event=flight.event();String kind=destination?event.toKind():event.fromKind();int seat=destination?event.toSeat():event.fromSeat();
        long id=event.faces().isEmpty()?-1:event.faces().get(flight.index()).instanceId();
        if(kind.equals("HAND") && seat==view.snapshot().viewerSeat()){
            Rect hand=destination?handHits.get(id):previousHandAnchors.get(id);
            if(hand!=null)return new ReferenceBattleLayout.Box(hand.x(),hand.y(),hand.w(),hand.h());
            var h=board().hand();int w=Math.round(80*handScale());return new ReferenceBattleLayout.Box(h.right()-w,h.y(),w,Math.round(w*1.4f));
        }
        int count=Math.min(8,event.count()),offset=(flight.index()*24-(count-1)*12);
        if(seat>=0 && seatHits.containsKey(seat)){
            var r=seatHits.get(seat);int w=Math.max(12,Math.round(62*board().unit()));
            int x=r.x()+r.w()/2-w/2+Math.round(offset*board().unit());
            int y=r.y()+r.h()/2-Math.round(w*.7f);
            if(kind.equals("EQUIPMENT"))y=r.y()+r.h()-Math.round(w*1.4f);
            if(kind.equals("JUDGMENT"))y=r.y();
            return new ReferenceBattleLayout.Box(x,y,w,Math.round(w*1.4f));
        }
        return switch(kind){
            case "DRAW"->board().box(780+offset,310,80,112);
            case "DISCARD"->board().box(920+offset,380,80,112);
            default->board().box(740+offset,390,100,140);
        };
    }
    private void drawPublicCards(GuiGraphics g,MatchSnapshot s,int mx,int my){
        var cards=s.processing();
        if(cards.isEmpty()){
            var latest=s.publicMoves().reversed().stream()
                .filter(m->Set.of("USE","RESPONSE","RECAST").contains(m.reason())).findFirst();
            if(latest.isEmpty())return;
            cards=latest.get().cards();
        }
        int left=handLeft(),right=handRight(),top=27+heroHeight()+30,bottom=controlY()-34;
        int available=bottom-top;
        // The persistent header's paper "牌区" control opens the full area at compact GUI scales.
        if(available<24)return;
        float scale=Math.min(.62f,(available-13)/112f);int cw=Math.round(80*scale),ch=Math.round(112*scale);
        int step=Math.max(4,Math.min(cw+4,(right-left-cw)/Math.max(1,cards.size()-1)));
        g.enableScissor(left,top+11,right,bottom);
        for(int i=0;i<cards.size();i++)drawCard(g,cards.get(i),left+i*step,top+12,scale,false);
        g.disableScissor();
    }
    private void drawPublicCardPanel(GuiGraphics g,MatchSnapshot s){
        var cards=s.processing();String label="处理区 · 结算中";
        if(cards.isEmpty()){
            var last=s.publicMoves().reversed().stream().filter(m->Set.of("USE","RESPONSE","RECAST").contains(m.reason())).findFirst();
            if(last.isPresent()){cards=last.get().cards();label="最近出牌 · 座位 "+(last.get().actorSeat()+1);}
            else label="处理区为空 · 尚无公开出牌";
        }
        text(g,label,30,59,0xffd6b985,width-60);
        int maxHeight=Math.max(25,(height-128)*2/3);float scale=Math.min(.82f,maxHeight/112f);
        int cw=Math.round(80*scale),step=Math.min(cw+5,Math.max(5,(width-60-cw)/Math.max(1,cards.size()-1)));
        g.enableScissor(28,72,width-28,height-48);
        for(int i=0;i<cards.size();i++)drawCard(g,cards.get(i),30+i*step,73,scale,false);
        int y=76+Math.round(112*scale);var history=s.publicMoves().reversed();
        for(var move:history){
            if(y+10>=height-47)break;
            String names=move.cards().stream().map(this::cardName).collect(java.util.stream.Collectors.joining("、"));
            String action=switch(move.reason()){
                case "USE"->"使用";case "RESPONSE"->"打出";case "FINISH"->"结算结束";case "RECAST"->"重铸";
                case "DEATH","DISCARD","EQUIPMENT_REPLACED"->"弃置";case "EFFECT_GAIN"->"获得";
                case "JUDGMENT_REVEAL"->"亮出判定";case "JUDGMENT_REPLACE"->"改判";case "REVEAL"->"亮出";
                case "EQUIP"->"装备";case "DELAYED_USE","DELAY_PASS"->"置入判定区";default->"公开移动";};
            text(g,"#"+move.sequence()+" · "+(move.actorSeat()<0?"牌堆":"座位 "+(move.actorSeat()+1))+" · "+action+" · "+names,30,y,0xffdcc49b,width-60);y+=11;
        }
        g.disableScissor();
    }
    private void drawHandPreview(GuiGraphics g,SeatView seat,int mx,int my){
        previewedSeat=seat.seat();
        if(!seat.handVisible()){
            var me=self();boolean teammate=me!=null && !seat.self() && me.teamId()>=0 && seat.teamId()==me.teamId();
            paperTip(g,"手牌 "+seat.handCount()+"张"+(teammate?" · 队友未开启手牌共享":""),mx,my);return;
        }
        int columns=Math.max(1,Math.min(8,(width-36)/44)),rows=Math.max(1,(seat.hand().size()+columns-1)/columns);
        int w=Math.min(columns,Math.max(1,seat.hand().size()))*44+16,h=Math.min(height-38,rows*63+30);
        int x=Math.clamp(mx-w/2,8,width-w-8),y=Math.clamp(my-h-8,25,height-h-8);
        g.pose().pushPose();g.pose().translate(0,0,450);
        g.fill(x-2,y-2,x+w+2,y+h+2,0xffad8449);g.fill(x,y,x+w,y+h,0xff3d2b1e);
        text(g,(seat.self()?"我的手牌":"队友手牌 · 已授权")+" · "+seat.handCount(),x+8,y+7,0xffebd4a3,w-16);
        if(seat.hand().isEmpty())text(g,"没有手牌",x+8,y+22,0xffebd4a3,w-16);
        g.enableScissor(x,y+20,x+w,y+h);
        for(int i=0;i<seat.hand().size();i++)drawCard(g,seat.hand().get(i),x+8+i%columns*44,y+22+i/columns*63,.52f,false);
        g.disableScissor();g.pose().popPose();
    }
    private String promptName(int ordinal){return switch(PromptType.values()[ordinal]){case DODGE_SHA->"打出闪";case HUJIA_SHAN->"护驾 · 为主公提供闪";case JIJIANG_SHA->"激将 · 为主公提供杀";case QINGLONG_SHA->"青龙刀 · 对原目标使用新的杀";case SKILL_INVOKE->"选择技能";case RESCUE_TAO->"濒死救援";case WUXIE_COUNTER->"无懈可击";case DUEL_SHA,RESPOND_SHA_NANMAN,JIEDAO_RESPONSE->"打出杀";case WUGU_PICK->"五谷丰登选牌";case HUOGONG_REVEAL->"火攻 · 展示一张手牌";case HUOGONG_PAY->"火攻 · 弃置同花色手牌";case FANJIAN_SUIT->"反间 · 请选择一种花色";case GUI_CAI_REPLACE->"鬼才 · 选择自己的手牌替换判定";case FANKUI_TAKE->"反馈 · 抽取手牌或选择装备";case YIJI_GIVE->"遗计 · 选择一张牌与接收角色";case TUXI_TARGET->"突袭 · 选择有手牌的目标";case GANGLIE_DISCARD->"刚烈 · 依次选两张手牌，否则受伤";case LIJIAN_TARGET->"离间 · 选择第二名男性角色";case GUANXING_ORDER->"观星 · 逐张选择牌堆顶或底";case LIULI_TARGET->"流离 · 选择弃牌与转移目标";case CARD_ZONE_SELECT->"选择目标的手牌、装备或判定牌";case WEAPON_DISCARD->"双股剑 · 弃一张手牌或让对方摸一张";case WEAPON_COST_FIRST->"贯石斧 · 暂选第一张代价（尚未弃置）";case WEAPON_COST_SECOND->"贯石斧 · 选择第二张后一起弃置";case EXPANSION_CHOICE->"技能选牌与选将";case EXPANSION_OPTION->"技能选项";};}
    private String outcome(int ordinal){var values=dev.sgs.core.engine.GameOutcome.values();return ordinal>=0 && ordinal<values.length?switch(values[ordinal]){case ONGOING->"等待结算";case LORD_WIN->"主公与忠臣获胜";case REBELS_WIN->"反贼获胜";case RENEGADE_WIN->"内奸获胜";case TEAM_A_WIN,TEAM_B_WIN->{var me=self();yield me==null || me.teamId()<0?"对局结束":me.teamId()==(values[ordinal]==dev.sgs.core.engine.GameOutcome.TEAM_A_WIN?0:1)?"胜利":"失利";}case DRAW->"平局";}:"等待结算";}
    private List<SeatView> opponentSeats(MatchSnapshot snapshot){
        if(snapshot.seats().size()==8){
            int anchor=snapshot.viewerSeat()>=0?snapshot.viewerSeat():0;
            return snapshot.seats().stream().filter(seat->seat.seat()!=anchor)
                .sorted(Comparator.comparingInt(seat->ReferenceBattleLayout.eightPlayerOpponentIndex(seat.seat(),anchor))).toList();
        }
        var opponents=snapshot.seats().stream().filter(seat->!seat.self());
        if(snapshot.seats().size()==4 && snapshot.viewerSeat()>=0)
            opponents=opponents.sorted(Comparator.comparingInt(seat->ReferenceBattleLayout.fourPlayerOpponentIndex(seat.seat(),snapshot.viewerSeat())));
        return opponents.toList();
    }
    private void drawSeats(GuiGraphics g,MatchSnapshot s){seatClockRendered=false;
        renderedChainRelease=null;
        relationBadges.clear();
        seatHits.clear();countHits.clear();zoneTips.clear();equipmentHits.clear();skillTips.clear();
        var others=opponentSeats(s);
        for(int i=0;i<others.size();i++){
            var r=board().opponent(i,others.size());drawSeat(g,s,fixtureSeat(others.get(i)),r.x(),r.y(),r.width(),r.height());
        }
        // Spectators anchor seat zero at the bottom without making it their private/self seat.
        var me=self();if(me==null && s.seats().size()==8)me=s.seats().stream().filter(seat->seat.seat()==0).findFirst().orElse(null);
        var r=board().self();if(me!=null)drawSeat(g,s,me,r.x(),r.y(),r.width(),r.height());
    }
    private void drawDamageFeedback(GuiGraphics g,dev.sgs.validation.client.layout.DamageFeedback.Hit hit,long age,int x,int y,int w,int h){
        float fade=Math.min(1f,(1200-age)/400f);
        int alpha=Math.max(0,Math.min(255,Math.round(255*fade)));
        if(alpha<4)return;
        g.pose().pushPose();g.pose().translate(0,0,250);
        int thickness=Math.max(2,Math.round(4*board().unit()));
        int color=(alpha<<24)|0xff4545;
        for(int i=0;i<thickness;i++)g.renderOutline(x-i,y-i,w+2*i,h+2*i,color);
        String label="−"+hit.amount();
        float scale=Math.max(1.4f,w/38f)*(1+.18f*Math.max(0,1-age/180f));
        float rise=Math.min(1,age/1000f)*h*.20f;
        g.pose().translate(x+w*.53f,y+h*.45f-rise,0);g.pose().scale(scale,scale,1);
        int left=-font.width(label)/2;
        g.drawString(font,label,left-1,0,(alpha<<24)|0x380608,false);
        g.drawString(font,label,left+1,0,(alpha<<24)|0x380608,false);
        g.drawString(font,label,left,-1,(alpha<<24)|0x380608,false);
        g.drawString(font,label,left,1,(alpha<<24)|0x380608,false);
        g.drawString(font,label,left,0,(alpha<<24)|0xffdddd,false);
        g.pose().popPose();
    }
    private void drawSeat(GuiGraphics g,MatchSnapshot s,SeatView seat,int x,int y,int w,int h){
        seatHits.put(seat.seat(),new Rect(x,y,w,h));
        String blocked=targetHint(seat.seat());
        boolean unavailable=seat.alive() && blocked!=null && !blocked.isEmpty();
        // Unavailable is temporary: retain color. Only confirmed death desaturates the portrait.
        boolean colored=seat.alive();
        int border=(fangtianSelection()?attackTargets.contains(seat.seat()):seat.seat()==target || seat.seat()==secondary)?0xffffcd68:seat.seat()==s.currentSeat()?0xffa9ce84:0xffb38a48;
        var style=FactionStyle.of(seat.kingdomOrdinal());
        var classic=HeroRenderer.classic(seat.heroId());
        var presentation=s.heroPresentations().stream().filter(p->p.id().equals(seat.heroId())).findFirst().orElse(null);
        if(presentation!=null && !presentation.portrait().isEmpty()){
            var texture=net.minecraft.resources.ResourceLocation.parse(presentation.portrait());
            if(minecraft.getResourceManager().getResource(texture).isPresent())classic=texture;
        }
        var damageHit=damageFeedback.hit(seat.seat(),net.minecraft.Util.getMillis());
        long damageAge=damageHit==null?0:net.minecraft.Util.getMillis()-damageHit.started();
        g.pose().pushPose();
        if(damageHit!=null && damageAge<320){
            float shake=(float)(Math.sin(damageAge*.075)*(1-damageAge/320f)*Math.max(2,w*.035f));
            g.pose().translate(shake,0,0);
        }
        HeroRenderer.portraitExact(g,font,seat.heroId(),x,y,w,h,colored?border:HeroRenderer.grayColor(border),colored,HeroSkins.syncedTexture(seat.heroId(),view.skins().getOrDefault(seat.seat(),"classic"),classic));
        g.pose().popPose();
        if(damageHit!=null){
            int flash=Math.round(110*Math.max(0,1-damageAge/550f));
            if(flash>0)g.fill(x,y,x+w,y+h,(flash<<24)|0xe52c35);
        }
        var frame=dev.sgs.validation.ProbeMod.id("textures/gui/panels/hero_frame_v1.png");
        BoardSkin.nine(g,colored?frame:HeroRenderer.grayscale(frame),x-3,y-3,w+6,h+6,320,448,64);
        String name=seat.self() && skinNameHovered?"换肤":heroName(seat.heroId());
        if(seat.self())renderedSelfName=name;
        int nameW=PortraitOverlayLayout.nameRailWidth(w),nameStep=Math.max(8,Math.min(nameW-2,h/9));
        var nameRail=PortraitOverlayLayout.nameRail(x,y,w,h);
        g.fill(nameRail.x(),nameRail.y(),nameRail.right(),nameRail.bottom(),colored?style.main():HeroRenderer.grayColor(style.main()));
        float nameTop=nameRail.y()+(nameRail.height()-name.length()*nameStep)/2f;
        for(int i=0;i<name.length();i++)InkText.fitted(g,font,name.substring(i,i+1),nameRail.x()+nameRail.width()/2f,nameTop+i*nameStep,nameW-2,nameStep,0xffffebcb);
        String faction=seat.kingdomOrdinal()<0?"?":switch(dev.sgs.core.Kingdom.values()[seat.kingdomOrdinal()]){case SHU->"蜀";case WEI->"魏";case WU->"吴";case QUN->"群";};
        // Reference video: faction upper-left, circular relation/identity upper-right.
        InkText.fitted(g,font,faction,x+nameW/2f,y+3,nameW-2,Math.max(10,nameW),0xffffebcb);
        var identity=PortraitOverlayLayout.identity(x,y,w,h);
        drawIdentityBadge(g,seat,identity.x(),identity.y(),identity.width());
        int statusIndex=0,limitedIndex=0;
        for(var status:seat.statuses()){
            if(status.id().startsWith("limited:")){
                var box=PortraitOverlayLayout.limited(x,y,w,h,limitedIndex++);
                boolean ready=seat.alive() && status.label().equals("READY") && s.counterspellWindow()==null
                    && (s.prompt()==null || s.prompt().askerSeat()==seat.seat()
                        && status.id().equals("limited:"+s.prompt().skillId()));
                drawLimitedSeal(g,box.x(),box.y(),box.width(),ready);
                skillTips.put(new Rect(box.x(),box.y(),box.width(),box.height()),status.id().substring("limited:".length()));
                continue;
            }
            var box=PortraitOverlayLayout.status(x,y,w,h,statusIndex++);
            g.fill(box.x(),box.y(),box.right(),box.bottom(),0xb052342a);
            InkText.fitted(g,font,status.label(),box.x()+box.width()/2f,box.y(),box.width()-2,box.height(),0xffffefd8);
            zoneTips.put(new Rect(box.x(),box.y(),box.width(),box.height()),status.label()+" · "+status.description());
        }
        HealthRenderer.verticalBottom(g,x+w-2,y+h-3,seat.hp(),seat.maxHp(),Math.round(h*.55f),board().unit());
        int badgeW=Math.max(15,font.width(Integer.toString(seat.handCount()))+6);
        Rect badge=new Rect(x-2,y+h-16,badgeW,17);countHits.put(seat.seat(),badge);
        g.fill(badge.x()-1,badge.y()-1,badge.x()+badge.w()+1,badge.y()+badge.h()+1,0xffbd9553);
        g.fill(badge.x(),badge.y(),badge.x()+badge.w(),badge.y()+badge.h(),0xff4e4635);
        g.drawCenteredString(font,Integer.toString(seat.handCount()),badge.x()+badge.w()/2,badge.y()+4,0xffffefd1);
        String number=List.of("一","二","三","四","五","六","七","八").get(seat.seat());
        drawSeatClock(g,s,seat,x,y+h+3,w,number+"号位");
        if(showDistances && !seat.self() && seat.alive() && seat.distanceFromViewer()!=null){
            String label="距离 "+seat.distanceFromViewer();
            int labelW=font.width(label)+8;
            g.fill(x+w/2-labelW/2,y-14,x+w/2+labelW/2,y-2,0xe136271e);
            g.drawCenteredString(font,label,x+w/2,y-12,0xffffd987);
        }
        drawEquipment(g,seat,x,y,w,h);
        String controlLabel=seat.control().automatic()?(seat.control().source().equals("DISCONNECT")?"离线托管":"托管"):seat.human() && !seat.online()?"离线":"";
        if(!controlLabel.isEmpty()){
            int labelX=x+nameW+3,labelY=y+h-29,labelW=Math.max(12,w-nameW-7),labelH=Math.max(9,Math.min(13,h/9));
            g.fill(labelX,labelY-1,labelX+labelW,labelY+labelH+1,0xe13a2c1c);
            InkText.fitted(g,font,controlLabel,labelX+labelW/2f,labelY,labelW-2,labelH,0xffffd987);
        }
        if(seat.alive() && (seat.chained() || chainReleased.containsKey(seat.seat()))){
            Long started=chainStarted.get(seat.seat());
            long elapsed=started==null?ChainRenderer.WRAP_MILLIS:net.minecraft.Util.getMillis()-started;
            int inset=Math.max(3,Math.round(12*board().unit()));
            int chainX=x+nameW+inset,chainY=y+Math.round(h*.49f);
            int hpWidth=Math.max(1,Math.round(16*board().unit()))+2*Math.max(1,Math.round(3*board().unit()));
            int chainW=Math.max(1,x+w-2-hpWidth-inset-chainX);
            if(seat.chained()) {
                ChainRenderer.drawWrapping(g,chainX,chainY,chainW,h,elapsed);
                if(elapsed>=ChainRenderer.WRAP_MILLIS)chainStarted.remove(seat.seat());
                zoneTips.put(new Rect(chainX,chainY-Math.max(3,chainW/15),chainW,Math.max(8,chainW/6)),"连环 · 受到属性伤害时传导给其他连环角色");
            } else {
                long releaseElapsed=net.minecraft.Util.getMillis()-chainReleased.get(seat.seat());
                ChainRenderer.drawReleasing(g,chainX,chainY,chainW,releaseElapsed);
                if(releaseElapsed<ChainRenderer.RELEASE_MILLIS)renderedChainRelease=new int[]{seat.seat(),(int)releaseElapsed};
                if(releaseElapsed>=ChainRenderer.RELEASE_MILLIS)chainReleased.remove(seat.seat());
            }
        }
        var viewer=self();
        String deathLabel=dev.sgs.validation.client.layout.DeathSealLayout.label(seat.alive(),seat.self(),seat.teamId(),viewer==null?-1:viewer.teamId(),seat.identityOrdinal());
        if(!deathLabel.isEmpty()) {
            var deathBox=dev.sgs.validation.client.layout.DeathSealLayout.box(x,y,w,h,board().unit());
            DeathSeal.draw(g,font,deathLabel,deathBox);
            zoneTips.put(new Rect(deathBox.x(),deathBox.y(),deathBox.size(),deathBox.size()),deathLabel);
        }
        if(unavailable) {
            // Darken the original colors without the gray wash reserved for death.
            g.fill(x-3,y-3,x+w+3,y+h+3,0x78000000);
            zoneTips.put(new Rect(x,y,w,h),targetHintText(blocked));
        } else drawAttackTargetMarker(g,seat,x,y,w,h);
        if(damageHit!=null)drawDamageFeedback(g,damageHit,damageAge,x,y,w,h);
    }
    /** Paint after the decorative frame so selection cannot be covered by its gold texture. */
    private void drawAttackTargetMarker(GuiGraphics g,SeatView seat,int x,int y,int w,int h){
        var choice=ownedPromptChoice();
        boolean recipient=choice!=null && choice.allowsTarget(seat.seat()) && target==seat.seat();
        boolean multi=fangtianSelection() && attackTargets.contains(seat.seat());
        if((!multi && !recipient) || w<32 || h<24)return;
        int viewer=view.snapshot().viewerSeat(),count=view.snapshot().seats().size();
        var ordered=attackTargets.stream().sorted(Comparator.comparingInt(n->Math.floorMod(n-viewer,count))).toList();
        int number=multi?ordered.indexOf(seat.seat())+1:1;float unit=board().unit();
        int outside=Math.max(4,Math.round(6*unit));
        int topEdge=Math.max(y-outside,Math.max(15,Math.round(55*unit))+3),bottomEdge=y+h+outside;
        g.renderOutline(x-outside-1,topEdge-1,w+outside*2+2,bottomEdge-topEdge+2,0xff35220e);
        g.renderOutline(x-outside,topEdge,w+outside*2,bottomEdge-topEdge,0xffffe27d);
        g.renderOutline(x-outside+1,topEdge+1,w+outside*2-2,bottomEdge-topEdge-2,0xffe8a746);
        int nameW=Math.max(12,Math.round(w*.16f)),available=Math.max(1,w-nameW-8);
        int badgeW=Math.min(available,Math.max(24,Math.round(64*unit))),badgeH=Math.max(11,Math.round(22*unit));
        int left=x+nameW+4+(available-badgeW)/2,top=y+Math.max(5,Math.round(9*unit));
        g.fill(left-1,top-1,left+badgeW+1,top+badgeH+1,0xffffd778);
        g.fill(left,top,left+badgeW,top+badgeH,0xf04b3019);
        InkText.fitted(g,font,recipient?"目标":badgeW>=28?"目标 "+number:Integer.toString(number),left+badgeW/2f,top+1,badgeW-4,badgeH-2,0xffffe7a6);
        attackTargetMarkers.put(seat.seat(),number);
    }
    private void drawIdentityBadge(GuiGraphics g,SeatView seat,int x,int y){
        drawIdentityBadge(g,seat,x,y,16);
    }
    private void drawIdentityBadge(GuiGraphics g,SeatView seat,int x,int y,int size){
        String identityLabel="?",badgeTip="";
        if(seat.teamId()>=0){
            var me=self();boolean friendly=me!=null && me.teamId()>=0 && me.teamId()==seat.teamId();
            identityLabel=me==null?"?":seat.self()?"我":friendly?"友":"敌";badgeTip=me==null?"公开队伍":seat.self()?"我方 · 自己":friendly?"我方 · 队友":"敌方";
        }else if(seat.identityOrdinal()>=0){
            var identity=dev.sgs.core.Identity.values()[seat.identityOrdinal()];
            identityLabel=switch(identity){case LORD->"主";case LOYALIST->"忠";case REBEL->"反";case RENEGADE->"内";};
            badgeTip=switch(identity){case LORD->"主公";case LOYALIST->"忠臣";case REBEL->"反贼";case RENEGADE->"内奸";};
        }else if(canMarkIdentity(seat)){
            identityLabel=MatchClient.identityMarks.label(seat.seat());
            identityMarkHits.put(seat.seat(),new Rect(x,y,size,size));
        }
        boolean deduction=canMarkIdentity(seat) && !identityLabel.equals("?");
        IdentitySeal.draw(g,font,identityLabel,x,y,size,deduction);
        if(canMarkIdentity(seat))badgeTip=deduction?"我的标记："+IdentitySeal.name(identityLabel):"身份未知";
        relationBadges.put(seat.seat(),identityLabel);
        if(!badgeTip.isEmpty())zoneTips.put(new Rect(x,y,size,size),badgeTip);
    }
    private boolean canMarkIdentity(SeatView seat){
        return view.snapshot().viewerSeat()>=0 && !seat.self() && seat.teamId()<0 && seat.identityOrdinal()<0;
    }
    private boolean identityMarkControlsAvailable(){
        return overlay.isEmpty() && !cardsOpen && !logOpen && !socialPaletteVisible()
            && (!draftPrompt() || selectionWaiting(view.snapshot())) && !zonePrompt()
            && (view.snapshot().status()==1 || selectionWaiting(view.snapshot()))
            && view.snapshot().identityIntro()==null && view.snapshot().openingHand()==null;
    }
    private void drawIdentityPicker(GuiGraphics g,int mx,int my){
        if(identityPickerSeat<0)return;
        var seat=view.snapshot().seats().stream().filter(s->s.seat()==identityPickerSeat).findFirst().orElse(null);
        var anchor=identityMarkHits.get(identityPickerSeat);
        if(!identityMarkControlsAvailable() || seat==null || !canMarkIdentity(seat) || anchor==null){identityPickerSeat=-1;return;}
        int size=anchor.w(),gap=3,padding=3;
        int x=Math.min(anchor.x()+size+4,width-size-padding-1);
        int y=Math.clamp(anchor.y(),padding,Math.max(padding,height-(size+gap)*3+gap-padding));
        g.pose().pushPose();g.pose().translate(0,0,400);
        g.fill(x-padding,y-padding,x+size+padding,y+3*size+2*gap+padding,0xff6a5033);
        g.fill(x-padding+1,y-padding+1,x+size+padding-1,y+3*size+2*gap+padding-1,0xffe4d9b9);
        String[] labels={"忠","反","内"};
        for(int i=0;i<labels.length;i++){
            int top=y+i*(size+gap);String label=labels[i];var hit=new Rect(x,top,size,size);
            identityChoiceHits.put(label,hit);
            IdentitySeal.draw(g,font,label,x,top,size,false);
            if(hit.contains(mx,my) || MatchClient.identityMarks.label(identityPickerSeat).equals(label))
                g.renderOutline(x-1,top-1,size+2,size+2,0xffffcf7a);
        }
        g.pose().popPose();
    }
    private void drawEquipment(GuiGraphics g,SeatView seat,int x,int y,int w,int h){
        CardView[] equips={seat.treasure(),seat.weapon(),seat.armor(),seat.horseMinus(),seat.horsePlus()};
        String[] labels={"宝","武","甲","攻","防"};
        String[] slotNames={"宝具","武器","防具","进攻马","防御马"};
        if(seat.self()){
            var eq=board().equipment();int ew=eq.width(),ex=eq.x(),ey=eq.y(),eh=eq.height()/5;
            for(int i=0;i<5;i++){
                BoardSkin.nine(g,BoardSkin.SLOT,ex,ey+i*eh,ew,Math.max(8,eh-2),192,64,12);
                if(equips[i]==null)InkText.label(g,font,labels[i],ex+ew/2f,ey+i*eh+1,ew-6,Math.max(6,eh-4),0xffb79863);
                if(equips[i]!=null)drawEquippedCard(g,equips[i],ex+3,ey+i*eh+1,ew-6,Math.max(6,eh-4),0xffebd5ab);
                zoneTips.put(new Rect(ex,ey+i*eh,ew,Math.max(8,eh-2)),equips[i]==null?labels[i]+" · 空装备栏":equipmentTip(equips[i]));
                if(equips[i]!=null){
                    equipmentHits.put(equips[i].instanceId(),new Rect(ex,ey+i*eh,ew,Math.max(8,eh-2)));
                    if(selected.contains(equips[i].instanceId()))g.renderOutline(ex,ey+i*eh,ew,eh-2,0xffffcf64);
                }
            }
        }else{
            int count=0;for(var card:equips)if(card!=null)count++;
            int index=0;
            for(int i=0;i<5;i++){
                if(equips[i]==null)continue;
                var box=PortraitOverlayLayout.equipment(x,y,w,h,index++,count,Math.max(7,(int)Math.ceil(19/InkText.pixels())));
                g.fill(box.x(),box.y(),box.right(),box.bottom(),0xffe6d3af);
                g.fill(box.x(),box.y(),box.right(),box.y()+1,0xfff5e6ca);
                drawEquippedCard(g,equips[i],box.x()+2,box.y()+1,box.width()-4,Math.max(1,box.height()-2),0xff422c1c);
                zoneTips.put(new Rect(box.x(),box.y(),box.width(),box.height()),slotNames[i]+" · "+equipmentTip(equips[i]));
            }
        }
        for(int i=seat.delay().size()-1;i>=0;i--){
            var box=PortraitOverlayLayout.judgment(x,y,w,h,seat.self(),seat.delay().size()-1-i);
            String card=cardName(seat.delay().get(i));
            String seal=card.contains("乐")?"乐":card.contains("兵")?"兵":card.contains("闪电")?"闪":card.substring(0,1);
            for(int row=0;row<box.height();row++){
                int half=Math.min(row+1,box.height()-row);int center=box.x()+box.width()/2;
                g.fill(center-half,box.y()+row,center+half+1,box.y()+row+1,0xff8b733e);
                if(half>2)g.fill(center-half+2,box.y()+row,center+half-1,box.y()+row+1,0xffd5bd78);
            }
            InkText.fitted(g,font,seal,box.x()+box.width()/2f,box.y()+box.height()*.17f,box.width()*.65f,box.height()*.65f,0xff3d3825);
            zoneTips.put(new Rect(box.x(),box.y(),box.width(),box.height()),"判定区 · "+card+" · 后入先判");}
    }
    /** Keep the physical card's rank and suit visible after it leaves the hand. */
    private void drawEquippedCard(GuiGraphics g,CardView card,int x,int y,int w,int h,int nameColor){
        var suit=Suit.values()[card.suitOrdinal()];
        String rank=Rank.values()[card.rankOrdinal()].label();
        int lineHeight=h;
        if(suit==Suit.NONE){equipmentText(g,cardName(card),x+w/2f,y,w,h,lineHeight,nameColor);return;}
        int stampW=Math.min(w/3,Math.max(8,(int)Math.ceil(25/InkText.pixels())));
        int stampX=x,iconH=Math.max(1,Math.min(h,(int)Math.ceil(11/InkText.pixels()))),iconW=Math.max(1,Math.round(iconH*8f/9));
        int nameX=x+stampW+2,nameWidth=Math.max(1,w-stampW-2);
        equipmentText(g,cardName(card),nameX+nameWidth/2f,y,nameWidth,h,lineHeight,nameColor);
        g.fill(stampX,y,stampX+stampW,y+h,0xffe9d4ae);
        int color=suit==Suit.HEART || suit==Suit.DIAMOND?0xffac3039:0xff242422;
        equipmentText(g,rank,stampX+(stampW-iconW)/2f,y,Math.max(1,stampW-iconW-1),h,lineHeight,color);
        g.blit(dev.sgs.validation.ProbeMod.id("textures/gui/suits/"+suit.id()+"_v1.png"),
            stampX+stampW-iconW,y+(h-iconH)/2,iconW,iconH,0,0,8,9,8,9);
    }
    /** A fixed em per board scale: short names never grow to fill a tall self equipment slot. */
    private void equipmentText(GuiGraphics g,String value,float centerX,int y,int width,int height,int lineHeight,int color){
        InkText.label(g,font,value,centerX,y,width,height,color);
    }
    private void drawLimitedSeal(GuiGraphics g,int x,int y,int size,boolean ready){
        int edge=ready?0xffb58a42:0xff77746d,light=ready?0xffffe2a0:0xffb0aca2;
        g.fill(x+1,y+1,x+size,y+size,0xb0191510);
        g.fill(x+1,y,x+size-1,y+size,edge);
        g.fill(x,y+1,x+size,y+size-1,edge);
        g.fill(x+1,y+1,x+size-1,y+size-1,ready?0xff513a23:0xff373735);
        g.fill(x+2,y+1,x+size-2,y+2,light);
        g.fill(x+1,y+2,x+2,y+size-2,light);
        g.fill(x+2,y+size-2,x+size-1,y+size-1,ready?0xff765025:0xff4d4c48);
        InkText.fitted(g,font,"限",x+size/2f,y+2,size-4,size-4,ready?0xffffdfa0:0xffb2afa7);
    }
    private String equipmentTip(CardView card){
        String suit=switch(Suit.values()[card.suitOrdinal()]){case SPADE->"黑桃";case HEART->"红桃";case CLUB->"梅花";case DIAMOND->"方块";case NONE->"";};
        return Rank.values()[card.rankOrdinal()].label()+suit+" · "+cardName(card);
    }


    private void drawCard(GuiGraphics g,CardView card,int x,int y,float scale,boolean chosen){
        var type=CardType.values()[card.typeOrdinal()];
        var illustration=art.stream().filter(a->a.id().equals(type.id())).findFirst().orElse(null);
        if(illustration!=null)CardRenderer.draw(g,font,illustration,Suit.values()[card.suitOrdinal()].id(),Rank.values()[card.rankOrdinal()].label(),x,y,scale,
            chosen?CardRenderer.State.SELECTED:CardRenderer.State.NORMAL,false);
        else{
            int cw=Math.round(80*scale),ch=Math.round(112*scale);
            g.fill(x,y,x+cw,y+ch,0xffefdfb9);g.renderOutline(x,y,cw,ch,chosen?0xffffbf62:0xff654a2a);
            text(g,Rank.values()[card.rankOrdinal()].label(),x+2,y+3,0xff30271c,cw-4);
            String suit=switch(Suit.values()[card.suitOrdinal()]){case SPADE->"♠";case CLUB->"♣";case HEART->"♥";case DIAMOND->"♦";default->"";};
            text(g,suit,x+2,y+13,0xff863627,cw-4);
            InkText.fitted(g,font,cardName(card),x+cw/2f,y+6,Math.max(1,cw-24*scale),Math.max(1,24*scale),0xff30271c);text(g,"图案待补",x+2,y+ch/2,0xff725d44,cw-4);
        }
        var marks=card.marks();
        if(markPreview && self()!=null && self().hand().stream().anyMatch(c->c.instanceId()==card.instanceId()))marks=List.of(new dev.sgs.core.card.CardSkillMark("preview:qin","琴","组件预览，非实际技能效果",0x6b4129,dev.sgs.core.card.CardSkillMark.Visibility.OWNER));
        dev.sgs.validation.client.widget.CardMarkRenderer.draw(g,font,marks,x,y,Math.round(80*scale),Math.round(112*scale));
        if(markPreview)text(g,"标记组件预览",handLeft(),height-10,0xffd8bc84,120);
    }
    boolean autoplayVeilVisible(){
        var s=view.snapshot();return s.status()==1 && self()!=null && self().alive() && self().control().automatic()
            && s.identityIntro()==null && s.heroBan()==null && s.openingHand()==null
            && overlay.isEmpty() && !cardsOpen && !logOpen && !socialPaletteVisible();
    }
    private void drawAutoplayVeil(GuiGraphics g){
        if(!autoplayVeilVisible())return;
        var hand=board().hand();int top=Math.max(0,hand.y()-Math.round(92*board().unit()));
        g.pose().pushPose();g.pose().translate(0,0,210);
        g.fillGradient(0,top,width,height,0x0027180e,0xe8211209);
        int cx=hand.x()+hand.width()/2,radius=Math.max(1,Math.round(300*board().unit()));
        int glowTop=hand.y()+hand.height()/3;
        for(int i=0;i<48;i++){
            double t=(i+.5-24)/24;int alpha=(int)(100*Math.exp(-4*t*t));
            int x=cx-radius+2*radius*i/48,right=cx-radius+2*radius*(i+1)/48;
            g.fillGradient(x,glowTop,right,height,0x00b47718,(alpha<<24)|0xb47718);
        }
        g.pose().popPose();
    }
    private void drawHand(GuiGraphics g,int mx,int my){
        handHits.clear();var me=self();if(me==null)return;var cards=me.hand();
        float scale=handScale();int cw=Math.round(80*scale),ch=Math.round(112*scale),baseline=board().hand().bottom()-ch;
        var layout=handLayout(cards.size());handOffset=layout.offset();hoverCard=-1;
        for(int i=cards.size()-1;i>=0;i--){var card=cards.get(i);int y=baseline-(selected.contains(card.instanceId())?selectedLift():0);
            if(!me.control().automatic() && mx>=handLeft() && mx<handRight() && new Rect(layout.x(i),y,cw,ch).contains(mx,my)){hoverCard=card.instanceId();break;}}
        g.enableScissor(handLeft()-2,baseline-selectedLift()-3,handRight()+2,height);
        for(int layer=0;layer<2;layer++)for(int i=0;i<cards.size();i++){
            var card=cards.get(i);boolean chosen=selected.contains(card.instanceId());
            int cardLayer=card.instanceId()==hoverCard?1:0;if(cardLayer!=layer)continue;
            g.pose().pushPose();g.pose().translate(0,0,layer==1?Math.max(180,22+cards.size()*2):20+i*2);
            int x=layout.x(i),y=baseline-(chosen?selectedLift():0);
            if(cardMotions.arriving(card.instanceId(),me.seat(),net.minecraft.Util.getMillis())){
                handHits.put(card.instanceId(),new Rect(x,y,cw,ch));g.pose().popPose();continue;
            }
            drawCard(g,card,x,y,scale,chosen);
            if(layout.step()<cw-3 && i<cards.size()-1 && card.instanceId()!=hoverCard){
                // The name rail is narrow even when most of a card is exposed; never cover its art.
                int rail=Math.min(layout.step()-2,Math.max(8,Math.round(12*scale)));
                float labelScale=Math.min(.85f,(rail-1)/9f);String label=cardName(card);
                g.fill(x+1,y+25,x+1+rail,y+ch-2,0xf2eddfbc);
                g.pose().pushPose();g.pose().translate(x+1,y+27,0);g.pose().scale(labelScale,labelScale,1);
                for(int c=0;c<label.length();c++)g.drawString(font,label.substring(c,c+1),0,c*10,0xff30271c,false);
                g.pose().popPose();
            }
            if(!me.control().automatic() && !handCardBlockedReason(card).isEmpty()){
                g.fill(x+1,y+1,x+cw-1,y+ch-1,0x99141618);
            }
            g.pose().popPose();
            handHits.put(card.instanceId(),new Rect(x,y,cw,ch));
        }
        g.disableScissor();
        if(layout.contentWidth()>layout.available())text(g,"滚轮横移手牌",handLeft(),baseline-12,0xffead2a0,120);
    }
    @Override public boolean mouseClicked(double mx,double my,int button){
        if(identityMarkControlsAvailable()){
            if(identityPickerSeat>=0 && button==0){
                var seat=view.snapshot().seats().stream().filter(s->s.seat()==identityPickerSeat).findFirst().orElse(null);
                if(seat!=null && canMarkIdentity(seat))for(var choice:identityChoiceHits.entrySet())if(choice.getValue().contains(mx,my)){
                    MatchClient.identityMarks.select(identityPickerSeat,choice.getKey());identityPickerSeat=-1;identityChoiceHits.clear();return true;
                }
            }
            if(button==0 || button==1)
            for(var hit:identityMarkHits.entrySet())if(hit.getValue().contains(mx,my)){
                var seat=view.snapshot().seats().stream().filter(s->s.seat()==hit.getKey()).findFirst().orElse(null);
                if(seat!=null && canMarkIdentity(seat)){
                    if(button==1){MatchClient.identityMarks.reset(seat.seat());identityPickerSeat=-1;}
                    else identityPickerSeat=identityPickerSeat==seat.seat()?-1:seat.seat();
                    identityChoiceHits.clear();return true;
                }
            }
        }
        if(identityPickerSeat>=0){identityPickerSeat=-1;identityChoiceHits.clear();return true;}
        if(super.mouseClicked(mx,my,button))return true;
        if(self()!=null && self().control().automatic())return true;
        if(socialPaletteVisible()){
            if(socialPaletteBox().contains(mx,my))return true;
            if(giftPalette && button==0)for(var e:seatHits.entrySet())if(e.getValue().contains(mx,my)){
                var seat=view.snapshot().seats().stream().filter(p->p.seat()==e.getKey()).findFirst().orElse(null);
                if(seat==null || seat.self() || !seat.human()){feedbackMessage="请选择本局其他真人";return true;}
                giftTarget=seat.seat();sendSelectedGift();return true;
            }
            giftPalette=false;emotePalette=false;rebuildWidgets();return true;
        }
        if(!overlay.isEmpty() || cardsOpen || logOpen || draftPrompt() || zonePrompt() || view.snapshot().identityIntro()!=null || view.snapshot().heroBan()!=null || view.snapshot().status()==2 || view.snapshot().openingHand()!=null || selectionWaiting(view.snapshot()))return true;
        if(button==0 && skinStripAvailable() && skinStrip().contains(mx,my)){openOverlay("SKINS");return true;}
        if(wuxiePending() || view.snapshot().counterspellWindow()!=null && !myPrompt())return true;
        boolean equipmentCosts=self()!=null && (ownedCards(self()).stream().anyMatch(this::hasConversion) || wusheng || qixi || jijiu || guose || duanliang || view.snapshot().heroPresentations().stream()
            .filter(h->h.id().equals(self().heroId())).flatMap(h->h.skills().stream())
            .anyMatch(meta->meta.id().equals(costSkill) && meta.allowEquipmentCosts()
                && view.snapshot().prompt()==null && view.snapshot().counterspellWindow()==null && view.snapshot().processing().isEmpty() && view.snapshot().currentSeat()==view.snapshot().viewerSeat()
                && view.snapshot().phaseOrdinal()==Phase.PLAY.ordinal()));
        var ownedChoice=ownedPromptChoice();
        if(ownedChoice!=null){
            for(var entry:equipmentHits.entrySet())if(entry.getValue().contains(mx,my)){
                if(button==0)selectOwnedPromptCard(entry.getKey());return true;
            }
        }
        if(equipmentCosts)
            for(var entry:equipmentHits.entrySet())if(entry.getValue().contains(mx,my)){
                long id=entry.getKey();var card=ownedCards(self()).stream().filter(c->c.instanceId()==id).findFirst().orElse(null);
                if(card!=null && !handCardBlockedReason(card).isEmpty())return true;
                if(!selected.remove(id)){selected.clear();selected.add(id);}convertedCard=-1;convertedType=-1;reason="";rebuildWidgets();return true;
            }
        // Match drawHand's pointer resolution, not the previous frame's promoted hover layer.
        var hand=self()==null?List.<CardView>of():self().hand();
        for(var card:hand.reversed())if(mx>=handLeft() && mx<handRight() && handHits.containsKey(card.instanceId()) && handHits.get(card.instanceId()).contains(mx,my)){
            long id=card.instanceId();
            if(ownedChoice!=null){if(button==0)selectOwnedPromptCard(id);return true;}
            if(card!=null && !handCardBlockedReason(card).isEmpty())return true;
            if(!selected.remove(id)){
                if(costSkill.isEmpty() && !myPrompt() && view.snapshot().phaseOrdinal()==Phase.PLAY.ordinal())selected.clear();attackTargets.clear();
                selected.add(id);
            }convertedCard=-1;convertedType=-1;reason="";rebuildWidgets();return true;
        }
        for(var entry:seatHits.entrySet())if(entry.getValue().contains(mx,my)){
            String blocked=targetHint(entry.getKey());if(blocked!=null && !blocked.isEmpty()){reason=targetHintText(blocked);return true;}
            if(fangtianSelection()){
                int seat=entry.getKey();var candidate=view.snapshot().seats().stream().filter(p->p.seat()==seat).findFirst().orElse(null);
                if(candidate==null || !candidate.alive() || candidate.self()){reason="请选择其他存活角色";return true;}
                if(!attackTargets.remove(seat)){
                    if(attackTargets.size()>=3){reason="方天画戟至多选择三名目标";return true;}
                    attackTargets.add(seat);
                }
                target=attackTargets.isEmpty()?-1:attackTargets.iterator().next();secondary=-1;reason="";return true;
            }
            attackTargets.clear();
            boolean borrow=selected.size()==1 && self()!=null && self().hand().stream().anyMatch(c->selected.contains(c.instanceId()) && (c.typeOrdinal()==CardType.JIEDAO.ordinal() || c.typeOrdinal()==CardType.TIESUO.ordinal()));
            if(borrow && target>=0 && target!=entry.getKey())secondary=entry.getKey();
            else{target=entry.getKey();secondary=-1;}
            reason="";rebuildWidgets();return true;
        }
        return false;
    }

























    private boolean wuxiePending(){return wuxiePendingPromptId!=0 && wuxiePendingPromptId==currentWuxiePromptId();}


















    @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical){
        if(!logOpen && my>=height-7-Math.round(112*handScale())-selectedLift() && mx>=handLeft() && mx<handRight()){
            handOffset=Math.max(0,handOffset-(int)((horizontal!=0?horizontal:vertical)*24));return true;
        }
        return super.mouseScrolled(mx,my,horizontal,vertical);
    }













    @Override public void renderBackground(GuiGraphics g,int mx,int my,float partial){}
    @Override public void onClose(){
        if(view.snapshot().status()==2){
            if(!overlay.isEmpty() || cardsOpen || logOpen){overlay="";cardsOpen=false;logOpen=false;rebuildWidgets();}
            else if(resultStage<2)resultPage(resultStage+1);
            else MatchClient.leaveFinished();
        }else if(view.snapshot().openingHand()!=null){
            if(!overlay.isEmpty() || cardsOpen || logOpen){overlay="";cardsOpen=false;logOpen=false;rebuildWidgets();}
            else decideOpening(false);
        }else if(zonePrompt() && overlay.isEmpty() && !cardsOpen && !logOpen){zoneChoiceHidden=!zoneChoiceHidden;rebuildWidgets();}
        else MatchClient.hide();
    }
    @Override public boolean isPauseScreen(){return false;}

private boolean identityRevealReady(){
return identityRevealStarted<0 || net.minecraft.Util.getMillis()-identityRevealStarted>=420;
}
}
