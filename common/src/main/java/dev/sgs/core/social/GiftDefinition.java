package dev.sgs.core.social;
/** Version1 public gift-pack descriptor. Server owns price/polarity; clients own installed art. */
public record GiftDefinition(String id,String name,Polarity polarity,int cost,int received,
                             String texture,int textureWidth,int textureHeight,int u,int v,int width,int height,
                             int frames,int frameMillis,Animation animation,int color) {
    public enum Polarity { POSITIVE, NEGATIVE }
    public enum Animation { PETALS, SPLASH, SPRITE }
    public GiftDefinition {
        if(id==null || !id.matches("[a-z0-9_.-]{1,32}:[a-z0-9_./-]{1,48}") || name==null || name.isBlank() || name.length()>32
            || polarity==null || cost<1 || cost>1_000_000 || received<1 || received>1000 || animation==null
            || texture==null || !texture.matches("[a-z0-9_.-]+:textures/[a-z0-9_./-]+\\.png") || texture.contains("..")
            || textureWidth<1 || textureHeight<1 || textureWidth>2048 || textureHeight>2048 || u<0 || v<0 || width<1 || height<1
            || frames<1 || frames>64 || u+(long)width*frames>textureWidth || v+height>textureHeight || frameMillis<20 || frameMillis>1000 || color<0 || color>0xffffff)
            throw new IllegalArgumentException("Invalid gift descriptor: "+id);
    }
    public boolean positive(){return polarity==Polarity.POSITIVE;}
}
