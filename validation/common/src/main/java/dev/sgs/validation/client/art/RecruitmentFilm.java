package dev.sgs.validation.client.art;

import com.google.gson.Gson;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.sgs.validation.ProbeMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Bounded, resource-pack replaceable video/GIF frame sequence. No native video decoder required. */
public final class RecruitmentFilm {
    private record Film(int frames,int fps,int columns,int rows,int width,int height,boolean interpolate,String[] pages){}
    private Film film;
    private final Object resourceManager;
    public RecruitmentFilm(){
        resourceManager=Minecraft.getInstance().getResourceManager();
        try(var reader=new InputStreamReader(Minecraft.getInstance().getResourceManager().open(
                ProbeMod.id("textures/gui/unified/recruit_film.json")),StandardCharsets.UTF_8)){
            var f=new Gson().fromJson(reader,Film.class);
            if(f==null || f.frames<2 || f.frames>600 || f.fps<1 || f.fps>60 || f.columns<1 || f.columns>8 ||
                f.rows<1 || f.rows>8 || f.width<1 || f.height<1 || f.width*f.columns>4096 ||
                f.height*f.rows>4096 || f.pages==null || f.pages.length>40 ||
                f.pages.length!=(f.frames+f.columns*f.rows-1)/(f.columns*f.rows))throw new IllegalArgumentException("invalid film bounds");
            for(var page:f.pages)if(Minecraft.getInstance().getResourceManager().getResource(ResourceLocation.parse(page)).isEmpty())throw new IllegalArgumentException("missing film frame page");
            film=f;
        }catch(Exception error){ProbeMod.LOG.warn("Recruitment film unavailable; using card reveal",error);}
    }
    public boolean available(){return film!=null && resourceManager==Minecraft.getInstance().getResourceManager();}
    public long duration(){return film==null?0:Math.round(1000.0*(film.frames-1)/film.fps);}
    public void render(GuiGraphics g,long elapsed,int x,int y,int w,int h){
        if(!available())return;
        float cursor=Math.clamp(elapsed*film.fps/1000f,0,film.frames-1);
        int index=(int)cursor;
        g.flush();RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
        frame(g,index,x,y,w,h,1);
        if(film.interpolate && index+1<film.frames && cursor-index>.001f)frame(g,index+1,x,y,w,h,cursor-index);
        g.setColor(1,1,1,1);
    }
    private void frame(GuiGraphics g,int index,int x,int y,int w,int h,float alpha){
        int perPage=film.columns*film.rows,page=index/perPage,tile=index%perPage;
        g.setColor(1,1,1,alpha);
        g.blit(ResourceLocation.parse(film.pages[page]),x,y,w,h,(tile%film.columns)*film.width,
            (tile/film.columns)*film.height,film.width,film.height,film.width*film.columns,film.height*film.rows);
    }
}
