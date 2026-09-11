package com.jloads.parser;

/** Evento estruturado extraído de uma linha de saída do yt-dlp. */
public sealed interface YtDlpOutputEvent {

    /** Linha do template de progresso de download (valores brutos; {@code null} quando indisponíveis). */
    record Progress(
            String status,
            Long downloadedBytes,
            Long totalBytes,
            Long totalBytesEstimate,
            Double speedBytesPerSecond,
            Long etaSeconds) implements YtDlpOutputEvent {

        public boolean finished() {
            return "finished".equals(status);
        }

        public long bestTotal() {
            if (totalBytes != null && totalBytes > 0) {
                return totalBytes;
            }
            return totalBytesEstimate != null && totalBytesEstimate > 0 ? totalBytesEstimate : 0;
        }
    }

    /** Linha do template de pós-processamento (merge, extração de áudio, etc.). */
    record PostProcess(String status, String postprocessor) implements YtDlpOutputEvent {

        public boolean started() {
            return "started".equals(status);
        }

        /** "MoveFiles" sempre ocorre ao final e não representa processamento real da mídia. */
        public boolean isMediaProcessing() {
            return postprocessor != null && !"MoveFiles".equals(postprocessor);
        }
    }
}
