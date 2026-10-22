package dev.sgs.core.collection;

import java.util.*;

public record CollectionAccount(UUID player,long tokens,long souls,Set<String> heroes,Set<String> skins) {
    public CollectionAccount {
        Objects.requireNonNull(player);if(tokens<0 || souls<0)throw new IllegalArgumentException("余额不能为负");
        heroes=Set.copyOf(heroes);skins=Set.copyOf(skins);
        if(heroes.size()>10000 || skins.size()>20000)throw new IllegalArgumentException("藏品数量超限");
        heroes.forEach(CollectionAccount::checkId);skins.forEach(CollectionAccount::checkId);
    }
    public static void checkId(String id){if(id==null || !id.matches("[a-z0-9_.:/-]{1,128}"))throw new IllegalArgumentException("无效藏品ID");}
}
