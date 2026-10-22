package dev.sgs.core.addon;
import java.nio.file.Path;
import java.util.*;
/** java -cp sgs-core.jar dev.sgs.core.addon.HeroPackCli validate DIRECTORY FILE.jar ... */
public final class HeroPackCli {
  public static void main(String[] args)throws Exception{
    if(args.length<3 || !args[0].equals("validate"))throw new IllegalArgumentException("validate DIRECTORY FILE.jar ...");
    try(var loaded=HeroPackLoader.loadDirectory(Path.of(args[1]),Arrays.asList(args).subList(2,args.length))){
      for(var receipt:loaded.receipts())System.out.println("ACCEPT "+receipt.manifest().id()+" "+receipt.manifest().version()+" sha256="+receipt.sha256());
      for(var hero:loaded.catalog().heroes())if(hero.id().contains(":"))System.out.println("HERO "+hero.id()+" skills="+hero.skillIds());
    }
  }
}
