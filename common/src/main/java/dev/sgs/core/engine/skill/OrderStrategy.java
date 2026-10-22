package dev.sgs.core.engine.skill;

/**
 * The three arbitration rules of the skill bus.
 *
 * <ul>
 *   <li>{@link #REGISTRATION_PRIORITY}: static priority on the same owner; rule-changing skills
 *       settle before rule-dependent ones. This is the default.
 *   <li>{@link #MULTI_ENTITY_ORDER}: when several seats may react, start at the current turn player
 *       and walk the alive ring. Static priority only orders skills owned by the same seat. Used
 *       for judge modification and rescue offers.
 *   <li>{@link #INSERTION}: events pushed in the middle of a settlement run immediately, before the
 *       outer settlement continues (driven by {@link ResolutionStack}).
 * </ul>
 */
public enum OrderStrategy {
  REGISTRATION_PRIORITY,
  MULTI_ENTITY_ORDER,
  INSERTION
}
