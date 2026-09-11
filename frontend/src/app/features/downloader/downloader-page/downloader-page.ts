import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, inject, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription, catchError, from, map, mergeMap, of } from 'rxjs';
import { AnalyzerState, DownloadJob, VideoAnalysis } from '../../../core/models/download.models';
import { ApiService } from '../../../core/services/api.service';
import { toFriendlyError } from '../../../core/services/api-error';
import { ConfigService } from '../../../core/services/config.service';
import { DownloadService } from '../../../core/services/download.service';
import { Icon } from '../../../shared/components/icon/icon';
import { LimitationsPanel } from '../../../shared/components/limitations-panel/limitations-panel';
import { BatchItem, BatchPanel } from '../batch-panel/batch-panel';
import { DownloadList } from '../download-list/download-list';
import { FormatSelection, FormatSelector } from '../format-selector/format-selector';
import { UrlInput } from '../url-input/url-input';
import { VideoPreview } from '../video-preview/video-preview';

/** Análises simultâneas por lote (o backend também limita). */
const BATCH_ANALYSIS_CONCURRENCY = 3;

/** Erros que indicam que o conteúdo em si não pode ser baixado — esses links não são enviados. */
const BLOCKING_ERROR_CODES = new Set([
  'INVALID_URL', 'UNSUPPORTED_URL', 'UNSUPPORTED_FORMAT', 'VIDEO_UNAVAILABLE', 'VIDEO_PRIVATE', 'RESTRICTED_CONTENT',
  'DRM_PROTECTED', 'GEO_RESTRICTED', 'COPYRIGHT_BLOCKED', 'LIVE_NOT_SUPPORTED', 'MEDIA_TOO_LONG',
]);

@Component({
  selector: 'app-downloader-page',
  imports: [UrlInput, VideoPreview, FormatSelector, BatchPanel, DownloadList, LimitationsPanel, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './downloader-page.html',
})
export class DownloaderPage {
  private readonly api = inject(ApiService);
  private readonly downloads = inject(DownloadService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly downloadsSection = viewChild<ElementRef<HTMLElement>>('downloadsSection');
  private readonly urlInput = viewChild(UrlInput);
  private analysisSubscription?: Subscription;
  private batchSubscription?: Subscription;

  protected readonly config = inject(ConfigService).config;
  protected readonly state = signal<AnalyzerState>('IDLE');
  protected readonly analysis = signal<VideoAnalysis | null>(null);
  protected readonly batchItems = signal<BatchItem[]>([]);
  protected readonly analyzeError = signal<string | null>(null);
  protected readonly downloadError = signal<string | null>(null);
  protected readonly submitting = signal(false);
  protected readonly notice = signal<string | null>(null);

  protected onAnalyze(url: string): void {
    this.clearTransientState();
    this.state.set('ANALYZING');

    this.analysisSubscription = this.api
      .analyze(url)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.analysis.set(result);
          this.state.set('READY');
        },
        error: (error: unknown) => {
          this.analyzeError.set(toFriendlyError(error).message);
          this.state.set('IDLE');
        },
      });
  }

  /** Mostra o lote imediatamente e preenche título/miniatura de cada link conforme as análises terminam. */
  protected onBatch(urls: string[]): void {
    this.clearTransientState();
    this.batchItems.set(urls.map((url) => ({ url, status: 'loading' })));
    this.state.set('BATCH');

    this.batchSubscription = from(urls)
      .pipe(
        mergeMap(
          (url) =>
            this.api.analyze(url).pipe(
              map((analysis): BatchItem => ({ url, status: 'ready', analysis })),
              catchError((error: unknown) => {
                const friendly = toFriendlyError(error);
                return of<BatchItem>({
                  url,
                  status: 'error',
                  error: friendly.message,
                  blocking: BLOCKING_ERROR_CODES.has(friendly.code),
                });
              }),
            ),
          BATCH_ANALYSIS_CONCURRENCY,
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((result) =>
        this.batchItems.update((items) => items.map((item) => (item.url === result.url ? result : item))),
      );
  }

  protected removeBatchUrl(index: number): void {
    const remaining = this.batchItems().filter((_, i) => i !== index);
    if (remaining.length === 0) {
      this.reset();
    } else {
      this.batchItems.set(remaining);
    }
  }

  protected onDownload(selection: FormatSelection): void {
    const analysis = this.analysis();
    if (!analysis || this.submitting()) {
      return;
    }
    this.enqueue(analysis.url, selection, analysis.title);
  }

  protected onBatchDownload(selection: FormatSelection): void {
    const urls = this.batchItems().filter((item) => !item.blocking).map((item) => item.url);
    if (urls.length === 0 || this.submitting()) {
      return;
    }
    this.submitting.set(true);
    this.downloadError.set(null);
    this.downloads
      .createBatch({ urls, ...selection })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (jobs) => {
          this.submitting.set(false);
          this.notice.set(jobs.length === 1 ? '1 download adicionado à fila.' : `${jobs.length} downloads adicionados à fila.`);
          this.reset();
          this.urlInput()?.reset();
          this.scrollToDownloads();
        },
        error: (error: unknown) => {
          this.submitting.set(false);
          this.downloadError.set(toFriendlyError(error).message);
        },
      });
  }

  protected onRetry(job: DownloadJob): void {
    this.enqueue(job.sourceUrl, { type: job.type, quality: job.quality }, job.title);
  }

  protected reset(): void {
    this.analysisSubscription?.unsubscribe();
    this.batchSubscription?.unsubscribe();
    this.analysis.set(null);
    this.batchItems.set([]);
    this.analyzeError.set(null);
    this.downloadError.set(null);
    this.state.set('IDLE');
  }

  private clearTransientState(): void {
    this.reset();
    this.notice.set(null);
  }

  private enqueue(url: string, selection: FormatSelection, title?: string): void {
    this.submitting.set(true);
    this.downloadError.set(null);
    this.notice.set(null);
    this.downloads
      .create({ url, ...selection })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.submitting.set(false);
          this.notice.set(title ? `“${title}” foi adicionado à fila.` : 'Download adicionado à fila.');
          this.scrollToDownloads();
        },
        error: (error: unknown) => {
          this.submitting.set(false);
          this.downloadError.set(toFriendlyError(error).message);
        },
      });
  }

  private scrollToDownloads(): void {
    this.downloadsSection()?.nativeElement.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }
}
