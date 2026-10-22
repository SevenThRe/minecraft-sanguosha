package dev.sgs.core.addon;
import dev.sgs.core.engine.skill.SkillEngine;
import dev.sgs.core.player.SgsPlayer;
@FunctionalInterface public interface SkillFactory {SkillBinding create(SgsPlayer owner,SkillEngine engine);}
