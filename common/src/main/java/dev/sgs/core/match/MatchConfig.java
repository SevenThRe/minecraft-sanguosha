package dev.sgs.core.match;

/** Timing configuration for one live match. The integration layer fills this from SgsConfig. */
public record MatchConfig(
    int responseTimeoutSeconds,
    int turnTimeoutSeconds,
    int selectionTimeoutSeconds,
    long botStepDelayMillis,
    java.util.List<dev.sgs.core.hero.HeroDefinition> heroPool,
    dev.sgs.core.room.RoomSettings roomSettings,
    ContentPool contentPool, boolean heroBansEnabled, boolean openingHandEnabled) {

  public MatchConfig(int response,int turn,int selection,long delay,java.util.List<dev.sgs.core.hero.HeroDefinition> heroes,
      dev.sgs.core.room.RoomSettings settings,ContentPool pool,boolean bans){this(response,turn,selection,delay,heroes,settings,pool,bans,false);}
  public MatchConfig withOpeningHandSelection(boolean enabled){return new MatchConfig(responseTimeoutSeconds,turnTimeoutSeconds,selectionTimeoutSeconds,botStepDelayMillis,heroPool,roomSettings,contentPool,heroBansEnabled,enabled);}

  public MatchConfig(int response,int turn,int selection,long delay,java.util.List<dev.sgs.core.hero.HeroDefinition> heroes,
      dev.sgs.core.room.RoomSettings settings,ContentPool pool){this(response,turn,selection,delay,heroes,settings,pool,false);}
  public MatchConfig withBotDelay(long millis){return new MatchConfig(responseTimeoutSeconds,turnTimeoutSeconds,selectionTimeoutSeconds,millis,heroPool,roomSettings,contentPool,heroBansEnabled,openingHandEnabled);}
  public MatchConfig withHeroBans(boolean enabled){return new MatchConfig(responseTimeoutSeconds,turnTimeoutSeconds,selectionTimeoutSeconds,botStepDelayMillis,heroPool,roomSettings,contentPool,enabled,openingHandEnabled);}

  public MatchConfig {
    roomSettings=java.util.Objects.requireNonNull(roomSettings);
    contentPool=java.util.Objects.requireNonNull(contentPool);
    heroPool=java.util.List.copyOf(heroPool);
    if(heroPool.size()<3 || heroPool.stream().map(dev.sgs.core.hero.HeroDefinition::id).distinct().count()!=heroPool.size())
      throw new IllegalArgumentException("hero pool requires at least three distinct definitions");
  }

  public MatchConfig(int response, int turn, int selection, long delay,
      java.util.List<dev.sgs.core.hero.HeroDefinition> heroes) {
    this(response, turn, selection, delay, heroes, dev.sgs.core.room.RoomSettings.defaults(),ContentPool.DEVELOPMENT_119);
  }

  /** Explicit compatibility constructor for existing deterministic callers. */
  public MatchConfig(int response,int turn,int selection,long delay,
      java.util.List<dev.sgs.core.hero.HeroDefinition> heroes,dev.sgs.core.room.RoomSettings settings){
    this(response,turn,selection,delay,heroes,settings,ContentPool.DEVELOPMENT_119);
  }

  public MatchConfig withContentPool(ContentPool pool){
    return new MatchConfig(responseTimeoutSeconds,turnTimeoutSeconds,selectionTimeoutSeconds,
        botStepDelayMillis,heroPool,roomSettings,pool,heroBansEnabled,openingHandEnabled);
  }

  /** Snapshot room rules once; each live session keeps its own filtered catalog and timing. */
  public MatchConfig withRoomSettings(dev.sgs.core.room.RoomSettings settings) {
    return new MatchConfig(settings.responseSeconds(), settings.turnSeconds(), settings.selectionSeconds(),
        botStepDelayMillis, heroPool.stream().filter(hero -> !settings.bannedHeroes().contains(hero.id())).toList(),
        settings,contentPool,heroBansEnabled,openingHandEnabled);
  }

  /** Project convention: one free whole-hand replacement, not inferred video economy. */
  public boolean openingHandSelection(int players){return openingHandEnabled && contentPool!=ContentPool.DEVELOPMENT_119 && (players==4 || players==6 || players==8);}
  public int openingHandSelectionSeconds(){return Math.min(30,Math.max(1,selectionTimeoutSeconds));}

  public boolean identityIntroduction(int players){return contentPool!=ContentPool.DEVELOPMENT_119 && (players==6 || players==8);}
  public int identityIntroductionSeconds(){return Math.min(30,Math.max(1,selectionTimeoutSeconds));}

  /** Normal rooms reserve four distinct candidates per seat. Legacy engine fixtures are explicit. */
  public int heroCandidateCount(int players){
    return uniqueHeroSelection(players)?4:3;
  }
  public boolean uniqueHeroSelection(int players){return contentPool!=ContentPool.DEVELOPMENT_119;}

  public void validateHeroOffers(int players){
    if(contentPool.size()<players*4+2)throw new ContentCapacityException(players,contentPool.size());
    if(uniqueHeroSelection(players) && heroPool.size()<(long)players*heroCandidateCount(players))
      throw new HeroOfferCapacityException(players,heroPool.size());
  }
  public static final class HeroOfferCapacityException extends IllegalArgumentException {
    public HeroOfferCapacityException(int players,int available){
      super("可用武将不足："+players+" 人局每人需 4 名独立候选，共需 "+(players*4)+" 名，当前 "+available+" 名");
    }
  }
  public static final class ContentCapacityException extends IllegalArgumentException {
    public ContentCapacityException(int players,int available){super("启用的牌包不足以开局：需要至少 "+(players*4+2)+" 张，当前 "+available+" 张");}
  }

  /** Existing deterministic development fixtures retain their original four-hero pool. */
  public MatchConfig(int response,int turn,int selection,long delay){
    this(response,turn,selection,delay,dev.sgs.core.hero.StandardHeroes.ALL);
  }

  /** Normal identity and explicit project 2v2 rooms share the pinned physical Junzheng table. */
  public static MatchConfig standard() {
    return new MatchConfig(10, 15, 30, 2000,dev.sgs.core.addon.AddonRuntime.current().heroes()).withContentPool(ContentPool.JUNZHENG_160).withHeroBans(true);
  }

  /** Fast config for automated tests: short deadlines and no pacing delay. */
  public static MatchConfig fast() {
    return new MatchConfig(1, 1, 1, 0);
  }
}
