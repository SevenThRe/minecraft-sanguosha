package dev.sgs.core.game;

import dev.sgs.core.card.CardInstance;
import java.util.*;

/** Explicit public presentation of real entities. This is never a movement or a rules cost. */
public record PublicCardReveal(String id,int owner,int subject,String reason,List<CardInstance> cards,boolean held) {
  public PublicCardReveal {
    cards=List.copyOf(cards);
    if(id==null||id.isBlank()||id.length()>256||owner<0||owner>7||subject<0||subject>7||reason==null
        ||!reason.matches("(?:[a-z][a-z0-9_]{1,31}:)?[a-z][a-z0-9_]{0,63}")||cards.isEmpty()||cards.size()>8
        ||cards.stream().map(CardInstance::instanceId).distinct().count()!=cards.size())throw new IllegalArgumentException("invalid public reveal");
  }
  public static String key(int turn,String reason,int owner,int subject,long card){return turn+":"+reason+":"+owner+":"+subject+":"+card;}
  public PublicCardReveal completed(){return new PublicCardReveal(id,owner,subject,reason,cards,false);}
}
