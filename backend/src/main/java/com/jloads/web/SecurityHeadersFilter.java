package com.jloads.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Headers de segurança. A API (JSON e arquivos) recebe uma política que não permite executar nada; a interface
 * embutida no jar recebe a política mínima necessária para o Angular funcionar.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class SecurityHeadersFilter extends OncePerRequestFilter {

    private static final String API_POLICY = "default-src 'none'; frame-ancestors 'none'; sandbox";
    private static final String APP_POLICY = "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
            + "img-src 'self' data: https://i.ytimg.com https://*.ytimg.com https://*.ggpht.com https://*.googleusercontent.com; "
            + "connect-src 'self' ws: wss:; font-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'; "
            + "frame-ancestors 'none'";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String uri = request.getRequestURI();
        boolean api = uri.startsWith("/api/") || uri.startsWith("/actuator");

        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Content-Security-Policy", api ? API_POLICY : APP_POLICY);
        if (api) {
            response.setHeader("Cross-Origin-Resource-Policy", "same-origin");
            response.setHeader("Cache-Control", "no-store");
        }
        chain.doFilter(request, response);
    }
}
