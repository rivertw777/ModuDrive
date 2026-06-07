package com.moduDrive.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import reactivefeign.spring.config.EnableReactiveFeignClients;

@EnableReactiveFeignClients
@SpringBootApplication(scanBasePackages = {
        "com.moduDrive.gateway",
        "com.moduDrive.common.core",
        "com.moduDrive.common.infrastructure.resilience4j"
})
public class GatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}

