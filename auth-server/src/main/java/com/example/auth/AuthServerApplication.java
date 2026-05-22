package com.example.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AuthServerApplication {

    /**
     * http://localhost:9000/.well-known/openid-configuration
     * @param args
     */
    public static void main(String[] args) {
        SpringApplication.run(AuthServerApplication.class, args);
    }
}
