package dev.sgs.core.addon;
import java.util.*;
public record PackManifest(int format,int api,String id,String version,String name,String provider){
  public static final int FORMAT=1,API=1;
  public PackManifest{
    if(format!=FORMAT || api!=API)throw new IllegalArgumentException("unsupported hero pack format/API");
    if(id==null || !id.matches("[a-z][a-z0-9_]{1,31}") || id.equals("sgs") || id.equals("minecraft"))throw new IllegalArgumentException("invalid/reserved pack namespace");
    if(version==null || !version.matches("[0-9]+\\.[0-9]+\\.[0-9]+"))throw new IllegalArgumentException("version must be fixed major.minor.patch");
    HeroCatalog.text(name,64);
    if(provider==null || !provider.matches("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)+"))throw new IllegalArgumentException("invalid provider class");
  }
  public static PackManifest from(Properties p){
    var expected=Set.of("format","api","id","version","name","provider");
    if(!p.stringPropertyNames().equals(expected))throw new IllegalArgumentException("manifest keys must be "+expected);
    return new PackManifest(Integer.parseInt(p.getProperty("format")),Integer.parseInt(p.getProperty("api")),p.getProperty("id"),p.getProperty("version"),p.getProperty("name"),p.getProperty("provider"));
  }
}
