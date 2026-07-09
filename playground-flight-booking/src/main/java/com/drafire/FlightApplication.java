package com.drafire;

import org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = PgVectorStoreAutoConfiguration.class)
@EnableScheduling
public class FlightApplication {
    public static void main(String[] args) {
        SpringApplication.run(FlightApplication.class, args);
    }
}