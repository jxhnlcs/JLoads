package com.jloads.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jloads.exception.DownloadException;
import com.jloads.exception.FileNotFoundException;
import com.jloads.model.StoredFile;
import com.jloads.support.TestProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFileStorageServiceTest {

    @TempDir
    Path root;

    private LocalFileStorageService storage;

    @BeforeEach
    void setUp() {
        storage = new LocalFileStorageService(TestProperties.create(root.toString(), 1, 10, 5));
        storage.initialize();
    }

    @Test
    void storesMediaWithSanitizedNameInsideJobDirectory() throws Exception {
        UUID jobId = UUID.randomUUID();
        Path workspace = storage.prepareWorkspace(jobId);
        Files.writeString(workspace.resolve("media.f140.m4a.part"), "partial");
        Files.write(workspace.resolve("media.mp3"), new byte[128]);

        Path media = storage.locateDownloadedMedia(jobId);
        StoredFile stored = storage.storeCompleted(jobId, media, "../../evil/..\\name:?");

        assertThat(media.getFileName().toString()).isEqualTo("media.mp3");
        assertThat(stored.path().getParent()).isEqualTo(root.resolve("completed").resolve(jobId.toString()));
        assertThat(stored.filename()).doesNotContain("/", "\\", ":").endsWith(".mp3");
        assertThat(stored.sizeBytes()).isEqualTo(128);
        assertThat(workspace).doesNotExist();
        assertThat(storage.loadCompleted(jobId, stored.filename()).path()).isEqualTo(stored.path());
    }

    @Test
    void refusesTraversalWhenLoading() {
        UUID jobId = UUID.randomUUID();
        assertThatThrownBy(() -> storage.loadCompleted(jobId, "../other/secret.mp3"))
                .isInstanceOf(FileNotFoundException.class);
        assertThatThrownBy(() -> storage.loadCompleted(jobId, "..\\..\\application.yml"))
                .isInstanceOf(FileNotFoundException.class);
    }

    @Test
    void refusesFilesOutsideWorkspaceOrWithUnexpectedExtensions() throws Exception {
        UUID jobId = UUID.randomUUID();
        Path workspace = storage.prepareWorkspace(jobId);
        Path outside = Files.writeString(root.resolve("media.mp3"), "x");
        Files.writeString(workspace.resolve("media.exe"), "x");

        assertThatThrownBy(() -> storage.storeCompleted(jobId, outside, "t")).isInstanceOf(DownloadException.class);
        assertThatThrownBy(() -> storage.locateDownloadedMedia(jobId)).isInstanceOf(DownloadException.class);
    }

    @Test
    void deletesOnlyOldUnprotectedJobDirectories() throws Exception {
        UUID oldJob = UUID.randomUUID();
        UUID protectedJob = UUID.randomUUID();
        UUID recentJob = UUID.randomUUID();
        Instant old = Instant.now().minus(Duration.ofHours(2));
        for (UUID id : Set.of(oldJob, protectedJob)) {
            Path file = Files.writeString(storage.prepareWorkspace(id).resolve("media.mp3"), "x");
            Files.setLastModifiedTime(file, FileTime.from(old));
            Files.setLastModifiedTime(file.getParent(), FileTime.from(old));
        }
        storage.prepareWorkspace(recentJob);
        Path unrelated = Files.createDirectories(root.resolve("temporary").resolve("not-a-job"));
        Files.setLastModifiedTime(unrelated, FileTime.from(old));

        int removed = storage.deleteOrphans(Instant.now().minus(Duration.ofMinutes(30)), Set.of(protectedJob));

        assertThat(removed).isEqualTo(1);
        assertThat(root.resolve("temporary").resolve(oldJob.toString())).doesNotExist();
        assertThat(root.resolve("temporary").resolve(protectedJob.toString())).exists();
        assertThat(root.resolve("temporary").resolve(recentJob.toString())).exists();
        assertThat(unrelated).exists();
    }

    @Test
    void reportsUsedSpace() throws Exception {
        Files.write(storage.prepareWorkspace(UUID.randomUUID()).resolve("media.mp4"), new byte[1000]);
        assertThat(storage.usedSpaceBytes()).isEqualTo(1000);
    }
}
