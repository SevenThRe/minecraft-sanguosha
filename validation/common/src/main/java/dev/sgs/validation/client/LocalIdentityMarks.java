package dev.sgs.validation.client;

import java.util.HashMap;
import java.util.Map;

/** Private deductions for one viewer in one match. Never part of a network message. */
final class LocalIdentityMarks {
    private final Map<Integer, String> marks = new HashMap<>();
    private long matchId = Long.MIN_VALUE;
    private int viewerSeat = -1;

    void bind(long matchId, int viewerSeat) {
        if (this.matchId != matchId || this.viewerSeat != viewerSeat) {
            clear();
            this.matchId = matchId;
            this.viewerSeat = viewerSeat;
        }
    }

    String label(int seat) { return marks.getOrDefault(seat, "?"); }

    void select(int seat, String label) {
        if (!label.equals("忠") && !label.equals("内") && !label.equals("反"))
            throw new IllegalArgumentException("Invalid identity mark: " + label);
        marks.put(seat, label);
    }

    void reset(int seat) { marks.remove(seat); }

    void clear() {
        marks.clear();
        matchId = Long.MIN_VALUE;
        viewerSeat = -1;
    }
}
