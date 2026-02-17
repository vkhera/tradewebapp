package com.example.stockbrokerage;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Stock Brokerage Application - Low latency trading platform
 */
@SpringBootApplication
@EnableCaching
@EnableAsync
@EnableScheduling
public class StockBrokerageApplication {
    public static void main(String[] args) {
        SpringApplication.run(StockBrokerageApplication.class, args);
    }
}
