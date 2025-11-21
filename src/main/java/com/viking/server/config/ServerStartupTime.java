package com.viking.server.config;

import java.time.Instant;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
public class ServerStartupTime {
    
    @Bean
    public Instant serverStartTime() {
        return Instant.now();
    }
}

