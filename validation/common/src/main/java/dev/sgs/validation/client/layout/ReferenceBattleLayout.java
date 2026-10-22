package dev.sgs.validation.client.layout;

/** Final product reference coordinates, authored on the user's 1672 x 941 board. */
public record ReferenceBattleLayout(int width,int height,float unit,int offsetX,int offsetY,int seats){
    public static ReferenceBattleLayout fit(int width,int height){
        return fit(width,height,4);
    }
    public static ReferenceBattleLayout fit(int width,int height,int seats){
        float unit=Math.min(width/1672f,height/941f);
        return new ReferenceBattleLayout(width,height,unit,Math.round((width-1672*unit)/2),Math.round((height-941*unit)/2),seats);
    }
    public record Box(int x,int y,int width,int height){public int right(){return x+width;}public int bottom(){return y+height;}}
    public Box box(int x,int y,int w,int h){return new Box(offsetX+Math.round(x*unit),offsetY+Math.round(y*unit),Math.max(1,Math.round(w*unit)),Math.max(1,Math.round(h*unit)));}
    public Box self(){return seats==8?box(1435,658,200,260):box(1393,544,239,352);}
    public Box hand(){return box(525,728,623,198);}
    public Box equipment(){return box(1192,643,190,205);}
    public Box social(){return box(8,727,144,200);}
    /** Row-major: flowers/report, autoplay/distance, emotes/chat. */
    public Box socialButton(int index){
        if(index<0 || index>=6)throw new IllegalArgumentException("social button index");
        return box(15+(index%2)*70,731+(index/2)*64,60,60);
    }
    public Box notice(){return box(526,338,622,40);}
    /** Opponent slots run left -> upper-left -> upper-right; the next seat is upper-right. */
    public static int fourPlayerOpponentIndex(int seat,int viewer){
        if(seat<0 || seat>=4 || viewer<0 || viewer>=4 || seat==viewer)
            throw new IllegalArgumentException("Expected distinct four-player seats");
        return Math.floorMod(viewer-seat,4)-1;
    }
    /** Left middle -> five top slots -> right middle. Viewer+1 is immediately above self. */
    public static int eightPlayerOpponentIndex(int seat,int viewer){
        if(seat<0 || seat>=8 || viewer<0 || viewer>=8 || seat==viewer)
            throw new IllegalArgumentException("Expected distinct eight-player seats");
        return Math.floorMod(viewer-seat,8)-1;
    }
    public Box opponent(int index,int count){
        if(count==7){
            if(index<0 || index>=7)throw new IllegalArgumentException("Eight-player opponent slot");
            if(index==0)return box(30,364,200,260);
            if(index==6)return box(1435,364,200,260);
            int[] topX={30,416,739,1057,1435};
            return box(topX[index-1],57,200,260);
        }
        if(count==3)return switch(index){case 0->box(29,264,231,290);case 1->box(637,64,225,260);default->box(1048,64,225,260);};
        if(count==5){if(index==0)return box(29,264,231,290);if(index==4)return box(1404,75,225,260);return box(360+(index-1)*334,64,225,260);}
        if(index==0)return box(27,170,214,230);
        if(index==1)return box(27,425,214,230);
        if(index==count-1)return box(1410,90,214,256);
        int top=count-3;
        return box(300+(index-2)*1080/Math.max(1,top),64,Math.min(215,1040/Math.max(1,top)),250);
    }
    public DecisionOverlayLayout.Layout decision(){
        var self=self();var hand=hand();
        int footer=Math.max(7,Math.round(34*unit));
        int left=self.x()+Math.max(12,Math.round(self.width()*.16f))+Math.max(2,Math.round(4*unit));
        int right=selfHealthLane().x()-Math.max(2,Math.round(5*unit));
        int insetBottom=Math.max(3,Math.round(18*unit));
        var skills=new DecisionOverlayLayout.Rect(left,self.bottom()-insetBottom-footer,Math.max(1,right-left),footer);
        var caption=box(526,607,622,25);var timer=box(526,639,622,12);
        var confirm=box(649,663,178,42);var cancel=box(847,663,178,42);
        return new DecisionOverlayLayout.Layout(rect(caption),rect(timer),rect(confirm),rect(cancel),skills);
    }
    /** Reserve maximum HP width so skills never invade the live heart column. */
    public Box selfHealthLane(){
        var self=self();int width=Math.max(1,Math.round(16*unit))+2*Math.max(1,Math.round(3*unit));
        int height=Math.round(self.height()*.55f);
        return new Box(self.right()-2-width,self.bottom()-3-height,width,height);
    }
    private static DecisionOverlayLayout.Rect rect(Box box){return new DecisionOverlayLayout.Rect(box.x(),box.y(),box.width(),box.height());}
}
