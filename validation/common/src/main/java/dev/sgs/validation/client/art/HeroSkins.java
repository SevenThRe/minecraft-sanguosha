package dev.sgs.validation.client.art;

import dev.sgs.validation.ProbeMod;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/** Client cosmetic preferences. Never changes the hero, skills, identity or server state. */
public final class HeroSkins {
    public record Skin(String id,String name,ResourceLocation texture) {}
    private static final Map<String,String> ACTION_NAMES=Map.ofEntries(
        Map.entry("liubei","桃园举誓"),Map.entry("caocao","横槊观海"),Map.entry("zhaoyun","长坂突围"),
        Map.entry("zhangfei","当阳怒吼"),Map.entry("guanyu","夜读春秋"),Map.entry("huanggai","赤壁赴火"),
        Map.entry("huangyueying","巧械天工"),Map.entry("ganning","锦帆踏浪"),Map.entry("huatuo","青囊济世"),
        Map.entry("sunquan","坐镇江东"),Map.entry("lumeng","白衣渡江"),Map.entry("simayi","鹰视狼顾"),
        Map.entry("guojia","遗计定辽"),Map.entry("zhouyu","弦动赤壁"),Map.entry("luxun","火烧连营"),
        Map.entry("xuchu","虎痴撼阵"),Map.entry("zhangliao","威震逍遥"),Map.entry("xiahoudun","独目奋战"),
        Map.entry("zhenji","洛水凌波"),Map.entry("zhugeliang","空城抚琴"),Map.entry("machao","铁骑破阵"),
        Map.entry("daqiao","国色折扇"),Map.entry("sunshangxiang","枭姬试弓"),Map.entry("lvbu","方天破阵"),
        Map.entry("diaochan","闭月旋舞"));
    private static final Properties preferences=new Properties();
    private static boolean loaded;
    private HeroSkins() {}
    public static List<Skin> available(String hero,ResourceLocation classic){
        var skins=new ArrayList<Skin>();
        if(classic!=null)skins.add(new Skin("classic",classic.getPath().contains("_faction_v2")?"势力新绘":"经典",classic));
        var legacy=HeroRenderer.legacy(hero);
        if(legacy!=null&&!legacy.equals(classic))skins.add(new Skin("legacy","旧版立绘",legacy));
        if(ACTION_NAMES.containsKey(hero) && dev.sgs.validation.client.CollectionClient.ownsSkin(hero,"action")){
            var texture=ProbeMod.id("textures/gui/heroes/"+hero+"_action_v2.png");
            if(Minecraft.getInstance().getResourceManager().getResource(texture).isPresent())
                skins.add(new Skin("action",ACTION_NAMES.get(hero),texture));
        }
        return List.copyOf(skins);
    }
    /** Remote portraits depend on the owner's server authorization, not this viewer's collection. */
    public static ResourceLocation syncedTexture(String hero,String id,ResourceLocation classic){
        ResourceLocation texture=classic;
        if("action".equals(id) && ACTION_NAMES.containsKey(hero))texture=ProbeMod.id("textures/gui/heroes/"+hero+"_action_v2.png");
        else if("legacy".equals(id) && HeroRenderer.legacy(hero)!=null)texture=HeroRenderer.legacy(hero);
        return texture!=null && Minecraft.getInstance().getResourceManager().getResource(texture).isPresent()?texture:classic;
    }
    public static Skin selected(String hero,ResourceLocation classic){
        load();var skins=available(hero,classic);String id=preferences.getProperty(hero,"action");
        return skins.stream().filter(s->s.id().equals(id)).findFirst().orElse(skins.isEmpty()?null:skins.getFirst());
    }
    public static boolean select(String hero,String id,ResourceLocation classic){
        load();if(available(hero,classic).stream().noneMatch(s->s.id().equals(id)))return false;
        var path=Minecraft.getInstance().gameDirectory.toPath().resolve("config/sgs-hero-skins.properties");
        var next=new Properties();next.putAll(preferences);next.setProperty(hero,id);
        try{
            Files.createDirectories(path.getParent());var temporary=Files.createTempFile(path.getParent(),"sgs-skins-",".tmp");
            try{
                try(var out=Files.newOutputStream(temporary)){next.store(out,"SGS local hero cosmetics");}
                try{Files.move(temporary,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
                catch(java.nio.file.AtomicMoveNotSupportedException ignored){Files.move(temporary,path,StandardCopyOption.REPLACE_EXISTING);}
            }finally{Files.deleteIfExists(temporary);}
            preferences.clear();preferences.putAll(next);return true;
        }catch(java.io.IOException ex){ProbeMod.LOG.warn("Could not save hero skin preferences",ex);return false;}
    }
    private static void load(){
        if(loaded)return;loaded=true;
        var path=Minecraft.getInstance().gameDirectory.toPath().resolve("config/sgs-hero-skins.properties");
        if(Files.exists(path))try(var in=Files.newInputStream(path)){preferences.load(in);}
        catch(java.io.IOException | IllegalArgumentException ex){ProbeMod.LOG.warn("Could not read hero skin preferences",ex);}
    }
    public static void reload(){loaded=false;preferences.clear();}
}
