package dev.sgs.core.engine.skill;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Explicit LIFO settlement stack implementing arbitration rule three (insertion resolution): any
 * task pushed while another task is running executes before the outer task continues. A damage
 * resolution, for instance, pushes its after-damage continuation first and the dying task second,
 * so the dying chain (rescue prompts, possibly death rewards) fully completes before "after damage"
 * skills fire.
 */
public final class ResolutionStack<D> {

  private final Deque<ResolutionTask<D>> tasks = new ArrayDeque<>();
  private int drainDepth;
  private boolean faulted;
  public boolean checkpointIdle(){return drainDepth==0 && tasks.isEmpty() && !faulted;}

  public void push(ResolutionTask<D> task) {
    tasks.push(task);
  }

  public boolean isEmpty() {
    return tasks.isEmpty();
  }

  public int depth() {
    return tasks.size();
  }

  /** Runs every queued task; tasks pushed during execution are drained first (LIFO). */
  public void drain(D driver) {
    drainDepth++;
    try{
    while (!tasks.isEmpty()) {
      tasks.pop().run(driver);
    }
    }catch(RuntimeException|Error error){faulted=true;throw error;}
    finally{drainDepth--;}
  }

  /** Top-to-bottom task descriptions for debugging nested settlement. */
  public List<String> debugView() {
    List<String> view = new ArrayList<>(tasks.size());
    for (ResolutionTask<D> task : tasks) {
      view.add(task.timing() + "#" + task.taskId() + " " + task.snapshot());
    }
    return List.copyOf(view);
  }
}
