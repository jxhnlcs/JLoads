package com.jloads.dto;

import com.jloads.model.enums.DownloadQuality;
import com.jloads.model.enums.DownloadType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateDownloadRequest(
        @NotBlank(message = "Informe a URL do vídeo.")
        @Size(max = 2048, message = "URL muito longa.")
        String url,
        @NotNull(message = "Selecione o tipo de download.")
        DownloadType type,
        @NotNull(message = "Selecione a qualidade.")
        DownloadQuality quality) {
}
