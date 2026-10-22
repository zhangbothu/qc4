package com.mapsearch.qc.service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(QcProperties.class)
public class QcApplication {

    public static void main(String[] args) {
        SpringApplication.run(QcApplication.class, args);
    }
}
