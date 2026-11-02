package com.example.sns;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** @EnableScheduling: SSE の接続に 30 秒ごとに合図を送るため(StreamRegistry.heartbeat) */
@SpringBootApplication
@EnableScheduling
public class SnsApplication {

    public static void main(String[] args) {
        SpringApplication.run(SnsApplication.class, args);
    }
}
