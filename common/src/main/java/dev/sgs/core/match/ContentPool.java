package dev.sgs.core.match;

import dev.sgs.core.card.*;
import dev.sgs.core.util.IdSequencer;
import java.util.List;

/** Explicit physical content policy; development fixtures never silently become production rules. */
public enum ContentPool {
  DEVELOPMENT_119(DevelopmentDeckFactory.ID), JUNZHENG_160(JunzhengDeckFactory.ID),
  STANDARD_104("standard-104-qs-e876885-v1"), STANDARD_EX_108("standard-ex-108-qs-e876885-v1"),
  STANDARD_JUNZHENG_156("standard-junzheng-156-qs-e876885-v1"), JUNZHENG_52("junzheng-only-52-qs-e876885-v1"),
  EX_JUNZHENG_56("ex-junzheng-56-qs-e876885-v1"), EX_4("ex-only-4-qs-e876885-v1"), EMPTY("empty-v1");
  private final String id;
  ContentPool(String id){this.id=id;}
  public String id(){return id;}
  public List<CardInstance> create(IdSequencer ids){
    if(this==DEVELOPMENT_119)return DevelopmentDeckFactory.create(ids);
    return JunzhengDeckFactory.slots().stream().filter(s->includes(s.sourcePackage())).map(s->new CardInstance(ids.getAsLong(),CardDefinition.basic(s.type()),s.suit(),s.rank())).toList();
  }
  public static ContentPool selected(boolean standard,boolean ex,boolean junzheng){
    return standard?(ex?(junzheng?JUNZHENG_160:STANDARD_EX_108):(junzheng?STANDARD_JUNZHENG_156:STANDARD_104))
        :(ex?(junzheng?EX_JUNZHENG_56:EX_4):(junzheng?JUNZHENG_52:EMPTY));
  }
  private boolean includes(String pack){return switch(pack){
    case "standard"->this==JUNZHENG_160||this==STANDARD_104||this==STANDARD_EX_108||this==STANDARD_JUNZHENG_156;
    case "ex"->this==JUNZHENG_160||this==STANDARD_EX_108||this==EX_JUNZHENG_56||this==EX_4;
    case "junzheng"->this==JUNZHENG_160||this==STANDARD_JUNZHENG_156||this==JUNZHENG_52||this==EX_JUNZHENG_56;
    default->false;
  };}
  public int size(){return this==DEVELOPMENT_119?119:(int)JunzhengDeckFactory.slots().stream().filter(s->includes(s.sourcePackage())).count();}
}

