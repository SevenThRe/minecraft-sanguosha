package dev.sgs.validation.client.art;

import dev.sgs.core.match.dto.MatchViews.*;
import dev.sgs.validation.client.layout.CardMotionTimeline;
import dev.sgs.validation.client.layout.ReferenceBattleLayout.Box;
import net.minecraft.client.gui.GuiGraphics;

/** Draws only filtered faces; an undisclosed move has anonymous backs at every frame. */
public final class CardMotionRenderer {
    public interface Anchors {Box at(CardMotionTimeline.Flight flight,boolean destination);}
    public interface Face {void draw(CardView card,int x,int y,float scale);}
    public record Frame(long sequence,String reason,boolean faceUp,float progress){}
    public static Frame draw(GuiGraphics g,CardMotionTimeline timeline,long now,Anchors anchors,Face face){
        Frame captured=null;
        for(var flight:timeline.visible(now)){
            var from=anchors.at(flight,false);var to=anchors.at(flight,true);
            float t=flight.eased(now),p=flight.progress(now);
            int w=Math.max(1,Math.round(from.width()+(to.width()-from.width())*t));
            int h=Math.max(1,Math.round(w*1.4f));
            int x=Math.round(from.x()+(to.x()-from.x())*t);
            int y=Math.round(from.y()+(to.y()-from.y())*t-(float)Math.sin(Math.PI*p)*Math.min(20,from.height()*.14f));
            g.pose().pushPose();g.pose().translate(0,0,195+flight.index());
            boolean disclosed=!flight.event().faces().isEmpty();
            if(disclosed)face.draw(flight.event().faces().get(flight.index()),x,y,w/80f);
            else CardBackRenderer.draw(g,x,y,w,h);
            g.pose().popPose();
            if(p>.12f && p<.85f)captured=new Frame(flight.event().sequence(),flight.event().reason(),disclosed,p);
        }
        return captured;
    }
    private CardMotionRenderer(){}
}
