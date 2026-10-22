package dev.sgs.core.engine;

import dev.sgs.core.card.*;
import dev.sgs.core.game.GameState;
import java.util.*;

/** Unpaid, immutable physical-card proposal. Payment belongs to the parent's atomic move batch.
 * Restoring a proposal never moves cards; all ownership and exclusions are checked again at commit.
 */
final class CardCostSelection {
  enum Scope { HAND, OWNED }
  private final int owner,required;
  private final Scope scope;
  private final List<Long> selected;

  CardCostSelection(int owner,int required,Scope scope,List<Long> selected){
    if(owner<0 || required<1 || required>160 || selected.size()>required
        || selected.stream().anyMatch(id->id==null || id<=0)
        || selected.stream().distinct().count()!=selected.size())throw new IllegalArgumentException("invalid unpaid card proposal");
    this.owner=owner;this.required=required;this.scope=Objects.requireNonNull(scope);this.selected=List.copyOf(selected);
  }
  List<Long> selected(){return selected;}
  boolean complete(){return selected.size()==required;}
  private List<CardInstance> owned(GameState state,Set<Long> excluded){
    var player=state.players().get(owner);var cards=new ArrayList<>(player.handView());
    if(scope==Scope.OWNED)cards.addAll(player.equipment().all());
    return cards.stream().filter(c->!excluded.contains(c.instanceId()))
        .filter(c->{var zone=state.cards().locate(c.instanceId());return CardZone.hand(owner).equals(zone)
            || scope==Scope.OWNED && CardZone.equipment(owner).equals(zone);}).toList();
  }
  List<CardInstance> options(GameState state,Set<Long> excluded){
    return owned(state,excluded).stream().filter(c->!selected.contains(c.instanceId())).toList();
  }
  Optional<CardCostSelection> select(GameState state,long id,Set<Long> excluded){
    if(complete() || options(state,excluded).stream().noneMatch(c->c.instanceId()==id))return Optional.empty();
    var next=new ArrayList<>(selected);next.add(id);return Optional.of(new CardCostSelection(owner,required,scope,next));
  }
  Optional<List<CardInstance>> resolve(GameState state,Set<Long> excluded){
    if(!complete())return Optional.empty();
    var cards=owned(state,excluded);var result=new ArrayList<CardInstance>();
    for(long id:selected){var card=cards.stream().filter(c->c.instanceId()==id).findFirst();if(card.isEmpty())return Optional.empty();result.add(card.get());}
    return Optional.of(List.copyOf(result));
  }
}
