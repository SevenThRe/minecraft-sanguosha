package dev.sgs.validation.client.art;

import dev.sgs.validation.ProbeMod;
import java.util.Map;
import java.util.HashMap;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Portrait and data are independent; the artwork contains no baked-in identity or life. */
public final class HeroRenderer {
    private static final ResourceLocation LIU_BEI = ProbeMod.id("textures/gui/heroes/liubei_v4.png");
    private static final Map<String, ResourceLocation> PORTRAITS = Map.ofEntries(
        Map.entry("liubei", LIU_BEI),
        Map.entry("caocao", ProbeMod.id("textures/gui/heroes/caocao_v1.png")),
        Map.entry("zhaoyun", ProbeMod.id("textures/gui/heroes/zhaoyun_v1.png")),
        Map.entry("zhangfei", ProbeMod.id("textures/gui/heroes/zhangfei_v1.png")),
        Map.entry("guanyu", ProbeMod.id("textures/gui/heroes/guanyu_v1.png")),
        Map.entry("huanggai", ProbeMod.id("textures/gui/heroes/huanggai_v1.png")),
        Map.entry("huangyueying", ProbeMod.id("textures/gui/heroes/huangyueying_v1.png")),
        Map.entry("ganning", ProbeMod.id("textures/gui/heroes/ganning_v1.png")),
        Map.entry("huatuo", ProbeMod.id("textures/gui/heroes/huatuo_v1.png")),
        Map.entry("sunquan", ProbeMod.id("textures/gui/heroes/sunquan_v1.png")),
        Map.entry("lumeng", ProbeMod.id("textures/gui/heroes/lvmeng_v1.png")),
        Map.entry("simayi", ProbeMod.id("textures/gui/heroes/simayi_v1.png")),
        Map.entry("guojia", ProbeMod.id("textures/gui/heroes/guojia_v1.png")),
        Map.entry("zhouyu", ProbeMod.id("textures/gui/heroes/zhouyu_v1.png")),
        Map.entry("luxun", ProbeMod.id("textures/gui/heroes/luxun_v1.png")),
        Map.entry("xuchu", ProbeMod.id("textures/gui/heroes/xuchu_v1.png")),
        Map.entry("zhangliao", ProbeMod.id("textures/gui/heroes/zhangliao_v1.png")),
        Map.entry("xiahoudun", ProbeMod.id("textures/gui/heroes/xiahoudun_v1.png")),
        Map.entry("zhenji", ProbeMod.id("textures/gui/heroes/zhenji_v1.png")),
        Map.entry("zhugeliang", ProbeMod.id("textures/gui/heroes/zhugeliang_v1.png")),
        Map.entry("machao", ProbeMod.id("textures/gui/heroes/machao_v1.png")),
        Map.entry("daqiao", ProbeMod.id("textures/gui/heroes/daqiao_v1.png")),
        Map.entry("sunshangxiang", ProbeMod.id("textures/gui/heroes/sunshangxiang_v1.png")),
        Map.entry("lvbu", ProbeMod.id("textures/gui/heroes/lvbu_v1.png")),
        Map.entry("diaochan", ProbeMod.id("textures/gui/heroes/diaochan_v1.png")),
        Map.entry("huangzhong", ProbeMod.id("textures/gui/heroes/huangzhong_v1.png")),
        Map.entry("weiyan", ProbeMod.id("textures/gui/heroes/weiyan_v1.png")),
        Map.entry("xuhuang", ProbeMod.id("textures/gui/heroes/xuhuang_v1.png")),
        Map.entry("dianwei", ProbeMod.id("textures/gui/heroes/dianwei_v1.png")),
        Map.entry("yujin", ProbeMod.id("textures/gui/heroes/yujin_v1.png")),
        Map.entry("huaxiong", ProbeMod.id("textures/gui/heroes/huaxiong_v1.png")),
        Map.entry("gongsunzan", ProbeMod.id("textures/gui/heroes/gongsunzan_v1.png")));
    private record SourceSize(int width,int height) {}
    private static final SourceSize STANDARD_SIZE=new SourceSize(320,448);
    /** Generated native PNGs remain unedited; source pixels scale to the same logical 5:7 art. */
    private static final Map<ResourceLocation,SourceSize> SOURCE_SIZES=Map.ofEntries(
        Map.entry(ProbeMod.id("textures/gui/heroes/xuchu_v1.png"), new SourceSize(1060,1484)),
        Map.entry(ProbeMod.id("textures/gui/heroes/zhangliao_v1.png"), new SourceSize(1060,1484)),
        Map.entry(ProbeMod.id("textures/gui/heroes/xiahoudun_v1.png"), new SourceSize(1060,1484)),
        Map.entry(ProbeMod.id("textures/gui/heroes/zhenji_v1.png"), new SourceSize(1060,1484)),
        Map.entry(ProbeMod.id("textures/gui/heroes/zhugeliang_v1.png"), new SourceSize(1060,1484)),
        Map.entry(ProbeMod.id("textures/gui/heroes/machao_v1.png"), new SourceSize(1060,1484)),
        Map.entry(ProbeMod.id("textures/gui/heroes/daqiao_v1.png"), new SourceSize(1060,1484)),
        Map.entry(ProbeMod.id("textures/gui/heroes/sunshangxiang_v1.png"), new SourceSize(1060,1484)),
        Map.entry(ProbeMod.id("textures/gui/heroes/lvbu_v1.png"), new SourceSize(1060,1484)),
        Map.entry(ProbeMod.id("textures/gui/heroes/diaochan_v1.png"), new SourceSize(1060,1484)));
    private static final Map<ResourceLocation,SourceSize> measuredSizes=new HashMap<>();
    private static final Map<ResourceLocation,ResourceLocation> grayTextures=new java.util.LinkedHashMap<>();
    private HeroRenderer() {}
    public static void initialize(){
        if(net.minecraft.client.Minecraft.getInstance().getResourceManager() instanceof net.minecraft.server.packs.resources.ReloadableResourceManager resources)
            resources.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener)manager->
                net.minecraft.client.Minecraft.getInstance().execute(HeroRenderer::clearGrayTextures));
    }
    private static void clearGrayTextures(){
        var textures=net.minecraft.client.Minecraft.getInstance().getTextureManager();
        grayTextures.values().forEach(textures::release);grayTextures.clear();measuredSizes.clear();
    }
    /** NativeImage pixels are ABGR; preserve alpha and use the same luminance in all channels. */
    public static int grayColor(int color){
        int gray=(((color>>>16)&255)*77+((color>>>8)&255)*150+(color&255)*29)>>>8;
        return (color&0xff000000)|(gray<<16)|(gray<<8)|gray;
    }
    public static ResourceLocation grayscale(ResourceLocation source){
        var cached=grayTextures.get(source);if(cached!=null)return cached;
        var client=net.minecraft.client.Minecraft.getInstance();
        try(var input=client.getResourceManager().open(source)){
            var pixels=com.mojang.blaze3d.platform.NativeImage.read(input);
            for(int y=0;y<pixels.getHeight();y++)for(int x=0;x<pixels.getWidth();x++){
                int abgr=pixels.getPixelRGBA(x,y);
                int gray=((abgr&255)*77+((abgr>>>8)&255)*150+((abgr>>>16)&255)*29)>>>8;
                pixels.setPixelRGBA(x,y,(abgr&0xff000000)|(gray<<16)|(gray<<8)|gray);
            }
            var size=new SourceSize(pixels.getWidth(),pixels.getHeight());
            var texture=client.getTextureManager().register("sgs_dead_hero",new net.minecraft.client.renderer.texture.DynamicTexture(pixels));
            grayTextures.put(source,texture);measuredSizes.put(texture,size);
            if(grayTextures.size()>16){var oldest=grayTextures.keySet().iterator().next();var expired=grayTextures.remove(oldest);client.getTextureManager().release(expired);measuredSizes.remove(expired);}
            return texture;
        }catch(java.io.IOException ex){ProbeMod.LOG.warn("Could not grayscale hero texture {}",source,ex);return source;}
    }
    public static ResourceLocation classic(String hero){
        if(dev.sgs.core.hero.StandardHeroes.CATALOG.stream().anyMatch(h->h.id().equals(hero))){
            var directed=ProbeMod.id("textures/gui/heroes/"+hero+"_faction_v2.png");
            if(net.minecraft.client.Minecraft.getInstance().getResourceManager().getResource(directed).isPresent())return directed;
        }
        return legacy(hero);
    }
    public static ResourceLocation legacy(String hero){
        var original=PORTRAITS.get(hero);if(original!=null)return original;
        return dev.sgs.core.hero.MythicHeroes.IDS.contains(hero)?ProbeMod.id("textures/gui/heroes/"+hero+"_v1.png"):null;
    }
    public static ResourceLocation texture(String hero,ResourceLocation classic){
        var skin=HeroSkins.selected(hero,classic);return skin==null?classic:skin.texture();
    }
    private static SourceSize sourceSize(ResourceLocation texture){
        if(SOURCE_SIZES.containsKey(texture))return SOURCE_SIZES.get(texture);
        return measuredSizes.computeIfAbsent(texture,key->{
            try(var input=net.minecraft.client.Minecraft.getInstance().getResourceManager().open(key);
                var image=com.mojang.blaze3d.platform.NativeImage.read(input)){
                return new SourceSize(image.getWidth(),image.getHeight());
            }catch(java.io.IOException ex){return STANDARD_SIZE;}
        });
    }

    public static void portrait(GuiGraphics g,Font font,String hero,int x,int y,int width,int height,int border,boolean alive){
        portrait(g,font,hero,x,y,width,height,border,alive,classic(hero));
    }

    /** Resolve external art before drawing so placeholder text and dead-seat tint cannot leak through. */
    public static void portrait(GuiGraphics g,Font font,String hero,int x,int y,int width,int height,int border,boolean alive,ResourceLocation classic){
        portraitExact(g,font,hero,x,y,width,height,border,alive,texture(hero,classic));
    }
    public static void portraitExact(GuiGraphics g,Font font,String hero,int x,int y,int width,int height,int border,boolean alive,ResourceLocation portrait){
        g.fill(x-2,y-2,x+width+2,y+height+2,0xff34271c);
        g.fill(x-1,y-1,x+width+1,y+height+1,border);
        if(portrait != null) portraitCrop(g,alive?portrait:grayscale(portrait),x,y,width,height);
        else {
            // Honest portrait placeholder; never relabel Liu Bei artwork as a different general.
            g.fill(x,y,x+width,y+height,0xff554b37);
            int cx=x+width/2,cy=y+height/2;
            g.fill(cx-width/7,cy-height/4,cx+width/7,cy,0xffb69d6d);
            g.fill(cx-width/4,cy,cx+width/4,y+height-3,0xff857349);
            g.drawCenteredString(font,"立绘待补",cx,y+height-15,0xffe5cd9c);
        }
        for(int dx:new int[]{x-2,x+width-5})for(int dy:new int[]{y-2,y+height-5}){
            g.fill(dx,dy,dx+7,dy+2,0xffdfb970);g.fill(dx,dy,dx+2,dy+7,0xffdfb970);
        }
        if(!alive)g.fill(x,y,x+width,y+height,0x40303030);
    }

    /** Fill the target aspect without flattening heads; wide opponent cards retain the upper art. */
    public static void portraitCrop(GuiGraphics g,ResourceLocation texture,int x,int y,int width,int height){
        if(width<=0 || height<=0)return;
        var size=sourceSize(texture);
        int sourceWidth=size.width(),sourceHeight=size.height();
        if((long)width*size.height()>(long)height*size.width())
            sourceHeight=Math.clamp(Math.round((float)size.width()*height/width),1,size.height());
        else sourceWidth=Math.clamp(Math.round((float)size.height()*width/height),1,size.width());
        int u=(size.width()-sourceWidth)/2;
        // Wide previews must keep the face, rather than anchoring the crop on a tall crown/sky.
        int v=Math.clamp(Math.round(size.height()*.27f-sourceHeight*.5f),0,size.height()-sourceHeight);
        g.blit(texture,x,y,width,height,u,v,sourceWidth,sourceHeight,size.width(),size.height());
    }

    public static void draw(GuiGraphics graphics, Font font, int x, int y, float scale, int health, int maximum) {
        graphics.pose().pushPose();
        graphics.pose().translate(x,y,0); graphics.pose().scale(scale,scale,1);
        graphics.fill(-2,-2,82,114,0xff38251b);
        graphics.fill(-1,-1,81,113,0xffbc9550);
        graphics.blit(LIU_BEI,0,0,80,112,0,0,320,448,320,448);
        graphics.fill(0,0,13,112,FactionStyle.of(dev.sgs.core.Kingdom.SHU.ordinal()).main());
        graphics.drawString(font,"刘",2,12,0xfff1d3,false);
        graphics.drawString(font,"备",2,24,0xfff1d3,false);
        graphics.fill(69,0,80,112,0x9a30251b);
        HealthRenderer.vertical(graphics,70,8,health,maximum);
        graphics.fill(14,98,69,112,0xe636261c);
        graphics.drawCenteredString(font,"仁德  激将",41,100,0xffe3b7);
        graphics.pose().popPose();
    }
}
