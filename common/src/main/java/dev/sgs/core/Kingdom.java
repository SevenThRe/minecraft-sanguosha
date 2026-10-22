package dev.sgs.core;

/** Hero kingdoms (factions). MVP heroes are all Shu. */
public enum Kingdom {
  QUN("qun"),
  SHU("shu"),
  WEI("wei"),
  WU("wu");

  private final String id;

  Kingdom(String id) {
    this.id = id;
  }

  public String id() {
    return id;
  }
}
