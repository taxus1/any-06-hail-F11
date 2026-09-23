package com.somepro;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class SomeProApplication {

    public static void main(String[] args) {
        SpringApplication.run(SomeProApplication.class, args);
    }
}
