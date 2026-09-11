package com.jloads.websocket;

import com.jloads.model.ClientId;
import com.jloads.web.ClientIdArgumentResolver;
import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * WebSocket nativo em {@code /ws?clientId={uuid}}. Cada sessão recebe apenas eventos dos jobs do seu
 * cliente. O canal é somente servidor → cliente; mensagens recebidas são tratadas apenas como keep-alive.
 */
@Slf4j
@Component
public class DownloadWebSocketHandler extends TextWebSocketHandler {

    static final int MAX_SESSIONS_PER_CLIENT = 10;
    static final int MAX_TOTAL_SESSIONS = 2_000;
    private static final int SEND_TIME_LIMIT_MS = 5_000;
    private static final int BUFFER_SIZE_LIMIT_BYTES = 512 * 1024;
    private static final String CLIENT_ATTRIBUTE = "clientId";

    private final Map<ClientId, Set<WebSocketSession>> sessionsByClient = new ConcurrentHashMap<>();
    private final AtomicInteger totalSessions = new AtomicInteger();

    @Override
    public void afterConnectionEstablished(WebSocketSession rawSession) throws IOException {
        Optional<ClientId> clientId = clientIdFrom(rawSession.getUri());
        if (clientId.isEmpty()) {
            rawSession.close(CloseStatus.POLICY_VIOLATION.withReason("invalid clientId"));
            return;
        }
        Set<WebSocketSession> sessions = sessionsByClient.computeIfAbsent(clientId.get(), k -> ConcurrentHashMap.newKeySet());
        if (sessions.size() >= MAX_SESSIONS_PER_CLIENT || totalSessions.get() >= MAX_TOTAL_SESSIONS) {
            rawSession.close(CloseStatus.SERVICE_OVERLOAD.withReason("too many connections"));
            return;
        }
        WebSocketSession session = new ConcurrentWebSocketSessionDecorator(
                rawSession, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT_BYTES);
        rawSession.getAttributes().put(CLIENT_ATTRIBUTE, clientId.get());
        rawSession.getAttributes().put(ConcurrentWebSocketSessionDecorator.class.getName(), session);
        sessions.add(session);
        totalSessions.incrementAndGet();
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        if ("ping".equals(message.getPayload())) {
            decorated(session).sendMessage(new TextMessage("pong"));
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws IOException {
        log.debug("WebSocket transport error: {}", exception.getMessage());
        session.close(CloseStatus.SERVER_ERROR);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession rawSession, CloseStatus status) {
        Object clientId = rawSession.getAttributes().get(CLIENT_ATTRIBUTE);
        Object session = rawSession.getAttributes().get(ConcurrentWebSocketSessionDecorator.class.getName());
        if (clientId instanceof ClientId id && session instanceof WebSocketSession decorated) {
            Set<WebSocketSession> sessions = sessionsByClient.get(id);
            if (sessions != null && sessions.remove(decorated)) {
                totalSessions.decrementAndGet();
                sessionsByClient.computeIfPresent(id, (k, v) -> v.isEmpty() ? null : v);
            }
        }
    }

    /** Envia uma mensagem a todas as sessões do cliente. Falhas de envio encerram apenas a sessão afetada. */
    public void sendToClient(ClientId clientId, String payload) {
        Set<WebSocketSession> sessions = sessionsByClient.get(clientId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        TextMessage message = new TextMessage(payload);
        for (WebSocketSession session : sessions) {
            try {
                if (session.isOpen()) {
                    session.sendMessage(message);
                }
            } catch (IOException | RuntimeException ex) {
                log.debug("Closing WebSocket session after send failure: {}", ex.getMessage());
                closeQuietly(session);
            }
        }
    }

    public int sessionCount() {
        return totalSessions.get();
    }

    private static WebSocketSession decorated(WebSocketSession rawSession) {
        Object session = rawSession.getAttributes().get(ConcurrentWebSocketSessionDecorator.class.getName());
        return session instanceof WebSocketSession decorated ? decorated : rawSession;
    }

    private static Optional<ClientId> clientIdFrom(URI uri) {
        if (uri == null) {
            return Optional.empty();
        }
        String value = UriComponentsBuilder.fromUri(uri).build().getQueryParams().getFirst("clientId");
        return ClientIdArgumentResolver.parse(value);
    }

    private static void closeQuietly(WebSocketSession session) {
        try {
            session.close(CloseStatus.SESSION_NOT_RELIABLE);
        } catch (IOException | RuntimeException ignored) {
            // sessão já encerrada
        }
    }
}
