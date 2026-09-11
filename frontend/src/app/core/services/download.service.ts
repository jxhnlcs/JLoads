import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Observable, Subject, catchError, map, of, tap } from 'rxjs';
import {
  CreateBatchDownloadRequest,
  CreateDownloadRequest,
  DownloadEvent,
  DownloadJob,
  TERMINAL_STATUSES,
} from '../models/download.models';
import { ApiService } from './api.service';
import { WebSocketService } from './websocket.service';

const DISMISSED_KEY = 'jloads.dismissedJobs';

/**
 * Estado dos downloads deste navegador. Carregado via REST e mantido em tempo real pelos eventos do
 * WebSocket — sem polling.
 */
@Injectable({ providedIn: 'root' })
export class DownloadService {
  private readonly api = inject(ApiService);
  private readonly socket = inject(WebSocketService);

  private readonly jobsById = signal<ReadonlyMap<string, DownloadJob>>(new Map());
  private readonly dismissed = signal<ReadonlySet<string>>(this.loadDismissed());

  readonly loaded = signal(false);

  readonly jobs = computed(() =>
    [...this.jobsById().values()]
      .filter((job) => !this.dismissed().has(job.id))
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt)),
  );

  readonly activeCount = computed(() => this.jobs().filter((job) => !TERMINAL_STATUSES.has(job.status)).length);

  readonly hasFinished = computed(() => this.jobs().some((job) => TERMINAL_STATUSES.has(job.status)));

  /** Soma da velocidade de todos os downloads em andamento (bytes/s). */
  readonly totalSpeedBytesPerSecond = computed(() =>
    this.jobs()
      .filter((job) => job.status === 'DOWNLOADING')
      .reduce((sum, job) => sum + (job.speedBytesPerSecond || 0), 0),
  );

  private readonly completedSubject = new Subject<DownloadJob>();

  /** Emite quando um job termina com arquivo disponível (usado pelo salvamento automático). */
  readonly completed$ = this.completedSubject.asObservable();

  constructor() {
    const destroyRef = inject(DestroyRef);
    this.socket.events$.pipe(takeUntilDestroyed(destroyRef)).subscribe((event) => this.applyEvent(event));
    // A cada (re)conexão, ressincroniza para não perder eventos emitidos enquanto estava desconectado.
    this.socket.connected$.pipe(takeUntilDestroyed(destroyRef)).subscribe(() => this.refresh());
  }

  refresh(): void {
    this.api
      .listDownloads()
      .pipe(catchError(() => of<DownloadJob[] | null>(null)))
      .subscribe((jobs) => {
        if (jobs) {
          const next = new Map<string, DownloadJob>();
          jobs.forEach((job) => next.set(job.id, job));
          this.jobsById.set(next);
        }
        this.loaded.set(true);
      });
  }

  create(request: CreateDownloadRequest): Observable<DownloadJob> {
    return this.api.createDownload(request).pipe(
      map((response) => response.job),
      tap((job) => this.upsert(job)),
    );
  }

  createBatch(request: CreateBatchDownloadRequest): Observable<DownloadJob[]> {
    return this.api.createBatch(request).pipe(
      map((response) => response.jobs),
      tap((jobs) => jobs.forEach((job) => this.upsert(job))),
    );
  }

  cancel(id: string): Observable<DownloadJob> {
    return this.api.cancelDownload(id).pipe(tap((job) => this.upsert(job)));
  }

  fileUrl(id: string): string {
    return this.api.fileUrl(id);
  }

  dismiss(id: string): void {
    this.dismissed.update((current) => new Set([...current, id]));
    this.persistDismissed();
  }

  clearFinished(): void {
    const finished = this.jobs()
      .filter((job) => TERMINAL_STATUSES.has(job.status))
      .map((job) => job.id);
    this.dismissed.update((current) => new Set([...current, ...finished]));
    this.persistDismissed();
  }

  private applyEvent(event: DownloadEvent): void {
    if (event?.job?.id) {
      this.upsert(event.job);
      if (event.event === 'JOB_COMPLETED' && event.job.fileAvailable) {
        this.completedSubject.next(event.job);
      }
    }
  }

  /** Aplica uma versão do job ignorando atualizações mais antigas que a atual. */
  private upsert(job: DownloadJob): void {
    this.jobsById.update((current) => {
      const existing = current.get(job.id);
      if (existing && isOlder(job, existing)) {
        return current;
      }
      const next = new Map(current);
      next.set(job.id, job);
      return next;
    });
  }

  private loadDismissed(): ReadonlySet<string> {
    try {
      const raw = localStorage.getItem(DISMISSED_KEY);
      const parsed: unknown = raw ? JSON.parse(raw) : [];
      return new Set(Array.isArray(parsed) ? parsed.filter((v): v is string => typeof v === 'string') : []);
    } catch {
      return new Set();
    }
  }

  private persistDismissed(): void {
    try {
      // Mantém apenas IDs que ainda existem para não crescer indefinidamente.
      const known = [...this.dismissed()].filter((id) => this.jobsById().has(id));
      localStorage.setItem(DISMISSED_KEY, JSON.stringify(known));
    } catch {
      // armazenamento indisponível: a lista só não persiste
    }
  }
}

function isOlder(candidate: DownloadJob, current: DownloadJob): boolean {
  const candidateTerminal = TERMINAL_STATUSES.has(candidate.status);
  const currentTerminal = TERMINAL_STATUSES.has(current.status);
  if (currentTerminal && !candidateTerminal) {
    return true;
  }
  // Instants ISO podem ter quantidades diferentes de casas decimais; compara numericamente.
  return Date.parse(candidate.updatedAt) < Date.parse(current.updatedAt);
}
