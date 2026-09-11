package com.jloads.service;

import com.jloads.config.AppProperties;
import com.jloads.exception.ErrorCode;
import com.jloads.exception.FileNotFoundException;
import com.jloads.exception.InvalidUrlException;
import com.jloads.exception.JobNotFoundException;
import com.jloads.exception.RequestRejectedException;
import com.jloads.model.ClientId;
import com.jloads.model.DownloadJob;
import com.jloads.model.DownloadJobSnapshot;
import com.jloads.model.StoredFile;
import com.jloads.model.VideoUrl;
import com.jloads.model.enums.DownloadEventType;
import com.jloads.model.enums.DownloadQuality;
import com.jloads.model.enums.DownloadStatus;
import com.jloads.model.enums.DownloadType;
import com.jloads.process.ProcessRegistry;
import com.jloads.process.RunningProcess;
import com.jloads.repository.DownloadJobRepository;
import com.jloads.validation.YouTubeUrlValidator;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Casos de uso de download: criar (individual ou em lote), consultar, listar, cancelar e obter o arquivo. */
@Slf4j
@Service
public class DownloadService {

    private final DownloadJobRepository repository;
    private final YouTubeUrlValidator urlValidator;
    private final VideoAnalysisService analysisService;
    private final QueueService queueService;
    private final ProcessRegistry processRegistry;
    private final FileStorageService storage;
    private final DownloadEventPublisher events;
    private final DownloadMetrics metrics;
    private final AppProperties properties;
    private final Clock clock;
    private final Object admissionLock = new Object();

    public DownloadService(DownloadJobRepository repository, YouTubeUrlValidator urlValidator,
                           VideoAnalysisService analysisService, QueueService queueService,
                           ProcessRegistry processRegistry, FileStorageService storage,
                           DownloadEventPublisher events, DownloadMetrics metrics,
                           AppProperties properties, Clock clock) {
        this.repository = repository;
        this.urlValidator = urlValidator;
        this.analysisService = analysisService;
        this.queueService = queueService;
        this.processRegistry = processRegistry;
        this.storage = storage;
        this.events = events;
        this.metrics = metrics;
        this.properties = properties;
        this.clock = clock;
    }

    public DownloadJobSnapshot create(String rawUrl, DownloadType type, DownloadQuality quality,
                                      ClientId ownerId, String requesterKey) {
        VideoUrl url = urlValidator.validate(rawUrl);
        analysisService.validateOptionIfKnown(url, type, quality);
        return admit(List.of(url), type, quality, ownerId, requesterKey).getFirst();
    }

    /**
     * Cria vários jobs com o mesmo tipo/qualidade. A validação é "tudo ou nada": se um link for inválido ou os
     * limites não comportarem o lote inteiro, nenhum job é criado. Links repetidos são enviados uma única vez.
     */
    public List<DownloadJobSnapshot> createBatch(List<String> rawUrls, DownloadType type, DownloadQuality quality,
                                                 ClientId ownerId, String requesterKey) {
        List<String> candidates = rawUrls == null ? List.of()
                : rawUrls.stream().filter(url -> url != null && !url.isBlank()).toList();
        int maxBatchSize = properties.downloads().maxBatchSize();
        if (candidates.isEmpty()) {
            throw new RequestRejectedException(ErrorCode.INVALID_REQUEST, "Informe ao menos um link.", "empty batch");
        }
        if (candidates.size() > maxBatchSize) {
            throw new RequestRejectedException(ErrorCode.BATCH_TOO_LARGE,
                    "Você pode enviar no máximo " + maxBatchSize + " links por vez.", "batch too large");
        }

        Map<String, VideoUrl> unique = new LinkedHashMap<>();
        for (int i = 0; i < candidates.size(); i++) {
            VideoUrl url = validateBatchItem(candidates.get(i), i + 1);
            unique.putIfAbsent(url.videoId(), url);
        }
        unique.values().forEach(url -> analysisService.validateOptionIfKnown(url, type, quality));
        return admit(unique.values(), type, quality, ownerId, requesterKey);
    }

    public DownloadJobSnapshot get(UUID jobId) {
        return find(jobId).snapshot();
    }

    public List<DownloadJobSnapshot> list(ClientId ownerId) {
        return repository.findByOwner(ownerId).stream().map(DownloadJob::snapshot).toList();
    }

