package com.jloads.support;

import com.jloads.config.AppProperties;
import java.time.Duration;
import java.util.List;
import org.springframework.util.unit.DataSize;

public final class TestProperties {

    private TestProperties() {
    }

    public static AppProperties create(String storageDirectory, int maxConcurrent, int maxQueueSize, int maxActivePerClient) {
        return new AppProperties(
                new AppProperties.YtDlp("yt-dlp", "node", Duration.ofSeconds(30), 30, 3, 2, Duration.ofMinutes(10)),
                new AppProperties.Ffmpeg(""),
                new AppProperties.Downloads(storageDirectory, maxConcurrent, maxQueueSize, maxActivePerClient, 5,
                        DataSize.ofMegabytes(100), DataSize.ofGigabytes(1), Duration.ofMinutes(5), Duration.ofHours(3), ""),
                new AppProperties.Cleanup(true, 30, 120, Duration.ofMinutes(5)),
                new AppProperties.RateLimit(true, 120, 20, 10),
                new AppProperties.Cors(List.of("http://localhost:4200")));
    }

    public static AppProperties defaults() {
        return create("storage", 3, 50, 5);
    }
}
