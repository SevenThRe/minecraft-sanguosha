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
        }catch(SQLException e){db.close();throw e;}
    }
    public synchronized CollectionAccount account(UUID player,long startingTokens) throws SQLException {
        try(var s=db.prepareStatement("SELECT tokens,souls,heroes,skins FROM collection_accounts WHERE player=?")) {
            s.setObject(1,player);try(var r=s.executeQuery()){if(r.next())return new CollectionAccount(player,r.getLong(1),r.getLong(2),decode(r.getString(3)),decode(r.getString(4)));}
        }
        var fresh=new CollectionAccount(player,startingTokens,0,Set.of(),Set.of());save(fresh);return fresh;
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
            var a=account(player,policy.startingTokens());var heroes=new TreeSet<>(a.heroes());var skins=new TreeSet<>(a.skins());
            long tokens=a.tokens(),souls=a.souls(),award=0;String hero="";boolean duplicate=false;
            var draws=new ArrayList<Draw>();
            switch(action) {
                case "draw", "draw10" -> {
                    if(!policy.enabled())throw new IllegalArgumentException("服务器未启用独立武将");
                    int count=action.equals("draw10")?10:1;
                    if(tokens<count)throw new IllegalArgumentException("招募令不足");
                    var pool=policy.pool(catalog);if(pool.isEmpty())throw new IllegalArgumentException("抽取卡池为空");
                    for(int i=0;i<count;i++) {
                        var unseen=pool.stream().filter(id->!heroes.contains(id)).toList();
                        var choices=heroes.size()<policy.guaranteedNewBelow() && !unseen.isEmpty()?unseen:pool;
                        hero=choices.get(random.nextInt(choices.size()));duplicate=!heroes.add(hero);tokens--;
                        long converted=duplicate?policy.duplicateSouls():0;
                        award=Math.addExact(award,converted);souls=Math.addExact(souls,converted);
                        draws.add(new Draw(hero,duplicate,converted));
                    }
                }
                case "buy" -> {
                    if(!policy.enabled() || !policy.marketEnabled())throw new IllegalArgumentException("武将市场未开放");
                    if(!policy.pool(catalog).contains(target))throw new IllegalArgumentException("此武将不在市场");
                    if(heroes.contains(target))throw new IllegalArgumentException("已拥有此武将");
                    long price=policy.price(target);if(souls<price)throw new IllegalArgumentException("将符不足");
                    souls-=price;heroes.add(target);hero=target;
                }
                case "hero" -> {if(!catalog.contains(target))throw new IllegalArgumentException("未知武将");heroes.add(target);hero=target;}
                case "skin" -> {CollectionAccount.checkId(target);skins.add(target);}
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
    private List<Draw> draws(String receipt) throws SQLException {
        var result=new ArrayList<Draw>();try(var s=db.prepareStatement("SELECT hero,duplicate,award FROM collection_draws WHERE receipt=? ORDER BY ordinal")){
            s.setString(1,receipt);try(var r=s.executeQuery()){while(r.next())result.add(new Draw(r.getString(1),r.getBoolean(2),r.getLong(3)));}
        }return List.copyOf(result);
    }
    @Override public synchronized void close() throws SQLException {db.close();}
}
