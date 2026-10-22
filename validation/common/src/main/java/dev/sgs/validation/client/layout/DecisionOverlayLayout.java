package dev.sgs.validation.client.layout;

/** Pure geometry for the decision strip above lifted cards and skills inside the portrait footer. */
public final class DecisionOverlayLayout {
    private DecisionOverlayLayout() {}

    /** Half-open bounds, matching Minecraft's draw and pointer coordinate convention. */
    public record Rect(int x, int y, int width, int height) {
        public Rect {
            if (width < 1 || height < 1) throw new IllegalArgumentException("Empty rectangle");
        }
        public int right() { return x + width; }
        public int bottom() { return y + height; }
        public boolean contains(double px, double py) {
            return px >= x && px < right() && py >= y && py < bottom();
        }
        public boolean intersects(Rect other) {
            return x < other.right() && right() > other.x && y < other.bottom() && bottom() > other.y;
        }
    }

    public record Layout(Rect caption, Rect timer, Rect confirm, Rect cancel, Rect skillRow) {
        /** Fixed two-column cells: skill count never stretches a button. Extra rows grow upward. */
        public Rect skill(int index, int count) {
            if (count < 1 || index < 0 || index >= count)
                throw new IllegalArgumentException("Skill cell index/count");
            int gap = skillRow.width() >= 4 ? 2 : 0;
            int cellWidth = Math.max(1, (skillRow.width() - gap) / 2);
            int rows = (count + 1) / 2;
            return new Rect(skillRow.x() + (index % 2) * (cellWidth + gap),
                    skillRow.y() - (rows - 1 - index / 2) * (skillRow.height() + gap),
                    cellWidth, skillRow.height());
        }
    }

    /**
     * handTop must be the highest selected-card edge, including the lift, rather than the baseline.
     * self bounds describe the visible portrait, excluding any external equipment or name tooltip.
     * Choices share one row; the timer spans the hand lane and has room for a 9px caption above it.
     */
    public static Layout fit(int width, int height, int handLeft, int handRight, int handTop,
                             int selfX, int selfY, int selfWidth, int selfHeight) {
        if (width < 64 || height < 64 || selfWidth < 8 || selfHeight < 12)
            throw new IllegalArgumentException("Screen/portrait too small for decision controls");
        int margin = 4;
        int left = Math.clamp(handLeft, margin, width - margin - 4);
        int right = Math.clamp(handRight, left + 4, width - margin);
        int portraitLeft = Math.clamp(selfX, margin, width - margin - 8);
        int portraitRight = Math.clamp(selfX + selfWidth, portraitLeft + 8, width - margin);
        int portraitTop = Math.clamp(selfY, margin, height - margin - 12);
        int portraitBottom = Math.clamp(selfY + selfHeight, portraitTop + 12, height - margin);

        int bottom = Math.min(height - margin, handTop - 5);
        // In unusual narrow layouts the hand lane can reach under the portrait; stay above it.
        if (left < portraitRight && right > portraitLeft) bottom = Math.min(bottom, portraitTop - 4);
        if (bottom < 34) throw new IllegalArgumentException("No vertical decision lane above hand/portrait");
        int buttonHeight = Math.min(20, Math.max(12, height / 22));
        int captionHeight = 11;
        int timerHeight = 3;
        int gap = 3;
        int total = captionHeight + gap + timerHeight + gap + buttonHeight;
        if (bottom - total < margin) buttonHeight -= margin - (bottom - total);
        int buttonTop = bottom - buttonHeight;
        Rect timer = new Rect(left, buttonTop - gap - timerHeight, right - left, timerHeight);
        Rect caption = new Rect(left, timer.y() - gap - captionHeight, right - left, captionHeight);

        int choiceGap = Math.min(8, Math.max(2, (right - left) / 20));
        int choiceWidth = Math.min(116, Math.max(1, (right - left - choiceGap) / 2));
        int choiceLeft = left + (right - left - choiceWidth * 2 - choiceGap) / 2;
        Rect confirm = new Rect(choiceLeft, buttonTop, choiceWidth, buttonHeight);
        Rect cancel = new Rect(confirm.right() + choiceGap, buttonTop, choiceWidth, buttonHeight);

        int skillHeight = Math.min(19, Math.max(10, (portraitBottom - portraitTop) / 5));
        Rect skillRow = new Rect(portraitLeft + 2, portraitBottom - 2 - skillHeight,
                portraitRight - portraitLeft - 4, skillHeight);
        return new Layout(caption, timer, confirm, cancel, skillRow);
    }
}
