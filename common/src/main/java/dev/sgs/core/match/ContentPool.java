package dev.sgs.core.match;

import dev.sgs.core.card.*;
import dev.sgs.core.util.IdSequencer;
import java.util.List;

/** Explicit physical content policy; development fixtures never silently become production rules. */
public enum ContentPool {
  DEVELOPMENT_119(DevelopmentDeckFactory.ID), JUNZHENG_160(JunzhengDeckFactory.ID);
  private final String id;
  ContentPool(String id){this.id=id;}
  public String id(){return id;}
  public List<CardInstance> create(IdSequencer ids){
    return this==JUNZHENG_160?JunzhengDeckFactory.create(ids):DevelopmentDeckFactory.create(ids);
  }
}
