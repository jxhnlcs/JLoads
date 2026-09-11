package com.jloads.model;

/**
 * Progresso agregado de um download.
 *
 * @param percentage          0 a 100
 * @param downloadedBytes     bytes já baixados
 * @param totalBytes          total estimado em bytes; 0 quando desconhecido
 * @param speed               velocidade formatada (ex.: "4.2 MB/s"); {@code null} quando desconhecida
 * @param eta                 tempo restante formatado (ex.: "00:08"); {@code null} quando desconhecido
 * @param speedBytesPerSecond velocidade numérica (permite somar downloads); 0 quando desconhecida
 */
public record DownloadProgress(int percentage, long downloadedBytes, long totalBytes, String speed, String eta,
                               long speedBytesPerSecond) {

    public DownloadProgress {
        percentage = Math.clamp(percentage, 0, 100);
        downloadedBytes = Math.max(0, downloadedBytes);
        totalBytes = Math.max(0, totalBytes);
        speedBytesPerSecond = Math.max(0, speedBytesPerSecond);
    }
}
