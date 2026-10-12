package com.example.client.config;

import feign.Retryer;
import org.springframework.context.annotation.Bean;

public class FeignConfig {

    @Bean
    public Retryer retryer() {
        return new Retryer.Default(2000, 5000, 3);
    }
}