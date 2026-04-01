package com.outreach.datasyncdriver;

import com.outreach.datasyncdriver.connector.ExternalSystemConnector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;

import java.util.List;

@SpringBootApplication
@EnableCaching
@Slf4j
public class DataSyncDriverApplication {

    public static void main(String[] args) {
        SpringApplication.run(DataSyncDriverApplication.class, args);
    }

    @Bean
    CommandLineRunner validateConnector(List<ExternalSystemConnector> connectors) {
        return args -> {
            if (connectors.isEmpty()) {
                log.warn("No active connector found. Set 'driver.connector.type' in application.properties.");
            } else if (connectors.size() > 1) {
                log.warn("Multiple connectors active: {}. Each driver instance should have exactly one connector.",
                        connectors.stream().map(ExternalSystemConnector::name).toList());
            } else {
                log.info("Active connector: [{}] (target system: {})",
                        connectors.get(0).name(), connectors.get(0).targetSystemName());
            }
        };
    }
}

