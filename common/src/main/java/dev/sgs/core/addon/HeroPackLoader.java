package dev.sgs.core.addon;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.jar.*;

/** Only exact server-selected local jar filenames execute code. Java packs are trusted, not sandboxed. */
public final class HeroPackLoader {
  public static final String MANIFEST="META-INF/sgs/hero-pack.properties";
  public static final String SERVICE="META-INF/services/"+HeroPack.class.getName();
  public record Receipt(String filename,PackManifest manifest,String sha256){}
  public record Loaded(HeroCatalog catalog,List<Receipt> receipts,URLClassLoader classLoader) implements AutoCloseable{
    public Loaded{receipts=List.copyOf(receipts);}
    public void close()throws IOException{classLoader.close();}
  }
  private HeroPackLoader(){}
  /** configRoot/hero-packs.properties: api=1, enabled=exact.jar,other.jar; absent means none. */
  public static Loaded loadConfigured(Path configRoot)throws IOException{
    Path config=configRoot.resolve("hero-packs.properties");
    if(!Files.exists(config))return loadDirectory(configRoot.resolve("hero-packs"),List.of());
    if(Files.size(config)>8192)throw new IllegalArgumentException("hero pack configuration too large");
    var properties=new Properties();try(var reader=Files.newBufferedReader(config,StandardCharsets.UTF_8)){properties.load(reader);}
    if(!properties.stringPropertyNames().equals(Set.of("api","enabled")) || !properties.getProperty("api").equals("1"))throw new IllegalArgumentException("invalid server hero pack configuration");
    String enabled=properties.getProperty("enabled").strip();
    return loadDirectory(configRoot.resolve("hero-packs"),enabled.isEmpty()?List.of():Arrays.stream(enabled.split(",",-1)).map(String::strip).toList());
  }
  public static Loaded loadDirectory(Path directory,List<String> enabledFiles)throws IOException{
    if(enabledFiles.size()>32 || enabledFiles.stream().distinct().count()!=enabledFiles.size())throw new IllegalArgumentException("invalid enabled pack list");
    var root=directory.toAbsolutePath().normalize();var paths=new ArrayList<Path>();var receipts=new ArrayList<Receipt>();
    for(String filename:enabledFiles){
      if(filename==null || !filename.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,120}\\.jar"))throw new IllegalArgumentException("invalid allowlisted filename");
      Path path=root.resolve(filename).normalize();
      if(!path.getParent().equals(root) || Files.isSymbolicLink(path) || !Files.isRegularFile(path) || Files.size(path)>32*1024*1024)throw new IllegalArgumentException("invalid pack file "+filename);
      PackManifest manifest;
      try(var jar=new JarFile(path.toFile())){
        var props=new Properties();try(var data=new StringReader(entry(jar,MANIFEST,8192))){props.load(data);}manifest=PackManifest.from(props);
        var providers=entry(jar,SERVICE,4096).lines().map(line->line.split("#",2)[0].strip()).filter(line->!line.isEmpty()).toList();
        if(!providers.equals(List.of(manifest.provider())))throw new IllegalArgumentException("provider service/manifest mismatch");
        if(jar.getJarEntry(PackDependencies.RESOURCE)!=null){
          var dependencies=new Properties();try(var data=new StringReader(entry(jar,PackDependencies.RESOURCE,8192))){dependencies.load(data);}
          PackDependencies.validate(manifest.id(),dependencies,receipts);
        }
      }
      if(receipts.stream().anyMatch(r->r.manifest().id().equals(manifest.id())))throw new IllegalArgumentException("duplicate pack namespace "+manifest.id());
      try{receipts.add(new Receipt(filename,manifest,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)))));}
      catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
      paths.add(path.toRealPath());
    }
    URL[] urls=new URL[paths.size()];for(int i=0;i<paths.size();i++)urls[i]=paths.get(i).toUri().toURL();
    var loader=new URLClassLoader(urls,HeroPack.class.getClassLoader());
    try{
      var builder=HeroCatalog.builder();
      for(int i=0;i<receipts.size();i++){
        var manifest=receipts.get(i).manifest();Class<?> type=Class.forName(manifest.provider(),false,loader);
        if(!HeroPack.class.isAssignableFrom(type) || !Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath().equals(paths.get(i)))
          throw new IllegalArgumentException("provider type/origin mismatch "+manifest.provider());
        builder.add(manifest,(HeroPack)type.getConstructor().newInstance(),receipts.get(i).sha256());
      }
      var catalog=builder.build();
      for(int i=0;i<receipts.size();i++){
        String prefix=receipts.get(i).manifest().id()+":";
        try(var jar=new JarFile(paths.get(i).toFile())){
          for(var view:catalog.present(catalog.heroes().stream().map(dev.sgs.core.hero.HeroDefinition::id).filter(id->id.startsWith(prefix)).toList())){
            if(view.portrait().isEmpty())continue;
            String resource="assets/"+view.portrait().replace(':','/');var image=jar.getJarEntry(resource);
            if(image==null || image.isDirectory() || image.getSize()>4*1024*1024)throw new IllegalArgumentException("missing/oversized portrait "+resource);
            try(var in=new DataInputStream(jar.getInputStream(image))){
              if(in.readLong()!=0x89504e470d0a1a0aL || in.readInt()!=13 || in.readInt()!=0x49484452)throw new IllegalArgumentException("invalid portrait PNG "+resource);
              int width=in.readInt(),height=in.readInt();if(width<=0 || width>4096 || height<=0 || height>4096)throw new IllegalArgumentException("invalid portrait size "+resource);
            }
          }
        }
      }
      return new Loaded(catalog,receipts,loader);
    }catch(Exception|LinkageError e){loader.close();throw new IllegalArgumentException("hero pack load rejected: "+e.getMessage(),e);}
  }
  private static String entry(JarFile jar,String name,int limit)throws IOException{
    var entry=jar.getJarEntry(name);if(entry==null || entry.isDirectory())throw new IllegalArgumentException("missing "+name);
    try(var in=jar.getInputStream(entry)){byte[] bytes=in.readNBytes(limit+1);if(bytes.length>limit)throw new IllegalArgumentException("oversized "+name);return new String(bytes,StandardCharsets.UTF_8);}
  }
}