    /**
     * Cancela um job ativo. Jobs na fila são removidos; jobs em execução têm o processo do yt-dlp (e seus
     * descendentes) encerrado.
     */
    public DownloadJobSnapshot cancel(UUID jobId, ClientId requester) {
        DownloadJob job = find(jobId);
        if (!job.getOwnerId().equals(requester)) {
            throw new JobNotFoundException(jobId);
        }
        if (!job.markCancelled(Instant.now(clock))) {
            throw new RequestRejectedException(ErrorCode.JOB_NOT_CANCELLABLE, "job already finished");
        }
        repository.save(job);
        events.publish(DownloadEventType.JOB_CANCELLED, job);
        metrics.jobCancelled(job.getType());
        boolean removedFromQueue = queueService.remove(jobId);
        boolean processKilled = processRegistry.terminate(jobId, RunningProcess.TerminationReason.CANCELLED);
        if (removedFromQueue) {
            storage.deleteJobFiles(jobId);
        }
        log.info("Job cancelled: jobId={} removedFromQueue={} processKilled={}", jobId, removedFromQueue, processKilled);
        return job.snapshot();
    }

    public StoredFile getCompletedFile(UUID jobId) {
        DownloadJobSnapshot job = get(jobId);
        if (job.status() != DownloadStatus.COMPLETED) {
            throw new FileNotFoundException("job not completed");
        }
        if (job.fileExpired()) {
            throw FileNotFoundException.expired("file expired");
        }
        return storage.loadCompleted(jobId, job.filename());
    }

    private VideoUrl validateBatchItem(String rawUrl, int position) {
        try {
            return urlValidator.validate(rawUrl);
        } catch (InvalidUrlException ex) {
            throw new InvalidUrlException(ex.getErrorCode(),
                    "O link " + position + " não é um vídeo do YouTube válido.", ex.getMessage());
        }
    }

    private List<DownloadJobSnapshot> admit(Collection<VideoUrl> urls, DownloadType type, DownloadQuality quality,
                                            ClientId ownerId, String requesterKey) {
        Instant now = Instant.now(clock);
        List<DownloadJob> jobs = urls.stream()
                .map(url -> DownloadJob.create(ownerId, requesterKey, url, type, quality, now))
                .toList();

        synchronized (admissionLock) {
            ensureCapacity(requesterKey, jobs.size());
            for (DownloadJob job : jobs) {
                repository.save(job);
                // O monitor do job bloqueia o worker (markAnalyzing é sincronizado) até JOB_QUEUED ser publicado,
                // garantindo a ordem dos eventos.
                synchronized (job) {
                    try {
                        queueService.enqueue(job.getId());
                    } catch (RequestRejectedException ex) {
                        repository.delete(job.getId());
                        throw ex;
                    }
                    events.publish(DownloadEventType.JOB_QUEUED, job);
                }
                metrics.jobCreated(type);
                log.info("Job created: jobId={} videoId={} type={} quality={}",
                        job.getId(), job.getVideoUrl().videoId(), type, quality);
            }
        }
        return jobs.stream().map(DownloadJob::snapshot).toList();
    }

    /** Deve ser chamado sob {@code admissionLock}: apenas este serviço enfileira, então a capacidade não diminui. */
    private void ensureCapacity(String requesterKey, int requested) {
        int limit = properties.downloads().maxActiveJobsPerClient();
        long active = repository.countActiveByRequester(requesterKey);
        if (active + requested > limit) {
            long remaining = Math.max(0, limit - active);
            String message = remaining == 0 || requested == 1
                    ? ErrorCode.TOO_MANY_ACTIVE_JOBS.defaultMessage()
                    : "Você pode adicionar mais " + remaining + " download(s) agora. Aguarde a conclusão dos atuais.";
            throw new RequestRejectedException(ErrorCode.TOO_MANY_ACTIVE_JOBS, message, "active job limit reached");
        }
        if (queueService.remainingCapacity() < requested) {
            throw new RequestRejectedException(ErrorCode.QUEUE_FULL, "queue has no capacity for request");
        }
    }

    private DownloadJob find(UUID jobId) {
        return repository.findById(jobId).orElseThrow(() -> new JobNotFoundException(jobId));
    }
}
