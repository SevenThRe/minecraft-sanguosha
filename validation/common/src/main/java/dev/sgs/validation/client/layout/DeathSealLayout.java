package dev.sgs.validation.client.layout;

import dev.sgs.core.Identity;

/** Public server identity only; local identity guesses must never enter this policy. */
public final class DeathSealLayout {
    private DeathSealLayout() {}
    public static String label(boolean alive, boolean self, int team, int viewerTeam, int identity) {
        if(alive)return "";
        if(self)return "阵亡";
        if(team>=0)return viewerTeam<0?"阵亡":team==viewerTeam?"友方阵亡":"敌方阵亡";
        if(identity<0 || identity>=Identity.values().length)return "阵亡";
        return switch(Identity.values()[identity]) {
            case LORD -> "主公阵亡";
            case LOYALIST -> "忠臣阵亡";
            case REBEL -> "反贼阵亡";
            case RENEGADE -> "内奸阵亡";
        };
    }
    public record Box(int x,int y,int size) {}
    public static Box box(int x,int y,int w,int h,float unit) {
        int left=x+PortraitOverlayLayout.nameRailWidth(w)+Math.max(2,Math.round(4*unit));
        int right=x+w-2-Math.max(1,Math.round(16*unit))-2*Math.max(1,Math.round(3*unit))-Math.max(2,Math.round(5*unit));
        int size=Math.max(1,Math.min(right-left,Math.round(h*.34f)));
        return new Box(left+Math.max(0,(right-left-size)/2),y+Math.round(h*.40f),size);
    }
}
