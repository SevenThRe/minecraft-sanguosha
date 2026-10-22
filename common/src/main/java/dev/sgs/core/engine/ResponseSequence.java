package dev.sgs.core.engine;

/** Remaining successful response units, independent of hero, card and payment mechanism.
 * A caller advances only after the actual response has committed; failed input never advances it.
 * Parent programs keep their own next-step/target semantics and existing wire projections.
 */
public record ResponseSequence(int remaining) {
  public ResponseSequence {if(remaining<0)throw new IllegalArgumentException("negative response remainder");}
  public boolean complete(){return remaining==0;}
  public ResponseSequence committed(){
    if(complete())throw new IllegalStateException("response sequence already complete");
    return new ResponseSequence(remaining-1);
  }
  public static ResponseSequence restorePaid(int required,int paid){
    if(required<0 || paid<0 || paid>required)throw new IllegalArgumentException("invalid paid response sequence");
    return new ResponseSequence(required-paid);
  }
  public int paidOutOf(int required){
    if(required<remaining)throw new IllegalArgumentException("response total smaller than remainder");
    return required-remaining;
  }
}
