package com.labguard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class LabGuardApplication {

    public static void main(String[] args) {
        SpringApplication.run(LabGuardApplication.class, args);
    }
}
