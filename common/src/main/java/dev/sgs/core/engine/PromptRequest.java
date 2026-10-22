package dev.sgs.core.engine;

import java.util.List;

import dev.sgs.core.card.CardInstance;
import dev.sgs.core.card.CardType;
import dev.sgs.core.player.SgsPlayer;

/** A question the settlement queue puts to one player, with an absolute deadline. */
public final class PromptRequest {

  private final long id;
  private final PromptType type;
  private final SgsPlayer asker;
  private final SgsPlayer subject;
  private final List<CardType> allowedTypes;
  private final List<CardInstance> choices;
  private final long deadlineMillis;
  private final String skillId;
  private final List<Integer> allowedTargetSeats;
  private final boolean hiddenHandAvailable;

  public PromptRequest(
      long id,
      PromptType type,
      SgsPlayer asker,
      SgsPlayer subject,
      List<CardType> allowedTypes,
      long deadlineMillis) {
    this(id, type, asker, subject, allowedTypes, List.of(), deadlineMillis);
  }

  public PromptRequest(
      long id,
      PromptType type,
      SgsPlayer asker,
      SgsPlayer subject,
      List<CardType> allowedTypes,
      List<CardInstance> choices,
      long deadlineMillis) {
    this(id,type,asker,subject,allowedTypes,choices,deadlineMillis,"");
  }

  public PromptRequest(long id,PromptType type,SgsPlayer asker,SgsPlayer subject,
      List<CardType> allowedTypes,List<CardInstance> choices,long deadlineMillis,String skillId) {
    this(id,type,asker,subject,allowedTypes,choices,deadlineMillis,skillId,List.of(),false);
  }
  public PromptRequest(long id,PromptType type,SgsPlayer asker,SgsPlayer subject,
      List<CardType> allowedTypes,List<CardInstance> choices,long deadlineMillis,String skillId,
      List<Integer> allowedTargetSeats,boolean hiddenHandAvailable) {
    this.id = id;
    this.type = type;
    this.asker = asker;
    this.subject = subject;
    this.allowedTypes = List.copyOf(allowedTypes);
    this.choices = List.copyOf(choices);
    this.deadlineMillis = deadlineMillis;
    this.skillId = java.util.Objects.requireNonNull(skillId);
    this.allowedTargetSeats=List.copyOf(allowedTargetSeats);
    this.hiddenHandAvailable=hiddenHandAvailable;
  }

  public long id() {
    return id;
  }

  public PromptType type() {
    return type;
  }

  public SgsPlayer asker() {
    return asker;
  }

  /** The target of the Sha or the dying character. */
  public SgsPlayer subject() {
    return subject;
  }

  public List<CardType> allowedTypes() {
    return allowedTypes;
  }

  /** Wugu pool or the one publicly revealed Fire Attack card; not the payer's private options. */
  public List<CardInstance> choices() {
    return choices;
  }

  public long deadlineMillis() {
    return deadlineMillis;
  }
  public String skillId(){return skillId;}
  public List<Integer> allowedTargetSeats(){return allowedTargetSeats;}
  public boolean hiddenHandAvailable(){return hiddenHandAvailable;}
  public boolean allowsHiddenChoice(long cardId){
    return HandSelection.allowsHiddenChoice(type,hiddenHandAvailable,subject==null?0:subject.handCount(),cardId);
  }
}
