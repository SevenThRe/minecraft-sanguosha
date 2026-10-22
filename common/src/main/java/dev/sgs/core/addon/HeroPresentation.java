package dev.sgs.core.addon;
import java.util.List;
/** Public names/descriptions are data, not classes the client must load. Empty portrait uses fallback. */
public record HeroPresentation(String id,String name,String description,String portrait,List<SkillPresentation> skills){
  public HeroPresentation{
    HeroCatalog.text(id,128);HeroCatalog.text(name,32);HeroCatalog.text(description,512);
    if(portrait==null || (!portrait.isEmpty() && !portrait.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")))throw new IllegalArgumentException("invalid portrait resource");
    skills=List.copyOf(skills);if(skills.size()>8)throw new IllegalArgumentException("too many skills");
  }
}
