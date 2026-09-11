package com.jloads.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.jloads.model.ClientId;
import com.jloads.model.DownloadJobSnapshot;
import com.jloads.model.enums.DownloadQuality;
import com.jloads.model.enums.DownloadStatus;
import com.jloads.model.enums.DownloadType;
import com.jloads.process.ProcessRegistry;
import com.jloads.support.FakeEngineTestConfig;
import com.jloads.support.FakeYtDlp;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(properties = {
        "app.ytdlp.executable=fake-yt-dlp",
        "app.downloads.timeout=3s",
        "app.rate-limit.enabled=false",
        "app.cleanup.enabled=false"
})
@Import(FakeEngineTestConfig.class)
class DownloadTimeoutIntegrationTest {

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("app.downloads.directory",
                () -> Path.of("target", "test-storage", "timeout-" + UUID.randomUUID()).toAbsolutePath().toString());
    }

    @Autowired
    DownloadService downloadService;

    @Autowired
    ProcessRegistry processRegistry;

    @Test
    void killsProcessAndFailsJobWhenTimeoutIsExceeded() {
        DownloadJobSnapshot created = downloadService.create("https://youtu.be/" + FakeYtDlp.SLOW, DownloadType.VIDEO,
                DownloadQuality.BEST, new ClientId(UUID.randomUUID()), "127.0.0.1");

        await().atMost(Duration.ofSeconds(30))
                .until(() -> downloadService.get(created.id()).status() == DownloadStatus.FAILED);

        DownloadJobSnapshot failed = downloadService.get(created.id());
        assertThat(failed.errorCode()).isEqualTo("DOWNLOAD_TIMEOUT");
        assertThat(failed.errorMessage()).isEqualTo("O download excedeu o tempo máximo permitido.");
        assertThat(processRegistry.isRunning(created.id())).isFalse();
    }
}
