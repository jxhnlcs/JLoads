package com.jloads.service;

import com.jloads.model.DownloadJob;
import com.jloads.model.enums.DownloadEventType;

/**
 * Porta de publicação de eventos de jobs. A implementação atual envia por WebSocket para as sessões do dono
 * do job; pode ser substituída por Redis pub/sub/STOMP em ambientes com múltiplas instâncias.
 */
public interface DownloadEventPublisher {

    void publish(DownloadEventType type, DownloadJob job);
}
