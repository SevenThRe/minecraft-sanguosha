package dev.sgs.core.collection;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.*;

public record CollectionPolicy(boolean enabled,int guaranteedNewBelow,long duplicateSouls,
        long heroPrice,boolean marketEnabled,long startingTokens,long tokensPerMatch,Set<String> drawPool,Map<String,Long> prices, Currency tokenCurrency,Currency soulCurrency,long drawTokenCost,long startingSouls,boolean standardFree,Set<String> freeHeroes,CollectionRewards rewards) {
    public record Currency(String name,String description,String texture,String item){
        public Currency{
            if(name==null||name.isBlank()||name.length()>24||description==null||description.length()>256)throw new IllegalArgumentException("货币名称或说明无效");
            for(String resource:List.of(texture,item))if(!resource.isEmpty()&&(!resource.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")||resource.contains("..")||resource.contains("://")))throw new IllegalArgumentException("货币材质必须为资源ID");
            if(!texture.isEmpty()&&!texture.endsWith(".png"))throw new IllegalArgumentException("货币材质必须为PNG");
        }
    }
    public static final Currency DEFAULT_TOKEN=new Currency("武将招募令","用于招募武将。","","");
    public static final Currency DEFAULT_SOUL=new Currency("将符","用于兑换指定武将。","","");
    public CollectionPolicy(boolean enabled,int guaranteedNewBelow,long duplicateSouls,long heroPrice,boolean marketEnabled,long startingTokens,long tokensPerMatch,Set<String> drawPool,Map<String,Long> prices){this(enabled,guaranteedNewBelow,duplicateSouls,heroPrice,marketEnabled,startingTokens,tokensPerMatch,drawPool,prices,DEFAULT_TOKEN,DEFAULT_SOUL,1,0);}
    public CollectionPolicy(boolean enabled,int guaranteedNewBelow,long duplicateSouls,long heroPrice,boolean marketEnabled,long startingTokens,long tokensPerMatch,Set<String> drawPool,Map<String,Long> prices,Currency tokenCurrency,Currency soulCurrency,long drawTokenCost,long startingSouls){
        this(enabled,guaranteedNewBelow,duplicateSouls,heroPrice,marketEnabled,startingTokens,tokensPerMatch,drawPool,prices,tokenCurrency,soulCurrency,drawTokenCost,startingSouls,false,Set.of(),CollectionRewards.load(new Properties(),tokensPerMatch));
    }
    public Set<String> freeHeroes(Collection<String> catalog){
        var free=new TreeSet<String>();for(String id:catalog)if(freeHeroes.contains(id) || standardFree && dev.sgs.core.hero.StandardHeroes.CATALOG.stream().anyMatch(h->h.id().equals(id) && dev.sgs.core.config.PackPolicy.heroPack(h).equals("heroes.standard")))free.add(id);
        return Set.copyOf(free);
    }
    public Set<String> playable(Collection<String> catalog,Set<String> owned){var result=new HashSet<>(owned);result.addAll(freeHeroes(catalog));return Set.copyOf(result);}
    public static final String TEMPLATE="""
        # 独立武将养成；/sgs reload 生效。关闭时自由选将，已有资产保留。
        enabled=true
        # 拥有数量低于此值且卡池仍有未拥有武将时，抽取必出新武将。
        guaranteed_new_below=10
        # 基础武将直接可用，不进入抽奖/兑换池；可追加稳定ID。
        free.standard=true
        free.heroes=
        # 重复武将兑换的将符；沿用 souls 配置键兼容已有存档。
        duplicate_souls=20
        market.enabled=true
        market.hero_price=100
        # 账号首次建立时获得的招募令；修改不重复补发旧账号。
        starting_tokens=4
        tokens_per_match=1
        starting_souls=0
        draw.token_cost=1
        # 显示与实际消耗均由服务器配置；材质为客户端资源包的 PNG，item 可使用物品模型。
        currency.tokens.name=武将招募令
        currency.tokens.description=用于招募武将。
        currency.tokens.texture=
        currency.tokens.item=
        currency.souls.name=将符
        currency.souls.description=用于兑换指定武将。
        currency.souls.texture=
        currency.souls.item=
        # 留空使用全部已加载武将；可填稳定 ID，以逗号分隔。
        draw_pool=
        # 单将定价例：price.caocao=120
        """+CollectionRewards.TEMPLATE;
    public CollectionPolicy {
        if(guaranteedNewBelow<0 || guaranteedNewBelow>100000 || duplicateSouls<0 || heroPrice<1 || startingTokens<0 || tokensPerMatch<0)
            throw new IllegalArgumentException("养成数值无效");
        Objects.requireNonNull(tokenCurrency);Objects.requireNonNull(soulCurrency);Objects.requireNonNull(rewards);
        freeHeroes=Set.copyOf(freeHeroes);freeHeroes.forEach(CollectionAccount::checkId);
        if(drawTokenCost<1||drawTokenCost>1_000_000_000L||startingSouls<0||startingSouls>1_000_000_000L)throw new IllegalArgumentException("货币数量无效");
        drawPool=Set.copyOf(drawPool);prices=Map.copyOf(prices);
        if(prices.values().stream().anyMatch(v->v<1))throw new IllegalArgumentException("武将价格必须大于0");
    }
    public long price(String hero){return prices.getOrDefault(hero,heroPrice);}
    public List<String> pool(Collection<String> catalog){var free=freeHeroes(catalog);return catalog.stream().filter(id->!free.contains(id)).filter(id->drawPool.isEmpty() || drawPool.contains(id)).sorted().toList();}
    public static CollectionPolicy load(Path directory) throws IOException {
        Files.createDirectories(directory);var file=directory.resolve("collection.properties");
        if(!Files.exists(file))Files.writeString(file,TEMPLATE,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
        if(Files.size(file)>65536)throw new IllegalArgumentException("养成配置过大");
        var p=new Properties();try(var r=Files.newBufferedReader(file,StandardCharsets.UTF_8)){p.load(r);}
        var known=Set.of("enabled","guaranteed_new_below","duplicate_souls","market.enabled","market.hero_price","starting_tokens","tokens_per_match","draw_pool","starting_souls","draw.token_cost","currency.tokens.name","currency.tokens.description","currency.tokens.texture","currency.tokens.item","currency.souls.name","currency.souls.description","currency.souls.texture","currency.souls.item");
        var prices=new TreeMap<String,Long>();
        for(String key:p.stringPropertyNames())if(key.startsWith("price.")){
            var hero=key.substring(6);CollectionAccount.checkId(hero);prices.put(hero,number(p,key,100));
        }else if(!known.contains(key) && !Set.of("free.standard","free.heroes").contains(key) && !CollectionRewards.keys().contains(key))throw new IllegalArgumentException("未知养成配置："+key);
        var pool=new TreeSet<String>();for(String id:p.getProperty("draw_pool","").split(","))if(!id.isBlank()){CollectionAccount.checkId(id.strip());pool.add(id.strip());}
        var free=new TreeSet<String>();for(String id:p.getProperty("free.heroes","").split(","))if(!id.isBlank()){CollectionAccount.checkId(id.strip());free.add(id.strip());}
        return new CollectionPolicy(bool(p,"enabled",false),Math.toIntExact(number(p,"guaranteed_new_below",10)),number(p,"duplicate_souls",20),
            number(p,"market.hero_price",100),bool(p,"market.enabled",true),number(p,"starting_tokens",4),number(p,"tokens_per_match",1),pool,prices,currency(p,"tokens",DEFAULT_TOKEN),currency(p,"souls",DEFAULT_SOUL),number(p,"draw.token_cost",1),number(p,"starting_souls",0),bool(p,"free.standard",true),free,CollectionRewards.load(p,number(p,"tokens_per_match",1)));
    }
    private static Currency currency(Properties p,String key,Currency fallback){String prefix="currency."+key+".";return new Currency(p.getProperty(prefix+"name",fallback.name()).strip(),p.getProperty(prefix+"description",fallback.description()).strip(),p.getProperty(prefix+"texture","").strip(),p.getProperty(prefix+"item","").strip());}
    private static boolean bool(Properties p,String key,boolean fallback){var v=p.getProperty(key,""+fallback).strip();if(!Set.of("true","false").contains(v))throw new IllegalArgumentException(key+" 必须为true/false");return Boolean.parseBoolean(v);}
    private static long number(Properties p,String key,long fallback){try{long n=Long.parseLong(p.getProperty(key,""+fallback).strip());if(n<0 || n>1_000_000_000L)throw new NumberFormatException();return n;}catch(NumberFormatException e){throw new IllegalArgumentException(key+" 必须为0..1000000000整数");}}
}
