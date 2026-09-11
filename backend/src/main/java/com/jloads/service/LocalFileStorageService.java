package com.jloads.service;

import com.jloads.config.AppProperties;
import com.jloads.exception.DownloadException;
import com.jloads.exception.ErrorCode;
import com.jloads.exception.FileNotFoundException;
import com.jloads.model.StoredFile;
import com.jloads.util.FilenameSanitizer;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Armazenamento em disco local:
 * <pre>
 * {root}/temporary/{jobId}/   workspace do yt-dlp durante o download
 * {root}/completed/{jobId}/   arquivo final disponível para download
 * </pre>
 * Todos os caminhos são derivados de UUIDs e nomes sanitizados, e validados para permanecerem dentro da raiz.
 */
@Slf4j
@Service
public class LocalFileStorageService implements FileStorageService {

    static final Pattern MEDIA_FILE = Pattern.compile("^media\\.(mp3|mp4|m4a|webm|mkv)$");
    private static final Set<String> MEDIA_EXTENSIONS = Set.of("mp3", "mp4", "m4a", "webm", "mkv");

    private final Path root;
    private final Path temporaryRoot;
    private final Path completedRoot;

    public LocalFileStorageService(AppProperties properties) {
        this.root = Path.of(properties.downloads().directory()).toAbsolutePath().normalize();
        this.temporaryRoot = root.resolve("temporary");
        this.completedRoot = root.resolve("completed");
    }

    @PostConstruct
    void initialize() {
        try {
            Files.createDirectories(temporaryRoot);
            Files.createDirectories(completedRoot);
        } catch (IOException ex) {
            throw new UncheckedIOException("Unable to create storage directories", ex);
        }
        // O estado dos jobs fica em memória: workspaces de uma execução anterior nunca serão retomados.
        int removed = deleteChildren(temporaryRoot);
        log.info("Storage ready at {} (removed {} stale workspace(s))", root, removed);
    }

    @Override
    public Path prepareWorkspace(UUID jobId) {
        Path workspace = jobDirectory(temporaryRoot, jobId);
        try {
            deleteRecursively(workspace);
            return Files.createDirectories(workspace);
        } catch (IOException ex) {
            throw new DownloadException(ErrorCode.DOWNLOAD_FAILED, "unable to create workspace", ex);
        }
    }

