package com.persona;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AutonomousPersonaApplication {
    public static void main(String[] args) {
        SpringApplication.run(AutonomousPersonaApplication.class, args);
    }
}
