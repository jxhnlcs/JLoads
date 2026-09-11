package com.jloads.config;

import com.jloads.process.ExternalBinaries;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/** Verifica se o yt-dlp foi encontrado e se o diretório de armazenamento é gravável. */
@Component("downloadEngine")
@RequiredArgsConstructor
public class DownloadEngineHealthIndicator implements HealthIndicator {

    private final ExternalBinaries binaries;
    private final AppProperties properties;

    @Override
    public Health health() {
        Path storage = Path.of(properties.downloads().directory()).toAbsolutePath().normalize();
        boolean storageWritable = Files.isDirectory(storage) && Files.isWritable(storage);
        boolean ytDlpAvailable = binaries.ytDlpAvailable();
        Health.Builder builder = storageWritable && ytDlpAvailable ? Health.up() : Health.down();
        return builder
                .withDetail("ytDlpAvailable", ytDlpAvailable)
                .withDetail("ffmpegAvailable", binaries.ffmpegLocation().isPresent())
                .withDetail("jsRuntime", binaries.jsRuntimeName())
                .withDetail("storageWritable", storageWritable)
                .build();
    }
}
