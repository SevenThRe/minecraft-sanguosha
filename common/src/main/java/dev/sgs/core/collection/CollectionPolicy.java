package dev.sgs.core.collection;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.*;

public record CollectionPolicy(boolean enabled,int guaranteedNewBelow,long duplicateSouls,
        long heroPrice,boolean marketEnabled,long startingTokens,long tokensPerMatch,Set<String> drawPool,Map<String,Long> prices) {
    public static final String TEMPLATE="""
        # 独立武将养成；/sgs reload 生效。关闭时自由选将，已有资产保留。
        enabled=false
        # 拥有数量低于此值且卡池仍有未拥有武将时，抽取必出新武将。
        guaranteed_new_below=10
        # 重复武将兑换的将符；沿用 souls 配置键兼容已有存档。
        duplicate_souls=20
        market.enabled=true
        market.hero_price=100
        # 账号首次建立时获得的招募令；修改不重复补发旧账号。
        starting_tokens=4
        tokens_per_match=1
        # 留空使用全部已加载武将；可填稳定 ID，以逗号分隔。
        draw_pool=
        # 单将定价例：price.caocao=120
        """;
    public CollectionPolicy {
        if(guaranteedNewBelow<0 || guaranteedNewBelow>100000 || duplicateSouls<0 || heroPrice<1 || startingTokens<0 || tokensPerMatch<0)
            throw new IllegalArgumentException("养成数值无效");
        drawPool=Set.copyOf(drawPool);prices=Map.copyOf(prices);
        if(prices.values().stream().anyMatch(v->v<1))throw new IllegalArgumentException("武将价格必须大于0");
    }
    public long price(String hero){return prices.getOrDefault(hero,heroPrice);}
    public List<String> pool(Collection<String> catalog){return catalog.stream().filter(id->drawPool.isEmpty() || drawPool.contains(id)).sorted().toList();}
    public static CollectionPolicy load(Path directory) throws IOException {
        Files.createDirectories(directory);var file=directory.resolve("collection.properties");
        if(!Files.exists(file))Files.writeString(file,TEMPLATE,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
        if(Files.size(file)>65536)throw new IllegalArgumentException("养成配置过大");
        var p=new Properties();try(var r=Files.newBufferedReader(file,StandardCharsets.UTF_8)){p.load(r);}
        var known=Set.of("enabled","guaranteed_new_below","duplicate_souls","market.enabled","market.hero_price","starting_tokens","tokens_per_match","draw_pool");
        var prices=new TreeMap<String,Long>();
        for(String key:p.stringPropertyNames())if(key.startsWith("price.")){
            var hero=key.substring(6);CollectionAccount.checkId(hero);prices.put(hero,number(p,key,100));
        }else if(!known.contains(key))throw new IllegalArgumentException("未知养成配置："+key);
        var pool=new TreeSet<String>();for(String id:p.getProperty("draw_pool","").split(","))if(!id.isBlank()){CollectionAccount.checkId(id.strip());pool.add(id.strip());}
        return new CollectionPolicy(bool(p,"enabled",false),Math.toIntExact(number(p,"guaranteed_new_below",10)),number(p,"duplicate_souls",20),
            number(p,"market.hero_price",100),bool(p,"market.enabled",true),number(p,"starting_tokens",4),number(p,"tokens_per_match",1),pool,prices);
    }
    private static boolean bool(Properties p,String key,boolean fallback){var v=p.getProperty(key,""+fallback).strip();if(!Set.of("true","false").contains(v))throw new IllegalArgumentException(key+" 必须为true/false");return Boolean.parseBoolean(v);}
    private static long number(Properties p,String key,long fallback){try{long n=Long.parseLong(p.getProperty(key,""+fallback).strip());if(n<0 || n>1_000_000_000L)throw new NumberFormatException();return n;}catch(NumberFormatException e){throw new IllegalArgumentException(key+" 必须为0..1000000000整数");}}
}
