package dev.sgs.validation.client;

import static dev.sgs.validation.client.ClientText.tr;
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
    private MatchChatPanel chatPanel;
    private final Set<net.minecraft.client.gui.components.AbstractWidget> boardWidgets=Collections.newSetFromMap(new IdentityHashMap<>());
    private boolean boardCovered(){return MatchChatClient.open||!overlay.isEmpty()||cardsOpen||logOpen||privateDeckVisible()||draftVisible()||zoneModal();}
    private void drawBoardWidgets(GuiGraphics g,int mx,int my,float partial){
        g.pose().pushPose();g.pose().translate(0,0,200);
        for(var widget:boardWidgets)widget.render(g,boardCovered()?-1:mx,boardCovered()?-1:my,partial);
        g.pose().popPose();
    }
    private void drawForegroundWidgets(GuiGraphics g,int mx,int my,float partial){
        var hidden=boardWidgets.stream().filter(w->w.visible).toList();
        hidden.forEach(w->w.visible=false);
        try{super.render(g,mx,my,partial);}finally{hidden.forEach(w->w.visible=true);}
    }

    private final dev.sgs.validation.client.layout.CardMotionTimeline cardMotions=new dev.sgs.validation.client.layout.CardMotionTimeline();
    private final dev.sgs.validation.client.layout.PublicCardTimeline publicCards=new dev.sgs.validation.client.layout.PublicCardTimeline();
    private final Map<Long,Rect> previousHandAnchors=new LinkedHashMap<>();
    private CardMotionRenderer.Frame motionFrame;
    public CardMotionRenderer.Frame cardMotionFrameForSmoke(){return motionFrame;}

    private final dev.sgs.validation.client.layout.PublicRevealTimeline revealTimeline=new dev.sgs.validation.client.layout.PublicRevealTimeline();
    private List<Long> drawnPublicReveals=List.of();
    private List<CardArtCatalog.Art> art=List.of();
    private final Map<Long,Rect> handHits=new LinkedHashMap<>();
    private final Map<Integer,Rect> seatHits=new LinkedHashMap<>();
    private final Set<Integer> autoplayBadgeSeats=new HashSet<>();
    boolean autoplayBadgeForSmoke(int seat){return autoplayBadgeSeats.contains(seat);}
    private final Map<Integer,Long> chainStarted=new HashMap<>();
    private final Map<Integer,Long> chainReleased=new HashMap<>();
    private int[] renderedChainRelease;
    public int[] chainReleaseFrameForSmoke(){return renderedChainRelease==null?null:renderedChainRelease.clone();}
    private final dev.sgs.validation.client.layout.DamageFeedback damageFeedback=new dev.sgs.validation.client.layout.DamageFeedback();
    private final Set<Long> selected=new LinkedHashSet<>();
    private String visiblePaperTip="";
    private final Map<Integer,String> relationBadges=new HashMap<>();
    private final Map<Integer,Rect> identityMarkHits=new LinkedHashMap<>();
    private final Map<String,Rect> identityChoiceHits=new LinkedHashMap<>();
    private int identityPickerSeat=-1;
    private int target=-1,secondary=-1,handOffset;
    private final Set<Integer> attackTargets=new LinkedHashSet<>();
    private final Map<Integer,Long> targetEffectStarted=new HashMap<>();
    private long skillChoiceCard=-1,weaponCostPendingPrompt;
    private int skillChoiceDestination=-1;
    private long deckOrderPrompt=-1,deckSelected=-1,deckDragged=-1,deckOrderSubmitted=-1;
    private double deckDragX,deckDragY;private boolean deckDidDrag;
    private long deckOrderBudgetMillis=1;
    private dev.sgs.validation.client.layout.GuanxingOrder deckOrder;
    private final Map<Long,Rect> deckCardHits=new LinkedHashMap<>();
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
    private int[] smokePointer;
    private String reason="";
    private String heroPreview="";
    private boolean heroPending;
    private String costSkill="";
    private int errors;
    private long errorShownUntil;
    private boolean duanliang;
    private String mythicTransform="";
    private long convertedCard=-1;
    private int convertedType=-1,conversionPage;
    private int nativeRenderedFrames,nativeConversionFrames;
    int nativeRenderedFramesForSmoke(){return nativeRenderedFrames;}
    int nativeConversionFramesForSmoke(){return nativeConversionFrames;}
    private boolean longdan;
    private boolean wusheng;
    private boolean qixi,jijiu,qingguo,guose;
    private boolean logOpen;
    private int journalX=-1,journalY=-1;
    private final java.util.Set<Integer> heroJournalSeats=new java.util.HashSet<>();
    private Rect heroModeButton(Rect r){return new Rect(r.x()+Math.round(r.w()*.23f),r.y()+Math.round(r.h()*.30f),30,12);}
    private boolean cardsOpen;
    private double logScrollY,logScrollX,logMaxY,logMaxX,logDragOffset;
    private boolean logFollowLatest=true;
    private int logDragging;
    private int logSeat=-1;
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
    private final Map<String,Button> testButtons=new HashMap<>();
    private record Rect(int x,int y,int w,int h){boolean contains(double mx,double my){return mx>=x && mx<x+w && my>=y && my<y+h;}}
    public MatchScreen(MatchNetwork.View view){super(Component.literal(tr("sgs.ui.sanguosha_2258bde7")));this.view=view;
        MatchClient.identityMarks.bind(view.matchId(),view.snapshot().viewerSeat());
        MatchChatClient.bind(view);
        cardMotions.accept(view.matchId(),view.connectionEpoch(),view.snapshot().viewerSeat(),view.revision(),view.snapshot().cardMotions(),net.minecraft.Util.getMillis());
        publicCards.accept(view.matchId(),view.connectionEpoch(),view.snapshot().viewerSeat(),view.revision(),view.snapshot().publicMoves(),view.snapshot().processing(),net.minecraft.Util.getMillis());
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
        publicCards.accept(next.matchId(),next.connectionEpoch(),next.snapshot().viewerSeat(),next.revision(),next.snapshot().publicMoves(),next.snapshot().processing(),net.minecraft.Util.getMillis());
        boolean newerDamageView=next.revision()>view.revision();
        boolean damageBaseline=view.connectionEpoch()!=next.connectionEpoch();
        boolean chainBaseline=damageBaseline || view.matchId()!=next.matchId() || view.snapshot().viewerSeat()!=next.snapshot().viewerSeat();
        if(view.matchId()!=next.matchId() || next.snapshot().status()==2)identityPickerSeat=-1;
        if(next.revision()>view.revision() && "STATE_CHANGED".equals(errorCode))reason="";
        var previous=view.snapshot();view=next;
        if(previous.viewerSeat()!=next.snapshot().viewerSeat())identityPickerSeat=-1;
        MatchClient.identityMarks.bind(next.matchId(),next.snapshot().viewerSeat());
        var current=next.snapshot();
        if(chainBaseline || current.status()!=0 || self()!=null && self().heroId()!=null
            || !current.heroCandidates().contains(heroPreview)){
            heroPreview="";heroPending=false;
        }
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
        var me=self();boolean consumedSelection=selected.stream().anyMatch(id->me==null || ownedCards(me).stream().noneMatch(card->card.instanceId()==id));
        selected.removeIf(id->me==null || ownedCards(me).stream().noneMatch(card->card.instanceId()==id));
        if(consumedSelection || previous.seats().stream().filter(SeatView::self).mapToLong(p->p.control().version()).findFirst().orElse(-1)!=current.seats().stream().filter(SeatView::self).mapToLong(p->p.control().version()).findFirst().orElse(-1)
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
    public void error(String code){if(heroPending){heroPending=false;rebuildWidgets();}if(deckOrderSubmitted>=0){deckOrderSubmitted=-1;rebuildWidgets();}if(!banSubmitted.isEmpty()){banSubmitted="";rebuildWidgets();}errors++;errorCode=code;reason=code.equals("HERO_TAKEN")?tr("sgs.ui.that_hero_was_picked_by_another_player_choose_ag_2b152fe3"):Component.translatable("game.sgs.error."+code).getString();if(reason.startsWith("game.sgs.error."))reason=code;errorShownUntil=System.currentTimeMillis()+(code.equals("HERO_TAKEN")?6000:1800);if(identityPending || openingPending || wuxiePendingPromptId!=0 || zonePendingPrompt!=0 || harvestPendingPrompt!=0 || weaponCostPendingPrompt!=0){weaponCostPendingPrompt=0;zonePendingPrompt=0;harvestPendingPrompt=0;identityPending=false;identityPendingSlot=-1;openingPending=false;wuxiePendingPromptId=0;rebuildWidgets();}}
    private SeatView self(){return view.snapshot().seats().stream().filter(SeatView::self).map(this::fixtureSeat).findFirst().orElse(null);}
    private SeatView fixtureSeat(SeatView seat){
        if(equipmentEffectsFixture)return new SeatView(seat.seat(),seat.name(),seat.human(),seat.online(),seat.alive(),seat.heroId(),seat.kingdomOrdinal(),seat.hp(),seat.maxHp(),seat.identityOrdinal(),seat.handCount(),
            seat.weapon(),seat.armor(),seat.horsePlus(),seat.horseMinus(),seat.delay(),seat.hand(),seat.self(),seat.handVisible(),seat.chained() || !Boolean.getBoolean("sgs.weaponVoxelSmoke") && (seat.self() || seat.seat()%2==0),seat.teamId(),seat.treasure(),seat.statuses()).withControl(seat.control());
        if(portraitFixture){
            var weapon=new CardView(-9100-seat.seat(),CardType.ZHUGE_NU.ordinal(),0,0);
            boolean metadata=Boolean.getBoolean("sgs.equipmentMetadataSmoke");
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
    private boolean privateDeckPrompt(){return myPrompt()&&view.snapshot().prompt().typeOrdinal()==PromptType.GUANXING_ORDER.ordinal();}
    private boolean privateDeckVisible(){return privateDeckPrompt()&&overlay.isEmpty()&&!cardsOpen&&!logOpen;}
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
        return prompt.allowedTypeOrdinals().contains(declaredType(card).ordinal()) || card.playOptions().stream().anyMatch(o->o.available() && prompt.allowedTypeOrdinals().contains(o.declaredTypeOrdinal()));
    }
    /** Same decision drives the paint, hit handling, stale selection cleanup and final submit. */
    private String handCardBlockedReason(CardView card){
        if(fixtureCount>0)return "";
        var s=view.snapshot();var me=self();if(me==null || !me.alive() || s.status()!=1)return tr("sgs.ui.you_cannot_select_hand_cards_now_b119f172");
        if(me.control().automatic())return tr("sgs.ui.autoplay_42d12169");
        var actual=CardType.values()[card.typeOrdinal()];var suit=Suit.values()[card.suitOrdinal()];
        if(myPrompt()){
            var p=s.prompt();var type=PromptType.values()[p.typeOrdinal()];
            var owned=ownedPromptChoice();
            if(owned!=null)return owned.allowsCard(card.instanceId())?"":tr("sgs.ui.this_card_is_not_available_for_this_choice_05233464");
            if(type==PromptType.GUI_CAI_REPLACE || type==PromptType.GANGLIE_DISCARD || type==PromptType.LIULI_TARGET)
                return p.choices().stream().anyMatch(c->c.instanceId()==card.instanceId())?"":tr("sgs.ui.this_card_is_not_available_for_this_choice_05233464");
            if(type==PromptType.YIJI_GIVE)return p.choices().stream().anyMatch(c->c.instanceId()==card.instanceId())?"":tr("sgs.ui.this_card_cannot_be_distributed_by_yiji_1e30c34e");
            if(!handResponsePrompt(type))return tr("sgs.ui.this_prompt_does_not_require_hand_cards_7bd99a56");
            return responseCardSelectable(card)?"":tr("sgs.ui.this_card_cannot_answer_the_current_prompt_abc41bbe");
        }
        if(s.prompt()!=null || s.counterspellWindow()!=null || !s.processing().isEmpty() || s.openingHand()!=null || s.currentSeat()!=s.viewerSeat())return tr("sgs.ui.you_cannot_play_cards_now_b50bd59e");
        if(s.phaseOrdinal()==Phase.DISCARD.ordinal())return "";
        if(s.phaseOrdinal()!=Phase.PLAY.ordinal())return tr("sgs.ui.it_is_not_the_play_phase_a6c58802");
        if(!costSkill.isEmpty()){
            if(costSkill.equals("qiangxi") && actual.category()!=dev.sgs.core.card.CardCategory.WEAPON)return tr("sgs.ui.qiangxi_discard_a_weapon_or_select_no_card_to_lo_a719710d");
            int maximum=ClientText.heroes(s.heroPresentations()).stream().filter(h->h.id().equals(me.heroId())).flatMap(h->h.skills().stream())
                .filter(m->m.id().equals(costSkill)).mapToInt(dev.sgs.core.addon.SkillPresentation::maximumCards).findFirst().orElse(160);
            return !selected.contains(card.instanceId()) && selected.size()>=maximum?tr("sgs.ui.you_have_selected_enough_cards_for_this_skill_e4f64fd2"):"";
        }
        if(wusheng && suit!=Suit.HEART && suit!=Suit.DIAMOND)return tr("sgs.ui.wusheng_requires_a_red_card_524ebc24");
        if(qixi && suit!=Suit.SPADE && suit!=Suit.CLUB)return tr("sgs.ui.qixi_requires_a_black_card_136accaa");
        if(guose && suit!=Suit.DIAMOND)return tr("sgs.ui.guose_requires_a_diamond_card_8148e02a");
        if(duanliang && ((suit!=Suit.SPADE && suit!=Suit.CLUB) || (actual.category()!=dev.sgs.core.card.CardCategory.BASIC && !actual.isEquipment())))return tr("sgs.ui.duanliang_requires_a_black_basic_or_equipment_ca_61e7b040");
        if(longdan && actual!=CardType.SHA && actual!=CardType.HUOSHA && actual!=CardType.LEISHA && actual!=CardType.SHAN)return tr("sgs.ui.longdan_requires_slash_or_dodge_75bf6d15");
        var option=card.playOptions().stream().filter(o->o.declaredTypeOrdinal()==declaredType(card).ordinal()).findFirst();
        if(hasConversion(card))return "";
        return option.map(o->o.available()?"":cardUnavailableText(o.reason())).orElse(tr("sgs.ui.this_card_cannot_be_played_now_6158a2e6"));
    }
    private static String cardUnavailableText(String code){return switch(code){
        case "SHA_LIMIT"->tr("sgs.ui.you_have_used_all_slashes_allowed_this_phase_077098b5");case "JIU_LIMIT"->tr("sgs.ui.you_have_already_used_wine_this_turn_7bb37052");case "FULL_HP"->tr("sgs.ui.hp_is_full_peach_cannot_be_used_f050b481");
        case "SHAN_NOT_PLAYABLE","RESPONSE_ONLY"->tr("sgs.ui.this_card_can_only_be_used_in_response_eb968e2b");case "OUT_OF_RANGE"->tr("sgs.ui.no_legal_target_is_in_range_9727f53c");
        case "DUPLICATE_DELAYED_TRICK"->tr("sgs.ui.that_delayed_trick_is_already_in_the_judgment_ar_02dea545");case "TARGET_PROHIBITED"->tr("sgs.ui.no_allowed_targets_e66282f2");
        case "TARGET_NO_CARDS"->tr("sgs.ui.no_target_has_selectable_cards_4a1aa811");case "TARGET_NO_WEAPON","TARGET_REQUIRED"->tr("sgs.ui.no_legal_targets_d9d5d849");
        default->tr("sgs.ui.this_card_cannot_be_played_now_6158a2e6");
    };}
    private boolean preparingSkill(){return !costSkill.isEmpty() || longdan || wusheng || qixi || jijiu || qingguo || guose || duanliang || !mythicTransform.isEmpty();}
    private void cancelPreparedSkill(){selected.clear();clearCardTargets();convertedCard=-1;convertedType=-1;costSkill="";longdan=false;wusheng=false;qixi=false;jijiu=false;qingguo=false;guose=false;duanliang=false;mythicTransform="";reason="";rebuildWidgets();}
    private void beginSkillCost(String id){costSkill=id;clearCardTargets();selected.clear();attackTargets.clear();longdan=false;wusheng=false;qixi=false;jijiu=false;qingguo=false;guose=false;duanliang=false;mythicTransform="";reason=tr("sgs.ui.select_382f4b55")+skillName(id)+tr("sgs.ui.cards_then_confirm_or_activate_the_skill_6b802d75");rebuildWidgets();}
    private Button button(String key,String label,int x,int y,int width,Runnable action){
        var b=addRenderableWidget(SgsButton.make(Component.literal(label),ignored->action.run()).bounds(x,y,width,18).build());testButtons.put(key,b);return b;
    }
    private Button decisionButton(String key,String label,boolean first,Runnable action){
        var rect=first?decisionLayout().confirm():decisionLayout().cancel();
        var b=button(key,label,rect.x(),rect.y(),rect.width(),action);b.setHeight(rect.height());return b;
    }
    private void icon(String key,BoardIconButton.Icon type,String title,int x,int y,int size,Runnable action){
        testButtons.put(key,addRenderableWidget(new BoardIconButton(x,y,size,type,title,key.startsWith("social:"),ignored->action.run())));
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
                case "INSUFFICIENT_BALANCE"->tr("sgs.ui.insufficient_consumables_b1e185eb");case "RATE_LIMITED"->tr("sgs.ui.please_wait_before_sending_another_gift_02ae774f");
                case "TARGET_HUMAN_ONLY"->tr("sgs.ui.choose_another_human_player_in_this_match_40c038ed");case "SELF_TARGET"->tr("sgs.ui.you_cannot_send_a_gift_to_yourself_a44eb95b");
                case "NOT_IN_ACTIVE_MATCH"->tr("sgs.ui.available_during_an_active_match_only_8a61ecb9");case "DATA_UNAVAILABLE"->tr("sgs.ui.database_unavailable_refresh_your_balance_and_re_50858b12");
                default->tr("sgs.ui.gift_failed_27c30866")+r.reason();
            };
            reason=feedbackMessage;errorShownUntil=System.currentTimeMillis()+2400;
        }
        rebuildWidgets();
    }
    public dev.sgs.validation.social.GiftNetwork.Result giftForSmoke(){return giftState;}
    public void setMarkPreviewForSmoke(boolean value){markPreview=value;}
    public boolean hasSeatClockForSmoke(){return seatClockRendered;}
    public void hoverLimitedForSmoke(int seat){
        if(!Boolean.getBoolean("sgs.seatWaitLimitedRender"))throw new IllegalStateException("Limited seal preview requires opt-in");
        var portrait=seatHits.get(seat);if(portrait==null)throw new IllegalStateException("Missing portrait");
        var seal=PortraitOverlayLayout.limited(portrait.x(),portrait.y(),portrait.w(),portrait.h(),0);
        if(seal.x()<portrait.x()+portrait.w() || seal.right()>width || seal.bottom()>height)
            throw new IllegalStateException("Limited seal must be exterior and on screen");
        smokePointer=new int[]{seal.x()+seal.width()/2,seal.y()+seal.height()/2};
    }
    public String emoteForSmoke(){return lastEmote;}
    public void clickGiftSeatForSmoke(int seat){var r=seatHits.get(seat);if(r==null || !mouseClicked(r.x()+r.w()/2d,r.y()+r.h()/2d,0))throw new IllegalStateException("Missing gift seat "+seat);}
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
        if(giftState==null || giftState.flowerBalance()<gift.cost()){feedbackMessage=tr("sgs.ui.not_enough_flowers_ecfe1669");return;}
        giftRequest=GiftClient.request(view.matchId(),giftTarget,gift.id(),gift.received());feedbackMessage=tr("sgs.ui.sending_gift_bc0a29f5");rebuildWidgets();
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
                    });testButtons.put("gift:"+gift.id(),addRenderableWidget(choice));
                }
            }
            if(pages>1){button("gift-prev","‹",r.x()+r.w()-42,r.y()+r.h()-18,18,()->{giftPage=(giftPage+pages-1)%pages;rebuildWidgets();});button("gift-next","›",r.x()+r.w()-22,r.y()+r.h()-18,18,()->{giftPage=(giftPage+1)%pages;rebuildWidgets();});}
        }else {
            var ids=dev.sgs.core.social.Emotes.IDS;
            for(int i=0;i<ids.size();i++){String id=ids.get(i);int x=r.x()+6+(i%4)*(cell+3),y=r.y()+23+(i/4)*(cell+3);
                var choice=new SgsButton(x,y,cell,cell,Component.literal(ClientText.named("sgs.emote."+id,dev.sgs.core.social.Emotes.name(id))),ignored->{GiftClient.request(view.matchId(),-1,"EMOTE:"+id,0);emotePalette=false;rebuildWidgets();}){
                    @Override protected boolean parchment(){return false;}
                    @Override protected void renderWidget(GuiGraphics g,int mx,int my,float partial){g.fill(getX(),getY(),getX()+getWidth(),getY()+getHeight(),0xee302016);g.renderOutline(getX(),getY(),getWidth(),getHeight(),isHoveredOrFocused()?0xffffc55b:0xff745333);EmoteAnimations.draw(g,id,getX()+3,getY()+3,getWidth()-6);}
                };choice.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(ClientText.named("sgs.emote."+id,dev.sgs.core.social.Emotes.name(id)))));testButtons.put("emote:"+id,addRenderableWidget(choice));
            }
        }
    }
    private void drawSocialPalette(GuiGraphics g){
        var r=socialPaletteBox();g.pose().pushPose();g.pose().translate(0,0,245);
        g.fill(r.x()-2,r.y()-2,r.x()+r.w()+2,r.y()+r.h()+2,0xff906e43);g.fill(r.x(),r.y(),r.x()+r.w(),r.y()+r.h(),0xf3432e20);
        if(giftPalette){
            GiftSprites.draw(g,"POPPY",r.x()+5,r.y()+4,12);text(g,giftState==null?tr("sgs.ui.refreshing_balance_abe90768"):Long.toString(giftState.flowerBalance()),r.x()+20,r.y()+5,0xffffd68b,r.w()-24);
            int cell=giftCell();g.fill(r.x()+1,r.y()+23,r.x()+3,r.y()+23+cell,0xff8ba95f);g.fill(r.x()+1,r.y()+26+cell,r.x()+3,r.y()+26+2*cell,0xffba7753);
        }else{text(g,tr("sgs.ui.emotes_fecd6640"),r.x()+6,r.y()+5,0xffead3a2,r.w()-12);}
        g.pose().popPose();
    }
    public void feedback(dev.sgs.validation.social.FeedbackNetwork.Result r){
        if(r.matchId()!=view.matchId())return;
        if(r.kind().equals("REPORT")){
            if(!reportPending || r.requestId()!=reportRequestId)return;
            reportPending=false;reportRequestId=0;
            var submit=testButtons.get("report-submit");if(submit!=null)submit.active=true;
            feedbackMessage=r.accepted()?tr("sgs.ui.report_submitted_see_history_for_updates_bc765e31"):tr("sgs.ui.not_submitted_e9e60b12")+feedbackReason(r.reason());
        }
        else if(!r.accepted())feedbackMessage=r.reason().equals("RESULT_NOT_READY")?tr("sgs.ui.rewards_are_not_ready_yet_check_again_later_68c73b18"):tr("sgs.ui.query_failed_cd87622d")+feedbackReason(r.reason());
        else if(r.reward()!=null)feedbackMessage="";
    }
    private static String feedbackReason(String c){return switch(c){case "DUPLICATE_REPORT"->tr("sgs.ui.this_player_has_already_been_reported_for_this_r_47f0be50");case "RATE_LIMITED"->tr("sgs.ui.you_are_submitting_too_frequently_d3d31081");case "TARGET_AI"->tr("sgs.ui.ai_players_cannot_be_reported_3fa967b8");case "INVALID_TARGET"->tr("sgs.ui.choose_another_human_player_in_this_match_40c038ed");case "DETAIL_TOO_LONG"->tr("sgs.ui.the_description_must_be_at_most_240_characters_853caa4b");default->c;};}
    @Override protected void init(){
        if(chatPanel==null)chatPanel=new MatchChatPanel(font);
        chatPanel.layout(board());
        refreshTargetChoices();
        var owner=self();if(owner!=null)selected.removeIf(id->owner.hand().stream().filter(c->c.instanceId()==id).anyMatch(c->!handCardBlockedReason(c).isEmpty()));
        if(art.isEmpty())art=CardArtCatalog.load(minecraft.getResourceManager());testButtons.clear();boardWidgets.clear();
        var s=view.snapshot();int center=width/2;
        var board=board();int size=Math.max(12,Math.round(36*board.unit())),gap=Math.max(3,Math.round(18*board.unit())),tx=width-4*(size+gap)-4;
        icon("settings",BoardIconButton.Icon.SETTINGS,tr("sgs.ui.settings_7debf9cb"),tx,2,size,MatchClient::openSettings);
        icon("heroes",BoardIconButton.Icon.HERO,tr("sgs.ui.match_heroes_1b0ed652"),tx+size+gap,2,size,()->openOverlay("HERO"));
        icon("menu",BoardIconButton.Icon.MENU,tr("sgs.ui.menu_70824562"),tx+3*(size+gap),2,size,()->openOverlay("MENU"));
        BoardIconButton.Icon[] kinds={BoardIconButton.Icon.FLOWER,BoardIconButton.Icon.REPORT,BoardIconButton.Icon.AUTOPLAY,BoardIconButton.Icon.DISTANCE,BoardIconButton.Icon.SMILE,BoardIconButton.Icon.CHAT};
        String[] captions={tr("sgs.ui.flowers_cb4633ff"),tr("sgs.ui.report_3eeaade9"),self()!=null && self().control().automatic()?tr("sgs.ui.stop_autoplay_3cc51234"):tr("sgs.ui.autoplay_731b0cba"),showDistances?tr("sgs.ui.hide_distance_f4c37a4b"):tr("sgs.ui.distance_b3480678"),tr("sgs.ui.emotes_fecd6640"),tr("sgs.ui.chat_5358b2dd")};
        String[] keys={"flowers","report","autoplay","distance","emotes","chat"};
        for(int i=0;i<6;i++){int n=i;var cell=board.socialButton(i);
            icon("social:"+i,kinds[i],captions[i],cell.x(),cell.y(),cell.width(),()->{
                switch(n){
                    case 0->toggleGiftPalette();
                    case 1->openOverlay("REPORT");
                    case 2->{if(self()!=null)MatchClient.setAutoplay(!self().control().automatic());}
                    case 3->{showDistances=!showDistances;rebuildWidgets();}
                    case 4->{emotePalette=!emotePalette;giftPalette=false;rebuildWidgets();}
                    case 5->{if(MatchChatClient.open)chatPanel.close();else{giftPalette=false;emotePalette=false;rebuildWidgets();chatPanel.open();}}
                }
            });
            var control=testButtons.get("social:"+i);testButtons.put(keys[i],control);
            if((i==0 || i==1 || i==2 || i==4 || i==5) && s.viewerSeat()<0 || (i==2 || i==3) && s.status()!=1){
                control.active=false;
                control.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(s.viewerSeat()<0?tr("sgs.ui.only_match_participants_can_do_this_32de60a7"):tr("sgs.ui.available_after_the_match_starts_b5dbb9e5"))));
            }
            if(i==3 && (self()==null || !self().alive()))control.active=false;
        }
        icon("journal-icon",BoardIconButton.Icon.LOG,tr("sgs.ui.match_log_7f513661"),tx+2*(size+gap),2,size,()->{logOpen=!logOpen;cardsOpen=false;overlay="";logSeat=-1;logFollowLatest=true;rebuildWidgets();});
        boardWidgets.addAll(testButtons.values());
        testButtons.put("eggs",testButtons.get("flowers"));
        if(s.viewerSeat()<0)for(String key:List.of("rewards","receipt"))testButtons.get(key).active=false;
        if(socialPaletteVisible())buildSocialPalette();
        if(!overlay.isEmpty()){buildOverlay();return;}
        if(cardsOpen){button("cards-close",tr("sgs.ui.return_to_match_e1d60dda"),center-48,height-38,96,()->{cardsOpen=false;rebuildWidgets();});return;}
        if(logOpen){
            button("log-close","×",logLeft()+logPanelWidth()-23,logTop()+6,18,()->{logOpen=false;logDragging=0;rebuildWidgets();});
            return;
        }
        if(s.status()==2){buildResultControls();return;}
        if(self()!=null && self().control().automatic()){
            if(autoplayVeilVisible()){
                var r=board().box(676,811,320,88);
                var b=addRenderableWidget(new dev.sgs.validation.client.widget.AutoplayCancelButton(r.x(),r.y(),r.width(),r.height(),ignored->MatchClient.setAutoplay(false)));
                testButtons.put("autoplay-cancel",b);
            }
            return;
        }
        if(s.viewerSeat()<0){button("spectator-leave",tr("sgs.ui.stop_spectating_fd640b42"),center-48,height-38,96,MatchClient::hide);return;}
        if(self()!=null && !self().alive())return;
        if(s.identityIntro()!=null){buildIdentityIntro();return;}
        if(s.heroBan()!=null){buildHeroBan();return;}
        if(s.openingHand()!=null){buildOpeningControls();return;}
        if(s.status()==0){
            if(selectionWaiting(s))return;
            var candidates=selectionCandidateIds(s);
            String chosen=self()==null?null:self().heroId();int count=candidates.size();
            for(int i=0;i<count;i++){
                String id=candidates.get(i);var hero=ClientText.heroes(s.heroPresentations()).stream().filter(h->h.id().equals(id)).findFirst().orElse(null);
                var box=selectionCandidate(i,count);
                if(hero==null){var missing=button("hero:"+id,tr("sgs.ui.hero_data_not_received_ec9a7e34")+id,box.x(),box.y(),box.width(),()->{});missing.setHeight(box.height());missing.active=false;continue;}
                var card=new HeroChoiceWidget(hero,box.x(),box.y(),box.width(),box.height(),id.equals(chosen)||id.equals(heroPreview),chosen==null && !heroPending,
                    ignored->{heroPreview=id;MatchClient.send(MatchAction.previewHero(MatchClient.nextNonce(),id));reason="";errorCode="";rebuildWidgets();},chosen==null?tr("sgs.ui.selected_743aaf95"):tr("sgs.ui.confirmed_cc4d48b2"));
                testButtons.put("hero:"+id,addRenderableWidget(card));
            }
            if(chosen==null && !candidates.isEmpty()){
                var box=board().box(746,766,180,32);
                var pick=button("hero-confirm",heroPending?tr("sgs.ui.submitting_4cc708dc"):tr("sgs.ui.confirm_b56d9ac6"),box.x(),box.y(),box.width(),this::confirmHero);
                pick.setHeight(box.height());pick.active=!heroPending && candidates.contains(heroPreview);
            }
            return;
        }
        if(myPrompt()){
            var prompt=s.prompt();
            if(zonePrompt() && zoneChoiceHidden){
                var restore=decisionButton("zone-restore",tr("sgs.ui.show_card_selection_17c0bc7e"),true,()->{zoneChoiceHidden=false;rebuildWidgets();});
                restore.active=zonePendingPrompt!=prompt.promptId();return;
            }
            if(buildSkillChoices()) {addSkillWidget();return;}
            if(prompt.typeOrdinal()==PromptType.SKILL_INVOKE.ordinal()){
                confirm=decisionButton("invoke",prompt.skillId().equals("yaowu_recover")?tr("sgs.ui.recover_hp_5656d6cb"):tr("sgs.ui.activate_90254ad3")+skillName(prompt.skillId()),true,()->response(true));
                decisionButton("decline",prompt.skillId().equals("yaowu_recover")?tr("sgs.ui.draw_1_card_9629d283"):tr("sgs.ui.decline_cb8ca0f5"),false,()->response(false));
                addSkillWidget();return;
            }
            if(draftPrompt()){
                buildHarvestChoices(prompt);
                return;
            }
            String replyLabel=prompt.typeOrdinal()==PromptType.QINGLONG_SHA.ordinal()?tr("sgs.ui.play_another_slash_5f74a18b"):prompt.typeOrdinal()==PromptType.HUOGONG_REVEAL.ordinal()?tr("sgs.ui.reveal_selected_7642576b"):prompt.typeOrdinal()==PromptType.HUOGONG_PAY.ordinal()?tr("sgs.ui.discard_for_fire_attack_09ad3f91"):tr("sgs.ui.respond_with_selected_2599e5d1");
            confirm=decisionButton("respond",replyLabel,true,()->response(true));
            confirm.active=responseSelectionReady();
            boolean wuxie=prompt.typeOrdinal()==PromptType.WUXIE_COUNTER.ordinal();
            var decline=decisionButton("decline",prompt.typeOrdinal()==PromptType.QINGLONG_SHA.ordinal()?tr("sgs.ui.stop_pursuit_48fce34d"):prompt.typeOrdinal()==PromptType.HUOGONG_REVEAL.ordinal()?tr("sgs.ui.auto_reveal_1060b415"):wuxie?tr("sgs.ui.pass_049bb10d"):tr("sgs.ui.pass_response_9f0a8fa0"),false,()->response(false));
            if(wuxie){
                // This is a server scope intent, independent of whether the private hand has Wuxie.
                var lane=decisionLayout().timer();var row=decisionLayout().confirm();
                int responseGap=Math.max(2,Math.min(8,lane.width()/32)),cell=Math.max(1,Math.min(116,(lane.width()-2*responseGap)/3));
                int left=lane.x()+(lane.width()-cell*3-responseGap*2)/2;
                confirm.setX(left);confirm.setWidth(cell);decline.setX(left+cell+responseGap);decline.setWidth(cell);
                var pass=button("wuxie-pass-scope",tr("sgs.ui.pass_this_trick_2a7dfae0"),left+2*(cell+responseGap),row.y(),cell,
                    ()->passWuxieScope(prompt.promptId()));
                pass.setHeight(row.height());
                pass.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(tr("sgs.ui.skip_further_prompts_for_this_trick_and_its_rema_0e15d03c"))));
                if(wuxiePending())lockWuxieButtons();
            }
        }else if(s.prompt()==null && s.counterspellWindow()==null && s.processing().isEmpty() && s.currentSeat()==s.viewerSeat()){
            if(s.phaseOrdinal()==Phase.PLAY.ordinal()){
                confirm=decisionButton("play",tr("sgs.ui.confirm_f526c899"),true,this::play);
                confirm.active=playSelectionReady();
                if(preparingSkill())decisionButton("skill-cancel",tr("sgs.ui.cancel_4d0b4688"),false,this::cancelPreparedSkill);
                else decisionButton("end",tr("sgs.ui.end_turn_58687e83"),false,()->MatchClient.send(MatchAction.endPlay(MatchClient.nextNonce())));
            }else if(s.phaseOrdinal()==Phase.DISCARD.ordinal() && self()!=null && self().requiredDiscard()>0){confirm=button("discard",tr("sgs.ui.discard_0b398b44"),center-46,controlY(),92,
                ()->{if(!selected.isEmpty() && selected.size()<=self().requiredDiscard())
                    MatchClient.send(MatchAction.confirmDiscard(MatchClient.nextNonce(),List.copyOf(selected)));});
                confirm.active=!selected.isEmpty() && selected.size()<=self().requiredDiscard();
            }
        }
        if(s.counterspellWindow()!=null && !myPrompt()){addSkillWidget();return;}
        var convertible=conversionCard();
        if(convertible!=null && hasConversion(convertible)){var conversionBox=board.box(1190,666,64,32);var conversionButton=button("convert",tr("sgs.ui.convert_to_285bfecd"),conversionBox.x(),conversionBox.y(),conversionBox.width(),this::openConversion);conversionButton.setHeight(conversionBox.height());}
        var clear=board.box(1154,666,32,32);icon("cancel",BoardIconButton.Icon.LEAVE,tr("sgs.ui.clear_selection_f02e9439"),clear.x(),clear.y(),Math.max(12,clear.width()),this::cancelPreparedSkill);

        addSkillWidget();
    }
    private int overlayWidth(){return Math.min(380,width-24);}
    private boolean hasConversion(CardView card){return card.playOptions().stream().anyMatch(o->o.available() && o.declaredTypeOrdinal()!=card.typeOrdinal());}
    private CardView conversionCard(){return self()==null || selected.size()!=1?null:ownedCards(self()).stream().filter(c->selected.contains(c.instanceId())).findFirst().orElse(null);}
    private void openConversion(){
        var card=conversionCard();if(card==null || !hasConversion(card)){reason=tr("sgs.ui.select_a_convertible_card_first_cdead856");return;}
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
                if(pages>1){button("convert-prev",tr("sgs.ui.previous_91bb7b0d"),x+16,y+147,64,()->{conversionPage=Math.max(0,conversionPage-1);rebuildWidgets();});button("convert-next",tr("sgs.ui.next_717b3f43"),x+w-80,y+147,64,()->{conversionPage=Math.min(pages-1,conversionPage+1);rebuildWidgets();});}
            }
        }
        if(overlay.equals("SKINS"))buildSkinOverlay(x,y,w);
        if(overlay.equals("MENU")){
            String[] labels={tr("sgs.ui.report_player_10b7d409"),tr("sgs.ui.rewards_23194f6f"),tr("sgs.ui.match_log_7f513661"),tr("sgs.ui.public_cards_b35d3d56"),tr("sgs.ui.rules_6bae6191"),tr("sgs.ui.return_to_room_a808cfd7")};
            for(int i=0;i<6;i++){int n=i;button("menu:"+i,labels[i],x+18+(i%2)*(w-36)/2,y+42+(i/2)*34,(w-44)/2,()->{
                if(n==0)openOverlay("REPORT");else if(n==1)openOverlay("REWARDS");else if(n==2){overlay="";logOpen=true;logFollowLatest=true;logScrollX=0;logDragging=0;rebuildWidgets();}else if(n==3){overlay="";cardsOpen=true;rebuildWidgets();}else if(n==4)openOverlay("HELP");else if(view.snapshot().status()==2)MatchClient.leaveFinished();else MatchClient.hide();
            });}
            testButtons.put("report",testButtons.get("menu:0"));testButtons.put("hide",testButtons.get("menu:5"));
            if(view.snapshot().viewerSeat()<0)for(String key:List.of("menu:0","menu:1")){
                var control=testButtons.get(key);control.active=false;control.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(tr("sgs.ui.only_match_participants_can_do_this_32de60a7"))));
            }
        }
        if(overlay.equals("REPORT")){
            var players=view.snapshot().seats().stream().filter(p->p.human() && !p.self()).toList();
            for(int i=0;i<players.size();i++){var p=players.get(i);button("report-target:"+p.seat(),(p.seat()==reportTarget?"✓ ":"")+p.name(),x+10+(i%3)*((w-20)/3),y+33+(i/3)*20,(w-26)/3,()->{reportTarget=p.seat();rebuildWidgets();});}
            int ry=y+34+Math.max(1,(players.size()+2)/3)*20;
            String[] codes={"ABUSE","GRIEFING","CHEATING","OTHER"},labels={tr("sgs.ui.abuse_harassment_07a5ef2d"),tr("sgs.ui.disruptive_play_52a00f78"),tr("sgs.ui.suspected_cheating_61e172c1"),tr("sgs.ui.other_1a26edf9")};
            for(int i=0;i<4;i++){String code=codes[i];button("report-reason:"+code,(reportReason.equals(code)?"✓":"")+labels[i],x+10+i*(w-20)/4,ry,(w-24)/4,()->{reportReason=code;rebuildWidgets();});}
            String draft=reportDetail==null?"":reportDetail.getValue();
            reportDetail=new EditBox(font,x+13,ry+27,w-26,18,Component.literal(tr("sgs.ui.additional_details_ee71941a")));reportDetail.setMaxLength(240);reportDetail.setBordered(false);reportDetail.setTextColor(0xff4a301d);reportDetail.setValue(draft);addRenderableWidget(reportDetail);
            var submit=button("report-submit",tr("sgs.ui.submit_report_be38dc90"),x+w/2-102,ry+53,96,()->{
                if(reportPending)return;
                if(reportTarget<0){feedbackMessage=tr("sgs.ui.choose_another_human_player_in_this_match_40c038ed");return;}
                reportPending=true;feedbackMessage=tr("sgs.ui.submitting_10b2dbd5");
                var reportButton=testButtons.get("report-submit");if(reportButton!=null)reportButton.active=false;
                reportRequestId=FeedbackClient.request(view.matchId(),"REPORT",reportTarget,reportReason,reportDetail.getValue());
            });submit.active=!reportPending;
            button("report-mine",tr("sgs.ui.report_history_afd74a85"),x+w/2+6,ry+53,96,()->{
                if(minecraft.player!=null)minecraft.player.connection.sendCommand("sgs reports");
                minecraft.setScreen(new ReportHistoryChatScreen());
            });
        }
        button("overlay-close",tr("sgs.ui.return_to_match_e1d60dda"),x+w/2-48,y+overlayHeight()-21,96,()->{overlay="";rebuildWidgets();});
    }
    private net.minecraft.resources.ResourceLocation skinClassic(){
        var me=self();if(me==null)return null;
        var presentation=ClientText.heroes(view.snapshot().heroPresentations()).stream().filter(p->p.id().equals(me.heroId())).findFirst().orElse(null);
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
            pick.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(tr("sgs.ui.preview_ad5abaa0")+skin.name())));
        }
        button("skin-apply",tr("sgs.ui.use_this_skin_b83ceca1"),x+w/2-48,y+149,96,()->{
            skinRequest=MatchClient.selectSkin(me.heroId(),skinPreview);feedbackMessage=tr("sgs.ui.changing_skin_c7df92be");
        }).active=!skins.isEmpty();
    }
    public void skinResult(dev.sgs.validation.match.SkinNetwork.Result result){
        if(result.request()!=skinRequest)return;
        skinRequest=0;feedbackMessage=result.reason();
        if(result.accepted()){overlay="";rebuildWidgets();}
    }
    private void drawSkinOverlay(GuiGraphics g,int x,int y,int w){
        var me=self();if(me==null)return;var skins=HeroSkins.available(me.heroId(),skinClassic());
        text(g,heroName(me.heroId())+tr("sgs.ui.choose_a_skin_and_confirm_b0985711"),x+12,y+27,0xff513622,w-24);
        int cell=(w-36)/Math.max(1,skins.size()),ph=80,pw=57;
        var current=view.skins().getOrDefault(me.seat(),"classic");
        for(int i=0;i<skins.size();i++){
            var skin=skins.get(i);int px=x+18+i*cell+(cell-8-pw)/2,py=y+43;
            HeroRenderer.portraitCrop(g,skin.texture(),px,py,pw,ph);
            g.renderOutline(px-2,py-2,pw+4,ph+4,skinPreview.equals(skin.id())?0xffffb22f:0xff8e693d);
            if(current.equals(skin.id())){g.fill(px,py+ph-12,px+pw,py+ph,0xe538271c);text(g,tr("sgs.ui.in_use_e6aa2cbd"),px+12,py+ph-10,0xffffdfa0,pw-12);}
        }
        if(skins.isEmpty())text(g,tr("sgs.ui.no_artwork_available_for_this_hero_4beb6c33"),x+18,y+63,0xff6c482b,w-36);
    }
    private void drawOverlay(GuiGraphics g){
        int w=overlayWidth(),x=(width-w)/2,y=overlayTop();
        g.pose().pushPose();g.pose().translate(0,0,350);
        g.fill(x-3,y-3,x+w+3,y+overlayHeight()+3,0xff8f6939);g.fill(x,y,x+w,y+overlayHeight(),0xffe5c799);
        String title=switch(overlay){case "CONVERT"->tr("sgs.ui.convert_to_285bfecd");case "FLOWERS"->tr("sgs.ui.send_flowers_fab0eec2");case "EGGS"->tr("sgs.ui.throw_egg_01dfb354");case "REPORT"->tr("sgs.ui.report_player_10b7d409");case "REWARDS"->tr("sgs.ui.rewards_23194f6f");case "HERO"->tr("sgs.ui.match_heroes_1b0ed652");case "SKINS"->tr("sgs.ui.hero_skins_04ac5508");case "MENU"->tr("sgs.ui.menu_70824562");default->tr("sgs.ui.match_rules_6457cba0");};
        InkText.drawCentered(g,font,title,width/2,y+9,0xff4a2e19);
        if(overlay.equals("CONVERT")){nativeConversionFrames++;}
        else if(overlay.equals("SKINS"))drawSkinOverlay(g,x,y,w);
        else if(overlay.equals("REPORT")){
            int count=(int)view.snapshot().seats().stream().filter(p->p.human() && !p.self()).count();
            if(count==0)text(g,tr("sgs.ui.there_are_no_other_human_players_to_report_0f170704"),x+13,y+34,0xff513622,w-26);
            int ry=y+34+Math.max(1,(count+2)/3)*20;g.fill(x+9,ry+23,x+w-9,ry+47,0xfff5dfb7);
            text(g,tr("sgs.ui.details_optional_up_to_240_characters_9e372350"),x+12,ry+14,0xff755435,w-24);
        }else if(overlay.equals("REWARDS"))drawReward(g,x+16,y+37,w-32);
        else if(overlay.equals("HERO")){
            var heroes=ClientText.heroes(view.snapshot().heroPresentations());int columns=Math.max(1,Math.min(8,heroes.size())),cell=(w-24)/columns;
            for(int i=0;i<heroes.size();i++){var h=heroes.get(i);int hx=x+12+(i%columns)*cell,hy=y+37+(i/columns)*86;
                HeroRenderer.portrait(g,font,h.id(),hx,hy,cell-5,60,0xffa67b40,true);text(g,h.name(),hx,hy+65,0xff4e3522,cell-4);
            }
        }else if(!overlay.equals("MENU")){
            String[] lines={tr("sgs.ui.6_8_player_identity_mode_using_the_same_rules_en_41808b5b"),tr("sgs.ui.2v2_work_with_your_ally_to_defeat_the_opposing_t_6b0d5112"),tr("sgs.ui.each_hp_is_shown_as_one_half_heart_kingdoms_mode_9c40a6f7"),tr("sgs.ui.click_a_hand_card_to_select_it_choose_targets_th_9cd568a9"),tr("sgs.ui.your_skills_are_below_your_hero_card_hover_over_2f2395a9"),tr("sgs.ui.the_gold_bar_shows_the_time_remaining_on_the_ser_51efb79b")};
            for(int i=0;i<lines.length;i++)text(g,lines[i],x+12,y+37+i*18,0xff4e3522,w-24);
        }
        if(!feedbackMessage.isEmpty())text(g,feedbackMessage,x+12,y+165,0xff754029,w-24);
        g.pose().popPose();
    }
    private void drawReward(GuiGraphics g,int x,int y,int w){
        var reward=FeedbackClient.reward(view.matchId());
        if(reward==null){text(g,tr("sgs.ui.waiting_for_match_rewards_1549d16a"),x,y,0xff6b4c2c,w);return;}
        InkText.drawCentered(g,font,tr("sgs.ui.xp_59530b01")+reward.xpAward()+tr("sgs.ui.honor_ba1f9354")+reward.honorAward(),x+w/2,y,0xff6e3c1e);
        text(g,tr("sgs.ui.total_xp_0b2b7117")+reward.totalXp()+tr("sgs.ui.total_honor_1f465bfb")+reward.totalHonor(),x,y+20,0xff5b4028,w);
        text(g,tr("sgs.ui.receipt_530e1c98")+reward.receiptId(),x,y+38,0xff775b39,w);
        text(g,tr("sgs.ui.rewards_have_been_saved_by_the_server_bac49155"),x,y+56,0xff775b39,w);
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
        if(resultStage==0)resultButton("result-next",tr("sgs.ui.continue_1fc1afc5"),740,799,192,()->resultPage(1));
        else{
            if(resultStage==1){
                resultButton("result-details",tr("sgs.ui.match_details_52fad65e"),1260,736,240,()->resultPage(2));
                if(view.snapshot().viewerSeat()>=0)resultButton("result-query",tr("sgs.ui.refresh_rewards_aa80e3c5"),700,799,272,
                    ()->FeedbackClient.request(view.matchId(),"QUERY_RESULT",-1,"",""));
            }else{
                resultButton("result-rewards",tr("sgs.ui.rewards_248f85c9"),1260,736,240,()->resultPage(1));
                resultButton("finish",tr("sgs.ui.return_to_waiting_room_53277e7f"),700,799,272,MatchClient::leaveFinished);
            }
            if(view.snapshot().viewerSeat()>=0)resultButton("result-report",tr("sgs.ui.report_a_problem_5b51f704"),130,799,280,()->openOverlay("REPORT"));
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
        if(seat.teamId()>=0){var viewer=self();return viewer==null?(seat.teamId()==0?tr("sgs.ui.green_team_fe1ecc02"):tr("sgs.ui.red_team_13d56d03")):seat.teamId()==viewer.teamId()?tr("sgs.ui.allies_aa051cdb"):tr("sgs.ui.enemies_99d2b5e4");}
        if(seat.identityOrdinal()<0 || seat.identityOrdinal()>=dev.sgs.core.Identity.values().length)return tr("sgs.ui.hidden_identity_b4b69410");
        return switch(dev.sgs.core.Identity.values()[seat.identityOrdinal()]){case LORD->tr("sgs.ui.lord_70368eaf");case LOYALIST->tr("sgs.ui.loyalist_83bc6bab");case REBEL->tr("sgs.ui.rebel_487c2834");case RENEGADE->tr("sgs.ui.renegade_c5344088");};
    }
    private void drawResult(GuiGraphics g,MatchSnapshot snapshot){
        if(resultStage==0){drawSeats(g,snapshot);}
        // Result content must cover elevated seat counters and card decorations.
        g.pose().translate(0,0,300);
        g.fill(0,0,width,height,resultStage==0?0x9920140d:0xdf302015);
        var frame=board().box(82,103,1508,776);g.fill(frame.x(),frame.y(),frame.right(),frame.bottom(),resultStage==0?0x993e291a:0xd54e3421);
        g.renderOutline(frame.x(),frame.y(),frame.width(),frame.height(),0xffae8550);
        var reward=FeedbackClient.reward(view.matchId());
        boolean spectator=snapshot.viewerSeat()<0;
        boolean draw=snapshot.outcomeOrdinal()==dev.sgs.core.engine.GameOutcome.DRAW.ordinal();
        String verdict=spectator?outcome(snapshot.outcomeOrdinal()):draw?tr("sgs.ui.draw_66402ea5"):(reward!=null?reward.won():resultWon(self(),snapshot))?tr("sgs.ui.victory_3d90ba4a"):tr("sgs.ui.defeat_3e3c8068");
        if(resultPresentationAt==0)resultPresentationAt=net.minecraft.Util.getMillis();
        float reveal=Math.min(1f,(net.minecraft.Util.getMillis()-resultPresentationAt)/700f);
        selectionText(g,resultStage==0?verdict:resultStage==1?tr("sgs.ui.rewards_23194f6f"):tr("sgs.ui.match_details_851478ac"),300,resultStage==0?128+Math.round((1-reveal)*28):128,1072,resultStage==0?94:65,0xfff0ca80);
        if(resultStage==0){
            long elapsed=net.minecraft.Util.getMillis()-resultPresentationAt;var scores=snapshot.tablePresentation();
            var best=scores.completeStatistics()&&scores.damageTotals().size()>=snapshot.seats().size()?snapshot.seats().stream().filter(p->scores.damageTotals().get(p.seat())>0).max(java.util.Comparator.<SeatView>comparingLong(p->scores.damageTotals().get(p.seat())).thenComparingInt(p->-p.seat())).orElse(null):null;
            if(elapsed>=1800&&best!=null){
                g.fill(frame.x(),frame.y(),frame.right(),frame.bottom(),0xff302015);
                var art=board().box(270,190,620,530);var texture=HeroRenderer.classic(best.heroId());if(texture!=null)HeroRenderer.portraitCrop(g,texture,art.x(),art.y(),art.width(),art.height());
                g.renderOutline(art.x(),art.y(),art.width(),art.height(),0xffd8ad59);
                selectionText(g,tr("sgs.ui.mvp_269985f3"),950,280,430,80,0xffffdc85);
                selectionText(g,heroName(best.heroId()),950,400,430,50,0xfff3d7a2);
                selectionText(g,tr("sgs.ui.damage_dealt_b6a45129")+scores.damageTotals().get(best.seat()),950,500,430,32,0xffe6c994);
                if(elapsed>2100)captureScene("mvp");
            }else{
                var crest=board().box(570,290,530,280);float grow=Math.min(1,(net.minecraft.Util.getMillis()-resultPresentationAt)/700f);
                for(int i=0;i<4;i++){int inset=Math.round((10+i*12)*board().unit());g.renderOutline(crest.x()+inset,crest.y()+inset,Math.max(1,crest.width()-2*inset),Math.max(1,crest.height()-2*inset),0xffc89a52);}
                selectionText(g,verdict,570,350+Math.round(20*(1-grow)),530,110,0xffffda8c);
                selectionText(g,outcome(snapshot.outcomeOrdinal()),350,620,972,36,0xffe3bd7c);
            }
            return;
        }
        if(resultStage==1){
            var own=self();
            selectionText(g,own==null?tr("sgs.ui.spectating_bbe075b7"):own.name(),146,265,640,43,0xfff0d8a5);
            selectionText(g,tr("sgs.ui.match_result_0ff43ed2")+verdict,146,343,640,34,0xffe7bd7d);
            if(spectator){
                selectionText(g,tr("sgs.ui.spectators_do_not_receive_match_rewards_c7db6a99"),840,320,650,39,0xffe0cba8);
            }else if(reward==null){
                selectionText(g,tr("sgs.ui.calculating_rewards_184edd04"),820,310,690,39,0xffe0cba8);
                selectionText(g,"",820,385,690,26,0xffcdb58c);
            }else{
                selectionText(g,tr("sgs.ui.xp_59530b01")+reward.xpAward()+tr("sgs.ui.honor_ba1f9354")+reward.honorAward(),810,300,700,44,0xfff2cd86);
                selectionText(g,tr("sgs.ui.total_xp_0b2b7117")+reward.totalXp()+tr("sgs.ui.total_honor_295d74f2")+reward.totalHonor(),810,383,700,31,0xffe5cda6);



            }
            if(!feedbackMessage.isEmpty())selectionText(g,feedbackMessage,150,651,1360,29,0xffffbe95);
        }else{
            selectionText(g,tr("sgs.ui.player_turns_ce7f72ed")+snapshot.turnNumber(),760,215,755,26,0xffd8bc91);
            int rows=Math.max(1,(snapshot.seats().size()+1)/2),stride=Math.min(118,458/rows);
            for(int i=0;i<snapshot.seats().size();i++){
                var seat=snapshot.seats().get(i);int column=i/rows,row=i%rows,x=132+column*720,y=272+row*stride;
                var cell=board().box(x,y,688,stride-12);g.fill(cell.x(),cell.y(),cell.right(),cell.bottom(),seat.self()?0xff8a6237:0xff5f402a);
                var portrait=board().box(x+12,y+8,66,stride-28);HeroRenderer.portraitExact(g,font,seat.heroId(),portrait.x(),portrait.y(),portrait.width(),portrait.height(),0xffab8450,seat.alive(),HeroSkins.syncedTexture(seat.heroId(),view.skins().getOrDefault(seat.seat(),"classic"),HeroRenderer.classic(seat.heroId())));
                selectionText(g,resultRole(seat),x+86,y+10,95,29,0xffefd08f);
                selectionText(g,seat.name()+"  · "+(seat.human()?tr("sgs.ui.player_e7c31937"):"AI"),x+194,y+10,360,29,0xfff0ddbd);
                selectionText(g,heroName(seat.heroId())+"  · "+(seat.alive()?tr("sgs.ui.alive_4e497702"):tr("sgs.ui.dead_f26ed6e2")),x+190,y+51,365,25,0xffd8bd94);
                selectionText(g,draw?tr("sgs.ui.draw_3c9abaf9"):resultWon(seat,snapshot)?tr("sgs.ui.win_f580139f"):tr("sgs.ui.loss_24b8d003"),x+589,y+22,75,43,0xfff0ca80);
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
            var control=testButtons.get(availability.skillId());
            if(!(control instanceof SkillWidget widget))continue;
            widget.disableWithReason(skillUnavailableReason(availability.reason()));
        }
    }
    private static String skillUnavailableReason(String reason){
        if(reason==null)return tr("sgs.ui.cannot_activate_now_67a7b7c8");
        return switch(reason){
            case "SKILL_USED_THIS_TURN"->tr("sgs.ui.already_activated_this_turn_2fc8fdcd");
            case "PLAYER_DEAD"->tr("sgs.ui.dead_players_cannot_activate_skills_7509f88d");
            case "GAME_NOT_RUNNING"->tr("sgs.ui.no_match_is_running_8b1c90db");
            case "NOT_YOUR_TURN"->tr("sgs.ui.it_is_not_your_turn_58091e87");
            case "WRONG_PHASE"->tr("sgs.ui.cannot_activate_in_this_phase_f0e1c9c1");
            case "LORD_ONLY"->tr("sgs.ui.lord_only_de805390");
            default->tr("sgs.ui.cannot_activate_976cd509")+reason;
        };
    }
    private void buildSkillWidgets(){
        var me=self();if(me==null)return;
        var s=view.snapshot();boolean myPlay=self()!=null && !self().control().automatic() && s.prompt()==null && s.counterspellWindow()==null && s.processing().isEmpty() && s.currentSeat()==s.viewerSeat() && s.phaseOrdinal()==Phase.PLAY.ordinal();
        var presentation=ClientText.heroes(s.heroPresentations()).stream().filter(h->h.id().equals(me.heroId())).findFirst().orElse(null);
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
                if(meta.kind()==dev.sgs.core.addon.SkillPresentation.Kind.ACTIVE && meta.target()!=dev.sgs.core.addon.SkillPresentation.Target.NONE && !costSkill.equals(meta.id())){beginSkillCost(meta.id());return;}
                if(meta.id().equals("qiangxi") && selected.isEmpty() && !costSkill.equals(meta.id())){beginSkillCost(meta.id());reason=tr("sgs.ui.select_a_weapon_or_confirm_without_a_card_to_los_1722a633");return;}
                if(meta.id().equals("dimeng") && !costSkill.equals(meta.id())){beginSkillCost(meta.id());return;}
                if(meta.minimumCards()>0 && selected.isEmpty() && !costSkill.equals(meta.id())){beginSkillCost(meta.id());return;}
                if(meta.maximumCards()>0 && (selected.size()<meta.minimumCards() || selected.size()>meta.maximumCards())){reason=tr("sgs.ui.this_skill_requires_86e3b16b")+meta.minimumCards()+tr("sgs.ui.to_43401e73")+meta.maximumCards()+tr("sgs.ui.cards_55e29de8");return;}
                if(meta.maximumCards()>0 && !meta.allowEquipmentCosts() && me.hand().stream().filter(c->selected.contains(c.instanceId())).count()!=selected.size()){reason=tr("sgs.ui.this_skill_requires_hand_cards_as_its_cost_11db033d");return;}
                if(meta.target()!=dev.sgs.core.addon.SkillPresentation.Target.NONE && target<0){reason=tr("sgs.ui.choose_a_skill_target_6285de28");return;}
                if(meta.target()==dev.sgs.core.addon.SkillPresentation.Target.OTHER && target==s.viewerSeat()){reason=tr("sgs.ui.choose_another_player_7340c87c");return;}
                if(meta.target()!=dev.sgs.core.addon.SkillPresentation.Target.NONE && s.seats().stream().noneMatch(candidate->candidate.seat()==target && candidate.alive())){reason=tr("sgs.ui.choose_a_living_player_9ad2f621");return;}
                if(meta.id().equals("jieyin") && s.seats().stream().noneMatch(candidate->candidate.seat()==target && candidate.hp()<candidate.maxHp())){reason=tr("sgs.ui.jieyin_requires_another_wounded_male_player_a17d5e20");return;}
                if(meta.target()!=dev.sgs.core.addon.SkillPresentation.Target.NONE && !playSelectionReady())return;
                MatchClient.send(MatchAction.activeSkill(MatchClient.nextNonce(),meta.id(),meta.target()==dev.sgs.core.addon.SkillPresentation.Target.NONE?-1:target,meta.maximumCards()==0?List.of():List.copyOf(selected)));
            });widget.setHeight(rowHeight);if(costSkill.equals(meta.id()))widget.active=playSelectionReady();testButtons.put(meta.id(),addRenderableWidget(widget));boardWidgets.add(widget);
            // Compatibility alias for existing native drivers; points to the same component.
            if(meta.id().equals("rende") || meta.id().equals("paoxiao"))testButtons.put("skill",widget);
        }
    }
    /** Private choices come exclusively from the server-filtered prompt, never another hand. */
    private boolean buildSkillChoices(){
        var prompt=view.snapshot().prompt();var type=PromptType.values()[prompt.typeOrdinal()];
        if(type==PromptType.EXPANSION_CHOICE || type==PromptType.EXPANSION_OPTION){buildExpansionChoices(prompt,type);return true;}
        if(type.supportsHandPositions()){buildZoneChoices(prompt);return true;}
        if(type==PromptType.WEAPON_COST_FIRST || type==PromptType.WEAPON_COST_SECOND){buildWeaponCosts(prompt,type==PromptType.WEAPON_COST_SECOND);return true;}
        if(type==PromptType.FANJIAN_SUIT){
            String[] labels={tr("sgs.ui.spade_7d0b7808"),tr("sgs.ui.heart_84340951"),tr("sgs.ui.club_589660b1"),tr("sgs.ui.diamond_f6346a6b")};
            var lane=decisionLayout().confirm();int left=handLeft(),cell=Math.max(12,(handRight()-left-9)/4);
            for(int i=0;i<4;i++){int suit=i;var b=button("suit:"+i,labels[i],left+i*(cell+3),lane.y(),cell,
                ()->MatchClient.send(MatchAction.skillChoice(MatchClient.nextNonce(),prompt.promptId(),-1,suit)));b.setHeight(lane.height());}
            return true;
        }
        if(!EnumSet.of(PromptType.GUI_CAI_REPLACE,PromptType.YIJI_GIVE,
            PromptType.TUXI_TARGET,PromptType.GANGLIE_DISCARD,PromptType.WEAPON_DISCARD,PromptType.LIJIAN_TARGET,
            PromptType.GUANXING_ORDER,PromptType.LIULI_TARGET).contains(type))return false;
        if(type==PromptType.TUXI_TARGET){
            confirm=decisionButton("tuxi-confirm",tr("sgs.ui.confirm_f526c899"),true,this::submitTuxi);
            confirm.active=zonePendingPrompt!=prompt.promptId();return true;
        }
        if(type==PromptType.GUANXING_ORDER){buildPrivateDeckOrder();return true;}
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
                String label=(target==seat?"✓ ":"")+(seat+1)+tr("sgs.ui.empty_e094da75")+(candidate==null?tr("sgs.ui.player_6b26695e"):candidate.name());
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
                String label=hidden?tr("sgs.ui.random_hand_card_hidden_ee90ecc0"):cardName(card)+" "+suit+Rank.values()[card.rankOrdinal()].label();
                button("skill-choice:"+id,(skillChoiceCard==id?"✓ ":"")+label,handLeft()+i%cols*(cell+2),top+i/cols*22,cell-2,
                    ()->{skillChoiceCard=id;reason="";rebuildWidgets();});
            }
        }
        if(type==PromptType.GUANXING_ORDER){
            int cell=Math.max(12,(handRight()-handLeft()-4)/2),y=decisionLayout().caption().y()-29;
            for(int i=0;i<2;i++){int destination=i;
                button("skill-destination:"+i,(skillChoiceDestination==i?"✓ ":"")+(i==0?tr("sgs.ui.deck_top_a1318bc4"):tr("sgs.ui.deck_bottom_81928186")),handLeft()+i*(cell+4),y,cell,
                    ()->{skillChoiceDestination=destination;reason="";rebuildWidgets();});
            }
        }
        String confirmText=switch(type){
            case YIJI_GIVE->tr("sgs.ui.give_to_selected_player_b5471235");case GUI_CAI_REPLACE->tr("sgs.ui.replace_judgment_card_8883f2b9");
            case WEAPON_DISCARD->tr("sgs.ui.discard_this_hand_card_a759f89a");case TUXI_TARGET->tr("sgs.ui.tuxi_selected_player_41842fef");case GANGLIE_DISCARD->tr("sgs.ui.choose_discard_e71186fe");
            case LIJIAN_TARGET->tr("sgs.ui.confirm_second_male_8b3cc9ac");case GUANXING_ORDER->tr("sgs.ui.confirm_position_8460c062");
            case LIULI_TARGET->tr("sgs.ui.discard_and_redirect_slash_3f532414");default->tr("sgs.ui.take_selected_88381737");
        };
        confirm=decisionButton("skill-choice-confirm",confirmText,true,()->{
            long id=targetOnly?0:ownedPromptChoice()!=null?skillChoiceCard:type==PromptType.GUI_CAI_REPLACE && selected.size()==1?selected.iterator().next():skillChoiceCard;
            if(!(id==0 && prompt.hiddenHandAvailable()) && prompt.choices().stream().noneMatch(c->c.instanceId()==id)){
                reason=type==PromptType.GUI_CAI_REPLACE?tr("sgs.ui.select_one_of_your_hand_cards_562d4d5d"):tr("sgs.ui.select_a_card_allowed_by_this_prompt_7147cc54");return;
            }
            int recipient=type==PromptType.GUANXING_ORDER?skillChoiceDestination:prompt.allowedTargetSeats().isEmpty()?-1:target;
            if(!prompt.allowedTargetSeats().isEmpty() && !prompt.allowedTargetSeats().contains(recipient)){
                reason=type==PromptType.GUANXING_ORDER?tr("sgs.ui.choose_the_top_or_bottom_of_the_deck_4d53dbdf"):tr("sgs.ui.choose_a_target_allowed_by_this_prompt_69122138");return;
            }
            MatchClient.send(MatchAction.skillChoice(MatchClient.nextNonce(),prompt.promptId(),id,recipient));
        });
        var owned=ownedPromptChoice();
        if(owned!=null)confirm.active=(!redirectSelection() || "".equals(targetHint(target))) && owned.allowsCard(skillChoiceCard)
            && (owned.targets().isEmpty() || owned.allowsTarget(target));
        String declineText=switch(type){case WEAPON_DISCARD->tr("sgs.ui.let_target_draw_1_205a1a44");case YIJI_GIVE->tr("sgs.ui.keep_remaining_cards_9ccb1ad3");case GANGLIE_DISCARD->tr("sgs.ui.take_damage_edbfabb7");case GUANXING_ORDER->tr("sgs.ui.return_rest_to_top_in_order_3fff8555");case LIULI_TARGET->tr("sgs.ui.do_not_redirect_b00790be");default->tr("sgs.ui.pass_b8a81b7a");};
        decisionButton("decline",declineText,false,()->MatchClient.send(
            MatchAction.promptResponse(MatchClient.nextNonce(),prompt.promptId(),false,-1,-1)));
        return true;
    }
    public boolean optionalSkillSettingsAvailable(){return view.snapshot().status()==1&&!boardCovered();}
    private boolean tuxiPrompt(){return myPrompt() && view.snapshot().prompt().typeOrdinal()==PromptType.TUXI_TARGET.ordinal();}
    private void submitTuxi(){
        if(!tuxiPrompt())return;var p=view.snapshot().prompt();if(zonePendingPrompt==p.promptId())return;
        zonePendingPrompt=p.promptId();MatchClient.send(MatchAction.skillTargets(MatchClient.nextNonce(),p.promptId(),List.copyOf(attackTargets)));rebuildWidgets();
    }
    private boolean portraitTargetPrompt(){
        if(!myPrompt())return false;
        var p=view.snapshot().prompt();
        return p.typeOrdinal()==PromptType.EXPANSION_CHOICE.ordinal() && p.choices().isEmpty() && !p.allowedTargetSeats().isEmpty();
    }
    private void buildExpansionChoices(PromptView prompt,PromptType type){
        if(portraitTargetPrompt()){
            decisionButton("decline",tr("sgs.ui.cancel_4d0b4688"),false,()->MatchClient.send(MatchAction.promptResponse(MatchClient.nextNonce(),prompt.promptId(),false,-1,-1)));
            return;
        }
        int left=handLeft(),width=Math.max(120,handRight()-left),cols=3,cell=(width-8)/cols;
        int top=decisionLayout().caption().y()-119;
        boolean options=type==PromptType.EXPANSION_OPTION;
        String[] labels=prompt.skillId().split("\\|");
        if(!prompt.choices().isEmpty() && ownedPromptChoice()==null){
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
                case "yinghun"->value==0?tr("sgs.ui.draw_x_discard_1_8b5ad172"):tr("sgs.ui.draw_1_discard_x_11208789");case "benghuai"->value==0?tr("sgs.ui.lose_hp_e34d26a3"):tr("sgs.ui.reduce_max_hp_e8b6ada8");
                case "zhiji"->value==0?tr("sgs.ui.recover_hp_5656d6cb"):tr("sgs.ui.draw_2_cards_1be945be");default->tr("sgs.ui.option_f6998f9e")+(value+1);};
            else {var seat=view.snapshot().seats().stream().filter(s->s.seat()==value).findFirst().orElse(null);label=(value+1)+tr("sgs.ui.empty_e094da75")+(seat==null?tr("sgs.ui.player_6b26695e"):heroName(seat.heroId()));}
            if(options&&prompt.skillId().startsWith("guhuo_declare|")){String declared=label.toLowerCase(java.util.Locale.ROOT);label=art.stream().filter(a->a.id().equals(declared)).map(CardArtCatalog.Art::name).findFirst().orElse(label);}
            button("skill-target:"+value,(target==value?"✓ ":"")+label,left+i%3*(cell+3),top+i/3*22,cell,()->{target=value;rebuildWidgets();});
        }
        decisionButton("skill-choice-confirm",tr("sgs.ui.confirm_f526c899"),true,()->{
            long id=prompt.choices().isEmpty()?0:skillChoiceCard;
            if(!prompt.choices().isEmpty()&&prompt.choices().stream().noneMatch(c->c.instanceId()==id)){reason=tr("sgs.ui.select_cards_for_this_prompt_966b5107");return;}
            int destination=prompt.allowedTargetSeats().isEmpty()?-1:target;
            if(!prompt.allowedTargetSeats().isEmpty()&&!prompt.allowedTargetSeats().contains(destination)){reason=tr("sgs.ui.choose_an_option_for_this_prompt_7a0e5c34");return;}
            MatchClient.send(MatchAction.skillChoice(MatchClient.nextNonce(),prompt.promptId(),id,destination));
        });
        decisionButton("decline",tr("sgs.ui.cancel_4d0b4688"),false,()->MatchClient.send(MatchAction.promptResponse(MatchClient.nextNonce(),prompt.promptId(),false,-1,-1)));
    }
    private SeatView zoneSubject(PromptView prompt){return view.snapshot().seats().stream().filter(seat->seat.seat()==prompt.subjectSeat()).findFirst().orElse(null);}
    private dev.sgs.core.match.dto.MatchViews.DraftView publicDraft(){var d=view.snapshot().tablePresentation().draft();return d!=null&&d.active()&&!d.slots().isEmpty()?d:null;}
    private boolean draftVisible(){return draftPrompt()||publicDraft()!=null;}
    private ReferenceBattleLayout.Box harvestPanel(){
        int count=publicDraft()!=null?publicDraft().slots().size():view.snapshot().prompt().choices().size();int columns=Math.max(1,Math.min(4,count)),rows=Math.max(1,(count+3)/4);
        int w=columns*190+64,h=rows*242+140;
        return board().box((1672-w)/2,(941-h)/2,w,h);
    }
    private void submitHarvest(PromptView prompt,CardView card){
        if(!draftPrompt() || view.snapshot().prompt().promptId()!=prompt.promptId() || harvestPendingPrompt!=0)return;
        harvestPendingPrompt=prompt.promptId();skillChoiceCard=card==null?-1:card.instanceId();
        testButtons.forEach((key,control)->{if(key.startsWith("choice:") || key.equals("decline"))control.active=false;});
        MatchClient.send(MatchAction.promptResponse(MatchClient.nextNonce(),prompt.promptId(),card!=null,card==null?-1:card.instanceId(),-1));
    }
    private void buildHarvestChoices(PromptView prompt){
        var box=harvestPanel();float unit=board().unit();int count=publicDraft()!=null?publicDraft().slots().size():prompt.choices().size();
        int cardW=Math.max(1,Math.round(160*unit)),cardH=Math.max(1,Math.round(224*unit)),step=Math.max(1,Math.round(190*unit));
        for(int i=0;i<count;i++){
            var card=publicDraft()!=null?publicDraft().slots().get(i).card():prompt.choices().get(i);
            if(prompt.choices().stream().noneMatch(c->c.instanceId()==card.instanceId()))continue;
            int columns=Math.min(4,count-i/4*4),column=i%4;
            int x=box.x()+box.width()/2-(columns-1)*step/2-cardW/2+column*step,y=box.y()+Math.round((78+i/4*242)*unit);
            var widget=new ZoneChoiceButton(card,card.instanceId(),i,x,y,cardW,cardH,()->submitHarvest(prompt,card));
            widget.active=harvestPendingPrompt==0;
            testButtons.put("choice:"+prompt.choices().indexOf(card),addRenderableWidget(widget));
        }
        int footerW=Math.max(1,Math.round(220*unit)),footerH=Math.max(12,Math.round(32*unit));
        var automatic=button("decline",tr("sgs.ui.auto_select_9741bc8c"),box.x()+box.width()/2-footerW/2,box.bottom()-Math.round(46*unit),footerW,()->submitHarvest(prompt,null));
        automatic.setHeight(footerH);automatic.active=harvestPendingPrompt==0;
    }
    private void buildWeaponCosts(PromptView prompt,boolean second){
        var cards=prompt.choices();int pages=Math.max(1,(cards.size()+5)/6);skillCostPage=Math.clamp(skillCostPage,0,pages-1);
        int cell=Math.max(12,(handRight()-handLeft()-6)/3),top=decisionLayout().caption().y()-70;
        for(int i=skillCostPage*6;i<Math.min(cards.size(),skillCostPage*6+6);i++){
            var card=cards.get(i);int local=i%6;long id=card.instanceId();
            var control=button("skill-choice:"+id,(skillChoiceCard==id?"✓ ":"")+cardName(card)+" "+Rank.values()[card.rankOrdinal()].label(),handLeft()+local%3*(cell+2),top+local/3*22,cell-2,()->{skillChoiceCard=id;reason="";rebuildWidgets();});
            control.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(cardName(card)+" · "+switch(Suit.values()[card.suitOrdinal()]){case SPADE->tr("sgs.ui.spade_fcb4182d");case HEART->tr("sgs.ui.heart_bee0bce1");case CLUB->tr("sgs.ui.club_ff8ba227");case DIAMOND->tr("sgs.ui.diamond_48ae409d");case NONE->"";}+" "+Rank.values()[card.rankOrdinal()].label())));
        }
        if(pages>1){
            var prev=button("cost-prev","‹",handLeft(),top+45,40,()->{skillCostPage--;rebuildWidgets();});prev.active=skillCostPage>0;
            var next=button("cost-next",(skillCostPage+1)+"/"+pages+" ›",handLeft()+44,top+45,70,()->{skillCostPage++;rebuildWidgets();});next.active=skillCostPage+1<pages;
        }
        decisionButton("skill-choice-confirm",second?tr("sgs.ui.discard_2_to_hit_326b6e36"):tr("sgs.ui.select_first_card_cd2548ee"),true,()->{
            if(cards.stream().noneMatch(c->c.instanceId()==skillChoiceCard)){reason=tr("sgs.ui.choose_a_cost_card_allowed_by_the_server_02c3263b");return;}
            if(weaponCostPendingPrompt==prompt.promptId())return;weaponCostPendingPrompt=prompt.promptId();lockWeaponCosts();
            MatchClient.send(MatchAction.skillChoice(MatchClient.nextNonce(),prompt.promptId(),skillChoiceCard,-1));
        });
        decisionButton("decline",tr("sgs.ui.cancel_keep_cards_3a8f863c"),false,()->{
            if(weaponCostPendingPrompt==prompt.promptId())return;weaponCostPendingPrompt=prompt.promptId();lockWeaponCosts();
            MatchClient.send(MatchAction.promptResponse(MatchClient.nextNonce(),prompt.promptId(),false,-1,-1));
        });
        if(weaponCostPendingPrompt==prompt.promptId())lockWeaponCosts();
    }
    private void lockWeaponCosts(){testButtons.forEach((key,control)->{if(key.startsWith("skill-choice:")||Set.of("skill-choice-confirm","decline","cost-prev","cost-next").contains(key))control.active=false;});}
    private void drawHarvestPanel(GuiGraphics g,PromptView prompt){
        var box=harvestPanel();float unit=board().unit();var draft=publicDraft();
        drawCardStageBackdrop(g,box);
        String title=draft!=null?draft.title():prompt.skillId().equals("zhulu")?tr("sgs.ui.battle_for_supremacy_55ca8566"):tr("sgs.ui.amazing_grace_961b376e");
        int titleW=Math.min(box.width()-20,Math.round(490*unit)),titleH=Math.max(12,Math.round(34*unit));
        int titleX=box.x()+(box.width()-titleW)/2,titleY=box.y()+Math.round(10*unit);
        g.fill(titleX,titleY,titleX+titleW,titleY+titleH,0xe5614525);
        g.renderOutline(titleX,titleY,titleW,titleH,0xff9f7a40);
        InkText.fitted(g,font,title,box.x()+box.width()/2f,box.y()+Math.round(16*unit),box.width()-20,Math.max(10,Math.round(24*unit)),0xffebce92);
        if(draft!=null&&draft.slots().stream().anyMatch(slot->slot.recipient()>=0))captureScene("harvest-recipients");
        String status=draftPrompt()?(harvestPendingPrompt==prompt.promptId()?tr("sgs.ui.awaiting_confirmation_25a45621"):tr("sgs.ui.select_a_card_08ae30ce")):tr("sgs.ui.waiting_for_6c5f9ce8")+sceneName(draft.picker())+tr("sgs.ui.to_choose_b9f5ad13");
        InkText.fitted(g,font,status,box.x()+box.width()/2f,box.y()+Math.round(49*unit),box.width()-20,Math.max(8,Math.round(18*unit)),0xffd2bb92);
        g.pose().pushPose();g.pose().translate(0,0,10);
        if(draft!=null){int count=draft.slots().size(),cw=Math.max(1,Math.round(160*unit)),ch=Math.max(1,Math.round(224*unit)),step=Math.max(1,Math.round(190*unit));
          for(int i=0;i<count;i++){var slot=draft.slots().get(i);int columns=Math.min(4,count-i/4*4),x=box.x()+box.width()/2-(columns-1)*step/2-cw/2+i%4*step,y=box.y()+Math.round((78+i/4*242)*unit);
            drawCard(g,slot.card(),x,y,cw/80f,false);
            if(slot.recipient()>=0){g.fill(x,y,x+cw,y+ch,0xa0000000);var seat=view.snapshot().seats().stream().filter(p->p.seat()==slot.recipient()).findFirst().orElse(null);
              if(seat!=null){var texture=HeroRenderer.classic(seat.heroId());if(texture!=null)HeroRenderer.portraitCrop(g,texture,x+cw/5,y+ch/3,cw*3/5,ch/2);}
              InkText.fitted(g,font,sceneName(slot.recipient()),x+cw/2f,y+ch-20*unit,cw-4,16*unit,0xffffdda0);
            }
          }
        }
        g.pose().popPose();
    }
    /** Shared dark stage under public deals and judgment cards. */
    private void drawCardStageBackdrop(GuiGraphics g,ReferenceBattleLayout.Box box){
        g.fill(box.x(),box.y(),box.right(),box.bottom(),0xe82b1d10);
    }
    private final java.util.Set<String> sceneCaptures=new java.util.HashSet<>();
    private final java.util.List<String> pendingSceneCaptures=new java.util.ArrayList<>();
    private void captureScene(String name){if(Boolean.getBoolean("sgs.matchSmoke")&&sceneCaptures.add(name))pendingSceneCaptures.add(name);}
    private void flushSceneCaptures(GuiGraphics g){
        if(pendingSceneCaptures.isEmpty())return;
        g.flush();
        for(String name:pendingSceneCaptures){net.minecraft.client.Screenshot.grab(minecraft.gameDirectory,"sgs-scene-"+name+".png",minecraft.getMainRenderTarget(),m->{});dev.sgs.validation.ProbeMod.LOG.info("SGS_TABLE_SCENE_CAPTURE {}",name);}
        pendingSceneCaptures.clear();
    }
    private String sceneKey="";private long sceneSeen,sceneStarted,sceneLastFrame;private final java.util.ArrayDeque<dev.sgs.core.match.dto.MatchViews.TableEffect> sceneQueue=new java.util.ArrayDeque<>();
    private dev.sgs.core.match.dto.MatchViews.TableEffect scene;
    private void drawTableEffects(GuiGraphics g){
        var snapshot=view.snapshot();var effects=snapshot.tablePresentation().effects();long now=net.minecraft.Util.getMillis();String key=view.matchId()+":"+view.connectionEpoch();
        if(!sceneKey.equals(key)){sceneKey=key;sceneSeen=effects.stream().mapToLong(e->e.id()).max().orElse(0);sceneQueue.clear();scene=null;}
        for(var event:effects)if(event.id()>sceneSeen){if(!event.kind().equals("CARD_USE"))sceneQueue.addLast(event);sceneSeen=event.id();}
        while(sceneQueue.size()>8)sceneQueue.removeFirst();
        boolean obscured=draftVisible()||zoneModal()||!overlay.isEmpty()||cardsOpen||logOpen||privateDeckVisible();
        sceneLastFrame=now;
        if(scene!=null&&now-sceneStarted>=dev.sgs.core.match.TablePresentationTiming.durationMillis(scene.kind()))scene=null;
        if(scene==null&&!sceneQueue.isEmpty()){scene=sceneQueue.removeFirst();sceneStarted=now;}
        if(scene==null || obscured)return;
        if(scene.kind().equals("JUDGMENT")){drawJudgmentScene(g,now-sceneStarted);return;}
        float unit=board().unit();var box=board().box(480,285,710,350);long elapsed=now-sceneStarted;
        g.pose().pushPose();g.pose().translate(0,0,220);
        g.fill(box.x(),box.y(),box.right(),box.bottom(),0xe0322419);g.renderOutline(box.x(),box.y(),box.width(),box.height(),0xffb18d51);
        boolean duel=scene.kind().equals("PINDIAN");String label=duel?skillName(scene.label())+tr("sgs.ui.pindian_2dde9f37"):switch(scene.label()){case "BAGUA"->tr("sgs.ui.eight_trigrams_3122bd75");case "LEBU"->tr("sgs.ui.indulgence_53028258");case "BINGLIANG"->tr("sgs.ui.supply_shortage_ab4dd157");case "SHANDIAN"->tr("sgs.ui.lightning_796ce9f9");default->tr("sgs.ui.judgment_b6cb2164");};
        InkText.fitted(g,font,label,box.x()+box.width()/2f,box.y()+12*unit,box.width()-16,28*unit,0xfff3d18b);
        int cw=Math.max(1,Math.round(115*unit)),ch=Math.max(1,Math.round(161*unit));
        for(int i=0;i<scene.cards().size();i++){
          int x=box.x()+box.width()/2+Math.round((duel?(i==0?-170:170):0)*unit)-cw/2,y=box.y()+Math.round(65*unit);
          float flip=elapsed<400?0:Math.min(1,(elapsed-400)/420f);float squeeze=Math.max(.025f,Math.abs(1-2*flip));
          g.pose().pushPose();g.pose().translate(x+cw/2f,y,0);g.pose().scale(squeeze,1,1);
          if(flip<.5f)dev.sgs.validation.client.art.CardBackRenderer.draw(g,-cw/2,0,cw,ch);else drawCard(g,scene.cards().get(i),-cw/2,0,cw/80f,false);
          g.pose().popPose();InkText.fitted(g,font,sceneName(i==0?scene.source():scene.target()),x+cw/2f,y+ch+8*unit,180*unit,20*unit,0xffedce92);
        }
        if(elapsed>1100)captureScene(scene.kind().toLowerCase(java.util.Locale.ROOT)+"-result");
        if(elapsed>=950){String result=duel?(scene.success()?sceneName(scene.source())+tr("sgs.ui.wins_pindian_d678d9f1"):tr("sgs.ui.initiator_did_not_win_30d2c9e9")):scene.label().equals("BAGUA")?(scene.success()?tr("sgs.ui.judgment_succeeded_0ce080b3"):tr("sgs.ui.judgment_failed_a6b991be")):(scene.success()?tr("sgs.ui.judgment_takes_effect_e9f9de1e"):tr("sgs.ui.judgment_has_no_effect_9e4e2a04"));
          InkText.fitted(g,font,(scene.success()?"✓ ":"× ")+result,box.x()+box.width()/2f,box.bottom()-55*unit,box.width()-20,32*unit,scene.success()?0xffa8e07a:0xffff7b65);
        }
        g.pose().popPose();
    }
    private void drawJudgmentScene(GuiGraphics g,long elapsed){
        float u=board().unit();var panel=board().box(230,180,1212,430);
        String name=switch(scene.label()){case "SHANDIAN"->tr("sgs.ui.lightning_796ce9f9");case "LEBU"->tr("sgs.ui.indulgence_53028258");case "BINGLIANG"->tr("sgs.ui.supply_shortage_ab4dd157");case "BAGUA"->tr("sgs.ui.eight_trigrams_3122bd75");default->tr("sgs.ui.judgment_b6cb2164");};
        g.pose().pushPose();g.pose().translate(0,0,240);
        drawCardStageBackdrop(g,panel);
        var title=board().box(590,202,490,44);g.fill(title.x(),title.y(),title.right(),title.bottom(),0xe5614525);
        g.renderOutline(title.x(),title.y(),title.width(),title.height(),0xff9f7a40);
        selectionText(g,name,590,207,490,32,0xffffe0a0);
        int cw=Math.max(1,Math.round(150*u)),ch=Math.max(1,Math.round(210*u));
        var origin=board().box(415,285,150,210);var result=board().box(755,285,150,210);
        if(scene.cards().size()>1)drawCard(g,scene.cards().get(1),origin.x(),origin.y(),cw/80f,false);
        drawJudgmentRibbon(g,scene.label().equals("BAGUA")?tr("sgs.ui.equipment_1faf3321"):tr("sgs.ui.delayed_trick_fef65c84"),345,300);
        drawJudgmentRibbon(g,tr("sgs.ui.judgment_result_4b7cb7ac"),685,300);
        float flip=Math.clamp((elapsed-600)/650f,0,1),squeeze=Math.max(.025f,Math.abs(1-2*flip));
        g.pose().pushPose();g.pose().translate(result.x()+cw/2f,result.y(),0);g.pose().scale(squeeze,1,1);
        if(flip<.5f)dev.sgs.validation.client.art.CardBackRenderer.draw(g,-cw/2,0,cw,ch);
        else if(!scene.cards().isEmpty())drawCard(g,scene.cards().getFirst(),-cw/2,0,cw/80f,false);
        g.pose().popPose();
        String ruleKey="sgs.ui.judgment_rule_"+scene.label().toLowerCase(java.util.Locale.ROOT);
        selectionText(g,tr(ruleKey+"_hit"),950,340,425,30,0xfff5dfb7);
        selectionText(g,tr(ruleKey+"_miss"),950,405,425,30,0xfff5dfb7);
        if(elapsed>=1350){
            int color=scene.success()?0xffa9d46a:0xffff6a57;
            selectionText(g,scene.success()?"✓":"×",765,305,130,100,color);
            var seal=board().box(760,421,140,44);g.fill(seal.x(),seal.y(),seal.right(),seal.bottom(),0xd6332118);
            selectionText(g,tr(scene.success()?"sgs.ui.judgment_succeeded_0ce080b3":"sgs.ui.judgment_failed_a6b991be"),760,427,140,28,color);
            captureScene("judgment-result");
        }else if(elapsed<550)captureScene("judgment-cardback");
        var bar=board().box(535,565,600,10);g.fill(bar.x(),bar.y(),bar.right(),bar.bottom(),0xff493320);
        g.fill(bar.x(),bar.y(),bar.x()+Math.round(bar.width()*Math.clamp(1-elapsed/3600f,0,1)),bar.bottom(),0xffdba74c);
        g.pose().popPose();
    }
    private void drawJudgmentRibbon(GuiGraphics g,String label,int x,int y){
        var box=board().box(x,y,48,182);g.fill(box.x(),box.y(),box.right(),box.bottom(),0xd56b4529);
        if(ClientText.english()){selectionText(g,label,x-22,y+65,92,26,0xffffd18b);return;}
        int[] letters=label.codePoints().toArray();
        for(int i=0;i<letters.length;i++)selectionText(g,new String(Character.toChars(letters[i])),x,y+12+i*36,48,30,0xffffd18b);
    }
    private String sceneName(int seat){return view.snapshot().seats().stream().filter(p->p.seat()==seat).map(p->heroName(p.heroId())).findFirst().orElse(tr("sgs.ui.player_6b26695e"));}
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
        var hide=button("zone-hide",tr("sgs.ui.hide_window_15b3146c"),box.right()-pad-hideW,box.y()+Math.max(3,Math.round(8*unit)),hideW,()->{zoneChoiceHidden=true;rebuildWidgets();});
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
                testButtons.put(hidden?"zone-hand:"+i:"skill-choice:"+id,addRenderableWidget(widget));
            }
        }
        int footer=box.bottom()-Math.max(17,Math.round(41*unit)),wide=Math.max(55,Math.round(220*unit));
        confirm=button("skill-choice-confirm",tr("sgs.ui.confirm_selection_fcec8a6d"),box.x()+box.width()/2-wide/2,footer,wide,()->{
            long id=skillChoiceCard;boolean allowed=dev.sgs.core.engine.HandSelection.allowsPosition(PromptType.values()[prompt.typeOrdinal()],prompt.hiddenHandAvailable(),zoneHandCount(prompt),id)
                || id>0 && prompt.choices().stream().anyMatch(card->card.instanceId()==id);
            if(!allowed){reason=tr("sgs.ui.select_a_card_08ae30ce");return;}
            if(zonePendingPrompt==prompt.promptId())return;
            zonePendingPrompt=prompt.promptId();lockZoneButtons();
            MatchClient.send(MatchAction.skillChoice(MatchClient.nextNonce(),prompt.promptId(),id,-1));
        });
        confirm.setHeight(Math.max(12,Math.round(28*unit)));
        if(zonePendingPrompt==prompt.promptId())lockZoneButtons();
    }
    private void lockZoneButtons(){
        testButtons.forEach((key,control)->{if(key.startsWith("zone-") || key.startsWith("skill-choice:") || key.equals("skill-choice-confirm") || key.equals("decline"))control.active=false;});
    }
    public boolean zoneHiddenForSmoke(){return zonePrompt() && zoneChoiceHidden;}
    public long zoneSelectedForSmoke(){return skillChoiceCard;}
    private final class ZoneChoiceButton extends SgsButton {
        private final CardView card;private final long token;private final int faceWidth;
        ZoneChoiceButton(CardView card,long token,int slot,int x,int y,int w,int h,Runnable action){
            super(x,y,w,h,Component.literal(card==null?tr("sgs.ui.hidden_hand_card_00ffb5c9"):cardName(card)),ignored->action.run());this.card=card;this.token=token;this.faceWidth=w;
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
            case "fankui"->tr("sgs.ui.fankui_42a36e94");case "guohe"->tr("sgs.ui.dismantlement_3d6814dd");case "qilin_gong"->tr("sgs.ui.qilin_bow_f3bbf15f");case "hanbing"->tr("sgs.ui.frost_blade_85368acd");case "shunshou"->tr("sgs.ui.snatch_e68f5494");default->skillName(prompt.skillId());
        };
        InkText.fitted(g,font,title,box.x()+box.width()/2f,box.y()+Math.round(13*unit),Math.round(500*unit),Math.max(12,Math.round(22*unit)),0xffebce92);
        var rows=zoneRows(prompt);int rowHeight=(box.height()-Math.round(124*unit))/Math.max(1,rows.size());
        for(int row=0;row<rows.size();row++){
            String label=switch(rows.get(row)){case "equipment"->tr("sgs.ui.equipment_1faf3321");case "judgment"->tr("sgs.ui.judgment_b6cb2164");default->tr("sgs.ui.hand_b2c6b4aa");};
            InkText.fitted(g,font,label,box.x()+box.width()/2f,box.y()+Math.round(57*unit)+row*rowHeight,Math.round(100*unit),Math.max(8,Math.round(15*unit)),0xffd2bb92);
        }
        if(rows.isEmpty())InkText.drawCentered(g,font,tr("sgs.ui.no_selectable_cards_a4260c57"),box.x()+box.width()/2,box.y()+box.height()/2,0xffdac19a);
        if(!reason.isEmpty())InkText.drawCentered(g,font,reason,box.x()+box.width()/2,box.bottom()-Math.round(61*unit),0xffffb08c);
    }
    private long currentWuxiePromptId(){
        var prompt=view.snapshot().prompt();
        return myPrompt() && prompt.typeOrdinal()==PromptType.WUXIE_COUNTER.ordinal()?prompt.promptId():0;
    }
    private void lockWuxieButtons(){
        for(String key:List.of("respond","decline","wuxie-pass-scope")){
            var control=testButtons.get(key);if(control!=null)control.active=false;
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
    private boolean responseSelectionReady(){
        if(!myPrompt() || self()==null)return false;
        var p=view.snapshot().prompt();var cards=self().hand().stream().filter(c->selected.contains(c.instanceId())).toList();
        if(cards.size()==2 && selected.size()==2)return p.allowedTypeOrdinals().contains(CardType.SHA.ordinal()) && self().weapon()!=null && self().weapon().typeOrdinal()==CardType.ZHANGBA.ordinal();
        if(cards.size()!=1 || selected.size()!=1)return false;
        var c=cards.getFirst();var type=PromptType.values()[p.typeOrdinal()];
        if(type==PromptType.HUOGONG_REVEAL || type==PromptType.HUOGONG_PAY)return responseCardSelectable(c);
        return p.allowedTypeOrdinals().contains(declaredType(c).ordinal()) || c.playOptions().stream().anyMatch(o->o.available() && p.allowedTypeOrdinals().contains(o.declaredTypeOrdinal()));
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
                || me.hand().stream().filter(c->selected.contains(c.instanceId())).count()!=2){reason=tr("sgs.ui.serpent_spear_requires_two_of_your_hand_cards_59b5cd48");return;}
            MatchClient.send(MatchAction.promptCardsResponse(MatchClient.nextNonce(),prompt.promptId(),List.copyOf(selected)));return;
        }
        if(accept && selected.size()!=1){reason=tr("sgs.ui.choose_a_response_card_or_two_hand_cards_for_ser_b757dd8d");return;}
        long card=selected.stream().findFirst().orElse(-1L);
        if(accept && card<0){reason=tr("sgs.ui.select_a_response_card_first_65521442");return;}
        int declared=-1;
        if(accept){
            var selectedCard=ownedCards(self()).stream().filter(c->c.instanceId()==card).findFirst().orElse(null);
            if(selectedCard==null){reason=tr("sgs.ui.select_one_of_your_cards_2671521b");return;}
            if(qingguo && self().hand().stream().noneMatch(c->c.instanceId()==selectedCard.instanceId())){reason=tr("sgs.ui.qingguo_requires_a_black_hand_card_equipped_card_8391f2ab");return;}
            boolean fire=prompt.typeOrdinal()==PromptType.HUOGONG_REVEAL.ordinal() || prompt.typeOrdinal()==PromptType.HUOGONG_PAY.ordinal();
            if(fire && self().hand().stream().noneMatch(c->c.instanceId()==selectedCard.instanceId())){reason=tr("sgs.ui.fire_attack_requires_one_of_your_hand_cards_25b33465");return;}
            if(prompt.typeOrdinal()==PromptType.HUOGONG_PAY.ordinal()
                && (prompt.choices().size()!=1 || selectedCard.suitOrdinal()!=prompt.choices().getFirst().suitOrdinal())){reason=tr("sgs.ui.select_a_hand_card_matching_the_revealed_suit_759b91b9");return;}
            declared=fire?selectedCard.typeOrdinal():declaredType(selectedCard).ordinal();
            if(!fire && !prompt.allowedTypeOrdinals().contains(declared) && hasConversion(selectedCard)){openConversion();return;}
            if(!prompt.allowedTypeOrdinals().contains(declared)){reason=tr("sgs.ui.this_card_cannot_respond_directly_you_may_choose_42b65675");return;}
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
    private void clearCardTargets(){target=-1;secondary=-1;attackTargets.clear();targetEffectStarted.clear();}
    private void play(){
        if(!costSkill.isEmpty()){var skill=testButtons.get(costSkill);if(skill!=null && skill.active)skill.onPress();return;}
        if(view.snapshot().counterspellWindow()!=null || !view.snapshot().processing().isEmpty()){reason=tr("sgs.ui.wait_for_the_current_card_to_resolve_95a9cdc0");return;}
        if(selected.size()!=1){reason=tr("sgs.ui.select_a_hand_card_1c7c5985");return;}
        long id=selected.iterator().next();
        var card=ownedCards(self()).stream().filter(c->c.instanceId()==id).findFirst().orElseThrow();
        String unavailable=handCardBlockedReason(card);if(!unavailable.isEmpty()){selected.remove(id);clearCardTargets();return;}
        if(guose && card.suitOrdinal()!=Suit.DIAMOND.ordinal()){reason=tr("sgs.ui.guose_requires_a_diamond_hand_or_equipment_card_3e043358");return;}
        var declared=declaredType(card);
        if(card.playOptions().stream().noneMatch(o->o.available() && o.declaredTypeOrdinal()==declared.ordinal()) && hasConversion(card)){openConversion();return;}
        if(!playSelectionReady())return;
        if(fangtianSelection()){
            var targets=attackTargets.isEmpty() && target>=0?List.of(target):List.copyOf(attackTargets);
            if(targets.isEmpty()){reason=tr("sgs.ui.sky_piercer_choose_1_3_targets_click_again_to_de_2f03d6a7");return;}
            MatchClient.send(MatchAction.playAttackTargets(MatchClient.nextNonce(),id,declared.ordinal(),targets));return;
        }
        MatchClient.send(declared.ordinal()==card.typeOrdinal()?MatchAction.playCard(MatchClient.nextNonce(),id,target,secondary)
            :MatchAction.playCardAs(MatchClient.nextNonce(),id,declared.ordinal(),target,secondary));
    }
    private static final java.util.concurrent.atomic.AtomicLong targetQueries=new java.util.concurrent.atomic.AtomicLong();
    private String targetQueryKey="";
    private long targetQueryId;
    private boolean targetQueryPending;
    private Map<Integer,String> targetChoices=Map.of();
    private CardView selectedPhysical(){return self()==null || selected.size()!=1?null:ownedCards(self()).stream().filter(c->selected.contains(c.instanceId())).findFirst().orElse(null);}
    private boolean targetSelectionContext(){var s=view.snapshot();return self()!=null && s.status()==1 && s.prompt()==null && s.counterspellWindow()==null && s.processing().isEmpty() && s.currentSeat()==s.viewerSeat() && s.phaseOrdinal()==Phase.PLAY.ordinal() && (!costSkill.isEmpty() || selectedPhysical()!=null);}
    private int selectionPrimary(){var c=selectedPhysical();return costSkill.isEmpty() && c!=null && (declaredType(c)==CardType.JIEDAO || declaredType(c)==CardType.TIESUO)?target:-1;}
    private boolean redirectSelection(){return myPrompt() && view.snapshot().prompt().typeOrdinal()==PromptType.LIULI_TARGET.ordinal();}
    private void refreshTargetChoices(){
        if(!targetSelectionContext() && !redirectSelection()){targetQueryKey="";targetChoices=Map.of();targetQueryPending=false;return;}
        var card=selectedPhysical();int declared=card==null?-1:declaredType(card).ordinal();
        String key=view.matchId()+":"+view.connectionEpoch()+":"+view.revision()+":"+costSkill+":"+selected+":"+declared+":"+selectionPrimary();
        if(key.equals(targetQueryKey))return;
        targetQueryKey=key;targetChoices=Map.of();targetQueryPending=true;targetQueryId=targetQueries.incrementAndGet();
        var query=new MatchNetwork.TargetRequest(targetQueryId,view.matchId(),view.revision(),view.connectionEpoch(),costSkill,List.copyOf(selected),declared,selectionPrimary());
        net.blay09.mods.balm.api.Balm.getNetworking().sendToServer(new MatchNetwork.TargetQuery(MatchNetwork.JSON.toJson(query)));
    }
    public void targetReply(MatchNetwork.TargetReply reply){
        if(reply.request()!=targetQueryId || reply.match()!=view.matchId() || reply.revision()!=view.revision())return;
        targetChoices=Map.copyOf(reply.targets());targetQueryPending=false;rebuildWidgets();
    }
    private String targetHint(int seat){
        var snapshot=view.snapshot();
        if(redirectSelection())return targetQueryPending?"TARGET_PENDING":targetChoices.getOrDefault(seat,"TARGET_PROHIBITED");
        if(myPrompt()){
            var p=snapshot.prompt();
            if(!p.allowedTargetSeats().isEmpty())return p.allowedTargetSeats().contains(seat)?"":"TARGET_PROHIBITED";
            return "TARGET_PROHIBITED";
        }
        if(!targetSelectionContext())return "TARGET_PROHIBITED";
        return targetQueryPending?"TARGET_PENDING":targetChoices.getOrDefault(seat,"TARGET_PROHIBITED");
    }
    private boolean playSelectionReady(){
        if(!targetSelectionContext() || targetQueryPending)return false;
        if(!costSkill.isEmpty()){
            var meta=ClientText.heroes(view.snapshot().heroPresentations()).stream().filter(h->h.id().equals(self().heroId())).flatMap(h->h.skills().stream()).filter(m->m.id().equals(costSkill)).findFirst().orElse(null);
            return meta!=null && ("".equals(targetChoices.get(meta.target()==dev.sgs.core.addon.SkillPresentation.Target.NONE?-1:target)));
        }
        var c=selectedPhysical();if(c==null || !handCardBlockedReason(c).isEmpty())return false;
        var type=declaredType(c);
        if(fangtianSelection())return !attackTargets.isEmpty() && attackTargets.stream().allMatch(seat->"".equals(targetChoices.get(seat)));
        if(type==CardType.TIESUO)return target<0?c.typeOrdinal()==CardType.TIESUO.ordinal():"".equals(targetChoices.get(target)) && (secondary<0 || "".equals(targetChoices.get(secondary)));
        if(type==CardType.JIEDAO)return target>=0 && secondary>=0 && "".equals(targetChoices.get(target)) && "".equals(targetChoices.get(secondary));
        if(Set.of(CardType.SHA,CardType.HUOSHA,CardType.LEISHA,CardType.LEBU,CardType.BINGLIANG,CardType.JUEDOU,CardType.GUOHE,CardType.SHUNSHOU,CardType.HUOGONG,CardType.JIEJIA).contains(type))return "".equals(targetChoices.get(target));
        return true;
    }
    private String targetHintText(String code){return switch(code){
        case "OUT_OF_RANGE"->tr("sgs.ui.out_of_range_ff21594f");case "TARGET_SELF"->tr("sgs.ui.you_cannot_target_yourself_59fcc504");case "TARGET_DEAD"->tr("sgs.ui.this_player_is_dead_46ca2cde");
        case "TARGET_NO_CARDS"->tr("sgs.ui.the_target_has_no_selectable_cards_d07a3e72");case "TARGET_PROHIBITED"->tr("sgs.ui.cannot_be_targeted_by_this_card_9c141a4b");default->tr("sgs.ui.this_target_cannot_be_selected_bd137f00");
    };}
    private boolean hasSelfSkill(String id){return self()!=null && ClientText.heroes(view.snapshot().heroPresentations()).stream().filter(h->h.id().equals(self().heroId())).flatMap(h->h.skills().stream()).anyMatch(skill->skill.id().equals(id));}
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
    public String heroName(String id){return ClientText.heroes(view.snapshot().heroPresentations()).stream().filter(h->h.id().equals(id)).map(dev.sgs.core.addon.HeroPresentation::name).findFirst().orElse(id==null || id.isEmpty()?tr("sgs.ui.hero_not_chosen_5bc86f35"):id);}
    private String skillName(String id){return ClientText.heroes(view.snapshot().heroPresentations()).stream().flatMap(h->h.skills().stream()).filter(s->s.id().equals(id)).map(dev.sgs.core.addon.SkillPresentation::name).findFirst().orElse(switch(id){case "guhuo_response"->tr("sgs.ui.guhuo_response_904b5411");case "guhuo_question"->tr("sgs.ui.question_guhuo_b5fcd98d");case "zhiba_gain"->tr("sgs.ui.zhiba_take_pindian_cards_9d11160e");case "zhiba_accept"->tr("sgs.ui.zhiba_accept_pindian_c9d09e31");case "yaowu_recover"->tr("sgs.ui.yaowu_recover_or_draw_7f982e2c");case "bagua"->tr("sgs.ui.eight_trigrams_3122bd75");case "zhuque"->tr("sgs.ui.vermilion_fan_25709d8e");case "qilin_gong"->tr("sgs.ui.qilin_bow_f3bbf15f");case "cixiong"->tr("sgs.ui.twin_swords_d789b183");case "hanbing"->tr("sgs.ui.frost_blade_85368acd");case "guanshi"->tr("sgs.ui.stone_axe_372a5887");case "qinglong"->tr("sgs.ui.green_dragon_blade_9d01f4e0");case "fangtian"->tr("sgs.ui.sky_piercer_c4b95e9c");default->id;});}
    private String cardName(CardView card){
        String id=CardType.values()[card.typeOrdinal()].id();
        return art.stream().filter(a->a.id().equals(id)).map(CardArtCatalog.Art::name).findFirst()
            .orElseGet(()->Component.translatable("sgs.card."+id).getString());
    }
    private List<Component> journalRows(List<String> entries){
        return entries.stream().filter(line->JournalText.involves(line,logSeat)).map(line->JournalText.render(line,
            id->art.stream().filter(a->a.id().equals(id)).map(CardArtCatalog.Art::name).findFirst().orElseGet(()->Component.translatable("sgs.card."+id).getString()),this::skillName)).toList();
    }
    private int logPanelWidth(){return Math.min(width-24,Math.max(280,(int)(width*.37)));}
    private int logPanelHeight(){return Math.min(height-24,Math.max(180,(int)(height*.78)));}
    private int logLeft(){return journalX<0?width-logPanelWidth()-16:Math.clamp(journalX,0,Math.max(0,width-logPanelWidth()));}
    private int logTop(){return journalY<0?height-logPanelHeight()-12:Math.clamp(journalY,0,Math.max(0,height-logPanelHeight()));}
    private int logTextLeft(){return logLeft()+9;}
    private int logTextTop(){return logTop()+60;}
    private int logViewportHeight(){return Math.max(12,logPanelHeight()-79);}
    private int logViewportWidth(){return logPanelWidth()-27;}
    private int logTabWidth(){return Math.min(28,(logPanelWidth()-18)/(view.snapshot().seats().size()+1));}
    void openJournalForSmoke(){logOpen=true;overlay="";logSeat=-1;logFollowLatest=true;rebuildWidgets();}
    void filterJournalForSmoke(int seat){mouseClicked(logLeft()+8+(seat+1.5)*logTabWidth(),logTop()+36,0);if(logSeat!=seat)throw new IllegalStateException("journal tab click failed");}
    void scrollJournalForSmoke(){
        mouseScrolled(logTextLeft()+20,logTextTop()+20,0,2);
        if(logFollowLatest||logScrollY>=logMaxY)throw new IllegalStateException("journal wheel failed");
        int x=logLeft()+logPanelWidth()-9;
        mouseClicked(x,logTextTop()+2,0);mouseDragged(x,logTextTop()+logViewportHeight()/2d,0,0,20);mouseReleased(x,logTextTop()+logViewportHeight()/2d,0);
        if(logDragging!=0||logScrollY<=0||logScrollY>=logMaxY)throw new IllegalStateException("journal drag failed");
    }
    private double logThumbSize(boolean horizontal){
        double track=horizontal?logViewportWidth():logViewportHeight(),max=horizontal?logMaxX:logMaxY;
        return Math.min(track,Math.max(18,track*track/(track+max)));
    }
    private double logThumbStart(boolean horizontal){
        double track=horizontal?logViewportWidth():logViewportHeight(),max=horizontal?logMaxX:logMaxY;
        return (horizontal?logTextLeft():logTextTop())+(max<=0?0:(track-logThumbSize(horizontal))*(horizontal?logScrollX:logScrollY)/max);
    }
    private void dragJournal(double coordinate){
        boolean horizontal=logDragging==2;
        double track=horizontal?logViewportWidth():logViewportHeight(),max=horizontal?logMaxX:logMaxY;
        double value=Math.clamp((coordinate-(horizontal?logTextLeft():logTextTop())-logDragOffset)/Math.max(1,track-logThumbSize(horizontal))*max,0,max);
        if(horizontal)logScrollX=value;else{logScrollY=value;logFollowLatest=logScrollY>=logMaxY-1;}
    }
    private void text(GuiGraphics g,String value,int x,int y,int color,int limit){g.drawString(font,font.plainSubstrByWidth(value,Math.max(1,limit)),x,y,color,false);}
    private long completedRenderCount;
    private boolean skinNameHovered;
    private String renderedSelfName="";
    public String renderedSelfNameForSmoke(){return renderedSelfName;}
    public long completedRenderCountForSmoke(){return completedRenderCount;}
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
        if(smokePointer!=null){mx=smokePointer[0];my=smokePointer[1];}
        skinNameHovered=skinStripAvailable() && overlay.isEmpty() && !cardsOpen && !logOpen && skinStrip().contains(mx,my);
        CardRenderer.background(g,width,height);var s=view.snapshot();
        if(s.status()==2 && !cardsOpen && !logOpen){
            handHits.clear();equipmentHits.clear();hoverCard=-1;
            // Let the final blow identify its victim before the result replaces the board.
            if(damageFeedback.active(net.minecraft.Util.getMillis())){drawSeats(g,s);return;}
            g.pose().pushPose();drawResult(g,s);g.pose().popPose();
            if(s.prompt()!=null && s.prompt().typeOrdinal()==PromptType.DUEL_SHA.ordinal()){
            String duel="决斗："+sceneName(s.prompt().askerSeat())+" ↔ "+sceneName(s.prompt().subjectSeat());
            g.drawCenteredString(font,duel,width/2,36,0xffffd779);
            for(int participant:new int[]{s.prompt().askerSeat(),s.prompt().subjectSeat()}){
                var r=seatHits.get(participant);if(r!=null)g.renderOutline(r.x(),r.y(),r.w(),r.h(),0xffffd779);
            }
        }
        if(!boardCovered())for(var seat:s.seats()){
            var r=seatHits.get(seat.seat());
            if(!seat.self() && r!=null && r.contains(mx,my)){
                var b=heroModeButton(r);g.fill(b.x(),b.y(),b.x()+b.w(),b.y()+b.h(),0xff49321b);
                text(g,heroJournalSeats.contains(seat.seat())?"记录":"技能",b.x()+3,b.y()+2,0xffeddbb1,b.w()-4);
            }
        }
        drawTargetArrows(g);
        drawBoardWidgets(g,mx,my,partial);
        if(socialPaletteVisible())drawSocialPalette(g);
        if(!overlay.isEmpty())drawOverlay(g);
            g.pose().pushPose();g.pose().translate(0,0,!overlay.isEmpty()?360:310);
            drawForegroundWidgets(g,mx,my,partial);g.pose().popPose();chatPanel.render(g,mx,my,partial,board());flushSceneCaptures(g);return;
        }
        g.renderOutline(1,1,width-2,height-2,0xff8b663c);
        previewedSeat=-1;
        int header=Math.max(15,Math.round(55*board().unit()));g.fill(0,0,width,header,0xee36271d);g.hLine(0,width,header,0xffaa8247);g.hLine(0,width,header+2,0xff664627);
        String phase=s.phaseOrdinal()>=0 && s.phaseOrdinal()<Phase.values().length?switch(Phase.values()[s.phaseOrdinal()]){
            case NONE->tr("sgs.ui.resolution_4c506e4e");case PREPARE->tr("sgs.ui.ready_cc3ce6df");case JUDGMENT->tr("sgs.ui.judgment_b6cb2164");case DRAW->tr("sgs.ui.draw_0c8a6e06");case PLAY->tr("sgs.ui.play_4d099fcf");case DISCARD->tr("sgs.ui.discard_0b398b44");case END->tr("sgs.ui.finish_76b98808");}:tr("sgs.ui.ready_cc3ce6df");
        int members=s.seats().size();String mode=members==4?tr("sgs.ui.2v2_match_c0065ed2"):members==8?tr("sgs.ui.8_player_identity_5bb21dbd"):tr("sgs.ui.6_player_identity_39b2e48d");
        var headerBox=board().box(137,8,540,39);g.renderOutline(headerBox.x(),headerBox.y(),headerBox.width(),headerBox.height(),0xff896239);
        String[] headFields={mode,tr("sgs.ui.players_3cdf2868")+members+"/"+members,tr("sgs.ui.room_id_5ecfe844")+view.roomId()};int[] headX={178,365,516};
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
            String prompt=myPrompt()?(s.prompt().typeOrdinal()==PromptType.SKILL_INVOKE.ordinal()?s.prompt().skillId().equals("yaowu_recover")?tr("sgs.ui.yaowu_recover_hp_or_draw_a_card_d4430556"):tr("sgs.ui.activate_2e068093")+skillName(s.prompt().skillId())+"？":portraitTargetPrompt()?skillName(s.prompt().skillId())+"："+tr("sgs.ui.choose_a_skill_target_6285de28"):tr("sgs.ui.respond_c901c92f")+promptName(s.prompt().typeOrdinal()))
                :"";
            if(fangtianSelection())prompt=tr("sgs.ui.sky_piercer_b9043625")+attackTargets.size()+tr("sgs.ui.3_targets_click_again_to_deselect_4870083f");
            if(wuxiePending())prompt="";
            boolean chainCard=self()!=null && selected.size()==1 && self().hand().stream().anyMatch(c->selected.contains(c.instanceId()) && c.typeOrdinal()==CardType.TIESUO.ordinal());
            if(chainCard && !myPrompt() && s.counterspellWindow()==null && s.processing().isEmpty()){
                prompt=tr("sgs.ui.iron_chain_choose_1_2_targets_or_none_to_recast_0b38ba42");
                if(confirm!=null)confirm.setMessage(Component.literal(target<0?tr("sgs.ui.recast_selected_7869bac2"):tr("sgs.ui.play_iron_chain_8fd6eb9e")));
            }else if(confirm!=null && !myPrompt() && s.counterspellWindow()==null && s.processing().isEmpty() && s.phaseOrdinal()==Phase.PLAY.ordinal())confirm.setMessage(Component.literal(tr("sgs.ui.confirm_f526c899")));
            if(s.prompt()!=null && s.prompt().typeOrdinal()==PromptType.HUOGONG_PAY.ordinal() && !s.prompt().choices().isEmpty()){
                var revealed=s.prompt().choices().getFirst();
                String suit=switch(Suit.values()[revealed.suitOrdinal()]){case SPADE->"♠";case CLUB->"♣";case HEART->"♥";case DIAMOND->"♦";default->"";};
                prompt=myPrompt()?tr("sgs.ui.fire_attack_discard_9a38d8fa")+suit+tr("sgs.ui.hand_card_541ea0df")+cardName(revealed):tr("sgs.ui.fire_attack_reveal_138f6b93")+suit+" "+cardName(revealed);
            }
            boolean ownDecision=myPrompt() || s.prompt()==null && s.counterspellWindow()==null
                && s.processing().isEmpty() && s.currentSeat()==s.viewerSeat();
            if(s.status()==1 && ownDecision && (self()==null || !self().control().automatic()))drawDecisionTimer(g,prompt,deadline);
            if(myPrompt() && s.prompt().typeOrdinal()==PromptType.GUI_CAI_REPLACE.ordinal() && !s.processing().isEmpty()){
                var current=s.processing().getLast();
                text(g,tr("sgs.ui.current_judgment_06111a0e")+cardName(current)+" · "+Rank.values()[current.rankOrdinal()].label(),handLeft(),decisionLayout().caption().y()-14,0xffe8bc86,handRight()-handLeft());
            }
            if(s.status()==1){drawPublicCards(g,s,mx,my);drawPublicReveals(g,s);motionFrame=CardMotionRenderer.draw(g,cardMotions,net.minecraft.Util.getMillis(),this::motionAnchor,(card,x,y,scale)->drawCard(g,card,x,y,scale,false),event->!publicCards.replaces(event));drawSkillAnnouncement(g,s);}
            if(s.status()==1 && !reason.isEmpty()){
                g.pose().pushPose();g.pose().translate(0,0,300);
                int errorY=decisionLayout().caption().y()-15;
                g.fill(handLeft()-2,errorY-2,handRight()+2,errorY+10,0xf03b271d);
                text(g,reason,handLeft(),errorY,0xffb18b,handRight()-handLeft());g.pose().popPose();
            }

        }
        if(s.prompt()!=null && s.prompt().typeOrdinal()==PromptType.DUEL_SHA.ordinal()){
            String duel="决斗："+sceneName(s.prompt().askerSeat())+" ↔ "+sceneName(s.prompt().subjectSeat());
            g.drawCenteredString(font,duel,width/2,36,0xffffd779);
            for(int participant:new int[]{s.prompt().askerSeat(),s.prompt().subjectSeat()}){
                var r=seatHits.get(participant);if(r!=null)g.renderOutline(r.x(),r.y(),r.w(),r.h(),0xffffd779);
            }
        }
        if(!boardCovered())for(var seat:s.seats()){
            var r=seatHits.get(seat.seat());
            if(!seat.self() && r!=null && r.contains(mx,my)){
                var b=heroModeButton(r);g.fill(b.x(),b.y(),b.x()+b.w(),b.y()+b.h(),0xff49321b);
                text(g,heroJournalSeats.contains(seat.seat())?"记录":"技能",b.x()+3,b.y()+2,0xffeddbb1,b.w()-4);
            }
        }
        drawTargetArrows(g);
        drawBoardWidgets(g,mx,my,partial);
        if(cardsOpen){
            g.pose().pushPose();g.pose().translate(0,0,250);
            g.fill(18,35,width-18,height-14,0xffaa8047);g.fill(20,37,width-20,height-16,0xff33251b);
            text(g,tr("sgs.ui.public_cards_server_state_dfee46be"),30,44,0xffead1a2,width-60);
            drawPublicCardPanel(g,s);g.pose().popPose();
        }
        else if(logOpen){
            g.pose().pushPose();g.pose().translate(0,0,250);
            int left=logLeft(),top=logTop(),right=left+logPanelWidth(),bottom=top+logPanelHeight(),vh=logViewportHeight(),vw=logViewportWidth(),tx=logTextLeft(),ty=logTextTop();
            var logs=journalRows(s.logLines());
            logMaxY=Math.max(0,logs.size()*14-vh);
            logMaxX=Math.max(0,Math.ceil(logs.stream().mapToInt(font::width).max().orElse(0)*1.1)-vw);
            logScrollY=logFollowLatest?logMaxY:Math.clamp(logScrollY,0,logMaxY);
            logScrollX=Math.clamp(logScrollX,0,logMaxX);
            g.fill(left,top,right,bottom,0xe523170b);
            g.fillGradient(left,top,right,top+59,0xef49321b,0xc32d1f0e);
            g.renderOutline(left,top,logPanelWidth(),logPanelHeight(),0xff806139);
            InkText.fitted(g,font,tr("sgs.ui.match_log_7f513661"),left+logPanelWidth()/2f,top+3,logPanelWidth()-60,16,0xffeddbb1);
            int tabW=logTabWidth();
            for(int tab=0;tab<=s.seats().size();tab++){
                int x=left+8+tab*tabW,y=top+23,bw=tabW-3;
                var seat=tab==0?null:s.seats().get(tab-1);boolean active=seat==null?logSeat<0:seat.seat()==logSeat;
                g.fillGradient(x,y,x+bw,y+32,active?0xffc99e55:0xff544027,active?0xffa77c35:0xff382712);
                g.renderOutline(x,y,bw,32,active?0xffe8c878:0xff735735);
                if(active)for(int j=0;j<3;j++)g.fill(x+bw/2-3+j,y+32+j,x+bw/2+3-j,y+33+j,0xffe8c878);
                if(seat==null && ClientText.english()){InkText.fitted(g,font,ClientText.category("全部"),x+bw/2f,y+10,bw-4,13,active?0xff39230e:0xffdfc69e);}
                else if(seat==null){InkText.drawCentered(g,font,"全",x+bw/2,y+5,active?0xff39230e:0xffdfc69e);InkText.drawCentered(g,font,"部",x+bw/2,y+17,active?0xff39230e:0xffdfc69e);}
                else{
                    int size=Math.min(19,bw-4);var portrait=HeroRenderer.classic(seat.heroId());
                    var presentation=ClientText.heroes(s.heroPresentations()).stream().filter(h->h.id().equals(seat.heroId())).findFirst().orElse(null);
                    if(presentation!=null&&!presentation.portrait().isEmpty())portrait=net.minecraft.resources.ResourceLocation.tryParse(presentation.portrait());
                    portrait=HeroSkins.syncedTexture(seat.heroId(),view.skins().getOrDefault(seat.seat(),"classic"),portrait);
                    if(portrait!=null)HeroRenderer.portraitCrop(g,portrait,x+(bw-size)/2,y+2,size,size);
                    String[] numerals={"一","二","三","四","五","六","七","八"};
                    InkText.drawCentered(g,font,!ClientText.english() && seat.seat()<8?numerals[seat.seat()]:Integer.toString(seat.seat()+1),x+bw/2,y+23,active?0xff39230e:0xffdfc69e);
                }
            }
            g.enableScissor(tx,ty,tx+vw,ty+vh);
            int start=Math.max(0,(int)logScrollY/14),end=Math.min(logs.size(),(int)Math.ceil((logScrollY+vh)/14d));
            for(int i=start;i<end;i++){
                g.pose().pushPose();g.pose().translate(tx-(int)logScrollX,ty+i*14-(int)logScrollY+2,0);g.pose().scale(1.1f,1.1f,1);
                g.drawString(font,logs.get(i),0,0,0xffb98c66,false);g.pose().popPose();
            }
            if(logs.isEmpty())g.drawString(font,tr("sgs.ui.no_records_4f9ba235"),tx,ty,0xffb98c66,false);
            g.disableScissor();
            int barX=right-10;
            g.fill(barX,ty,barX+3,ty+vh,0x9021180f);
            int thumbY=(int)logThumbStart(false);
            g.fill(barX,thumbY,barX+3,thumbY+(int)logThumbSize(false),0xffe9d6a5);
            if(logMaxX>0){int barY=ty+vh+3,thumbX=(int)logThumbStart(true);
                g.fill(tx,barY,tx+vw,barY+3,0x9021180f);
                g.fill(thumbX,barY,thumbX+(int)logThumbSize(true),barY+3,0xffe9d6a5);
            }
            g.pose().popPose();
        }
        else if(privateDeckVisible()){g.pose().pushPose();g.pose().translate(0,0,250);drawPrivateDeckOrder(g);g.pose().popPose();}
        else if(zoneModal()){
            g.pose().pushPose();g.pose().translate(0,0,250);drawZoneChoicePanel(g,s);g.pose().popPose();
        }
        else if(draftVisible()){
            g.pose().pushPose();g.pose().translate(0,0,250);
            drawHarvestPanel(g,s.prompt());
            g.pose().popPose();
        }
        drawTableEffects(g);
        if(socialPaletteVisible())drawSocialPalette(g);
        if(!overlay.isEmpty())drawOverlay(g);
        g.pose().pushPose();if(!overlay.isEmpty() || cardsOpen || logOpen || privateDeckVisible() || draftPrompt() || zoneModal() || socialPaletteVisible())g.pose().translate(0,0,!overlay.isEmpty()?360:260);
        drawForegroundWidgets(g,mx,my,partial);
        if(privateDeckVisible()&&deckDragged>=0&&deckDidDrag){
            var b=deckBox();var card=view.snapshot().prompt().choices().stream().filter(c->c.instanceId()==deckDragged).findFirst().orElse(null);
            if(card!=null){g.pose().pushPose();g.pose().translate(0,0,400);
                drawCard(g,card,mx-Math.round(40*b.scale()),my-Math.round(56*b.scale()),b.scale(),false);g.pose().popPose();}
        }

        g.pose().popPose();
        drawAutoplayVeil(g);
        giftAnimations.render(g,font,this::giftAnchor,board().unit());
        emoteAnimations.render(g,this::giftAnchor,board().unit());
        drawIdentityPicker(g,mx,my);
        chatPanel.render(g,mx,my,partial,board());
        flushSceneCaptures(g);
        completedRenderCount++;
        if(MatchChatClient.open)return;
        if(identityPickerSeat>=0 || autoplayVeilVisible())return;
        if(!overlay.isEmpty() || cardsOpen || logOpen || privateDeckVisible() || draftPrompt() || zonePrompt() || s.identityIntro()!=null || s.heroBan()!=null || s.openingHand()!=null)return;
        if(fixtureCount>0)text(g,"布局样本 · "+fixtureCount+"张（仅客户端验图）",handLeft(),height-7-Math.round(112*handScale())-selectedLift()-12,0xffffe5a3,width-150);
        if(portraitFixture)text(g,"布局样本 · 装备／判定／状态（非真实对局效果）",handLeft(),board().hand().y()-15,0xffffe5a3,width-150);
        if(skinStripAvailable() && skinStrip().contains(mx,my))return;
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
                if(!seat.self() && heroModeButton(rect).contains(mx,my))return;
                if(heroJournalSeats.contains(seat.seat())){
                    var lines=s.logLines().stream().filter(line->JournalText.involves(line,seat.seat())).toList();
                    String body=lines.stream().skip(Math.max(0,lines.size()-12)).map(line->JournalText.render(line,id->Component.translatable("sgs.card."+id).getString(),this::skillName).getString()).collect(java.util.stream.Collectors.joining("\n"));
                    paperTip(g,heroName(seat.heroId())+" · 牌局记录\n"+(body.isEmpty()?"暂无记录":body),mx,my);
                }else heroDetails(g,seat,mx,my);
                break;
            }
        }
    }
    private void drawTargetArrows(GuiGraphics g){
        if(boardCovered())return;
        var snap=view.snapshot();int source=snap.viewerSeat();
        var destinations=new java.util.LinkedHashSet<Integer>();
        if(target>=0)destinations.add(target);if(secondary>=0)destinations.add(secondary);destinations.addAll(attackTargets);
        var receipts=snap.tablePresentation().effects().stream().filter(e->e.kind().equals("CARD_USE") && (e.cards().stream().anyMatch(c->snap.processing().stream().anyMatch(p->p.instanceId()==c.instanceId())) || e.cards().isEmpty() && snap.prompt()!=null && snap.prompt().typeOrdinal()==PromptType.DUEL_SHA.ordinal())).toList();
        if(!receipts.isEmpty()){
            var latest=receipts.getLast();source=latest.source();destinations.clear();
            for(var e:receipts)if(e.source()==source && e.label().equals(latest.label()) && e.cards().equals(latest.cards()))destinations.add(e.target());
            if(snap.counterspellWindow()!=null){destinations.clear();destinations.add(snap.counterspellWindow().subjectSeat());}
            String names=destinations.stream().map(this::sceneName).collect(java.util.stream.Collectors.joining("、"));
            g.drawCenteredString(font,sceneName(source)+" → "+names+" · "+Component.translatable("sgs.card."+latest.label()).getString(),width/2,decisionLayout().caption().y()-14,0xffffd779);
        }else if(snap.prompt()!=null){
            destinations.clear();
            var kind=PromptType.values()[snap.prompt().typeOrdinal()];
            if(kind==PromptType.DUEL_SHA || kind==PromptType.DODGE_SHA || kind==PromptType.RESPOND_SHA_NANMAN){source=snap.prompt().subjectSeat();destinations.add(snap.prompt().askerSeat());}
        }else if(snap.counterspellWindow()!=null){destinations.clear();}
        var from=seatHits.get(source);if(from==null)return;
        for(int destination:destinations){var to=seatHits.get(destination);if(to==null || destination==source)continue;
            double ax=from.x()+from.w()/2d,ay=from.y()+from.h()/2d,bx=to.x()+to.w()/2d,by=to.y()+to.h()/2d;
            double dx=bx-ax,dy=by-ay,length=Math.hypot(dx,dy);if(length<1)continue;
            double start=1/Math.max(Math.abs(dx)/(from.w()/2d),Math.abs(dy)/(from.h()/2d));
            double end=1-1/Math.max(Math.abs(dx)/(to.w()/2d),Math.abs(dy)/(to.h()/2d));
            ax+=dx*start;ay+=dy*start;bx=from.x()+from.w()/2d+dx*end;by=from.y()+from.h()/2d+dy*end;
            g.pose().pushPose();g.pose().translate(ax,ay,220);g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees((float)Math.toDegrees(Math.atan2(dy,dx))));
            int n=(int)Math.hypot(bx-ax,by-ay);g.fill(0,-3,n,4,0x554a260a);g.fill(0,-1,n,2,0xffffcf68);
            for(int i=0;i<9;i++)g.fill(n-i,-i/2-1,n-i+1,i/2+2,0xffffdc82);
            g.pose().popPose();
        }
    }
    private void heroDetails(GuiGraphics g,SeatView seat,int mx,int my){
        var hero=ClientText.heroes(view.snapshot().heroPresentations()).stream().filter(h->h.id().equals(seat.heroId())).findFirst().orElse(null);
        if(hero==null){paperTip(g,heroName(seat.heroId()),mx,my);return;}
        var style=FactionStyle.of(seat.kingdomOrdinal());
        String faction=seat.kingdomOrdinal()<0?"":switch(dev.sgs.core.Kingdom.values()[seat.kingdomOrdinal()]){case SHU->ClientText.category("蜀");case WEI->ClientText.category("魏");case WU->ClientText.category("吴");case QUN->ClientText.category("群");};
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
        text(g,tr("sgs.ui.hp_d0fb23cf")+seat.hp()+" / "+seat.maxHp()+(seat.chained()?tr("sgs.ui.chained_907053ed"):"")+(!seat.alive()?tr("sgs.ui.dead_52ddb9ce"):""),tx+8,28,0xffd9c5a3,textW-16);
        int row=40;
        for(var line:biography){g.drawString(font,line,tx+8,row,0xffcbb898,false);row+=11;}
        if(!biography.isEmpty())row+=6;
        for(int i=0;i<hero.skills().size();i++){
            var skill=hero.skills().get(i);
            String kind=skill.lordOnly()?tr("sgs.ui.lord_skill_4b4d0a0f"):switch(skill.kind()){case ACTIVE->tr("sgs.ui.active_3ba4d7bd");case TRIGGERED->tr("sgs.ui.triggered_27eccf97");case PASSIVE->tr("sgs.ui.passive_7992277d");case TOGGLE->tr("sgs.ui.conversion_bbcdf72b");case LIMITED->tr("sgs.ui.limited_70c31580");};
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
                card.active=!identityPending;testButtons.put("identity-back:"+slot,addRenderableWidget(card));
            }
        }else if(intro.stage()==1){
            var box=board().box(740,673,192,47);
            var confirm=button("identity-confirm",tr("sgs.ui.confirm_identity_8c27bfd8"),box.x(),box.y(),box.width(),()->{if(identityPending || !identityRevealReadyForSmoke())return;identityPending=true;
                MatchClient.send(MatchAction.confirmIdentity(MatchClient.nextNonce()));rebuildWidgets();});confirm.setHeight(box.height());
            confirm.active=!identityPending && self()!=null && self().identityOrdinal()>=0 && identityRevealReadyForSmoke();
        }
    }
    private final class IdentityBackButton extends SgsButton {
        private final int slot;
        IdentityBackButton(int slot,ReferenceBattleLayout.Box box,Runnable run){
            super(box.x(),box.y(),box.width(),box.height(),Component.literal(tr("sgs.ui.draw_identity_7d3a510a")),ignored->run.run());this.slot=slot;
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
        selectionText(g,intro.stage()==0?tr("sgs.ui.draw_identity_7d3a510a"):tr("sgs.ui.your_identity_4aceb382"),470,181,732,36,0xfff0ce8b);
        if(intro.stage()==0){
            selectionText(g,identityPending?tr("sgs.ui.drawing_c3e59c0a"):tr("sgs.ui.click_to_draw_identity_a3861bba"),420,695,832,24,0xffe4c79a);
        }else{
            var me=self();int ordinal=me==null?-1:me.identityOrdinal();
            String role=tr("sgs.ui.syncing_identity_33a0529b"),goal=tr("sgs.ui.waiting_for_your_identity_from_the_server_bce5682d");
            if(ordinal>=0 && ordinal<dev.sgs.core.Identity.values().length){
                var identity=dev.sgs.core.Identity.values()[ordinal];
                role=switch(identity){case LORD->tr("sgs.ui.lord_70368eaf");case LOYALIST->tr("sgs.ui.loyalist_83bc6bab");case REBEL->tr("sgs.ui.rebel_487c2834");case RENEGADE->tr("sgs.ui.renegade_c5344088");};
                goal=switch(identity){case LORD->tr("sgs.ui.eliminate_all_rebels_and_renegades_to_win_32cce349");case LOYALIST->tr("sgs.ui.protect_the_lord_and_eliminate_all_rebels_and_re_629fc663");case REBEL->tr("sgs.ui.defeat_the_lord_to_win_d6c1fe7c");case RENEGADE->tr("sgs.ui.eliminate_the_others_then_defeat_the_lord_as_the_3c1fb1c6");};
            }
            var paper=board().box(498,279,224,294);
            float reveal=identityRevealStarted<0?1:Math.clamp((net.minecraft.Util.getMillis()-identityRevealStarted)/420f,0,1);
            float flip=Math.max(.035f,Math.abs((float)Math.cos(Math.PI*reveal)));
            g.pose().pushPose();g.pose().translate(paper.x()+paper.width()/2f,paper.y(),0);g.pose().scale(flip,1,1);
            drawIdentityCard(g,reveal<.5f || ordinal<0?"?":role.substring(0,1),-paper.width()/2,0,paper.width(),paper.height());
            g.pose().popPose();
            var confirm=testButtons.get("identity-confirm");
            if(confirm!=null)confirm.active=!identityPending && ordinal>=0 && reveal>=1;
            selectionText(g,tr("sgs.ui.your_identity_57da2105")+role,778,328,465,36,0xffffd78f);
            selectionText(g,goal,770,405,472,54,0xffecd6ae);
            if(intro.stage()==2)selectionText(g,tr("sgs.ui.identity_confirmed_waiting_for_others_d35fb08d"),470,679,732,31,0xfff0ce8b);
            else if(identityPending)selectionText(g,tr("sgs.ui.waiting_for_server_f9a51d77"),470,730,732,23,0xffd9bd8f);
        }
        if(!reason.isEmpty())selectionText(g,reason,385,724,902,24,0xffffa991);
        var bar=board().box(525,757,623,11);long now=System.currentTimeMillis();
        if(intro.deadlineMillis()!=countdownDeadline){countdownDeadline=intro.deadlineMillis();countdownObservedAt=now;}
        float fraction=Math.clamp((intro.deadlineMillis()-now)/(float)Math.max(1,intro.deadlineMillis()-countdownObservedAt),0,1);
        g.fill(bar.x(),bar.y(),bar.right(),bar.bottom(),0xff4b3523);g.fill(bar.x(),bar.y(),bar.x()+Math.round(bar.width()*fraction),bar.bottom(),0xffd8ab64);
    }
    private void drawIdentitySeatBack(GuiGraphics g,ReferenceBattleLayout.Box box){
        g.fill(box.x(),box.y(),box.right(),box.bottom(),0xff4c3522);g.renderOutline(box.x(),box.y(),box.width(),box.height(),0xff9d783f);
        InkText.fitted(g,font,ClientText.english()?"Hero":"将",box.x()+box.width()/2f,box.y()+box.height()*.4f,box.width()*.65f,box.height()*.23f,0xffcda361);
    }
    private void drawIdentityCard(GuiGraphics g,String label,int x,int y,int w,int h){
        BoardSkin.nine(g,BoardSkin.PAPER,x,y,w,h,192,64,8);
        int size=Math.max(8,Math.round(w*.68f));
        IdentitySeal.drawCard(g,font,label,x+(w-size)/2,y+(h-size)/2,size);
    }
    public boolean identityRevealReadyForSmoke(){
        return identityRevealStarted<0 || net.minecraft.Util.getMillis()-identityRevealStarted>=420;
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
        var replace=button("opening-replace",tr("sgs.ui.redraw_425daea0"),replaceBox.x(),replaceBox.y(),replaceBox.width(),()->decideOpening(true));replace.setHeight(replaceBox.height());
        var keep=button("opening-keep",tr("sgs.ui.cancel_4d0b4688"),keepBox.x(),keepBox.y(),keepBox.width(),()->decideOpening(false));keep.setHeight(keepBox.height());
        replace.active=keep.active=!openingPending;
        replace.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(tr("sgs.ui.redraw_your_entire_opening_hand_once_for_free_ha_9fcb260b"))));
        keep.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(tr("sgs.ui.keep_your_opening_hand_a_timeout_also_keeps_it_be5fa7cb"))));
    }
    private void drawOpening(GuiGraphics g,MatchSnapshot snapshot,int mx,int my){
        drawSeats(g,snapshot);drawHand(g,mx,my);
        var opening=snapshot.openingHand();var panel=board().box(454,243,764,333);
        g.fill(panel.x(),panel.y(),panel.right(),panel.bottom(),0xec3d2a1b);g.renderOutline(panel.x(),panel.y(),panel.width(),panel.height(),0xffb78e51);
        selectionText(g,opening.stage()==0?tr("sgs.ui.redraw_your_opening_hand_8534391d"):tr("sgs.ui.waiting_for_other_players_7c96946f"),480,278,712,43,0xfff0cd86);
        selectionText(g,openingPending?tr("sgs.ui.waiting_for_server_f9a51d77"):opening.stage()==0?tr("sgs.ui.each_player_may_redraw_their_entire_opening_hand_4f352bf1"):tr("sgs.ui.choice_saved_play_begins_when_everyone_has_decid_f2420b6a"),480,357,712,28,0xffdfc59f);
        if(opening.stage()!=0)selectionText(g,tr("sgs.ui.your_opening_hand_d045660d"),480,439,712,28,0xffd9bd92);
        long now=System.currentTimeMillis();
        if(opening.deadlineMillis()!=countdownDeadline){countdownDeadline=opening.deadlineMillis();countdownObservedAt=now;}
        float fraction=Math.clamp((opening.deadlineMillis()-now)/(float)Math.max(1,opening.deadlineMillis()-countdownObservedAt),0,1);
        var bar=board().box(514,522,644,10);g.fill(bar.x(),bar.y(),bar.right(),bar.bottom(),0xff65492a);
        g.fill(bar.x(),bar.y(),bar.x()+Math.round(bar.width()*fraction),bar.bottom(),0xffddb265);
        if(!reason.isEmpty())selectionText(g,reason,480,548,712,22,0xffffb395);
    }
    private boolean selectionLordSkill(String id){return ClientText.heroes(view.snapshot().heroPresentations()).stream().filter(hero->hero.id().equals(id)).flatMap(hero->hero.skills().stream()).anyMatch(skill->skill.lordOnly());}
    private ReferenceBattleLayout.Box banCard(int i){return board().box(306+(i%5)*220,170+(i/5)*285,180,240);}
    private void buildHeroBan(){
        var s=view.snapshot();var ban=s.heroBan();if(ban.stage()!=0)return;
        String chosen=ban.choices().stream().filter(c->c.seat()==s.viewerSeat()).map(HeroBanChoice::heroId).findFirst().orElse("");
        var available=ban.candidates().stream().filter(id->ban.choices().stream().noneMatch(c->c.heroId().equals(id))).toList();
        for(int i=0;i<available.size();i++){
            String id=available.get(i);var hero=ClientText.heroes(s.heroPresentations()).stream().filter(h->h.id().equals(id)).findFirst().orElse(null);if(hero==null)continue;
            var b=banCard(i);var card=new HeroChoiceWidget(hero,b.x(),b.y(),b.width(),b.height(),id.equals(chosen),chosen.isEmpty()&&banSubmitted.isEmpty()&&ban.choices().stream().noneMatch(c->c.heroId().equals(id)),ignored->{
                banSubmitted=id;reason="";MatchClient.send(MatchAction.banHero(MatchClient.nextNonce(),id));rebuildWidgets();
            });testButtons.put("ban:"+id,addRenderableWidget(card));
        }
    }
    private void drawHeroBan(GuiGraphics g,MatchSnapshot s){
        var ban=s.heroBan();boolean own=ban.choices().stream().anyMatch(c->c.seat()==s.viewerSeat());
        selectionText(g,ban.stage()==1?tr("sgs.ui.hero_bans_3b341a16"):own?tr("sgs.ui.ban_submitted_waiting_for_others_6c2eea06"):tr("sgs.ui.choose_a_hero_to_ban_6660d845"),320,105,1032,35,0xffedc57f);
        if(ban.stage()==1 || s.viewerSeat()<0){
            for(int i=0;i<s.seats().size();i++){
                final int seat=i;var b=board().box(380+(i%4)*240,180+(i/4)*285,174,225);
                var choice=ban.choices().stream().filter(c->c.seat()==seat).findFirst().orElse(null);
                if(choice!=null){
                    HeroRenderer.portrait(g,font,choice.heroId(),b.x(),b.y(),b.width(),b.height(),0xffb59559,false);
                    InkText.fitted(g,font,tr("sgs.ui.ban_607629e4"),b.x()+b.width()/2f,b.y()+b.height()*.42f,b.width()*.5f,b.height()*.25f,0xffffd47f);
                    InkText.fitted(g,font,heroName(choice.heroId()),b.x()+b.width()/2f,b.bottom()-b.height()*.12f,b.width()*.92f,b.height()*.09f,0xffedce94);
                }else dev.sgs.validation.client.art.CardBackRenderer.draw(g,b.x(),b.y(),b.width(),b.height());
                InkText.fitted(g,font,(seat+1)+tr("sgs.ui.seat_eb4fae82"),b.x()+b.width()/2f,b.bottom()+b.height()*.025f,b.width(),b.height()*.08f,0xffedce94);
            }
        }else selectionText(g,ban.choices().size()+" / "+s.seats().size()+tr("sgs.ui.confirmed_6651ad83"),570,760,532,24,0xffead3a6);
        // Pregame cards occupy the normal action lane; keep this timer below both rows.
        g.pose().pushPose();
        g.pose().translate(0,board().box(0,810,1,1).y()-decisionLayout().caption().y(),0);
        drawDecisionTimer(g,ban.stage()==1?tr("sgs.ui.banned_heroes_372e6b8a"):tr("sgs.ui.ban_heroes_01079dd9"),ban.deadlineMillis());
        g.pose().popPose();
    }
    private List<String> selectionCandidateIds(MatchSnapshot snapshot){
        var ids=snapshot.heroCandidates();
        // Stable sorting changes only presentation; submission always uses the server's immutable hero ID.
        return ids.size()>4?ids.stream().sorted(Comparator.comparing((String id)->!selectionLordSkill(id))).toList():ids;
    }
    private void confirmHero(){
        var s=view.snapshot();
        if(heroPending || s.status()!=0 || self()==null || self().heroId()!=null || !s.heroCandidates().contains(heroPreview))return;
        heroPending=true;reason="";errorCode="";
        MatchClient.send(MatchAction.selectHero(MatchClient.nextNonce(),heroPreview));
        rebuildWidgets();
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
        selectionText(g,tr("sgs.ui.waiting_for_others_to_choose_heroes_6bc3e6f9"),430,437,812,43,0xffedce94);
        drawDecisionTimer(g,tr("sgs.ui.confirmed_56634cd8")+(me==null?"":heroName(me.heroId())),snapshot.selectionDeadlineMillis());
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
            selectionText(g,lord!=null&&lord.heroId()!=null?tr("sgs.ui.lord_chosen_4dbb04a8"):tr("sgs.ui.lord_s_hero_pick_88a20b58"),100,145,312,27,0xffd9b67d);
            String role=me==null || me.identityOrdinal()<0?tr("sgs.ui.identity_pending_b68b692b"):switch(dev.sgs.core.Identity.values()[me.identityOrdinal()]){case LORD->tr("sgs.ui.lord_70368eaf");case LOYALIST->tr("sgs.ui.loyalist_83bc6bab");case REBEL->tr("sgs.ui.rebel_487c2834");case RENEGADE->tr("sgs.ui.renegade_c5344088");};
            drawSelectionPreview(g,lord,138,190,tr("sgs.ui.lord_70368eaf"));
            selectionText(g,tr("sgs.ui.your_identity_cab8934c"),100,620,312,22,0xffd9b67d);
            selectionText(g,role,100,654,312,40,0xffffd284);
            selectionText(g,me==null?tr("sgs.ui.spectate_c90a24d7"):(me.seat()+1)+tr("sgs.ui.seat_eb4fae82"),100,710,312,22,0xffe5c798);
            selectionText(g,s.heroCandidates().isEmpty()?tr("sgs.ui.waiting_for_the_lord_to_choose_24c2bf84"):tr("sgs.ui.choose_your_hero_a8b0ff59"),505,270,1050,29,0xffedc57f);
            var ids=selectionCandidateIds(s);
            for(int i=0;i<ids.size();i++){
                var card=selectionCandidate(i,ids.size());String category=selectionLordSkill(ids.get(i))?tr("sgs.ui.has_lord_skill_83e65877"):ClientText.category("其他武将");
                InkText.fitted(g,font,category,card.x()+card.width()/2f,card.bottom()+Math.max(3,Math.round(9*board().unit())),card.width()+8,Math.max(7,Math.round(15*board().unit())),selectionLordSkill(ids.get(i))?0xffffd58b:0xffcbb897);
            }
        }else{
            drawSelectionPreview(g,me,team?420:718,tr("sgs.ui.you_4b8c923e"));if(team)drawSelectionPreview(g,friend,1016,tr("sgs.ui.ally_1714e8e2"));
            selectionText(g,tr("sgs.ui.choose_your_hero_9e9c8bc1"),350,460,972,30,0xffedc57f);
        }
        String order=me==null?tr("sgs.ui.waiting_for_seat_information_a801ddc6"):tr("sgs.ui.turn_order_92e2e408")+(me.seat()+1)+tr("sgs.ui.empty_90de686b");
        String label=s.heroCandidates().isEmpty()?tr("sgs.ui.waiting_for_hero_pick_afac6d22"):me!=null && me.heroId()!=null?order+tr("sgs.ui.confirmed_waiting_for_others_2e4bb5b9"):order+tr("sgs.ui.choose_your_hero_d0dff792");
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
            else InkText.fitted(g,font,ClientText.english()?switch(label){case "我"->"Y";case "友"->"A";case "敌"->"E";default->label;}:label,x+size/2f,y+1,size-2,size-2,0xffebd0a2);
            if(seat.self())g.renderOutline(x-2,y-2,size+4,size+4,0xffa8e37d);
            text(g,Integer.toString(seat.seat()+1),x+size/2-2,y+size+1,0xffdfbd89,size);
            zoneTips.put(new Rect(x,y,size,size+10),(seat.seat()+1)+tr("sgs.ui.seat_2a957dae")+(seat.self()?tr("sgs.ui.you_4b8c923e"):friend?tr("sgs.ui.ally_1714e8e2"):tr("sgs.ui.opponent_4f76b088")));
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
        String previewId=seat==null?null:seat.self() && seat.heroId()==null && !heroPreview.isEmpty()?heroPreview:seat.heroId();
        var hero=previewId==null?null:ClientText.heroes(view.snapshot().heroPresentations()).stream().filter(h->h.id().equals(previewId)).findFirst().orElse(null);
        if(hero!=null){
            HeroChoiceWidget.drawCard(g,hero,box.x(),box.y(),box.width(),box.height(),seat.heroId()!=null,false);
            if(seat.heroId()!=null)HealthRenderer.vertical(g,box.right()-11,box.y()+24,seat.hp(),seat.maxHp(),box.height()-50);
        }else{
            g.fill(box.x(),box.y(),box.right(),box.bottom(),0xb7453424);g.renderOutline(box.x(),box.y(),box.width(),box.height(),0xffa47c45);
            InkText.fitted(g,font,tr("sgs.ui.waiting_for_hero_pick_afac6d22"),box.x()+box.width()/2f,box.y()+box.height()/2f-6,box.width()-10,14,0xffd5b880);
        }
        if(seat!=null)drawIdentityBadge(g,seat,box.right()-18,box.y()+3);
        var name=board().box(referenceX-40,referenceY+328,316,32);
        InkText.fitted(g,font,label,name.x()+name.width()/2f,name.y(),name.width(),Math.max(10,name.height()*.65f),0xffecd4a3);
        if(seat!=null)g.drawCenteredString(font,seat.name()+(seat.control().automatic()?(seat.control().source().equals("DISCONNECT")?tr("sgs.ui.offline_autoplay_a158b9da"):tr("sgs.ui.autoplay_aec5aaf1")):!seat.online()?tr("sgs.ui.offline_7290897c"):""),name.x()+name.width()/2,name.bottom()+1,0xffdfcaa4);
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
        var window=snapshot.counterspellWindow();if(window==null)return tr("sgs.ui.waiting_for_counterspell_67b42a98");
        var subject=snapshot.seats().stream().filter(seat->seat.seat()==window.subjectSeat()).findFirst().orElse(null);
        return tr("sgs.ui.waiting_for_counterspell_chain_depth_7dbb9d87")+window.chainDepth()+(subject==null?"":tr("sgs.ui.target_5a74d995")+subject.name());
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
            InkText.fitted(g,font,tr("sgs.ui.play_phase_61991861"),left-labelW/2f-5,top-1,labelW,Math.max(8,bar.height()+2),0xffdbe9b7);
        }
        if(pixels>0){g.fill(left+1,top,left+1+pixels,bar.bottom(),remaining<3000?0xffcc6c42:0xffe9af56);g.hLine(left+1,left+pixels,top,0xffffda83);}
        text(g,(remaining+999)/1000+tr("sgs.ui.s_eb6aaba1"),right-32,title.y()+1,0xfff2ce83,32);
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
        String label=heroName(actor.heroId())+tr("sgs.ui.activated_705a5adc")+skillName(activeNotice.skillId());
        g.pose().pushPose();g.pose().translate(0,0,200);
        g.fill(x,y,x+w,y+notice.height(),0x75392d21);
        float scale=Math.max(.6f,board().unit()*2.6f);g.pose().translate(x+w/2f,y+Math.max(0,(notice.height()-10*scale)/2),0);g.pose().scale(scale,scale,1);InkText.drawCentered(g,font,label,0,0,0xffffd77b);g.pose().popPose();
    }
    private String skillDescription(String id){return ClientText.skillDescription(id,ClientText.heroes(view.snapshot().heroPresentations()).stream().flatMap(h->h.skills().stream()).filter(s->s.id().equals(id)).map(dev.sgs.core.addon.SkillPresentation::description).findFirst().orElse(switch(id){
        case "rende"->"出牌阶段，将任意数量手牌交给其他角色；本回合累计交出至少两张时回复1点体力。";
        case "jijiang"->"主公技，需要使用或打出杀时，可请求其他蜀角色依行动顺序提供杀。";
        case "jianxiong"->"受到伤害且存活后，可获得造成伤害且仍在处理区的全部实体牌。";
        case "hujia"->"主公技，需要闪时，可请求其他魏角色依行动顺序提供闪。";
        case "longdan"->"将杀（含火杀、雷杀）当闪，或将闪当普通杀使用或打出。";
        case "wusheng"->"将一张红色手牌或装备当普通杀使用或打出，仍须满足距离与次数。";
        case "kurou"->"出牌阶段，失去1点体力，然后摸两张牌。若濒死，先结算救援，存活才摸牌。";
        case "jizhi"->"使用非延时锦囊时，可以摸一张牌；重铸不属于使用。";
        case "qicai"->"锁定技，使用锦囊无距离限制，仍须满足其他目标条件。";
        case "qinglong"->tr("sgs.ui.after_your_slash_is_dodged_you_may_immediately_u_084b0915");
        case "guanshi"->tr("sgs.ui.after_your_slash_is_dodged_you_may_discard_two_c_4afd0cfd");
        case "hanbing"->tr("sgs.ui.when_your_slash_would_deal_damage_if_the_target_c5ea541e");
        case "cixiong"->tr("sgs.ui.when_your_slash_targets_a_hero_of_the_opposite_g_6f11d0bd");
        case "qilin_gong"->tr("sgs.ui.when_your_slash_damages_a_target_you_may_choose_5303a84d");
        case "paoxiao"->"锁定技，出牌阶段使用杀无次数限制。";
        default->tr("sgs.ui.skill_conditions_and_resolution_are_determined_b_7a0d8ef2");}));}
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
        String label=heroName(s.seats().get(event.ownerSeat()).heroId())+tr("sgs.ui.reveal_704b1189");
        text(g,label,x,y-Math.max(10,Math.round(22*board().unit())),0xffebd4a3,Math.max(cw,80));
        for(int i=0;i<cards.size();i++)drawCard(g,cards.get(i),x+i*step,y,scale,false);
        drawnPublicReveals=cards.stream().map(CardView::instanceId).toList();
    }
    public List<Long> publicRevealedCardsForSmoke(){return drawnPublicReveals;}
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
        long now=net.minecraft.Util.getMillis();
        var cards=publicCards.visible(now);if(cards.isEmpty())return;
        float opacity=publicCards.opacity(now),unit=board().unit();
        var area=board().box(526,384,622,212);
        float scale=Math.min(1.25f*unit,Math.max(1,area.height()-36*unit)/112f);
        int cw=Math.max(1,Math.round(80*scale));
        int top=area.y()+Math.round(30*unit+(1-opacity)*12*unit);
        g.pose().pushPose();g.pose().translate(0,0,194);
        g.setColor(1,1,1,opacity);
        for(var entry:cards){
            float t=entry.arrival(now);
            int endX=area.x()+area.width()/2-cw/2+Math.round(entry.x(now)*unit);
            Rect source=entry.actor==s.viewerSeat()?previousHandAnchors.get(entry.card.instanceId()):null;
            if(source==null)source=seatHits.get(entry.actor);
            int startX=source==null?endX:source.x()+source.w()/2-cw/2;
            int startY=source==null?top:source.y()+source.h()/2-Math.round(56*scale);
            float grow=.72f+.28f*t,drawScale=scale*grow;
            int x=Math.round(startX+(endX-startX)*t+cw*(1-grow)/2);
            int y=Math.round(startY+(top-startY)*t);
            drawCard(g,entry.card,x,y,drawScale,false);
            if(t>=.92f){
                String name=s.seats().stream().filter(p->p.seat()==entry.actor).map(p->heroName(p.heroId())).findFirst().orElse("");
                int h=Math.max(10,Math.round(20*unit)),ch=Math.round(112*drawScale),w=Math.round(80*drawScale);
                g.fill(x,y+ch-h,x+w,y+ch,0xc0302218);
                InkText.fitted(g,font,name+(entry.reason.equals("USE")?tr("sgs.ui.uses_d7de594d"):entry.reason.equals("RECAST")?tr("sgs.ui.recast_8de9abfc"):tr("sgs.ui.responds_62a7a9df")),x+w/2f,y+ch-h,w-4,h,0xffffe2ad);
            }
        }
        g.setColor(1,1,1,1);g.pose().popPose();
    }
    public int publicCardCountForSmoke(){return publicCards.visible(net.minecraft.Util.getMillis()).size();}
    private void drawPublicCardPanel(GuiGraphics g,MatchSnapshot s){
        var cards=s.processing();String label=tr("sgs.ui.processing_resolving_b6a6b37a");
        if(cards.isEmpty()){
            var last=s.publicMoves().reversed().stream().filter(m->Set.of("USE","RESPONSE","RECAST").contains(m.reason())).findFirst();
            if(last.isPresent()){cards=last.get().cards();label=tr("sgs.ui.last_card_seat_667de90a")+(last.get().actorSeat()+1);}
            else label=tr("sgs.ui.processing_area_empty_no_public_play_yet_243862f0");
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
                case "USE"->tr("sgs.ui.use_0e2d3a3c");case "RESPONSE"->tr("sgs.ui.respond_2cfb1504");case "FINISH"->tr("sgs.ui.resolved_fbe27079");case "RECAST"->tr("sgs.ui.recast_8de9abfc");
                case "DEATH","DISCARD","EQUIPMENT_REPLACED"->tr("sgs.ui.discard_1cf9dbc6");case "EFFECT_GAIN"->tr("sgs.ui.obtain_aab9ff18");
                case "JUDGMENT_REVEAL"->tr("sgs.ui.reveal_judgment_2c44b3d9");case "JUDGMENT_REPLACE"->tr("sgs.ui.replace_judgment_9674f1b7");case "REVEAL"->tr("sgs.ui.reveal_287b606a");
                case "EQUIP"->tr("sgs.ui.equipment_1faf3321");case "DELAYED_USE","DELAY_PASS"->tr("sgs.ui.place_in_judgment_area_060ff964");default->tr("sgs.ui.public_movement_88b76ca4");};
            text(g,"#"+move.sequence()+" · "+(move.actorSeat()<0?tr("sgs.ui.deck_f5f4cc6d"):tr("sgs.ui.seat_cb0d0023")+(move.actorSeat()+1))+" · "+action+" · "+names,30,y,0xffdcc49b,width-60);y+=11;
        }
        g.disableScissor();
    }
    private void drawHandPreview(GuiGraphics g,SeatView seat,int mx,int my){
        previewedSeat=seat.seat();
        if(!seat.handVisible()){
            var me=self();boolean teammate=me!=null && !seat.self() && me.teamId()>=0 && seat.teamId()==me.teamId();
            paperTip(g,tr("sgs.ui.hand_5db4c658")+seat.handCount()+tr("sgs.ui.cards_9273cc90")+(teammate?tr("sgs.ui.ally_hand_sharing_disabled_8211c25a"):""),mx,my);return;
        }
        int columns=Math.max(1,Math.min(8,(width-36)/44)),rows=Math.max(1,(seat.hand().size()+columns-1)/columns);
        int w=Math.min(columns,Math.max(1,seat.hand().size()))*44+16,h=Math.min(height-38,rows*63+30);
        int x=Math.clamp(mx-w/2,8,width-w-8),y=Math.clamp(my-h-8,25,height-h-8);
        g.pose().pushPose();g.pose().translate(0,0,450);
        g.fill(x-2,y-2,x+w+2,y+h+2,0xffad8449);g.fill(x,y,x+w,y+h,0xff3d2b1e);
        text(g,(seat.self()?tr("sgs.ui.your_hand_2881fe6f"):tr("sgs.ui.ally_s_hand_shared_ef70d38d"))+" · "+seat.handCount(),x+8,y+7,0xffebd4a3,w-16);
        if(seat.hand().isEmpty())text(g,tr("sgs.ui.no_hand_cards_8949e150"),x+8,y+22,0xffebd4a3,w-16);
        g.enableScissor(x,y+20,x+w,y+h);
        for(int i=0;i<seat.hand().size();i++)drawCard(g,seat.hand().get(i),x+8+i%columns*44,y+22+i/columns*63,.52f,false);
        g.disableScissor();g.pose().popPose();
    }
    private String promptName(int ordinal){return switch(PromptType.values()[ordinal]){case DODGE_SHA->tr("sgs.ui.play_dodge_ae66172a");case HUJIA_SHAN->tr("sgs.ui.royal_guard_provide_dodge_for_the_lord_f9bc7fe8");case JIJIANG_SHA->tr("sgs.ui.rally_provide_slash_for_the_lord_e71ff5d7");case QINGLONG_SHA->tr("sgs.ui.green_dragon_slash_the_same_target_again_f82ddb21");case SKILL_INVOKE->tr("sgs.ui.choose_skill_af6ccc9d");case RESCUE_TAO->tr("sgs.ui.dying_rescue_5ed6db90");case WUXIE_COUNTER->tr("sgs.ui.counterspell_637cf56c");case DUEL_SHA,RESPOND_SHA_NANMAN,JIEDAO_RESPONSE->tr("sgs.ui.play_slash_126bcca3");case WUGU_PICK->view.snapshot().prompt()!=null && view.snapshot().prompt().skillId().equals("zhulu")?tr("sgs.ui.battle_for_supremacy_choose_equipment_30f97a96"):tr("sgs.ui.amazing_grace_choose_a_card_7e15a383");case HUOGONG_REVEAL->tr("sgs.ui.fire_attack_reveal_a_hand_card_78640e18");case HUOGONG_PAY->tr("sgs.ui.fire_attack_discard_a_matching_suit_1a5610ec");case FANJIAN_SUIT->tr("sgs.ui.sow_discord_choose_a_suit_40e3292d");case GUI_CAI_REPLACE->tr("sgs.ui.supernatural_talent_replace_judgment_with_a_hand_e8cf2d81");case FANKUI_TAKE->tr("sgs.ui.retaliation_take_a_hand_or_equipped_card_15d59028");case YIJI_GIVE->tr("sgs.ui.legacy_choose_a_card_and_recipient_dd21c8ba");case TUXI_TARGET->tr("sgs.ui.raid_choose_a_target_with_hand_cards_0c73930b");case GANGLIE_DISCARD->tr("sgs.ui.unyielding_discard_two_hand_cards_or_take_damage_3a21c61f");case LIJIAN_TARGET->tr("sgs.ui.estrangement_choose_the_second_male_61c1aa77");case GUANXING_ORDER->tr("sgs.ui.stargazing_d2b976c0");case LIULI_TARGET->tr("sgs.ui.deflection_choose_a_discard_and_new_target_a89bae34");case CARD_ZONE_SELECT->tr("sgs.ui.choose_a_target_s_hand_equipped_or_judgment_card_c43007b2");case WEAPON_DISCARD->tr("sgs.ui.twin_swords_discard_a_hand_card_or_let_the_attac_4d391614");case WEAPON_COST_FIRST->tr("sgs.ui.stone_axe_choose_first_cost_card_not_yet_discard_62a48c10");case WEAPON_COST_SECOND->tr("sgs.ui.stone_axe_choose_second_card_to_discard_both_d6ee9522");case EXPANSION_CHOICE->tr("sgs.ui.skill_card_hero_choice_ad97b003");case EXPANSION_OPTION->tr("sgs.ui.skill_options_438ba043");};}
    private String outcome(int ordinal){var values=dev.sgs.core.engine.GameOutcome.values();return ordinal>=0 && ordinal<values.length?switch(values[ordinal]){case ONGOING->tr("sgs.ui.awaiting_result_f293b187");case LORD_WIN->tr("sgs.ui.lord_and_loyalists_win_76847cf7");case REBELS_WIN->tr("sgs.ui.rebels_win_ca01ef9d");case RENEGADE_WIN->tr("sgs.ui.renegade_wins_3def0300");case TEAM_A_WIN,TEAM_B_WIN->{var me=self();yield me==null || me.teamId()<0?tr("sgs.ui.match_finished_28363ab2"):me.teamId()==(values[ordinal]==dev.sgs.core.engine.GameOutcome.TEAM_A_WIN?0:1)?tr("sgs.ui.victory_3d90ba4a"):tr("sgs.ui.defeat_8e7343e2");}case DRAW->tr("sgs.ui.draw_66402ea5");}:tr("sgs.ui.awaiting_result_f293b187");}
    private List<SeatView> opponentSeats(MatchSnapshot snapshot){
        int count=snapshot.seats().size();
        int anchor=snapshot.viewerSeat()>=0?snapshot.viewerSeat():0;
        return snapshot.seats().stream().filter(seat->seat.seat()!=anchor)
            .sorted(Comparator.comparingInt(seat->ReferenceBattleLayout.opponentIndex(seat.seat(),anchor,count))).toList();
    }

    private void drawSeats(GuiGraphics g,MatchSnapshot s){seatClockRendered=false;autoplayBadgeSeats.clear();
        renderedChainRelease=null;
        relationBadges.clear();
        seatHits.clear();countHits.clear();zoneTips.clear();equipmentHits.clear();skillTips.clear();
        var others=opponentSeats(s);
        for(int i=0;i<others.size();i++){
            var r=board().opponent(i,others.size());drawSeat(g,s,fixtureSeat(others.get(i)),r.x(),r.y(),r.width(),r.height());
        }
        // Spectators anchor seat zero at the bottom without making it their private/self seat.
        var me=self();if(me==null)me=s.seats().stream().filter(seat->seat.seat()==0).findFirst().orElse(null);
        var r=board().self();if(me!=null)drawSeat(g,s,me,r.x(),r.y(),r.width(),r.height());
    }
    private void drawDamageFeedback(GuiGraphics g,dev.sgs.validation.client.layout.DamageFeedback.Hit hit,long age,int x,int y,int w,int h){
        PortraitCombatEffects.hit(g,age,x,y,w,h);
    }
    private void drawSeat(GuiGraphics g,MatchSnapshot s,SeatView seat,int x,int y,int w,int h){
        seatHits.put(seat.seat(),new Rect(x,y,w,h));
        String blocked=targetHint(seat.seat());
        boolean unavailable=seat.alive() && (targetSelectionContext() || myPrompt() && !view.snapshot().prompt().allowedTargetSeats().isEmpty()) && blocked!=null && !blocked.isEmpty();
        // Temporary target prohibition also desaturates; death retains its separate seal.
        boolean colored=seat.alive() && !unavailable;
        int border=((fangtianSelection() || tuxiPrompt())?attackTargets.contains(seat.seat()):seat.seat()==target || seat.seat()==secondary)?0xffffcd68:seat.seat()==s.currentSeat()?0xffa9ce84:0xffb38a48;
        var style=FactionStyle.of(seat.kingdomOrdinal());
        var classic=HeroRenderer.classic(seat.heroId());
        var presentation=ClientText.heroes(s.heroPresentations()).stream().filter(p->p.id().equals(seat.heroId())).findFirst().orElse(null);
        if(presentation!=null && !presentation.portrait().isEmpty()){
            var texture=net.minecraft.resources.ResourceLocation.parse(presentation.portrait());
            if(minecraft.getResourceManager().getResource(texture).isPresent())classic=texture;
        }
        var damageHit=damageFeedback.hit(seat.seat(),net.minecraft.Util.getMillis());
        long damageAge=damageHit==null?0:net.minecraft.Util.getMillis()-damageHit.started();
        g.pose().pushPose();
        if(damageHit!=null && damageAge<260){
            float shake=(float)(Math.sin(damageAge*.065)*(1-damageAge/260f)*Math.max(2,w*.045f));
            g.pose().translate(shake,0,0);
        }
        HeroRenderer.portraitExact(g,font,seat.heroId(),x,y,w,h,colored?border:HeroRenderer.grayColor(border),colored,HeroSkins.syncedTexture(seat.heroId(),view.skins().getOrDefault(seat.seat(),"classic"),classic));
        g.pose().popPose();
        var frame=dev.sgs.validation.ProbeMod.id("textures/gui/panels/hero_frame_v1.png");
        BoardSkin.nine(g,colored?frame:HeroRenderer.grayscale(frame),x-3,y-3,w+6,h+6,320,448,64);
        String name=seat.self() && skinNameHovered?tr("sgs.ui.skins_e6081d73"):heroName(seat.heroId());
        if(seat.self())renderedSelfName=name;
        int nameW=PortraitOverlayLayout.nameRailWidth(w),nameStep=Math.max(8,Math.min(nameW-2,h/9));
        var nameRail=PortraitOverlayLayout.nameRail(x,y,w,h);
        g.fill(nameRail.x(),nameRail.y(),nameRail.right(),nameRail.bottom(),colored?style.main():HeroRenderer.grayColor(style.main()));
        float nameTop=nameRail.y()+(nameRail.height()-name.length()*nameStep)/2f;
        if(ClientText.english()){
            g.pose().pushPose();g.pose().translate(nameRail.x()+nameRail.width()/2f,nameRail.y()+nameRail.height()/2f,0);
            g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-90));
            InkText.fitted(g,font,name,0,-nameRail.width()/2f,nameRail.height()-8,nameRail.width()-2,0xffffebcb);g.pose().popPose();
        }else for(int i=0;i<name.length();i++)InkText.fitted(g,font,name.substring(i,i+1),nameRail.x()+nameRail.width()/2f,nameTop+i*nameStep,nameW-2,nameStep,0xffffebcb);
        String faction=seat.kingdomOrdinal()<0?"?":switch(dev.sgs.core.Kingdom.values()[seat.kingdomOrdinal()]){case SHU->ClientText.category("蜀");case WEI->ClientText.category("魏");case WU->ClientText.category("吴");case QUN->ClientText.category("群");};
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
        String number=ClientText.english()?Integer.toString(seat.seat()+1):List.of("一","二","三","四","五","六","七","八").get(seat.seat());
        drawSeatClock(g,s,seat,x,y+h+3,w,number+tr("sgs.ui.seat_eb4fae82"));
        if(showDistances && !seat.self() && seat.alive() && seat.distanceFromViewer()!=null){
            String label=tr("sgs.ui.distance_3587547e")+seat.distanceFromViewer();
            int labelW=font.width(label)+8;
            g.fill(x+w/2-labelW/2,y-14,x+w/2+labelW/2,y-2,0xe136271e);
            g.drawCenteredString(font,label,x+w/2,y-12,0xffffd987);
        }
        drawEquipment(g,seat,x,y,w,h);
        if(seat.alive() && seat.control().automatic()){
            // Compact portrait tag, independent of skills and the bottom takeover veil.
            int tagX=x+nameW+Math.max(2,Math.round(5*board().unit()));
            int tagY=y+Math.round(h*.31f),tagW=Math.max(17,Math.round(48*board().unit())),tagH=Math.max(10,Math.round(26*board().unit()));
            g.pose().pushPose();g.pose().translate(0,0,216);
            g.fill(tagX,tagY,tagX+tagW,tagY+tagH,0xf43b3026);
            g.renderOutline(tagX,tagY,tagW,tagH,0xffbda77e);
            InkText.fitted(g,font,tr("sgs.ui.autoplay_731b0cba"),tagX+tagW/2f,tagY+1,tagW-3,tagH-2,0xffffebca);
            g.pose().popPose();autoplayBadgeSeats.add(seat.seat());
            zoneTips.put(new Rect(tagX,tagY,tagW,tagH),seat.control().source().equals("DISCONNECT")?tr("sgs.ui.offline_autoplay_8bb4332e"):tr("sgs.ui.autoplay_731b0cba"));
        }else if(seat.alive() && seat.human() && !seat.online()){
            int labelX=x+nameW+3,labelY=y+h-29;
            InkText.fitted(g,font,tr("sgs.ui.offline_211357d2"),labelX+(w-nameW-7)/2f,labelY,w-nameW-9,Math.max(9,Math.min(13,h/9)),0xffd8c5a5);
        }
        if(seat.alive() && (seat.chained() || chainReleased.containsKey(seat.seat()))){
            Long started=chainStarted.get(seat.seat());
            long elapsed=started==null?ChainRenderer.WRAP_MILLIS:net.minecraft.Util.getMillis()-started;
            int inset=Math.max(3,Math.round(12*board().unit()));
            int chainX=x-3,chainY=y+Math.round(h*.49f);
            int hpWidth=Math.max(1,Math.round(16*board().unit()))+2*Math.max(1,Math.round(3*board().unit()));
            int chainW=w+6;
            if(seat.chained()) {
                ChainRenderer.drawWrapping(g,font,chainX,chainY,chainW,h,elapsed);
                if(elapsed>=ChainRenderer.WRAP_MILLIS)chainStarted.remove(seat.seat());
                zoneTips.put(new Rect(chainX,chainY-Math.max(3,chainW/15),chainW,Math.max(8,chainW/6)),tr("sgs.ui.chained_elemental_damage_spreads_to_other_chaine_336c1730"));
            } else {
                long releaseElapsed=net.minecraft.Util.getMillis()-chainReleased.get(seat.seat());
                ChainRenderer.drawReleasing(g,font,chainX,chainY,chainW,releaseElapsed);
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
            targetEffectStarted.remove(seat.seat());
            // Dim prohibited targets before clicks and keep the range reason on the portrait.
            g.fill(x-3,y-3,x+w+3,y+h+3,0x78000000);
            if("OUT_OF_RANGE".equals(blocked))InkText.fitted(g,font,targetHintText(blocked),x+w/2f,y+h*.38f,w-8,Math.max(10,h*.09f),0xffffcb70);
            zoneTips.put(new Rect(x,y,w,h),targetHintText(blocked));
        } else drawAttackTargetMarker(g,seat,x,y,w,h);
        if(damageHit!=null)drawDamageFeedback(g,damageHit,damageAge,x,y,w,h);
    }
    /** Paint after the decorative frame so selection cannot be covered by its gold texture. */
    private void drawAttackTargetMarker(GuiGraphics g,SeatView seat,int x,int y,int w,int h){
        var choice=ownedPromptChoice();
        boolean recipient=choice!=null && choice.allowsTarget(seat.seat()) && target==seat.seat();
        boolean multi=(fangtianSelection() || tuxiPrompt()) && attackTargets.contains(seat.seat());
        boolean ordinary=!fangtianSelection() && (target==seat.seat() || secondary==seat.seat());
        if((!multi && !recipient && !ordinary) || !seat.alive() || w<32 || h<24){targetEffectStarted.remove(seat.seat());return;}
        long now=net.minecraft.Util.getMillis();
        long effectStart=targetEffectStarted.computeIfAbsent(seat.seat(),ignored->now);
        int viewer=view.snapshot().viewerSeat(),count=view.snapshot().seats().size();
        var ordered=attackTargets.stream().sorted(Comparator.comparingInt(n->Math.floorMod(n-viewer,count))).toList();
        int number=multi?ordered.indexOf(seat.seat())+1:secondary==seat.seat()?2:1;float unit=board().unit();
        int outside=Math.max(4,Math.round(6*unit));
        int topEdge=Math.max(y-outside,Math.max(15,Math.round(55*unit))+3),bottomEdge=y+h+outside;
        PortraitCombatEffects.target(g,now-effectStart,x-outside,topEdge,w+outside*2,bottomEdge-topEdge,!recipient);
        int nameW=Math.max(12,Math.round(w*.16f)),available=Math.max(1,w-nameW-8);
        int badgeW=Math.min(available,Math.max(24,Math.round(64*unit))),badgeH=Math.max(11,Math.round(22*unit));
        int left=x+nameW+4+(available-badgeW)/2,top=y+Math.max(5,Math.round(9*unit));
        g.fill(left-1,top-1,left+badgeW+1,top+badgeH+1,0xffffd778);
        g.fill(left,top,left+badgeW,top+badgeH,0xf04b3019);
        InkText.fitted(g,font,recipient?tr("sgs.ui.target_941f0831"):badgeW>=28?tr("sgs.ui.target_90abaca5")+number:Integer.toString(number),left+badgeW/2f,top+1,badgeW-4,badgeH-2,0xffffe7a6);
        attackTargetMarkers.put(seat.seat(),number);
    }
    private void drawIdentityBadge(GuiGraphics g,SeatView seat,int x,int y){
        drawIdentityBadge(g,seat,x,y,16);
    }
    private void drawIdentityBadge(GuiGraphics g,SeatView seat,int x,int y,int size){
        String identityLabel="?",badgeTip="";
        if(seat.teamId()>=0){
            var me=self();boolean friendly=me!=null && me.teamId()>=0 && me.teamId()==seat.teamId();
            identityLabel=me==null?"?":seat.self()?"我":friendly?"友":"敌";badgeTip=me==null?tr("sgs.ui.public_team_f4e655c0"):seat.self()?tr("sgs.ui.your_team_you_6c5de5e8"):friendly?tr("sgs.ui.your_team_ally_2306412a"):tr("sgs.ui.enemies_99d2b5e4");
        }else if(seat.identityOrdinal()>=0){
            var identity=dev.sgs.core.Identity.values()[seat.identityOrdinal()];
            identityLabel=switch(identity){case LORD->"主";case LOYALIST->"忠";case REBEL->"反";case RENEGADE->"内";};
            badgeTip=switch(identity){case LORD->tr("sgs.ui.lord_70368eaf");case LOYALIST->tr("sgs.ui.loyalist_83bc6bab");case REBEL->tr("sgs.ui.rebel_487c2834");case RENEGADE->tr("sgs.ui.renegade_c5344088");};
        }else if(canMarkIdentity(seat)){
            identityLabel=MatchClient.identityMarks.label(seat.seat());
            identityMarkHits.put(seat.seat(),new Rect(x,y,size,size));
        }
        boolean deduction=canMarkIdentity(seat) && !identityLabel.equals("?");
        IdentitySeal.draw(g,font,identityLabel,x,y,size,deduction);
        if(canMarkIdentity(seat))badgeTip=deduction?tr("sgs.ui.your_mark_3d6e2e84")+IdentitySeal.name(identityLabel):tr("sgs.ui.unknown_identity_b0cb6fbe");
        relationBadges.put(seat.seat(),identityLabel);
        if(!badgeTip.isEmpty())zoneTips.put(new Rect(x,y,size,size),badgeTip);
    }
    private boolean canMarkIdentity(SeatView seat){
        return view.snapshot().viewerSeat()>=0 && !seat.self() && seat.teamId()<0 && seat.identityOrdinal()<0;
    }
    private boolean identityMarkControlsAvailable(){
        return overlay.isEmpty() && !cardsOpen && !logOpen && !privateDeckVisible() && !socialPaletteVisible()
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
        CardView[] equips={seat.treasure(),seat.weapon(),seat.armor(),seat.horsePlus(),seat.horseMinus()};
        String[] labels={"宝","武","甲","防","攻"};
        String[] slotNames={tr("sgs.ui.treasure_fd4136fb"),tr("sgs.ui.weapon_6f0f16e0"),tr("sgs.ui.armor_e4f78202"),tr("sgs.ui.defensive_mount_b764e135"),tr("sgs.ui.offensive_mount_01c24721")};
        if(seat.self()){
            var eq=board().equipment();int ew=eq.width(),ex=eq.x(),ey=eq.y(),eh=eq.height()/5;
            for(int i=0;i<5;i++){
                BoardSkin.nine(g,BoardSkin.SLOT,ex,ey+i*eh,ew,Math.max(8,eh-2),192,64,12);
                if(equips[i]==null)InkText.label(g,font,labels[i],ex+ew/2f,ey+i*eh+1,ew-6,Math.max(6,eh-4),0xffb79863);
                if(equips[i]!=null)drawEquippedCard(g,equips[i],ex+3,ey+i*eh+1,ew-6,Math.max(6,eh-4),0xffebd5ab);
                zoneTips.put(new Rect(ex,ey+i*eh,ew,Math.max(8,eh-2)),equips[i]==null?labels[i]+tr("sgs.ui.empty_equipment_slot_ac1f94f4"):equipmentTip(equips[i]));
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
            String seal=ClientText.english()?switch(CardType.values()[seat.delay().get(i).typeOrdinal()]){case LEBU->"I";case BINGLIANG->"S";case SHANDIAN->"L";default->card.substring(0,1);}:card.contains("乐")?"乐":card.contains("兵")?"兵":card.contains("闪电")?"闪":card.substring(0,1);
            for(int row=0;row<box.height();row++){
                int half=Math.min(row+1,box.height()-row);int center=box.x()+box.width()/2;
                g.fill(center-half,box.y()+row,center+half+1,box.y()+row+1,0xff8b733e);
                if(half>2)g.fill(center-half+2,box.y()+row,center+half-1,box.y()+row+1,0xffd5bd78);
            }
            InkText.fitted(g,font,seal,box.x()+box.width()/2f,box.y()+box.height()*.17f,box.width()*.65f,box.height()*.65f,0xff3d3825);
            zoneTips.put(new Rect(box.x(),box.y(),box.width(),box.height()),tr("sgs.ui.judgment_area_77f3ea47")+card+tr("sgs.ui.last_placed_resolves_first_ee7e4c96"));}
    }
    /** Keep the physical card's rank and suit visible after it leaves the hand. */
    private void drawEquippedCard(GuiGraphics g,CardView card,int x,int y,int w,int h,int nameColor){
        g.fill(x,y,x+w,y+h,0xffe9d4ae);
        nameColor=0xff422c1c;
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
        InkText.fitted(g,font,ClientText.english()?"L":"限",x+size/2f,y+2,size-4,size-4,ready?0xffffdfa0:0xffb2afa7);
    }
    private String equipmentTip(CardView card){
        String suit=switch(Suit.values()[card.suitOrdinal()]){case SPADE->tr("sgs.ui.spade_fcb4182d");case HEART->tr("sgs.ui.heart_bee0bce1");case CLUB->tr("sgs.ui.club_ff8ba227");case DIAMOND->tr("sgs.ui.diamond_48ae409d");case NONE->"";};
        return Rank.values()[card.rankOrdinal()].label()+suit+" · "+cardName(card);
    }
    public void portraitFixtureForSmoke(boolean enabled){
        if(!Boolean.getBoolean("sgs.portraitOverlaySmoke"))throw new IllegalStateException("portrait fixture requires opt-in");
        portraitFixture=enabled;
    }
    public void equipmentFixtureForSmoke(String id,long elapsed){
        if(!Boolean.getBoolean("sgs.equipmentEffectsSmoke"))throw new IllegalStateException("equipment fixture requires opt-in");
        equipmentEffectsFixture=true;equipmentFixtureElapsed=elapsed;skillNotices.clear();
        activeNotice=id.isEmpty()?null:new PublicSkillActivationView(0,view.snapshot().viewerSeat(),id);
        noticeStarted=System.currentTimeMillis()-elapsed;
    }
    private record DeckBox(int x,int y,int w,int h,int laneHeight,float scale){}
    private DeckBox deckBox(){
        int w=Math.min(720,width-24),h=Math.min(410,height-20);
        int lane=Math.max(24,(h-140)/2);
        int count=Math.max(1,view.snapshot().prompt().choices().size());
        float scale=Math.min(1.2f,Math.min(lane/112f,(w-64f)/(count*106f-26f)));
        return new DeckBox((width-w)/2,(height-h)/2,w,h,lane,Math.max(.14f,scale));
    }
    private Rect deckLane(int lane){var b=deckBox();int count=view.snapshot().prompt().choices().size();
        int cw=Math.round(80*b.scale()),gap=Math.round(26*b.scale()),rw=count*cw+Math.max(0,count-1)*gap;
        return new Rect(b.x()+(b.w()-rw)/2,b.y()+38+lane*(b.laneHeight()+12),rw,b.laneHeight());
    }
    private Rect deckSlot(int lane,int index){var b=deckBox();var row=deckLane(lane);
        int cw=Math.round(80*b.scale()),ch=Math.round(112*b.scale());
        return new Rect(row.x()+index*(cw+Math.round(26*b.scale())),row.y()+(row.h()-ch)/2,cw,ch);
    }
    private void ensureDeckOrder(){var p=view.snapshot().prompt();if(deckOrder==null||deckOrderPrompt!=p.promptId()){
        deckOrderBudgetMillis=Math.max(1,p.deadlineMillis()-System.currentTimeMillis());
        deckOrderPrompt=p.promptId();deckOrder=new dev.sgs.validation.client.layout.GuanxingOrder(p.choices().stream().map(CardView::instanceId).toList());deckSelected=-1;deckDragged=-1;deckOrderSubmitted=-1;
    }}
    private void buildPrivateDeckOrder(){
        ensureDeckOrder();deckCardHits.clear();var b=deckBox();var p=view.snapshot().prompt();
        boolean pending=deckOrderSubmitted==p.promptId();int cardW=Math.round(80*b.scale()),cardH=Math.round(112*b.scale());
        for(int lane=0;lane<2;lane++){
            var ids=lane==0?deckOrder.top():deckOrder.bottom();
            for(int i=0;i<ids.size();i++){
                long id=ids.get(i);var card=p.choices().stream().filter(c->c.instanceId()==id).findFirst().orElseThrow();
                var slot=deckSlot(lane,i);int x=slot.x(),y=slot.y();deckCardHits.put(id,new Rect(x,y,cardW,cardH));
                var widget=new dev.sgs.validation.client.widget.SgsButton(x,y,cardW,cardH,Component.literal(cardName(card)),ignored->{deckSelected=id;rebuildWidgets();}){
                    @Override protected void renderWidget(GuiGraphics g,int mx,int my,float partial){if(deckDragged!=id||!deckDidDrag)drawCard(g,card,getX(),getY(),b.scale(),false);}
                };
                widget.active=!pending;addRenderableWidget(widget);testButtons.put("skill-choice:"+id,widget);
            }
        }
        int y=b.y()+b.h()-42,c=b.x()+b.w()/2;
        var ok=button("skill-choice-confirm",pending?tr("sgs.ui.submitting_4cc708dc"):tr("sgs.ui.confirm_f526c899"),c-60,y,120,()->{
            if(deckOrderSubmitted==p.promptId())return;var order=deckOrder.submitted();deckOrderSubmitted=p.promptId();
            MatchClient.send(MatchAction.orderDeck(MatchClient.nextNonce(),p.promptId(),order.top(),order.bottom()));rebuildWidgets();
        });ok.active=!pending;ok.setHeight(32);
    }
    private void drawPrivateDeckOrder(GuiGraphics g){
        var b=deckBox();g.fill(0,0,width,height,0x70000000);
        g.fillGradient(b.x(),b.y(),b.x()+b.w(),b.y()+b.h(),0xc5322317,0xe052361b);
        int cx=width/2;
        g.fill(b.x()+22,b.y()+20,b.x()+b.w()-22,b.y()+21,0xff967343);
        g.fill(cx-80,b.y()+6,cx+80,b.y()+32,0xdd624727);
        InkText.fitted(g,font,tr("sgs.ui.stargazing_d2b976c0"),cx,b.y()+9,140,20,0xffffe0a0);
        for(int lane=0;lane<2;lane++){
            var ids=lane==0?deckOrder.top():deckOrder.bottom();
            String label=lane==0?tr("sgs.ui.deck_top_a1318bc4"):tr("sgs.ui.deck_bottom_81928186");
            for(int i=0;i<view.snapshot().prompt().choices().size();i++){
                var slot=deckSlot(lane,i);
                g.fill(slot.x(),slot.y(),slot.x()+slot.w(),slot.y()+slot.h(),0x9564472b);
                g.renderOutline(slot.x(),slot.y(),slot.w(),slot.h(),0x887d6040);
                if(i>=ids.size()||(deckDidDrag&&ids.get(i)==deckDragged))
                    InkText.fitted(g,font,label,slot.x()+slot.w()/2f,slot.y()+slot.h()/2f-5,slot.w()-8,12,0xffe8cfa2);
            }
        }
        g.drawCenteredString(font,tr("sgs.ui.guanxing.move_cards"),cx,b.y()+b.h()-76,0xfff5dfb2);
        int x=b.x()+24,y=b.y()+b.h()-57,w=b.w()-48;
        var prompt=view.snapshot().prompt();
        float remaining=Math.clamp((prompt.deadlineMillis()-System.currentTimeMillis())/(float)deckOrderBudgetMillis,0,1);
        g.fill(x,y,x+w,y+5,0xaa271c13);g.fill(x,y,x+Math.round(w*remaining),y+5,0xffd6a45c);
        g.renderOutline(x,y,w,5,0xffa38150);
    }
    @Override public boolean mouseDragged(double mx,double my,int button,double dx,double dy){
        if(MatchChatClient.open)return true;
        if(logOpen&&logDragging==3&&button==0){journalX=logLeft()+(int)dx;journalY=logTop()+(int)dy;rebuildWidgets();return true;}
        if(logOpen&&logDragging!=0&&button==0){dragJournal(logDragging==2?mx:my);return true;}
        if(privateDeckVisible()&&deckDragged>=0&&button==0){deckDidDrag|=Math.abs(mx-deckDragX)+Math.abs(my-deckDragY)>4;return true;}
        return super.mouseDragged(mx,my,button,dx,dy);
    }
    @Override public boolean mouseReleased(double mx,double my,int button){
        if(MatchChatClient.open)return true;
        if(logDragging!=0&&button==0){logDragging=0;return true;}
        if(privateDeckVisible()&&deckDragged>=0&&button==0){
            if(deckDidDrag)for(int lane=0;lane<2;lane++){var row=deckLane(lane);if(row.contains(mx,my)){
                var ids=(lane==0?deckOrder.top():deckOrder.bottom()).stream().filter(id->id!=deckDragged).toList();int index=0;
                for(long id:ids){var hit=deckCardHits.get(id);if(hit!=null&&mx>hit.x()+hit.w()/2d)index++;}deckOrder.move(deckDragged,lane,index);break;
            }}deckDragged=-1;deckDidDrag=false;rebuildWidgets();return true;
        }return super.mouseReleased(mx,my,button);
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
            InkText.fitted(g,font,cardName(card),x+cw/2f,y+6,Math.max(1,cw-24*scale),Math.max(1,24*scale),0xff30271c);text(g,tr("sgs.ui.artwork_pending_3f879c7d"),x+2,y+ch/2,0xff725d44,cw-4);
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
            }else if(myPrompt()){
                g.renderOutline(x-2,y-2,cw+4,ch+4,0xffffdb75);
                g.renderOutline(x-1,y-1,cw+2,ch+2,0xfff5c34f);
            }
            g.pose().popPose();
            handHits.put(card.instanceId(),new Rect(x,y,cw,ch));
        }
        g.disableScissor();
        if(layout.contentWidth()>layout.available())text(g,tr("sgs.ui.scroll_to_move_through_hand_7af56204"),handLeft(),baseline-12,0xffead2a0,120);
    }
    @Override public boolean mouseClicked(double mx,double my,int button){
        if(chatPanel!=null&&chatPanel.click(mx,my,button))return true;
        if(button==0 && !boardCovered())for(var seat:view.snapshot().seats()){
            var r=seatHits.get(seat.seat());if(!seat.self() && r!=null && heroModeButton(r).contains(mx,my)){
                if(!heroJournalSeats.remove(seat.seat()))heroJournalSeats.add(seat.seat());return true;
            }
        }
        if(logOpen&&button==0){
            if(mx>=logLeft()&&mx<logLeft()+logPanelWidth()-26&&my>=logTop()&&my<logTop()+22){logDragging=3;return true;}
            if(my>=logTop()+23&&my<logTop()+55&&mx>=logLeft()+8){
                int tab=(int)((mx-logLeft()-8)/logTabWidth());
                if(tab>=0&&tab<=view.snapshot().seats().size()&&(mx-logLeft()-8)%logTabWidth()<logTabWidth()-3){
                    logSeat=tab==0?-1:view.snapshot().seats().get(tab-1).seat();logFollowLatest=true;logScrollX=0;logDragging=0;return true;
                }
            }
            int barX=logLeft()+logPanelWidth()-10;
            boolean vertical=mx>=barX-2&&mx<=barX+5&&my>=logTextTop()&&my<=logTextTop()+logViewportHeight();
            boolean horizontal=logMaxX>0&&mx>=logTextLeft()&&mx<=logTextLeft()+logViewportWidth()&&my>=logTextTop()+logViewportHeight()+2&&my<=logTextTop()+logViewportHeight()+8;
            if(vertical||horizontal){
                logDragging=horizontal?2:1;double coordinate=horizontal?mx:my,start=logThumbStart(horizontal),size=logThumbSize(horizontal);
                logDragOffset=coordinate>=start&&coordinate<=start+size?coordinate-start:size/2;dragJournal(coordinate);return true;
            }
        }
        if(privateDeckVisible()&&deckOrderSubmitted!=view.snapshot().prompt().promptId()&&button==0){
            for(var entry:deckCardHits.entrySet())if(entry.getValue().contains(mx,my)){deckSelected=deckDragged=entry.getKey();deckDragX=mx;deckDragY=my;deckDidDrag=false;rebuildWidgets();return true;}
        }

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
        // Background controls must not receive clicks through a modal panel.
        var disabled=boardCovered()?boardWidgets.stream().filter(w->w.active).toList():List.<net.minecraft.client.gui.components.AbstractWidget>of();
        disabled.forEach(w->w.active=false);
        boolean widgetClicked;
        try{widgetClicked=super.mouseClicked(mx,my,button);}finally{disabled.forEach(w->w.active=true);}
        if(widgetClicked)return true;
        if(self()!=null && self().control().automatic())return true;
        if(socialPaletteVisible()){
            if(socialPaletteBox().contains(mx,my))return true;
            if(giftPalette && button==0)for(var e:seatHits.entrySet())if(e.getValue().contains(mx,my)){
                var seat=view.snapshot().seats().stream().filter(p->p.seat()==e.getKey()).findFirst().orElse(null);
                if(seat==null || seat.self() || !seat.human()){feedbackMessage=tr("sgs.ui.choose_another_human_player_in_this_match_40c038ed");return true;}
                giftTarget=seat.seat();sendSelectedGift();return true;
            }
            giftPalette=false;emotePalette=false;rebuildWidgets();return true;
        }
        if(!overlay.isEmpty() || cardsOpen || logOpen || privateDeckVisible() || draftPrompt() || zonePrompt() || view.snapshot().identityIntro()!=null || view.snapshot().heroBan()!=null || view.snapshot().status()==2 || view.snapshot().openingHand()!=null || selectionWaiting(view.snapshot()))return true;
        if(button==0 && skinStripAvailable() && skinStrip().contains(mx,my)){openOverlay("SKINS");return true;}
        if(wuxiePending() || view.snapshot().counterspellWindow()!=null && !myPrompt())return true;
        boolean equipmentCosts=self()!=null && (ownedCards(self()).stream().anyMatch(this::hasConversion) || wusheng || qixi || jijiu || guose || duanliang || ClientText.heroes(view.snapshot().heroPresentations()).stream()
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
                if(!selected.remove(id)){selected.clear();selected.add(id);}if(!myPrompt() && costSkill.isEmpty())clearCardTargets();convertedCard=-1;convertedType=-1;reason="";rebuildWidgets();return true;
            }
        // Match drawHand's pointer resolution, not the previous frame's promoted hover layer.
        var hand=self()==null?List.<CardView>of():self().hand();
        for(var card:hand.reversed())if(mx>=handLeft() && mx<handRight() && handHits.containsKey(card.instanceId()) && handHits.get(card.instanceId()).contains(mx,my)){
            long id=card.instanceId();
            if(ownedChoice!=null){if(button==0)selectOwnedPromptCard(id);return true;}
            if(card!=null && !handCardBlockedReason(card).isEmpty())return true;
            if(!selected.remove(id)){
                if(!myPrompt() && view.snapshot().phaseOrdinal()==Phase.DISCARD.ordinal()
                    && selected.size()>=self().requiredDiscard())return true;
                if(costSkill.isEmpty() && !myPrompt() && view.snapshot().phaseOrdinal()==Phase.PLAY.ordinal())selected.clear();
                selected.add(id);
            }
            if(!myPrompt() && costSkill.isEmpty())clearCardTargets();
            convertedCard=-1;convertedType=-1;reason="";rebuildWidgets();return true;
        }
        for(var entry:seatHits.entrySet())if(entry.getValue().contains(mx,my)){
            if(tuxiPrompt()){
                var p=view.snapshot().prompt();int seat=entry.getKey();
                if(button==0 && zonePendingPrompt!=p.promptId() && p.allowedTargetSeats().contains(seat)){
                    if(!attackTargets.remove(seat) && attackTargets.size()<2)attackTargets.add(seat);
                    if(attackTargets.size()==2)submitTuxi();else rebuildWidgets();
                }
                return true;
            }
            if(portraitTargetPrompt()){
                var p=view.snapshot().prompt();
                if(button==0 && zonePendingPrompt!=p.promptId() && p.allowedTargetSeats().contains(entry.getKey())){
                    target=entry.getKey();zonePendingPrompt=p.promptId();
                    MatchClient.send(MatchAction.skillChoice(MatchClient.nextNonce(),p.promptId(),0,target));
                }
                return true;
            }
            String blocked=targetHint(entry.getKey());if(blocked!=null && !blocked.isEmpty())return true;
            if(fangtianSelection()){
                int seat=entry.getKey();var candidate=view.snapshot().seats().stream().filter(p->p.seat()==seat).findFirst().orElse(null);
                if(candidate==null || !candidate.alive() || candidate.self()){reason=tr("sgs.ui.choose_another_living_player_67c9483d");return true;}
                if(!attackTargets.remove(seat)){
                    if(attackTargets.size()>=3){reason=tr("sgs.ui.sky_piercer_allows_up_to_three_targets_e992fc70");return true;}
                    attackTargets.add(seat);
                }
                target=attackTargets.isEmpty()?-1:attackTargets.iterator().next();secondary=-1;reason="";rebuildWidgets();return true;
            }
            attackTargets.clear();
            var physical=selectedPhysical();boolean borrow=costSkill.isEmpty() && physical!=null && (declaredType(physical)==CardType.JIEDAO || declaredType(physical)==CardType.TIESUO);
            if(target==entry.getKey()){clearCardTargets();rebuildWidgets();return true;}
            if(secondary==entry.getKey()){secondary=-1;rebuildWidgets();return true;}
            if(borrow && target>=0 && target!=entry.getKey())secondary=entry.getKey();
            else{target=entry.getKey();secondary=-1;}
            reason="";rebuildWidgets();return true;
        }
        return false;
    }
    public MatchNetwork.View stateForSmoke(){return view;}
    public dev.sgs.core.engine.SkillChoice.DeckOrder privateDeckOrderForSmoke(){ensureDeckOrder();return deckOrder.submitted();}
    public int[] privateDeckCardForSmoke(long id){var r=deckCardHits.get(id);if(r==null)throw new IllegalStateException("private card not rendered");return new int[]{r.x(),r.y(),r.w(),r.h()};}
    public int[] privateDeckLaneForSmoke(int lane){var r=deckLane(lane);return new int[]{r.x(),r.y(),r.w(),r.h()};}
    public int[] skinStripForSmoke(){var r=skinStrip();return new int[]{r.x()+r.w()/2,r.y()+r.h()/2};}
    public String skinForSmoke(){return view.skins().getOrDefault(self().seat(),"classic");}
    public String heroForSkinSmoke(){return self().heroId();}
    public void layoutFixtureForSmoke(int count,boolean shared){
        if(!Boolean.getBoolean("sgs.uiSmoke"))throw new IllegalStateException("UI fixture requires opt-in");
        fixtureCount=count;fixtureShared=shared;selected.clear();attackTargets.clear();handOffset=0;rebuildWidgets();
    }
    public int[] handBoundsForSmoke(int index){
        var me=self();if(me==null || index>=me.hand().size())return null;
        var rect=handHits.get(me.hand().get(index).instanceId());var layout=handLayout(me.hand().size());
        return rect==null?null:new int[]{rect.x(),rect.y(),layout.exposedWidth(index),rect.h()};
    }
    public int[] badgeBoundsForSmoke(boolean own){
        var seat=view.snapshot().seats().stream().filter(s->s.self()==own).findFirst().orElseThrow();
        var r=countHits.get(seat.seat());return new int[]{r.x(),r.y(),r.w(),r.h(),seat.seat()};
    }
    public int previewedSeatForSmoke(){return previewedSeat;}
    public String visiblePaperTipForSmoke(){return visiblePaperTip;}
    public Map<Integer,String> relationBadgesForSmoke(){return Map.copyOf(relationBadges);}
    public boolean hoverEnemyHandForSmoke(){
        var me=self();if(me==null || me.teamId()<0)return false;
        var enemy=view.snapshot().seats().stream().filter(p->p.teamId()>=0 && p.teamId()!=me.teamId()).findFirst().orElse(null);
        if(enemy==null)return false;var hit=countHits.get(enemy.seat());if(hit==null)return false;
        smokePointer=new int[]{hit.x()+hit.w()/2,hit.y()+hit.h()/2};return true;
    }
    public void hoverForSmoke(int x,int y){
        if(!Boolean.getBoolean("sgs.betaTodoRender") && !Boolean.getBoolean("sgs.uiSmoke") && !Boolean.getBoolean("sgs.heroSkinSmoke") && !RevealedGiftWorldRestartProbe.enabled() && !TargetRedirectWorldRestartProbe.enabled())throw new IllegalStateException("UI pointer requires opt-in");
        smokePointer=x<0?null:new int[]{x,y};
    }
    public int errorsForSmoke(){return errors;}
    public String errorForSmoke(){return errorCode;}
    public String feedbackForSmoke(){return feedbackMessage;}
    public boolean overlayForSmoke(){return !overlay.isEmpty();}
    public boolean cardsOpenForSmoke(){return cardsOpen;}
    public String controlsForSmoke(){return "status="+view.snapshot().status()+" prompt="+view.snapshot().prompt()+" overlay="+overlay+" cards="+cardsOpen+" logs="+logOpen+" buttons="+testButtons.keySet();}
    public boolean focusSkillForSmoke(){
        for(var child:children())if(child instanceof SkillWidget widget){setFocused(widget);smokePointer=new int[]{widget.getX()+widget.getWidth()/2,widget.getY()+widget.getHeight()/2};return true;}
        return false;
    }
    public void clearSkillFocusForSmoke(){setFocused(null);smokePointer=null;}
    public boolean skillNoticeForSmoke(){return activeNotice!=null;}
    public boolean skillPortraitForSmoke(){return activeNotice!=null && announcedPortraitSeat==activeNotice.seat();}
    public boolean skillPortraitSettledForSmoke(){return activeNotice!=null && System.currentTimeMillis()-noticeStarted>=400 && System.currentTimeMillis()-noticeStarted<1500;}
    public DecisionOverlayLayout.Layout decisionLayoutForSmoke(){return decisionLayout();}
    public void verifySkillGeometryForSmoke(){
        var widgets=children().stream().filter(child->child instanceof SkillWidget).map(child->(SkillWidget)child).toList();
        var hp=board().selfHealthLane();
        for(var widget:widgets)if(widget.getX()+widget.getWidth()>=hp.x())
            throw new IllegalStateException("Skill button entered the reserved health lane");
        for(var widget:widgets)if(widget.getWidth()!=widgets.getFirst().getWidth() || widget.getHeight()!=widgets.getFirst().getHeight())
            throw new IllegalStateException("Inconsistent skill button dimensions");
        for(var widget:widgets)if(widget.getX()<selfX() || widget.getY()<board().self().y()
            || widget.getX()+widget.getWidth()>selfX()+heroWidth() || widget.getY()+widget.getHeight()+2>board().self().y()+heroHeight())
            throw new IllegalStateException("Skill button escaped its actual portrait, including its shadow");
        // Exterior limited-skill seals legitimately have tooltips; only interior strips are forbidden.
        for(var tip:skillTips.keySet())for(var seat:seatHits.values())
            if(tip.x()<seat.x()+seat.w() && tip.x()+tip.w()>seat.x()
                && tip.y()<seat.y()+seat.h() && tip.y()+tip.h()>seat.y())
                throw new IllegalStateException("Portrait contains permanent skill strips");
    }
    private boolean wuxiePending(){return wuxiePendingPromptId!=0 && wuxiePendingPromptId==currentWuxiePromptId();}
    public boolean wuxiePendingForSmoke(){return wuxiePending();}
    String heroPreviewForSmoke(){return heroPreview;}
    boolean heroPendingForSmoke(){return heroPending;}
    public boolean openingPendingForSmoke(){return openingPending;}
    void twoTargetsForSmoke(long card){if(!Boolean.getBoolean("sgs.betaTodoRender"))throw new IllegalStateException("fixture only");selected.clear();selected.add(card);target=2;secondary=3;attackTargets.clear();attackTargets.add(2);attackTargets.add(3);}
    boolean targetsClearedForSmoke(){return target==-1&&secondary==-1&&attackTargets.isEmpty();}
    void resultPageForSmoke(int stage){if(!Boolean.getBoolean("sgs.betaTodoRender"))throw new IllegalStateException("fixture only");resultStage=stage;rebuildWidgets();}
    int tuxiTargetCountForSmoke(){return attackTargets.size();}
    boolean tuxiPortraitOnlyForSmoke(){return tuxiPrompt() && testButtons.keySet().stream().noneMatch(k->k.startsWith("skill-target:")||k.equals("skill-choice-confirm"));}
    boolean portraitOnlyForSmoke(){return portraitTargetPrompt() && testButtons.keySet().stream().noneMatch(k->k.startsWith("skill-target:"));}
    public int resultStageForSmoke(){return resultStage;}
    public long candidateControlCountForSmoke(){return testButtons.keySet().stream().filter(key->key.startsWith("hero:")).count();}
    public boolean identityPendingForSmoke(){return identityPending;}
    public boolean longdanForSmoke(){return longdan;}
    public boolean qixiForSmoke(){return qixi;}
    public boolean qingguoForSmoke(){return qingguo;}
    public boolean guoseForSmoke(){return guose;}
    public int declaredTypeForSmoke(long cardId){var card=ownedCards(self()).stream().filter(c->c.instanceId()==cardId).findFirst().orElseThrow();return declaredType(card).ordinal();}
    public boolean enabledForSmoke(String key){var b=testButtons.get(key);return b!=null && b.active;}
    public void verifySuitGeometryForSmoke(){
        int width=-1,height=-1,right=-1;
        for(int i=0;i<4;i++){var b=testButtons.get("suit:"+i);
            if(b==null||!b.active||b.getX()<0||b.getY()<0||b.getX()+b.getWidth()>this.width||b.getY()+b.getHeight()>this.height
                ||i>0&&(b.getWidth()!=width||b.getHeight()!=height||b.getX()<right))throw new IllegalStateException("four suit controls differ or overflow");
            width=b.getWidth();height=b.getHeight();right=b.getX()+width;
        }
    }
    private boolean clickOwnedChoiceForSmoke(String key){
        if(!key.startsWith("skill-choice:") || ownedPromptChoice()==null)return false;
        long id=Long.parseLong(key.substring("skill-choice:".length()));
        if(!ownedPromptChoice().allowsCard(id))throw new IllegalStateException("Unoffered owned card "+id);
        var hand=self().hand();
        for(int i=0;i<hand.size();i++)if(hand.get(i).instanceId()==id){
            var hit=handBoundsForSmoke(i);if(hit==null || !mouseClicked(hit[0]+hit[2]/2d,hit[1]+hit[3]/2d,0))throw new IllegalStateException("Missing owned hand hit "+id);return true;
        }
        var hit=equipmentHits.get(id);if(hit==null || !mouseClicked(hit.x()+hit.w()/2d,hit.y()+hit.h()/2d,0))throw new IllegalStateException("Missing owned equipment hit "+id);return true;
    }
    public void pressForSmoke(String key){if(clickOwnedChoiceForSmoke(key))return;var b=testButtons.get(key);if(b==null || !b.active)throw new IllegalStateException("Unavailable match button "+key);b.onPress();}
    public void clickForSmoke(String key){if(clickOwnedChoiceForSmoke(key))return;var b=testButtons.get(key);if(b==null || !b.active || !mouseClicked(b.getX()+b.getWidth()/2d,b.getY()+b.getHeight()/2d,0))throw new IllegalStateException("Unavailable match click "+key);}
    public void selectForSmoke(Collection<Long> ids,int seat){
        if(ownedPromptChoice()!=null){if(!ids.isEmpty()){selected.clear();skillChoiceCard=ids.size()==1?ids.iterator().next():-1;selected.addAll(ids);}target=seat;rebuildWidgets();return;}
        selected.clear();attackTargets.clear();selected.addAll(ids);target=seat;
        rebuildWidgets();
    }
    void combatFrameForSmoke(boolean hit,long age){
        if(!Boolean.getBoolean("sgs.videoEffectsRender"))throw new IllegalStateException("Combat fixture requires opt-in");
        long now=net.minecraft.Util.getMillis();
        if(hit){target=-1;damageFeedback.clear();damageFeedback.observe(1,4,3,now-age);}
        else{target=1;targetEffectStarted.put(1,now-age);}
    }
    public boolean ownedChoiceUsesCardsForSmoke(){return ownedPromptChoice()!=null && testButtons.keySet().stream().noneMatch(k->k.startsWith("skill-choice:"));}
    public int[] firstHandBoundsForSmoke(){
        var me=self();if(me==null || me.hand().isEmpty())return null;
        int index=-1;for(int i=0;i<me.hand().size();i++)if(handCardBlockedReason(me.hand().get(i)).isEmpty()){index=i;break;}
        if(index<0)return null;var rect=handHits.get(me.hand().get(index).instanceId());var layout=handLayout(me.hand().size());
        return rect==null?null:new int[]{rect.x(),rect.y(),Math.min(rect.w(),layout.exposedWidth(index)),rect.h()};
    }
    @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical){
        if(chatPanel!=null&&chatPanel.scroll(vertical!=0?vertical:horizontal))return true;
        if(logOpen){
            if(hasShiftDown()||horizontal!=0)logScrollX=Math.clamp(logScrollX-(horizontal!=0?horizontal:vertical)*24,0,logMaxX);
            else{logScrollY=Math.clamp(logScrollY-vertical*36,0,logMaxY);logFollowLatest=logScrollY>=logMaxY-1;}
            return true;
        }
        if(!logOpen && my>=height-7-Math.round(112*handScale())-selectedLift() && mx>=handLeft() && mx<handRight()){
            handOffset=Math.max(0,handOffset-(int)((horizontal!=0?horizontal:vertical)*24));return true;
        }
        return super.mouseScrolled(mx,my,horizontal,vertical);
    }
    public boolean fangtianSelectionForSmoke(){return fangtianSelection();}
    public Set<Integer> attackTargetsForSmoke(){return Set.copyOf(attackTargets);}
    public Map<Integer,Integer> attackTargetMarkersForSmoke(){return Map.copyOf(attackTargetMarkers);}
    public int selectedCountForSmoke(){return selected.size();}
    public boolean handIndexSelectedForSmoke(int index){
        var me=self();return me!=null && index>=0 && index<me.hand().size() && selected.contains(me.hand().get(index).instanceId());
    }
    public boolean responseCardSelectableForSmoke(long id){return self().hand().stream().filter(c->c.instanceId()==id).findFirst().map(this::responseCardSelectable).orElse(false);}
    public boolean handCardSelectableForSmoke(long id){return self().hand().stream().filter(c->c.instanceId()==id).anyMatch(c->handCardBlockedReason(c).isEmpty());}
    public String targetHintForSmoke(int seat){return targetHint(seat);}
    public boolean targetQueryPendingForSmoke(){return targetQueryPending;}
    public long skillChoiceCardForSmoke(){return skillChoiceCard;}
    public int targetForSmoke(){return target;}
    public void clickSeatForSmoke(int seat){var r=seatHits.get(seat);if(r==null || !mouseClicked(r.x()+r.w()/2d,r.y()+r.h()/2d,0))throw new IllegalStateException("Missing seat hitbox");}
    public boolean customControlsForSmoke(){return children().stream().filter(c->c instanceof Button).allMatch(c->c instanceof SgsButton);}
    public void focusSkillForSmoke(boolean focused){var skill=testButtons.getOrDefault("longdan",testButtons.get("skill"));if(skill!=null)skill.setFocused(focused);}
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float partial){}
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(chatPanel!=null&&chatPanel.key(key,scan,modifiers))return true;
        return super.keyPressed(key,scan,modifiers);
    }
    @Override public boolean charTyped(char c,int modifiers){
        if(chatPanel!=null&&chatPanel.character(c,modifiers))return true;
        return super.charTyped(c,modifiers);
    }
    void chatDraftForSmoke(String text){chatPanel.draftForSmoke(text);}
    @Override public void onClose(){
        if(MatchChatClient.open){chatPanel.close();return;}

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
}
