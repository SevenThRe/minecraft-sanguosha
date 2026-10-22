package dev.sgs.core.social;

import java.nio.file.Path;
import java.sql.*;
import java.util.*;

/** Embedded SQL lifetime ledger. No round reset, client balances or automatic item grants. */
public final class GiftDatabase implements AutoCloseable {
    public enum Kind { POPPY, TULIP, EGG }
    public record Totals(UUID player,long flowers,long eggs) {}
    public record Wallet(UUID player,long flowers) {}
    public record Receipt(String id,UUID sender,UUID target,String kind,int amount,int cost,boolean positive,long flowers,long eggs) {}
    private final Connection db;
    public GiftDatabase(Path path) throws SQLException {
        db=new org.h2.Driver().connect("jdbc:h2:file:"+path.toAbsolutePath().toString().replace('\\','/')+";WRITE_DELAY=0",new Properties());
        try(var s=db.createStatement()){
            s.execute("CREATE TABLE IF NOT EXISTS gift_schema (version INT PRIMARY KEY)");
            s.execute("INSERT INTO gift_schema SELECT 3 WHERE NOT EXISTS (SELECT 1 FROM gift_schema)");
            int schema;try(var r=s.executeQuery("SELECT version FROM gift_schema")){if(!r.next())throw new SQLException("Missing gift schema");schema=r.getInt(1);if((schema!=2 && schema!=3) || r.next())throw new SQLException("Unsupported gift schema");}
            s.execute("CREATE TABLE IF NOT EXISTS gift_totals (player UUID PRIMARY KEY, flowers BIGINT NOT NULL CHECK(flowers>=0), eggs BIGINT NOT NULL CHECK(eggs>=0))");
            s.execute("CREATE TABLE IF NOT EXISTS gift_wallets (player UUID PRIMARY KEY, flowers BIGINT NOT NULL CHECK(flowers>=0))");
            s.execute("CREATE TABLE IF NOT EXISTS gift_grants (id VARCHAR(100) PRIMARY KEY, player UUID NOT NULL, flowers BIGINT NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS gift_receipts (id VARCHAR(100) PRIMARY KEY, sender UUID NOT NULL, target UUID NOT NULL, kind VARCHAR(96) NOT NULL, amount INT NOT NULL, flowers BIGINT NOT NULL, eggs BIGINT NOT NULL, cost INT NOT NULL, positive BOOLEAN NOT NULL)");
            if(schema==2){
                s.execute("ALTER TABLE gift_receipts ALTER COLUMN kind VARCHAR(96)");
                s.execute("ALTER TABLE gift_receipts ADD COLUMN IF NOT EXISTS cost INT DEFAULT -1 NOT NULL");
                s.execute("ALTER TABLE gift_receipts ADD COLUMN IF NOT EXISTS positive BOOLEAN DEFAULT TRUE NOT NULL");
                s.execute("UPDATE gift_receipts SET cost=amount WHERE cost=-1");
                s.execute("UPDATE gift_receipts SET positive=FALSE WHERE kind='EGG'");
                s.execute("UPDATE gift_receipts SET kind=CASE WHEN kind='POPPY' THEN 'sgs:poppy' WHEN kind='TULIP' THEN 'sgs:tulip_bouquet' WHEN kind='EGG' AND amount=10 THEN 'sgs:egg_volley' WHEN kind='EGG' THEN 'sgs:egg' ELSE kind END");
                s.execute("UPDATE gift_schema SET version=3");
            }
        } catch(SQLException failure){db.close();throw failure;}
    }
    public synchronized Totals totals(UUID player) throws SQLException {
        try(var s=db.prepareStatement("SELECT flowers,eggs FROM gift_totals WHERE player=?")){
            s.setObject(1,player);try(var r=s.executeQuery()){return r.next()?new Totals(player,r.getLong(1),r.getLong(2)):new Totals(player,0,0);}
        }
    }
    public synchronized Receipt receipt(String id) throws SQLException {
        try(var s=db.prepareStatement("SELECT sender,target,kind,amount,cost,positive,flowers,eggs FROM gift_receipts WHERE id=?")){
            s.setString(1,id);try(var r=s.executeQuery()){return r.next()?new Receipt(id,r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getString(3),r.getInt(4),r.getInt(5),r.getBoolean(6),r.getLong(7),r.getLong(8)):null;}
        }
    }
    public synchronized Wallet wallet(UUID player) throws SQLException {
        try(var s=db.prepareStatement("SELECT flowers FROM gift_wallets WHERE player=?")){
            s.setObject(1,player);try(var r=s.executeQuery()){return r.next()?new Wallet(player,r.getLong(1)):new Wallet(player,0);}
        }
    }
    private void saveWallet(UUID player,long flowers) throws SQLException {
        try(var s=db.prepareStatement("MERGE INTO gift_wallets (player,flowers) KEY(player) VALUES (?,?)")){
            s.setObject(1,player);s.setLong(2,flowers);s.executeUpdate();
        }
    }
    /** Trusted server grants only. Reusing a grant key never mints currency twice. */
    public synchronized Wallet grant(String id,UUID player,long flowers) throws SQLException {
        if(id==null || id.isBlank() || id.length()>100 || player==null || flowers<=0)throw new IllegalArgumentException("Invalid grant");
        try(var s=db.prepareStatement("SELECT player,flowers FROM gift_grants WHERE id=?")){
            s.setString(1,id);try(var r=s.executeQuery()){if(r.next()){
                if(!r.getObject(1,UUID.class).equals(player)||r.getLong(2)!=flowers)throw new IllegalArgumentException("Grant conflict");
                return wallet(player);
            }}
        }
        db.setAutoCommit(false);
        try{
            var old=wallet(player);saveWallet(player,Math.addExact(old.flowers(),flowers));
            try(var s=db.prepareStatement("INSERT INTO gift_grants VALUES (?,?,?)")){s.setString(1,id);s.setObject(2,player);s.setLong(3,flowers);s.executeUpdate();}
            db.commit();return wallet(player);
        }catch(SQLException|RuntimeException failure){db.rollback();throw failure;}finally{db.setAutoCommit(true);}
    }
    /** Virtual wallet debit + target lifetime counters + receipt commit in one SQL transaction. */
    public synchronized Receipt record(String id,UUID sender,UUID target,Kind kind,int amount) throws SQLException {
        if(kind==null)throw new IllegalArgumentException("Invalid gift");
        return recordGift(id,sender,target,new GiftCatalog(List.of()).resolve(kind.name(),amount));
    }
    public synchronized Receipt recordGift(String id,UUID sender,UUID target,GiftDefinition gift) throws SQLException {
        if(gift==null)throw new IllegalArgumentException("Invalid gift");
        int amount=gift.received();
        if(id==null || id.isBlank() || id.length()>100 || sender==null || target==null || sender.equals(target) || gift==null)throw new IllegalArgumentException("Invalid gift");
        var prior=receipt(id);
        if(prior!=null){
            if(!prior.sender().equals(sender)||!prior.target().equals(target)||!prior.kind().equals(gift.id())||prior.amount()!=amount||prior.cost()!=gift.cost()||prior.positive()!=gift.positive())throw new IllegalArgumentException("Receipt conflict");
            return prior;
        }
        db.setAutoCommit(false);
        try{
            var balance=wallet(sender);
            if(balance.flowers()<gift.cost())throw new IllegalArgumentException("INSUFFICIENT_BALANCE");
            saveWallet(sender,balance.flowers()-gift.cost());
            var old=totals(target);
            long flowers=Math.addExact(old.flowers(),gift.positive()?amount:0),eggs=Math.addExact(old.eggs(),gift.positive()?0:amount);
            try(var s=db.prepareStatement("MERGE INTO gift_totals (player,flowers,eggs) KEY(player) VALUES (?,?,?)")){
                s.setObject(1,target);s.setLong(2,flowers);s.setLong(3,eggs);s.executeUpdate();
            }
            try(var s=db.prepareStatement("INSERT INTO gift_receipts (id,sender,target,kind,amount,flowers,eggs,cost,positive) VALUES (?,?,?,?,?,?,?,?,?)")){
                s.setString(1,id);s.setObject(2,sender);s.setObject(3,target);s.setString(4,gift.id());s.setInt(5,amount);s.setLong(6,flowers);s.setLong(7,eggs);s.setInt(8,gift.cost());s.setBoolean(9,gift.positive());s.executeUpdate();
            }
            db.commit();return new Receipt(id,sender,target,gift.id(),amount,gift.cost(),gift.positive(),flowers,eggs);
        }catch(SQLException|RuntimeException failure){db.rollback();throw failure;}
        finally{db.setAutoCommit(true);}
    }
    @Override public synchronized void close() throws SQLException {db.close();}
}
