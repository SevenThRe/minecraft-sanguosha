package dev.sgs.validation.client.layout;

import dev.sgs.core.engine.PromptType;
import java.util.*;

/** Select an existing physical card only when the entire server offer is visibly owned. */
public record OwnedCardChoice(List<Long> cards, List<Integer> targets) {
    public OwnedCardChoice { cards=List.copyOf(cards);targets=List.copyOf(targets); }
    public static OwnedCardChoice from(PromptType type,List<Long> offered,List<Long> owned,
                                       boolean hidden,List<Integer> targets) {
        if(!EnumSet.of(PromptType.GUI_CAI_REPLACE,PromptType.GANGLIE_DISCARD,
            PromptType.WEAPON_DISCARD,PromptType.LIULI_TARGET,PromptType.YIJI_GIVE).contains(type)
            || hidden || offered.isEmpty() || offered.stream().anyMatch(id->id<=0)
            || new HashSet<>(offered).size()!=offered.size() || !owned.containsAll(offered))return null;
        return new OwnedCardChoice(offered,targets);
    }
    public boolean allowsCard(long id){return cards.contains(id);}
    public boolean allowsTarget(int seat){return targets.contains(seat);}
}
