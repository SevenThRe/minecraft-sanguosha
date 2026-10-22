package dev.sgs.validation.client;
import dev.sgs.validation.social.GiftNetwork;
import java.util.*;
import java.util.function.IntFunction;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import com.mojang.math.Axis;

/** Straight seat-relative travel. Concurrent receipts own independent effects. */
public final class GiftAnimations {
    public record Anchor(float x,float y) {}
    private record Flight(GiftNetwork.Result event,long started) {}
    private final Deque<Flight> flights=new ArrayDeque<>();
    private final Set<String> seen=new LinkedHashSet<>();
    private record Combo(long first,long last,int clicks,GiftNetwork.Result event) {}
    private final Map<String,Combo> combos=new LinkedHashMap<>();
    public void accept(GiftNetwork.Result event){
        if(event.receipt()==null || event.receipt().isEmpty() || !seen.add(event.receipt()))return;
        if(seen.size()>512)seen.remove(seen.iterator().next());
        while(flights.size()>=48)flights.removeFirst();
        flights.addLast(new Flight(event,System.nanoTime()));
        String key=event.from()+":"+event.to()+":"+event.kind();long now=System.nanoTime();var old=combos.get(key);
        combos.put(key,old!=null && now-old.last()<1_800_000_000L?new Combo(old.first(),now,Math.min(9999,old.clicks()+1),event):new Combo(now,now,1,event));
    }
    private void sprite(GuiGraphics g,dev.sgs.core.social.GiftDefinition definition,float x,float y,float size,float angle,long elapsed){
        g.pose().pushPose();g.pose().translate(x,y,0);g.pose().mulPose(Axis.ZP.rotationDegrees(angle));
        GiftSprites.draw(g,definition,-Math.round(size/2),-Math.round(size/2),Math.round(size),elapsed);g.pose().popPose();
    }
    public void render(GuiGraphics g,Font font,IntFunction<Anchor> anchors,float unit){
        long now=System.nanoTime();flights.removeIf(f->(now-f.started())/1e6>2800);
        combos.values().removeIf(c->now-c.last()>2_100_000_000L);
        var positive=new HashMap<Integer,Integer>();var negative=new HashMap<Integer,Integer>();
        g.pose().pushPose();g.pose().translate(0,0,450);
        for(var f:flights){
            var e=f.event();var from=anchors.apply(e.from());var to=anchors.apply(e.to());if(from==null || to==null)continue;
            var definition=e.definition();if(definition==null)continue;
            boolean egg=!definition.positive(),bunch=definition.positive() && definition.received()>1;
            float elapsed=(now-f.started())/1_000_000f;
            float distance=(float)Math.hypot(to.x()-from.x(),to.y()-from.y());
            float duration=Math.clamp(distance/Math.max(.4f,unit)*.8f,280,620);
            // Ten flowers are one distinct bouquet; ten eggs are a rapid volley.
            int projectiles=definition.animation()==dev.sgs.core.social.GiftDefinition.Animation.SPLASH?Math.min(10,e.amount()):1;
            for(int i=0;i<projectiles;i++){
                float age=elapsed-i*65;if(age<0)continue;
                float t=Math.min(1,age/duration),p=t*t*(3-2*t);
                float x=from.x()+(to.x()-from.x())*p,y=from.y()+(to.y()-from.y())*p;
                float size=Math.max(16,(bunch?110:egg?44:78)*unit);
                if(age<duration){
                    sprite(g,definition,x,y,size,egg?-250*t:8*(float)Math.sin(t*Math.PI*2),(long)elapsed);
                }else if(age<duration+620){
                    float q=(age-duration)/620;
                    if((!egg || definition.animation()==dev.sgs.core.social.GiftDefinition.Animation.SPRITE) && q<.55f)sprite(g,definition,to.x(),to.y()-8*q*unit,size*(1+.12f*(float)Math.sin(q*Math.PI)),0,(long)elapsed);
                    for(int n=0;n<(bunch?16:12);n++){
                        double a=n*2.4+i*.73;float radius=(9+(bunch?48:32)*q)*unit;
                        int px=Math.round(to.x()+(float)Math.cos(a)*radius),py=Math.round(to.y()+(float)Math.sin(a)*radius+(egg?26*q*q*unit:-18*q*unit));
                        int alpha=Math.round(255*(1-q));int rgb=egg?(n%3==0?0xf3b936:0xfff3d3):n%4==0?0x67a84b:definition.color();
                        int pixel=Math.max(1,Math.round((egg?5:3)*unit));g.fill(px,py,px+pixel,py+pixel,(alpha<<24)|rgb);
                    }
                    if(definition.animation()==dev.sgs.core.social.GiftDefinition.Animation.SPLASH){int r=Math.max(2,Math.round(10*(1-q)*unit));int tx=Math.round(to.x()),ty=Math.round(to.y());g.fill(tx-r,ty-r/2,tx+r,ty+r/2+1,(Math.round(190*(1-q))<<24)|definition.color());}
                }
            }
            if(elapsed>=duration && elapsed<duration+1500)(egg?negative:positive).merge(e.to(),e.amount(),Integer::sum);
        }
        for(var counts:List.of(positive,negative))for(var entry:counts.entrySet()){
            var to=anchors.apply(entry.getKey());if(to==null)continue;boolean egg=counts==negative;
            String label=(egg?"菜":"鲜花")+" +"+entry.getValue();int tx=Math.round(to.x()),ty=Math.round(to.y()-(egg?16:42)*unit);
            int w=font.width(label);g.fill(tx-w/2-5,ty-3,tx+w/2+5,ty+12,0xdd36271d);g.drawString(font,label,tx-w/2,ty,egg?0xffffd875:0xffffb7c7,false);
        }
        var newest=new HashMap<Integer,Combo>();
        for(var combo:combos.values())if(now-combo.first()>620_000_000L){var old=newest.get(combo.event().to());if(old==null || old.last()<combo.last())newest.put(combo.event().to(),combo);}
        for(var combo:newest.values()){
            var anchor=anchors.apply(combo.event().to());var definition=combo.event().definition();if(anchor==null || definition==null)continue;
            String label=definition.name()+" ×"+combo.clicks();int x=Math.round(anchor.x()),y=Math.round(anchor.y()-78*unit),w=font.width(label);
            g.fill(x-w/2-4,y-2,x+w/2+4,y+11,0xdd3f241a);g.drawString(font,label,x-w/2,y,0xffffce78,false);
        }
        g.pose().popPose();
    }
}
