package dev.sgs.core.card;

/**
 * The color of a settlement card. Physical cards are always {@link #RED} or {@link #BLACK}; {@link
 * #NONE} (colorless) only exists on {@link VirtualCard}s without sources or with mixed-color
 * sources.
 */
public enum Color {
  RED,
  BLACK,
  NONE;
}
