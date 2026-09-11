package com.jloads.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "app.ytdlp.executable=fake-yt-dlp",
        "app.rate-limit.enabled=true",
        "app.rate-limit.analyze-requests-per-minute=2",
        "app.cleanup.enabled=false"
})
@AutoConfigureMockMvc
class RateLimitIntegrationTest {

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("app.downloads.directory",
                () -> Path.of("target", "test-storage", "rl-" + UUID.randomUUID()).toAbsolutePath().toString());
    }

    @Autowired
    MockMvc mvc;

    @Test
    void rejectsExcessAnalyzeRequestsWithRetryAfter() throws Exception {
        String body = "{\"url\":\"https://evil.example/not-youtube\"}";
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/videos/analyze").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/videos/analyze").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }
}
