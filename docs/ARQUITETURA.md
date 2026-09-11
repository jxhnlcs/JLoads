# Arquitetura do JLoads

Documento para quem quer entender, modificar ou contribuir com o código. Para instalar e usar, veja o
[README](../README.md).

## Visão geral

```text
Angular ──HTTP /api──▶ Controller ──▶ Serviço de aplicação ──▶ Domínio (DownloadJob) ──▶ Infraestrutura
   ▲                                   │                                                ├─ YtDlpService → ProcessBuilder → yt-dlp → ffmpeg
   └────────── WebSocket /ws ◀──── DownloadEventPublisher                                ├─ LocalFileStorageService
                                                                                         └─ InMemoryDownloadJobRepository
```

```text
POST /api/downloads(/batch) → DownloadService → QueueService (fila limitada)
                                                     │
                                      ┌──────────────┼──────────────┐
                                   Worker 1       Worker 2       Worker N   (app.downloads.max-concurrent)
                                      │              │              │
                                   yt-dlp         yt-dlp         yt-dlp
```

| Parte | Tecnologia |
|---|---|
| Backend | Java 21, Spring Boot 4.1 (Web MVC, Validation, WebSocket, Actuator), Maven |
| Frontend | Angular 22 (standalone, signals, zoneless), RxJS, CSS próprio |
| Download | [yt-dlp](https://github.com/yt-dlp/yt-dlp) como processo externo + FFmpeg |
| Distribuição | Jar único com a interface embutida (perfil Maven `bundle`) ou Docker Compose |

## Backend (`backend/src/main/java/com/jloads`)

| Pacote | Responsabilidade |
|---|---|
| `controller` | `VideoController`, `DownloadController`, `ConfigController` — só HTTP ↔ DTO |
| `service` | Casos de uso (`DownloadService`, `VideoAnalysisService`, `QueueService`), `YtDlpService`, `YtDlpCommandBuilder`, armazenamento, métricas |
| `worker` | `DownloadWorker`: análise → download → processamento → armazenamento |
| `parser` | `YtDlpProgressParser`, `YtDlpMetadataParser`, `YtDlpErrorTranslator` |
| `model` | `DownloadJob` (máquina de estados thread-safe), `DownloadProgress`, enums |
| `process` | `ExternalBinaries` (localiza yt-dlp, FFmpeg e runtime JS), launcher e registro de processos |
| `validation` | `YouTubeUrlValidator` |
| `web` | Correlation ID, rate limit, headers de segurança, CORS, `ClientId`, encaminhamento das rotas do Angular |
| `websocket` | `DownloadWebSocketHandler` e publicação de eventos |
| `scheduler` | `CleanupScheduler` |
| `exception` | Exceções de negócio, `ErrorCode` e `GlobalExceptionHandler` |
| `config` | Propriedades tipadas, health indicator, métricas, aviso de inicialização |

### Estados de um download

```text
QUEUED → ANALYZING → DOWNLOADING → PROCESSING → COMPLETED
   └──────────┴────────────┴─────────────┴──────→ FAILED | CANCELLED
```

Transições inválidas são recusadas pelo próprio `DownloadJob`; estados finais não mudam mais.

### Pontos de extensão

| Interface | Implementação atual | Alternativa possível |
|---|---|---|
| `DownloadJobRepository` | Em memória | SQLite / PostgreSQL / Redis |
| `FileStorageService` | Disco local | S3 / object storage |
| `DownloadEventPublisher` | WebSocket local | Redis pub/sub / STOMP |
| `ProcessLauncher` | `ProcessBuilder` | Execução isolada / yt-dlp simulado (testes) |

## Frontend (`frontend/src/app`)

| Pasta | Conteúdo |
|---|---|
| `core/models` | Tipos da API e presets de formato |
| `core/services` | `api.service` (HTTP), `download.service` (estado com signals), `websocket.service` (reconexão), `file-save.service` (salvar em pasta), `config.service`, `preferences.service`, interceptor do `X-Client-Id` |
| `features/downloader` | Página principal: `url-input`, `video-preview`, `format-selector`, `batch-panel`, `download-list`/`download-card` |
| `features/settings` | Preferências: pasta de destino, salvamento automático, tipo padrão |
| `shared` | Ícones, pipes (bytes, duração), painel de limitações, apresentação de estados |

**Salvar em pasta:** usa a File System Access API do navegador. A autorização da pasta fica no IndexedDB; a cada
evento `JOB_COMPLETED`, o arquivo é baixado de `/api/downloads/{id}/file` e gravado na pasta sem sobrescrever
(`nome (1).mp3`). O servidor nunca recebe caminhos do computador do usuário.

## API

| Método | Endpoint | Descrição |
|---|---|---|
| `GET` | `/api/config` | Limites públicos (lote, tamanho, duração, retenção) |
| `POST` | `/api/videos/analyze` | `{ "url" }` → título, miniatura, canal, duração, `availableOptions` |
| `POST` | `/api/downloads` | `{ "url", "type": "AUDIO\|VIDEO", "quality": "BEST\|HIGH\|MEDIUM\|LOW" }` → `202 { jobId, status, job }` |
| `POST` | `/api/downloads/batch` | `{ "urls": [...], "type", "quality" }` → `202 { jobs }` (tudo ou nada, repetidos enviados uma vez) |
| `GET` | `/api/downloads` | Downloads deste navegador (header `X-Client-Id`) |
| `GET` | `/api/downloads/{id}` | Estado de um download |
| `DELETE` | `/api/downloads/{id}` | Cancela (remove da fila ou encerra o yt-dlp e processos filhos) |
| `GET` | `/api/downloads/{id}/file` | Arquivo final (somente `COMPLETED`) |
| `GET` | `/actuator/health`, `/actuator/metrics` | Saúde e métricas `jloads.*` |

Erros sempre seguem o formato:

```json
{ "timestamp": "...", "status": 400, "code": "INVALID_URL", "message": "URL inválida.", "correlationId": "..." }
```

Para vídeo, a qualidade é um **limite superior**: `HIGH` ("até 1080p") num vídeo de 720p baixa 720p.

### WebSocket

`ws://host/ws?clientId={uuid}` — o servidor envia apenas eventos dos downloads daquele navegador:
`JOB_QUEUED`, `JOB_STARTED`, `JOB_PROGRESS` (no máximo ~2 por segundo), `JOB_PROCESSING`, `JOB_COMPLETED`,
`JOB_FAILED`, `JOB_CANCELLED`, `JOB_EXPIRED`. Cada mensagem traz os campos de progresso e o estado completo em `job`.
O cliente envia `ping` periodicamente e recebe `pong`.

## Execução do yt-dlp

- A URL do usuário é validada por allowlist (`youtube.com`, `youtu.be`, Shorts, YouTube Music). Só o **ID do vídeo**
  é aproveitado; o comando recebe sempre `https://www.youtube.com/watch?v={id}`, após `--`.
- `YtDlpCommandBuilder` monta uma lista fixa de opções (`--ignore-config`, `--no-playlist`, `--max-filesize`,
  formato, etc.). Nenhum texto do usuário vira argumento.
- O progresso vem de `--progress-template` com linhas estruturadas (`YTDLP_PROGRESS|status|baixado|total|…`),
  interpretadas por `YtDlpProgressParser`.
- Erros (`ERROR: …`) são traduzidos por `YtDlpErrorTranslator` em mensagens amigáveis — a saída bruta nunca chega à
  interface.
- Cancelamento e timeout encerram o processo **e os descendentes** (bootloader do yt-dlp, FFmpeg).

## Armazenamento e limpeza

```text
storage/
├── temporary/{jobId}/   workspace do yt-dlp durante o download
└── completed/{jobId}/   arquivo final com nome sanitizado
```

O `CleanupScheduler` roda a cada 5 minutos e:

- apaga arquivos concluídos há mais de `app.cleanup.max-age-minutes` (evento `JOB_EXPIRED`);
- marca como `FAILED` downloads presos na fila ou abandonados (sem processo e sem atualização);
- remove registros antigos e pastas órfãs.

## Configuração

Tudo em `backend/src/main/resources/application.yml`, sobrescrevível por `config/application.yml` (na pasta de
execução), argumentos `--chave=valor` ou variáveis de ambiente.

| Variável | Padrão | Descrição |
|---|---|---|
| `SERVER_PORT` | `8080` | Porta HTTP |
| `SERVER_ADDRESS` | `127.0.0.1` | Interface de rede (`0.0.0.0` libera a rede; o Docker usa isso) |
| `OPEN_BROWSER` | `false` | Abre o navegador ao iniciar (os scripts do pacote ativam) |
| `YTDLP_EXECUTABLE` | `./bin/yt-dlp` | Caminho do yt-dlp; se não existir, procura no PATH |
| `YTDLP_JS_RUNTIME` | automático | `deno`, `node`, `quickjs`, `bun` ou `runtime:caminho` |
| `YTDLP_METADATA_TIMEOUT` | `60s` | Tempo máximo de análise |
| `YTDLP_SOCKET_TIMEOUT` | `30` | Timeout de rede do yt-dlp (segundos) |
| `YTDLP_RETRIES` | `3` | Tentativas do yt-dlp |
| `MAX_CONCURRENT_ANALYSIS` | `4` | Análises simultâneas |
| `ANALYSIS_CACHE_TTL` | `10m` | Cache dos metadados analisados |
| `FFMPEG_EXECUTABLE` | `./bin/ffmpeg` | Caminho do FFmpeg; se não existir, procura no PATH |
| `DOWNLOAD_DIRECTORY` | `./storage` | Raiz com `temporary/` e `completed/` |
| `MAX_CONCURRENT_DOWNLOADS` | `3` | Workers simultâneos |
| `MAX_QUEUE_SIZE` | `50` | Capacidade da fila |
| `MAX_ACTIVE_JOBS_PER_CLIENT` | `5` | Downloads ativos por IP |
| `MAX_BATCH_SIZE` | `5` | Links por envio |
| `MAX_FILE_SIZE` | `1GB` | Tamanho máximo por download |
| `MAX_STORAGE_SIZE` | `10GB` | Uso máximo da pasta de armazenamento |
| `MAX_MEDIA_DURATION` | `3h` | Duração máxima do conteúdo |
| `DOWNLOAD_TIMEOUT` | `30m` | Tempo máximo de um download |
| `DOWNLOAD_LIMIT_RATE` | vazio | Limite de banda por download (ex.: `5M`) |
| `CLEANUP_ENABLED` | `true` | Liga a limpeza automática |
| `CLEANUP_MAX_AGE` | `30` | Minutos até apagar um arquivo concluído |
| `JOB_RETENTION_MINUTES` | `120` | Minutos que downloads finalizados ficam na lista |
| `CLEANUP_INTERVAL` | `5m` | Intervalo da limpeza |
| `RATE_LIMIT_ENABLED` | `true` | Rate limit por IP |
| `RATE_LIMIT_API` / `RATE_LIMIT_ANALYZE` / `RATE_LIMIT_DOWNLOADS` | `120` / `20` / `10` | Requisições por minuto |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:4200` | Origens permitidas quando a interface roda separada |
| `FORWARD_HEADERS_STRATEGY` | `none` | `native` atrás de proxy reverso confiável |
| `LOG_LEVEL` | `INFO` | Nível de log do pacote `com.jloads` |

## Segurança

- **URLs:** allowlist de esquema, host e porta; URL reconstruída a partir do ID (evita SSRF e injeção).
- **Processos:** `ProcessBuilder` sem shell, argumentos fixos e `--` antes da URL.
- **Arquivos:** caminhos derivados de UUID, nomes sanitizados (`FilenameSanitizer`) e verificação de que tudo fica
  dentro da pasta do download.
- **Abuso:** rate limit, fila limitada, workers fixos, semáforo de análises, limites de tamanho, duração, espaço e
  tempo.
- **Rede:** por padrão escuta só em `127.0.0.1`. Headers de segurança e CSP: restritiva na API, mínima necessária na
  interface.
- **Privacidade:** sem cookies, contas ou credenciais; a interface nunca exibe stack traces nem saídas do yt-dlp.

## Testes

```bash
cd backend && ./mvnw test
cd frontend && npm test -- --watch=false
```

Os testes do backend usam `FakeYtDlp`, um yt-dlp simulado que roda como processo de verdade (em outra JVM) — então
`ProcessBuilder`, leitura da saída, timeout e encerramento do processo são exercitados sem acessar o YouTube.

Para desenvolver a interface com o motor simulado:

```bash
cd backend
YTDLP_EXECUTABLE=fake-yt-dlp ./mvnw spring-boot:test-run \
  -Dspring-boot.run.main-class=com.jloads.TestJLoadsApplication
```

IDs simulados: `okvideo0001`, `slowvideo01`, `forbidden01`, `hugevideo01`, `unavailable`, `livestream1`.

## Empacotamento e releases

- `frontend`: `ng build` gera `dist/jloads/browser`.
- `backend`: `./mvnw -Pbundle package` copia a interface para `static/` dentro do jar. O `SpaForwardingController`
  devolve o `index.html` para rotas do Angular (ex.: `/settings`).
- `scripts/build-release.sh` / `.ps1` fazem os dois passos e montam `dist/jloads-X.Y.Z.zip` com o jar, os scripts
  `jloads.cmd` / `jloads.sh`, README, licença e `config/application.yml.example`.
- Ao enviar uma tag `vX.Y.Z`, o workflow **Release** roda os testes, gera o pacote e publica na página de Releases.
