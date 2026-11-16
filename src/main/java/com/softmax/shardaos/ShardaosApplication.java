package com.softmax.shardaos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class ShardaosApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShardaosApplication.class, args);
    }

}

