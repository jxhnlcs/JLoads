package com.jloads.process;

import com.jloads.config.AppProperties;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Localiza os programas externos usados pelo JLoads. Para yt-dlp e FFmpeg, tenta primeiro o caminho configurado
 * (padrão {@code ./bin/}) e depois o PATH do sistema. Para o runtime JavaScript exigido pelo YouTube, usa o valor
 * configurado ou detecta Deno (padrão do yt-dlp) e, na falta dele, Node.
 */
@Slf4j
@Component
public class ExternalBinaries {

    private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

    private final Optional<Path> ytDlpPath;
    private final String ytDlpCommand;
    private final Optional<Path> ffmpegLocation;
    private final Optional<String> jsRuntimeArgument;
    private final String jsRuntimeName;

    public ExternalBinaries(AppProperties properties) {
        String configuredYtDlp = properties.ytdlp().executable().strip();
        this.ytDlpPath = locate(configuredYtDlp, "yt-dlp");
        this.ytDlpCommand = ytDlpPath.map(Path::toString).orElse(configuredYtDlp);
        this.ffmpegLocation = locate(properties.ffmpeg().executable(), "ffmpeg");

        String configuredRuntime = properties.ytdlp().jsRuntime();
        if (configuredRuntime != null && !configuredRuntime.isBlank()) {
            this.jsRuntimeArgument = Optional.of(configuredRuntime.strip());
            this.jsRuntimeName = configuredRuntime.strip();
        } else if (findOnPath("deno").isPresent()) {
            this.jsRuntimeArgument = Optional.empty(); // Deno já é o padrão do yt-dlp
            this.jsRuntimeName = "deno";
        } else if (findOnPath("node").isPresent()) {
            this.jsRuntimeArgument = Optional.of("node");
            this.jsRuntimeName = "node";
        } else {
            this.jsRuntimeArgument = Optional.empty();
            this.jsRuntimeName = "nenhum";
        }
        logDetectedSetup();
    }

    public String ytDlpCommand() {
        return ytDlpCommand;
    }

    public boolean ytDlpAvailable() {
        return ytDlpPath.isPresent();
    }

    /** Caminho do FFmpeg a ser informado ao yt-dlp; vazio quando não foi encontrado. */
    public Optional<Path> ffmpegLocation() {
        return ffmpegLocation;
    }

    /** Valor para {@code --js-runtimes}; vazio quando o padrão do yt-dlp (Deno) deve ser usado. */
    public Optional<String> jsRuntimeArgument() {
        return jsRuntimeArgument;
    }

    public String jsRuntimeName() {
        return jsRuntimeName;
    }

    private void logDetectedSetup() {
        ytDlpPath.ifPresentOrElse(
                path -> log.info("yt-dlp encontrado: {}", path),
                () -> log.warn("yt-dlp não encontrado. Instale-o (https://github.com/yt-dlp/yt-dlp#installation), "
                        + "coloque-o na pasta bin/ ou defina YTDLP_EXECUTABLE."));
        ffmpegLocation.ifPresentOrElse(
                path -> log.info("FFmpeg encontrado: {}", path),
                () -> log.warn("FFmpeg não encontrado: conversão para MP3 e junção de vídeo com áudio vão falhar. "
                        + "Instale-o, coloque-o na pasta bin/ ou defina FFMPEG_EXECUTABLE."));
        if ("nenhum".equals(jsRuntimeName)) {
            log.warn("Nenhum runtime JavaScript (Deno ou Node.js) encontrado: o YouTube pode recusar downloads. "
                    + "Instale o Deno ou defina YTDLP_JS_RUNTIME.");
        } else {
            log.info("Runtime JavaScript para o yt-dlp: {}", jsRuntimeName);
        }
    }

    /** Caminho configurado (relativo ao diretório de trabalho) ou, se não existir, o mesmo programa no PATH. */
    static Optional<Path> locate(String configured, String defaultName) {
        if (configured == null || configured.isBlank()) {
            return findOnPath(defaultName);
        }
        Optional<Path> direct = resolveExecutable(configured);
        if (direct.isPresent()) {
            return direct;
        }
        try {
            String name = Path.of(configured.strip()).getFileName().toString().replaceFirst("(?i)\\.exe$", "");
            return findOnPath(name);
        } catch (InvalidPathException ex) {
            return Optional.empty();
        }
    }

    static Optional<Path> resolveExecutable(String configured) {
        try {
            Path path = Path.of(configured.strip()).toAbsolutePath().normalize();
            if (Files.isRegularFile(path)) {
                return Optional.of(path);
            }
            if (WINDOWS && !path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".exe")) {
                Path exe = path.resolveSibling(path.getFileName() + ".exe");
                if (Files.isRegularFile(exe)) {
                    return Optional.of(exe);
                }
            }
        } catch (InvalidPathException ex) {
            log.warn("Caminho de executável inválido na configuração");
        }
        return Optional.empty();
    }

    static Optional<Path> findOnPath(String name) {
        String pathVariable = System.getenv("PATH");
        if (pathVariable == null || name == null || name.isBlank()) {
            return Optional.empty();
        }
        List<String> candidates = WINDOWS ? List.of(name + ".exe", name) : List.of(name);
        for (String directory : pathVariable.split(File.pathSeparator)) {
            String cleaned = directory.strip().replace("\"", "");
            if (cleaned.isEmpty()) {
                continue;
            }
            for (String candidate : candidates) {
                try {
                    Path path = Path.of(cleaned, candidate);
                    if (Files.isRegularFile(path) && (WINDOWS || Files.isExecutable(path))) {
                        return Optional.of(path.toAbsolutePath().normalize());
                    }
                } catch (InvalidPathException ignored) {
                    // entrada inválida no PATH
                }
            }
        }
        return Optional.empty();
    }
}
