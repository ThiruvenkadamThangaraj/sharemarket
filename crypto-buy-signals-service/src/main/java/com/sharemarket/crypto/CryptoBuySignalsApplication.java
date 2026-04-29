package com.sharemarket.crypto;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CryptoBuySignalsApplication {

    public static void main(String[] args) {
        SpringApplication.run(CryptoBuySignalsApplication.class, args);
    }
}
