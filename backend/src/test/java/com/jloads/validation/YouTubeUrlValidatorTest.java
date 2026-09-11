package com.jloads.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jloads.exception.ApplicationException;
import com.jloads.exception.InvalidUrlException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class YouTubeUrlValidatorTest {

    private final YouTubeUrlValidator validator = new YouTubeUrlValidator();

    @ParameterizedTest
    @ValueSource(strings = {
            "https://www.youtube.com/watch?v=jNQXAC9IVRw",
            "https://youtube.com/watch?v=jNQXAC9IVRw&t=10s",
            "https://m.youtube.com/watch?feature=share&v=jNQXAC9IVRw",
            "https://music.youtube.com/watch?v=jNQXAC9IVRw&list=RDAMVM",
            "http://www.youtube.com/watch?v=jNQXAC9IVRw",
            "https://youtu.be/jNQXAC9IVRw",
            "https://youtu.be/jNQXAC9IVRw?si=abcdef",
            "https://www.youtube.com/shorts/jNQXAC9IVRw",
            "https://www.youtube.com/embed/jNQXAC9IVRw",
            "https://www.youtube.com/live/jNQXAC9IVRw?feature=share",
            "www.youtube.com/watch?v=jNQXAC9IVRw",
            "  https://WWW.YOUTUBE.COM/watch?v=jNQXAC9IVRw  ",
            "https://www.youtube.com:443/watch?v=jNQXAC9IVRw"
    })
    void acceptsSupportedForms(String url) {
        var result = validator.validate(url);

        assertThat(result.videoId()).isEqualTo("jNQXAC9IVRw");
        assertThat(result.canonicalUrl()).isEqualTo("https://www.youtube.com/watch?v=jNQXAC9IVRw");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://evil.com/watch?v=jNQXAC9IVRw",
            "https://youtube.com.evil.com/watch?v=jNQXAC9IVRw",
            "https://evilyoutube.com/watch?v=jNQXAC9IVRw",
            "https://www.youtube.com@evil.com/watch?v=jNQXAC9IVRw",
            "https://user:pass@www.youtube.com/watch?v=jNQXAC9IVRw",
            "ftp://www.youtube.com/watch?v=jNQXAC9IVRw",
            "file:///etc/passwd",
            "javascript:alert(1)",
            "https://www.youtube.com:8080/watch?v=jNQXAC9IVRw",
            "https://127.0.0.1/watch?v=jNQXAC9IVRw",
            "https://169.254.169.254/latest/meta-data",
            "https://www.youtube.com/playlist?list=PL123",
            "https://www.youtube.com/watch?v=short",
            "https://www.youtube.com/watch?v=jNQXAC9IVRw;rm",
            "https://www.youtube.com/watch?v=--exec=calc",
            "https://www.youtube.com/@channel",
            "https://youtu.be/"
    })
    void rejectsUnsupportedOrMalicious(String url) {
        assertThatThrownBy(() -> validator.validate(url)).isInstanceOf(InvalidUrlException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "https://www.youtube.com/watch?v=jNQXAC9IVRw\n--exec calc",
            "https://www.youtube.com/watch? v=jNQXAC9IVRw", "https://[::1"})
    void rejectsBlankMalformedOrControlCharacters(String url) {
        assertThatThrownBy(() -> validator.validate(url))
                .isInstanceOf(InvalidUrlException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode().name())
                .isIn("INVALID_URL", "UNSUPPORTED_URL");
    }

    @org.junit.jupiter.api.Test
    void rejectsOversizedUrl() {
        String url = "https://www.youtube.com/watch?v=jNQXAC9IVRw&x=" + "a".repeat(YouTubeUrlValidator.MAX_URL_LENGTH);
        assertThatThrownBy(() -> validator.validate(url)).isInstanceOf(InvalidUrlException.class);
    }
}
