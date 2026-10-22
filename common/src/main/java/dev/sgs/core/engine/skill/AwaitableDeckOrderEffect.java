package dev.sgs.core.engine.skill;

import dev.sgs.core.card.*;
import java.util.*;

/** Optional preparation body which privately reorders real deck cards before the turn continues. */
public interface AwaitableDeckOrderEffect extends SkillListener {
    int privateDeckCount(SkillContext context);
    default List<CardInstance> revealPrivateDeck(SkillContext c){
        return c.state().cards().drawTo(CardZone.PROCESSING,privateDeckCount(c),CardMove.Reason.REVEAL,owner().seat(),false);
    }
    default void returnPrivateDeck(SkillContext c,List<CardInstance> top,List<CardInstance> bottom){
        c.state().cards().returnPrivateDeckCards(top,bottom,owner().seat());
    }
    default void finishPrivateDeck(SkillContext c){c.confirmPublicActivation();}
}
