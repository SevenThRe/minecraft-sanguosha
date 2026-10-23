package dev.sgs.validation.client.layout;

import java.util.*;
import dev.sgs.core.engine.SkillChoice;

/** Local draft only. No card leaves either lane until the player confirms the whole order. */
public final class GuanxingOrder {
    private final List<Long> original;private final List<Long> top=new ArrayList<>(),bottom=new ArrayList<>();
    public GuanxingOrder(List<Long> cards){original=List.copyOf(cards);top.addAll(cards);}
    public List<Long> top(){return List.copyOf(top);}public List<Long> bottom(){return List.copyOf(bottom);}
    public boolean contains(long id){return original.contains(id);}
    public void move(long id,int lane,int index){
        if(!contains(id)||lane<0||lane>1)throw new IllegalArgumentException("invalid private draft move");
        top.remove(id);bottom.remove(id);var destination=lane==0?top:bottom;destination.add(Math.clamp(index,0,destination.size()),id);
    }
    public void shift(long id,int direction){var lane=top.contains(id)?top:bottom;int index=lane.indexOf(id);if(index<0)return;move(id,lane==top?0:1,index+direction);}
    public SkillChoice.DeckOrder submitted(){var result=new SkillChoice.DeckOrder(top,bottom);if(!result.matches(original))throw new IllegalStateException("private draft lost cards");return result;}
}
