package dev.sgs.core.engine.skill;

import java.io.Serializable;
import java.util.Map;

/**
 * One unit of nested settlement pushed onto the {@link ResolutionStack} (for example: damage
 * resolution inserts a dying-resolution task before its own after-damage continuation).
 *
 * <p>Tasks expose continuations, but the current driver can still drain synchronously from inside a
 * task. This is not yet a fully resumable scheduler. Concrete tasks use seat numbers rather than
 * platform player references; {@link #snapshot()} is debug data, not a complete save/reload codec.
 *
 * @param <D> the driver that executes the task (the game engine in production)
 */
public abstract class ResolutionTask<D> implements Serializable {

  private static final long TASK_ID_BASE = 7_000_000_000L;
  private static final java.util.concurrent.atomic.AtomicLong IDS =
      new java.util.concurrent.atomic.AtomicLong();

  private final long taskId;
  private final Timing timing;

  protected ResolutionTask(Timing timing) {
    this.taskId = TASK_ID_BASE + IDS.incrementAndGet();
    this.timing = timing;
  }

  public long taskId() {
    return taskId;
  }

  public Timing timing() {
    return timing;
  }

  /** Executes the task; may push further tasks onto the driver, which run before continuations. */
  public abstract void run(D driver);

  /** Primitive-only context snapshot for structured logging / future serialization. */
  public abstract Map<String, Object> snapshot();
}
