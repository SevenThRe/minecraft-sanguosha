package dev.sgs.validation.client;
import java.util.*;
import java.util.function.IntFunction;
import net.minecraft.client.gui.GuiGraphics;
/** Self-seat expressions: local pixel drawing, server-confirmed public playback. */
public final class EmoteAnimations {
    private record Entry(String id,long started) {}
    private final Map<Integer,Entry> seats=new HashMap<>();
    private final Set<String> seen=new LinkedHashSet<>();
    public void accept(dev.sgs.validation.social.GiftNetwork.Result r){
        if(!seen.add(r.receipt()))return;if(seen.size()>128)seen.remove(seen.iterator().next());
        seats.put(r.from(),new Entry(r.kind().substring(6),net.minecraft.Util.getMillis()));
    }
    public void render(GuiGraphics g,IntFunction<GiftAnimations.Anchor> anchors,float unit){
        long now=net.minecraft.Util.getMillis();seats.entrySet().removeIf(e->now-e.getValue().started()>2200);
        g.pose().pushPose();g.pose().translate(0,0,450);
        for(var e:seats.entrySet()){var a=anchors.apply(e.getKey());if(a==null)continue;float age=(now-e.getValue().started())/1000f;
            int size=Math.max(20,Math.round((float)((76+8*Math.sin(age*8))*unit)));draw(g,e.getValue().id(),Math.round(a.x()-size/2),Math.round(a.y()-size/2),size);}
        g.pose().popPose();
    }
    public static void draw(GuiGraphics g,String id,int x,int y,int size){
        g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(size/16f,size/16f,1);
        int face=id.equals("angry")?0xffce7255:0xffe3b850,ink=0xff51351f;
        for(int row=1;row<15;row++){int inset=row<3 || row>12?4:row<5 || row>10?2:1;g.fill(inset,row,16-inset,row+1,face);}
        if(id.equals("heart")){g.fill(4,5,7,9,0xffb5474e);g.fill(9,5,12,9,0xffb5474e);g.fill(6,8,10,11,0xffb5474e);}
        else {g.fill(4,5,6,7,ink);g.fill(10,5,12,7,ink);}
        if(id.equals("cry")){g.fill(4,7,6,12,0xff70c6df);g.fill(10,7,12,12,0xff70c6df);g.fill(6,11,10,12,ink);}
        else if(id.equals("surprised")){g.fill(7,9,9,12,ink);}
        else if(id.equals("angry")){g.fill(3,4,7,5,ink);g.fill(9,4,13,5,ink);g.fill(5,10,11,11,ink);}
        else if(id.equals("sleep")){g.fill(3,5,7,6,ink);g.fill(9,5,13,6,ink);g.fill(7,10,9,11,ink);}
        else {g.fill(4,9,6,11,ink);g.fill(6,11,10,12,ink);g.fill(10,9,12,11,ink);if(id.equals("laugh"))g.fill(5,9,11,12,ink);}
        if(id.equals("thumbsup")){g.fill(12,9,15,14,0xfffae4a2);g.fill(13,7,15,10,0xfffae4a2);}
        g.pose().popPose();
    }
}
