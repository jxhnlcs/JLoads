package com.jloads.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jloads.process.ProcessRegistry;
import com.jloads.support.FakeEngineTestConfig;
import com.jloads.support.FakeYtDlp;
import com.jayway.jsonpath.JsonPath;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "app.ytdlp.executable=fake-yt-dlp",
        "app.ffmpeg.executable=",
        "app.downloads.max-concurrent=2",
        "app.downloads.max-active-jobs-per-client=50",
        "app.rate-limit.enabled=false",
        "app.cleanup.enabled=false"
})
@AutoConfigureMockMvc
@Import(FakeEngineTestConfig.class)
class DownloadApiIntegrationTest {

    static final Path storage = Path.of("target", "test-storage", UUID.randomUUID().toString()).toAbsolutePath();

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("app.downloads.directory", () -> storage.toString());
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    ProcessRegistry processRegistry;

    private final String clientId = UUID.randomUUID().toString();

    @Test
    void analyzeReturnsMetadataAndOptions() throws Exception {
        mvc.perform(post("/api/videos/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("https://youtu.be/" + FakeYtDlp.OK)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.videoId").value(FakeYtDlp.OK))
                .andExpect(jsonPath("$.channel").value("Canal Teste"))
                .andExpect(jsonPath("$.duration").value(125))
                .andExpect(jsonPath("$.thumbnail").value(containsString("i.ytimg.com")))
                .andExpect(jsonPath("$.availableOptions[?(@.type == 'VIDEO')]", hasSize(3)))
                .andExpect(jsonPath("$.availableOptions[?(@.type == 'AUDIO')]", hasSize(4)));
    }

    @Test
    void analyzeRejectsUnsupportedHost() throws Exception {
        mvc.perform(post("/api/videos/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("https://example.com/watch?v=" + FakeYtDlp.OK)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_URL"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void analyzeTranslatesEngineErrors() throws Exception {
        mvc.perform(post("/api/videos/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("https://www.youtube.com/watch?v=" + FakeYtDlp.UNAVAILABLE)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VIDEO_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(containsString("ERROR"))));

        mvc.perform(post("/api/videos/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("https://www.youtube.com/watch?v=" + FakeYtDlp.LIVE)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("LIVE_NOT_SUPPORTED"));
    }

    @Test
    void createDownloadCompletesAndServesSanitizedFile() throws Exception {
        String jobId = createJob(FakeYtDlp.OK, "AUDIO", "MEDIUM");

        awaitStatus(jobId, "COMPLETED");

        mvc.perform(get("/api/downloads/{id}", jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progress").value(100))
                .andExpect(jsonPath("$.fileAvailable").value(true))
                .andExpect(jsonPath("$.filename").value("Vídeo de teste script .. CON.mp3"));

        mvc.perform(get("/api/downloads/{id}/file", jobId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "audio/mpeg"))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().longValue("Content-Length", 4096));

        assertThat(storage.resolve("temporary").resolve(jobId)).doesNotExist();
        assertThat(storage.resolve("completed").resolve(jobId)).isDirectory();
    }

    @Test
    void getUnknownJobReturns404() throws Exception {
        mvc.perform(get("/api/downloads/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("JOB_NOT_FOUND"));
    }

    @Test
    void fileIsNotServedBeforeCompletion() throws Exception {
        String jobId = createJob(FakeYtDlp.SLOW, "VIDEO", "BEST");
        mvc.perform(get("/api/downloads/{id}/file", jobId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FILE_NOT_AVAILABLE"));
        cancel(jobId).andExpect(status().isOk());
    }

    @Test
    void cancelStopsRunningProcess() throws Exception {
        String jobId = createJob(FakeYtDlp.SLOW, "VIDEO", "BEST");
        UUID id = UUID.fromString(jobId);

        awaitStatus(jobId, "DOWNLOADING");
        await().atMost(Duration.ofSeconds(10)).until(() -> processRegistry.isRunning(id));

        cancel(jobId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        await().atMost(Duration.ofSeconds(10)).until(() -> !processRegistry.isRunning(id));
        await().atMost(Duration.ofSeconds(10)).until(() -> !Files.exists(storage.resolve("temporary").resolve(jobId)));
        mvc.perform(get("/api/downloads/{id}", jobId)).andExpect(jsonPath("$.status").value("CANCELLED"));

        cancel(jobId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("JOB_NOT_CANCELLABLE"));
    }

    @Test
    void cancelByAnotherClientIsRejected() throws Exception {
        String jobId = createJob(FakeYtDlp.SLOW, "AUDIO", "LOW");
        mvc.perform(delete("/api/downloads/{id}", jobId).header("X-Client-Id", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());
        cancel(jobId).andExpect(status().isOk());
    }

    @Test
    void failedDownloadExposesFriendlyMessage() throws Exception {
        String forbidden = createJob(FakeYtDlp.FORBIDDEN, "VIDEO", "BEST");
        awaitStatus(forbidden, "FAILED");
        mvc.perform(get("/api/downloads/{id}", forbidden))
                .andExpect(jsonPath("$.errorCode").value("SOURCE_FORBIDDEN"))
                .andExpect(jsonPath("$.errorMessage").value(containsString("recusou o acesso")));

        String huge = createJob(FakeYtDlp.HUGE, "VIDEO", "BEST");
        awaitStatus(huge, "FAILED");
        mvc.perform(get("/api/downloads/{id}", huge)).andExpect(jsonPath("$.errorCode").value("FILE_TOO_LARGE"));
    }

    @Test
    void listReturnsOnlyJobsOfClient() throws Exception {
        String jobId = createJob(FakeYtDlp.OK, "AUDIO", "BEST");
        mvc.perform(get("/api/downloads").header("X-Client-Id", clientId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(jobId));
        mvc.perform(get("/api/downloads").header("X-Client-Id", UUID.randomUUID().toString()))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void createRejectsInvalidPayloads() throws Exception {
        mvc.perform(post("/api/downloads")
                        .header("X-Client-Id", clientId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://youtu.be/okvideo0001\",\"type\":\"EXE\",\"quality\":\"BEST\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mvc.perform(post("/api/downloads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://youtu.be/okvideo0001\",\"type\":\"AUDIO\",\"quality\":\"BEST\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CLIENT_ID"));
    }

    @Test
    void batchCreatesJobsForUniqueLinks() throws Exception {
        String body = mvc.perform(post("/api/downloads/batch")
                        .header("X-Client-Id", clientId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"urls":["https://youtu.be/%s","https://www.youtube.com/watch?v=%s","https://youtu.be/%s"],
                                 "type":"VIDEO","quality":"HIGH"}
                                """.formatted(FakeYtDlp.OK, FakeYtDlp.OK, FakeYtDlp.SLOW)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobs", hasSize(2)))
                .andReturn().getResponse().getContentAsString();

        String okJob = JsonPath.read(body, "$.jobs[0].id");
        String slowJob = JsonPath.read(body, "$.jobs[1].id");
        // "Até 1080p" em um vídeo cujo máximo é 1080p (ou menor) não deve falhar.
        awaitStatus(okJob, "COMPLETED");
        cancel(slowJob).andExpect(status().isOk());
    }

    @Test
    void batchRejectsInvalidOrOversizedRequests() throws Exception {
        mvc.perform(post("/api/downloads/batch")
                        .header("X-Client-Id", clientId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"urls":["https://youtu.be/okvideo0001","https://vimeo.com/123"],"type":"AUDIO","quality":"BEST"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_URL"))
                .andExpect(jsonPath("$.message").value(containsString("link 2")));

        mvc.perform(post("/api/downloads/batch")
                        .header("X-Client-Id", clientId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"urls":["https://youtu.be/aaaaaaaaaa1","https://youtu.be/aaaaaaaaaa2","https://youtu.be/aaaaaaaaaa3",
                                         "https://youtu.be/aaaaaaaaaa4","https://youtu.be/aaaaaaaaaa5","https://youtu.be/aaaaaaaaaa6"],
                                 "type":"AUDIO","quality":"BEST"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BATCH_TOO_LARGE"));

        mvc.perform(get("/api/downloads").header("X-Client-Id", clientId))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void configExposesPublicLimits() throws Exception {
        mvc.perform(get("/api/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxBatchSize").value(5))
                .andExpect(jsonPath("$.maxConcurrentDownloads").value(2))
                .andExpect(jsonPath("$.fileRetentionMinutes").value(30))
                .andExpect(jsonPath("$.maxFileSizeBytes").value(1024L * 1024 * 1024));
    }

    private String createJob(String videoId, String type, String quality) throws Exception {
        String body = mvc.perform(post("/api/downloads")
                        .header("X-Client-Id", clientId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url":"https://www.youtube.com/watch?v=%s","type":"%s","quality":"%s"}
                                """.formatted(videoId, type, quality)))
                .andExpect(status().isAccepted())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.jobId").exists())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.jobId");
    }

    private org.springframework.test.web.servlet.ResultActions cancel(String jobId) throws Exception {
        return mvc.perform(delete("/api/downloads/{id}", jobId).header("X-Client-Id", clientId));
    }

    private void awaitStatus(String jobId, String expected) {
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(100)).until(() -> {
            String body = mvc.perform(get("/api/downloads/{id}", jobId)).andReturn().getResponse().getContentAsString();
            return expected.equals(JsonPath.read(body, "$.status"));
        });
    }

    private static String json(String url) {
        return "{\"url\":\"" + url + "\"}";
    }
}
