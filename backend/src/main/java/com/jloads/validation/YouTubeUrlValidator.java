package com.jloads.validation;

import com.jloads.exception.InvalidUrlException;
import com.jloads.model.VideoUrl;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Valida URLs do YouTube por allowlist estrita de esquema, host, porta e formato de caminho. O resultado é
 * apenas o ID do vídeo — nada da URL original é repassado adiante, o que neutraliza SSRF e injeção de
 * argumentos no processo externo.
 */
@Component
public class YouTubeUrlValidator {

    public static final int MAX_URL_LENGTH = 2048;

    private static final Set<String> SCHEMES = Set.of("http", "https");
    private static final Set<String> WATCH_HOSTS = Set.of(
            "youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com");
    private static final Set<String> SHORT_HOSTS = Set.of("youtu.be", "www.youtu.be");
    private static final Set<String> ID_PATH_PREFIXES = Set.of("shorts", "embed", "live", "v");

    public VideoUrl validate(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new InvalidUrlException("blank url");
        }
        String candidate = rawUrl.strip();
        if (candidate.length() > MAX_URL_LENGTH) {
            throw new InvalidUrlException("url too long");
        }
        if (candidate.chars().anyMatch(ch -> Character.isWhitespace(ch) || Character.isISOControl(ch))) {
            throw new InvalidUrlException("url contains whitespace/control characters");
        }
        if (!candidate.contains("://")) {
            candidate = "https://" + candidate;
        }

        URI uri = parse(candidate);
        String scheme = Optional.ofNullable(uri.getScheme()).map(s -> s.toLowerCase(Locale.ROOT)).orElse("");
        if (!SCHEMES.contains(scheme)) {
            throw InvalidUrlException.unsupported("scheme not allowed");
        }
        if (uri.getRawUserInfo() != null) {
            throw InvalidUrlException.unsupported("user info not allowed");
        }
        if (uri.getPort() != -1 && uri.getPort() != 80 && uri.getPort() != 443) {
            throw InvalidUrlException.unsupported("port not allowed");
        }
        String host = Optional.ofNullable(uri.getHost()).map(h -> h.toLowerCase(Locale.ROOT)).orElse("");

        String videoId;
        if (SHORT_HOSTS.contains(host)) {
            videoId = firstPathSegment(uri.getRawPath(), 0);
        } else if (WATCH_HOSTS.contains(host)) {
            videoId = extractFromWatchHost(uri);
        } else {
            throw InvalidUrlException.unsupported("host not allowed");
        }

        if (!VideoUrl.isValidVideoId(videoId)) {
            throw InvalidUrlException.unsupported("video id not found");
        }
        return new VideoUrl(videoId);
    }

    private static URI parse(String candidate) {
        try {
            return new URI(candidate);
        } catch (URISyntaxException ex) {
            throw new InvalidUrlException("malformed url");
        }
    }

    private static String extractFromWatchHost(URI uri) {
        String path = Optional.ofNullable(uri.getRawPath()).orElse("");
        if (path.equals("/watch") || path.equals("/watch/")) {
            return queryParameter(uri.getRawQuery(), "v");
        }
        String prefix = firstPathSegment(path, 0);
        if (prefix != null && ID_PATH_PREFIXES.contains(prefix)) {
            return firstPathSegment(path, 1);
        }
        return null;
    }

    private static String firstPathSegment(String rawPath, int index) {
        if (rawPath == null) {
            return null;
        }
        String[] segments = rawPath.replaceFirst("^/+", "").split("/");
        return index < segments.length && !segments[index].isEmpty() ? segments[index] : null;
    }

    private static String queryParameter(String rawQuery, String name) {
        if (rawQuery == null) {
            return null;
        }
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(name)) {
                try {
                    return URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
                } catch (IllegalArgumentException ex) {
                    return null;
                }
            }
        }
        return null;
    }
}
