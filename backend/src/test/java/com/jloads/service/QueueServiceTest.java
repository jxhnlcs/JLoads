package com.jloads.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.jloads.exception.ApplicationException;
import com.jloads.exception.ErrorCode;
import com.jloads.support.TestProperties;
import com.jloads.worker.DownloadWorker;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class QueueServiceTest {

    private final DownloadWorker worker = mock(DownloadWorker.class);
    private QueueService queue;

    @AfterEach
    void tearDown() {
        if (queue != null) {
            queue.stop();
        }
    }

    @Test
    void neverRunsMoreJobsThanConfiguredWorkers() throws Exception {
        queue = new QueueService(worker, TestProperties.create("storage", 2, 10, 5));
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger concurrent = new AtomicInteger();
        AtomicInteger maxConcurrent = new AtomicInteger();
        AtomicInteger processed = new AtomicInteger();
        doAnswer(invocation -> {
            maxConcurrent.accumulateAndGet(concurrent.incrementAndGet(), Math::max);
            release.await();
            concurrent.decrementAndGet();
            processed.incrementAndGet();
            return null;
        }).when(worker).process(any());

        queue.start();
        for (int i = 0; i < 5; i++) {
            queue.enqueue(UUID.randomUUID());
        }

        await().atMost(Duration.ofSeconds(5)).until(() -> queue.busyWorkers() == 2);
        assertThat(queue.size()).isEqualTo(3);
        Thread.sleep(200);
        assertThat(maxConcurrent.get()).isEqualTo(2);

        release.countDown();
        await().atMost(Duration.ofSeconds(5)).until(() -> processed.get() == 5);
        assertThat(maxConcurrent.get()).isEqualTo(2);
    }

    @Test
    void rejectsWhenQueueIsFull() {
        queue = new QueueService(worker, TestProperties.create("storage", 1, 2, 5));
        CountDownLatch block = new CountDownLatch(1);
        doAnswer(invocation -> {
            block.await();
            return null;
        }).when(worker).process(any());
        queue.start();

        queue.enqueue(UUID.randomUUID());
        await().atMost(Duration.ofSeconds(5)).until(() -> queue.busyWorkers() == 1);
        queue.enqueue(UUID.randomUUID());
        queue.enqueue(UUID.randomUUID());

        assertThatThrownBy(() -> queue.enqueue(UUID.randomUUID()))
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(ErrorCode.QUEUE_FULL);
        block.countDown();
    }

    @Test
    void removedJobsAreNeverProcessed() {
        queue = new QueueService(worker, TestProperties.create("storage", 1, 10, 5));
        CountDownLatch block = new CountDownLatch(1);
        AtomicInteger processed = new AtomicInteger();
        doAnswer(invocation -> {
            block.await();
            processed.incrementAndGet();
            return null;
        }).when(worker).process(any());
        queue.start();

        UUID running = UUID.randomUUID();
        UUID removed = UUID.randomUUID();
        UUID kept = UUID.randomUUID();
        queue.enqueue(running);
        await().atMost(Duration.ofSeconds(5)).until(() -> queue.busyWorkers() == 1);
        queue.enqueue(removed);
        queue.enqueue(kept);

        assertThat(queue.remove(removed)).isTrue();
        block.countDown();

        await().atMost(Duration.ofSeconds(5)).until(() -> processed.get() == 2);
        verify(worker, never()).process(removed);
        verify(worker).process(kept);
    }

    @Test
    void rejectsEnqueueWhenStopped() {
        queue = new QueueService(worker, TestProperties.create("storage", 1, 10, 5));
        assertThatThrownBy(() -> queue.enqueue(UUID.randomUUID())).isInstanceOf(ApplicationException.class);
    }

    @Test
    void workerFailureDoesNotKillWorkerThread() {
        queue = new QueueService(worker, TestProperties.create("storage", 1, 10, 5));
        AtomicInteger calls = new AtomicInteger();
        doAnswer(invocation -> {
            if (calls.incrementAndGet() == 1) {
                throw new IllegalStateException("boom");
            }
            return null;
        }).when(worker).process(any());
        queue.start();

        queue.enqueue(UUID.randomUUID());
        queue.enqueue(UUID.randomUUID());

        await().atMost(Duration.ofSeconds(5)).until(() -> calls.get() == 2);
    }
}
