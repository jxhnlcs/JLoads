package com.jloads.web;

import com.jloads.config.AppProperties;
import com.jloads.dto.ErrorResponse;
import com.jloads.exception.ErrorCode;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Limita requisições por IP em três categorias: geral da API, análise (que executa o yt-dlp) e criação de
 * downloads. O IP vem de {@link HttpServletRequest#getRemoteAddr()}; atrás de proxy, habilite
 * {@code server.forward-headers-strategy=native} para que apenas proxies confiáveis alterem esse valor.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    private final boolean enabled;
    private final RateLimiter apiLimiter;
    private final RateLimiter analyzeLimiter;
    private final RateLimiter downloadLimiter;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    public RateLimitFilter(AppProperties properties, ObjectMapper objectMapper, MeterRegistry meterRegistry, Clock clock) {
        AppProperties.RateLimit config = properties.rateLimit();
        this.enabled = config.enabled();
        this.apiLimiter = new RateLimiter(config.apiRequestsPerMinute(), clock);
        this.analyzeLimiter = new RateLimiter(config.analyzeRequestsPerMinute(), clock);
        this.downloadLimiter = new RateLimiter(config.downloadRequestsPerMinute(), clock);
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled || !request.getRequestURI().startsWith("/api/")
                || HttpMethod.OPTIONS.matches(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String key = request.getRemoteAddr();
        RateLimiter.Decision decision = apiLimiter.tryAcquire(key);
        String category = "api";
        if (decision.allowed() && isPost(request, "/api/videos/analyze")) {
            decision = analyzeLimiter.tryAcquire(key);
            category = "analyze";
        } else if (decision.allowed()
                && (isPost(request, "/api/downloads") || isPost(request, "/api/downloads/batch"))) {
            decision = downloadLimiter.tryAcquire(key);
            category = "download";
        }

        if (decision.allowed()) {
            chain.doFilter(request, response);
            return;
        }
        meterRegistry.counter("jloads.ratelimit.rejected", "category", category).increment();
        reject(response, decision.retryAfterSeconds());
    }

    @Scheduled(fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
    void evictIdleBuckets() {
        apiLimiter.evictFullBuckets();
        analyzeLimiter.evictFullBuckets();
        downloadLimiter.evictFullBuckets();
    }

    private static boolean isPost(HttpServletRequest request, String path) {
        return HttpMethod.POST.matches(request.getMethod()) && path.equals(request.getRequestURI());
    }

    private void reject(HttpServletResponse response, long retryAfterSeconds) throws IOException {
        ErrorCode code = ErrorCode.RATE_LIMITED;
        ErrorResponse body = new ErrorResponse(Instant.now(clock), code.httpStatus().value(), code.name(),
                code.defaultMessage(), MDC.get(CorrelationIdFilter.MDC_KEY));
        response.setStatus(code.httpStatus().value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
