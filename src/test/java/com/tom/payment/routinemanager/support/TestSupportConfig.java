package com.tom.payment.routinemanager.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Beans every component test shares (one Spring context per run). */
@TestConfiguration(proxyBeanMethods = false)
public class TestSupportConfig {

    /** Replaces the system clock from ClockConfig. */
    @Bean
    @Primary
    MutableClock testClock() {
        return new MutableClock();
    }

    /**
     * Runs @Async work (chat compaction after commit) on the calling thread, so
     * it has finished when the request returns. Safe because
     * ChatCompactionService.compactIfDue uses its own REQUIRES_NEW transaction.
     */
    @Bean(name = "taskExecutor")
    TaskExecutor taskExecutor() {
        return new SyncTaskExecutor();
    }

    /** Production-type database for -PtestDb=postgres. Started once per run. */
    @TestConfiguration(proxyBeanMethods = false)
    @Profile("test-postgres")
    static class Postgres {

        @Bean
        @ServiceConnection
        PostgreSQLContainer postgres() {
            return new PostgreSQLContainer(DockerImageName.parse("postgres:17"));
        }
    }
}
