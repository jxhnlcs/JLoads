package com.jloads.parser;

import static org.assertj.core.api.Assertions.assertThat;

import com.jloads.exception.ErrorCode;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class YtDlpErrorTranslatorTest {

    private final YtDlpErrorTranslator translator = new YtDlpErrorTranslator();

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "ERROR: unable to download video data: HTTP Error 403: Forbidden | SOURCE_FORBIDDEN",
            "ERROR: [youtube] abc: This video is unavailable | VIDEO_UNAVAILABLE",
            "ERROR: [youtube] abc: Private video. Sign in if you've been granted access | VIDEO_PRIVATE",
            "ERROR: [youtube] abc: Sign in to confirm your age. This video may be inappropriate | RESTRICTED_CONTENT",
            "ERROR: [youtube] abc: Sign in to confirm you’re not a bot. Use --cookies | SOURCE_BLOCKED",
            "ERROR: [youtube] abc: This live event will begin in 3 hours. | LIVE_NOT_SUPPORTED",
            "ERROR: [youtube] abc: Video unavailable. This video contains content from X who has blocked it on copyright grounds | COPYRIGHT_BLOCKED",
            "ERROR: [youtube] abc: The uploader has not made this video available in your country | GEO_RESTRICTED",
            "ERROR: [youtube] abc: Requested format is not available | UNSUPPORTED_FORMAT",
            "ERROR: unable to download video data: HTTP Error 429: Too Many Requests | SOURCE_RATE_LIMITED",
            "ERROR: [youtube] abc: Unable to download webpage: timed out | NETWORK_ERROR",
            "ERROR: This video is DRM protected | DRM_PROTECTED",
            "ERROR: Postprocessing: ffprobe and ffmpeg not found | PROCESSING_FAILED",
            "ERROR: something completely unexpected | DOWNLOAD_FAILED"
    })
    void translatesKnownErrors(String line, ErrorCode expected) {
        assertThat(translator.translate(List.of(line), ErrorCode.DOWNLOAD_FAILED)).isEqualTo(expected);
    }

    @org.junit.jupiter.api.Test
    void usesFallbackWithoutLines() {
        assertThat(translator.translate(List.of(), ErrorCode.ANALYSIS_FAILED)).isEqualTo(ErrorCode.ANALYSIS_FAILED);
        assertThat(translator.translate(null, ErrorCode.ANALYSIS_FAILED)).isEqualTo(ErrorCode.ANALYSIS_FAILED);
    }
}
