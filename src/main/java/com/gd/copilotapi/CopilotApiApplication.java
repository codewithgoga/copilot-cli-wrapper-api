package com.gd.copilotapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = "com.gd.copilotapi")
@ConfigurationPropertiesScan
public class CopilotApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(CopilotApiApplication.class, args);
    }
}
