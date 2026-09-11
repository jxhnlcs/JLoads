package com.jloads.repository;

import com.jloads.model.ClientId;
import com.jloads.model.DownloadJob;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Porta de persistência de jobs. Implementação atual em memória; pode ser trocada por Redis/banco. */
public interface DownloadJobRepository {

    DownloadJob save(DownloadJob job);

    Optional<DownloadJob> findById(UUID id);

    /** Jobs de um cliente, do mais recente para o mais antigo. */
    List<DownloadJob> findByOwner(ClientId ownerId);

    List<DownloadJob> findAll();

    long countActiveByRequester(String requesterKey);

    void delete(UUID id);
}
