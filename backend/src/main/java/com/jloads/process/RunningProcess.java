package com.jloads.process;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** Processo externo em execução, com o motivo pelo qual foi encerrado à força (se foi). */
public final class RunningProcess {

    public enum TerminationReason {
        CANCELLED,
        TIMEOUT,
        SIZE_LIMIT,
        SHUTDOWN
    }

    private final Process process;
    private final AtomicReference<TerminationReason> terminationReason = new AtomicReference<>();

    public RunningProcess(Process process) {
        this.process = Objects.requireNonNull(process, "process");
    }

    public Process process() {
        return process;
    }

    public TerminationReason terminationReason() {
        return terminationReason.get();
    }

    /** Encerra o processo e todos os descendentes (ex.: bootloader do yt-dlp e FFmpeg). */
    public void terminate(TerminationReason reason) {
        terminationReason.compareAndSet(null, reason);
        killTree(process);
    }

    public static void killTree(Process process) {
        List<ProcessHandle> descendants = process.descendants().toList();
        descendants.forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
    }
}
