package dev.sgs.core.addon;
/** Server lifecycle hook. Install before constructing rooms/matches; stop/reset after all workers. */
public final class AddonRuntime {
  private static volatile HeroCatalog current=HeroCatalog.builtins();
  private AddonRuntime(){}
  public static HeroCatalog current(){return current;}
  public static void installForServer(HeroCatalog catalog){current=java.util.Objects.requireNonNull(catalog);}
  public static void resetAfterServerStopped(){current=HeroCatalog.builtins();}
}
