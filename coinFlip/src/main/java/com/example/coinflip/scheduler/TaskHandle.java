package com.example.coinflip.scheduler;

/**
 * A handle representing a scheduled task that can be cancelled.
 */
public interface TaskHandle {
    /**
     * Cancels the scheduled task.
     */
    void cancel();
}
