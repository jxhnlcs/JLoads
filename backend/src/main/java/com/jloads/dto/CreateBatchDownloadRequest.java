package com.jloads.dto;

import com.jloads.model.enums.DownloadQuality;
import com.jloads.model.enums.DownloadType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** O limite efetivo de itens vem de {@code app.downloads.max-batch-size}; o {@code @Size} é só um teto de segurança. */
public record CreateBatchDownloadRequest(
        @NotEmpty(message = "Informe ao menos um link.")
        @Size(max = 50, message = "Muitos links enviados de uma só vez.")
        List<@NotBlank(message = "Há um link vazio na lista.") @Size(max = 2048, message = "URL muito longa.") String> urls,
        @NotNull(message = "Selecione o tipo de download.")
        DownloadType type,
        @NotNull(message = "Selecione a qualidade.")
        DownloadQuality quality) {
}
