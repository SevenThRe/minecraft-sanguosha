package dev.sgs.core.match.dto;

import java.util.List;

import dev.sgs.core.card.CardInstance;

/**
 * Wire views for a match. Everything here is a plain immutable record carrying only primitive
 * ordinals and strings, so the integration layer can serialize it without touching engine types.
 * Enums are referenced by ordinal; both sides must keep the enum order unchanged.
 */
public final class MatchViews {

  public static final int STATUS_SELECTING = 0;
  public static final int STATUS_RUNNING = 1;
  public static final int STATUS_FINISHED = 2;

  public static final int HIDDEN = -1;

  private MatchViews() {}

  /** Physical card view. Only ever included for cards the receiver is allowed to see. */
  public record CardPlayOption(int declaredTypeOrdinal,boolean available,String reason) {}
  public record CardView(long instanceId, int typeOrdinal, int suitOrdinal, int rankOrdinal,List<dev.sgs.core.card.CardSkillMark> marks,List<CardPlayOption> playOptions) {
    public CardView {marks=marks==null?List.of():List.copyOf(marks);playOptions=playOptions==null?List.of():List.copyOf(playOptions);}
    public CardView(long instanceId,int typeOrdinal,int suitOrdinal,int rankOrdinal,List<dev.sgs.core.card.CardSkillMark> marks){this(instanceId,typeOrdinal,suitOrdinal,rankOrdinal,marks,List.of());}
    public CardView(long instanceId,int typeOrdinal,int suitOrdinal,int rankOrdinal){this(instanceId,typeOrdinal,suitOrdinal,rankOrdinal,List.of());}
    public CardView withPlayOptions(List<CardPlayOption> options){return new CardView(instanceId,typeOrdinal,suitOrdinal,rankOrdinal,marks,options);}

    public static CardView of(CardInstance card) {
      return of(card,false,false);
    }
    public static CardView of(CardInstance card,boolean owner,boolean handVisible) {
      return new CardView(
          card.instanceId(), card.type().ordinal(), card.suit().ordinal(), card.rank().ordinal(),card.skillMarks().stream().filter(mark->
              mark.visibility()==dev.sgs.core.card.CardSkillMark.Visibility.PUBLIC || owner
              || handVisible && mark.visibility()==dev.sgs.core.card.CardSkillMark.Visibility.HAND_VIEWERS).toList());
    }
  }

  /** Public rule effect only; never contains private skill memory or hidden cards. */
  public record StatusView(String id,String label,String description) {}

  public record ControlView(String source,long version) {public boolean automatic(){return !"MANUAL".equals(source);}}

