package com.jloads.service;

import com.jloads.model.StoredFile;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Armazenamento dos arquivos de download. A implementação atual usa o disco local; outra implementação
 * (ex.: S3) pode substituí-la mantendo o workspace temporário local para o yt-dlp.
 */
public interface FileStorageService {

    /** Cria (limpo) o diretório temporário {@code temporary/{jobId}} usado durante o download. */
    Path prepareWorkspace(UUID jobId);

    /** Localiza o arquivo de mídia final gerado pelo yt-dlp no workspace do job. */
    Path locateDownloadedMedia(UUID jobId);

    /** Move a mídia para {@code completed/{jobId}/} com um nome de arquivo sanitizado. */
    StoredFile storeCompleted(UUID jobId, Path downloadedFile, String title);

    /** Carrega um arquivo concluído, validando que pertence ao diretório do job. */
    StoredFile loadCompleted(UUID jobId, String filename);

    /** Remove workspace temporário e arquivos concluídos do job. */
    void deleteJobFiles(UUID jobId);

    void deleteWorkspace(UUID jobId);

    long usedSpaceBytes();

    /**
     * Remove diretórios de jobs modificados antes de {@code olderThan} que não estejam protegidos.
     *
     * @return quantidade de diretórios removidos
     */
    int deleteOrphans(Instant olderThan, Set<UUID> protectedJobIds);
}
