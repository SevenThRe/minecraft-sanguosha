package dev.sgs.core.collection;

import java.nio.file.Path;
import java.sql.*;
import java.util.*;

/** One durable transaction owns token spending, unlock/duplicate conversion, and receipt. */
public final class CollectionDatabase implements AutoCloseable {
    public record Draw(String hero,boolean duplicate,long soulsAward){}
    public record Result(String hero,boolean duplicate,long soulsAward,List<Draw> draws){
        public Result{draws=List.copyOf(draws);}
        public Result(String hero,boolean duplicate,long soulsAward){this(hero,duplicate,soulsAward,List.of());}
    }
    private final Connection db;
    public CollectionDatabase(Path path) throws SQLException {
        db=new org.h2.Driver().connect("jdbc:h2:file:"+path.toAbsolutePath().toString().replace('\\','/')+";WRITE_DELAY=0",new Properties());
        try(var s=db.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS collection_schema (version INT PRIMARY KEY)");
            s.execute("INSERT INTO collection_schema SELECT 1 WHERE NOT EXISTS (SELECT 1 FROM collection_schema)");
            try(var r=s.executeQuery("SELECT version FROM collection_schema")){if(!r.next() || r.getInt(1)!=1 || r.next())throw new SQLException("Unsupported collection schema");}
            s.execute("CREATE TABLE IF NOT EXISTS collection_accounts (player UUID PRIMARY KEY,tokens BIGINT NOT NULL CHECK(tokens>=0),souls BIGINT NOT NULL CHECK(souls>=0),heroes CLOB NOT NULL,skins CLOB NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS collection_receipts (id VARCHAR(160) PRIMARY KEY,player UUID NOT NULL,action VARCHAR(256) NOT NULL,hero VARCHAR(128) NOT NULL,duplicate BOOLEAN NOT NULL,award BIGINT NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS collection_draws (receipt VARCHAR(160) NOT NULL,ordinal INT NOT NULL,hero VARCHAR(128) NOT NULL,duplicate BOOLEAN NOT NULL,award BIGINT NOT NULL,PRIMARY KEY(receipt,ordinal))");
            s.execute("CREATE TABLE IF NOT EXISTS collection_reward_counters (player UUID NOT NULL,period VARCHAR(48) NOT NULL,kind VARCHAR(16) NOT NULL,matches BIGINT NOT NULL,claimed BOOLEAN NOT NULL,PRIMARY KEY(player,period,kind))");
            s.execute("CREATE TABLE IF NOT EXISTS collection_reward_receipts (id VARCHAR(160) PRIMARY KEY,player UUID NOT NULL,facts VARCHAR(512) NOT NULL,tokens BIGINT NOT NULL,souls BIGINT NOT NULL,eligible BOOLEAN NOT NULL)");
        }catch(SQLException e){db.close();throw e;}
    }
    public synchronized CollectionAccount account(UUID player,long startingTokens) throws SQLException {return account(player,startingTokens,0);}
    public synchronized CollectionAccount account(UUID player,long startingTokens,long startingSouls) throws SQLException {
        try(var s=db.prepareStatement("SELECT tokens,souls,heroes,skins FROM collection_accounts WHERE player=?")) {
            s.setObject(1,player);try(var r=s.executeQuery()){if(r.next())return new CollectionAccount(player,r.getLong(1),r.getLong(2),decode(r.getString(3)),decode(r.getString(4)));}
        }
        var fresh=new CollectionAccount(player,startingTokens,startingSouls,Set.of(),Set.of());save(fresh);return fresh;
    }
    private static Set<String> decode(String s){return s.isEmpty()?Set.of():new HashSet<>(Arrays.asList(s.split("\n")));}
    private void save(CollectionAccount a) throws SQLException {
        try(var s=db.prepareStatement("MERGE INTO collection_accounts (player,tokens,souls,heroes,skins) KEY(player) VALUES (?,?,?,?,?)")) {
            s.setObject(1,a.player());s.setLong(2,a.tokens());s.setLong(3,a.souls());
            s.setString(4,String.join("\n",new TreeSet<>(a.heroes())));s.setString(5,String.join("\n",new TreeSet<>(a.skins())));s.executeUpdate();
        }
    }
    public synchronized boolean hasReceipt(String id,UUID player) throws SQLException {
        try(var s=db.prepareStatement("SELECT player FROM collection_receipts WHERE id=?")){s.setString(1,id);try(var r=s.executeQuery()){return r.next() && player.equals(r.getObject(1,UUID.class));}}
    }
    public synchronized Result apply(String receipt,UUID player,String action,String target,long amount,
            CollectionPolicy policy,List<String> catalog,Random random) throws SQLException {
        if(receipt==null || receipt.length()>160 || receipt.isBlank() || player==null)throw new IllegalArgumentException("无效交易凭证");
        String signature=action+":"+target+":"+amount;
        try(var s=db.prepareStatement("SELECT player,action,hero,duplicate,award FROM collection_receipts WHERE id=?")) {
            s.setString(1,receipt);try(var r=s.executeQuery()){if(r.next()){
                if(!player.equals(r.getObject(1,UUID.class)) || !signature.equals(r.getString(2)))throw new IllegalArgumentException("交易凭证冲突");
                return new Result(r.getString(3),r.getBoolean(4),r.getLong(5),draws(receipt));
            }}
        }
        db.setAutoCommit(false);
        try {
            var a=account(player,policy.startingTokens(),policy.startingSouls());var heroes=new TreeSet<>(a.heroes());var skins=new TreeSet<>(a.skins());
            long tokens=a.tokens(),souls=a.souls(),award=0;String hero="";boolean duplicate=false;
            var draws=new ArrayList<Draw>();
            switch(action) {
                case "draw", "draw10" -> {
                    if(!policy.enabled())throw new IllegalArgumentException("服务器未启用独立武将");
                    int count=action.equals("draw10")?10:1;
                    if(tokens<Math.multiplyExact(count,policy.drawTokenCost()))throw new IllegalArgumentException(policy.tokenCurrency().name()+"不足");
                    var pool=policy.pool(catalog);if(pool.isEmpty())throw new IllegalArgumentException("抽取卡池为空");
                    for(int i=0;i<count;i++) {
                        var unseen=pool.stream().filter(id->!heroes.contains(id)).toList();
                        var choices=heroes.size()<policy.guaranteedNewBelow() && !unseen.isEmpty()?unseen:pool;
                        hero=choices.get(random.nextInt(choices.size()));duplicate=!heroes.add(hero);tokens-=policy.drawTokenCost();
                        long converted=duplicate?policy.duplicateSouls():0;
                        award=Math.addExact(award,converted);souls=Math.addExact(souls,converted);
                        draws.add(new Draw(hero,duplicate,converted));
                    }
                }
                case "buy" -> {
                    if(!policy.enabled() || !policy.marketEnabled())throw new IllegalArgumentException("武将市场未开放");
                    if(!policy.pool(catalog).contains(target))throw new IllegalArgumentException("此武将不在市场");
                    if(heroes.contains(target))throw new IllegalArgumentException("已拥有此武将");
                    long price=policy.price(target);if(souls<price)throw new IllegalArgumentException(policy.soulCurrency().name()+"不足");
                    souls-=price;heroes.add(target);hero=target;
                }
                case "hero" -> {if(!catalog.contains(target))throw new IllegalArgumentException("未知武将");heroes.add(target);hero=target;}
                case "skin" -> {CollectionAccount.checkId(target);skins.add(target);}
                case "take_tokens" -> {if(amount<1 || tokens<amount)throw new IllegalArgumentException("招募令余额不足或数量无效");tokens-=amount;}
                case "take_souls" -> {if(amount<1 || souls<amount)throw new IllegalArgumentException("将符余额不足或数量无效");souls-=amount;}
                case "tokens" -> {if(amount<1)throw new IllegalArgumentException("招募令数量必须大于0");tokens=Math.addExact(tokens,amount);}
                case "souls" -> {if(amount<1)throw new IllegalArgumentException("将符数量必须大于0");souls=Math.addExact(souls,amount);}
                default -> throw new IllegalArgumentException("不支持的藏品操作");
            }
            save(new CollectionAccount(player,tokens,souls,heroes,skins));
            try(var s=db.prepareStatement("INSERT INTO collection_receipts VALUES (?,?,?,?,?,?)")){
                s.setString(1,receipt);s.setObject(2,player);s.setString(3,signature);s.setString(4,hero);s.setBoolean(5,duplicate);s.setLong(6,award);s.executeUpdate();
            }
            try(var s=db.prepareStatement("INSERT INTO collection_draws VALUES (?,?,?,?,?)")) {
                for(int i=0;i<draws.size();i++){var d=draws.get(i);s.setString(1,receipt);s.setInt(2,i);s.setString(3,d.hero());s.setBoolean(4,d.duplicate());s.setLong(5,d.soulsAward());s.addBatch();}
                s.executeBatch();
            }
            db.commit();return new Result(hero,duplicate,award,draws);
        }catch(SQLException|RuntimeException e){db.rollback();throw e;}finally{db.setAutoCommit(true);}
    }
    public record Earnings(long tokens,long souls,boolean eligible,boolean replayed){}
    public record Progress(long pvp,long ai,long daily,boolean dailyClaimed,long weekly,boolean weeklyClaimed){}
    private record Counter(long matches,boolean claimed){}
    private Counter counter(UUID player,String period,String kind) throws SQLException {
        try(var s=db.prepareStatement("SELECT matches,claimed FROM collection_reward_counters WHERE player=? AND period=? AND kind=?")){
            s.setObject(1,player);s.setString(2,period);s.setString(3,kind);try(var r=s.executeQuery()){return r.next()?new Counter(r.getLong(1),r.getBoolean(2)):new Counter(0,false);}
        }
    }
    private void counter(UUID player,String period,String kind,Counter value) throws SQLException {
        try(var s=db.prepareStatement("MERGE INTO collection_reward_counters (player,period,kind,matches,claimed) KEY(player,period,kind) VALUES (?,?,?,?,?)")){
            s.setObject(1,player);s.setString(2,period);s.setString(3,kind);s.setLong(4,value.matches());s.setBoolean(5,value.claimed());s.executeUpdate();
        }
    }
    private static String day(long millis,CollectionRewards rules){return java.time.Instant.ofEpochMilli(millis).atZone(rules.zone()).toLocalDate().toString();}
    private static String week(String day){return java.time.LocalDate.parse(day).with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)).toString();}
    public synchronized Progress progress(UUID player,long millis,CollectionRewards rules) throws SQLException {
        String day=day(millis,rules);var daily=counter(player,"D:"+day,"task");var weekly=counter(player,"W:"+week(day),"task");
        return new Progress(counter(player,"D:"+day,"pvp").matches(),counter(player,"D:"+day,"ai").matches(),daily.matches(),daily.claimed(),weekly.matches(),weekly.claimed());
    }
    /** Wallet, daily limits, task milestones and receipt commit together, including zero awards.
     * Match IDs are the ledger's persisted epoch-millisecond start allocator. */
    public synchronized Earnings settle(dev.sgs.core.social.MatchLedger.Receipt receipt,dev.sgs.core.social.MatchLedger.MatchRecord match,CollectionPolicy policy) throws SQLException {
        if(match==null || receipt==null || match.id()!=receipt.matchId() || !match.humans().contains(receipt.player())
            || match.outcome()==dev.sgs.core.engine.GameOutcome.ONGOING || match.completedAt()<=0
            || !receipt.receiptId().equals(match.id()+":"+receipt.player()))throw new IllegalArgumentException("无效牌局结算");
        String id="match:"+receipt.receiptId();UUID player=receipt.player();
        String facts=match.id()+":"+match.completedAt()+":"+match.capacity()+":"+match.humans().size()+":"+match.outcome()+":"+receipt.won();
        try(var s=db.prepareStatement("SELECT player,facts,tokens,souls,eligible FROM collection_reward_receipts WHERE id=?")){
            s.setString(1,id);try(var r=s.executeQuery()){if(r.next()){
                if(!player.equals(r.getObject(1,UUID.class)) || !facts.equals(r.getString(2)))throw new IllegalArgumentException("奖励凭证冲突");
                return new Earnings(r.getLong(3),r.getLong(4),r.getBoolean(5),true);
            }}
        }
        // Old releases already paid this receipt through the generic wallet transaction.
        if(hasReceipt(id,player))return new Earnings(0,0,false,true);
        var rules=policy.rewards();boolean ai=match.humans().size()<match.capacity();var source=ai?rules.ai():rules.pvp();
        String day=day(match.completedAt(),rules),period="D:"+day,kind=ai?"ai":"pvp";
        db.setAutoCommit(false);
        try{
            var count=counter(player,period,kind);
            boolean eligible=policy.enabled() && rules.enabled() && source.enabled() && count.matches()<source.dailyLimit()
                && match.completedAt()>=match.id() && match.completedAt()-match.id()>=rules.minimumSeconds()*1000
                && match.outcome()!=dev.sgs.core.engine.GameOutcome.DRAW;
            long tokens=0,souls=0;
            if(eligible){
                tokens=Math.addExact(source.tokens(),receipt.won()?source.winTokens():0);
                souls=Math.addExact(source.souls(),receipt.won()?source.winSouls():0);
                counter(player,period,kind,new Counter(count.matches()+1,false));
                if(!ai || rules.aiTasks()){
                    for(boolean daily:List.of(true,false)){
                        var task=daily?rules.daily():rules.weekly();if(!task.enabled())continue;
                        String key=daily?period:"W:"+week(day);var previous=counter(player,key,"task");
                        long matches=Math.addExact(previous.matches(),1);boolean pay=!previous.claimed() && matches>=task.matches();
                        if(pay){tokens=Math.addExact(tokens,task.tokens());souls=Math.addExact(souls,task.souls());}
                        counter(player,key,"task",new Counter(matches,previous.claimed()||pay));
                    }
                }
            }
            var account=account(player,policy.startingTokens(),policy.startingSouls());
            save(new CollectionAccount(player,Math.addExact(account.tokens(),tokens),Math.addExact(account.souls(),souls),account.heroes(),account.skins()));
            try(var s=db.prepareStatement("INSERT INTO collection_reward_receipts VALUES (?,?,?,?,?,?)")){
                s.setString(1,id);s.setObject(2,player);s.setString(3,facts);s.setLong(4,tokens);s.setLong(5,souls);s.setBoolean(6,eligible);s.executeUpdate();
            }
            try(var s=db.prepareStatement("INSERT INTO collection_receipts VALUES (?,?,?,?,?,?)")){
                s.setString(1,id);s.setObject(2,player);s.setString(3,"settlement");s.setString(4,"");s.setBoolean(5,false);s.setLong(6,souls);s.executeUpdate();
            }
            db.commit();return new Earnings(tokens,souls,eligible,false);
        }catch(SQLException|RuntimeException e){db.rollback();throw e;}finally{db.setAutoCommit(true);}
    }
    private List<Draw> draws(String receipt) throws SQLException {
        var result=new ArrayList<Draw>();try(var s=db.prepareStatement("SELECT hero,duplicate,award FROM collection_draws WHERE receipt=? ORDER BY ordinal")){
            s.setString(1,receipt);try(var r=s.executeQuery()){while(r.next())result.add(new Draw(r.getString(1),r.getBoolean(2),r.getLong(3)));}
        }return List.copyOf(result);
    }
    @Override public synchronized void close() throws SQLException {db.close();}
}
