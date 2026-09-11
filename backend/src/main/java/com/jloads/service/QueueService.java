package com.jloads.service;

import com.jloads.config.AppProperties;
import com.jloads.exception.ErrorCode;
import com.jloads.exception.RequestRejectedException;
import com.jloads.worker.DownloadWorker;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Service;

/**
 * Fila interna de downloads com número fixo de workers. Requisições HTTP apenas enfileiram IDs de jobs;
 * nenhuma thread é criada por requisição.
 *
 * <pre>
 *                Download Queue (limitada)
 *                         │
 *             ┌───────────┼───────────┐
 *          Worker 1    Worker 2    Worker N   (N = app.downloads.max-concurrent)
 * </pre>
 */
@Slf4j
@Service
public class QueueService implements SmartLifecycle {

    private final BlockingQueue<UUID> queue;
    private final DownloadWorker worker;
    private final int workerCount;
    private final List<Thread> workers = new ArrayList<>();
    private final AtomicInteger busyWorkers = new AtomicInteger();
    private volatile boolean running;

    public QueueService(DownloadWorker worker, AppProperties properties) {
        this.worker = worker;
        this.workerCount = properties.downloads().maxConcurrent();
        this.queue = new LinkedBlockingQueue<>(properties.downloads().maxQueueSize());
    }

    /** @throws RequestRejectedException com {@link ErrorCode#QUEUE_FULL} se a fila estiver cheia */
    public void enqueue(UUID jobId) {
        if (!running || !queue.offer(jobId)) {
            throw new RequestRejectedException(ErrorCode.QUEUE_FULL, "queue full or not running");
        }
    }

    /** Remove um job ainda não iniciado da fila. */
    public boolean remove(UUID jobId) {
        return queue.remove(jobId);
    }

    public int size() {
        return queue.size();
    }

    public int remainingCapacity() {
        return queue.remainingCapacity();
    }

    public int busyWorkers() {
        return busyWorkers.get();
    }

    public int workerCount() {
        return workerCount;
    }

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        for (int i = 1; i <= workerCount; i++) {
            Thread thread = Thread.ofPlatform().name("download-worker-" + i).daemon(false).unstarted(this::runWorker);
            workers.add(thread);
            thread.start();
        }
        log.info("Download queue started with {} worker(s)", workerCount);
    }

    @Override
    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;
        workers.forEach(Thread::interrupt);
        for (Thread thread : workers) {
            try {
                thread.join(10_000);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        workers.clear();
        log.info("Download queue stopped ({} job(s) left in queue)", queue.size());
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void runWorker() {
        while (running && !Thread.currentThread().isInterrupted()) {
            UUID jobId;
            try {
                jobId = queue.take();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }
            busyWorkers.incrementAndGet();
            try {
                worker.process(jobId);
            } catch (RuntimeException ex) {
                log.error("Worker failed unexpectedly for job {}", jobId, ex);
            } finally {
                busyWorkers.decrementAndGet();
            }
        }
    }
}
