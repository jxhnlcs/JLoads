package com.jloads.process;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExternalBinariesTest {

    @TempDir
    Path directory;

    @Test
    void usesConfiguredFileWhenItExists() throws Exception {
        Path executable = Files.writeString(directory.resolve("yt-dlp"), "#!/bin/sh");

        assertThat(ExternalBinaries.locate(executable.toString(), "yt-dlp")).contains(executable.toAbsolutePath().normalize());
    }

    @Test
    void returnsEmptyWhenProgramExistsNowhere() {
        String missing = directory.resolve("programa-que-nao-existe-jloads").toString();

        assertThat(ExternalBinaries.locate(missing, "programa-que-nao-existe-jloads")).isEmpty();
        assertThat(ExternalBinaries.findOnPath("programa-que-nao-existe-jloads")).isEmpty();
    }

    @Test
    void ignoresBlankNames() {
        assertThat(ExternalBinaries.findOnPath(" ")).isEmpty();
        assertThat(ExternalBinaries.findOnPath(null)).isEmpty();
    }
}
