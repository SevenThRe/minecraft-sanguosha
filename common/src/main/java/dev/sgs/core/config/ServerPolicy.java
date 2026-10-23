package dev.sgs.core.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Set;

/** Server-owned switches, deliberately independent of Minecraft and client preferences. */
public record ServerPolicy(boolean six, boolean eight, boolean twoVTwo, boolean matchmaking,
        boolean merchants, boolean virtualTableEnabled, boolean requireDeck, int maxRooms, PackPolicy packs, boolean openingHandEnabled) {
    public ServerPolicy(boolean six,boolean eight,boolean twoVTwo,boolean matchmaking,boolean merchants,boolean virtualTableEnabled,boolean requireDeck,int maxRooms,PackPolicy packs){
        this(six,eight,twoVTwo,matchmaking,merchants,virtualTableEnabled,requireDeck,maxRooms,packs,false);
    }
    public ServerPolicy(boolean six,boolean eight,boolean twoVTwo,boolean matchmaking,boolean merchants,boolean virtualTableEnabled,boolean requireDeck,int maxRooms){
        this(six,eight,twoVTwo,matchmaking,merchants,virtualTableEnabled,requireDeck,maxRooms,PackPolicy.defaults());
    }
    public static final String FILE_NAME = "server.properties";
    public static final String TEMPLATE = """
        # 三国杀服务器配置，UTF-8。修改后由管理员执行 /sgs reload；也可重启服务器。
        # 房间按玩法独立控制；关闭后禁止新建、加入和开局，已有对局继续。
        rooms.six.enabled=true
        rooms.eight.enabled=true
        rooms.two_v_two.enabled=true
        rooms.max=64
        # 是否允许开局免费更换一次手牌，仅影响新对局。
        match.opening_hand.enabled=false
        matchmaking.enabled=true
        # 排位系统尚未实现，必须保持 false；不会以普通匹配冒充排位。
        ranked.enabled=false
        merchants.enabled=true
        # 是否允许虚拟牌桌。false 时须通过实体牌桌进入，并在创建/加入/开局时仍在附近。
        tables.virtual.enabled=true
        # 房主创建和开局须携带可重复使用的卡组包，不消耗；其他玩家共用。
        survival.require_host_deck=true
        """;
    private static final Set<String> KEYS = Set.of("rooms.six.enabled", "rooms.eight.enabled",
        "rooms.two_v_two.enabled", "rooms.max", "matchmaking.enabled", "ranked.enabled",
        "merchants.enabled", "merchants.percent", "merchants.table_price", "merchants.deck_price",
        "tables.virtual.enabled", "survival.require_table", "survival.require_host_deck", "match.opening_hand.enabled");

    public static ServerPolicy load(Path directory) throws IOException {
        Files.createDirectories(directory);
        Path file = directory.resolve(FILE_NAME);
        if (!Files.exists(file)) {
            try { Files.writeString(file, TEMPLATE, StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE_NEW); }
            catch (java.nio.file.FileAlreadyExistsException ignored) { /* another startup won */ }
        }
        if (Files.size(file) > 32768) throw new IllegalArgumentException("服务器配置超过 32 KiB");
        Properties p = new Properties();
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { p.load(reader); }
        for (String key : p.stringPropertyNames()) if (!KEYS.contains(key))
            throw new IllegalArgumentException("未知配置项: " + key);
        if (bool(p, "ranked.enabled", false)) throw new IllegalArgumentException("排位尚未实现，请保持 ranked.enabled=false");
        var policy=new ServerPolicy(bool(p,"rooms.six.enabled",true), bool(p,"rooms.eight.enabled",true),
            bool(p,"rooms.two_v_two.enabled",true),bool(p,"matchmaking.enabled",true),
            bool(p,"merchants.enabled",true),virtualTableEnabled(p),
            bool(p,"survival.require_host_deck",true),number(p,"rooms.max",64,1,256),PackPolicy.load(directory),bool(p,"match.opening_hand.enabled",false));
        if(p.containsKey("survival.require_table") || p.containsKey("merchants.percent") || p.containsKey("merchants.table_price") || p.containsKey("merchants.deck_price")) {
            var backup=directory.resolve(p.containsKey("survival.require_table") ? "server.properties.legacy-v2.bak" : "server.properties.legacy-v1.bak");
            if(!Files.exists(backup))Files.copy(file,backup);
            String migrated=TEMPLATE;
            for(String key:p.stringPropertyNames())if(!key.equals("survival.require_table") && (!key.startsWith("merchants.") || key.equals("merchants.enabled")))
                migrated=migrated.replaceAll("(?m)^"+java.util.regex.Pattern.quote(key)+"=.*$",java.util.regex.Matcher.quoteReplacement(key+"="+p.getProperty(key)));
            migrated=migrated.replace("tables.virtual.enabled=true","tables.virtual.enabled="+policy.virtualTableEnabled());
            var temp=Files.createTempFile(directory,"server-",".tmp");
            try{Files.writeString(temp,migrated,StandardCharsets.UTF_8);try{Files.move(temp,file,java.nio.file.StandardCopyOption.ATOMIC_MOVE,java.nio.file.StandardCopyOption.REPLACE_EXISTING);}
                catch(java.nio.file.AtomicMoveNotSupportedException e){Files.move(temp,file,java.nio.file.StandardCopyOption.REPLACE_EXISTING);}}
            finally{Files.deleteIfExists(temp);}
        }
        return policy;
    }
    /** Compatibility accessor used by all server-side table admission gates. */
    public boolean requireTable() { return !virtualTableEnabled; }

    private static boolean virtualTableEnabled(Properties p) {
        // Always validate the legacy value, even when the explicit new key wins.
        boolean legacyRequired=bool(p,"survival.require_table",false);
        return bool(p,"tables.virtual.enabled",!legacyRequired);
    }
    public boolean modeEnabled(String id) {
        return switch (id) { case "six" -> six; case "eight" -> eight; case "two_v_two" -> twoVTwo; default -> false; };
    }
    private static boolean bool(Properties p, String key, boolean fallback) {
        String value=p.getProperty(key,Boolean.toString(fallback)).strip();
        if (!value.equals("true") && !value.equals("false")) throw new IllegalArgumentException(key+" 必须为 true 或 false");
        return Boolean.parseBoolean(value);
    }
    private static int number(Properties p, String key, int fallback, int min, int max) {
        int value;
        try { value=Integer.parseInt(p.getProperty(key,Integer.toString(fallback)).strip()); }
        catch(NumberFormatException e) { throw new IllegalArgumentException(key+" 必须为整数",e); }
        if(value<min || value>max)throw new IllegalArgumentException(key+" 范围为 "+min+".."+max);
        return value;
    }
}
