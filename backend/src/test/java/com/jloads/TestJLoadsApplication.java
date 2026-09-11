package com.jloads;

import com.jloads.support.FakeEngineTestConfig;
import org.springframework.boot.SpringApplication;

/**
 * Sobe a aplicação com o yt-dlp simulado ({@code ./mvnw spring-boot:test-run}), útil para desenvolver o
 * frontend sem acessar o YouTube. IDs de exemplo: okvideo0001, slowvideo01, forbidden01, unavailable.
 */
public class TestJLoadsApplication {

    public static void main(String[] args) {
        SpringApplication.from(JLoadsApplication::main)
                .with(FakeEngineTestConfig.class)
                .run(args);
    }
}
