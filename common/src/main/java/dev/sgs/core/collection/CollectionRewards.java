package dev.sgs.core.collection;

import java.time.ZoneId;
import java.util.*;

/** Server-owned earning rules; counters use the configured calendar, never client time. */
public record CollectionRewards(boolean enabled,ZoneId zone,long minimumSeconds,MatchReward pvp,MatchReward ai,
        Task daily,Task weekly,boolean aiTasks) {
    public record MatchReward(boolean enabled,long tokens,long souls,long winTokens,long winSouls,int dailyLimit){
        public MatchReward{check(tokens);check(souls);check(winTokens);check(winSouls);check(dailyLimit);}
    }
    public record Task(boolean enabled,int matches,long tokens,long souls){
        public Task{if(matches<1 || matches>100000)throw new IllegalArgumentException("任务局数必须为1..100000");check(tokens);check(souls);}
    }
    public CollectionRewards{Objects.requireNonNull(zone);Objects.requireNonNull(pvp);Objects.requireNonNull(ai);Objects.requireNonNull(daily);Objects.requireNonNull(weekly);check(minimumSeconds);}
    private static void check(long value){if(value<0 || value>1_000_000_000L)throw new IllegalArgumentException("奖励数值必须为0..1000000000");}
    public static final String TEMPLATE="""
        # 完局与任务奖励；所有数值支持 /sgs reload。0次上限表示该来源不发放。
        rewards.enabled=true
        rewards.timezone=Asia/Shanghai
        rewards.minimum_seconds=60
        rewards.pvp.enabled=true
        # 真人满员局；基础招募令沿用 tokens_per_match。
        rewards.pvp.souls=5
        rewards.pvp.win_tokens=0
        rewards.pvp.win_souls=2
        rewards.pvp.daily_limit=5
        # 含AI的对局单独计额，允许小服单人游玩。
        rewards.ai.enabled=true
        rewards.ai.tokens=1
        rewards.ai.souls=3
        rewards.ai.win_tokens=0
        rewards.ai.win_souls=1
        rewards.ai.daily_limit=3
        # 任务仅统计上述上限内的有效完局，达标自动到账，无需手动领取。
        tasks.ai_counts=true
        tasks.daily.enabled=true
        tasks.daily.matches=3
        tasks.daily.tokens=2
        tasks.daily.souls=10
        tasks.weekly.enabled=true
        tasks.weekly.matches=10
        tasks.weekly.tokens=5
        tasks.weekly.souls=30
        """;
    public static Set<String> keys(){var p=new Properties();try{p.load(new java.io.StringReader(TEMPLATE));}catch(java.io.IOException impossible){throw new AssertionError(impossible);}return p.stringPropertyNames();}
    public static CollectionRewards load(Properties p,long tokensPerMatch){
        try{return new CollectionRewards(bool(p,"rewards.enabled",true),ZoneId.of(p.getProperty("rewards.timezone","Asia/Shanghai").strip()),num(p,"rewards.minimum_seconds",60),
            new MatchReward(bool(p,"rewards.pvp.enabled",true),tokensPerMatch,num(p,"rewards.pvp.souls",5),num(p,"rewards.pvp.win_tokens",0),num(p,"rewards.pvp.win_souls",2),(int)num(p,"rewards.pvp.daily_limit",5)),
            new MatchReward(bool(p,"rewards.ai.enabled",true),num(p,"rewards.ai.tokens",1),num(p,"rewards.ai.souls",3),num(p,"rewards.ai.win_tokens",0),num(p,"rewards.ai.win_souls",1),(int)num(p,"rewards.ai.daily_limit",3)),
            task(p,"daily",3,2,10),task(p,"weekly",10,5,30),bool(p,"tasks.ai_counts",true));
        }catch(java.time.DateTimeException e){throw new IllegalArgumentException("奖励时区无效",e);}
    }
    private static Task task(Properties p,String key,int matches,long tokens,long souls){String k="tasks."+key+".";return new Task(bool(p,k+"enabled",true),(int)num(p,k+"matches",matches),num(p,k+"tokens",tokens),num(p,k+"souls",souls));}
    private static boolean bool(Properties p,String key,boolean fallback){String s=p.getProperty(key,""+fallback).strip();if(!Set.of("true","false").contains(s))throw new IllegalArgumentException(key+" 必须为true/false");return Boolean.parseBoolean(s);}
    private static long num(Properties p,String key,long fallback){try{long n=Long.parseLong(p.getProperty(key,""+fallback).strip());check(n);return n;}catch(NumberFormatException e){throw new IllegalArgumentException(key+" 必须为非负整数",e);}}
}
