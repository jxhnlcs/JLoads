package com.jloads.process;

import jakarta.annotation.PreDestroy;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Mantém os processos externos associados a cada job, permitindo cancelamento real. */
@Slf4j
@Component
public class ProcessRegistry {

    private final ConcurrentMap<UUID, RunningProcess> running = new ConcurrentHashMap<>();

    public RunningProcess register(UUID jobId, Process process) {
        RunningProcess entry = new RunningProcess(process);
        RunningProcess previous = running.put(jobId, entry);
        if (previous != null) {
            previous.terminate(RunningProcess.TerminationReason.CANCELLED);
        }
        return entry;
    }

    public void unregister(UUID jobId, RunningProcess entry) {
        running.remove(jobId, entry);
    }

    public boolean isRunning(UUID jobId) {
        return running.containsKey(jobId);
    }

    public int runningCount() {
        return running.size();
    }

    /** @return {@code true} se havia um processo em execução para o job */
    public boolean terminate(UUID jobId, RunningProcess.TerminationReason reason) {
        RunningProcess entry = running.get(jobId);
        if (entry == null) {
            return false;
        }
        entry.terminate(reason);
        return true;
    }

    @PreDestroy
    public void terminateAll() {
        if (!running.isEmpty()) {
            log.info("Terminating {} running external process(es) on shutdown", running.size());
        }
        running.values().forEach(entry -> entry.terminate(RunningProcess.TerminationReason.SHUTDOWN));
    }
}