    @Override
    public Path locateDownloadedMedia(UUID jobId) {
        Path workspace = jobDirectory(temporaryRoot, jobId);
        try (Stream<Path> files = Files.list(workspace)) {
            return files
                    .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> MEDIA_FILE.matcher(path.getFileName().toString()).matches())
                    .max(Comparator.comparingLong(LocalFileStorageService::sizeOf))
                    .orElseThrow(() -> new DownloadException(ErrorCode.PROCESSING_FAILED, "media file not produced"));
        } catch (IOException ex) {
            throw new DownloadException(ErrorCode.PROCESSING_FAILED, "unable to read workspace", ex);
        }
    }

    @Override
    public StoredFile storeCompleted(UUID jobId, Path downloadedFile, String title) {
        Path workspace = jobDirectory(temporaryRoot, jobId);
        Path source = downloadedFile.toAbsolutePath().normalize();
        if (!workspace.equals(source.getParent()) || !Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
            throw new DownloadException(ErrorCode.PROCESSING_FAILED, "downloaded file outside workspace");
        }
        String extension = extensionOf(source.getFileName().toString())
                .filter(MEDIA_EXTENSIONS::contains)
                .orElseThrow(() -> new DownloadException(ErrorCode.PROCESSING_FAILED, "unexpected media extension"));

        String filename = FilenameSanitizer.toFilename(title, extension);
        Path targetDirectory = jobDirectory(completedRoot, jobId);
        Path target = resolveInside(targetDirectory, filename);
        try {
            Files.createDirectories(targetDirectory);
            move(source, target);
            long size = Files.size(target);
            deleteWorkspace(jobId);
            return new StoredFile(filename, target, size);
        } catch (IOException ex) {
            throw new DownloadException(ErrorCode.PROCESSING_FAILED, "unable to store completed file", ex);
        }
    }

    @Override
    public StoredFile loadCompleted(UUID jobId, String filename) {
        if (filename == null) {
            throw new FileNotFoundException("no file for job");
        }
        Path target = resolveInside(jobDirectory(completedRoot, jobId), filename);
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            throw FileNotFoundException.expired("completed file missing");
        }
        return new StoredFile(filename, target, sizeOf(target));
    }

    @Override
    public void deleteJobFiles(UUID jobId) {
        deleteWorkspace(jobId);
        deleteQuietly(jobDirectory(completedRoot, jobId));
    }

    @Override
    public void deleteWorkspace(UUID jobId) {
        deleteQuietly(jobDirectory(temporaryRoot, jobId));
    }

    @Override
    public long usedSpaceBytes() {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .mapToLong(LocalFileStorageService::sizeOf)
                    .sum();
        } catch (IOException | UncheckedIOException ex) {
            log.warn("Unable to compute storage usage: {}", ex.getMessage());
            return 0;
        }
    }

    @Override
    public int deleteOrphans(Instant olderThan, Set<UUID> protectedJobIds) {
        int removed = 0;
        for (Path base : List.of(temporaryRoot, completedRoot)) {
            try (Stream<Path> children = Files.list(base)) {
                for (Path directory : children.toList()) {
                    Optional<UUID> jobId = parseUuid(directory.getFileName().toString());
                    if (jobId.isEmpty() || protectedJobIds.contains(jobId.get())) {
                        continue;
                    }
                    if (lastModified(directory).isBefore(olderThan) && deleteQuietly(directory)) {
                        removed++;
                    }
                }
            } catch (IOException ex) {
                log.warn("Unable to scan {} for cleanup: {}", base.getFileName(), ex.getMessage());
            }
        }
        return removed;
    }

    Path jobDirectory(Path base, UUID jobId) {
        Path directory = base.resolve(jobId.toString()).normalize();
        if (!base.equals(directory.getParent())) {
            throw new IllegalStateException("Resolved job directory escapes storage root");
        }
        return directory;
    }

    private static Path resolveInside(Path directory, String filename) {
        Path target = directory.resolve(filename).normalize();
        if (!directory.equals(target.getParent())) {
            throw new FileNotFoundException("invalid filename");
        }
        return target;
    }

    private static void move(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Optional<String> extensionOf(String filename) {
        Matcher matcher = MEDIA_FILE.matcher(filename);
        return matcher.matches() ? Optional.of(matcher.group(1).toLowerCase(Locale.ROOT)) : Optional.empty();
    }

    private static Optional<UUID> parseUuid(String value) {
        try {
            UUID uuid = UUID.fromString(value);
            return uuid.toString().equals(value) ? Optional.of(uuid) : Optional.empty();
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private static Instant lastModified(Path directory) {
        try (Stream<Path> entries = Files.walk(directory)) {
            return entries.map(LocalFileStorageService::modifiedAt).max(Instant::compareTo).orElse(Instant.EPOCH);
        } catch (IOException | UncheckedIOException ex) {
            return Instant.EPOCH;
        }
    }

    private static Instant modifiedAt(Path path) {
        try {
            return Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS)
                    .lastModifiedTime().toInstant();
        } catch (IOException ex) {
            return Instant.EPOCH;
        }
    }

    private static long sizeOf(Path path) {
        try {
            return Files.size(path);
        } catch (IOException ex) {
            return 0;
        }
    }

    private int deleteChildren(Path base) {
        try (Stream<Path> children = Files.list(base)) {
            return (int) children.toList().stream().filter(LocalFileStorageService::deleteQuietly).count();
        } catch (IOException ex) {
            return 0;
        }
    }

    private static boolean deleteQuietly(Path path) {
        try {
            return deleteRecursively(path);
        } catch (IOException ex) {
            log.warn("Unable to delete {}: {}", path.getFileName(), ex.getMessage());
            return false;
        }
    }

    private static boolean deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        try (Stream<Path> entries = Files.walk(path)) {
            for (Path entry : entries.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(entry);
            }
        }
        return true;
    }
}
