package com.usbank.corp.dcr.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Configuration class to enable scheduling for the application
 * This allows the @Scheduled annotations in services to work
 */
@Configuration
@EnableScheduling
public class ScheduledTasksConfig {
    // Configuration class only - no methods required
}