package com.jloads.repository;

import com.jloads.model.ClientId;
import com.jloads.model.DownloadJob;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryDownloadJobRepository implements DownloadJobRepository {

    private static final Comparator<DownloadJob> NEWEST_FIRST =
            Comparator.comparing(DownloadJob::getCreatedAt).reversed();

    private final Map<UUID, DownloadJob> jobs = new ConcurrentHashMap<>();

    @Override
    public DownloadJob save(DownloadJob job) {
        jobs.put(job.getId(), job);
        return job;
    }

    @Override
    public Optional<DownloadJob> findById(UUID id) {
        return Optional.ofNullable(jobs.get(id));
    }

    @Override
    public List<DownloadJob> findByOwner(ClientId ownerId) {
        return jobs.values().stream()
                .filter(job -> job.getOwnerId().equals(ownerId))
                .sorted(NEWEST_FIRST)
                .toList();
    }

    @Override
    public List<DownloadJob> findAll() {
        return jobs.values().stream().sorted(NEWEST_FIRST).toList();
    }

    @Override
    public long countActiveByRequester(String requesterKey) {
        return jobs.values().stream()
                .filter(job -> job.getRequesterKey().equals(requesterKey))
                .filter(job -> !job.isTerminal())
                .count();
    }

    @Override
    public void delete(UUID id) {
        jobs.remove(id);
    }
}
