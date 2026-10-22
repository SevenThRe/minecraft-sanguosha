package dev.sgs.validation.client.layout;

/** A complete centered row: crowded cards overlap, and only exposed strips receive clicks. */
public record ZoneCardRowLayout(int count,int cardWidth,int left,double stride) {
    public static ZoneCardRowLayout fit(int available,int cardWidth,int gap,int count){
        if(count<1 || cardWidth<1 || available<cardWidth)throw new IllegalArgumentException("Invalid card row");
        double stride=count==1?0:Math.min(cardWidth+gap,(available-cardWidth)/(double)(count-1));
        int span=cardWidth+(int)Math.round((count-1)*stride);
        return new ZoneCardRowLayout(count,cardWidth,(available-span)/2,stride);
    }
    public int x(int index){return left+(int)Math.round(index*stride);}
    public int hitWidth(int index){return index==count-1?cardWidth:Math.min(cardWidth,x(index+1)-x(index));}
}