  /** One seat as seen by one specific viewer. */
  public record SeatView(
      int seat,
      String name,
      boolean human,
      boolean online,
      boolean alive,
      String heroId,
      int kingdomOrdinal,
      int hp,
      int maxHp,
      int identityOrdinal,
      int handCount,
      CardView weapon,
      CardView armor,
      CardView horsePlus,
      CardView horseMinus,
      List<CardView> delay,
      List<CardView> hand,
      boolean self,
      boolean handVisible,
      boolean chained,
      int teamId,CardView treasure,List<StatusView> statuses,ControlView control,Integer distanceFromViewer,Integer handLimit) {
    public SeatView(int seat,String name,boolean human,boolean online,boolean alive,String heroId,
        int kingdomOrdinal,int hp,int maxHp,int identityOrdinal,int handCount,CardView weapon,
        CardView armor,CardView horsePlus,CardView horseMinus,List<CardView> delay,List<CardView> hand,
        boolean self,boolean handVisible,boolean chained,int teamId,CardView treasure,List<StatusView> statuses,ControlView control,Integer distanceFromViewer){
      this(seat,name,human,online,alive,heroId,kingdomOrdinal,hp,maxHp,identityOrdinal,handCount,
          weapon,armor,horsePlus,horseMinus,delay,hand,self,handVisible,chained,teamId,treasure,statuses,control,distanceFromViewer,null);
    }
    public SeatView withHandLimit(int limit){
      return new SeatView(seat,name,human,online,alive,heroId,kingdomOrdinal,hp,maxHp,identityOrdinal,handCount,
          weapon,armor,horsePlus,horseMinus,delay,hand,self,handVisible,chained,teamId,treasure,statuses,control,distanceFromViewer,limit);
    }
    public int requiredDiscard(){return Math.max(0,handCount-Math.max(0,handLimit==null?hp:handLimit));}
    public SeatView(int seat,String name,boolean human,boolean online,boolean alive,String heroId,
        int kingdomOrdinal,int hp,int maxHp,int identityOrdinal,int handCount,CardView weapon,
        CardView armor,CardView horsePlus,CardView horseMinus,List<CardView> delay,List<CardView> hand,
        boolean self,boolean handVisible,boolean chained,int teamId,CardView treasure,List<StatusView> statuses,ControlView control){
      this(seat,name,human,online,alive,heroId,kingdomOrdinal,hp,maxHp,identityOrdinal,handCount,
          weapon,armor,horsePlus,horseMinus,delay,hand,self,handVisible,chained,teamId,treasure,statuses,control,null);
    }
    public SeatView withDistanceFromViewer(Integer distance){
      return new SeatView(seat,name,human,online,alive,heroId,kingdomOrdinal,hp,maxHp,identityOrdinal,handCount,
          weapon,armor,horsePlus,horseMinus,delay,hand,self,handVisible,chained,teamId,treasure,statuses,control,distance,handLimit);
    }
    public SeatView withKingdom(int value){return new SeatView(seat,name,human,online,alive,heroId,value,hp,maxHp,identityOrdinal,handCount,weapon,armor,horsePlus,horseMinus,delay,hand,self,handVisible,chained,teamId,treasure,statuses,control,distanceFromViewer,handLimit);}
    public SeatView {control=control==null?new ControlView("MANUAL",0):control;statuses=statuses==null?List.of():List.copyOf(statuses);}
    public SeatView withControl(ControlView value){
      return new SeatView(seat,name,human,online,alive,heroId,kingdomOrdinal,hp,maxHp,identityOrdinal,handCount,
          weapon,armor,horsePlus,horseMinus,delay,hand,self,handVisible,chained,teamId,treasure,statuses,value,distanceFromViewer,handLimit);
    }
    public SeatView(int seat,String name,boolean human,boolean online,boolean alive,String heroId,
        int kingdomOrdinal,int hp,int maxHp,int identityOrdinal,int handCount,CardView weapon,
        CardView armor,CardView horsePlus,CardView horseMinus,List<CardView> delay,List<CardView> hand,
        boolean self,boolean handVisible,boolean chained,int teamId,CardView treasure,List<StatusView> statuses){
      this(seat,name,human,online,alive,heroId,kingdomOrdinal,hp,maxHp,identityOrdinal,handCount,weapon,armor,horsePlus,horseMinus,delay,hand,self,handVisible,chained,teamId,treasure,statuses,null);
    }
    public SeatView withPlayOptions(java.util.Map<Long,List<CardPlayOption>> options){
      java.util.function.Function<CardView,CardView> annotate=c->c==null?null:c.withPlayOptions(options.getOrDefault(c.instanceId(),List.of()));
      return new SeatView(seat,name,human,online,alive,heroId,kingdomOrdinal,hp,maxHp,identityOrdinal,handCount,
          annotate.apply(weapon),annotate.apply(armor),annotate.apply(horsePlus),annotate.apply(horseMinus),delay,
          hand.stream().map(annotate).toList(),self,handVisible,chained,teamId,annotate.apply(treasure),statuses,control,distanceFromViewer,handLimit);
    }
    public SeatView(int seat,String name,boolean human,boolean online,boolean alive,String heroId,
        int kingdomOrdinal,int hp,int maxHp,int identityOrdinal,int handCount,CardView weapon,
        CardView armor,CardView horsePlus,CardView horseMinus,List<CardView> delay,List<CardView> hand,
        boolean self,boolean handVisible,boolean chained,int teamId,CardView treasure){
      this(seat,name,human,online,alive,heroId,kingdomOrdinal,hp,maxHp,identityOrdinal,handCount,weapon,armor,horsePlus,horseMinus,delay,hand,self,handVisible,chained,teamId,treasure,List.of());
    }
    public SeatView(int seat,String name,boolean human,boolean online,boolean alive,String heroId,
        int kingdomOrdinal,int hp,int maxHp,int identityOrdinal,int handCount,CardView weapon,
        CardView armor,CardView horsePlus,CardView horseMinus,List<CardView> delay,List<CardView> hand,
        boolean self,boolean handVisible,boolean chained,int teamId){
      this(seat,name,human,online,alive,heroId,kingdomOrdinal,hp,maxHp,identityOrdinal,handCount,weapon,armor,horsePlus,horseMinus,delay,hand,self,handVisible,chained,teamId,null);
    }
    public SeatView(int seat,String name,boolean human,boolean online,boolean alive,String heroId,
        int kingdomOrdinal,int hp,int maxHp,int identityOrdinal,int handCount,CardView weapon,
        CardView armor,CardView horsePlus,CardView horseMinus,List<CardView> delay,List<CardView> hand,
        boolean self,boolean handVisible,boolean chained){
      this(seat,name,human,online,alive,heroId,kingdomOrdinal,hp,maxHp,identityOrdinal,handCount,
          weapon,armor,horsePlus,horseMinus,delay,hand,self,handVisible,chained,HIDDEN);
    }
    public SeatView(int seat,String name,boolean human,boolean online,boolean alive,String heroId,
        int kingdomOrdinal,int hp,int maxHp,int identityOrdinal,int handCount,CardView weapon,
        CardView armor,CardView horsePlus,CardView horseMinus,List<CardView> delay,List<CardView> hand,boolean self,boolean handVisible) {
      this(seat,name,human,online,alive,heroId,kingdomOrdinal,hp,maxHp,identityOrdinal,handCount,
          weapon,armor,horsePlus,horseMinus,delay,hand,self,handVisible,false,HIDDEN);
    }
    public SeatView(int seat,String name,boolean human,boolean online,boolean alive,String heroId,
        int kingdomOrdinal,int hp,int maxHp,int identityOrdinal,int handCount,CardView weapon,
        CardView armor,CardView horsePlus,CardView horseMinus,List<CardView> delay,List<CardView> hand,boolean self) {
      this(seat,name,human,online,alive,heroId,kingdomOrdinal,hp,maxHp,identityOrdinal,handCount,
          weapon,armor,horsePlus,horseMinus,delay,hand,self,self,false,HIDDEN);
    }
  }

