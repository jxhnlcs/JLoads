package com.jloads.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AnalyzeVideoRequest(
        @NotBlank(message = "Informe a URL do vídeo.")
        @Size(max = 2048, message = "URL muito longa.")
        String url) {
}
