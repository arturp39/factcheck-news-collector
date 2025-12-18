package com.factcheck.collector.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
public class IngestionExecutorConfig {

    @Bean
    public Executor ingestionExecutor() {
        return Executors.newFixedThreadPool(4);
    }
}
