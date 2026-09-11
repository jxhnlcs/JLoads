package com.jloads.exception;

import org.springframework.http.HttpStatus;

/** Códigos de erro públicos, com status HTTP e mensagem amigável padrão. */
public enum ErrorCode {
    INVALID_URL(HttpStatus.BAD_REQUEST, "URL inválida."),
    UNSUPPORTED_URL(HttpStatus.BAD_REQUEST, "Apenas links de vídeos do YouTube são suportados."),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "Requisição inválida."),
    INVALID_CLIENT_ID(HttpStatus.BAD_REQUEST, "Identificador de cliente ausente ou inválido."),
    BATCH_TOO_LARGE(HttpStatus.BAD_REQUEST, "Muitos links enviados de uma só vez."),
    UNSUPPORTED_FORMAT(HttpStatus.UNPROCESSABLE_CONTENT, "A opção selecionada não está disponível para este conteúdo."),

    VIDEO_UNAVAILABLE(HttpStatus.UNPROCESSABLE_CONTENT, "Este conteúdo não está disponível."),
    VIDEO_PRIVATE(HttpStatus.UNPROCESSABLE_CONTENT, "Este conteúdo é privado e não pode ser baixado."),
    RESTRICTED_CONTENT(HttpStatus.UNPROCESSABLE_CONTENT,
            "Este conteúdo possui acesso restrito (login, idade ou assinatura) e não pode ser baixado."),
    DRM_PROTECTED(HttpStatus.UNPROCESSABLE_CONTENT, "Este conteúdo é protegido por DRM e não pode ser baixado."),
    GEO_RESTRICTED(HttpStatus.UNPROCESSABLE_CONTENT, "Este conteúdo não está disponível na região do servidor."),
    COPYRIGHT_BLOCKED(HttpStatus.UNPROCESSABLE_CONTENT, "Este conteúdo foi bloqueado por questões de direitos autorais."),
    LIVE_NOT_SUPPORTED(HttpStatus.UNPROCESSABLE_CONTENT, "Transmissões ao vivo ou agendadas não são suportadas."),
    MEDIA_TOO_LONG(HttpStatus.UNPROCESSABLE_CONTENT, "O conteúdo excede a duração máxima permitida."),
    FILE_TOO_LARGE(HttpStatus.UNPROCESSABLE_CONTENT, "O arquivo excede o tamanho máximo permitido."),

    SOURCE_FORBIDDEN(HttpStatus.BAD_GATEWAY,
            "Não foi possível baixar este conteúdo. O servidor de origem recusou o acesso ao formato solicitado. "
                    + "Tente outra opção disponível."),
    SOURCE_RATE_LIMITED(HttpStatus.SERVICE_UNAVAILABLE,
            "O servidor de origem está limitando as requisições. Tente novamente em alguns minutos."),
    SOURCE_BLOCKED(HttpStatus.SERVICE_UNAVAILABLE,
            "O servidor de origem bloqueou temporariamente o acesso. Tente novamente mais tarde."),
    NETWORK_ERROR(HttpStatus.BAD_GATEWAY, "Falha de comunicação com o servidor de origem. Tente novamente."),
    ANALYSIS_FAILED(HttpStatus.BAD_GATEWAY, "Não foi possível analisar este conteúdo. Tente novamente."),
    ANALYSIS_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "A análise demorou mais que o esperado. Tente novamente."),

    DOWNLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "Não foi possível concluir o download."),
    DOWNLOAD_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "O download excedeu o tempo máximo permitido."),
    DOWNLOAD_CANCELLED(HttpStatus.CONFLICT, "O download foi cancelado."),
    PROCESSING_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "Falha ao processar o arquivo baixado."),
    JOB_INTERRUPTED(HttpStatus.INTERNAL_SERVER_ERROR, "O download foi interrompido inesperadamente. Tente novamente."),
    QUEUE_TIMEOUT(HttpStatus.SERVICE_UNAVAILABLE,
            "O download aguardou tempo demais na fila e foi descartado. Tente novamente."),

    JOB_NOT_FOUND(HttpStatus.NOT_FOUND, "Download não encontrado."),
    JOB_NOT_CANCELLABLE(HttpStatus.CONFLICT, "Este download já foi finalizado e não pode ser cancelado."),
    FILE_NOT_AVAILABLE(HttpStatus.NOT_FOUND, "O arquivo ainda não está disponível."),
    FILE_EXPIRED(HttpStatus.GONE, "O arquivo expirou e foi removido do servidor. Faça o download novamente."),

    QUEUE_FULL(HttpStatus.SERVICE_UNAVAILABLE, "A fila de downloads está cheia. Tente novamente em instantes."),
    TOO_MANY_ACTIVE_JOBS(HttpStatus.TOO_MANY_REQUESTS,
            "Você atingiu o limite de downloads simultâneos. Aguarde a conclusão dos atuais."),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Muitas requisições. Aguarde um momento e tente novamente."),
    SERVER_BUSY(HttpStatus.SERVICE_UNAVAILABLE, "O servidor está ocupado. Tente novamente em instantes."),
    STORAGE_FULL(HttpStatus.SERVICE_UNAVAILABLE, "O servidor está sem espaço temporário. Tente novamente mais tarde."),
    ENGINE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE,
            "O yt-dlp não foi encontrado ou não pôde ser iniciado. Instale-o seguindo o aviso no topo da página "
                    + "e reinicie o JLoads."),

    NOT_FOUND(HttpStatus.NOT_FOUND, "Recurso não encontrado."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "Método não suportado."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Ocorreu um erro inesperado. Tente novamente.");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
