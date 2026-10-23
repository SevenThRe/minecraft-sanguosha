package dev.sgs.core.engine;

/** A server-validated selection. Zero chooses a random hand card; -2-index chooses an opaque hand position. */
public record SkillChoice(long cardId,int targetSeat,DeckOrder deckOrder,java.util.List<Integer> targetSeats) {
  public SkillChoice {targetSeats=targetSeats==null?java.util.List.of():java.util.List.copyOf(targetSeats);}
  public SkillChoice(long cardId,int targetSeat){this(cardId,targetSeat,null);}
  public SkillChoice(long cardId,int targetSeat,DeckOrder order){this(cardId,targetSeat,order,java.util.List.of());}
  public static SkillChoice targets(java.util.List<Integer> seats){return new SkillChoice(0,-1,null,seats);}
  public java.util.List<Integer> selectedTargets(){return targetSeats.isEmpty()&&targetSeat>=0?java.util.List.of(targetSeat):targetSeats;}
  public boolean validTuxi(java.util.List<Integer> allowed){
    var seats=selectedTargets();return cardId==0&&deckOrder==null&&targetSeat>=-1&&seats.size()<=2
        &&new java.util.HashSet<>(seats).size()==seats.size()&&allowed.containsAll(seats)
        &&(targetSeats.isEmpty()||targetSeat==-1);
  }
  public static SkillChoice order(DeckOrder order){return new SkillChoice(-1,-1,java.util.Objects.requireNonNull(order));}
  /** Ordered physical IDs; neither lane is published to opponents. */
  public record DeckOrder(java.util.List<Long> top,java.util.List<Long> bottom) {
    public DeckOrder { top=java.util.List.copyOf(top);bottom=java.util.List.copyOf(bottom); }
    public boolean matches(java.util.List<Long> offered){
      var all=new java.util.ArrayList<>(top);all.addAll(bottom);
      return all.size()<=160&&all.size()==offered.size()&&new java.util.HashSet<>(all).size()==all.size()
          &&new java.util.HashSet<>(all).equals(new java.util.HashSet<>(offered));
    }
  }
}
