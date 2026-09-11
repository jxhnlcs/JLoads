package com.jloads.parser;

import com.jloads.exception.ErrorCode;
import com.jloads.exception.VideoAnalysisException;
import com.jloads.model.MediaFormat;
import com.jloads.model.VideoMetadata;
import com.jloads.model.VideoUrl;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Converte o JSON de {@code yt-dlp --dump-single-json} em {@link VideoMetadata}. */
@Component
public class YtDlpMetadataParser {

    private static final int MAX_TITLE_LENGTH = 300;
    private static final int MAX_CHANNEL_LENGTH = 150;
    private static final Set<String> LIVE_STATUSES = Set.of("is_live", "is_upcoming", "post_live");
    private static final Set<String> THUMBNAIL_HOST_SUFFIXES = Set.of(".ytimg.com", ".ggpht.com", ".googleusercontent.com");

    private final ObjectMapper objectMapper;

    public YtDlpMetadataParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public VideoMetadata parse(String json, VideoUrl requested) {
        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (JacksonException ex) {
            throw new VideoAnalysisException(ErrorCode.ANALYSIS_FAILED, "invalid metadata json", ex);
        }
        if (root == null || !root.isObject()) {
            throw new VideoAnalysisException(ErrorCode.ANALYSIS_FAILED, "metadata is not an object");
        }
        String type = text(root, "_type");
        if (type != null && !"video".equals(type)) {
            throw new VideoAnalysisException(ErrorCode.UNSUPPORTED_FORMAT, "unexpected metadata type: " + type);
        }
        String id = text(root, "id");
        if (id == null || !id.equals(requested.videoId())) {
            throw new VideoAnalysisException(ErrorCode.ANALYSIS_FAILED, "metadata id mismatch");
        }

        String liveStatus = text(root, "live_status");
        boolean live = root.path("is_live").asBoolean(false)
                || (liveStatus != null && LIVE_STATUSES.contains(liveStatus));

        return new VideoMetadata(
                id,
                limit(firstNonBlank(text(root, "title"), "Sem título"), MAX_TITLE_LENGTH),
                limit(firstNonBlank(text(root, "channel"), text(root, "uploader")), MAX_CHANNEL_LENGTH),
                positiveLong(root, "duration"),
                safeThumbnail(text(root, "thumbnail"), requested),
                live,
                parseFormats(root.path("formats")));
    }

    private static List<MediaFormat> parseFormats(JsonNode formatsNode) {
        List<MediaFormat> formats = new ArrayList<>();
        if (!formatsNode.isArray()) {
            return formats;
        }
        for (JsonNode node : formatsNode) {
            String vcodec = text(node, "vcodec");
            String acodec = text(node, "acodec");
            Integer height = positiveLong(node, "height") == null ? null : positiveLong(node, "height").intValue();
            boolean hasVideo = vcodec != null ? !"none".equals(vcodec) : (acodec == null && height != null);
            boolean hasAudio = acodec != null && !"none".equals(acodec);
            if (!hasVideo && !hasAudio) {
                continue; // storyboards e formatos sem mídia útil
            }
            Long size = positiveLong(node, "filesize");
            if (size == null) {
                size = positiveLong(node, "filesize_approx");
            }
            formats.add(new MediaFormat(
                    text(node, "format_id"),
                    text(node, "ext"),
                    hasVideo,
                    hasAudio,
                    hasVideo ? height : null,
                    size,
                    node.path("has_drm").asBoolean(false)));
        }
        return formats;
    }

    private static String safeThumbnail(String candidate, VideoUrl requested) {
        if (candidate == null) {
            return requested.fallbackThumbnailUrl();
        }
        try {
            URI uri = URI.create(candidate);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            boolean allowedHost = THUMBNAIL_HOST_SUFFIXES.stream().anyMatch(host::endsWith);
            if ("https".equalsIgnoreCase(uri.getScheme()) && allowedHost && uri.getRawUserInfo() == null) {
                return candidate;
            }
        } catch (IllegalArgumentException ignored) {
            // fallback abaixo
        }
        return requested.fallbackThumbnailUrl();
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull() || !value.isValueNode()) {
            return null;
        }
        String text = value.asString().strip();
        return text.isEmpty() ? null : text;
    }

    private static Long positiveLong(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isNumber()) {
            return null;
        }
        double number = value.asDouble();
        return Double.isFinite(number) && number > 0 ? Math.round(number) : null;
    }

    private static String firstNonBlank(String first, String second) {
        return first != null ? first : second;
    }

    private static String limit(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max).strip();
    }
}
