package com.cards.api.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.util.ErrorHandler;

@Configuration
@EnableScheduling
public class SchedulingConfig {

    private static final Logger log = LoggerFactory.getLogger(SchedulingConfig.class);

    private final Counter failedTasksCounter;

    public SchedulingConfig(MeterRegistry meterRegistry) {
        this.failedTasksCounter = Counter.builder("flashcards.scheduled.tasks.failed.total")
            .description("Total failed scheduled tasks")
            .register(meterRegistry);
    }

    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("CronScheduler-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(60);
        scheduler.setErrorHandler(new SchedulerErrorHandler(failedTasksCounter));
        return scheduler;
    }

    private static class SchedulerErrorHandler implements ErrorHandler {

        private final Counter failedTasksCounter;

        SchedulerErrorHandler(Counter failedTasksCounter) {
            this.failedTasksCounter = failedTasksCounter;
        }

        @Override
        public void handleError(@NonNull Throwable t) {
            failedTasksCounter.increment();
            log.error("Fatal error in scheduled task. Cron will continue running. Error: {}",
                t.getMessage(), t);
        }
    }
}
