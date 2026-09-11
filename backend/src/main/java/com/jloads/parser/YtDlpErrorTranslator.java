package com.jloads.parser;

import com.jloads.exception.ErrorCode;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Traduz mensagens de erro do yt-dlp em códigos de erro amigáveis. A saída bruta nunca é devolvida ao
 * usuário.
 */
@Component
public class YtDlpErrorTranslator {

    private record Rule(ErrorCode code, List<String> fragments) {
    }

    private static final List<Rule> RULES = List.of(
            new Rule(ErrorCode.DRM_PROTECTED, List.of("drm protected", "this video is drm", " drm")),
            new Rule(ErrorCode.VIDEO_PRIVATE, List.of("private video", "video is private")),
            new Rule(ErrorCode.COPYRIGHT_BLOCKED, List.of("copyright")),
            new Rule(ErrorCode.GEO_RESTRICTED, List.of("available in your country", "geo restrict",
                    "geo-restrict", "blocked it in your country")),
            new Rule(ErrorCode.SOURCE_BLOCKED, List.of("not a bot", "confirm you're not a bot",
                    "confirm you’re not a bot")),
            new Rule(ErrorCode.RESTRICTED_CONTENT, List.of("sign in to confirm your age", "age-restricted",
                    "age restricted", "inappropriate for some users", "members-only", "join this channel",
                    "requires payment", "youtube premium", "login required", "use --cookies")),
            new Rule(ErrorCode.LIVE_NOT_SUPPORTED, List.of("live event will begin", "premieres in",
                    "this live event", "is live", "is_live", "is upcoming")),
            new Rule(ErrorCode.FILE_TOO_LARGE, List.of("larger than max-filesize")),
            new Rule(ErrorCode.UNSUPPORTED_FORMAT, List.of("requested format is not available",
                    "no video formats found", "format is not available")),
            new Rule(ErrorCode.SOURCE_FORBIDDEN, List.of("http error 403", "403: forbidden", "403 forbidden")),
            new Rule(ErrorCode.SOURCE_RATE_LIMITED, List.of("http error 429", "too many requests")),
            new Rule(ErrorCode.VIDEO_UNAVAILABLE, List.of("video unavailable", "this video is unavailable",
                    "has been removed", "no longer available", "does not exist", "http error 404",
                    "incomplete youtube id", "video has been terminated")),
            new Rule(ErrorCode.NETWORK_ERROR, List.of("unable to download webpage", "timed out", "timeout",
                    "getaddrinfo", "connection reset", "connection refused", "network is unreachable",
                    "temporary failure in name resolution", "ssl", "urlopen error")),
            new Rule(ErrorCode.PROCESSING_FAILED, List.of("ffmpeg", "ffprobe", "postprocessing",
                    "conversion failed")));

    public ErrorCode translate(List<String> errorLines, ErrorCode fallback) {
        if (errorLines == null || errorLines.isEmpty()) {
            return fallback;
        }
        String haystack = String.join("\n", errorLines).toLowerCase(Locale.ROOT);
        return RULES.stream()
                .filter(rule -> rule.fragments().stream().anyMatch(haystack::contains))
                .map(Rule::code)
                .findFirst()
                .orElse(fallback);
    }
}
