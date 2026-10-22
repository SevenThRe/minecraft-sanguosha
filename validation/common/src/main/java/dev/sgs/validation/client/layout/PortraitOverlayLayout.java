package dev.sgs.validation.client.layout;

/** Shared draw/hit geometry for overlays anchored to a general portrait. */
public final class PortraitOverlayLayout {
    private PortraitOverlayLayout(){}
    public static int nameRailWidth(int w){return Math.max(12,Math.round(w*.16f));}
    /** Exact colored rail, shared by painting and the local skin-picker hit area. */
    public static ReferenceBattleLayout.Box nameRail(int x,int y,int w,int h){
        return new ReferenceBattleLayout.Box(x+1,y+1,nameRailWidth(w)-1,Math.max(1,h-3));
    }
    /** Video 00:05, 03:30: round relation seal in the upper-right corner. */
    public static ReferenceBattleLayout.Box identity(int x,int y,int w,int h){
        int size=Math.max(12,Math.round(w*.18f)),margin=Math.max(1,Math.round(w*.02f));
        return new ReferenceBattleLayout.Box(x+w-margin-size,y+margin,size,size);
    }
    public static ReferenceBattleLayout.Box equipment(int x,int y,int w,int h,int index,int count){
        return equipment(x,y,w,h,index,count,7);
    }
    public static ReferenceBattleLayout.Box equipment(int x,int y,int w,int h,int index,int count,int minimumRow){
        int row=Math.max(minimumRow,Math.min(11,h/13));
        int nameRail=nameRailWidth(w),margin=Math.max(3,Math.round(w*.035f));
        int ew=Math.max(1,w-nameRail-margin*2);
        int top=Math.min(y+Math.round(h*.64f),y+h-17-row*count);
        return new ReferenceBattleLayout.Box(x+(w-ew)/2,top+index*row,ew,row-1);
    }
    public static ReferenceBattleLayout.Box judgment(int x,int y,int w,int h,boolean self,int index){
        int size=Math.max(8,Math.round(w*.13f)),gap=Math.max(1,Math.round(w*.015f));
        // Video 04:08.85: others touch the lower-left edge, level with the seat caption.
        // No self judgment occurs in this clip: keep the user's specified upper-right exterior lane.
        return self?new ReferenceBattleLayout.Box(x+w-size-index*(size+gap),y-size-gap,size,size)
            :new ReferenceBattleLayout.Box(x+Math.round(w*.03f)+(index%2)*(size+gap),y+h+(index/2)*(size+gap),size,size);
    }
    public static ReferenceBattleLayout.Box status(int x,int y,int w,int h,int index){
        var seal=identity(x,y,w,h);int sh=Math.max(8,Math.round(h*.075f)),sw=Math.max(seal.width(),Math.round(w*.26f));
        return new ReferenceBattleLayout.Box(seal.right()-sw,seal.bottom()+Math.max(1,Math.round(h*.015f))+index*(sh+1),sw,sh);
    }
    /** Limited-use seal outside the portrait, immediately right of the identity seal. */
    public static ReferenceBattleLayout.Box limited(int x,int y,int w,int h,int index){
        var identity=identity(x,y,w,h);int size=Math.max(8,Math.round(w*.13f));
        int gap=Math.max(1,Math.round(w*.015f));
        return new ReferenceBattleLayout.Box(x+w+gap,identity.y()+index*(size+gap),size,size);
    }
    /** Video 00:04.567..00:06.5: effect sits below the face, not at portrait center. */
    public static ReferenceBattleLayout.Box activation(int x,int y,int w,int h){
        int size=Math.max(18,Math.round(w*.70f));
        return new ReferenceBattleLayout.Box(x+Math.round(w*.55f)-size/2,y+Math.round(h*.59f)-size/2,size,size);
    }
}
