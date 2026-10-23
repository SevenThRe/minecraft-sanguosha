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
        Map.entry("liubei","sgs.ui.peach_garden_oath_e6f0a76d"),Map.entry("caocao","sgs.ui.gazing_over_the_sea_1a5f612b"),Map.entry("zhaoyun","sgs.ui.escape_from_changban_30ff5356"),
        Map.entry("zhangfei","sgs.ui.roar_at_dangyang_d8d3bea5"),Map.entry("guanyu","sgs.ui.reading_by_night_53202b7c"),Map.entry("huanggai","sgs.ui.flames_of_red_cliffs_77800ee0"),
        Map.entry("huangyueying","sgs.ui.masterful_invention_cf9e32ee"),Map.entry("ganning","sgs.ui.silken_sails_b227bf6c"),Map.entry("huatuo","sgs.ui.healing_the_world_c1f48326"),
        Map.entry("sunquan","sgs.ui.lord_of_jiangdong_c8f1823b"),Map.entry("lumeng","sgs.ui.crossing_in_white_5359593d"),Map.entry("simayi","sgs.ui.eagle_eyes_7c461152"),
        Map.entry("guojia","sgs.ui.a_final_stratagem_f698f47d"),Map.entry("zhouyu","sgs.ui.melody_of_red_cliffs_c2568e9f"),Map.entry("luxun","sgs.ui.burning_camps_836c23ea"),
        Map.entry("xuchu","sgs.ui.tiger_s_fury_f6bbe3e4"),Map.entry("zhangliao","sgs.ui.terror_at_xiaoyao_d9e77063"),Map.entry("xiahoudun","sgs.ui.one_eyed_warrior_ebdc68d4"),
        Map.entry("zhenji","sgs.ui.goddess_of_the_luo_d9cc41ab"),Map.entry("zhugeliang","sgs.ui.melody_of_the_empty_city_5dccf2d3"),Map.entry("machao","sgs.ui.iron_cavalry_charge_b80b29c7"),
        Map.entry("daqiao","sgs.ui.beauty_s_fan_e7e75168"),Map.entry("sunshangxiang","sgs.ui.warrior_princess_2f60f536"),Map.entry("lvbu","sgs.ui.sky_piercer_s_charge_ef9bb50e"),
        Map.entry("diaochan","sgs.ui.moonlit_dance_f92c5072"));
    private static final Properties preferences=new Properties();
    private static boolean loaded;
    private HeroSkins() {}
    public static List<Skin> available(String hero,ResourceLocation classic){
        var skins=new ArrayList<Skin>();
        if(classic!=null)skins.add(new Skin("classic",classic.getPath().contains("_faction_v2")?"sgs.ui.kingdom_artwork_d4fbdda1":"sgs.ui.classic_c0ca4b88",classic));
        var legacy=HeroRenderer.legacy(hero);
        if(legacy!=null&&!legacy.equals(classic))skins.add(new Skin("legacy","sgs.ui.legacy_artwork_6ad5df18",legacy));
        if(ACTION_NAMES.containsKey(hero) && dev.sgs.validation.client.CollectionClient.ownsSkin(hero,"action")){
            var texture=ProbeMod.id("textures/gui/heroes/"+hero+"_action_v2.png");
            if(Minecraft.getInstance().getResourceManager().getResource(texture).isPresent())
                skins.add(new Skin("action",ACTION_NAMES.get(hero),texture));
        }
        return skins.stream().map(skin->new Skin(skin.id(),dev.sgs.validation.client.ClientText.tr(skin.name()),skin.texture())).toList();
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
