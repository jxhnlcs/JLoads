package com.jloads.web;

import com.jloads.exception.ErrorCode;
import com.jloads.exception.RequestRejectedException;
import com.jloads.model.ClientId;
import java.util.Optional;
import java.util.UUID;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** Resolve parâmetros {@link ClientId} a partir do header {@value #HEADER}. */
public class ClientIdArgumentResolver implements HandlerMethodArgumentResolver {

    public static final String HEADER = "X-Client-Id";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return ClientId.class.equals(parameter.getParameterType());
    }

    @Override
    public ClientId resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                    NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        return parse(webRequest.getHeader(HEADER))
                .orElseThrow(() -> new RequestRejectedException(ErrorCode.INVALID_CLIENT_ID, "missing/invalid client id"));
    }

    public static Optional<ClientId> parse(String raw) {
        if (raw == null || raw.length() != 36) {
            return Optional.empty();
        }
        try {
            return Optional.of(new ClientId(UUID.fromString(raw)));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