  /**
   * Active settlement prompt. Public fields (type/asker/subject/deadline) go to everyone; allowed
   * types and the Wugu choice pool go to the asker only.
   */
  public record PromptView(
      long promptId,
      int typeOrdinal,
      int askerSeat,
      int subjectSeat,
      long deadlineMillis,
      List<Integer> allowedTypeOrdinals,
      List<CardView> choices,
      String skillId,List<Integer> allowedTargetSeats,boolean hiddenHandAvailable) {
    public PromptView {allowedTypeOrdinals=List.copyOf(allowedTypeOrdinals);choices=List.copyOf(choices);allowedTargetSeats=List.copyOf(allowedTargetSeats);}
    public PromptView(long promptId,int typeOrdinal,int askerSeat,int subjectSeat,long deadlineMillis,
        List<Integer> allowedTypeOrdinals,List<CardView> choices,String skillId){
      this(promptId,typeOrdinal,askerSeat,subjectSeat,deadlineMillis,allowedTypeOrdinals,choices,skillId,List.of(),false);
    }
    public PromptView(long promptId,int typeOrdinal,int askerSeat,int subjectSeat,long deadlineMillis,
        List<Integer> allowedTypeOrdinals,List<CardView> choices){
      this(promptId,typeOrdinal,askerSeat,subjectSeat,deadlineMillis,allowedTypeOrdinals,choices,"");
    }
  }

  /** A judgment card that was just revealed. */
  public record JudgmentView(int seat, CardView card) {}

  /** Only movements whose physical identities are public; never contains private draw order. */
  public record PublicMoveView(long sequence, int actorSeat, String reason, List<CardView> cards,
      String fromKind,int fromSeat,String toKind,int toSeat) {
    public PublicMoveView(long sequence,int actorSeat,String reason,List<CardView> cards){this(sequence,actorSeat,reason,cards,"",-1,"",-1);}
  }
  /** Per-recipient committed movement. Empty faces means anonymous backs, never hidden physical IDs. */
  public record CardMotionView(long sequence,int actorSeat,String reason,String fromKind,int fromSeat,
      String toKind,int toSeat,int count,List<CardView> faces) {
    public CardMotionView {faces=List.copyOf(faces);if(count<0 || !faces.isEmpty()&&faces.size()!=count)throw new IllegalArgumentException("motion card count");}
  }
  /** Public receipt of a successful skill use; contains no role, card, or private choice data. */
  public record PublicSkillActivationView(long sequence,int seat,String skillId) {}

  /** Private pre-selection identity presentation: 0 unrevealed, 1 revealed, 2 confirmed. */
  public record IdentityIntroView(int stage,long deadlineMillis,int chosenBackSlot) {}

  public record OpeningHandView(int stage,long deadlineMillis) {}
  /** Owner-only capability receipt; contains no skill memory or another player's status. */
  public record SkillAvailabilityView(String skillId,boolean available,String reason) {}

  public record TargetAvailabilityView(long cardId,int targetSeat,String reason) {}

  public record CounterspellWindowView(long windowId,int subjectSeat,int chainDepth,long deadlineMillis) {}

  /** Already-public real cards, separate from processing and private hands. */
  public record PublicCardRevealView(String id,int ownerSeat,int subjectSeat,String reason,List<CardView> cards,boolean held){
    public PublicCardRevealView{cards=List.copyOf(cards);}
    public static PublicCardRevealView of(dev.sgs.core.game.PublicCardReveal r){return new PublicCardRevealView(r.id(),r.owner(),r.subject(),r.reason(),r.cards().stream().map(CardView::of).toList(),r.held());}
  }

  public record HeroBanChoice(int seat,String heroId) {}
  public record HeroBanView(int stage,long deadlineMillis,List<String> candidates,List<HeroBanChoice> choices) {
    public HeroBanView {candidates=List.copyOf(candidates);choices=List.copyOf(choices);}
  }

