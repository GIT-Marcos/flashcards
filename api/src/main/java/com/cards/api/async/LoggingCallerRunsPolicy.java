package com.cards.api.async;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Rejection policy that runs the task in the caller thread (like CallerRunsPolicy)
 * but logs the event for monitoring and debugging.
 *
 * <p>Activated when the pool + queue are saturated.</p>
 *
 * <p>Thread-safe: this class has no mutable state.</p>
 */
public class LoggingCallerRunsPolicy implements RejectedExecutionHandler {

    private static final Logger log = LoggerFactory.getLogger(LoggingCallerRunsPolicy.class);

    @Override
    public void rejectedExecution(Runnable runnable, ThreadPoolExecutor executor) {

        log.warn(
            """
                [POOL SATURATED] Running task in caller thread as fallback.
                  → Caller thread : {}
                  → Active pool   : {} (max: {})
                  → Pending queue : {} (capacity: {})
                  → Total tasks   : {} (completed: {})
                """,
            Thread.currentThread().getName(),
            executor.getActiveCount(),
            executor.getMaximumPoolSize(),
            executor.getQueue().size(),
            executor.getQueue().remainingCapacity() + executor.getQueue().size(),
            executor.getTaskCount(),
            executor.getCompletedTaskCount()
        );

        if (!executor.isShutdown()) {
            runnable.run();
        }
    }
}
