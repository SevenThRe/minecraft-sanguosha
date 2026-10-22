package dev.sgs.core.addon;

import java.util.*;

/** Display links for actual built-in grants in MythicRules; never grants gameplay abilities. */
public final class DerivedSkillPresentations {
    private static final Map<String,List<String>> GRANTS=Map.of(
        "zaoxian",List.of("jixi"),
        "zhiji",List.of("guanxing"),
        "ruoyu",List.of("jijiang"),
        "hunzi",List.of("yingzi","yinghun"),
        "huangtian",List.of("huangtian_give"),
        "zhiba",List.of("zhiba_pindian"));
    private DerivedSkillPresentations(){}
    public static List<SkillPresentation> resolve(HeroCatalog catalog,List<SkillPresentation> base){
        var own=new HashSet<String>();base.forEach(s->own.add(s.id()));
        var result=new LinkedHashMap<String,SkillPresentation>();
        for(var source:base)for(var id:GRANTS.getOrDefault(source.id(),List.of()))
            if(!own.contains(id))catalog.skill(id).ifPresent(s->result.putIfAbsent(id,s));
        return List.copyOf(result.values());
    }
}