  public record DraftSlot(CardView card,int recipient) {}
  public record DraftView(long id,int source,int picker,String title,List<DraftSlot> slots,boolean active){
    public DraftView{slots=List.copyOf(slots);}
  }
  public record TableEffect(long id,String kind,int source,int target,String label,List<CardView> cards,boolean success){
    public TableEffect{cards=List.copyOf(cards);}
  }
  public record TablePresentation(DraftView draft,List<TableEffect> effects,List<Long> damageTotals,boolean completeStatistics){
    public TablePresentation{effects=List.copyOf(effects);damageTotals=List.copyOf(damageTotals);}
    public static TablePresentation empty(){return new TablePresentation(null,List.of(),List.of(),false);}
  }

  /** Per-viewer full-table snapshot. */
  public record MatchSnapshot(
      int status,
      int viewerSeat,
      int turnNumber,
      int phaseOrdinal,
      int currentSeat,
      int lordSeatOrdinal,
      int drawPileCount,
      int discardPileCount, // Retained wire field; ordinary views always send HIDDEN, not a debug counter.
      long phaseDeadlineMillis,
      int outcomeOrdinal,
      List<String> logLines,
      List<SeatView> seats,
      PromptView prompt,
      List<String> heroCandidates,
      long selectionDeadlineMillis,
      JudgmentView judgment,
      List<CardView> processing,
      List<PublicMoveView> publicMoves,
      List<PublicSkillActivationView> skillActivations,
      List<dev.sgs.core.addon.HeroPresentation> heroPresentations,
      IdentityIntroView identityIntro,
      OpeningHandView openingHand,
      List<SkillAvailabilityView> selfSkillAvailability,
      CounterspellWindowView counterspellWindow,
      List<TargetAvailabilityView> selfTargetAvailability,
      List<PublicCardRevealView> publicCardReveals,
      HeroBanView heroBan,List<CardMotionView> cardMotions, TablePresentation tablePresentation) {
    public MatchSnapshot(
      int status,
      int viewerSeat,
      int turnNumber,
      int phaseOrdinal,
      int currentSeat,
      int lordSeatOrdinal,
      int drawPileCount,
      int discardPileCount, // Retained wire field; ordinary views always send HIDDEN, not a debug counter.
      long phaseDeadlineMillis,
      int outcomeOrdinal,
      List<String> logLines,
      List<SeatView> seats,
      PromptView prompt,
      List<String> heroCandidates,
      long selectionDeadlineMillis,
      JudgmentView judgment,
      List<CardView> processing,
      List<PublicMoveView> publicMoves,
      List<PublicSkillActivationView> skillActivations,
      List<dev.sgs.core.addon.HeroPresentation> heroPresentations,
      IdentityIntroView identityIntro,
      OpeningHandView openingHand,
      List<SkillAvailabilityView> selfSkillAvailability,
      CounterspellWindowView counterspellWindow,
      List<TargetAvailabilityView> selfTargetAvailability,
      List<PublicCardRevealView> publicCardReveals,
      HeroBanView heroBan,List<CardMotionView> cardMotions) {this(status,viewerSeat,turnNumber,phaseOrdinal,currentSeat,lordSeatOrdinal,drawPileCount,discardPileCount,phaseDeadlineMillis,outcomeOrdinal,logLines,seats,prompt,heroCandidates,selectionDeadlineMillis,judgment,processing,publicMoves,skillActivations,heroPresentations,identityIntro,openingHand,selfSkillAvailability,counterspellWindow,selfTargetAvailability,publicCardReveals,heroBan,cardMotions,TablePresentation.empty());}
    public MatchSnapshot withTablePresentation(TablePresentation value){return new MatchSnapshot(status,viewerSeat,turnNumber,phaseOrdinal,currentSeat,lordSeatOrdinal,drawPileCount,discardPileCount,phaseDeadlineMillis,outcomeOrdinal,logLines,seats,prompt,heroCandidates,selectionDeadlineMillis,judgment,processing,publicMoves,skillActivations,heroPresentations,identityIntro,openingHand,selfSkillAvailability,counterspellWindow,selfTargetAvailability,publicCardReveals,heroBan,cardMotions,value);}

  public MatchSnapshot(
      int status,
      int viewerSeat,
      int turnNumber,
      int phaseOrdinal,
      int currentSeat,
      int lordSeatOrdinal,
      int drawPileCount,
      int discardPileCount, // Retained wire field; ordinary views always send HIDDEN, not a debug counter.
      long phaseDeadlineMillis,
      int outcomeOrdinal,
      List<String> logLines,
      List<SeatView> seats,
      PromptView prompt,
      List<String> heroCandidates,
      long selectionDeadlineMillis,
      JudgmentView judgment,
      List<CardView> processing,
      List<PublicMoveView> publicMoves,
      List<PublicSkillActivationView> skillActivations,
      List<dev.sgs.core.addon.HeroPresentation> heroPresentations,
      IdentityIntroView identityIntro,
      OpeningHandView openingHand,
      List<SkillAvailabilityView> selfSkillAvailability,
      CounterspellWindowView counterspellWindow,
      List<TargetAvailabilityView> selfTargetAvailability,
      List<PublicCardRevealView> publicCardReveals,
      HeroBanView heroBan) {
      this(status,viewerSeat,turnNumber,phaseOrdinal,currentSeat,lordSeatOrdinal,drawPileCount,discardPileCount,phaseDeadlineMillis,outcomeOrdinal,logLines,seats,prompt,heroCandidates,selectionDeadlineMillis,judgment,processing,publicMoves,skillActivations,heroPresentations,identityIntro,openingHand,selfSkillAvailability,counterspellWindow,selfTargetAvailability,publicCardReveals,heroBan,List.of());
    }

    public MatchSnapshot(
      int status,
      int viewerSeat,
      int turnNumber,
      int phaseOrdinal,
      int currentSeat,
      int lordSeatOrdinal,
      int drawPileCount,
      int discardPileCount, // Retained wire field; ordinary views always send HIDDEN, not a debug counter.
      long phaseDeadlineMillis,
      int outcomeOrdinal,
      List<String> logLines,
      List<SeatView> seats,
      PromptView prompt,
      List<String> heroCandidates,
      long selectionDeadlineMillis,
      JudgmentView judgment,
      List<CardView> processing,
      List<PublicMoveView> publicMoves,
      List<PublicSkillActivationView> skillActivations,
      List<dev.sgs.core.addon.HeroPresentation> heroPresentations,
      IdentityIntroView identityIntro,
      OpeningHandView openingHand,
      List<SkillAvailabilityView> selfSkillAvailability,
      CounterspellWindowView counterspellWindow,
      List<TargetAvailabilityView> selfTargetAvailability,
      List<PublicCardRevealView> publicCardReveals) {
      this(status,viewerSeat,turnNumber,phaseOrdinal,currentSeat,lordSeatOrdinal,drawPileCount,discardPileCount,phaseDeadlineMillis,outcomeOrdinal,logLines,seats,prompt,heroCandidates,selectionDeadlineMillis,judgment,processing,publicMoves,skillActivations,heroPresentations,identityIntro,openingHand,selfSkillAvailability,counterspellWindow,selfTargetAvailability,publicCardReveals,null);
    }

    /** Source-compatible constructor for views produced before typed public disclosures. */
    public MatchSnapshot(
      int status,
      int viewerSeat,
      int turnNumber,
      int phaseOrdinal,
      int currentSeat,
      int lordSeatOrdinal,
      int drawPileCount,
      int discardPileCount, // Retained wire field; ordinary views always send HIDDEN, not a debug counter.
      long phaseDeadlineMillis,
      int outcomeOrdinal,
      List<String> logLines,
      List<SeatView> seats,
      PromptView prompt,
      List<String> heroCandidates,
      long selectionDeadlineMillis,
      JudgmentView judgment,
      List<CardView> processing,
      List<PublicMoveView> publicMoves,
      List<PublicSkillActivationView> skillActivations,
      List<dev.sgs.core.addon.HeroPresentation> heroPresentations,
      IdentityIntroView identityIntro,
      OpeningHandView openingHand,
      List<SkillAvailabilityView> selfSkillAvailability,
      CounterspellWindowView counterspellWindow,
      List<TargetAvailabilityView> selfTargetAvailability) {
      this(status,viewerSeat,turnNumber,phaseOrdinal,currentSeat,lordSeatOrdinal,drawPileCount,discardPileCount,phaseDeadlineMillis,outcomeOrdinal,logLines,seats,prompt,heroCandidates,selectionDeadlineMillis,judgment,processing,publicMoves,skillActivations,heroPresentations,identityIntro,openingHand,selfSkillAvailability,counterspellWindow,selfTargetAvailability,List.of());
    }

    public MatchSnapshot withCardMotions(List<CardMotionView> motions){return new MatchSnapshot(status,viewerSeat,turnNumber,phaseOrdinal,currentSeat,lordSeatOrdinal,drawPileCount,discardPileCount,phaseDeadlineMillis,outcomeOrdinal,logLines,seats,prompt,heroCandidates,selectionDeadlineMillis,judgment,processing,publicMoves,skillActivations,heroPresentations,identityIntro,openingHand,selfSkillAvailability,counterspellWindow,selfTargetAvailability,publicCardReveals,heroBan,motions,tablePresentation);}
    public MatchSnapshot withHeroBan(HeroBanView ban){return new MatchSnapshot(status,viewerSeat,turnNumber,phaseOrdinal,currentSeat,lordSeatOrdinal,drawPileCount,discardPileCount,phaseDeadlineMillis,outcomeOrdinal,logLines,seats,prompt,heroCandidates,selectionDeadlineMillis,judgment,processing,publicMoves,skillActivations,heroPresentations,identityIntro,openingHand,selfSkillAvailability,counterspellWindow,selfTargetAvailability,publicCardReveals,ban,cardMotions,tablePresentation);}
    public MatchSnapshot withPublicCardReveals(List<PublicCardRevealView> values){
      return new MatchSnapshot(status,viewerSeat,turnNumber,phaseOrdinal,currentSeat,lordSeatOrdinal,drawPileCount,discardPileCount,phaseDeadlineMillis,outcomeOrdinal,logLines,seats,prompt,heroCandidates,selectionDeadlineMillis,judgment,processing,publicMoves,skillActivations,heroPresentations,identityIntro,openingHand,selfSkillAvailability,counterspellWindow,selfTargetAvailability,values,heroBan,cardMotions,tablePresentation);
    }
    public MatchSnapshot withControls(java.util.Map<Integer,ControlView> controls){return new MatchSnapshot(status,viewerSeat,turnNumber,phaseOrdinal,currentSeat,lordSeatOrdinal,drawPileCount,discardPileCount,phaseDeadlineMillis,outcomeOrdinal,logLines,seats.stream().map(s->s.withControl(controls.get(s.seat()))).toList(),prompt,heroCandidates,selectionDeadlineMillis,judgment,processing,publicMoves,skillActivations,heroPresentations,identityIntro,openingHand,selfSkillAvailability,counterspellWindow,selfTargetAvailability,publicCardReveals,heroBan,cardMotions,tablePresentation);}
    public MatchSnapshot withOwnCardPlayOptions(java.util.Map<Long,List<CardPlayOption>> options){
      return new MatchSnapshot(status,viewerSeat,turnNumber,phaseOrdinal,currentSeat,lordSeatOrdinal,drawPileCount,
          discardPileCount,phaseDeadlineMillis,outcomeOrdinal,logLines,
          seats.stream().map(s->s.self()?s.withPlayOptions(options):s).toList(),prompt,heroCandidates,
          selectionDeadlineMillis,judgment,processing,publicMoves,skillActivations,heroPresentations,identityIntro,
          openingHand,selfSkillAvailability,counterspellWindow,selfTargetAvailability,publicCardReveals,heroBan,cardMotions,tablePresentation);
    }
    public MatchSnapshot(int status,int viewerSeat,int turnNumber,int phaseOrdinal,int currentSeat,
        int lordSeatOrdinal,int drawPileCount,int discardPileCount,long phaseDeadlineMillis,int outcomeOrdinal,
        List<String> logLines,List<SeatView> seats,PromptView prompt,List<String> heroCandidates,long selectionDeadlineMillis,
        JudgmentView judgment,List<CardView> processing,List<PublicMoveView> publicMoves,
        List<PublicSkillActivationView> skillActivations,List<dev.sgs.core.addon.HeroPresentation> heroPresentations,
        IdentityIntroView identityIntro,OpeningHandView openingHand,List<SkillAvailabilityView> selfSkillAvailability,
        CounterspellWindowView counterspellWindow){
      this(status,viewerSeat,turnNumber,phaseOrdinal,currentSeat,lordSeatOrdinal,drawPileCount,discardPileCount,
          phaseDeadlineMillis,outcomeOrdinal,logLines,seats,prompt,heroCandidates,selectionDeadlineMillis,judgment,
          processing,publicMoves,skillActivations,heroPresentations,identityIntro,openingHand,selfSkillAvailability,counterspellWindow,List.of());
    }
    public MatchSnapshot withSelfTargetAvailability(List<TargetAvailabilityView> hints){
      return new MatchSnapshot(status,viewerSeat,turnNumber,phaseOrdinal,currentSeat,lordSeatOrdinal,drawPileCount,
          discardPileCount,phaseDeadlineMillis,outcomeOrdinal,logLines,seats,prompt,heroCandidates,
          selectionDeadlineMillis,judgment,processing,publicMoves,skillActivations,heroPresentations,identityIntro,
          openingHand,selfSkillAvailability,counterspellWindow,hints,publicCardReveals,heroBan,cardMotions,tablePresentation);
    }
    public MatchSnapshot(int status,int viewerSeat,int turnNumber,int phaseOrdinal,int currentSeat,
        int lordSeatOrdinal,int drawPileCount,int discardPileCount,long phaseDeadlineMillis,int outcomeOrdinal,
        List<String> logLines,List<SeatView> seats,PromptView prompt,List<String> heroCandidates,long selectionDeadlineMillis,
        JudgmentView judgment,List<CardView> processing,List<PublicMoveView> publicMoves,
        List<PublicSkillActivationView> skillActivations,List<dev.sgs.core.addon.HeroPresentation> heroPresentations,
        IdentityIntroView identityIntro,OpeningHandView openingHand,List<SkillAvailabilityView> selfSkillAvailability){
      this(status,viewerSeat,turnNumber,phaseOrdinal,currentSeat,lordSeatOrdinal,drawPileCount,discardPileCount,
          phaseDeadlineMillis,outcomeOrdinal,logLines,seats,prompt,heroCandidates,selectionDeadlineMillis,judgment,
          processing,publicMoves,skillActivations,heroPresentations,identityIntro,openingHand,selfSkillAvailability,null);
    }
    public MatchSnapshot withCounterspellWindow(CounterspellWindowView window){
      return new MatchSnapshot(status,viewerSeat,turnNumber,phaseOrdinal,currentSeat,lordSeatOrdinal,drawPileCount,
          discardPileCount,phaseDeadlineMillis,outcomeOrdinal,logLines,seats,prompt,heroCandidates,
          selectionDeadlineMillis,judgment,processing,publicMoves,skillActivations,heroPresentations,identityIntro,
          openingHand,selfSkillAvailability,window,selfTargetAvailability,publicCardReveals,heroBan,cardMotions,tablePresentation);
    }
    public MatchSnapshot(int status,int viewerSeat,int turnNumber,int phaseOrdinal,int currentSeat,
        int lordSeatOrdinal,int drawPileCount,int discardPileCount,long phaseDeadlineMillis,int outcomeOrdinal,
        List<String> logLines,List<SeatView> seats,PromptView prompt,List<String> heroCandidates,long selectionDeadlineMillis,
        JudgmentView judgment,List<CardView> processing,List<PublicMoveView> publicMoves,
        List<PublicSkillActivationView> skillActivations,List<dev.sgs.core.addon.HeroPresentation> heroPresentations,
        IdentityIntroView identityIntro,OpeningHandView openingHand){
      this(status,viewerSeat,turnNumber,phaseOrdinal,currentSeat,lordSeatOrdinal,drawPileCount,discardPileCount,
          phaseDeadlineMillis,outcomeOrdinal,logLines,seats,prompt,heroCandidates,selectionDeadlineMillis,judgment,
          processing,publicMoves,skillActivations,heroPresentations,identityIntro,openingHand,List.of());
    }
    public MatchSnapshot withSelfSkillAvailability(List<SkillAvailabilityView> availability){
      return new MatchSnapshot(status,viewerSeat,turnNumber,phaseOrdinal,currentSeat,lordSeatOrdinal,drawPileCount,
          discardPileCount,phaseDeadlineMillis,outcomeOrdinal,logLines,seats,prompt,heroCandidates,
          selectionDeadlineMillis,judgment,processing,publicMoves,skillActivations,heroPresentations,identityIntro,openingHand,availability,counterspellWindow,selfTargetAvailability,publicCardReveals,heroBan,cardMotions,tablePresentation);
    }
    public MatchSnapshot withHeroPresentations(List<dev.sgs.core.addon.HeroPresentation> values){
      return new MatchSnapshot(status,viewerSeat,turnNumber,phaseOrdinal,currentSeat,lordSeatOrdinal,drawPileCount,
          discardPileCount,phaseDeadlineMillis,outcomeOrdinal,logLines,seats,prompt,heroCandidates,
          selectionDeadlineMillis,judgment,processing,publicMoves,skillActivations,values,identityIntro,openingHand,selfSkillAvailability,counterspellWindow,selfTargetAvailability,publicCardReveals,heroBan,cardMotions,tablePresentation);
    }
    public MatchSnapshot(int status,int viewerSeat,int turnNumber,int phaseOrdinal,int currentSeat,
        int lordSeatOrdinal,int drawPileCount,int discardPileCount,long phaseDeadlineMillis,int outcomeOrdinal,
        List<String> logLines,List<SeatView> seats,PromptView prompt,List<String> heroCandidates,long selectionDeadlineMillis,
        JudgmentView judgment,List<CardView> processing,List<PublicMoveView> publicMoves,
        List<PublicSkillActivationView> skillActivations,List<dev.sgs.core.addon.HeroPresentation> heroPresentations,
        IdentityIntroView identityIntro){
      this(status,viewerSeat,turnNumber,phaseOrdinal,currentSeat,lordSeatOrdinal,drawPileCount,discardPileCount,
          phaseDeadlineMillis,outcomeOrdinal,logLines,seats,prompt,heroCandidates,selectionDeadlineMillis,judgment,
          processing,publicMoves,skillActivations,heroPresentations,identityIntro,null);
    }
    public MatchSnapshot withOpeningHand(OpeningHandView opening){
      return new MatchSnapshot(status,viewerSeat,turnNumber,phaseOrdinal,currentSeat,lordSeatOrdinal,drawPileCount,
          discardPileCount,phaseDeadlineMillis,outcomeOrdinal,logLines,seats,prompt,heroCandidates,
          selectionDeadlineMillis,judgment,processing,publicMoves,skillActivations,heroPresentations,identityIntro,opening,selfSkillAvailability,counterspellWindow,selfTargetAvailability,publicCardReveals,heroBan,cardMotions,tablePresentation);
    }
    public MatchSnapshot(int status,int viewerSeat,int turnNumber,int phaseOrdinal,int currentSeat,
        int lordSeatOrdinal,int drawPileCount,int discardPileCount,long phaseDeadlineMillis,int outcomeOrdinal,
        List<String> logLines,List<SeatView> seats,PromptView prompt,List<String> heroCandidates,long selectionDeadlineMillis,
        JudgmentView judgment,List<CardView> processing,List<PublicMoveView> publicMoves,
        List<PublicSkillActivationView> skillActivations,List<dev.sgs.core.addon.HeroPresentation> heroPresentations){
      this(status,viewerSeat,turnNumber,phaseOrdinal,currentSeat,lordSeatOrdinal,drawPileCount,discardPileCount,
          phaseDeadlineMillis,outcomeOrdinal,logLines,seats,prompt,heroCandidates,selectionDeadlineMillis,judgment,
          processing,publicMoves,skillActivations,heroPresentations,null);
    }
    public MatchSnapshot(int status, int viewerSeat, int turnNumber, int phaseOrdinal,
        int currentSeat, int lordSeatOrdinal, int drawPileCount, int discardPileCount,
        long phaseDeadlineMillis, int outcomeOrdinal, List<String> logLines, List<SeatView> seats,
        PromptView prompt, List<String> heroCandidates, long selectionDeadlineMillis, JudgmentView judgment,
        List<CardView> processing,List<PublicMoveView> publicMoves,List<PublicSkillActivationView> skillActivations){
      this(status,viewerSeat,turnNumber,phaseOrdinal,currentSeat,lordSeatOrdinal,drawPileCount,
          discardPileCount,phaseDeadlineMillis,outcomeOrdinal,logLines,seats,prompt,heroCandidates,
          selectionDeadlineMillis,judgment,processing,publicMoves,skillActivations,
          dev.sgs.core.addon.AddonRuntime.current().present(java.util.stream.Stream.concat(
              heroCandidates.stream(),seats.stream().map(SeatView::heroId).filter(java.util.Objects::nonNull)).toList()));
    }
    public MatchSnapshot(int status, int viewerSeat, int turnNumber, int phaseOrdinal,
        int currentSeat, int lordSeatOrdinal, int drawPileCount, int discardPileCount,
        long phaseDeadlineMillis, int outcomeOrdinal, List<String> logLines, List<SeatView> seats,
        PromptView prompt, List<String> heroCandidates, long selectionDeadlineMillis, JudgmentView judgment,
        List<CardView> processing,List<PublicMoveView> publicMoves) {
      this(status,viewerSeat,turnNumber,phaseOrdinal,currentSeat,lordSeatOrdinal,drawPileCount,
          discardPileCount,phaseDeadlineMillis,outcomeOrdinal,logLines,seats,prompt,heroCandidates,
          selectionDeadlineMillis,judgment,processing,publicMoves,List.of());
    }
    public MatchSnapshot(int status, int viewerSeat, int turnNumber, int phaseOrdinal,
        int currentSeat, int lordSeatOrdinal, int drawPileCount, int discardPileCount,
        long phaseDeadlineMillis, int outcomeOrdinal, List<String> logLines, List<SeatView> seats,
        PromptView prompt, List<String> heroCandidates, long selectionDeadlineMillis, JudgmentView judgment) {
      this(status, viewerSeat, turnNumber, phaseOrdinal, currentSeat, lordSeatOrdinal, drawPileCount,
          discardPileCount, phaseDeadlineMillis, outcomeOrdinal, logLines, seats, prompt,
          heroCandidates, selectionDeadlineMillis, judgment, List.of(), List.of());
    }
    public MatchSnapshot {
      if(tablePresentation==null)tablePresentation=TablePresentation.empty();
      cardMotions=cardMotions==null?List.of():List.copyOf(cardMotions);
      publicCardReveals=publicCardReveals==null?List.of():List.copyOf(publicCardReveals);
      processing = List.copyOf(processing);
      publicMoves = List.copyOf(publicMoves);
      skillActivations = skillActivations==null?List.of():List.copyOf(skillActivations);
      heroPresentations=heroPresentations==null?List.of():List.copyOf(heroPresentations);
      selfSkillAvailability=selfSkillAvailability==null?List.of():List.copyOf(selfSkillAvailability);
      selfTargetAvailability=selfTargetAvailability==null?List.of():List.copyOf(selfTargetAvailability);
    }
  }
}
