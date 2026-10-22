package dev.sgs.core.persistence;

import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/** Common bounded memory grammar for idle and every host-owned continuation codec. */
final class SkillMemoryLayout {
  static final int MAX_RECORDS=80;
  private static final Pattern KEY=Pattern.compile("[0-7]:(?:[a-z][a-z0-9_]{0,63}|[a-z][a-z0-9_]{1,31}:(?:[a-z][a-z0-9_]{0,63}|__pack))");
  private SkillMemoryLayout(){}
  static void validate(String key,Map<String,Long> fields,int seats,boolean allowMythic){
    if(key==null||!KEY.matcher(key).matches()||key.charAt(0)-'0'>=seats
        ||fields.size()>(allowMythic&&key.equals("0:mythic")?4096:8))throw new IllegalArgumentException("invalid skill memory layout");
    fields.forEach((name,value)->{StateCheckpoint.text(name,64);Objects.requireNonNull(value);});
  }
}
