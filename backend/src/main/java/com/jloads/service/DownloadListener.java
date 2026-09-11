package com.jloads.service;

import com.jloads.model.DownloadProgress;

/** Callbacks emitidos pelo {@link YtDlpService} durante um download. */
public interface DownloadListener {

    void onProgress(DownloadProgress progress);

    void onProcessingStarted(String postprocessor);

    /** Consultado antes e durante a execução para evitar iniciar/continuar processos de jobs cancelados. */
    boolean isCancelled();
}
