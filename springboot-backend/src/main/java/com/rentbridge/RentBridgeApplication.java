package com.rentbridge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RentBridgeApplication {
    public static void main(String[] args) {
        SpringApplication.run(RentBridgeApplication.class, args);
    }
}
