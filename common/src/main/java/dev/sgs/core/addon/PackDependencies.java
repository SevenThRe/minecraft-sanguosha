package dev.sgs.core.addon;
import java.util.*;
/** Optional API-initial sidecar, separate from the original six-field manifest. Exact versions only. */
public final class PackDependencies {
  public static final String RESOURCE="META-INF/sgs/dependencies.properties";
  private PackDependencies(){}
  public static void validate(String owner,Properties values,List<HeroPackLoader.Receipt> preceding){
    if(values.size()>32)throw new IllegalArgumentException("too many pack dependencies");
    for(var id:values.stringPropertyNames()){
      String version=values.getProperty(id);
      if(!id.matches("[a-z][a-z0-9_]{1,31}")||id.equals(owner)||!version.matches("[0-9]+\\.[0-9]+\\.[0-9]+"))throw new IllegalArgumentException("invalid exact dependency "+id);
      var actual=preceding.stream().filter(r->r.manifest().id().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("dependency must be enabled earlier: "+id+"@"+version));
      if(!actual.manifest().version().equals(version))throw new IllegalArgumentException("dependency version mismatch: "+id+" requires "+version+", enabled "+actual.manifest().version());
    }
  }
}
