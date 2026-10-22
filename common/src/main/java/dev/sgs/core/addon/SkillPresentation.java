package dev.sgs.core.addon;

/** Server-sent declarative UI hints. All actual availability, costs and targets remain server rules. */
public record SkillPresentation(String id,String name,String description,Kind kind,Target target,
    int minimumCards,int maximumCards,boolean lordOnly,boolean allowEquipmentCosts){
  /** Preserve the API v1 constructor descriptor used by already compiled external packs. */
  public SkillPresentation(String id,String name,String description,Kind kind,Target target,
      int minimumCards,int maximumCards,boolean lordOnly){
    this(id,name,description,kind,target,minimumCards,maximumCards,lordOnly,false);
  }
  public enum Kind { ACTIVE, TRIGGERED, PASSIVE, TOGGLE, LIMITED }
  public enum Target { NONE, OTHER, ANY }
  public SkillPresentation{
    HeroCatalog.text(id,128);HeroCatalog.text(name,32);HeroCatalog.text(description,512);
    java.util.Objects.requireNonNull(kind);java.util.Objects.requireNonNull(target);
    if(minimumCards<0 || maximumCards<minimumCards || maximumCards>160)throw new IllegalArgumentException("invalid skill card bounds");
  }
}
