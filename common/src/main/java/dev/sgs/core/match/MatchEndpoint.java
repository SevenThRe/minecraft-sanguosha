package dev.sgs.core.match;

import dev.sgs.core.match.dto.MatchViews.MatchSnapshot;

/**
 * Per-player push channel. The integration layer implements this over the Minecraft network
 * handler; send calls only enqueue netty messages and are safe from the match worker thread.
 */
public interface MatchEndpoint {

  /** Pushes a snapshot built specifically for this receiver. */
  void sendSnapshot(MatchSnapshot snapshot);

  /** Reports a rejected action back to the sender; {@code reason} is a RejectReason constant. */
  void sendError(int nonce, String reason);

  /** Endpoint for bot seats that never receive data. */
  MatchEndpoint NOOP =
      new MatchEndpoint() {
        @Override
        public void sendSnapshot(MatchSnapshot snapshot) {}

        @Override
        public void sendError(int nonce, String reason) {}
      };
}
