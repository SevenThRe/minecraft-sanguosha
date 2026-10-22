package dev.sgs.validation.client.layout;

/** Horizontal overlap keeps every card addressable; only extreme counts require scrolling. */
public record HandFanLayout(int count,int left,int available,int cardWidth,int step,int offset,int contentWidth) {
    public static HandFanLayout fit(int count,int left,int available,int cardWidth,int offset){
        // Scale the minimum exposed rail with the card, not with fixed logical pixels.
        // A fixed 8px floor pushed the twentieth card outside the lane at GUI scales 2/3.
        int minimumStep=Math.max(3,cardWidth/10);
        int step=count<=1?cardWidth:Math.max(minimumStep,Math.min(cardWidth+3,(available-cardWidth)/Math.max(1,count-1)));
        int content=count==0?0:cardWidth+Math.max(0,count-1)*step;
        return new HandFanLayout(count,left+Math.max(0,(available-content)/2),available,cardWidth,step,
            Math.clamp(offset,0,Math.max(0,content-available)),content);
    }
    public int x(int index){return left+index*step-offset;}
    public int exposedWidth(int index){return index==count-1?cardWidth:Math.min(step,cardWidth);}
}
