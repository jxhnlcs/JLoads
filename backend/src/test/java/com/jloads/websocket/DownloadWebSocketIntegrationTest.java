package com.jloads.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.jloads.support.FakeEngineTestConfig;
import com.jloads.support.FakeYtDlp;
import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.ytdlp.executable=fake-yt-dlp",
        "app.ffmpeg.executable=",
        "app.rate-limit.enabled=false",
        "app.cleanup.enabled=false"
})
@Import(FakeEngineTestConfig.class)
class DownloadWebSocketIntegrationTest {

    static {
        System.setProperty("app.downloads.directory",
                Path.of("target", "test-storage", "ws-" + UUID.randomUUID()).toAbsolutePath().toString());
    }

    @LocalServerPort
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void streamsJobLifecycleOnlyToOwner() throws Exception {
        String owner = UUID.randomUUID().toString();
        Recorder ownerEvents = new Recorder();
        Recorder otherEvents = new Recorder();
        WebSocketSession ownerSession = connect(owner, ownerEvents);
        WebSocketSession otherSession = connect(UUID.randomUUID().toString(), otherEvents);

        String jobId = createJob(owner, FakeYtDlp.OK);

        await().atMost(Duration.ofSeconds(30)).until(() -> ownerEvents.events().contains("JOB_COMPLETED"));
        List<String> events = ownerEvents.events();
        assertThat(events).containsSubsequence("JOB_QUEUED", "JOB_STARTED", "JOB_PROGRESS", "JOB_PROCESSING", "JOB_COMPLETED");
        String completed = ownerEvents.messages.getLast();
        assertThat((String) JsonPath.read(completed, "$.jobId")).isEqualTo(jobId);
        assertThat((Integer) JsonPath.read(completed, "$.progress")).isEqualTo(100);
        assertThat((Boolean) JsonPath.read(completed, "$.job.fileAvailable")).isTrue();
        assertThat(otherEvents.messages).isEmpty();

        ownerSession.close();
        otherSession.close();
    }

    @Test
    void publishesCancellation() throws Exception {
        String owner = UUID.randomUUID().toString();
        Recorder recorder = new Recorder();
        WebSocketSession session = connect(owner, recorder);

        String jobId = createJob(owner, FakeYtDlp.SLOW);
        await().atMost(Duration.ofSeconds(30)).until(() -> recorder.events().contains("JOB_PROGRESS"));
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(uri("/api/downloads/" + jobId))
                .header("X-Client-Id", owner).DELETE().build(), HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        await().atMost(Duration.ofSeconds(10)).until(() -> recorder.events().contains("JOB_CANCELLED"));
        assertThat(recorder.events()).doesNotContain("JOB_COMPLETED");
        session.close();
    }

    @Test
    void rejectsConnectionWithoutValidClientId() throws Exception {
        Recorder recorder = new Recorder();
        connect("not-a-uuid", recorder);
        await().atMost(Duration.ofSeconds(5)).until(() -> recorder.closeStatus != null);
        assertThat(recorder.closeStatus.getCode()).isEqualTo(CloseStatus.POLICY_VIOLATION.getCode());
    }

    private WebSocketSession connect(String clientId, Recorder recorder) throws Exception {
        return new StandardWebSocketClient()
                .execute(recorder, new WebSocketHttpHeaders(), URI.create("ws://localhost:" + port + "/ws?clientId=" + clientId))
                .get(10, TimeUnit.SECONDS);
    }

    private String createJob(String owner, String videoId) throws Exception {
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(uri("/api/downloads"))
                .header("Content-Type", "application/json")
                .header("X-Client-Id", owner)
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {"url":"https://youtu.be/%s","type":"AUDIO","quality":"BEST"}""".formatted(videoId)))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(202);
        return JsonPath.read(response.body(), "$.jobId");
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    static final class Recorder extends TextWebSocketHandler {
        final List<String> messages = new CopyOnWriteArrayList<>();
        volatile CloseStatus closeStatus;

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            messages.add(message.getPayload());
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
            closeStatus = status;
        }

        List<String> events() {
            return messages.stream().map(m -> (String) JsonPath.read(m, "$.event")).toList();
        }
    }
}
