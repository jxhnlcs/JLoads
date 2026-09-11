# syntax=docker/dockerfile:1

# ---------- Build ----------
FROM eclipse-temurin:21-jdk-noble AS build
WORKDIR /workspace

COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src src
RUN ./mvnw -B -q package -DskipTests \
    && cp target/jloads-*.jar /workspace/app.jar

# ---------- Ferramentas externas ----------
FROM eclipse-temurin:21-jre-noble AS tools
ARG TARGETARCH
ARG YTDLP_VERSION=2026.08.19
RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl \
    && rm -rf /var/lib/apt/lists/*
# Binário standalone do yt-dlp com verificação de integridade (SHA-256 publicado na release).
RUN set -eu; \
    case "${TARGETARCH:-amd64}" in \
      amd64) asset="yt-dlp_linux" ;; \
      arm64) asset="yt-dlp_linux_aarch64" ;; \
      *) echo "Arquitetura não suportada: ${TARGETARCH}" >&2; exit 1 ;; \
    esac; \
    base="https://github.com/yt-dlp/yt-dlp/releases/download/${YTDLP_VERSION}"; \
    curl -fsSL -o "/tmp/${asset}" "${base}/${asset}"; \
    curl -fsSL -o /tmp/SHA2-256SUMS "${base}/SHA2-256SUMS"; \
    (cd /tmp && grep " ${asset}\$" SHA2-256SUMS | sha256sum -c -); \
    install -m 0755 "/tmp/${asset}" /opt/yt-dlp

# ---------- Runtime ----------
FROM eclipse-temurin:21-jre-noble

# FFmpeg para conversão/merge; curl apenas para o healthcheck.
RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg curl ca-certificates \
    && rm -rf /var/lib/apt/lists/*

COPY --from=tools /opt/yt-dlp /usr/local/bin/yt-dlp
# Deno: runtime JavaScript padrão usado pelo yt-dlp para o YouTube.
COPY --from=denoland/deno:bin-2.9.6 /deno /usr/local/bin/deno

RUN groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid app --home-dir /app --shell /usr/sbin/nologin app \
    && mkdir -p /app/storage \
    && chown -R app:app /app

WORKDIR /app
COPY --from=build --chown=app:app /workspace/app.jar /app/app.jar

ENV SERVER_ADDRESS=0.0.0.0 \
    YTDLP_EXECUTABLE=/usr/local/bin/yt-dlp \
    FFMPEG_EXECUTABLE=/usr/bin/ffmpeg \
    DOWNLOAD_DIRECTORY=/app/storage \
    XDG_CACHE_HOME=/tmp/cache \
    DENO_DIR=/tmp/deno \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

USER app
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD curl -fsS http://localhost:8080/actuator/health/liveness || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
