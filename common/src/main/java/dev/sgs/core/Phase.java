package dev.sgs.core;

/** Turn phases in strict execution order. */
public enum Phase {
  NONE("none"),
  PREPARE("prepare"),
  JUDGMENT("judgment"),
  DRAW("draw"),
  PLAY("play"),
  DISCARD("discard"),
  END("end");

  private final String id;

  Phase(String id) {
    this.id = id;
  }

  public String id() {
    return id;
  }
}
