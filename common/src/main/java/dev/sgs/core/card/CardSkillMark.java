package dev.sgs.core.card;
/** Skill-authored arbitrary card ribbon. Rules decide when to add/remove a mark. */
public record CardSkillMark(String id,String text,String description,int color,Visibility visibility) implements java.io.Serializable {
    public enum Visibility { OWNER, HAND_VIEWERS, PUBLIC }
    public CardSkillMark {
        if(id==null || id.isBlank() || id.length()>96 || text==null || text.isBlank() || text.length()>48
            || description==null || description.length()>512 || visibility==null)throw new IllegalArgumentException("Invalid card skill mark");
    }
}
