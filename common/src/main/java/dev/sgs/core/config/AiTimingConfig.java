package dev.sgs.core.config;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.Properties;

/** Server-side pacing shared by AI card plays, responses and skill decisions. */
public record AiTimingConfig(long responseDelayMillis) {
  public AiTimingConfig { if(responseDelayMillis<0 || responseDelayMillis>60000)throw new IllegalArgumentException("AI responseDelayMillis must be 0..60000"); }
  public static AiTimingConfig load(Path directory) {
    try {
      Files.createDirectories(directory);var file=directory.resolve("ai.properties");
      if(!Files.exists(file))try {Files.writeString(file,"# AI play / response delay in milliseconds; applies to new matches\nresponseDelayMillis=2000\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);}catch(FileAlreadyExistsException ignored){}
      if(Files.size(file)>8192)throw new IllegalArgumentException("AI config too large");
      var p=new Properties();try(var reader=Files.newBufferedReader(file,StandardCharsets.UTF_8)){p.load(reader);}
      for(String key:p.stringPropertyNames())if(!key.equals("responseDelayMillis"))throw new IllegalArgumentException("Unknown AI config: "+key);
      return new AiTimingConfig(Long.parseLong(p.getProperty("responseDelayMillis","2000").trim()));
    } catch(IOException e) {throw new IllegalStateException("Cannot load AI timing config",e);}
  }
}
