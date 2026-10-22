package dev.sgs.core;

/** Hidden-role identities. */
public enum Identity {
  LORD("lord"),
  LOYALIST("loyalist"),
  REBEL("rebel"),
  RENEGADE("renegade");

  private final String id;

  Identity(String id) {
    this.id = id;
  }

  public String id() {
    return id;
  }
}
