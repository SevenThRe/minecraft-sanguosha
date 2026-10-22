package dev.sgs.core.engine;

/** Pure opaque-hand wire values. Validation never resolves a card or advances randomness. */
public final class HandSelection {
  private HandSelection(){}

  public static long positionToken(int index){
    if(index<0)throw new IllegalArgumentException("negative hand position");
    return -2L-index;
  }

  /** Returns -1 for any non-position or out-of-range value, including overflowing wire inputs. */
  public static int positionIndex(long token,int handCount){
    return handCount>0&&token<=-2&&token>=-(long)handCount-1?(int)(-2-token):-1;
  }

  public static boolean allowsPosition(PromptType type,boolean hidden,int handCount,long token){
    return type!=null&&type.supportsHandPositions()&&hidden&&positionIndex(token,handCount)>=0;
  }

  /** Zero retains its legacy random/target-only sentinel meaning; it is never a position. */
  public static boolean allowsHiddenChoice(PromptType type,boolean hidden,int handCount,long token){
    return type!=null&&hidden&&(token==0||allowsPosition(type,true,handCount,token));
  }
}
