package com.messenger.chatgateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ChatGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChatGatewayApplication.class, args);
    }

}
